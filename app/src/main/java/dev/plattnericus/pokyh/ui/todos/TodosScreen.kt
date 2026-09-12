@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.todos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.ApiTodo
import dev.plattnericus.pokyh.ui.components.BackendUnavailableView
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhFab
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.components.PokyhTileRow
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.StatusLabel
import dev.plattnericus.pokyh.ui.components.SwipeToDeleteBackground
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Todos — a swipe-to-delete list of [PokyhTileRow]s, with the add action as a labelled
 * [PokyhFab]. Rows stand on their own surface rather than in a grouped card, because each one is
 * independently swipeable and a swipe out of a grouped card would tear the card. */
@Composable
fun TodosScreen(viewModel: TodosViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val hasBackend = session?.apiToken != null
    var showAdd by remember { mutableStateOf(false) }
    val openCount = ui.todos.count { !it.done }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Todos",
                eyebrow = if (ui.todos.isEmpty()) null else "$openCount offen · ${ui.todos.size} gesamt",
            )
        },
        floatingActionButton = {
            if (hasBackend) {
                PokyhFab(text = "Neu", icon = PokyhIcons.add, onClick = { showAdd = true })
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend -> BackendUnavailableView(feature = "Todos", status = backendStatus)
                ui.loading && ui.todos.isEmpty() -> ListSkeleton()
                ui.error != null && ui.todos.isEmpty() ->
                    ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                ui.todos.isEmpty() -> EmptyStateView(
                    icon = PokyhIcons.todos,
                    title = "Keine Todos",
                    subtitle = "Lege eine Aufgabe an, um sie hier zu sehen.",
                    action = {
                        PokyhSecondaryButton(
                            text = "Aufgabe anlegen",
                            icon = PokyhIcons.add,
                            onClick = { showAdd = true },
                        )
                    },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = PokyhSpacing.screenH,
                        end = PokyhSpacing.screenH,
                        bottom = PokyhSpacing.huge + PokyhSpacing.xxxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap),
                ) {
                    items(ui.todos, key = { it.id }) { todo ->
                        TodoSwipeRow(
                            todo = todo,
                            onToggle = { viewModel.toggle(todo) },
                            onDelete = { viewModel.delete(todo) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTodoSheet(
            submitting = ui.submitting,
            onDismiss = { showAdd = false },
            onAdd = { title, details, dueAt -> viewModel.addTodo(title, details, dueAt) { showAdd = false } },
        )
    }
}

@Composable
private fun TodoSwipeRow(todo: ApiTodo, onToggle: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.fadeIn(),
        backgroundContent = { SwipeToDeleteBackground(dismissState.dismissDirection) },
    ) {
        TodoRow(todo = todo, onToggle = onToggle)
    }
}

@Composable
private fun TodoRow(todo: ApiTodo, onToggle: () -> Unit) {
    val colors = PokyhTheme.colors
    PokyhTileRow(verticalAlignment = Alignment.Top) {
        PokyhIconButton(
            icon = if (todo.done) PokyhIcons.radioOn else PokyhIcons.radioOff,
            contentDescription = if (todo.done) "Erledigt" else "Offen",
            onClick = onToggle,
            tint = if (todo.done) Brand.success else colors.textTertiary,
            size = 32.dp,
            iconSize = 24.dp,
        )
        Column(
            modifier = Modifier.weight(1f).padding(top = PokyhSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
        ) {
            Text(
                text = todo.title,
                style = PokyhType.body,
                color = if (todo.done) colors.textTertiary else colors.textPrimary,
                textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (todo.details.isNotEmpty()) {
                Text(todo.details, style = PokyhType.footnote, color = colors.textSecondary)
            }
            todo.dueAt?.let { due -> parseDueDate(due) }?.let { date ->
                StatusLabel(
                    text = date.format(DueDateFormatter),
                    color = Brand.orange,
                    icon = PokyhIcons.timetable,
                )
            }
        }
    }
}

@Composable
private fun AddTodoSheet(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onAdd: (title: String, details: String, dueAt: String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var hasDue by remember { mutableStateOf(false) }
    var dueMillis by remember { mutableStateOf(Instant.now().toEpochMilli()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        containerColor = PokyhTheme.colors.card,
        shape = PokyhShapes.topXxl,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(bottom = PokyhSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
        ) {
            Text("Neues Todo", style = PokyhType.title1, color = PokyhTheme.colors.textPrimary)
            PokyhTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Titel",
                label = "Titel",
                modifier = Modifier.fillMaxWidth(),
            )
            PokyhTextField(
                value = details,
                onValueChange = { details = it },
                placeholder = "Optional",
                label = "Details",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
                    Text("Fälligkeitsdatum", style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
                    if (hasDue) {
                        PokyhTextButton(
                            text = Instant.ofEpochMilli(dueMillis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                                .format(DueDateFormatter),
                            onClick = { showDatePicker = true },
                        )
                    }
                }
                Switch(
                    checked = hasDue,
                    onCheckedChange = { checked ->
                        hasDue = checked
                        if (checked) showDatePicker = true
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Brand.accent,
                        checkedThumbColor = Brand.onAccent,
                    ),
                )
            }
            Spacer(Modifier.size(PokyhSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PokyhTextButton(
                    text = "Abbrechen",
                    onClick = ::dismiss,
                    color = PokyhTheme.colors.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                PokyhPrimaryButton(
                    text = "Hinzufügen",
                    onClick = {
                        val dueAt = if (hasDue) Instant.ofEpochMilli(dueMillis).toString() else null
                        onAdd(title.trim(), details, dueAt)
                    },
                    enabled = title.isNotBlank(),
                    loading = submitting,
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                PokyhTextButton(
                    text = "Übernehmen",
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dueMillis = it }
                        showDatePicker = false
                    },
                )
            },
            dismissButton = {
                PokyhTextButton(
                    text = "Abbrechen",
                    onClick = { showDatePicker = false },
                    color = PokyhTheme.colors.textSecondary,
                )
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private val DueDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

/** `MessageFormat.parse`, ported — tries ISO-8601 first, then a handful of common backend
 * timestamp shapes, else `null` (the row's due-date label is simply omitted). */
private fun parseDueDate(raw: String): LocalDate? {
    runCatching { return OffsetDateTime.parse(raw).toLocalDate() }
    runCatching { return Instant.parse(raw).atZone(ZoneId.of("UTC")).toLocalDate() }
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm")) {
        runCatching { return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern)).toLocalDate() }
    }
    runCatching { return LocalDate.parse(raw) }
    return null
}

