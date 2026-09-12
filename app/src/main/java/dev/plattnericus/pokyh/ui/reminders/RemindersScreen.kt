@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.reminders

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.ui.components.BackendUnavailableView
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.IconTile
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhFab
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
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
import dev.plattnericus.pokyh.ui.theme.insetSurface
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Klassen-Erinnerungen — the same swipe-to-delete tile-row list as Todos, so the two screens
 * that sit next to each other in the School hub behave identically. */
@Composable
fun RemindersScreen(
    onReminderClick: (String) -> Unit,
    viewModel: RemindersViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val hasBackend = session?.apiToken != null
    val hasClass = session?.classId != null
    var showAdd by remember { mutableStateOf(false) }
    val visible = remember(ui.reminders) { visibleReminders(ui.reminders) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Erinnerungen",
                eyebrow = if (visible.isEmpty()) null else "${visible.size} anstehend",
            )
        },
        floatingActionButton = {
            if (hasBackend && hasClass) {
                PokyhFab(text = "Neu", icon = PokyhIcons.add, onClick = { showAdd = true })
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend -> BackendUnavailableView(feature = "Erinnerungen", status = backendStatus)
                ui.loading && ui.reminders.isEmpty() -> ListSkeleton()
                ui.error != null && ui.reminders.isEmpty() ->
                    ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                !hasClass -> EmptyStateView(
                    icon = PokyhIcons.classMembers,
                    title = "Keine Klasse",
                    subtitle = "Du bist noch keiner Klasse beigetreten.",
                )
                visible.isEmpty() -> EmptyStateView(
                    icon = PokyhIcons.reminders,
                    title = "Keine Erinnerungen",
                    subtitle = "Lege eine Klassen-Erinnerung an, um sie hier zu sehen.",
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
                    items(visible, key = { it.id }) { reminder ->
                        ReminderSwipeRow(
                            reminder = reminder,
                            onClick = { onReminderClick(reminder.id) },
                            onDelete = { viewModel.delete(reminder) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddReminderSheet(
            submitting = ui.submitting,
            onDismiss = { showAdd = false },
            onAdd = { title, body, remindAt -> viewModel.addReminder(title, body, remindAt) { showAdd = false } },
        )
    }
}

@Composable
private fun ReminderSwipeRow(reminder: ApiReminder, onClick: () -> Unit, onDelete: () -> Unit) {
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
        ReminderRow(reminder = reminder, onClick = onClick)
    }
}

@Composable
private fun ReminderRow(reminder: ApiReminder, onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    PokyhTileRow(onClick = onClick, verticalAlignment = Alignment.Top) {
        IconTile(icon = PokyhIcons.reminders, color = Brand.accent, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
            Text(reminder.title, style = PokyhType.headline, color = colors.textPrimary)
            if (reminder.body.isNotEmpty()) {
                Text(
                    text = reminder.body,
                    style = PokyhType.footnote,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            parseRemindAt(reminder.remindAt)?.let { instant ->
                StatusLabel(text = dueText(instant), color = Brand.orange, icon = PokyhIcons.timetable)
            }
        }
    }
}

@Composable
private fun AddReminderSheet(
    submitting: Boolean,
    onDismiss: () -> Unit,
    onAdd: (title: String, body: String, remindAtIso: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    val now = remember { LocalDateTime.now() }
    var dateMillis by remember {
        mutableStateOf(now.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    }
    var time by remember { mutableStateOf(LocalTime.of(now.hour, now.minute)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val pickedDate = remember(dateMillis) { Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate() }

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
            Text("Neue Erinnerung", style = PokyhType.title1, color = PokyhTheme.colors.textPrimary)

            PokyhTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = "Titel",
                label = "Titel",
                modifier = Modifier.fillMaxWidth(),
            )
            PokyhTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = "Optional",
                label = "Beschreibung",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                PokyhLabel("Erinnern am", color = PokyhTheme.colors.textSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .insetSurface(PokyhShapes.md)
                        .padding(horizontal = PokyhSpacing.sm, vertical = PokyhSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PokyhTextButton(
                        text = pickedDate.format(DueDateFormatter),
                        icon = PokyhIcons.timetable,
                        onClick = { showDatePicker = true },
                    )
                    Spacer(Modifier.weight(1f))
                    PokyhTextButton(
                        text = time.format(TimeFormatter),
                        icon = PokyhIcons.clock,
                        onClick = { showTimePicker = true },
                    )
                }
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
                        val remindAt = pickedDate.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toString()
                        onAdd(title.trim(), body, remindAt)
                    },
                    enabled = title.isNotBlank(),
                    loading = submitting,
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                PokyhTextButton(
                    text = "Übernehmen",
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis = it }
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

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            shape = PokyhShapes.xl,
            containerColor = PokyhTheme.colors.card,
            confirmButton = {
                PokyhTextButton(
                    text = "Übernehmen",
                    onClick = {
                        time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    },
                )
            },
            dismissButton = {
                PokyhTextButton(
                    text = "Abbrechen",
                    onClick = { showTimePicker = false },
                    color = PokyhTheme.colors.textSecondary,
                )
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

private val DueDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)

/** Nur aktuelle Erinnerungen: abgelaufene (länger als 1 Tag vorbei) ausblenden, chronologisch
 * sortiert (nächste zuerst) — port of `RemindersView.visibleReminders`. */
internal fun visibleReminders(list: List<ApiReminder>): List<ApiReminder> {
    val cutoff = Instant.now().minusSeconds(86_400)
    return list
        .map { it to (parseRemindAt(it.remindAt) ?: Instant.MAX) }
        .filter { (_, instant) -> !instant.isBefore(cutoff) }
        .sortedBy { (_, instant) -> instant }
        .map { (reminder, _) -> reminder }
}

/** `TodoRow.dueText`, ported — date only (no time), matching the iOS source 1:1. */
internal fun dueText(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault()).toLocalDate().format(DueDateFormatter)

/** `MessageFormat.parse`, ported — tries ISO-8601 first, then a handful of common backend
 * timestamp shapes, else `null`. */
internal fun parseRemindAt(raw: String): Instant? {
    runCatching { return Instant.parse(raw) }
    runCatching { return OffsetDateTime.parse(raw).toInstant() }
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm")) {
        runCatching {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern))
                .atZone(ZoneId.systemDefault())
                .toInstant()
        }
    }
    runCatching { return LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant() }
    return null
}
