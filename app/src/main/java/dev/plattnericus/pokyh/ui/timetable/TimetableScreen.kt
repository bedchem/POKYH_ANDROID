@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package dev.plattnericus.pokyh.ui.timetable

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.material3.Icon

import androidx.compose.material3.Scaffold

import androidx.compose.material3.Text

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
import dev.plattnericus.pokyh.ui.components.PokyhDayPills
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhSegmentedControl
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTileRow
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.SpecialDayCard
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.profile.CurrentUserAvatar
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits

import dev.plattnericus.pokyh.ui.theme.appBackground

import dev.plattnericus.pokyh.ui.theme.fadeIn

import dev.plattnericus.pokyh.ui.theme.softenedFill
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/**
 * `TimetableView` (TimetableView.swift), ported. Week/day segmented control, a `HorizontalPager`
 * over the [TIMETABLE_PAGE_SPAN] window (mirroring iOS's `LazyHStack` paging ScrollView), a
 * day-mode list with a horizontal drag-to-change-day gesture, and the `.ics` export menu.
 */
@Composable
fun TimetableScreen(onNavigate: (String) -> Unit, viewModel: TimetableViewModel = hiltViewModel()) {
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
            PokyhTopBar(
                title = "Stundenplan",
                nav = TopBarNav.None,
                actions = {
                    Box {
                        PokyhIconButton(
                            icon = PokyhIcons.share,
                            contentDescription = "Exportieren",
                            onClick = { menuExpanded = true },
                            enabled = !ui.exporting,
                            tinted = true,
                        )
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Diese Woche exportieren", style = PokyhType.body) },
                                leadingIcon = { Icon(PokyhIcons.timetable, contentDescription = null) },
                                onClick = { menuExpanded = false; viewModel.exportWeek(context) },
                            )
                            DropdownMenuItem(
                                text = { Text("Prüfungen exportieren", style = PokyhType.body) },
                                leadingIcon = { Icon(PokyhIcons.school, contentDescription = null) },
                                onClick = { menuExpanded = false; viewModel.exportExams(context) },
                            )
                        }
                    }
                    TabRootActions(
                        avatarContent = { CurrentUserAvatar() },
                        onMessages = { onNavigate(PokyhDestinations.MESSAGES) },
                        onProfile = { onNavigate(PokyhDestinations.PROFILE) },
                    )
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

/**
 * The week stepper. Its own quiet row under the screen title rather than part of it: the title
 * says where you are in the app, this says where you are in time, and the two change on
 * different schedules.
 */
@Composable
private fun WeekHeader(offset: Int, rangeText: String, weekNumber: Int, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PokyhIconButton(
            icon = PokyhIcons.chevronLeft,
            contentDescription = "Vorherige Woche",
            onClick = onPrev,
            tinted = true,
            iconSize = 18.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs),
        ) {
            Text(rangeText, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
            if (offset != 0) {
                PokyhTextButton(text = "Zu heute", onClick = onToday)
            } else {
                Text(
                    text = "KW $weekNumber · Diese Woche",
                    style = PokyhType.caption,
                    color = PokyhTheme.colors.textSecondary,
                )
            }
        }
        PokyhIconButton(
            icon = PokyhIcons.chevronRight,
            contentDescription = "Nächste Woche",
            onClick = onNext,
            tinted = true,
            iconSize = 18.dp,
        )
    }
}

@Composable
private fun ModeSegmentedControl(mode: TimetableMode, onModeChange: (TimetableMode) -> Unit) {
    PokyhSegmentedControl(
        options = TimetableMode.entries,
        selected = mode,
        onSelect = onModeChange,
        label = { it.label },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.lg),
    )
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
                    // SpecialDayCard(compact = false) sizes itself — see its own file.
                    SpecialDayCard(
                        spec = specialDaySpecFor(DayKind.HOLIDAY),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.screenH),
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
                                modifier = Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.screenH),
                            )
                        }
                    } else {
                        val slots = TimetableSlots.buildSlots(dayEntries)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = PokyhSpacing.screenH,
                                end = PokyhSpacing.screenH,
                                bottom = PokyhSpacing.xxxl,
                            ),
                            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap),
                        ) {
                            itemsIndexed(slots, key = { _, s -> s.id }) { idx, slot ->
                                SlotRow(
                                    slot = slot,
                                    onClick = { viewModel.showDetail(slot) },
                                    modifier = Modifier.fadeIn(delayMillis = idx * 30),
                                )
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
    PokyhDayPills(
        count = 6,
        selectedIndex = selectedDay,
        onSelect = onSelect,
        weekdayLabel = { dayLabels.getOrElse(it) { "" } },
        dayNumberLabel = { "${dates.getOrNull(it)?.dayOfMonth ?: ""}" },
        isToday = { dayNums.getOrNull(it) == todayNum },
        modifier = Modifier
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.lg),
    )
}

// ── Day-mode row ────────────────────────────────────────────────────────────

/** Same shape as Home's lesson row, so a lesson looks the same wherever it appears. */
@Composable
private fun SlotRow(slot: MergedSlot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val d = slot.display
    val accent = slotColor(slot)

    PokyhTileRow(modifier = modifier, onClick = onClick, verticalAlignment = Alignment.Top) {
        Box(Modifier.width(4.dp).height(44.dp).background(accent.softenedFill(), PokyhShapes.xs))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                Text(
                    text = d.subjectName.ifEmpty { d.note ?: "—" },
                    style = PokyhType.headline,
                    color = if (d.isCancelled) Brand.danger else colors.textPrimary,
                    textDecoration = if (d.isCancelled) TextDecoration.LineThrough else TextDecoration.None,
                )
                slotBadge(slot)
            }
            val meta = listOf(d.teacherName, d.roomName).filter { it.isNotEmpty() }.joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = PokyhType.footnote, color = colors.textSecondary)
            }
            slot.replacement?.let { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
                ) {
                    Icon(
                        imageVector = PokyhIcons.replacement,
                        contentDescription = null,
                        tint = Brand.orange,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "Ersatz: ${r.subjectName.ifEmpty { r.note ?: "" }}",
                        style = PokyhType.caption,
                        color = Brand.orange,
                    )
                }
            }
        }
        Text(
            text = "${Fmt.time(d.startTime)}\n${Fmt.time(d.endTime)}",
            style = PokyhType.caption2.monospacedDigits(),
            color = colors.textTertiary,
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
