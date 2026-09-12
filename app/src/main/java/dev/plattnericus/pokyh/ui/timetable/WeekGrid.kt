package dev.plattnericus.pokyh.ui.timetable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.plattnericus.pokyh.ui.components.DiagonalStripes
import dev.plattnericus.pokyh.ui.components.SpecialDayCard
import dev.plattnericus.pokyh.ui.components.SpecialDaySpec
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.pressable
import dev.plattnericus.pokyh.ui.theme.softenedFill
import dev.plattnericus.pokyh.ui.theme.subjectColor
import kotlinx.datetime.LocalDate

/**
 * `WeekGrid`/`GridLessonCell` (TimetableView.swift), ported. Six day columns (Mo–Sa) against a
 * fixed 10-period time axis — geometry constants ([PX_PER_MINUTE]/[GRID_GUTTER_DP]/
 * [TIMETABLE_PERIODS]) come from `TimetableSlots.kt` so every screen agrees on the same pixel math.
 *
 * Unlike iOS (which threads a `GeometryReader`-measured width down from the pager to avoid a
 * measurement pass per scrolled page), this simply measures its own width once via
 * [BoxWithConstraints] — Compose's layout pass is cheap enough per-page that the extra
 * indirection iOS needs isn't necessary here.
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
) {
    BoxWithConstraints(modifier) {
        val gutter = GRID_GUTTER_DP.dp
        val colW = (maxWidth - gutter - 10.dp) / 6

        val daySlots = remember(dayEntries) { (0 until 6).map { d -> TimetableSlots.buildSlots(dayEntries.getOrElse(d) { emptyList() }) } }
        val dayKinds = remember(dayEntries, anyDayHasEntries) {
            (0 until 6).map { d ->
                TimetableSlots.dayKind(dayEntries.getOrElse(d) { emptyList() }, daySlots[d], anyDayHasEntries, d)
            }
        }
        val allEntries = remember(dayEntries) { dayEntries.flatten() }
        val minMins = remember(allEntries) { allEntries.minOfOrNull { Fmt.minutes(it.startTime) } ?: 470 }
        val maxMins = remember(allEntries) { allEntries.maxOfOrNull { Fmt.minutes(it.endTime) } ?: 1005 }
        val totalHeight = remember(minMins, maxMins) { maxOf(380.dp, ((maxMins - minMins) * PX_PER_MINUTE).dp) }
        val visiblePeriods = remember(minMins, maxMins) { TIMETABLE_PERIODS.filter { it.endMinute > minMins && it.startMinute < maxMins } }
        val boundaryTimes = remember(visiblePeriods, minMins, maxMins) {
            val set = sortedSetOf<Int>()
            visiblePeriods.forEach { set.add(it.startMinute); set.add(it.endMinute) }
            set.filter { it >= minMins - 2 && it <= maxMins + 2 }
        }

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 5.dp)
                .padding(bottom = PokyhSpacing.xxxl),
        ) {
            WeekGridHeaderRow(colW = colW, gutter = gutter, dayLabels = dayLabels, dates = dates, todayNum = todayNum, dayNums = dayNums)
            Row(Modifier.height(totalHeight)) {
                TimeAxis(gutter = gutter, visiblePeriods = visiblePeriods, boundaryTimes = boundaryTimes, minMins = minMins, height = totalHeight)
                for (d in 0 until 6) {
                    DayColumn(
                        width = colW,
                        height = totalHeight,
                        boundaryTimes = boundaryTimes,
                        minMins = minMins,
                        kind = dayKinds[d],
                        slots = daySlots[d],
                        onTap = onTap,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekGridHeaderRow(colW: Dp, gutter: Dp, dayLabels: List<String>, dates: List<LocalDate>, todayNum: Int, dayNums: List<Int>) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Spacer(Modifier.width(gutter))
        for (d in 0 until 6) {
            val isToday = dayNums.getOrNull(d) == todayNum
            Column(
                modifier = Modifier.width(colW),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    dayLabels.getOrElse(d) { "" },
                    style = PokyhType.caption2,
                    color = if (isToday) Brand.accent else PokyhTheme.colors.textSecondary,
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(if (isToday) Brand.accent else Color.Transparent, PokyhShapes.pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${dates.getOrNull(d)?.dayOfMonth ?: ""}",
                        style = PokyhType.caption,
                        color = if (isToday) Brand.onAccent else PokyhTheme.colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeAxis(gutter: Dp, visiblePeriods: List<TimetablePeriod>, boundaryTimes: List<Int>, minMins: Int, height: Dp) {
    Box(Modifier.width(gutter).height(height)) {
        for (p in visiblePeriods) {
            val top = (((p.startMinute + p.endMinute) / 2 - minMins) * PX_PER_MINUTE).dp - 6.dp
            Text(
                "${p.number}.",
                style = PokyhType.timeAxisPeriod,
                color = PokyhTheme.colors.textTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(gutter - 4.dp).offset(y = top),
            )
        }
        for (m in boundaryTimes) {
            val top = ((m - minMins) * PX_PER_MINUTE).dp - 6.dp
            Text(
                hhmm(m),
                style = PokyhType.timeAxisBoundary,
                color = PokyhTheme.colors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(gutter - 4.dp).offset(y = top),
            )
        }
    }
}

@Composable
private fun DayColumn(
    width: Dp,
    height: Dp,
    boundaryTimes: List<Int>,
    minMins: Int,
    kind: DayKind,
    slots: List<MergedSlot>,
    onTap: (MergedSlot) -> Unit,
) {
    Box(Modifier.width(width).height(height)) {
        for (m in boundaryTimes) {
            val top = ((m - minMins) * PX_PER_MINUTE).dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .offset(y = top)
                    .background(PokyhTheme.colors.separator.copy(alpha = 0.35f)),
            )
        }
        if (kind != DayKind.NORMAL) {
            SpecialDayCard(
                spec = specialDaySpecFor(kind),
                modifier = Modifier
                    .width(width - 4.dp)
                    .height(height - 6.dp)
                    .offset(x = 2.dp, y = 3.dp),
                compact = true,
            )
        } else {
            for (slot in slots) {
                val top = ((Fmt.minutes(slot.display.startTime) - minMins) * PX_PER_MINUTE).dp
                val h = maxOf(24.dp, ((Fmt.minutes(slot.display.endTime) - Fmt.minutes(slot.display.startTime)) * PX_PER_MINUTE).dp)
                val interactionSource = remember(slot.id) { MutableInteractionSource() }
                GridLessonCell(
                    slot = slot,
                    height = h,
                    modifier = Modifier
                        .width(width - 4.dp)
                        .height(h)
                        .offset(x = 2.dp, y = top)
                        .pressable(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = null) { onTap(slot) },
                )
            }
        }
    }
}

@Composable
private fun GridLessonCell(slot: MergedSlot, height: Dp, modifier: Modifier = Modifier) {
    val d = slot.display
    val color = slotColor(slot)
    val subjectText = d.subjectName.ifEmpty { d.note ?: "—" }
    val shape = PokyhShapes.sm

    Box(
        modifier = modifier
            .clip(shape)
            .border(borderWidthFor(slot.kind), borderColorFor(slot.kind), shape),
    ) {
        when (slot.kind) {
            SlotKind.CANCELLED -> {
                Box(Modifier.fillMaxSize().background(Brand.danger.copy(alpha = 0.06f)))
                DiagonalStripes(color = Brand.danger.copy(alpha = 0.12f), modifier = Modifier.fillMaxSize())
            }
            SlotKind.EXAM -> Box(Modifier.fillMaxSize().background(Brand.warning.copy(alpha = 0.13f)))
            SlotKind.REPLACEMENT -> Box(Modifier.fillMaxSize().background(Brand.orange.copy(alpha = 0.12f)))
            SlotKind.EVENT -> Box(Modifier.fillMaxSize().background(Brand.accent.copy(alpha = 0.12f)))
            SlotKind.NORMAL -> Box(Modifier.fillMaxSize().background(PokyhTheme.colors.card))
        }

        Row(Modifier.fillMaxSize()) {
            if (slot.kind == SlotKind.NORMAL && !d.isCancelled) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(color.softenedFill()))
            }
            Column(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    subjectText,
                    style = PokyhType.gridCellSubject,
                    color = if (d.isCancelled) Brand.danger else PokyhTheme.colors.textPrimary,
                    textDecoration = if (d.isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = if (height > 32.dp) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (height > 30.dp) {
                    Text(
                        "${Fmt.time(d.startTime)}–${Fmt.time(d.endTime)}",
                        style = PokyhType.gridCellTime,
                        color = PokyhTheme.colors.textTertiary,
                        maxLines = 1,
                    )
                }
                if (height > 52.dp && d.roomName.isNotEmpty()) {
                    Text(d.roomName, style = PokyhType.gridCellRoom, color = PokyhTheme.colors.textSecondary, maxLines = 1)
                }
            }
        }

        if (height > 28.dp) {
            statusIconFor(slot)?.let { icon ->
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(11.dp),
                )
            }
        }
    }
}

private fun hhmm(totalMinutes: Int): String = "%02d:%02d".format(totalMinutes / 60, totalMinutes % 60)

private fun borderWidthFor(kind: SlotKind): Dp = if (kind == SlotKind.CANCELLED) 1.5.dp else 0.6.dp

@Composable
private fun borderColorFor(kind: SlotKind): Color = when (kind) {
    SlotKind.CANCELLED -> Brand.danger.copy(alpha = 0.6f)
    SlotKind.EXAM -> Brand.warning.copy(alpha = 0.4f)
    SlotKind.REPLACEMENT -> Brand.orange.copy(alpha = 0.4f)
    SlotKind.EVENT -> Brand.accent.copy(alpha = 0.35f)
    SlotKind.NORMAL -> PokyhTheme.colors.border
}

private fun statusIconFor(slot: MergedSlot): ImageVector? {
    val d = slot.display
    return when {
        d.isCancelled && slot.replacement != null -> PokyhIcons.replacement
        d.isCancelled -> PokyhIcons.close
        d.isExam -> PokyhIcons.document
        slot.kind == SlotKind.REPLACEMENT -> PokyhIcons.replacement
        slot.kind == SlotKind.EVENT -> PokyhIcons.timetable
        else -> null
    }
}

/** `Timetable.color(_:)` (TimetableView.swift), ported — shared with the day-mode list and the detail sheet. */
fun slotColor(slot: MergedSlot): Color {
    val d = slot.display
    return when {
        d.isCancelled -> Brand.danger
        d.isExam -> Brand.warning
        slot.kind == SlotKind.REPLACEMENT -> Brand.orange
        slot.kind == SlotKind.EVENT -> Brand.accent
        else -> subjectColor(d.subjectName)
    }
}

/** `SpecialDayCard`'s `config` table (TimetableView.swift), ported to a [SpecialDaySpec]. */
fun specialDaySpecFor(kind: DayKind): SpecialDaySpec = when (kind) {
    DayKind.HOLIDAY -> SpecialDaySpec(PokyhIcons.holiday, Brand.orange, "Ferien", "Kein Unterricht")
    DayKind.WEEKEND -> SpecialDaySpec(PokyhIcons.weekend, Brand.success, "Wochenende", "Frei")
    DayKind.ALL_CANCELLED -> SpecialDaySpec(PokyhIcons.failed, Brand.danger, "Entfall", "Alle Stunden ausgefallen")
    DayKind.ALL_REPLACEMENT -> SpecialDaySpec(PokyhIcons.replacement, Brand.accent, "Vertretung", "Tag durchgehend ersetzt")
    DayKind.FULL_DAY_EVENT -> SpecialDaySpec(PokyhIcons.timetable, Brand.accent, "Veranstaltung", "Ganztägig")
    DayKind.NORMAL -> SpecialDaySpec(PokyhIcons.timetable, Brand.accent, "", "")
}
