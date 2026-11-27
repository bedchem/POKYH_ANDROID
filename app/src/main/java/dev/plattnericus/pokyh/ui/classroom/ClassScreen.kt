@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.classroom

import dev.plattnericus.pokyh.ui.components.PixelState
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.components.PixelSpriteArt
import dev.plattnericus.pokyh.ui.classreg.ClassregEntryList
import dev.plattnericus.pokyh.ui.classreg.WebSection
import dev.plattnericus.pokyh.ui.classreg.WebPageHeader
import dev.plattnericus.pokyh.ui.theme.DecorativeTone
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.SmoothCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn as animateFadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.toLocalDate
import kotlinx.datetime.isoDayNumber
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.ApiClassMember
import dev.plattnericus.pokyh.data.model.ClassService
import dev.plattnericus.pokyh.data.model.ClassregEvent
import dev.plattnericus.pokyh.data.model.HomeworkItem
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.ui.components.BackendUnavailableView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.InitialAvatar
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSectionHeader
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.accentSurface
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.nestedSurface
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import dev.plattnericus.pokyh.core.i18n.strings
import kotlinx.datetime.plus

private val Weekdays: List<String> get() = dev.plattnericus.pokyh.core.i18n.I18n.s.common.weekdaysShort
private val MonthsShort: List<String> get() = dev.plattnericus.pokyh.core.i18n.I18n.s.common.monthsShort

/**
 * Klasse — the web frontend's class page, rebuilt for the phone.
 *
 * Same sections in the same order as `app/class/page.tsx` (github.com/bedchem/pokyh-frontend):
 * the class header, new class-register entries (last 3 months), open absences, Klassendienste,
 * Prüfungen and Hausaufgaben split by this/next week, and only then the member list.
 *
 * Every section renders even when it has nothing to show — the empty line is the content. That's
 * intentional: a class page that hides its empty halves changes shape week to week, and you
 * can't tell "nothing due" from "didn't load".
 */
@Composable
fun ClassScreen(
    onNavigateBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: ClassViewModel = hiltViewModel(),
) {
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val klasseName = viewModel.klasseName
    val t = strings().tasks

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = { PokyhTopBar(title = null, nav = TopBarNav.Back(onNavigateBack)) },
    ) { innerPadding ->
        // The web's class page (app/[lang]/(app)/class/page.tsx), one column on phones: the
        // register's last three months, the class duties, the open absences, then this and next
        // week's exams and homework.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(bottom = 80.dp),
        ) {
            WebPageHeader(
                title = if (klasseName.isNotEmpty()) t.klasseNamed(klasseName) else t.myClass,
                subtitle = strings().common.schoolYear(viewModel.schoolYear),
                modifier = Modifier.fadeIn(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(48.dp)) {
                WebSection(
                    title = "${t.classreg} · ${t.last3Months.replaceFirstChar { it.lowercase() }}",
                    modifier = Modifier.fadeIn(),
                    action = { SectionLink(onClick = { onNavigate(PokyhDestinations.CLASSREG_EVENTS) }) },
                ) {
                    ListBody(
                        items = overview.recentEvents,
                        loading = overview.loading,
                        empty = if (overview.eventsUnknown) OFFLINE_NOT_STORED else t.noClassreg3,
                        failed = overview.eventsUnknown,
                    ) { ClassregEntryList(overview.recentEvents) }
                }

                WebSection(title = t.services, modifier = Modifier.fadeIn(40)) {
                    ListBody(
                        items = overview.classServices,
                        loading = overview.loading,
                        empty = if (overview.servicesUnknown) OFFLINE_NOT_STORED else t.noServices,
                        failed = overview.servicesUnknown,
                    ) {
                        WebList(overview.classServices) { i, service -> ClassServiceRow(service, i) }
                    }
                }

                WebSection(
                    title = t.openAbsences,
                    modifier = Modifier.fadeIn(80),
                    action = { SectionLink(onClick = { onNavigate(PokyhDestinations.ABSENCES) }) },
                ) {
                    val shown = overview.openAbsences.take(4)
                    ListBody(
                        items = shown,
                        loading = overview.loading,
                        empty = if (overview.absencesUnknown) OFFLINE_NOT_STORED else t.noOpenAbsences,
                        failed = overview.absencesUnknown,
                    ) {
                        WebList(shown, footer = (overview.openAbsences.size - 4).takeIf { it > 0 }?.let { t.more(it) }) { _, a -> OpenAbsenceRow(a) }
                    }
                }

                val monday = remember { dev.plattnericus.pokyh.core.util.schoolWeekMonday() }
                val nextMonday = monday.plus(7, kotlinx.datetime.DateTimeUnit.DAY)
                WebSection(
                    title = t.exams,
                    modifier = Modifier.fadeIn(120),
                    titleIcon = { Icon(PokyhIcons.classExams, contentDescription = null, tint = PokyhDecorative.butter(PokyhTheme.colors.isDark).ink, modifier = Modifier.size(22.dp)) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        WeekBlock(strings().timetable.thisWeek, weekRange(monday), overview.loading, overview.thisWeekUnknown, overview.examsThisWeek, t.noExamsThisWeek) { _, e -> ExamRow(e) }
                        WeekBlock(strings().timetable.nextWeek, weekRange(nextMonday), overview.loading, overview.nextWeekUnknown, overview.examsNextWeek, t.noExamsNextWeek) { _, e -> ExamRow(e) }
                    }
                }

                WebSection(
                    title = t.homework,
                    modifier = Modifier.fadeIn(160),
                    titleIcon = { Icon(PokyhIcons.classHomework, contentDescription = null, tint = PokyhDecorative.sage(PokyhTheme.colors.isDark).ink, modifier = Modifier.size(22.dp)) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        WeekBlock(strings().timetable.thisWeek, weekRange(monday), overview.loading, overview.homeworkUnknown, overview.homeworkThisWeek, t.noHomeworkThisWeek) { _, h -> HomeworkRow(h) }
                        WeekBlock(strings().timetable.nextWeek, weekRange(nextMonday), overview.loading, overview.homeworkUnknown, overview.homeworkNextWeek, t.noHomeworkNextWeek) { _, h -> HomeworkRow(h) }
                    }
                }
            }
        }
    }
}

/** "27. Okt. – 31. Okt.": the school week from its Monday, as the web's weekRange. */
private fun weekRange(monday: kotlinx.datetime.LocalDate): String {
    val c = dev.plattnericus.pokyh.core.i18n.I18n.s.common
    val friday = monday.plus(4, kotlinx.datetime.DateTimeUnit.DAY)
    return c.dayMonthShort(monday.dayOfMonth, monday.monthNumber) + " – " + c.dayMonthShort(friday.dayOfMonth, friday.monthNumber)
}

// ── The web's pieces ────────────────────────────────────────────────────────

/** The web's `SectionLink`: "Alle" and a caret, 14sp semibold in ink-2. */
@Composable
private fun SectionLink(onClick: () -> Unit) {
    Row(
        Modifier.clip(PokyhShapes.pill).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(strings().tasks.all, style = PokyhType.callout.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = PokyhTheme.colors.textSecondary)
        Icon(PokyhIcons.chevronRight, contentDescription = null, tint = PokyhTheme.colors.textSecondary, modifier = Modifier.size(14.dp))
    }
}

/** The web's `ListBody`: a skeleton while loading, a pixel state when empty, else the list. */
@Composable
private fun <T> ListBody(items: List<T>, loading: Boolean, empty: String, failed: Boolean, content: @Composable () -> Unit) {
    when {
        items.isEmpty() && loading -> LoadingLines()
        items.isEmpty() -> Box(Modifier.fillMaxWidth().clip(SmoothCornerShape(24.dp)).background(PokyhTheme.colors.card)) {
            PixelState(title = empty, sprite = if (failed) PixelSprite.Unplugged else PixelSprite.Empty, compact = true)
        }
        else -> content()
    }
}

/** Rows on one surface (the web's `ul.squircle rounded-[24px] bg-surface p-2`), thin rules between them. */
@Composable
private fun <T> WebList(items: List<T>, footer: String? = null, row: @Composable (Int, T) -> Unit) {
    val colors = PokyhTheme.colors
    Column(Modifier.fillMaxWidth().clip(SmoothCornerShape(24.dp)).background(colors.card).padding(8.dp)) {
        items.forEachIndexed { i, item ->
            if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(colors.line))
            Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) { row(i, item) }
        }
        if (footer != null) {
            Text(footer, style = PokyhType.callout.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textSecondary, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp))
        }
    }
}

/**
 * One week of exams or homework (the web's `weekBlock`): its name and dates over the list; empty,
 * a small sun and the sentence on the surface.
 */
@Composable
private fun <T> WeekBlock(label: String, dates: String, loading: Boolean, failed: Boolean, items: List<T>, emptyText: String, row: @Composable (Int, T) -> Unit) {
    val colors = PokyhTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(label, style = PokyhType.callout.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(dates, style = PokyhType.footnote.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold).monospacedDigits(), color = colors.textTertiary)
        }
        when {
            loading && items.isEmpty() -> LoadingLines()
            items.isEmpty() -> Row(
                Modifier.fillMaxWidth().clip(SmoothCornerShape(20.dp)).background(colors.card).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PixelSpriteArt(if (failed) PixelSprite.Unplugged else PixelSprite.Sun, width = 60.dp)
                Text(if (failed) OFFLINE_NOT_STORED else emptyText, style = PokyhType.callout.copy(fontSize = 14.sp), color = colors.textSecondary)
            }
            else -> WebList(items, row = row)
        }
    }
}

/** A loading list's skeleton. */
@Composable
private fun LoadingLines() {
    Column(
        Modifier.fillMaxWidth().clip(SmoothCornerShape(24.dp)).background(PokyhTheme.colors.card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        dev.plattnericus.pokyh.ui.components.SkeletonBlock(height = 16.dp, width = 180.dp)
        dev.plattnericus.pokyh.ui.components.SkeletonBlock(height = 14.dp, width = 120.dp)
    }
}

/** The web's `DayTile`: the weekday over the day of the month, 48dp, in a tone. */
@Composable
private fun DayTile(date: kotlinx.datetime.LocalDate, tone: DecorativeTone) {
    Column(
        Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tone.fill),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            Weekdays[(date.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)].replace(".", "").uppercase(),
            style = PokyhType.caption2.copy(fontSize = 10.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em),
            color = tone.ink,
            modifier = Modifier.alpha(0.8f),
        )
        Spacer(Modifier.height(4.dp))
        Text("${date.dayOfMonth}", style = PokyhType.headline.copy(fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em), color = tone.ink)
    }
}

// ── Rows ────────────────────────────────────────────────────────────────────

/** An unexcused absence: a blush day tile, the dates, what it was, and "Offen". */
@Composable
private fun OpenAbsenceRow(absence: AbsenceEntry) {
    val colors = PokyhTheme.colors
    val blush = PokyhDecorative.blush(colors.isDark)
    val range = if (absence.startDate != absence.endDate) "${Fmt.dateShort(absence.startDate)} – ${Fmt.dateShort(absence.endDate)}" else Fmt.dateShort(absence.startDate)
    val sub = absence.subjectName?.takeIf { it.isNotEmpty() } ?: absence.reasonName?.takeIf { it.isNotEmpty() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DayTile(absence.startDate.toLocalDate(), blush)
        Column(Modifier.weight(1f)) {
            Text(range, style = PokyhType.headline.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold).monospacedDigits(), color = colors.textPrimary)
            if (sub != null) Text(sub, style = PokyhType.callout.copy(fontSize = 13.5.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier.height(28.dp).clip(PokyhShapes.pill).background(blush.fill).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(PokyhIcons.absenceOpen, contentDescription = null, tint = blush.ink, modifier = Modifier.size(14.dp))
            Text(strings().tasks.open, style = PokyhType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold), color = blush.ink)
        }
    }
}

/** An exam: a butter day tile, the subject, the time and room, its note. */
@Composable
private fun ExamRow(exam: TimetableEntry) {
    val colors = PokyhTheme.colors
    val room = exam.roomName.takeIf { it.isNotEmpty() }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        DayTile(exam.date.toLocalDate(), PokyhDecorative.butter(colors.isDark))
        Column(Modifier.weight(1f)) {
            Text(exam.subjectLong.ifEmpty { exam.subjectName.ifEmpty { strings().tasks.exam } }, style = PokyhType.headline.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            Text(
                "${Fmt.time(exam.startTime)} – ${Fmt.time(exam.endTime)}" + (if (room != null) " · $room" else ""),
                style = PokyhType.callout.copy(fontSize = 13.5.sp).monospacedDigits(),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            exam.examDescription?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = PokyhType.callout.copy(fontSize = 13.5.sp), color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A class duty: the holder's initials in a circle (periwinkle and sage by turns), the name, the duty. */
@Composable
private fun ClassServiceRow(service: ClassService, index: Int) {
    val colors = PokyhTheme.colors
    val tone = if (index % 2 == 1) PokyhDecorative.sage(colors.isDark) else PokyhDecorative.periwinkle(colors.isDark)
    val name = service.personName.ifEmpty { "-" }
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(PokyhShapes.pill).background(tone.fill), contentAlignment = Alignment.Center) {
            Text(initials, style = PokyhType.callout.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = tone.ink)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = PokyhType.headline.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (service.duty.isNotEmpty()) Text(service.duty, style = PokyhType.callout.copy(fontSize = 13.5.sp), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Homework: a sage pencil tile, the subject, the task, and when it is due. */
@Composable
private fun HomeworkRow(homework: HomeworkItem) {
    val colors = PokyhTheme.colors
    val sage = PokyhDecorative.sage(colors.isDark)
    val due = homework.dueDate.toLocalDate()
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(sage.fill), contentAlignment = Alignment.Center) {
            Icon(PokyhIcons.classHomework, contentDescription = null, tint = sage.ink, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(homework.subject.ifEmpty { "-" }, style = PokyhType.headline.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            if (homework.text.isNotEmpty()) {
                Text(homework.text, style = PokyhType.callout.copy(fontSize = 14.sp), color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
            }
            val c = strings().common
            Text(
                "${strings().tasks.due}: ${Weekdays[(due.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)]} ${c.dayMonthShort(due.dayOfMonth, due.monthNumber)}",
                style = PokyhType.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                color = colors.textSecondary,
                modifier = Modifier.padding(top = 6.dp).clip(PokyhShapes.pill).background(colors.cardAlt).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

/** Empty-line text for a section whose source failed offline with nothing stored. */
private val OFFLINE_NOT_STORED: String get() = dev.plattnericus.pokyh.core.i18n.I18n.s.tasks.offlineNotStored
