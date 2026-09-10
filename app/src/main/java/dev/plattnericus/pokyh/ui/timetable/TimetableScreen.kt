@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package dev.plattnericus.pokyh.ui.timetable
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.untis.DayKind
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.data.untis.TimetableSlots
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.SpecialDayCard
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.pressable
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/**
 * `TimetableView` (TimetableView.swift), ported. Week/day segmented control, a `HorizontalPager`
 * over the [TIMETABLE_PAGE_SPAN] window (mirroring iOS's `LazyHStack` paging ScrollView), a
 * day-mode list with a horizontal drag-to-change-day gesture, and the `.ics` export menu.
 */
@Composable
fun TimetableScreen(viewModel: TimetableViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = TIMETABLE_PAGE_SPAN, pageCount = { TIMETABLE_PAGE_COUNT })

    // Pager settles on a page (swipe OR a programmatic animateScrollToPage below) -> tell the
    // ViewModel, which updates the header + (re)loads/prefetches that week.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            viewModel.onWeekPageVisible(page - TIMETABLE_PAGE_SPAN)
        }
    }
    // Header chevrons / "Heute" change `ui.weekOffset` directly -> drive the pager to match.
    // Guarded to week-mode only: the pager isn't part of the tree while in day mode.
    LaunchedEffect(ui.weekOffset, ui.mode) {
        if (ui.mode != TimetableMode.WEEK) return@LaunchedEffect
        val target = (ui.weekOffset + TIMETABLE_PAGE_SPAN).coerceIn(0, TIMETABLE_PAGE_COUNT - 1)
        if (pagerState.currentPage != target) pagerState.animateScrollToPage(target)
    }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Stundenplan", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
                navigationIcon = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }, enabled = !ui.exporting) {
                            Icon(
                                PokyhIcons.square_and_arrow_up,
                                contentDescription = "Exportieren",
                                tint = if (ui.exporting) PokyhTheme.colors.textTertiary else PokyhTheme.colors.textPrimary,
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Diese Woche exportieren") },
                                leadingIcon = { Icon(PokyhIcons.calendar, contentDescription = null) },
                                onClick = { menuExpanded = false; viewModel.exportWeek(context) },
                            )
                            DropdownMenuItem(
                                text = { Text("Prüfungen exportieren") },
                                leadingIcon = { Icon(PokyhIcons.graduationcap_fill, contentDescription = null) },
                                onClick = { menuExpanded = false; viewModel.exportExams(context) },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            WeekHeader(
                offset = ui.weekOffset,
                rangeText = viewModel.rangeText(ui.weekOffset),
                weekNumber = viewModel.weekNumber(ui.weekOffset),
                onPrev = { viewModel.setWeekOffset((ui.weekOffset - 1).coerceIn(-TIMETABLE_PAGE_SPAN, TIMETABLE_PAGE_SPAN)) },
                onNext = { viewModel.setWeekOffset((ui.weekOffset + 1).coerceIn(-TIMETABLE_PAGE_SPAN, TIMETABLE_PAGE_SPAN)) },
                onToday = { viewModel.goToday() },
            )
            ModeSegmentedControl(mode = ui.mode, onModeChange = viewModel::setMode)
            HorizontalDivider(color = PokyhTheme.colors.separator, thickness = 0.5.dp)

            if (ui.mode == TimetableMode.WEEK) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val offset = page - TIMETABLE_PAGE_SPAN
                    WeekPageContent(
                        state = pages[offset] ?: WeekPageState.Loading,
                        dayLabels = viewModel.dayLabels,
                        dates = (0 until 6).map { viewModel.dateOf(offset, it) },
                        dayNums = (0 until 6).map { viewModel.dateNumOf(offset, it) },
                        todayNum = viewModel.todayDateNum(),
                        onTap = viewModel::showDetail,
                        onRetry = { viewModel.retryWeek(offset) },
                    )
                }
            } else {
                DayModeContent(viewModel = viewModel, ui = ui, pages = pages)
            }
        }
    }

    detail?.let { slot -> LessonDetailSheet(slot = slot, onDismiss = viewModel::dismissDetail) }
}

// ── Week header (chevrons / range / "Heute" · KW n) ─────────────────────────

@Composable
private fun WeekHeader(offset: Int, rangeText: String, weekNumber: Int, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(44.dp)) {
            Icon(PokyhIcons.chevron_left, contentDescription = "Vorherige Woche", tint = Brand.accent)
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(rangeText, style = PokyhType.subheadline.bold(), color = PokyhTheme.colors.textPrimary)
            if (offset != 0) {
                val interactionSource = remember { MutableInteractionSource() }
                Text(
                    "Heute",
                    style = PokyhType.caption2,
                    color = Brand.accent,
                    modifier = Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onToday),
                )
            } else {
                Text("KW $weekNumber · Diese Woche", style = PokyhType.caption2, color = Brand.accent)
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onNext, modifier = Modifier.size(44.dp)) {
            Icon(PokyhIcons.chevron_right, contentDescription = "Nächste Woche", tint = Brand.accent)
        }
    }
}

@Composable
private fun ModeSegmentedControl(mode: TimetableMode, onModeChange: (TimetableMode) -> Unit) {
    val modes = TimetableMode.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
    ) {
        modes.forEachIndexed { idx, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onModeChange(m) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Brand.accent,
                    activeContentColor = Color.White,
                    activeBorderColor = Brand.accent,
                    inactiveContainerColor = PokyhTheme.colors.surface,
                    inactiveContentColor = PokyhTheme.colors.textPrimary,
                ),
            ) {
                Text(m.label, style = PokyhType.subheadline)
            }
        }
    }
}

// ── Week mode (one pager page) ───────────────────────────────────────────────

@Composable
private fun WeekPageContent(
    state: WeekPageState,
    dayLabels: List<String>,
    dates: List<LocalDate>,
    dayNums: List<Int>,
    todayNum: Int,
    onTap: (MergedSlot) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is WeekPageState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Brand.accent)
        }
        is WeekPageState.Error -> ErrorStateView(message = state.message, onRetry = onRetry)
        is WeekPageState.Data -> {
            if (state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    // SpecialDayCard(compact = false) sizes itself (380dp) — see its own file.
                    SpecialDayCard(
                        spec = specialDaySpecFor(DayKind.HOLIDAY),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            } else {
                val dayEntries = (0 until 6).map { d -> state.entries.filter { it.date == dayNums[d] } }
                val anyDayHasEntries = dayEntries.any { it.isNotEmpty() }
                WeekGrid(
                    dayEntries = dayEntries,
                    dayLabels = dayLabels,
                    dates = dates,
                    todayNum = todayNum,
                    dayNums = dayNums,
                    anyDayHasEntries = anyDayHasEntries,
                    onTap = onTap,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ── Day mode ─────────────────────────────────────────────────────────────────

@Composable
private fun DayModeContent(viewModel: TimetableViewModel, ui: TimetableUiState, pages: Map<Int, WeekPageState>) {
    Column(Modifier.fillMaxSize()) {
        DayChipsRow(
            dayLabels = viewModel.dayLabels,
            selectedDay = ui.selectedDay,
            dates = (0 until 6).map { viewModel.dateOf(ui.weekOffset, it) },
            todayNum = viewModel.todayDateNum(),
            dayNums = (0 until 6).map { viewModel.dateNumOf(ui.weekOffset, it) },
            onSelect = viewModel::selectDay,
        )

        val pageState = pages[ui.weekOffset] ?: WeekPageState.Loading
        var dragAccumX by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumX = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragAccumX += dragAmount },
                        onDragEnd = {
                            // iOS threshold: drag further than 48pt before switching day.
                            if (abs(dragAccumX) > 48f) {
                                if (dragAccumX < 0) viewModel.goNextDay() else viewModel.goPrevDay()
                            }
                        },
                    )
                },
        ) {
            when (pageState) {
                is WeekPageState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.accent)
                }
                is WeekPageState.Error -> ErrorStateView(message = pageState.message, onRetry = { viewModel.retryWeek(ui.weekOffset) })
                is WeekPageState.Data -> {
                    val dayNum = viewModel.dateNumOf(ui.weekOffset, ui.selectedDay)
                    val dayEntries = pageState.entries.filter { it.date == dayNum }
                    val anyDayHasEntries = (0 until 6).any { d -> pageState.entries.any { it.date == viewModel.dateNumOf(ui.weekOffset, d) } }
                    val kind = TimetableSlots.dayKind(dayEntries, anyDayHasEntries, ui.selectedDay)
                    if (kind != DayKind.NORMAL) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            SpecialDayCard(
                                spec = specialDaySpecFor(kind),
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    } else {
                        val slots = TimetableSlots.buildSlots(dayEntries)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(slots, key = { _, s -> s.id }) { idx, slot ->
                                val interactionSource = remember(slot.id) { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .fadeIn(delayMillis = idx * 30)
                                        .pressable(interactionSource)
                                        .clickable(interactionSource = interactionSource, indication = null) { viewModel.showDetail(slot) },
                                ) {
                                    SlotRow(slot = slot)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChipsRow(
    dayLabels: List<String>,
    selectedDay: Int,
    dates: List<LocalDate>,
    todayNum: Int,
    dayNums: List<Int>,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0 until 6) {
            val isSelected = selectedDay == i
            val isToday = dayNums.getOrNull(i) == todayNum
            val interactionSource = remember { MutableInteractionSource() }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) Brand.accent else Color.Transparent, PokyhShapes.r10)
                    .pressable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val fg = if (isSelected) Color.White else if (isToday) Brand.accent else PokyhTheme.colors.textPrimary
                Text(dayLabels.getOrElse(i) { "" }, style = PokyhType.caption2.semibold(), color = fg)
                Text("${dates.getOrNull(i)?.dayOfMonth ?: ""}", style = PokyhType.subheadline.bold(), color = fg)
            }
        }
    }
}

// ── Day-mode row (`SlotRow`, TimetableView.swift) ───────────────────────────

@Composable
private fun SlotRow(slot: MergedSlot) {
    val d = slot.display
    val color = slotColor(slot)
    Row(
        modifier = Modifier.fillMaxWidth().cardSurface(radius = 12.dp).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 5.dp, height = 44.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    d.subjectName.ifEmpty { d.note ?: "—" },
                    style = PokyhType.subheadline.bold(),
                    color = if (d.isCancelled) Brand.danger.copy(alpha = 0.85f) else PokyhTheme.colors.textPrimary,
                    textDecoration = if (d.isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                )
                slotBadge(slot)
            }
            val meta = listOf(d.teacherName, d.roomName).filter { it.isNotEmpty() }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
            }
            slot.replacement?.let { r ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(PokyhIcons.arrow_left_arrow_right, contentDescription = null, tint = Brand.orange, modifier = Modifier.size(11.dp))
                    Text(
                        "Ersatz: ${r.subjectName.ifEmpty { r.note ?: "" }}",
                        style = PokyhType.caption2,
                        color = Brand.orange,
                    )
                }
            }
        }
        Text(
            "${Fmt.time(d.startTime)}\n${Fmt.time(d.endTime)}",
            style = PokyhType.caption2.monospacedDigits(),
            color = PokyhTheme.colors.textSecondary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun slotBadge(slot: MergedSlot) {
    val d = slot.display
    when {
        d.isCancelled -> TagChip("Entfall", Brand.danger)
        d.isExam -> TagChip("Prüfung", Brand.warning)
        slot.kind == SlotKind.REPLACEMENT -> TagChip("Vertretung", Brand.orange)
        slot.kind == SlotKind.EVENT -> TagChip("Veranstaltung", Brand.accent)
        else -> {}
    }
}
