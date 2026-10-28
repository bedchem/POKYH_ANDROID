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
import dev.plattnericus.pokyh.ui.components.PendingSyncRow
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
import dev.plattnericus.pokyh.ui.components.TopBarNav
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
import dev.plattnericus.pokyh.core.i18n.strings
import androidx.compose.foundation.background

/** Todos — a swipe-to-delete list of [PokyhTileRow]s, with the add action as a labelled
 * [PokyhFab]. Rows stand on their own surface rather than in a grouped card, because each one is
 * independently swipeable and a swipe out of a grouped card would tear the card. */
@Composable
fun TodosScreen(onNavigateBack: () -> Unit = {}, viewModel: TodosViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val hasBackend = session?.apiToken != null
    var showAdd by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<ApiTodo?>(null) }
    val openCount = ui.todos.count { !it.done }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = strings().tasks.todos,
                eyebrow = if (ui.todos.isEmpty()) null else strings().tasks.todosEyebrow(openCount, ui.todos.size),
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
        floatingActionButton = {
            if (hasBackend || ui.offline) {
                PokyhFab(text = strings().tasks.new, icon = PokyhIcons.add, onClick = { showAdd = true })
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend && !ui.offline && !ui.offlineCopy -> BackendUnavailableView(feature = strings().tasks.todos, status = backendStatus)
                ui.loading && ui.todos.isEmpty() -> ListSkeleton()
                ui.error != null && ui.todos.isEmpty() && ui.pending.isEmpty() ->
                    ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                ui.todos.isEmpty() && ui.pending.isEmpty() -> EmptyStateView(
                    sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Empty,
                    title = strings().tasks.todosEmpty,
                    subtitle = if (hasBackend) {
                        strings().tasks.todosEmptyText
                    } else {
                        strings().tasks.todosOffline
                    },
                    action = if (hasBackend || ui.offline) {
                        {
                            PokyhSecondaryButton(
                                text = strings().tasks.addTask,
                                icon = PokyhIcons.add,
                                onClick = { showAdd = true },
                            )
                        }
                    } else {
                        null
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
                    items(ui.pending, key = { "pending-" + it.id }) { item ->
                        PendingSyncRow(
                            title = item.title,
                            text = item.text,
                            dateLabel = item.date?.let { parseDueDate(it) }?.format(DueDateFormatter),
                            onDelete = { viewModel.deletePending(item) },
                        )
                    }
                    // Open first, then what was done (in the last day), each under its heading.
                    val open = ui.todos.filter { !it.done }
                    val done = ui.todos.filter { it.done }
                    if (open.isNotEmpty() && done.isNotEmpty()) {
                        item(key = "h-open") { PokyhLabel(strings().tasks.open + " · " + open.size, Modifier.padding(top = PokyhSpacing.sm)) }
                    }
                    items(open, key = { it.id }) { todo ->
                        TodoSwipeRow(
                            todo = todo,
                            onToggle = { viewModel.toggle(todo) },
                            onDelete = { viewModel.delete(todo) },
                            onEdit = { editingTodo = todo },
                        )
                    }
                    if (done.isNotEmpty()) {
                        item(key = "h-done") { PokyhLabel(strings().tasks.done + " · " + done.size, Modifier.padding(top = PokyhSpacing.md)) }
                    }
                    items(done, key = { it.id }) { todo ->
                        TodoSwipeRow(
                            todo = todo,
                            onToggle = { viewModel.toggle(todo) },
                            onDelete = { viewModel.delete(todo) },
                            onEdit = { editingTodo = todo },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddTodoSheet(
            submitting = ui.submitting,
            error = ui.addError,
            onDismiss = { showAdd = false; viewModel.clearAddError() },
            onAdd = { title, details, dueAt -> viewModel.addTodo(title, details, dueAt) { showAdd = false } },
        )
    }
    editingTodo?.let { todo ->
        AddTodoSheet(
            submitting = ui.submitting,
            error = ui.addError,
            editing = todo,
            onDismiss = { editingTodo = null; viewModel.clearAddError() },
            onAdd = { title, details, dueAt -> viewModel.editTodo(todo, title, details, dueAt) { editingTodo = null } },
        )
    }
}

@Composable
private fun TodoSwipeRow(todo: ApiTodo, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit = {}) {
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
        TodoRow(todo = todo, onToggle = onToggle, onEdit = onEdit)
    }
}

@Composable
private fun TodoRow(todo: ApiTodo, onToggle: () -> Unit, onEdit: () -> Unit) {
    val colors = PokyhTheme.colors
    // Tapping the to-do opens it to change it.
    PokyhTileRow(verticalAlignment = Alignment.Top, onClick = onEdit) {
        PokyhIconButton(
            icon = if (todo.done) PokyhIcons.radioOn else PokyhIcons.radioOff,
            contentDescription = if (todo.done) strings().tasks.markOpen else strings().tasks.markDone,
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
            // A done item shows its title only, as on the web.
            if (todo.details.isNotEmpty() && !todo.done) {
                Text(todo.details, style = PokyhType.footnote, color = colors.textSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            val due = todo.dueAt?.let { parseDueInstant(it) }
            if (due != null && !todo.done) {
                val overdue = due.isBefore(Instant.now())
                val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(colors.isDark)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (overdue) {
                        Text(
                            strings().tasks.overdue,
                            style = PokyhType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                            color = tone.ink,
                            modifier = Modifier.background(tone.fill, PokyhShapes.pill).padding(horizontal = 10.dp, vertical = 2.dp),
                        )
                    }
                    Row(
                        Modifier
                            .then(if (overdue) Modifier else Modifier.background(colors.nested, PokyhShapes.pill))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Icon(PokyhIcons.timetable, contentDescription = null, tint = if (overdue) tone.ink else colors.textSecondary, modifier = Modifier.size(13.dp))
                        Text(dueLabel(due), style = PokyhType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = if (overdue) tone.ink else colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTodoSheet(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (title: String, details: String, dueAt: String?) -> Unit,
    /** Editing this to-do instead of adding one. */
    editing: ApiTodo? = null,
) {
    val initialDue = editing?.dueAt?.let { parseDueInstant(it) }?.atZone(ZoneId.systemDefault())
    var title by remember { mutableStateOf(editing?.title.orEmpty()) }
    var details by remember { mutableStateOf(editing?.details.orEmpty()) }
    var hasDue by remember { mutableStateOf(initialDue != null) }
    // The day (picker, UTC midnight) and the time of day (local), as the web's date-time field.
    var dueMillis by remember { mutableStateOf(initialDue?.toLocalDate()?.atStartOfDay(ZoneId.of("UTC"))?.toInstant()?.toEpochMilli() ?: Instant.now().toEpochMilli()) }
    var dueMinute by remember { mutableStateOf(initialDue?.let { it.hour * 60 + it.minute } ?: (18 * 60)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    fun dueInstant(): Instant = Instant.ofEpochMilli(dueMillis).atZone(ZoneId.of("UTC")).toLocalDate()
        .atTime(dueMinute / 60, dueMinute % 60).atZone(ZoneId.systemDefault()).toInstant()
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
            Text(if (editing != null) strings().tasks.editTodo else strings().tasks.newTodo, style = PokyhType.title1, color = PokyhTheme.colors.textPrimary)
            PokyhTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = strings().tasks.title,
                label = strings().tasks.titleField,
                modifier = Modifier.fillMaxWidth(),
            )
            PokyhTextField(
                value = details,
                onValueChange = { details = it },
                placeholder = strings().common.optional,
                label = strings().tasks.detailsField,
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
                    Text(strings().tasks.dueDate, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
                    if (hasDue) {
                        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                            PokyhTextButton(
                                text = Instant.ofEpochMilli(dueMillis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                                    .format(DueDateFormatter),
                                onClick = { showDatePicker = true },
                            )
                            PokyhTextButton(
                                text = "%02d:%02d".format(dueMinute / 60, dueMinute % 60),
                                onClick = { showTimePicker = true },
                            )
                        }
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
            if (error != null) Text(error, style = PokyhType.footnote, color = Brand.danger)
            Spacer(Modifier.size(PokyhSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PokyhTextButton(
                    text = strings().common.cancel,
                    onClick = ::dismiss,
                    color = PokyhTheme.colors.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                PokyhPrimaryButton(
                    text = if (editing != null) strings().tasks.saveChanges else strings().tasks.add,
                    onClick = {
                        val dueAt = if (hasDue) dueInstant().toString() else null
                        onAdd(title.trim(), details, dueAt)
                    },
                    enabled = title.isNotBlank(),
                    loading = submitting,
                )
            }
        }
    }

    if (showTimePicker) {
        val timeState = androidx.compose.material3.rememberTimePickerState(initialHour = dueMinute / 60, initialMinute = dueMinute % 60, is24Hour = true)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = PokyhTheme.colors.card,
            title = { Text(strings().tasks.dueTime, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary) },
            text = { androidx.compose.material3.TimePicker(state = timeState) },
            confirmButton = {
                PokyhTextButton(text = strings().tasks.apply, onClick = { dueMinute = timeState.hour * 60 + timeState.minute; showTimePicker = false })
            },
            dismissButton = {
                PokyhTextButton(text = strings().common.cancel, onClick = { showTimePicker = false }, color = PokyhTheme.colors.textSecondary)
            },
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                PokyhTextButton(
                    text = strings().tasks.apply,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dueMillis = it }
                        showDatePicker = false
                    },
                )
            },
            dismissButton = {
                PokyhTextButton(
                    text = strings().common.cancel,
                    onClick = { showDatePicker = false },
                    color = PokyhTheme.colors.textSecondary,
                )
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** "21. Okt. 2026" / "21 ott 2026" / "21 Oct 2026", in the language in use. */
private val DueDateFormatter: DateTimeFormatter
    get() = dev.plattnericus.pokyh.core.i18n.I18n.lang.value.let { l ->
        DateTimeFormatter.ofPattern(if (l == dev.plattnericus.pokyh.core.i18n.Lang.De) "d. MMM yyyy" else "d MMM yyyy", l.locale)
    }

/** "Heute, 18:00" or "Mi., 29. Okt., 18:00" in local time, as the web's due pill. */
private fun dueLabel(at: Instant): String {
    val l = dev.plattnericus.pokyh.core.i18n.I18n.lang.value
    val t = dev.plattnericus.pokyh.core.i18n.I18n.s.tasks
    val local = at.atZone(ZoneId.systemDefault())
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm", l.locale))
    if (local.toLocalDate() == LocalDate.now()) return t.todayAt(time)
    return t.dateTime(local.format(DateTimeFormatter.ofPattern(if (l == dev.plattnericus.pokyh.core.i18n.Lang.De) "EEE, d. MMM" else "EEE d MMM", l.locale)), time)
}

private fun parseDueInstant(raw: String): Instant? {
    runCatching { return OffsetDateTime.parse(raw).toInstant() }
    runCatching { return Instant.parse(raw) }
    return parseDueDate(raw)?.atStartOfDay(ZoneId.systemDefault())?.toInstant()
}

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

