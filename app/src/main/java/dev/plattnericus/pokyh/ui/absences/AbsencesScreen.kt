@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.absences

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.core.util.toLocalDate
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PixelState
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhMenuButton
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhSectionHeader
import dev.plattnericus.pokyh.ui.components.PokyhToggleChip
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.components.openOrShareFile
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.slideInTrailing
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Abwesenheiten — the web app's page for the phone: the Fehlquote as the hero (how much of the
 * lessons so far was missed, excused against unexcused), the missed time per month as bars that
 * jump to the month, then the absences grouped by month. Each absence shows whether it is
 * excused in WebUntis, waiting for the class teachers (an excuse message was sent), or open.
 *
 * Missed time is worked out from the real timetable, not WebUntis' `hours` field — see
 * [AbsencesViewModel] — and shown in 60-minute hours, as on the web.
 */
@Composable
fun AbsencesScreen(
    onNavigateBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: AbsencesViewModel = hiltViewModel(),
) {
    val t = strings().absences
    val year by viewModel.year.collectAsStateWithLifecycle()
    val exact by viewModel.exact.collectAsStateWithLifecycle()
    val absences by viewModel.absences.collectAsStateWithLifecycle()
    val minutesMap by viewModel.minutesMap.collectAsStateWithLifecycle()
    val totalPossibleMins by viewModel.totalPossibleMins.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val canReport by viewModel.canReport.collectAsStateWithLifecycle()
    val letter by viewModel.letter.collectAsStateWithLifecycle()
    val excuses by viewModel.excuses.collectAsStateWithLifecycle()
    var yearMenuExpanded by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var excusing by remember { mutableStateOf<Pair<AbsenceEntry, String>?>(null) }
    var noViewer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val noPdf = strings().common.noPdfApp

    fun getMin(e: AbsenceEntry) = minutesMap[e.id] ?: (e.hours * 50)
    fun fmt(m: Int) = formatAbsenceMinutes(m, exact)

    val totalMinutes = absences.sumOf(::getMin)
    val excusedMinutes = absences.filter { it.isExcused }.sumOf(::getMin)
    val unexcusedMinutes = totalMinutes - excusedMinutes
    val rate = if (totalPossibleMins > 0) (totalMinutes.toDouble() / totalPossibleMins * 100).coerceAtMost(100.0) else 0.0
    val groups = remember(absences, minutesMap, t) { groupAbsencesByMonth(absences, minutesMap) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = t.title,
                eyebrow = strings().common.schoolYear(year),
                nav = TopBarNav.Back(onNavigateBack),
                actions = {
                    // A two-state reading of the same numbers, so a toggle chip rather than a menu.
                    PokyhToggleChip(
                        label = if (exact) t.exact else t.rounded,
                        checked = exact,
                        onToggle = viewModel::toggleExact,
                        icon = if (exact) PokyhIcons.clockExact else PokyhIcons.clock,
                    )
                    Box(modifier = Modifier.slideInTrailing()) {
                        PokyhMenuButton(
                            label = "$year/${(year + 1) % 100}",
                            expanded = yearMenuExpanded,
                            onClick = { yearMenuExpanded = true },
                        )
                        DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                            viewModel.availableYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y/${(y + 1) % 100}", style = PokyhType.body) },
                                    trailingIcon = {
                                        if (y == year) {
                                            Icon(PokyhIcons.check, contentDescription = null, tint = PokyhTheme.colors.accentText, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    onClick = {
                                        yearMenuExpanded = false
                                        viewModel.selectYear(y)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                else -> {
                    val listState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    // The list items before the months: the overview, the bars, the actions.
                    val headItems = 3
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = PokyhSpacing.screenH, end = PokyhSpacing.screenH, bottom = PokyhSpacing.xxxl),
                        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
                    ) {
                        item(key = "rate") {
                            RateCard(
                                rate = rate,
                                missed = fmt(totalMinutes),
                                possible = fmt(totalPossibleMins),
                                totalLabel = fmt(totalMinutes),
                                excusedLabel = fmt(excusedMinutes),
                                unexcusedLabel = fmt(unexcusedMinutes),
                                excusedShare = if (totalMinutes > 0) excusedMinutes.toFloat() / totalMinutes else 0f,
                                hasUnexcused = unexcusedMinutes > 0,
                                modifier = Modifier.fadeIn(),
                            )
                        }
                        item(key = "months") {
                            MonthBars(
                                year = year,
                                groups = groups,
                                fmt = ::fmt,
                                onPick = { key ->
                                    val index = groups.indexOfFirst { it.id == key }
                                    if (index >= 0) scope.launch { listState.animateScrollToItem(headItems + index) }
                                },
                                modifier = Modifier.fadeIn(30),
                            )
                        }
                        item(key = "actions") {
                            AbsenceActions(
                                canReport = canReport,
                                hasOpen = absences.any { !it.isExcused },
                                letterLoading = letter.loading,
                                letterError = letter.error ?: if (noViewer) noPdf else null,
                                onReport = { reporting = true },
                                onLetter = {
                                    noViewer = false
                                    viewModel.excuseLetter { file -> noViewer = !openOrShareFile(context, file.name, file.mimeType, file.bytes) }
                                },
                                onCalculator = {
                                    onNavigate(
                                        PokyhDestinations.absenceCalculator(
                                            absentHours = if (year == SchoolDates.currentSchoolYear) totalMinutes / 60.0 else null,
                                            totalHours = if (year == SchoolDates.currentSchoolYear && totalPossibleMins > 1) totalPossibleMins / 60.0 else null,
                                        ),
                                    )
                                },
                                modifier = Modifier.fadeIn(50),
                            )
                        }
                        if (absences.isEmpty()) {
                            item(key = "empty") {
                                PokyhCard {
                                    PixelState(title = t.emptyTitle, note = t.emptySubtitle, sprite = PixelSprite.Empty)
                                }
                            }
                        }
                        groups.forEachIndexed { idx, group ->
                            item(key = group.id) {
                                Column(modifier = Modifier.fadeIn(70 + idx * 30)) {
                                    PokyhSectionHeader(
                                        title = group.label,
                                        trailing = { PokyhLabel(fmt(group.totalMinutes)) },
                                    )
                                    Spacer(Modifier.size(PokyhSpacing.headerContent))
                                    PokyhCard(padding = 0.dp) {
                                        group.entries.sortedByDescending { it.startDate }.forEachIndexed { i, a ->
                                            if (i > 0) {
                                                Box(Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.card).height(1.dp).background(PokyhTheme.colors.separator))
                                            }
                                            val sent = if (a.isExcused) null else sentExcuseFor(a, excuses)
                                            val title = titleText(a)
                                            AbsenceRow(
                                                absence = a,
                                                label = fmt(getMin(a)),
                                                sent = sent,
                                                canExcuse = canReport && !a.isExcused && sent == null,
                                                onExcuse = { excusing = a to listOf(title, subText(a)).filter { it.isNotEmpty() }.joinToString(" · ") },
                                                onOpenMessage = { id -> onNavigate(PokyhDestinations.messageDetail(id, reply = false)) },
                                                onOpenInTimetable = { viewModel.openInTimetable(a.startDate) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (reporting) ReportAbsenceSheet(viewModel = viewModel, onDismiss = { reporting = false })
    excusing?.let { (absence, summary) ->
        ExcuseAbsenceSheet(viewModel = viewModel, absences = listOf(absence), summary = summary, onDismiss = { excusing = null })
    }
}

/** The Fehlquote, coloured by how much it is, the hours behind it and the excused share. */
@Composable
private fun RateCard(
    rate: Double,
    missed: String,
    possible: String,
    totalLabel: String,
    excusedLabel: String,
    unexcusedLabel: String,
    excusedShare: Float,
    hasUnexcused: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = strings().absences
    val colors = PokyhTheme.colors
    val rateColor = when {
        rate < 5.0 -> Brand.success
        rate < 15.0 -> PokyhDecorative.butter(colors.isDark).ink
        else -> Brand.danger
    }
    val share by animateFloatAsState(targetValue = excusedShare, animationSpec = tween(700), label = "excusedShare")
    PokyhCard(modifier = modifier) {
        PokyhLabel(t.rate, color = colors.textSecondary)
        Spacer(Modifier.size(PokyhSpacing.sm))
        val number = String.format(Locale.ROOT, "%.1f", rate).replace('.', strings().common.decimal)
        Text("$number %", style = PokyhType.statLarge.monospacedDigits(), color = rateColor)
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(t.ofLessons(missed, possible), style = PokyhType.footnote, color = colors.textSecondary)
        Spacer(Modifier.size(PokyhSpacing.xl))
        // Excused against unexcused: green for the excused share, red for the rest.
        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(PokyhShapes.pill).background(colors.cardAlt),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (share > 0f) Box(Modifier.fillMaxHeight().weight(share.coerceAtLeast(0.001f)).clip(PokyhShapes.pill).background(Brand.success))
            if (hasUnexcused) Box(Modifier.fillMaxHeight().weight((1f - share).coerceAtLeast(0.001f)).clip(PokyhShapes.pill).background(Brand.danger))
        }
        Spacer(Modifier.size(PokyhSpacing.lg))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Figure(t.totalMissed, totalLabel, dot = null, valueColor = colors.textPrimary, modifier = Modifier.weight(1f))
            Figure(t.excused, excusedLabel, dot = Brand.success, valueColor = colors.textPrimary, modifier = Modifier.weight(1f))
            Figure(t.unexcused, unexcusedLabel, dot = Brand.danger, valueColor = if (hasUnexcused) Brand.danger else colors.textPrimary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, dot: Color?, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dot != null) Box(Modifier.size(8.dp).clip(PokyhShapes.pill).background(dot))
            Text(label, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary, maxLines = 2)
        }
        Text(value, style = PokyhType.title3.monospacedDigits(), color = valueColor, maxLines = 1)
    }
}

/** September to June of the school year, missed time per month; a bar jumps to its month. */
@Composable
private fun MonthBars(
    year: Int,
    groups: List<MonthGroup>,
    fmt: (Int) -> String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    val months = strings().common.monthsShort
    val today = todayLocalDate()
    // September … June: (year, month) pairs.
    val bars = (listOf(9, 10, 11, 12).map { year to it } + (1..6).map { year + 1 to it }).map { (y, m) ->
        val key = "$y-${m.toString().padStart(2, '0')}"
        val minutes = groups.firstOrNull { it.id == key }?.totalMinutes ?: 0
        val future = LocalDate(y, m, 1) > today
        Triple(key, minutes, future) to months[m - 1].removeSuffix(".")
    }
    val max = maxOf(1, bars.maxOf { it.first.second })
    PokyhCard(modifier = modifier) {
        PokyhLabel(strings().absences.perMonth, color = colors.textSecondary)
        Spacer(Modifier.size(PokyhSpacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { i, (bar, _) ->
                val (key, minutes, future) = bar
                val has = minutes > 0
                val fraction by animateFloatAsState(
                    targetValue = if (has) maxOf(0.08f, minutes.toFloat() / max) else 0f,
                    animationSpec = tween(500, delayMillis = i * 30),
                    label = "bar$key",
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (has) Modifier.clickable { onPick(key) } else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = if (has) fmt(minutes) else "",
                        style = PokyhType.caption2.monospacedDigits(),
                        color = colors.textSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.size(4.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(if (has) Modifier.fillMaxHeight(fraction * 0.82f) else Modifier.height(4.dp))
                            .clip(PokyhShapes.xs)
                            .background(if (has) Brand.accent else if (future) colors.cardAlt.copy(alpha = 0.5f) else colors.cardAlt),
                    )
                }
            }
        }
        Spacer(Modifier.size(PokyhSpacing.sm))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bars.forEach { (bar, label) ->
                Text(
                    text = label,
                    style = PokyhType.caption2.semibold(),
                    color = if (bar.third) colors.textTertiary.copy(alpha = 0.6f) else colors.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** "Abwesenheit melden", the year's excuse letter as WebUntis prints it, and the calculator. */
@Composable
private fun AbsenceActions(
    canReport: Boolean,
    hasOpen: Boolean,
    letterLoading: Boolean,
    letterError: String?,
    onReport: () -> Unit,
    onLetter: () -> Unit,
    onCalculator: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings().absences
    val colors = PokyhTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
        if (canReport) {
            PokyhPrimaryButton(text = t.report, icon = PokyhIcons.add, onClick = onReport, modifier = Modifier.fillMaxWidth())
        }
        if (hasOpen) {
            // A butter banner, as on the web: the open absences' letter, opened in a PDF viewer.
            val tone = PokyhDecorative.butter(colors.isDark)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PokyhShapes.xl)
                    .background(tone.fill)
                    .clickable(enabled = !letterLoading, onClick = onLetter)
                    .padding(horizontal = PokyhSpacing.lg, vertical = PokyhSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.letter, style = PokyhType.headline, color = tone.ink)
                    Text(t.letterHint, style = PokyhType.footnote, color = tone.ink.copy(alpha = 0.8f))
                }
                if (letterLoading) {
                    androidx.compose.material3.CircularProgressIndicator(color = tone.ink, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(PokyhIcons.filePdf, contentDescription = null, tint = tone.ink, modifier = Modifier.size(22.dp))
                }
            }
        }
        letterError?.let { Text(it, style = PokyhType.footnote, color = Brand.danger) }
        PokyhSecondaryButton(
            text = t.calc,
            icon = PokyhIcons.absenceCalculator,
            onClick = onCalculator,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbsenceRow(
    absence: AbsenceEntry,
    label: String,
    sent: UntisClient.SentExcuse?,
    canExcuse: Boolean,
    onExcuse: () -> Unit,
    onOpenMessage: (Int) -> Unit,
    onOpenInTimetable: () -> Unit,
) {
    val t = strings().absences
    val c = strings().common
    val colors = PokyhTheme.colors
    // Excused in WebUntis, an excuse sent and waiting for the class teachers, or open.
    val tone = when {
        absence.isExcused -> PokyhDecorative.sage(colors.isDark)
        sent != null -> PokyhDecorative.butter(colors.isDark)
        else -> PokyhDecorative.blush(colors.isDark)
    }
    val start = absence.startDate.toLocalDate()
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md), verticalAlignment = Alignment.Top) {
            // The day as a tile in the status' tone.
            Column(
                modifier = Modifier.size(48.dp).clip(PokyhShapes.md).background(tone.fill),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("${start.dayOfMonth}", style = PokyhType.headline.monospacedDigits(), color = tone.ink)
                Text(
                    c.weekdaysShort[start.dayOfWeek.ordinal].uppercase(),
                    style = PokyhType.caption2.semibold(),
                    color = tone.ink.copy(alpha = 0.8f),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(titleText(absence), style = PokyhType.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(label, style = PokyhType.headline.monospacedDigits(), color = colors.textPrimary)
                }
                val sub = subText(absence)
                if (sub.isNotEmpty()) Text(sub, style = PokyhType.footnote, color = colors.textSecondary)
                val reason = absence.reasonName?.takeIf { it.isNotEmpty() }
                val note = absence.note?.takeIf { it.isNotEmpty() }
                if (reason != null || note != null) {
                    Text(
                        listOfNotNull(reason, note).joinToString(" · "),
                        style = PokyhType.footnote,
                        color = colors.textSecondary,
                    )
                }
                if (sent != null) {
                    val names = sent.recipients.joinToString(", ")
                    val date = sentDate(sent.sentAt)
                    val line = when {
                        names.isNotEmpty() && date.isNotEmpty() -> t.exSentTo(names, date)
                        names.isNotEmpty() -> t.exDoneTo(names)
                        else -> date
                    }
                    if (line.isNotEmpty()) Text(line, style = PokyhType.footnote, color = colors.textSecondary)
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            modifier = Modifier.padding(start = 48.dp + PokyhSpacing.md),
        ) {
            if (sent != null) {
                // Stays until the class teachers mark it excused in WebUntis; opens the message.
                StatusChip(
                    text = t.exSent,
                    icon = PokyhIcons.sendMessage,
                    fill = tone.fill,
                    ink = tone.ink,
                    onClick = sent.messageId?.let { id -> { onOpenMessage(id) } },
                )
            } else {
                StatusChip(
                    text = if (absence.isExcused) t.statusExcused else t.statusOpen,
                    icon = if (absence.isExcused) PokyhIcons.ok else PokyhIcons.failed,
                    fill = tone.fill,
                    ink = tone.ink,
                )
            }
            // Like WebUntis: only who may report absences (guardians, students of age) excuses them.
            if (canExcuse) {
                val p = PokyhDecorative.periwinkle(colors.isDark)
                StatusChip(text = t.exAction, icon = null, fill = p.fill, ink = p.ink, onClick = onExcuse)
            }
            StatusChip(text = t.timetableShort, icon = PokyhIcons.timetable, fill = colors.cardAlt, ink = colors.textPrimary, onClick = onOpenInTimetable)
        }
    }
}

@Composable
private fun StatusChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, fill: Color, ink: Color, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(PokyhShapes.pill)
            .background(fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
        Text(text, style = PokyhType.footnote.semibold(), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Mittwoch, 17. Dezember" for one day, "17. Dez. – 19. Dez." for several. */
@Composable
private fun titleText(a: AbsenceEntry): String {
    val c = strings().common
    val s = a.startDate.toLocalDate()
    if (a.startDate != a.endDate) {
        val e = a.endDate.toLocalDate()
        return "${c.dayMonthShort(s.dayOfMonth, s.monthNumber)} – ${c.dayMonthShort(e.dayOfMonth, e.monthNumber)}"
    }
    return "${c.weekdays[s.dayOfWeek.ordinal].replaceFirstChar { it.uppercase() }}, ${c.dayMonth(s.dayOfMonth, s.monthNumber)}"
}

private fun subText(a: AbsenceEntry): String {
    val parts = mutableListOf<String>()
    if (a.startTime != 0 || a.endTime != 0) parts.add("${absenceTime(a.startTime)} – ${absenceTime(a.endTime)}")
    a.subjectName?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    a.teacherName?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    return parts.joinToString(" · ")
}

/** WebUntis gives absence times as HHMM or as minutes of the day; "07:50" either way. */
private fun absenceTime(t: Int): String {
    if (t == 0) return ""
    val m = if (t > 2359 || t % 100 > 59) t else (t / 100) * 60 + t % 100
    return Fmt.time((m / 60) * 100 + m % 60)
}

/** "17. Dez." from the message's ISO date-time, or nothing when it can't be read. */
@Composable
private fun sentDate(iso: String): String {
    val c = strings().common
    val date = runCatching { java.time.OffsetDateTime.parse(iso).toLocalDate() }.getOrNull()
        ?: runCatching { java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(iso).toLocalDate() }.getOrNull()
        ?: return ""
    return c.dayMonthShort(date.dayOfMonth, date.monthValue)
}
