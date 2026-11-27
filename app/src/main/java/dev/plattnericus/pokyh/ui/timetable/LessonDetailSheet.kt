@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.LessonDetail
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.untis.AbsenceMark
import dev.plattnericus.pokyh.data.untis.AbsenceMarks
import dev.plattnericus.pokyh.data.untis.LessonAbsence
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.DecorativeTone
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.SubjectTone
import dev.plattnericus.pokyh.ui.theme.statusTone
import dev.plattnericus.pokyh.ui.theme.subjectTone
import kotlinx.coroutines.launch

/**
 * The lesson detail, as a bottom sheet — the same layout as the website's lesson popup
 * (pokyh-frontend timetable/LessonDetailSheet.tsx):
 *
 * - the subject's name, large and in the subject's colour, with its status chips right under it;
 * - the properties, each its glyph and name in grey with the value under it, lined up after the
 *   glyph: Zeit, Lehrkraft (a substitute first, the teacher it replaces struck through under it),
 *   Raum, Klassen, Gruppe, Abwesend;
 * - under a thin line, the lesson's texts the same way: Statt / Stattdessen, Prüfungsinhalt,
 *   notes, Lehrstoff, Hausaufgaben — or one grey line saying there are none, so every lesson's
 *   popup has the same shape.
 *
 * No cards, no backings behind the glyphs, no subject photos.
 *
 * What only the lesson's detail knows (classes, group, lesson texts, homework) comes from WebUntis
 * separately ([detail]); everything after "Zeit" waits for it, at most [DetailWaitMillis], so the
 * sheet fills in once instead of row by row.
 */
@Composable
fun LessonDetailSheet(
    slot: MergedSlot,
    onDismiss: () -> Unit,
    absences: List<AbsenceEntry> = emptyList(),
    detailLoading: Boolean = false,
    detail: LessonDetail? = null,
    /** Excuses sent to the class teachers: an unexcused absence they cover says so. */
    sentExcuses: List<UntisClient.SentExcuse> = emptyList(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        containerColor = PokyhTheme.colors.card,
        shape = PokyhShapes.topXxl,
        dragHandle = { SheetHandle() },
    ) {
        LessonDetailContent(
            slot = slot,
            absences = absences,
            detailLoading = detailLoading,
            detail = detail,
            sentExcuses = sentExcuses,
            onClose = ::dismiss,
        )
    }
}

/** How long the popup waits for the lesson's detail before it shows what the timetable knows. */
private const val DetailWaitMillis = 3500L

/** The grab bar on top, as on the website's phone sheet. */
@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 40.dp, height = 5.dp)
            .background(PokyhTheme.colors.textTertiary.copy(alpha = 0.45f), PokyhShapes.pill),
    )
}

/** Where a property's value starts: after the glyph (18) and its gap (8), under the label's text. */
private val ValueInset = 26.dp
private val GlyphSize = 18.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonDetailContent(
    slot: MergedSlot,
    absences: List<AbsenceEntry>,
    detailLoading: Boolean,
    detail: LessonDetail?,
    sentExcuses: List<UntisClient.SentExcuse>,
    onClose: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val d = slot.display
    val t = strings().timetable
    val tone = remember(slot.id, colors.isDark) { detailTone(slot, colors.isDark) }
    val headerName = headerNameFor(slot)
    val tags = tagsFor(slot, colors.isDark)
    var waited by remember(slot.id) { mutableStateOf(false) }
    LaunchedEffect(slot.id) {
        kotlinx.coroutines.delay(DetailWaitMillis)
        waited = true
    }
    // Cancelled lessons have no detail worth waiting for.
    val ready = d.isCancelled || !detailLoading || waited

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = PokyhSpacing.xxxl),
    ) {
        // The name in the subject's colour, the close button beside it.
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Text(
                text = headerName,
                style = PokyhType.title2.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.78).sp),
                color = tone.ink,
                modifier = Modifier.weight(1f),
            )
            PokyhIconButton(
                icon = PokyhIcons.sheetClose,
                contentDescription = strings().common.close,
                onClick = onClose,
                tint = colors.textSecondary,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }
        // The status right under the name.
        if (tags.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { (text, chip) ->
                    Text(
                        text = text,
                        style = PokyhType.footnote.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                        color = chip.ink,
                        modifier = Modifier.background(chip.fill, PokyhShapes.pill).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // The properties, close under the name or its chip.
        Spacer(Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Prop(PokyhIcons.clock, t.lessonTime) {
                Text(dayLine(d.date), style = ValueStyle, color = colors.textPrimary)
                Text(
                    "${Fmt.time(d.startTime)} – ${Fmt.time(d.endTime)}",
                    style = ValueStyle.copy(fontWeight = FontWeight.SemiBold).monospacedDigits(),
                    color = colors.textPrimary,
                )
            }
            if (ready) {
                val absence = remember(slot.id, absences) {
                    if (d.isCancelled) {
                        null
                    } else {
                        AbsenceMarks.lessonAbsence(
                            absences = absences,
                            dateNum = d.date,
                            startMinute = Fmt.minutes(d.startTime),
                            endMinute = Fmt.minutes(d.endTime),
                            nowDateNum = SchoolDates.todayNum(),
                            nowMinute = SchoolDates.minuteNow(),
                        )
                    }
                }
                Facts(d, classes = detail?.classes.orEmpty(), group = detail?.group.orEmpty())
                absence?.let { a ->
                    // An unexcused absence the class teacher was written about shows that, as the web.
                    val sent = if (a.mark == AbsenceMark.ABSENT) {
                        val start = d.date.toLong() * 10000 + d.startTime
                        val end = d.date.toLong() * 10000 + d.endTime
                        sentExcuses.filter { it.start < end && it.end > start }.maxByOrNull { it.sentAt }
                    } else {
                        null
                    }
                    AbsenceProp(a, sent)
                }
            }
        }

        if (!ready) {
            Spacer(Modifier.size(16.dp))
            DetailSkeleton()
            return@Column
        }

        // Every text of the lesson once: the timetable's own first, then what the detail adds,
        // then any further text type WebUntis sent with the period.
        val notes = mutableListOf<Pair<String, String>>()
        fun addNote(label: String, text: String?) {
            val v = text?.trim().orEmpty()
            if (v.isNotEmpty() && notes.none { sameText(it.second, v) }) notes += label to v
        }
        addNote(t.note, d.note)
        addNote(t.lessonText, d.lessonText)
        addNote(t.substitutionText, d.substitutionText)
        detail?.texts?.forEach { (label, text) -> addNote(label, text) }
        d.texts.forEach { x ->
            val label = when (x.type) {
                "SUBSTITUTION" -> t.substitutionText
                "LESSON_TEXT" -> t.lessonText
                "LESSON_INFO" -> t.note
                else -> t.info
            }
            addNote(label, x.text)
        }
        val exam = if (d.isExam) {
            listOfNotNull(detail?.examName, d.examDescription ?: detail?.examDescription)
                .filter { it.isNotBlank() }.distinct().joinToString("\n")
        } else {
            ""
        }
        val homeworks = detail?.homeworks.orEmpty()

        // The texts under a thin line; always there, so a lesson with little to say keeps the shape.
        Spacer(Modifier.size(20.dp))
        PokyhRowSeparator(startInset = 0.dp)
        Spacer(Modifier.size(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            slot.replacement?.let { InsteadSection(it) }
            if (exam.isNotEmpty()) TextSection(PokyhIcons.lessonExam, t.examContent) { BodyText(exam) }
            notes.forEach { (label, text) ->
                TextSection(if (label == "Lehrstoff") PokyhIcons.lessonContent else PokyhIcons.document, label) { BodyText(text) }
            }
            if (homeworks.isNotEmpty()) {
                TextSection(PokyhIcons.markHomework, t.homework) {
                    homeworks.forEachIndexed { i, hw ->
                        if (i > 0) Spacer(Modifier.size(8.dp))
                        BodyText(hw)
                    }
                }
            }
            if (slot.replacement == null && exam.isEmpty() && notes.isEmpty() && homeworks.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph(PokyhIcons.document)
                    Text(t.noTexts, style = LabelStyle, color = colors.textTertiary)
                }
            }
        }
    }
}

/** The same text once, whichever field it came from. */
private fun sameText(a: String, b: String): Boolean =
    a.replace(Regex("\\s+"), " ").trim().equals(b.replace(Regex("\\s+"), " ").trim(), ignoreCase = true)

/** "Montag, 23. November" in the app's language. */
private fun dayLine(date: Int): String = runCatching {
    val l = I18n.lang.value
    java.time.LocalDate.of(date / 10000, date / 100 % 100, date % 100)
        .format(java.time.format.DateTimeFormatter.ofPattern(I18n.s.tasks.dayLong, l.locale))
        .replaceFirstChar { it.titlecase(l.locale) }
}.getOrDefault("")

private val LabelStyle @Composable get() = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 20.sp)
private val ValueStyle @Composable get() = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
private val SmallStyle @Composable get() = PokyhType.footnote.copy(fontSize = 13.5.sp, lineHeight = 18.sp)

/** A label's glyph: the same size everywhere, in the label's grey. */
@Composable
private fun Glyph(icon: ImageVector, tint: Color = PokyhTheme.colors.textTertiary) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(GlyphSize))
}

/** One property: its glyph and name in grey, then the value under them, lined up after the glyph. */
@Composable
private fun Prop(icon: ImageVector, label: String, value: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyph(icon)
            Text(label, style = LabelStyle, color = PokyhTheme.colors.textTertiary)
        }
        Spacer(Modifier.size(4.dp))
        Column(Modifier.padding(start = ValueInset), content = value)
    }
}

/** A text of the lesson (instead, exam, note, lesson content, homework): built like a property. */
@Composable
private fun TextSection(icon: ImageVector, title: String, body: @Composable ColumnScope.() -> Unit) = Prop(icon, title, body)

@Composable
private fun BodyText(text: String) {
    Text(text, style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 23.sp), color = PokyhTheme.colors.textPrimary)
}

/** Stands in for the rest while the detail is on its way. */
@Composable
private fun DetailSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(2) { i ->
            Column {
                SkeletonBlock(height = 12.dp, width = 64.dp)
                Spacer(Modifier.size(6.dp))
                Box(Modifier.padding(start = ValueInset)) { SkeletonBlock(height = 14.dp, width = if (i == 0) 150.dp else 100.dp) }
            }
        }
    }
}

// ── Content ─────────────────────────────────────────────────────────────────

/** The status chips, each in its status colour (the website's blush, butter, periwinkle, sage). */
private fun tagsFor(slot: MergedSlot, isDark: Boolean): List<Pair<String, DecorativeTone>> {
    val d = slot.display
    return buildList {
        val t = I18n.s.timetable
        if (d.isCancelled) add(t.cancelled to PokyhDecorative.blush(isDark).let { DecorativeTone(it.fill, if (isDark) Color(0xFFF87171) else Color(0xFFB91C1C)) })
        if (d.isExam) add(t.exam to PokyhDecorative.butter(isDark))
        if (slot.kind == SlotKind.REPLACEMENT || (d.isSubstitution && !d.isCancelled)) add(t.substitution to PokyhDecorative.periwinkle(isDark))
        if (slot.kind == SlotKind.EVENT) add(t.event to PokyhDecorative.sage(isDark))
    }
}

/**
 * The name's colour. Reads [MergedSlot.display], not the replacement: the week grid gives each
 * half of a substitution its own slot, so `display` is always the lesson that was tapped.
 */
private fun detailTone(slot: MergedSlot, isDark: Boolean): SubjectTone {
    val d = slot.display
    return when {
        d.isCancelled -> statusTone(Brand.danger, isDark)
        d.isExam -> statusTone(Brand.warning, isDark)
        slot.kind == SlotKind.EVENT -> statusTone(Brand.accentSoft, isDark)
        d.subjectName.isEmpty() -> statusTone(Brand.accent, isDark)
        else -> subjectTone(d.subjectName, isDark)
    }
}

/** The long name where WebUntis has one, else the short name, else the note. */
private fun headerNameFor(slot: MergedSlot): String {
    val d = slot.display
    return d.subjectLong.ifEmpty { d.subjectName.ifEmpty { d.note ?: I18n.s.timetable.lesson } }
}

/** Teacher, room, classes and group. */
@Composable
private fun Facts(d: TimetableEntry, classes: List<String>, group: String) {
    val t = strings().timetable
    val colors = PokyhTheme.colors
    val teacher = d.teacherLongName ?: d.teacherName
    val origTeacher = d.originalTeacherLong ?: (d.originalTeacher ?: "")
    val room = d.roomName
    val origRoom = d.originalRoom ?: ""
    if (teacher.isNotEmpty() || origTeacher.isNotEmpty()) {
        Prop(PokyhIcons.lessonTeacher, t.teacher) {
            if (origTeacher.isNotEmpty() && origTeacher != teacher) Change(origTeacher, teacher)
            else Text(teacher.ifEmpty { origTeacher }, style = ValueStyle, color = colors.textPrimary)
        }
    }
    if (room.isNotEmpty() || origRoom.isNotEmpty()) {
        Prop(PokyhIcons.room, t.room) {
            if (origRoom.isNotEmpty() && origRoom != room) Change(origRoom, room)
            else Text(room.ifEmpty { origRoom }, style = ValueStyle, color = colors.textPrimary)
        }
    }
    if (classes.isNotEmpty()) {
        Prop(PokyhIcons.classMembers, t.classes) { Text(classes.joinToString(", "), style = ValueStyle, color = colors.textPrimary) }
    }
    if (group.isNotEmpty()) {
        Prop(PokyhIcons.classMembers, t.group) { Text(group, style = ValueStyle, color = colors.textPrimary) }
    }
}

/** The new value, and the one it replaces struck through under it. */
@Composable
private fun Change(from: String, to: String) {
    val colors = PokyhTheme.colors
    Text(to.ifEmpty { "–" }, style = ValueStyle, color = colors.textPrimary)
    Text(from, style = SmallStyle, color = colors.textTertiary, textDecoration = TextDecoration.LineThrough)
}

/**
 * "Abwesend": the status (Vorentschuldigung / Entschuldigt / Unentschuldigt), from when to when and
 * why. An unexcused absence whose excuse went to the class teacher says so until the school marks
 * it excused.
 */
@Composable
private fun AbsenceProp(absence: LessonAbsence, sent: UntisClient.SentExcuse?) {
    val colors = PokyhTheme.colors
    val t = strings().timetable
    val statusColor = when {
        sent != null -> PokyhDecorative.butter(colors.isDark).ink
        absence.mark == AbsenceMark.ABSENT -> colors.dangerText
        else -> PokyhDecorative.sage(colors.isDark).ink
    }
    val reasons = absence.covering.mapNotNull { it.reasonName?.takeIf { r -> r.isNotBlank() } }.distinct()
    val sentDate = sent?.let { s ->
        val l = I18n.lang.value
        val pattern = java.time.format.DateTimeFormatter.ofPattern(if (l == dev.plattnericus.pokyh.core.i18n.Lang.De) "d. MMM" else "d MMM", l.locale)
        runCatching { java.time.OffsetDateTime.parse(s.sentAt).format(pattern) }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(s.sentAt.take(19)).format(pattern) }.getOrNull()
    }
    Prop(if (sent != null) PokyhIcons.sendMessage else PokyhIcons.lessonAbsent, t.absent) {
        Text(
            text = if (sent != null) t.markSent else absence.mark.label,
            style = ValueStyle.copy(fontWeight = FontWeight.SemiBold),
            color = statusColor,
        )
        val range = absence.covering.joinToString(", ") { AbsenceMarks.rangeText(it) }
        Text(
            if (reasons.isEmpty()) range else "$range · ${reasons.joinToString(", ")}",
            style = SmallStyle,
            color = colors.textSecondary,
        )
        if (sent != null && sent.recipients.isNotEmpty()) {
            val names = sent.recipients.joinToString(", ")
            Text(if (sentDate != null) t.sentTo(names, sentDate) else names, style = SmallStyle, color = colors.textSecondary)
        }
    }
}

/** "Stattdessen" from the cancelled original, "Statt" from the replacement: a section like the notes. */
@Composable
private fun InsteadSection(other: TimetableEntry) {
    val colors = PokyhTheme.colors
    val t = strings().timetable
    val cancelled = other.isCancelled
    val meta = listOf(other.teacherName, other.roomName).filter { it.isNotEmpty() }.joinToString(" · ")
    TextSection(PokyhIcons.replacement, if (cancelled) t.insteadShort else t.instead) {
        Text(
            buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = if (cancelled) colors.textTertiary else colors.textPrimary,
                        textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                ) { append(other.subjectLong.ifEmpty { other.subjectName.ifEmpty { other.note ?: "–" } }) }
                if (meta.isNotEmpty()) withStyle(SpanStyle(color = colors.textSecondary)) { append(" · $meta") }
            },
            style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
        )
    }
}
