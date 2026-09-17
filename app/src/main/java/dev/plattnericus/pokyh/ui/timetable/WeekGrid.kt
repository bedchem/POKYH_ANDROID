package dev.plattnericus.pokyh.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.untis.AbsenceBand
import dev.plattnericus.pokyh.data.untis.AbsenceMark
import dev.plattnericus.pokyh.data.untis.AbsenceMarks
import dev.plattnericus.pokyh.data.untis.DayKind
import dev.plattnericus.pokyh.data.untis.GRID_GUTTER_DP
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.PX_PER_MINUTE
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.data.untis.TIMETABLE_PERIODS
import dev.plattnericus.pokyh.data.untis.TimetablePeriod
import dev.plattnericus.pokyh.data.untis.TimetableSlots
import dev.plattnericus.pokyh.data.untis.changes
import dev.plattnericus.pokyh.data.untis.splitChanged
import dev.plattnericus.pokyh.ui.components.PokyhFittedText
import dev.plattnericus.pokyh.ui.components.SpecialDayCard
import dev.plattnericus.pokyh.ui.components.SpecialDaySpec
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.SubjectTone
import dev.plattnericus.pokyh.ui.theme.lessonMarkIcon
import dev.plattnericus.pokyh.ui.theme.pressable
import dev.plattnericus.pokyh.ui.theme.smoothCorner
import dev.plattnericus.pokyh.ui.theme.statusTone
import dev.plattnericus.pokyh.ui.theme.subjectTone
import kotlinx.datetime.LocalDate

/**
 * The week grid — six day columns (Mo–Sa) against a time axis built from the week's own lesson
 * times. Geometry constants ([PX_PER_MINUTE]/[GRID_GUTTER_DP]) come from `TimetableSlots.kt` so
 * every screen agrees on the same pixel math; [TIMETABLE_PERIODS] supplies the period *numbers*
 * where a timetable matches it, and is dropped where it doesn't.
 *
 * **The look is Untis Mobile's, in POKYH's pastels.** A lesson is one soft tinted block and
 * nothing else — no outline, no stripe, no divider. The tint *is* the subject, and it does the
 * job a stripe was doing twice over; at six columns wide a 3dp bar on every cell added a visual
 * comb down the page and took width from text that had none to spare.
 *
 * With nothing else drawing edges, an outline is free to mean something, and does:
 *
 *  - **danger** — cancelled. The text is struck through as well.
 *  - **warning** — an exam.
 *
 * A substitution (stand-in teacher, swapped subject, different room) is not outlined: the field
 * that changed is drawn as a filled chip, and the chip already says it — see [ChangeableLine].
 *
 * Whatever else WebUntis attaches to a period — homework, a note, an exam, a file — arrives as
 * its `icons` array and is drawn in the cell's corner by [LessonMarks].
 *
 * Two lessons in one time band are drawn **side by side**, the one that happens leading; see
 * [resolveHorizontal].
 *
 * Unlike iOS (which threads a `GeometryReader`-measured width down from the pager to avoid a
 * measurement pass per scrolled page), this measures its own width once via [BoxWithConstraints]
 * — Compose's layout pass is cheap enough per page that the extra indirection isn't necessary.
 */
@Composable
fun WeekGrid(
    dayEntries: List<List<TimetableEntry>>,
    dayLabels: List<String>,
    dates: List<LocalDate>,
    todayNum: Int,
    dayNums: List<Int>,
    anyDayHasEntries: Boolean,
    onTap: (MergedSlot) -> Unit,
    modifier: Modifier = Modifier,
    weekNumber: Int? = null,
    /** The student's Abwesenheiten — drawn as [AbsenceOverlay] bands over the lessons they cover. */
    absences: List<AbsenceEntry> = emptyList(),
) {
    BoxWithConstraints(modifier) {
        val gutter = GRID_GUTTER_DP.dp
        val colGap = 3.dp
        val colW = (maxWidth - gutter - GridEdgeInset * 2 - colGap * 5) / 6

        val daySlots = remember(dayEntries) {
            (0 until 6).map { d -> TimetableSlots.buildSlots(dayEntries.getOrElse(d) { emptyList() }) }
        }
        val dayKinds = remember(dayEntries, anyDayHasEntries) {
            (0 until 6).map { d ->
                TimetableSlots.dayKind(dayEntries.getOrElse(d) { emptyList() }, daySlots[d], anyDayHasEntries, d)
            }
        }
        val allEntries = remember(dayEntries) { dayEntries.flatten() }
        val minute = rememberSchoolMinute()
        val minMins = remember(allEntries) {
            allEntries.minOfOrNull { Fmt.minutes(it.startTime) } ?: TIMETABLE_PERIODS.first().startMinute
        }
        val maxMins = remember(allEntries) {
            allEntries.maxOfOrNull { Fmt.minutes(it.endTime) } ?: TIMETABLE_PERIODS.last().endMinute
        }
        val totalHeight = remember(minMins, maxMins) { maxOf(380.dp, ((maxMins - minMins) * PX_PER_MINUTE).dp) }
        // **Numbered only when the numbers are this school's.** [TIMETABLE_PERIODS] is one
        // school's bell schedule; on a timetable that doesn't use it, "1./2./3." next to the
        // clock would be confidently wrong. If most lessons don't start on a known period
        // boundary the axis drops the numbers and shows times alone, which is right anywhere.
        val visiblePeriods = remember(allEntries, minMins, maxMins) {
            val onGrid = allEntries.count { e ->
                TIMETABLE_PERIODS.any { it.startMinute == Fmt.minutes(e.startTime) }
            }
            if (allEntries.isNotEmpty() && onGrid * 2 < allEntries.size) {
                emptyList()
            } else {
                TIMETABLE_PERIODS.filter { it.endMinute > minMins && it.startMinute < maxMins }
            }
        }
        // Rules come from the lessons themselves as well as the period table, so a cell edge
        // always lands on a line — whatever times the school actually runs.
        val boundaryTimes = remember(allEntries, visiblePeriods, minMins, maxMins) {
            val set = sortedSetOf<Int>()
            visiblePeriods.forEach { set.add(it.startMinute); set.add(it.endMinute) }
            allEntries.forEach { set.add(Fmt.minutes(it.startTime)); set.add(Fmt.minutes(it.endTime)) }
            set.filter { it >= minMins - 2 && it <= maxMins + 2 }
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GridEdgeInset)
                .padding(bottom = PokyhSpacing.xxxl),
        ) {
            WeekGridHeaderRow(
                colW = colW,
                colGap = colGap,
                gutter = gutter,
                dayLabels = dayLabels,
                dates = dates,
                todayNum = todayNum,
                dayNums = dayNums,
                weekNumber = weekNumber,
            )
            Box {
            Row(
                modifier = Modifier.height(totalHeight),
                horizontalArrangement = Arrangement.spacedBy(colGap),
            ) {
                TimeAxis(
                    gutter = gutter,
                    visiblePeriods = visiblePeriods,
                    boundaryTimes = boundaryTimes,
                    minMins = minMins,
                    height = totalHeight,
                )
                for (d in 0 until 6) {
                    DayColumn(
                        width = colW,
                        height = totalHeight,
                        boundaryTimes = boundaryTimes,
                        minMins = minMins,
                        kind = dayKinds[d],
                        slots = daySlots[d],
                        isToday = dayNums.getOrNull(d) == todayNum,
                        dayNum = dayNums.getOrNull(d) ?: 0,
                        todayNum = todayNum,
                        minute = minute,
                        onTap = onTap,
                        absences = absences,
                    )
                }
            }
            val todayIndex = dayNums.indexOf(todayNum)
            if (todayIndex in 0 until 6) {
                NowIndicator(
                    todayIndex = todayIndex,
                    minute = minute,
                    minMins = minMins,
                    maxMins = maxMins,
                    gutter = gutter,
                    colW = colW,
                    colGap = colGap,
                )
            }
            }
        }
    }
}

/**
 * Minutes since midnight on the school's clock (the zone the lesson times are in), re-read every
 * 30 seconds — shared by the now-line and the greying-out of finished lessons so both move together.
 */
@Composable
private fun rememberSchoolMinute(): Int {
    fun nowMinute(): Int = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome"))
        .let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }

    var minute by remember { mutableIntStateOf(nowMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            minute = nowMinute()
        }
    }
    return minute
}

/**
 * Where "now" is on this week's grid — the web timetable's current-time line, without its time
 * label: a faint rule across the whole week, and a stronger one on today's column.
 *
 * Only drawn while the school day is on the grid (between the first lesson's start and the last
 * one's end); before and after school there is no "where am I" to answer.
 */
@Composable
private fun NowIndicator(
    todayIndex: Int,
    minute: Int,
    minMins: Int,
    maxMins: Int,
    gutter: Dp,
    colW: Dp,
    colGap: Dp,
) {
    if (minute < minMins || minute > maxMins) return

    val lineThickness = 1.5.dp
    val y = ((minute - minMins) * PX_PER_MINUTE).dp
    val gridStart = gutter + colGap
    val columnStart = gridStart + (colW + colGap) * todayIndex

    // Faint, across all six days.
    Box(
        Modifier
            .padding(start = gridStart)
            .offset(y = y - lineThickness / 2)
            .fillMaxWidth()
            .height(lineThickness)
            .background(Brand.accent.copy(alpha = 0.35f)),
    )
    // Strong, on today's column only.
    Box(
        Modifier
            .offset(x = columnStart, y = y - lineThickness / 2)
            .width(colW)
            .height(lineThickness)
            .background(Brand.accent.copy(alpha = 0.9f)),
    )
}

/** Breathing room at the very edge of the grid, so the first/last column isn't flush to the screen. */
private val GridEdgeInset = 6.dp

// ── Header ──────────────────────────────────────────────────────────────────

/**
 * Weekday + date per column, with the calendar week and month sitting in the otherwise-empty
 * gutter above the time axis — the same use Untis makes of that corner. Today's date is an
 * accent-filled disc, which is the only fill in the header.
 */
@Composable
private fun WeekGridHeaderRow(
    colW: Dp,
    colGap: Dp,
    gutter: Dp,
    dayLabels: List<String>,
    dates: List<LocalDate>,
    todayNum: Int,
    dayNums: List<Int>,
    weekNumber: Int?,
) {
    val colors = PokyhTheme.colors
    Row(
        modifier = Modifier.padding(bottom = PokyhSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(colGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.width(gutter).padding(bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            if (weekNumber != null) {
                Text("KW $weekNumber", style = PokyhType.caption2, color = colors.textTertiary, maxLines = 1)
            }
            dates.firstOrNull()?.let { first ->
                Text(
                    text = monthShortDe(first.monthNumber),
                    style = PokyhType.caption.semibold(),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        for (d in 0 until 6) {
            val isToday = dayNums.getOrNull(d) == todayNum
            Column(
                modifier = Modifier.width(colW),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = dayLabels.getOrElse(d) { "" },
                    style = PokyhType.caption2,
                    color = if (isToday) colors.accentText else colors.textTertiary,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(if (isToday) Brand.accent else Color.Transparent, PokyhShapes.pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${dates.getOrNull(d)?.dayOfMonth ?: ""}",
                        style = PokyhType.caption.semibold(),
                        color = if (isToday) Brand.onAccent else colors.textPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun monthShortDe(monthNumber: Int): String = when (monthNumber) {
    1 -> "Jän."
    2 -> "Feb."
    3 -> "März"
    4 -> "Apr."
    5 -> "Mai"
    6 -> "Juni"
    7 -> "Juli"
    8 -> "Aug."
    9 -> "Sep."
    10 -> "Okt."
    11 -> "Nov."
    12 -> "Dez."
    else -> ""
}

// ── Time axis ───────────────────────────────────────────────────────────────

/**
 * Boundary clock times at their exact y, and the period number centred in the band between them
 * — the reading order Untis uses ("07:50 / 1. / 08:40"). The number is the larger of the two and
 * the time the quieter, because the number is what you count along and the time is a check.
 */
@Composable
private fun TimeAxis(
    gutter: Dp,
    visiblePeriods: List<TimetablePeriod>,
    boundaryTimes: List<Int>,
    minMins: Int,
    height: Dp,
) {
    val colors = PokyhTheme.colors
    Box(Modifier.width(gutter).height(height)) {
        for (p in visiblePeriods) {
            val top = (((p.startMinute + p.endMinute) / 2 - minMins) * PX_PER_MINUTE).dp - 9.dp
            Text(
                text = "${p.number}.",
                style = PokyhType.timeAxisPeriod,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(gutter - 6.dp).offset(y = top),
            )
        }
        for (m in boundaryTimes) {
            val top = ((m - minMins) * PX_PER_MINUTE).dp - 6.dp
            Text(
                text = hhmm(m),
                style = PokyhType.timeAxisBoundary,
                color = colors.textTertiary,
                textAlign = TextAlign.Start,
                maxLines = 1,
                modifier = Modifier.width(gutter - 6.dp).offset(y = top),
            )
        }
    }
}

// ── Day column ──────────────────────────────────────────────────────────────

/**
 * One thing to draw in a day column, already resolved to the time range *and* the slice of the
 * column's width it occupies.
 *
 * Slots are not that. A [SlotKind.REPLACEMENT] slot is *two* lessons — the cancelled one it is
 * filed under and the one that happens — and it carries the cancelled entry as its `display`, so
 * its times are the cancelled lesson's. Laying out from slots directly is what produced a
 * replacement drawn from 07:50 when it starts at 09:30. Flattening to cells first means every
 * box on screen has one entry, one time range, and one thing it opens when tapped.
 *
 * [start]/[width] are per-mille of the column, straight from WebUntis where it supplies them.
 */
private data class GridCell(
    val key: String,
    /** What a tap opens — not always the same object the cell draws. */
    val slot: MergedSlot,
    val entry: TimetableEntry,
    val kind: SlotKind,
    val startMinute: Int,
    val endMinute: Int,
    /** Left edge, per-mille of the column width. */
    val start: Int = 0,
    /** Width, per-mille of the column width. */
    val width: Int = PerMille,
) {
    val isCancelled: Boolean get() = kind == SlotKind.CANCELLED || entry.isCancelled

    fun overlaps(other: GridCell): Boolean =
        startMinute < other.endMinute && other.startMinute < endMinute
}

private const val PerMille = 1000

/**
 * Flattens a day's slots into the boxes the column draws.
 *
 * Every cell is a full cell — a cancelled lesson included. It used to collapse to a 9dp strip
 * whenever anything ran alongside it, and that is what this replaces: the strips were lane-
 * numbered by their position in the list rather than by what they actually overlapped, so two
 * cancellations that never shared a minute were still stacked on different sides of the column
 * (one flush right, one inset) while the lesson beside them gave up width for *both*. A column
 * with two cancellations ended up with a lesson squeezed to half width, two red slivers at
 * different depths, and a band of dead space above them where nothing was drawn at all.
 *
 * **The result is deduplicated**, and it has to be. One lesson that covers two cancelled periods
 * is filed under both of them, so `buildSlots` hands back two [SlotKind.REPLACEMENT] slots with
 * the *same* replacement — two identical cells. They used to land exactly on top of each other
 * and go unnoticed; [resolveHorizontal] gives each one its own lane, which is how a Monday with
 * one event and two cancellations turned into three slivers too narrow to draw anything in.
 */
private fun buildGridCells(slots: List<MergedSlot>): List<GridCell> {
    val cells = mutableListOf<GridCell>()

    for (slot in slots) {
        val replacement = slot.replacement
        if (slot.kind == SlotKind.REPLACEMENT && replacement != null) {
            // The lesson that happens, at its own times.
            cells += GridCell(
                key = slot.id + "-active",
                slot = MergedSlot(
                    id = slot.id + "-active",
                    display = replacement,
                    replacement = slot.display,
                    kind = SlotKind.REPLACEMENT,
                ),
                entry = replacement,
                kind = SlotKind.REPLACEMENT,
                startMinute = Fmt.minutes(replacement.startTime),
                endMinute = Fmt.minutes(replacement.endTime),
            )
            // The cancelled original gets a cell of its own ONLY when it isn't already in the
            // list as a standalone cancellation. It is whenever the replacement covers just part
            // of it (those land in different time groups, so `buildSlots` emits both); when the
            // two share an hour there is no standalone slot and this is the only chance to
            // show it.
            val standaloneExists = slots.any { it.kind == SlotKind.CANCELLED && it.display.id == slot.display.id }
            if (!standaloneExists) {
                cells += GridCell(
                    key = slot.id + "-cancelled",
                    slot = MergedSlot(
                        id = slot.id + "-cancelled",
                        display = slot.display,
                        replacement = replacement,
                        kind = SlotKind.CANCELLED,
                    ),
                    entry = slot.display,
                    kind = SlotKind.CANCELLED,
                    startMinute = Fmt.minutes(slot.display.startTime),
                    endMinute = Fmt.minutes(slot.display.endTime),
                )
            }
        } else {
            cells += GridCell(
                key = slot.id,
                slot = slot,
                entry = slot.display,
                kind = slot.kind,
                startMinute = Fmt.minutes(slot.display.startTime),
                endMinute = Fmt.minutes(slot.display.endTime),
            )
        }
    }

    return resolveHorizontal(
        cells
            // A zero-length period has no height to draw and overlaps nothing, so it would share
            // a lane with the lesson at the same minute and paint on top of it.
            .filter { it.endMinute > it.startMinute }
            .distinctBy { "${it.entry.id}-${it.startMinute}-${it.endMinute}-${it.kind}" },
    )
}

/**
 * Decides how wide each cell is and which side of the column it sits on.
 *
 * Two rules, and between them they settle every arrangement in the grid:
 *
 *  - **The lesson that happens leads.** Cancelled cells are laid out last, so they take the
 *    trailing lane and the lesson you actually have to attend keeps the leading edge. Before
 *    this the sides came from WebUntis's own `layoutStartPosition`, which puts the cancellation
 *    first whenever it starts earlier — a column where the thing to read was pushed right and
 *    the thing that isn't happening got the margin.
 *  - **A shared column splits [ActiveShare]/the rest, not down the middle.** A cancellation only
 *    has to be seen; the lesson beside it has to be *read*, and at a sixth of the screen an even
 *    split leaves neither legible.
 *
 * A cell widens into any lane that is free for its whole duration, so the common case — one
 * cancelled block with nothing overlapping it — is still a full-width cell, not a quarter of one.
 */
private fun resolveHorizontal(cells: List<GridCell>): List<GridCell> {
    // Happening first, so cancellations fill in around them rather than the other way round.
    val ordered = cells.sortedWith(
        compareBy({ it.isCancelled }, { it.startMinute }, { -(it.endMinute - it.startMinute) }),
    )
    val lanes = mutableMapOf<String, Int>()
    for (cell in ordered) {
        var lane = 0
        while (ordered.any { it.key != cell.key && lanes[it.key] == lane && it.overlaps(cell) }) lane++
        lanes[cell.key] = lane
    }
    val laneCount = (lanes.values.maxOrNull() ?: 0) + 1

    // Two lanes with the trailing one holding nothing but cancellations is the arrangement worth
    // weighting. Anything more crowded splits evenly — there is no "the important one" to favour.
    // ...and only when there is actually a lesson in the leading lane to favour. Two
    // cancellations overlapping each other and nothing else is two of the same thing, and
    // giving one of them three quarters of the column would be arbitrary.
    val trailing = ordered.filter { lanes[it.key] == 1 }
    val leading = ordered.filter { lanes[it.key] == 0 }
    val weighted = laneCount == 2 &&
        trailing.isNotEmpty() &&
        trailing.all { it.isCancelled } &&
        leading.any { !it.isCancelled }
    val edges = if (weighted) {
        listOf(0, ActiveShare, PerMille)
    } else {
        (0..laneCount).map { it * PerMille / laneCount }
    }

    return ordered.map { cell ->
        val lane = lanes.getValue(cell.key)
        var last = lane
        while (last + 1 < laneCount &&
            ordered.none { it.key != cell.key && lanes[it.key] == last + 1 && it.overlaps(cell) }
        ) {
            last++
        }
        cell.copy(start = edges[lane], width = edges[last + 1] - edges[lane])
    }
}

/** What the lesson that happens keeps when a cancellation runs alongside it. */
private const val ActiveShare = 750

/**
 * One day.
 *
 * Today is marked in the header (the filled date disc) and nowhere else. A tinted wash behind
 * the whole column was tried and removed: it sat *under* the pastel cells, so every lesson on
 * today read as a slightly different colour from the same lesson on any other day, which is
 * exactly the comparison the grid exists to support.
 *
 * An **empty Saturday draws nothing at all** — no card, no label. A day with no lessons is not
 * an event, and captioning it "Wochenende / Frei" spent a sixth of the grid's width telling you
 * something the empty column already said. The other [DayKind]s do carry information (a holiday,
 * a day cancelled outright) and keep their card.
 */
@Composable
private fun DayColumn(
    width: Dp,
    height: Dp,
    boundaryTimes: List<Int>,
    minMins: Int,
    kind: DayKind,
    slots: List<MergedSlot>,
    isToday: Boolean,
    dayNum: Int,
    todayNum: Int,
    minute: Int,
    onTap: (MergedSlot) -> Unit,
    absences: List<AbsenceEntry>,
) {
    val colors = PokyhTheme.colors
    val cells = remember(slots) { buildGridCells(slots) }
    val absenceBands = remember(cells, absences, dayNum, todayNum, minute) {
        AbsenceMarks.bands(
            absences = absences,
            dateNum = dayNum,
            lessons = cells.filterNot { it.isCancelled }.map { it.startMinute to it.endMinute },
            nowDateNum = todayNum,
            nowMinute = minute,
        )
    }

    Box(
        Modifier
            .width(width)
            .height(height),
    ) {
        for (m in boundaryTimes) {
            val top = ((m - minMins) * PX_PER_MINUTE).dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .offset(y = top)
                    .background(colors.separator.copy(alpha = 0.55f)),
            )
        }

        if (kind == DayKind.WEEKEND) {
            // Nothing. See the function doc.
        } else if (kind != DayKind.NORMAL) {
            SpecialDayCard(
                spec = specialDaySpecFor(kind),
                modifier = Modifier
                    .width(width)
                    .height(height - 4.dp)
                    .offset(y = 2.dp),
                compact = true,
            )
        } else {
            cells.forEach { cell ->
                val top = ((cell.startMinute - minMins) * PX_PER_MINUTE).dp
                val rawHeight = ((cell.endMinute - cell.startMinute) * PX_PER_MINUTE).dp
                val h = maxOf(MinCellHeight, rawHeight)
                // Both edges are computed from the per-mille span and the gap is taken off the
                // *leading* one, so the trailing lane still ends flush with the column. Taking
                // it off the width instead pushed the 25% lane half a point past the edge.
                val sharing = cell.width < PerMille
                val left = width * cell.start / PerMille + if (cell.start > 0) CellGap else 0.dp
                val right = width * (cell.start + cell.width) / PerMille
                val cellWidth = (right - left).coerceAtLeast(MinCellWidth)
                // Finished lessons (earlier days, or today once their end has passed) step back so the
                // rest of the day stands out — faded, still readable.
                val past = dayNum < todayNum || (dayNum == todayNum && cell.endMinute <= minute)
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(h)
                        .offset(x = left, y = top)
                        .padding(vertical = 1.dp)
                        .alpha(if (past) PastLessonAlpha else 1f),
                ) {
                    TappableCell(slot = cell.slot, onTap = onTap) { m ->
                        GridLessonCell(
                            entry = cell.entry,
                            kind = cell.kind,
                            height = h,
                            width = cellWidth,
                            dense = sharing,
                            modifier = m,
                        )
                    }
                }
            }
            absenceBands.forEach { band ->
                AbsenceOverlay(
                    band = band,
                    width = width,
                    top = ((band.startMinute - minMins) * PX_PER_MINUTE).dp,
                    height = maxOf(MinCellHeight, ((band.endMinute - band.startMinute) * PX_PER_MINUTE).dp),
                )
            }
        }
    }
}

/**
 * A wash over the lessons an absence covers, with its label in the middle — the way WebUntis marks
 * a Vorentschuldigung. Not clickable, so a tap still reaches the lesson underneath.
 *
 * Vorentschuldigung and Entschuldigt are a neutral grey (nothing to do); Gefehlt is tinted red,
 * because an unexcused absence is the one that still needs something from you.
 */
@Composable
private fun AbsenceOverlay(band: AbsenceBand, width: Dp, top: Dp, height: Dp) {
    val colors = PokyhTheme.colors
    val absent = band.mark == AbsenceMark.ABSENT
    val wash = if (absent) {
        Brand.danger.copy(alpha = if (colors.isDark) 0.28f else 0.18f)
    } else {
        colors.textSecondary.copy(alpha = if (colors.isDark) 0.32f else 0.26f)
    }
    val labelColor = if (absent) Brand.danger else colors.textPrimary
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .offset(y = top)
            .padding(vertical = 1.dp)
            .clip(CellShape)
            .background(wash, CellShape),
        contentAlignment = Alignment.Center,
    ) {
        PokyhFittedText(
            text = band.mark.label,
            style = PokyhType.gridCellSubject,
            color = labelColor,
            maxLines = 2,
            minScale = 0.7f,
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .background(colors.card.copy(alpha = 0.85f), smoothCorner(4.dp))
                .padding(horizontal = 3.dp, vertical = 1.5.dp),
        )
    }
}

/** How far a finished lesson fades — enough to read as "done", not so much it stops being legible. */
private const val PastLessonAlpha = 0.7f

/** Below this a cell can't hold even one line of text legibly, so it stops shrinking. */
private val MinCellHeight = 26.dp

/** A cell never shrinks past the point where even a colour block stops registering. */
private val MinCellWidth = 10.dp

/**
 * Below this a cell carries no text.
 *
 * A quarter-column is about 15dp, and `"St.t.Syst."` in 15dp is `"S…"` — a character of noise
 * where the tint, the red ring and the strikethrough have already said "cancelled", and the
 * detail sheet is one tap away. Dropping the text there is what makes the 75/25 split usable
 * instead of just narrow.
 */
private val TextMinCellWidth = 34.dp

/** The seam between two cells sharing one column. */
private val CellGap = 2.dp


/** Press-scale + click wiring, so [GridLessonCell] stays a pure renderer. */
@Composable
private fun TappableCell(
    slot: MergedSlot,
    onTap: (MergedSlot) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val interactionSource = remember(slot.id) { MutableInteractionSource() }
    content(
        modifier
            .fillMaxHeight()
            .pressable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onTap(slot) },
    )
}

// ── Cell ────────────────────────────────────────────────────────────────────

private val CellShape = PokyhShapes.sm

/**
 * One lesson block: a pastel [SubjectTone.fill] and up to three lines — subject, teacher, room —
 * each dropped as the cell gets shorter.
 *
 * Subject leads rather than teacher (which is what Untis Mobile puts first) so a lesson reads
 * the same here as it does in the Home "Heute" rows and the day list; the grid is the densest
 * view of the same objects, not a different app.
 *
 * [dense] narrows the padding for the half-width cancelled half of a [ReplacementPair].
 */
@Composable
private fun GridLessonCell(
    entry: TimetableEntry,
    kind: SlotKind,
    height: Dp,
    width: Dp,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val isDark = PokyhTheme.colors.isDark
    val cancelled = kind == SlotKind.CANCELLED || entry.isCancelled
    val changes = entry.changes()
    val tone = cellTone(entry = entry, kind = kind, isDark = isDark)

    // **A cancelled lesson is ringed in red, an exam in yellow — a changed one is not ringed at
    // all.** A different room or a stand-in teacher already gets its badge on the field itself,
    // which says *what* changed; a ring on top of that was noise. Both rings are drawn heavily
    // enough to survive a quarter-width cell.
    val outline: Color? = when {
        cancelled -> Brand.danger.copy(alpha = if (isDark) 0.85f else 0.60f)
        entry.isExam || kind == SlotKind.EXAM -> Brand.warning.copy(alpha = if (isDark) 0.95f else 0.80f)
        else -> null
    }

    val subjectText = entry.subjectName.ifEmpty { entry.note ?: "—" }
    val padH = if (dense) 3.dp else 5.dp

    /**
     * **Subject, then teacher, then room — always, whatever changed.**
     *
     * A changed field used to be promoted to the second line so it survived a short cell, and
     * that made the room jump above the teacher on exactly the cells a reader is already trying
     * to re-read. The three lines mean nothing if they are not in the same place on every cell;
     * the fix for a change that falls off the bottom is a cell tall enough to hold it, which
     * [PX_PER_MINUTE] now gives.
     */
    val secondaryLines = listOf(
        fieldParts(entry.teacherName, entry.addedTeachers, entry.originalTeacher, dense) to PokyhType.gridCellTime,
        fieldParts(entry.roomName, entry.addedRooms, entry.originalRoom, dense) to PokyhType.gridCellRoom,
    ).filter { (parts, _) -> parts.isNotEmpty() }
    val roomForLines = when {
        height > 42.dp -> 2
        height > 28.dp -> 1
        else -> 0
    }

    Box(
        modifier = modifier
            // `fillMaxSize` is load-bearing, not tidiness: the only thing that used to give this
            // Box a width was the Column inside it, so a cell narrow enough to draw no text
            // collapsed to nothing at all and the 25% lane simply vanished off the grid.
            .fillMaxSize()
            .clip(CellShape)
            .background(tone.fill, CellShape)
            .then(
                if (outline != null) Modifier.border(1.5.dp, outline, CellShape) else Modifier,
            ),
    ) {
        if (width < TextMinCellWidth) return@Box
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = padH, vertical = 2.5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            ChangeableLine(
                parts = listOf(subjectText to if (changes.subjectChanged) PartMark.Added else PartMark.Plain),
                style = PokyhType.gridCellSubject,
                color = tone.ink,
                struck = cancelled,
            )
            secondaryLines.take(roomForLines).forEach { (parts, style) ->
                ChangeableLine(
                    parts = parts,
                    style = style,
                    color = tone.ink.copy(alpha = 0.78f),
                    struck = cancelled,
                )
            }
        }

        LessonMarks(
            icons = entry.icons,
            tint = tone.ink,
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 2.dp, vertical = 2.dp),
        )
    }
}

/**
 * The little glyphs in a cell's bottom-right corner: homework set, a note on the period, an
 * attachment, an exam.
 *
 * **Drawn as an overlay in the corner, not as a line.** A cell has room for three lines and they
 * are already spoken for by subject, teacher and room; a fourth would have pushed the room off
 * again on every cell that has a mark. The corner is the one part of a lesson block that is
 * reliably empty — the room sits bottom-*left* — and it is where Untis Mobile puts the same
 * information.
 *
 * At most [MaxMarks], because the corner of a 40dp cell is not a list. Anything beyond that is
 * in the detail sheet, which lists them all with their text.
 */
@Composable
private fun LessonMarks(icons: List<String>, tint: Color, modifier: Modifier = Modifier) {
    if (icons.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        icons.take(MaxMarks).forEach { name ->
            Icon(
                imageVector = lessonMarkIcon(name),
                contentDescription = null,
                tint = tint.copy(alpha = 0.72f),
                modifier = Modifier.size(MarkSize),
            )
        }
    }
}

private const val MaxMarks = 2
private val MarkSize = 9.dp

/** How a single part of a field is drawn — see [ChangeableLine]. */
private enum class PartMark { Plain, Added, Removed }

/**
 * A comma-joined field split into the parts a cell draws, **changed ones first**.
 *
 * Two kinds of change, and both get a badge, because both are things you have to act on:
 *
 *  - **Added** — the room the lesson moved into, the teacher standing in. Blue.
 *  - **Removed** — a teacher who is out and not replaced, which WebUntis reports by dropping the
 *    name from the current list and leaving it in `original*`. Without this the cell simply had
 *    one fewer name on it and nothing said why.
 *
 * A lesson moved into an extra room arrives as `"Inf VI, a+2/05"` with only `Inf VI` flagged, and
 * the cell that has to show it is often a fraction of a column wide. Leading with the parts that
 * changed means the thing worth reading is the thing that survives the truncation; in a [dense]
 * cell the unchanged parts are dropped outright rather than eaten by an ellipsis.
 */
private fun fieldParts(
    joined: String,
    added: List<String>,
    original: String?,
    dense: Boolean,
): List<Pair<String, PartMark>> {
    val current = splitChanged(joined, added)
        .map { (text, isAdded) -> text to if (isAdded) PartMark.Added else PartMark.Plain }
    val present = current.map { it.first }
    val gone = splitChanged(original.orEmpty(), emptyList())
        .map { it.first }
        .filter { it !in present }
        .map { it to PartMark.Removed }

    val parts = (current + gone).sortedBy { it.second == PartMark.Plain }
    if (dense && parts.any { it.second != PartMark.Plain }) {
        return parts.filter { it.second != PartMark.Plain }
    }
    return parts
}

/**
 * One line of a cell. A part that *changed* is drawn as a filled chip rather than plain text —
 * Untis Mobile's convention, and the fastest read there is of "this lesson is in a different room
 * today". Parts are chipped individually, so a lesson that gained a second room highlights the
 * room it gained and leaves the one it always had alone.
 *
 * The chip is a **solid [Brand.accent] with white on it in both themes**, and that is the point:
 * every other mark in the grid is a soft tint of the subject, so the one saturated block on the
 * page is unmistakably the thing that changed. Tinting it from the cell's own palette was the
 * obvious alternative and it disappeared — a highlight the same colour family as its background
 * is not a highlight. White clears 4.5:1 on this indigo, so the smallest type in the app is
 * still legible on it.
 */
@Composable
private fun ChangeableLine(
    parts: List<Pair<String, PartMark>>,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    struck: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        parts.forEach { (text, mark) ->
            val fill = when (mark) {
                PartMark.Added -> Brand.accent
                PartMark.Removed -> Brand.danger
                PartMark.Plain -> null
            }
            if (fill != null) {
                Text(
                    text = text,
                    style = style,
                    color = Brand.onAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // A dropped name keeps its strikethrough *on* the badge: the badge says the
                    // roster changed, the rule says this is the name that left.
                    textDecoration = if (mark == PartMark.Removed) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier
                        .background(fill, smoothCorner(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.5.dp),
                )
            } else {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    textDecoration = if (struck) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The cell's pastel pair. A status (cancelled/exam/event) outranks the subject, because at grid
 * density the *state* is what you scan for; an ordinary or substituted lesson is tinted by its
 * own subject, which is what makes a column readable as a pattern of recurring blocks.
 */
@Composable
private fun cellTone(entry: TimetableEntry, kind: SlotKind, isDark: Boolean): SubjectTone = when {
    kind == SlotKind.CANCELLED || entry.isCancelled -> statusTone(Brand.danger, isDark)
    entry.isExam || kind == SlotKind.EXAM -> statusTone(Brand.warning, isDark)
    kind == SlotKind.EVENT -> statusTone(Brand.accentSoft, isDark)
    entry.subjectName.isEmpty() -> statusTone(Brand.accent, isDark)
    else -> subjectTone(entry.subjectName, isDark)
}

private fun hhmm(totalMinutes: Int): String = "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)

/** `SpecialDayCard`'s `config` table (TimetableView.swift), ported to a [SpecialDaySpec]. */
fun specialDaySpecFor(kind: DayKind): SpecialDaySpec = when (kind) {
    DayKind.HOLIDAY -> SpecialDaySpec(PokyhIcons.holiday, Brand.orange, "Ferien", "Kein Unterricht")
    DayKind.WEEKEND -> SpecialDaySpec(PokyhIcons.weekend, Brand.success, "Wochenende", "Frei")
    DayKind.ALL_CANCELLED -> SpecialDaySpec(PokyhIcons.failed, Brand.danger, "Entfall", "Alle Stunden ausgefallen")
    DayKind.ALL_REPLACEMENT -> SpecialDaySpec(PokyhIcons.replacement, Brand.accent, "Vertretung", "Tag durchgehend ersetzt")
    DayKind.FULL_DAY_EVENT -> SpecialDaySpec(PokyhIcons.timetable, Brand.accent, "Veranstaltung", "Ganztägig")
    DayKind.NORMAL -> SpecialDaySpec(PokyhIcons.timetable, Brand.accent, "", "")
}
