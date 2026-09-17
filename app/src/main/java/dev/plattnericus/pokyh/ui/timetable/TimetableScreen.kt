@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package dev.plattnericus.pokyh.ui.timetable

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithContent
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
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.untis.AbsenceMark
import dev.plattnericus.pokyh.data.untis.AbsenceMarks
import dev.plattnericus.pokyh.data.untis.DayKind
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.data.untis.TimetableSlots
import dev.plattnericus.pokyh.data.untis.changes
import dev.plattnericus.pokyh.ui.components.AsOfLabel
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.OfflineStateView
import dev.plattnericus.pokyh.ui.components.PokyhDayPills
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhInlineNotice
import dev.plattnericus.pokyh.ui.components.PokyhMenuButton
import dev.plattnericus.pokyh.ui.components.PokyhSegmentedControl
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTileRow
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.SpecialDayCard
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.components.TileRowSkeleton
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.components.WeekGridSkeleton
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.profile.CurrentUserAvatar
import dev.plattnericus.pokyh.ui.profile.rememberUnreadMessageCount
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits

import dev.plattnericus.pokyh.ui.theme.appBackground

import dev.plattnericus.pokyh.ui.theme.fadeIn

import dev.plattnericus.pokyh.ui.theme.SubjectHues
import dev.plattnericus.pokyh.ui.theme.statusTone
import dev.plattnericus.pokyh.ui.theme.subjectTone
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
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
    val absences by viewModel.absences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    var yearMenuExpanded by remember { mutableStateOf(false) }

    // Opens on the ViewModel's week, which is not always this week — "Im Stundenplan ansehen"
    // from the Abwesenheiten screen sets it before the screen is shown.
    val pagerState = rememberPagerState(
        initialPage = (ui.weekOffset + TIMETABLE_PAGE_SPAN).coerceIn(0, TIMETABLE_PAGE_COUNT - 1),
        pageCount = { TIMETABLE_PAGE_COUNT },
    )

    // Pager settles on a page (swipe OR a programmatic animateScrollToPage below) -> tell the
    // ViewModel, which updates the header + (re)loads/prefetches that week.
    // The first emission is skipped: it is only the page the pager was restored at, and reporting
    // it would overwrite a week the ViewModel was sent to while this screen was not shown. The
    // effect below scrolls the pager to the ViewModel's week instead.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            viewModel.onWeekPageVisible(page - TIMETABLE_PAGE_SPAN)
        }
    }
    // Header chevrons / "Heute" change `ui.weekOffset` directly -> drive the pager to match.
    // Guarded to week-mode only: the pager isn't part of the tree while in day mode.
    LaunchedEffect(ui.weekOffset, ui.mode) {
        if (ui.mode != TimetableMode.WEEK) return@LaunchedEffect
        val target = (ui.weekOffset + TIMETABLE_PAGE_SPAN).coerceIn(0, TIMETABLE_PAGE_COUNT - 1)
        // A year-picker jump spans hundreds of pages — animating that would load every week on
        // the way, so only a step to a neighbouring week slides.
        when {
            pagerState.currentPage == target -> Unit
            abs(pagerState.currentPage - target) > 2 -> pagerState.scrollToPage(target)
            else -> pagerState.animateScrollToPage(target)
        }
    }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Stundenplan",
                nav = TopBarNav.None,
                actions = {
                    val shownYear = viewModel.schoolYearOf(ui.weekOffset)
                    Box {
                        PokyhMenuButton(
                            label = "$shownYear/${(shownYear + 1) % 100}",
                            expanded = yearMenuExpanded,
                            onClick = { yearMenuExpanded = true },
                        )
                        DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                            viewModel.availableSchoolYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y/${(y + 1) % 100}", style = PokyhType.body) },
                                    trailingIcon = {
                                        if (y == shownYear) {
                                            Icon(
                                                imageVector = PokyhIcons.check,
                                                contentDescription = null,
                                                tint = PokyhTheme.colors.accentText,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = {
                                        yearMenuExpanded = false
                                        viewModel.selectSchoolYear(y)
                                    },
                                )
                            }
                        }
                    }
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
                        unreadMessages = rememberUnreadMessageCount(),
                        onMessages = { onNavigate(PokyhDestinations.MESSAGES) },
                        onProfile = { onNavigate(PokyhDestinations.PROFILE) },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            val shownPage = pages[ui.weekOffset] as? WeekPageState.Data
            WeekHeader(
                offset = ui.weekOffset,
                rangeText = viewModel.rangeText(ui.weekOffset),
                weekNumber = viewModel.weekNumber(ui.weekOffset),
                savedAt = shownPage?.savedAt ?: 0L,
                stale = shownPage?.stale == true,
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
                        weekNumber = viewModel.weekNumber(offset),
                        onTap = viewModel::showDetail,
                        onRetry = { viewModel.retryWeek(offset) },
                        absences = absences[viewModel.schoolYearOf(offset)].orEmpty(),
                    )
                }
            } else {
                DayModeContent(
                    viewModel = viewModel,
                    ui = ui,
                    pages = pages,
                    absences = absences[viewModel.schoolYearOf(ui.weekOffset)].orEmpty(),
                )
            }
        }
    }

    detail?.let { slot ->
        // Resolved here rather than inside the sheet so the sheet stays a pure renderer, and so
        // it re-resolves if the backend's key list lands while the sheet is already open.
        val subjectImageKeys by viewModel.subjectImageKeys.collectAsStateWithLifecycle()
        val imageUrl = remember(slot.id, subjectImageKeys) {
            viewModel.subjectImageUrl(slot.display.subjectLong, slot.display.subjectName)
        }
        val date = slot.display.date
        val schoolYear = if ((date / 100) % 100 >= 9) date / 10000 else date / 10000 - 1
        LessonDetailSheet(
            slot = slot,
            imageUrl = imageUrl,
            imageHeader = viewModel.subjectImageHeaders(),
            onDismiss = viewModel::dismissDetail,
            absences = absences[schoolYear].orEmpty(),
        )
    }
}

// ── Week header (chevrons / range / "Heute" · KW n) ─────────────────────────

/**
 * The week stepper. Its own quiet row under the screen title rather than part of it: the title
 * says where you are in the app, this says where you are in time, and the two change on
 * different schedules.
 */
@Composable
private fun WeekHeader(
    offset: Int,
    rangeText: String,
    weekNumber: Int,
    savedAt: Long,
    stale: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
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
            // Only shows over a week restored from the disk — see [AsOfLabel].
            AsOfLabel(savedAt = savedAt, stale = stale)
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
    weekNumber: Int,
    onTap: (MergedSlot) -> Unit,
    onRetry: () -> Unit,
    absences: List<AbsenceEntry>,
) {
    when (state) {
        is WeekPageState.Loading -> WeekGridSkeleton(Modifier.fillMaxSize())
        is WeekPageState.Error -> ErrorStateView(message = state.message, onRetry = onRetry)
        is WeekPageState.Data -> {
            if (state.entries.isEmpty() && state.stale) {
                // An empty week off the disk is not proof of holidays — only a live answer is.
                OfflineStateView(title = "Stundenplan unbekannt", message = OFFLINE_WEEK_UNKNOWN, onRetry = onRetry)
            } else if (state.entries.isEmpty()) {
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
                    weekNumber = weekNumber,
                    absences = absences,
                )
            }
        }
    }
}

// ── Day mode ─────────────────────────────────────────────────────────────────

@Composable
private fun DayModeContent(
    viewModel: TimetableViewModel,
    ui: TimetableUiState,
    pages: Map<Int, WeekPageState>,
    absences: List<AbsenceEntry>,
) {
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
                is WeekPageState.Loading -> TileRowSkeleton(
                    rows = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PokyhSpacing.screenH),
                )
                is WeekPageState.Error -> ErrorStateView(message = pageState.message, onRetry = { viewModel.retryWeek(ui.weekOffset) })
                is WeekPageState.Data if pageState.entries.isEmpty() && pageState.stale ->
                    OfflineStateView(title = "Stundenplan unbekannt", message = OFFLINE_WEEK_UNKNOWN, onRetry = { viewModel.retryWeek(ui.weekOffset) })
                is WeekPageState.Data -> {
                    val dayNum = viewModel.dateNumOf(ui.weekOffset, ui.selectedDay)
                    val dayEntries = pageState.entries.filter { it.date == dayNum }
                    val anyDayHasEntries = (0 until 6).any { d -> pageState.entries.any { it.date == viewModel.dateNumOf(ui.weekOffset, d) } }
                    val kind = TimetableSlots.dayKind(dayEntries, anyDayHasEntries, ui.selectedDay)
                    if (kind == DayKind.WEEKEND) {
                        // Not "Wochenende · Frei" on a card. A Saturday with no lessons is the
                        // absence of a timetable, not an entry in it, and dressing it up as one
                        // made an empty day the loudest thing on the screen.
                        PokyhInlineNotice(
                            icon = PokyhIcons.noExam,
                            text = "Kein Unterricht",
                            tint = Brand.success,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PokyhSpacing.screenH),
                        )
                    } else if (kind != DayKind.NORMAL) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            SpecialDayCard(
                                spec = specialDaySpecFor(kind),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.screenH),
                                plate = true,
                            )
                        }
                    } else {
                        remember(pageState.entries) { SubjectHues.register(pageState.entries.map { it.subjectName }) }
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
                                val shown = slot.replacement ?: slot.display
                                val absenceMark = if (shown.isCancelled) {
                                    null
                                } else {
                                    AbsenceMarks.markFor(
                                        absences = absences,
                                        dateNum = dayNum,
                                        startMinute = Fmt.minutes(shown.startTime),
                                        endMinute = Fmt.minutes(shown.endTime),
                                        nowDateNum = viewModel.todayDateNum(),
                                        nowMinute = schoolMinuteNow(),
                                    )
                                }
                                SlotRow(
                                    slot = slot,
                                    absenceMark = absenceMark,
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

/**
 * A lesson in day mode. Same shape as Home's lesson row, so a lesson looks the same wherever it
 * appears — and the same *semantics* as the week grid: the leading stripe is the subject's
 * pastel bar, a changed field is a chip rather than prose, and a substitution shows both
 * lessons.
 *
 * Where the grid puts the cancelled original *beside* the replacement (it has no vertical room
 * to spend), the list puts it underneath: the row is already full-width, and reading "statt X"
 * as a second line is more direct than a second column two thumbs wide.
 */
@Composable
private fun SlotRow(slot: MergedSlot, onClick: () -> Unit, modifier: Modifier = Modifier, absenceMark: AbsenceMark? = null) {
    val colors = PokyhTheme.colors
    val isDark = colors.isDark
    // For a substitution the lesson that actually happens leads the row; `display` is then the
    // cancelled original, which becomes the "statt …" line under it.
    val shown = slot.replacement ?: slot.display
    val original = if (slot.replacement != null) slot.display else null
    val cancelled = shown.isCancelled
    val changes = shown.changes()
    val tone = when {
        cancelled -> statusTone(Brand.danger, isDark)
        shown.isExam -> statusTone(Brand.warning, isDark)
        slot.kind == SlotKind.EVENT -> statusTone(Brand.accentSoft, isDark)
        shown.subjectName.isEmpty() -> statusTone(Brand.accent, isDark)
        else -> subjectTone(shown.subjectName, isDark)
    }

    // The same wash the week grid lays over an absence, with the label as a chip in the title.
    val absenceWash = when (absenceMark) {
        null -> Color.Transparent
        AbsenceMark.ABSENT -> Brand.danger.copy(alpha = if (isDark) 0.16f else 0.08f)
        else -> colors.textSecondary.copy(alpha = if (isDark) 0.18f else 0.12f)
    }
    PokyhTileRow(
        modifier = modifier.drawWithContent {
            drawContent()
            if (absenceMark != null) drawOutline(PokyhShapes.lg.createOutline(size, layoutDirection, this), absenceWash)
        },
        onClick = onClick,
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(4.dp).height(44.dp).background(tone.bar, PokyhShapes.xs))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                Text(
                    text = shown.subjectName.ifEmpty { shown.note ?: "—" },
                    style = PokyhType.headline,
                    color = if (cancelled) Brand.danger else colors.textPrimary,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None,
                )
                slotBadge(slot)
                if (absenceMark != null) {
                    TagChip(
                        absenceMark.label,
                        if (absenceMark == AbsenceMark.ABSENT) Brand.danger else colors.textSecondary,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
            ) {
                MetaField(shown.teacherName, changes.teacherChanged)
                if (shown.teacherName.isNotEmpty() && shown.roomName.isNotEmpty()) {
                    Text("·", style = PokyhType.footnote, color = colors.textTertiary)
                }
                MetaField(shown.roomName, changes.roomChanged)
            }
            if (original != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
                ) {
                    Icon(
                        imageVector = PokyhIcons.replacement,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "statt ${original.subjectName.ifEmpty { original.note ?: "—" }}",
                        style = PokyhType.caption,
                        color = colors.textTertiary,
                        textDecoration = TextDecoration.LineThrough,
                    )
                }
            }
        }
        Text(
            text = "${Fmt.time(shown.startTime)}\n${Fmt.time(shown.endTime)}",
            style = PokyhType.caption2.monospacedDigits(),
            color = colors.textTertiary,
            textAlign = TextAlign.End,
        )
    }
}

/** A teacher/room value, drawn as a filled accent chip when WebUntis says *this* is the field
 * that changed — the same highlight the week grid's cell uses, for the same reason. */
@Composable
private fun MetaField(text: String, changed: Boolean) {
    if (text.isEmpty()) return
    if (changed) {
        Text(
            text = text,
            style = PokyhType.caption,
            color = Brand.onAccent,
            modifier = Modifier
                .background(Brand.accent, PokyhShapes.xs)
                .padding(horizontal = PokyhSpacing.xs, vertical = 1.dp),
        )
    } else {
        Text(text, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
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

/** Shown for a week that is empty on the disk while offline — it could be holidays, or it could
 * be a week that was simply never loaded, and saying "Ferien" would be a guess. */
private const val OFFLINE_WEEK_UNKNOWN =
    "Du bist offline und für diese Woche ist kein Stundenplan gespeichert. Ob Unterricht ist, " +
        "lässt sich erst sagen, wenn du wieder Internet hast."

/** Minutes since midnight on the school's clock — the same zone the lesson times are in. */
private fun schoolMinuteNow(): Int =
    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Europe/Rome"))
        .let { it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE) }
