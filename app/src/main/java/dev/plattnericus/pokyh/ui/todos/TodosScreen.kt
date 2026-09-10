@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.todos

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.ApiTodo
import dev.plattnericus.pokyh.ui.components.BackendUnavailableView
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.fadeIn
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** TodosView.swift, ported. */
@Composable
fun TodosScreen(viewModel: TodosViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val hasBackend = session?.apiToken != null

    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Todos", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
        floatingActionButton = {
            if (hasBackend) {
                FloatingActionButton(onClick = { showAdd = true }, containerColor = Brand.accent, contentColor = Color.White) {
                    Icon(PokyhIcons.plus, contentDescription = "Neues Todo")
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend -> BackendUnavailableView(feature = "Todos", status = backendStatus)
                ui.loading && ui.todos.isEmpty() -> ListSkeleton()
                ui.error != null && ui.todos.isEmpty() -> ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                ui.todos.isEmpty() -> EmptyStateView(
                    icon = PokyhIcons.checklist,
                    title = "Keine Todos",
                    subtitle = "Tippe auf +, um eine Aufgabe anzulegen.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brand.danger, PokyhShapes.r14)
                    .padding(horizontal = 18.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                },
            ) {
                Icon(PokyhIcons.trash, contentDescription = "Löschen", tint = Color.White)
            }
        },
    ) {
        TodoRow(todo = todo, onToggle = onToggle)
    }
}

@Composable
private fun TodoRow(todo: ApiTodo, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(radius = 14.dp)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = if (todo.done) PokyhIcons.checkmark_circle_fill else PokyhIcons.circle_outline,
                contentDescription = if (todo.done) "Erledigt" else "Offen",
                tint = if (todo.done) Brand.tint else PokyhTheme.colors.textSecondary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = todo.title,
                style = PokyhType.body,
                color = if (todo.done) PokyhTheme.colors.textSecondary else PokyhTheme.colors.textPrimary,
                textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (todo.details.isNotEmpty()) {
                Text(todo.details, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
            }
            todo.dueAt?.let { due -> parseDueDate(due) }?.let { date ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(PokyhIcons.calendar, contentDescription = null, tint = Brand.orange, modifier = Modifier.size(12.dp))
                    Text(date.format(DueDateFormatter), style = PokyhType.caption2, color = Brand.orange)
                }
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

    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Neues Todo", style = PokyhType.title3, color = PokyhTheme.colors.textPrimary)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = pokyhTextFieldColors(),
            )
            OutlinedTextField(
                value = details,
                onValueChange = { details = it },
                label = { Text("Details (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = pokyhTextFieldColors(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Fälligkeitsdatum", style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
                Switch(
                    checked = hasDue,
                    onCheckedChange = { checked ->
                        hasDue = checked
                        if (checked) showDatePicker = true
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Brand.accent, checkedThumbColor = Color.White),
                )
            }
            if (hasDue) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardSurface(radius = 12.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .then(Modifier),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Fällig am", style = PokyhType.subheadline, color = PokyhTheme.colors.textSecondary)
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(
                            Instant.ofEpochMilli(dueMillis).atZone(ZoneId.of("UTC")).toLocalDate().format(DueDateFormatter),
                            color = Brand.accent,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = ::dismiss) { Text("Abbrechen") }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        val dueAt = if (hasDue) Instant.ofEpochMilli(dueMillis).toString() else null
                        onAdd(title.trim(), details, dueAt)
                    },
                    enabled = title.isNotBlank() && !submitting,
                    colors = ButtonDefaults.buttonColors(containerColor = Brand.accent),
                ) {
                    Text("Hinzufügen")
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dueMillis = it }
                    showDatePicker = false
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun pokyhTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Brand.accent,
    unfocusedBorderColor = PokyhTheme.colors.border,
    focusedLabelColor = Brand.accent,
    unfocusedLabelColor = PokyhTheme.colors.textSecondary,
    cursorColor = Brand.accent,
    focusedTextColor = PokyhTheme.colors.textPrimary,
    unfocusedTextColor = PokyhTheme.colors.textPrimary,
)

private val DueDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

/** `MessageFormat.parse`, ported — tries ISO-8601 first, then a handful of common backend
 * timestamp shapes, else `null` (row's due-date label is simply omitted, matching iOS). */
private fun parseDueDate(raw: String): LocalDate? {
    runCatching { return OffsetDateTime.parse(raw).toLocalDate() }
    runCatching { return Instant.parse(raw).atZone(ZoneId.of("UTC")).toLocalDate() }
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm")) {
        runCatching { return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern)).toLocalDate() }
    }
    runCatching { return LocalDate.parse(raw) }
    return null
}
