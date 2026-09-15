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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.untis.DayKind
import dev.plattnericus.pokyh.data.untis.GRID_GUTTER_DP
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.PX_PER_MINUTE
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.data.untis.TIMETABLE_PERIODS
import dev.plattnericus.pokyh.data.untis.TimetablePeriod
import dev.plattnericus.pokyh.data.untis.TimetableSlots
import dev.plattnericus.pokyh.data.untis.changes
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
import dev.plattnericus.pokyh.ui.theme.pressable
import dev.plattnericus.pokyh.ui.theme.smoothCorner
import dev.plattnericus.pokyh.ui.theme.statusTone
import dev.plattnericus.pokyh.ui.theme.subjectTone
import kotlinx.datetime.LocalDate

/**
 * The week grid — six day columns (Mo–Sa) against the fixed 10-period time axis. Geometry
 * constants ([PX_PER_MINUTE]/[GRID_GUTTER_DP]/[TIMETABLE_PERIODS]) come from `TimetableSlots.kt`
 * so every screen agrees on the same pixel math.
 *
 * **The look is Untis Mobile's, in POKYH's pastels.** A lesson is one soft tinted block and
 * nothing else — no outline, no stripe, no divider. The tint *is* the subject, and it does the
 * job a stripe was doing twice over; at six columns wide a 3dp bar on every cell added a visual
 * comb down the page and took width from text that had none to spare.
 *
 * With nothing else drawing edges, an outline is free to mean something, and does:
 *
 *  - **danger** — cancelled. The text is struck through as well.
 *  - **accent** — this lesson happens, but not as timetabled. The field that changed (room,
 *    teacher, subject) is drawn as a filled chip, so "different room on Thursday" is one glance,
 *    not a diff you have to reconstruct.
 *
 * A [SlotKind.REPLACEMENT] — the original cancelled outright and something else put in its place
 * — is two lessons in one time band, and is drawn as two cells **side by side**: the lesson that
 * actually happens on the leading side, the cancelled original beside it. See [ReplacementPair].
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
        val minMins = remember(allEntries) { allEntries.minOfOrNull { Fmt.minutes(it.startTime) } ?: 470 }
        val maxMins = remember(allEntries) { allEntries.maxOfOrNull { Fmt.minutes(it.endTime) } ?: 1005 }
        val totalHeight = remember(minMins, maxMins) { maxOf(380.dp, ((maxMins - minMins) * PX_PER_MINUTE).dp) }
        val visiblePeriods = remember(minMins, maxMins) {
            TIMETABLE_PERIODS.filter { it.endMinute > minMins && it.startMinute < maxMins }
        }
        val boundaryTimes = remember(visiblePeriods, minMins, maxMins) {
            val set = sortedSetOf<Int>()
            visiblePeriods.forEach { set.add(it.startMinute); set.add(it.endMinute) }
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
                        onTap = onTap,
                    )
                }
            }
        }
    }
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
 * One thing to draw in a day column, already resolved to the time range it actually occupies.
 *
 * Slots are not that. A [SlotKind.REPLACEMENT] slot is *two* lessons — the cancelled one it is
 * filed under and the one that happens — and it carries the cancelled entry as its `display`, so
 * its times are the cancelled lesson's. Laying out from slots directly is what produced a
 * replacement drawn from 07:50 when it starts at 09:30. Flattening to cells first means every
 * box on screen has one entry, one time range, and one thing it opens when tapped.
 */
private data class GridCell(
    val key: String,
    /** What a tap opens — not always the same object the cell draws. */
    val slot: MergedSlot,
    val entry: TimetableEntry,
    val kind: SlotKind,
    val startMinute: Int,
    val endMinute: Int,
    /** Drawn as a narrow colour strip instead of a text cell — see [buildGridCells]. */
    val asBar: Boolean,
) {
    val isCancelled: Boolean get() = kind == SlotKind.CANCELLED || entry.isCancelled

    fun overlaps(other: GridCell): Boolean =
        startMinute < other.endMinute && other.startMinute < endMinute
}

/**
 * Flattens a day's slots into the boxes the column draws, and decides which of them collapse to
 * a bar.
 *
 * **A cancelled lesson that shares any of its time with a lesson that does happen becomes a
 * narrow strip, and keeps its full length.** This is Untis Mobile's own handling and it is the
 * only arrangement that stays honest about both facts. The alternatives were tried and are
 * worse: stretching the replacement over the cancelled block hides the cancellation and repeats
 * the cancelled subject inside it; cutting the cancelled block off where the replacement starts
 * (what this did before) loses that the lesson was called off for the whole morning, not just
 * the part nobody covered.
 *
 * As a strip it costs about a fifth of the column, carries no text — there is nothing to read
 * about a lesson that isn't happening, and its detail sheet is one tap away — and leaves the
 * lesson you actually have to attend with the room and the type size it needs.
 *
 * A cancellation with nothing put in its place stays a full cell, struck through: there it *is*
 * the information.
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
                asBar = false,
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
                    asBar = false,
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
                asBar = false,
            )
        }
    }

    val happening = cells.filter { !it.isCancelled }
    return cells.map { cell ->
        if (cell.isCancelled && happening.any { it.overlaps(cell) }) cell.copy(asBar = true) else cell
    }
}

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
    onTap: (MergedSlot) -> Unit,
) {
    val colors = PokyhTheme.colors
    val cells = remember(slots) { buildGridCells(slots) }
    val bars = remember(cells) { cells.filter { it.asBar } }

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

                if (cell.asBar) {
                    // Strips are stacked from the trailing edge inward, so a second parallel
                    // cancellation sits beside the first rather than on top of it.
                    val lane = bars.indexOfFirst { it.key == cell.key }.coerceAtLeast(0)
                    Box(
                        modifier = Modifier
                            .width(BarWidth)
                            .height(rawHeight)
                            .offset(x = width - (BarWidth + BarGap) * (lane + 1), y = top)
                            .padding(vertical = 1.dp),
                    ) {
                        TappableCell(slot = cell.slot, onTap = onTap) { m ->
                            CancelledBar(modifier = m)
                        }
                    }
                } else {
                    // Give up exactly as much width as the strips running alongside this cell.
                    val alongside = bars.count { it.overlaps(cell) }
                    val cellWidth = (width - (BarWidth + BarGap) * alongside).coerceAtLeast(MinCellWidth)
                    val h = maxOf(MinCellHeight, rawHeight)
                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .height(h)
                            .offset(y = top)
                            .padding(vertical = 1.dp),
                    ) {
                        TappableCell(slot = cell.slot, onTap = onTap) { m ->
                            GridLessonCell(entry = cell.entry, kind = cell.kind, height = h, modifier = m)
                        }
                    }
                }
            }
        }
    }
}

/** Below this a cell can't hold even one line of text legibly, so it stops shrinking. */
private val MinCellHeight = 26.dp

/** A cell never gives up so much width to strips that its subject becomes unreadable. */
private val MinCellWidth = 28.dp

/** The strip a cancelled lesson collapses to when something else runs in its place. */
private val BarWidth = 9.dp
private val BarGap = 2.dp

/**
 * The strip itself: a solid danger fill, no text.
 *
 * Solid rather than the usual soft tint, and that is deliberate — it is 9dp wide, and a pastel
 * wash at that size is a smudge you cannot name. The one saturated shape in the column reads
 * immediately as "something was cancelled here", which is all it has to say; the rest is in the
 * detail sheet.
 */
@Composable
private fun CancelledBar(modifier: Modifier = Modifier) {
    val isDark = PokyhTheme.colors.isDark
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brand.danger.copy(alpha = if (isDark) 0.80f else 0.65f),
                smoothCorner(3.dp),
            ),
    )
}


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
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val isDark = PokyhTheme.colors.isDark
    val cancelled = kind == SlotKind.CANCELLED || entry.isCancelled
    val changes = entry.changes()
    val tone = cellTone(entry = entry, kind = kind, isDark = isDark)

    // Kept thin and semi-transparent. At 1.5dp and near-full opacity a column with two or three
    // cancellations read as a page of red boxes; the strikethrough is the primary signal and the
    // outline only has to group the cell.
    val outline: Color? = when {
        cancelled -> Brand.danger.copy(alpha = if (isDark) 0.55f else 0.40f)
        changes.any -> Brand.accent.copy(alpha = if (isDark) 0.65f else 0.50f)
        else -> null
    }

    val subjectText = entry.subjectName.ifEmpty { entry.note ?: "—" }
    val padH = if (dense) 3.dp else 5.dp

    /**
     * The lines this cell has room for, most important first.
     *
     * A standard 50-minute period is only ~41dp tall, which fits the subject and one more line —
     * and that second line used to be the teacher, unconditionally. So a room change, the thing
     * the highlight exists to announce, was drawn on a line that was never rendered at that
     * height: the cell showed a blue outline and no reason for it.
     *
     * A changed field therefore takes the second line. Nothing is lost — whichever line drops
     * out is an unchanged value, which the detail sheet still lists in full.
     */
    val secondaryLines = buildList {
        val teacher = entry.teacherName to changes.teacherChanged
        val room = entry.roomName to changes.roomChanged
        if (changes.roomChanged && !changes.teacherChanged) {
            add(room to PokyhType.gridCellTime)
            add(teacher to PokyhType.gridCellRoom)
        } else {
            add(teacher to PokyhType.gridCellTime)
            add(room to PokyhType.gridCellRoom)
        }
    }.filter { (value, _) -> value.first.isNotEmpty() }
    val roomForLines = when {
        height > 52.dp -> 2
        height > 34.dp -> 1
        else -> 0
    }

    Box(
        modifier = modifier
            .clip(CellShape)
            .background(tone.fill, CellShape)
            .then(
                if (outline != null) Modifier.border(1.dp, outline, CellShape) else Modifier,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = padH, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            ChangeableLine(
                text = subjectText,
                highlighted = changes.subjectChanged,
                style = PokyhType.gridCellSubject,
                color = tone.ink,
                struck = cancelled,
            )
            secondaryLines.take(roomForLines).forEach { (value, style) ->
                val (text, highlighted) = value
                ChangeableLine(
                    text = text,
                    highlighted = highlighted,
                    style = style,
                    color = tone.ink.copy(alpha = 0.78f),
                    struck = cancelled,
                )
            }
        }

    }
}

/**
 * One line of a cell. When the field is the one that *changed* it is drawn as a filled chip
 * rather than plain text — Untis Mobile's convention, and the fastest read there is of "this
 * lesson is in a different room today".
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
    text: String,
    highlighted: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    struck: Boolean,
) {
    if (highlighted) {
        Text(
            text = text,
            style = style,
            color = Brand.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(Brand.accent, smoothCorner(4.dp))
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
