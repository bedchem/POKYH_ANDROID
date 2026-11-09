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
import dev.plattnericus.pokyh.ui.components.PendingSyncRow
import dev.plattnericus.pokyh.ui.components.PokyhFab
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
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
import dev.plattnericus.pokyh.core.i18n.strings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width

/** Klassen-Erinnerungen — the same swipe-to-delete tile-row list as Todos, so the two screens
 * that sit next to each other in the School hub behave identically. */
@Composable
fun RemindersScreen(
    onReminderClick: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
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
                title = strings().tasks.reminders,
                // Only what is still to come counts, as on the web.
                eyebrow = visible.count { (parseRemindAt(it.remindAt) ?: Instant.MAX) >= Instant.now() }
                    .takeIf { it > 0 }?.let { strings().tasks.upcoming(it) },
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
        floatingActionButton = {
            if ((hasBackend && hasClass) || ui.offline) {
                PokyhFab(text = strings().tasks.new, icon = PokyhIcons.add, onClick = { showAdd = true })
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend && !ui.offline && !ui.offlineCopy -> BackendUnavailableView(feature = strings().tasks.remindersFeature, status = backendStatus)
                ui.loading && ui.reminders.isEmpty() -> ListSkeleton()
                ui.error != null && ui.reminders.isEmpty() && ui.pending.isEmpty() ->
                    ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                !hasClass && !ui.offline && !ui.offlineCopy -> EmptyStateView(
                    sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Search,
                    title = strings().tasks.noClass,
                    // The backend's reason where it gave one (waiting for the check, none in WebUntis, …).
                    subtitle = (backendStatus as? dev.plattnericus.pokyh.data.model.BackendStatus.NoClass)
                        ?.let { dev.plattnericus.pokyh.ui.components.noClassMessage(strings().tasks.remindersFeature, it.note) }
                        ?: strings().tasks.noClassText,
                )
                visible.isEmpty() && ui.pending.isEmpty() -> EmptyStateView(
                    sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Bell,
                    title = strings().tasks.remindersEmpty,
                    subtitle = if (ui.offline) {
                        strings().tasks.remindersOffline
                    } else {
                        strings().tasks.remindersEmptyText
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
                            dateLabel = item.date?.let { parseRemindAt(it) }?.let { dueText(it) },
                            onDelete = { viewModel.deletePending(item) },
                        )
                    }
                    // As on the web: what is due first, then one section per day ("Heute · …").
                    val now = Instant.now()
                    val due = visible.filter { (parseRemindAt(it.remindAt) ?: Instant.MAX) < now }
                    val days = visible.filter { (parseRemindAt(it.remindAt) ?: Instant.MAX) >= now }
                        .groupBy { parseRemindAt(it.remindAt)?.atZone(ZoneId.systemDefault())?.toLocalDate() }
                    fun section(key: String, title: String, list: List<ApiReminder>, overdue: Boolean) {
                        item(key = "h-$key") { PokyhLabel(title, Modifier.padding(top = PokyhSpacing.sm)) }
                        items(list, key = { it.id }) { reminder ->
                            val mine = (ui.stableUid != null && reminder.createdBy == ui.stableUid) || reminder.createdByUsername.equals(session?.username, ignoreCase = true)
                            ReminderSwipeRow(
                                reminder = reminder,
                                mine = mine,
                                overdue = overdue,
                                // Only the creator, or an admin, may delete it.
                                canDelete = mine || ui.isAdmin,
                                onClick = { onReminderClick(reminder.id) },
                                onDelete = { viewModel.delete(reminder) },
                            )
                        }
                    }
                    if (due.isNotEmpty()) section("due", dev.plattnericus.pokyh.core.i18n.I18n.s.tasks.due, due, overdue = true)
                    days.forEach { (date, list) -> section(date.toString(), date?.let { reminderDayTitle(it) }.orEmpty(), list, overdue = false) }
                }
            }
        }
    }

    if (showAdd) {
        AddReminderSheet(
            submitting = ui.submitting,
            error = ui.addError,
            onDismiss = { showAdd = false; viewModel.clearAddError() },
            onAdd = { title, body, remindAt -> viewModel.addReminder(title, body, remindAt) { showAdd = false } },
        )
    }
}

@Composable
private fun ReminderSwipeRow(reminder: ApiReminder, mine: Boolean, overdue: Boolean, canDelete: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    if (!canDelete) {
        ReminderRow(reminder = reminder, mine = mine, overdue = overdue, onClick = onClick)
        return
    }
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
        ReminderRow(reminder = reminder, mine = mine, overdue = overdue, onClick = onClick)
    }
}

@Composable
private fun ReminderRow(reminder: ApiReminder, mine: Boolean, overdue: Boolean, onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    val at = parseRemindAt(reminder.remindAt)
    // The web's row: the time, the title with its text and who added it, the countdown on the right.
    PokyhTileRow(onClick = onClick, verticalAlignment = Alignment.Top) {
        Text(
            at?.atZone(ZoneId.systemDefault())?.format(TimeFormatter).orEmpty(),
            style = PokyhType.footnote.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = colors.textPrimary,
            modifier = Modifier.width(46.dp).padding(top = 2.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
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
            Text(
                if (mine) strings().common.you else strings().tasks.by(reminder.createdByUsername.ifEmpty { reminder.createdByName }),
                style = PokyhType.caption,
                color = colors.textTertiary,
            )
        }
        if (at != null) CountdownPill(at, overdue)
    }
}

/** "in 3 Tagen", "in 5 Std.", "Gleich" or "Fällig" (web `timeUntil`). */
internal fun timeUntil(at: Instant, now: Instant = Instant.now()): String {
    val t = dev.plattnericus.pokyh.core.i18n.I18n.s.tasks
    val diff = at.toEpochMilli() - now.toEpochMilli()
    if (diff < 0) return t.due
    val h = (diff / 3_600_000L).toInt()
    val d = h / 24
    return when {
        d > 1 -> t.inDays(d)
        d == 1 -> t.inDay
        h > 0 -> t.inHours(h)
        else -> t.soon
    }
}

@Composable
internal fun CountdownPill(at: Instant, overdue: Boolean) {
    val colors = PokyhTheme.colors
    val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(colors.isDark)
    Text(
        timeUntil(at),
        style = PokyhType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
        color = if (overdue) tone.ink else colors.textSecondary,
        modifier = Modifier
            .background(if (overdue) tone.fill else colors.nested, PokyhShapes.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** "Heute · Mittwoch, 29. Oktober", "Morgen · …" or the date alone. */
private fun reminderDayTitle(date: LocalDate): String {
    val l = dev.plattnericus.pokyh.core.i18n.I18n.lang.value
    val long = DateTimeFormatter.ofPattern(dev.plattnericus.pokyh.core.i18n.I18n.s.tasks.dayLong, l.locale).format(date)
        .replaceFirstChar { it.titlecase(l.locale) }
    val today = LocalDate.now()
    return when (date) {
        today -> dev.plattnericus.pokyh.core.i18n.I18n.s.mensa.today + " · " + long
        today.plusDays(1) -> dev.plattnericus.pokyh.core.i18n.I18n.s.mensa.tomorrow + " · " + long
        else -> long
    }
}

@Composable
internal fun AddReminderSheet(
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (title: String, body: String, remindAtIso: String) -> Unit,
    /** Editing this reminder instead of adding one. */
    editing: ApiReminder? = null,
) {
    var title by remember { mutableStateOf(editing?.title.orEmpty()) }
    var body by remember { mutableStateOf(editing?.body.orEmpty()) }
    val now = remember { editing?.let { parseRemindAt(it.remindAt) }?.atZone(ZoneId.systemDefault())?.toLocalDateTime() ?: LocalDateTime.now() }
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
            Text(if (editing != null) strings().tasks.editReminder else strings().tasks.newReminder, style = PokyhType.title1, color = PokyhTheme.colors.textPrimary)

            PokyhTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = strings().tasks.title,
                label = strings().tasks.titleField,
                modifier = Modifier.fillMaxWidth(),
            )
            PokyhTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = strings().common.optional,
                label = strings().tasks.descriptionField,
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                PokyhLabel(strings().tasks.remindOn, color = PokyhTheme.colors.textSecondary)
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
            if (error != null) Text(error, style = PokyhType.footnote, color = Brand.danger)
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
                    text = strings().tasks.apply,
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis = it }
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
                    text = strings().tasks.apply,
                    onClick = {
                        time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    },
                )
            },
            dismissButton = {
                PokyhTextButton(
                    text = strings().common.cancel,
                    onClick = { showTimePicker = false },
                    color = PokyhTheme.colors.textSecondary,
                )
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

/** "21. Okt. 2026" / "21 ott 2026" / "21 Oct 2026", in the language in use. */
private val DueDateFormatter: DateTimeFormatter
    get() = dev.plattnericus.pokyh.core.i18n.I18n.lang.value.let { l ->
        DateTimeFormatter.ofPattern(if (l == dev.plattnericus.pokyh.core.i18n.Lang.De) "d. MMM yyyy" else "d MMM yyyy", l.locale)
    }
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** Nur aktuelle Erinnerungen: abgelaufene (länger als 1 Tag vorbei) ausblenden, chronologisch
 * sortiert (nächste zuerst) — port of `RemindersView.visibleReminders`. */
internal fun visibleReminders(list: List<ApiReminder>): List<ApiReminder> {
    // Kept 25 hours after they were due, as on the web.
    val cutoff = Instant.now().minusSeconds(25 * 3600L)
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
