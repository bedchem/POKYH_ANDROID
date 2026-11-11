@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.absences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.AbsenceReason
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.accentSurface
import dev.plattnericus.pokyh.ui.theme.insetSurface
import dev.plattnericus.pokyh.ui.theme.nestedSurface
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

// "Abwesenheit melden" and "Entschuldigen", the web app's two absence sheets for the phone.
// Built like "Mitteilung an Lehrkraft": the fields on the sheet, the reason list as a second view
// inside the same sheet, the action below, and a confirmation before the sheet leaves. Reasons,
// the preset reason and the preset range come from WebUntis' own report dialog (absences/meta).
// Nothing is reported as saved unless WebUntis saved it.

/** "Abwesenheit melden": from when to when, the reason and a text. */
@Composable
fun ReportAbsenceSheet(viewModel: AbsencesViewModel, onDismiss: () -> Unit) {
    val t = strings().absences
    val form by viewModel.form.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var startTime by remember { mutableStateOf<LocalTime?>(null) }
    var endTime by remember { mutableStateOf<LocalTime?>(null) }
    var reason by remember { mutableStateOf<AbsenceReason?>(null) }
    var text by remember { mutableStateOf("") }
    var pickingReason by remember { mutableStateOf(false) }
    var pickingLessons by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.openForm() }

    // The presets once they arrive, without overwriting what was already chosen.
    LaunchedEffect(form.meta, form.todayRange) {
        val meta = form.meta
        if (reason == null) reason = meta?.reasons?.firstOrNull { it.id == meta.defaultReasonId }
        val presetDate = meta?.defaultDate?.let(::localDateOf)
        if (meta != null && presetDate != null && meta.defaultStartTime != null && meta.defaultEndTime != null) {
            if (startTime == null) { startDate = presetDate; startTime = localTimeOf(meta.defaultStartTime) }
            if (endTime == null) { endDate = presetDate; endTime = localTimeOf(meta.defaultEndTime) }
        } else form.todayRange?.let { (from, to) ->
            if (startTime == null) startTime = localTimeOf(from)
            if (endTime == null) endTime = localTimeOf(to)
        }
    }

    // What was reported, under "Gemeldet" (web doneDetail).
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d.M.", dev.plattnericus.pokyh.core.i18n.I18n.lang.value.locale) }
    val reportedRange = run {
        val f = startTime ?: return@run null
        val e = endTime ?: return@run null
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        if (startDate == endDate) "${startDate.format(fmt)} ${f.format(hm)} – ${e.format(hm)}" else "${startDate.format(fmt)} ${f.format(hm)} – ${endDate.format(fmt)} ${e.format(hm)}"
    }
    AbsenceSheetFrame(
        title = when {
            pickingReason -> t.reason
            pickingLessons -> t.chooseLessons
            else -> t.reportTitle
        },
        done = form.done,
        doneText = t.reported,
        doneDetail = reportedRange,
        onBack = when {
            pickingReason -> ({ pickingReason = false })
            pickingLessons -> ({ pickingLessons = false })
            else -> null
        },
        onDismiss = onDismiss,
    ) {
        if (pickingLessons) {
            LessonPicker(
                viewModel = viewModel,
                initial = startDate,
                onTake = { day, from, to ->
                    startDate = day
                    endDate = day
                    startTime = from
                    endTime = to
                    pickingLessons = false
                },
            )
        } else if (pickingReason) {
            ReasonList(
                reasons = form.meta?.reasons,
                failed = form.metaFailed,
                selected = reason,
                onSelect = { reason = it; pickingReason = false },
            )
        } else {
            // Who it is for.
            val name = viewModel.personName?.takeIf { it.isNotBlank() } ?: t.me
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t.forLabel, style = PokyhType.footnote.semibold(), color = PokyhTheme.colors.textPrimary)
                Row(
                    Modifier.fillMaxWidth().nestedSurface(PokyhShapes.lg).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    dev.plattnericus.pokyh.ui.components.InitialAvatar(name = name, size = 36.dp)
                    Text(name, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
                }
            }
            // Changing the start keeps the end on the same day, never before the start (web).
            DateTimeField(
                label = t.from,
                date = startDate,
                time = startTime,
                enabled = !form.saving,
                onDate = { startDate = it; endDate = it; val s = startTime; val e = endTime; if (s != null && e != null && e < s) endTime = s },
                onTime = { startTime = it; val e = endTime; if (it != null && e != null && startDate == endDate && e < it) endTime = it },
            )
            DateTimeField(
                label = t.until,
                date = endDate,
                time = endTime,
                enabled = !form.saving,
                onDate = { endDate = it },
                onTime = { endTime = it },
            )
            dev.plattnericus.pokyh.ui.components.PokyhTextButton(
                text = t.fromTimetable,
                icon = PokyhIcons.timetable,
                onClick = { pickingLessons = true },
                enabled = !form.saving,
            )
            ReasonField(reason = reason, enabled = !form.saving, onClick = { pickingReason = true })
            PokyhTextField(
                value = text,
                onValueChange = { text = it.take(1000) },
                label = t.text,
                placeholder = t.textPlaceholder,
                singleLine = false,
                minLines = 3,
                enabled = !form.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let { SheetMessage(it, Brand.danger) }
            PokyhPrimaryButton(
                text = t.reportAction,
                icon = PokyhIcons.sendMessage,
                loading = form.saving,
                onClick = {
                    val from = startTime
                    val to = endTime
                    val chosen = reason
                    when {
                        from == null || to == null -> viewModel.formError(t.errChooseRange)
                        endDate.atTime(to).isBefore(startDate.atTime(from)) -> viewModel.formError(t.errEndAfterStart)
                        chosen == null -> viewModel.formError(t.errChooseReason)
                        else -> viewModel.report(
                            startDate = yyyyMMdd(startDate),
                            startTime = from.hour * 100 + from.minute,
                            endDate = yyyyMMdd(endDate),
                            endTime = to.hour * 100 + to.minute,
                            reasonId = chosen.id,
                            text = text,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * "Entschuldigen" for absences that are over, as the web does it now: WebUntis takes no reason for
 * those, so the excuse goes to the class teachers as a WebUntis message (one each), and they mark
 * the absence excused. The absence on top, who gets it, the reason as a field whose list is a
 * second step in the same sheet, the note, the send button, a confirmation at the end naming
 * whom it reached. A partial send stays open, so the names it missed can be read.
 */
@Composable
fun ExcuseAbsenceSheet(
    viewModel: AbsencesViewModel,
    absences: List<AbsenceEntry>,
    summary: String,
    onDismiss: () -> Unit,
) {
    val t = strings().absences
    val colors = PokyhTheme.colors
    val form by viewModel.form.collectAsStateWithLifecycle()
    var reason by remember { mutableStateOf<AbsenceReason?>(null) }
    var text by remember { mutableStateOf("") }
    var pickingReason by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.openForm(forExcuse = true) }
    LaunchedEffect(form.meta) {
        val meta = form.meta ?: return@LaunchedEffect
        if (reason == null) reason = meta.reasons.firstOrNull { it.id == meta.defaultReasonId }
    }
    val teachers = form.teachers
    val noTeacher = teachers != null && teachers.isEmpty() && !form.teachersFailed
    val result = form.excuse

    AbsenceSheetFrame(
        title = if (pickingReason) t.reason else t.exTitle,
        done = form.done,
        doneText = if (result?.viaMessage == false) t.exSavedInUntis else t.exDone,
        doneDetail = when {
            result == null -> null
            !result.viaMessage -> reason?.name
            else -> t.exDoneTo(result.sentTo.joinToString(", "))
        },
        doneWarning = result?.failed?.takeIf { it.isNotEmpty() }?.let { t.exPartial(it.joinToString(", ")) },
        autoClose = result?.failed.isNullOrEmpty(),
        onBack = if (pickingReason) ({ pickingReason = false }) else null,
        onDismiss = onDismiss,
    ) {
        if (pickingReason) {
            ReasonList(
                reasons = form.meta?.reasons,
                failed = form.metaFailed,
                selected = reason,
                onSelect = { reason = it; pickingReason = false },
            )
        } else {
            Text(t.exLead, style = PokyhType.footnote, color = colors.textSecondary)
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                PokyhLabel(t.exAbsence, color = colors.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth().insetSurface(PokyhShapes.lg).padding(PokyhSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PokyhIcons.absences, contentDescription = null, tint = Brand.danger, modifier = Modifier.size(20.dp))
                    Text(summary, style = PokyhType.body, color = colors.textPrimary)
                }
            }
            // Who it goes to: the class teachers WebUntis names for the class.
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                PokyhLabel(t.exTo, color = colors.textSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth().insetSurface(PokyhShapes.lg).padding(PokyhSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!teachers.isNullOrEmpty()) {
                        val tone = PokyhDecorative.periwinkle(colors.isDark)
                        Box {
                            teachers.take(3).forEachIndexed { i, name ->
                                Box(
                                    modifier = Modifier
                                        .offset(x = (i * 22).dp)
                                        .size(34.dp)
                                        .clip(PokyhShapes.pill)
                                        .background(colors.card)
                                        .padding(2.dp)
                                        .clip(PokyhShapes.pill)
                                        .background(tone.fill),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(initials(name), style = PokyhType.caption.semibold(), color = tone.ink)
                                }
                            }
                            Spacer(Modifier.size(width = (34 + (teachers.take(3).size - 1) * 22).dp, height = 34.dp))
                        }
                    } else {
                        Icon(PokyhIcons.classMembers, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = when {
                            teachers == null -> "…"
                            form.teachersFailed -> t.exToSchool
                            teachers.isNotEmpty() -> teachers.joinToString(", ")
                            else -> t.notAvailable
                        },
                        style = PokyhType.body,
                        color = if (!teachers.isNullOrEmpty()) colors.textPrimary else colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (noTeacher) Text(if (form.teacherUnclear) t.exTeacherUnclear else t.exNoTeacher, style = PokyhType.footnote, color = Brand.danger)
            }
            ReasonField(reason = reason, enabled = !form.saving, onClick = { pickingReason = true })
            PokyhTextField(
                value = text,
                onValueChange = { text = it.take(1000) },
                label = t.exNote,
                placeholder = strings().common.optional,
                singleLine = false,
                minLines = 3,
                enabled = !form.saving,
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let { SheetMessage(it, Brand.danger) }
            PokyhPrimaryButton(
                text = if (form.saving) t.exSaving else t.exSave,
                icon = PokyhIcons.sendMessage,
                loading = form.saving,
                enabled = !noTeacher,
                onClick = {
                    val chosen = reason
                    if (chosen == null) viewModel.formError(t.exPick)
                    else viewModel.excuse(absences, chosen, text)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(Regex("[\\s,]+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

// ── Sheet parts ─────────────────────────────────────────────────────────────

@Composable
private fun AbsenceSheetFrame(
    title: String,
    done: Boolean,
    doneText: String,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
    doneDetail: String? = null,
    doneWarning: String? = null,
    autoClose: Boolean = true,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val c = strings().common
    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    // Saved: the confirmation stays a moment, then the sheet leaves.
    LaunchedEffect(done, autoClose) {
        if (done && autoClose) {
            kotlinx.coroutines.delay(1600)
            dismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (onBack != null) onBack() else dismiss() },
        sheetState = sheetState,
        containerColor = PokyhTheme.colors.card,
        shape = PokyhShapes.topXxl,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PokyhIconButton(
                    icon = if (onBack != null) PokyhIcons.back else PokyhIcons.close,
                    contentDescription = if (onBack != null) c.back else c.close,
                    onClick = { if (onBack != null) onBack() else dismiss() },
                    tinted = true,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Text(
                    text = title,
                    style = PokyhType.headline,
                    color = PokyhTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).padding(horizontal = PokyhSpacing.md),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (done) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    Box(Modifier.size(64.dp).accentSurface(PokyhShapes.xl), contentAlignment = Alignment.Center) {
                        Icon(PokyhIcons.check, contentDescription = null, tint = Brand.success, modifier = Modifier.size(30.dp))
                    }
                    Text(doneText, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                    doneDetail?.let { Text(it, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary) }
                    doneWarning?.let { Text(it, style = PokyhType.footnote.semibold(), color = Brand.danger) }
                }
            } else {
                content()
            }
        }
    }
}

/** Label, then the date and the time as two buttons on one recessed strip (as in Erinnerungen). */
@Composable
private fun DateTimeField(
    label: String,
    date: LocalDate,
    time: LocalTime?,
    enabled: Boolean,
    onDate: (LocalDate) -> Unit,
    onTime: (LocalTime) -> Unit,
) {
    val t = strings().absences
    val c = strings().common
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
        PokyhLabel(label, color = PokyhTheme.colors.textSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .insetSurface(PokyhShapes.md)
                .padding(horizontal = PokyhSpacing.sm, vertical = PokyhSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PokyhTextButton(
                text = "${c.weekdaysShort[date.dayOfWeek.value - 1]}, ${c.dayMonthShort(date.dayOfMonth, date.monthValue)} ${date.year}",
                icon = PokyhIcons.timetable,
                onClick = { if (enabled) pickDate = true },
            )
            Spacer(Modifier.weight(1f))
            PokyhTextButton(
                text = time?.format(TimeFormatter) ?: t.time,
                icon = PokyhIcons.clock,
                onClick = { if (enabled) pickTime = true },
                color = if (time == null) PokyhTheme.colors.textTertiary else PokyhTheme.colors.accentText,
            )
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                PokyhTextButton(
                    text = t.apply,
                    onClick = {
                        state.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        pickDate = false
                    },
                )
            },
            dismissButton = {
                PokyhTextButton(text = c.cancel, onClick = { pickDate = false }, color = PokyhTheme.colors.textSecondary)
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (pickTime) {
        val start = time ?: LocalTime.of(8, 0)
        val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            shape = PokyhShapes.xl,
            containerColor = PokyhTheme.colors.card,
            confirmButton = {
                PokyhTextButton(
                    text = t.apply,
                    onClick = {
                        onTime(LocalTime.of(state.hour, state.minute))
                        pickTime = false
                    },
                )
            },
            dismissButton = {
                PokyhTextButton(text = c.cancel, onClick = { pickTime = false }, color = PokyhTheme.colors.textSecondary)
            },
            text = { TimePicker(state = state) },
        )
    }
}

/** "Grund → Krankheit ›": opens the reason list inside the sheet. */
@Composable
private fun ReasonField(reason: AbsenceReason?, enabled: Boolean, onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    val t = strings().absences
    Box(Modifier.fillMaxWidth().insetSurface(PokyhShapes.lg)) {
        PokyhRow(
            title = t.reason,
            enabled = enabled,
            trailing = {
                Text(
                    text = reason?.name ?: t.choose,
                    style = PokyhType.subheadline,
                    color = if (reason == null) colors.textTertiary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            onClick = onClick,
        )
    }
}

@Composable
private fun ReasonList(
    reasons: List<AbsenceReason>?,
    failed: Boolean,
    selected: AbsenceReason?,
    onSelect: (AbsenceReason) -> Unit,
) {
    val colors = PokyhTheme.colors
    val t = strings().absences
    if (reasons.isNullOrEmpty()) {
        Text(
            text = when {
                failed -> t.reasonsFailed
                reasons == null -> t.reasonsLoading
                else -> t.noReasons
            },
            style = PokyhType.footnote,
            color = colors.textTertiary,
            modifier = Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.xl),
        )
        return
    }
    Column(
        modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap),
    ) {
        reasons.forEach { r ->
            val isSelected = r.id == selected?.id
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isSelected) Modifier.accentSurface(PokyhShapes.lg) else Modifier)
                    .pressHighlight(interaction, PokyhShapes.lg)
                    .clickable(interactionSource = interaction, indication = null, onClick = { onSelect(r) })
                    .padding(horizontal = PokyhSpacing.md, vertical = PokyhSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = r.name,
                    style = if (isSelected) PokyhType.body.semibold() else PokyhType.body,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(PokyhIcons.ok, contentDescription = t.selected, tint = Brand.accent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SheetMessage(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = PokyhType.footnote,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .nestedSurface(PokyhShapes.lg)
            .padding(PokyhSpacing.md),
    )
}

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun yyyyMMdd(d: LocalDate): Int = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth

private fun localDateOf(n: Int): LocalDate? =
    runCatching { LocalDate.of(n / 10000, (n / 100) % 100, n % 100) }.getOrNull()

private fun localTimeOf(hhmm: Int): LocalTime? =
    runCatching { LocalTime.of(hhmm / 100, hhmm % 100) }.getOrNull()

/**
 * "Aus Stundenplan wählen": one day's lessons to tick; the range goes from the first chosen
 * lesson's start to the last one's end (web TimetableLessonPicker). The day moves back and forth.
 */
@Composable
private fun LessonPicker(viewModel: AbsencesViewModel, initial: LocalDate, onTake: (LocalDate, LocalTime, LocalTime) -> Unit) {
    val t = strings().absences
    val colors = PokyhTheme.colors
    var day by remember { mutableStateOf(initial) }
    var lessons by remember { mutableStateOf<List<dev.plattnericus.pokyh.data.model.TimetableEntry>?>(null) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(day) {
        lessons = null
        picked = emptySet()
        lessons = viewModel.lessonsOn(day)
    }
    val fmt = remember { DateTimeFormatter.ofPattern("EEEE, d. MMMM", dev.plattnericus.pokyh.core.i18n.I18n.lang.value.locale) }
    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
        Row(Modifier.fillMaxWidth().nestedSurface(PokyhShapes.lg).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            dev.plattnericus.pokyh.ui.components.PokyhIconButton(icon = PokyhIcons.chevronLeft, contentDescription = t.prevDay, onClick = { day = day.minusDays(1) })
            Text(day.format(fmt).replaceFirstChar { it.titlecase() }, style = PokyhType.callout.semibold(), color = colors.textPrimary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            dev.plattnericus.pokyh.ui.components.PokyhIconButton(icon = PokyhIcons.chevronRight, contentDescription = t.nextDay, onClick = { day = day.plusDays(1) })
        }
        val list = lessons
        when {
            list == null -> dev.plattnericus.pokyh.ui.components.SkeletonBlock(height = 48.dp)
            list.isEmpty() -> Text(t.noLessonsDay, style = PokyhType.callout, color = colors.textSecondary)
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEachIndexed { i, l ->
                    val on = i in picked
                    Row(
                        Modifier.fillMaxWidth().clip(PokyhShapes.lg).then(if (on) Modifier.accentSurface(PokyhShapes.lg) else Modifier.nestedSurface(PokyhShapes.lg))
                            .clickable { picked = if (on) picked - i else picked + i }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(if (on) PokyhIcons.radioOn else PokyhIcons.radioOff, contentDescription = null, tint = if (on) Brand.accent else colors.textTertiary, modifier = Modifier.size(20.dp))
                        Text("%02d:%02d".format(l.startTime / 100, l.startTime % 100) + " – " + "%02d:%02d".format(l.endTime / 100, l.endTime % 100), style = PokyhType.footnote.semibold(), color = colors.textSecondary)
                        Text(l.subjectName.ifEmpty { l.subjectLong } + if (l.isCancelled) t.cancelledSuffix else "", style = PokyhType.callout, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        PokyhPrimaryButton(
            text = t.takeLessons(picked.size),
            enabled = picked.isNotEmpty(),
            onClick = {
                val chosen = picked.mapNotNull { list?.getOrNull(it) }
                if (chosen.isEmpty()) return@PokyhPrimaryButton
                val from = chosen.minOf { it.startTime }
                val to = chosen.maxOf { it.endTime }
                onTake(day, LocalTime.of(from / 100, from % 100), LocalTime.of(to / 100, to % 100))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
