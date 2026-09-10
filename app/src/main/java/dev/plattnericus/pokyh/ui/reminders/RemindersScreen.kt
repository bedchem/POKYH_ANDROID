@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.ui.components.BackendUnavailableView
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.fadeIn
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

/** RemindersView.swift, ported. */
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
            TopAppBar(
                title = { Text("Erinnerungen", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
        floatingActionButton = {
            if (hasBackend && hasClass) {
                FloatingActionButton(onClick = { showAdd = true }, containerColor = Brand.accent, contentColor = Color.White) {
                    Icon(PokyhIcons.plus, contentDescription = "Neue Erinnerung")
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend -> BackendUnavailableView(feature = "Erinnerungen", status = backendStatus)
                ui.loading && ui.reminders.isEmpty() -> ListSkeleton()
                ui.error != null && ui.reminders.isEmpty() -> ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                !hasClass -> EmptyStateView(
                    icon = PokyhIcons.person_3_fill,
                    title = "Keine Klasse",
                    subtitle = "Du bist noch keiner Klasse beigetreten.",
                )
                visible.isEmpty() -> EmptyStateView(
                    icon = PokyhIcons.bell_fill,
                    title = "Keine Erinnerungen",
                    subtitle = "Lege eine Klassen-Erinnerung an.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
        ReminderRow(reminder = reminder, onClick = onClick)
    }
}

@Composable
private fun ReminderRow(reminder: ApiReminder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(radius = 14.dp)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(Brand.tint.copy(alpha = 0.14f), PokyhShapes.r10),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PokyhIcons.bell_fill, contentDescription = null, tint = Brand.tint)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(reminder.title, style = PokyhType.subheadline.semibold(), color = PokyhTheme.colors.textPrimary)
            if (reminder.body.isNotEmpty()) {
                Text(
                    reminder.body,
                    style = PokyhType.caption,
                    color = PokyhTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            parseRemindAt(reminder.remindAt)?.let { instant ->
                Text(dueText(instant), style = PokyhType.caption2, color = Brand.orange)
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
    var dateMillis by remember { mutableStateOf(now.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()) }
    var time by remember { mutableStateOf(LocalTime.of(now.hour, now.minute)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val pickedDate = remember(dateMillis) { Instant.ofEpochMilli(dateMillis).atZone(ZoneOffset.UTC).toLocalDate() }

    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Neue Erinnerung", style = PokyhType.title3, color = PokyhTheme.colors.textPrimary)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = pokyhTextFieldColors(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Beschreibung (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = pokyhTextFieldColors(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardSurface(radius = 12.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Erinnern am", style = PokyhType.subheadline, color = PokyhTheme.colors.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(pickedDate.format(DueDateFormatter), color = Brand.accent)
                    }
                    TextButton(onClick = { showTimePicker = true }) {
                        Text(time.format(TimeFormatter), color = Brand.accent)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = ::dismiss) { Text("Abbrechen") }
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = {
                        val remindAt = pickedDate.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toString()
                        onAdd(title.trim(), body, remindAt)
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
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMillis = it }
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

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Abbrechen") }
            },
            text = { TimePicker(state = timePickerState) },
        )
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
internal fun dueText(instant: Instant): String = instant.atZone(ZoneId.systemDefault()).toLocalDate().format(DueDateFormatter)

/** `MessageFormat.parse`, ported — tries ISO-8601 first, then a handful of common backend
 * timestamp shapes, else `null`. */
internal fun parseRemindAt(raw: String): Instant? {
    runCatching { return Instant.parse(raw) }
    runCatching { return OffsetDateTime.parse(raw).toInstant() }
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm")) {
        runCatching {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern)).atZone(ZoneId.systemDefault()).toInstant()
        }
    }
    runCatching { return LocalDate.parse(raw).atStartOfDay(ZoneId.systemDefault()).toInstant() }
    return null
}
