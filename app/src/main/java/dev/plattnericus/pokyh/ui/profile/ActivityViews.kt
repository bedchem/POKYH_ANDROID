@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.data.model.ApiAccountActivity
import dev.plattnericus.pokyh.data.model.ApiActivityDay
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhInlineNotice
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.components.StatCardSkeleton
import dev.plattnericus.pokyh.ui.components.TopBarNav
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
import java.time.LocalDate
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.plattnericus.pokyh.ui.components.PixelState
import dev.plattnericus.pokyh.ui.components.SettingsType
import dev.plattnericus.pokyh.ui.components.WebButton
import dev.plattnericus.pokyh.ui.components.WebButtonVariant
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

// The profile's activity and the statistics behind it, as on the web app (profile and
// profile/stats): everything comes from GET /users/me/activity, which only shows what the POKYH
// server already keeps. Time in POKYH and the most used pages need the personal usage statistics.



// ── Aktivität im Profil ─────────────────────────────────────────────────────

/**
 * "Aktivität", as the web's profile block: its name and lead, then — once loaded — a note when
 * the personal statistics are off, four figures in two rows, the grid of days on a card with
 * what is kept under it and the legend, and the way to the statistics. When it can't be read, a
 * pixel scene with the reason and a way to try again.
 */
@Composable
fun ActivitySection(viewModel: ProfileViewModel, onOpenStats: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.activity.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadActivity() }
    if (state is ProfileViewModel.ActivityState.Unavailable) return
    val t = I18n.s.profile
    val ta = I18n.s.account
    val colors = PokyhTheme.colors

    Column(modifier.fillMaxWidth()) {
        Text(t.activity, style = SettingsType.groupTitle, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(t.activityLead, style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 24.sp), color = colors.textSecondary)
        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            is ProfileViewModel.ActivityState.Failed -> Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.card)) {
                PixelState(
                    title = t.activityFailed,
                    detail = s.reason,
                    action = { WebButton(I18n.s.common.retry, onClick = { viewModel.loadActivity(force = true) }, variant = WebButtonVariant.Quiet, icon = PokyhIcons.refresh) },
                )
            }
            else -> {
                val activity = (s as? ProfileViewModel.ActivityState.Loaded)?.activity
                if (activity != null && !activity.usageConsent) {
                    StatsOffNotice(onOpenSettings)
                    Spacer(Modifier.height(12.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure(t.daysOnline, activity?.totals?.activeDays?.toString(), Modifier.weight(1f))
                        Figure(t.logins, activity?.totals?.logins?.toString(), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Figure(t.currentStreak, activity?.let { daysText(it.totals.currentStreak) }, Modifier.weight(1f))
                        Figure(t.longestStreak, activity?.let { daysText(it.totals.longestStreak) }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(colors.card).padding(20.dp)) {
                    if (activity != null) ActivityGrid(activity.days) else ActivityGridSkeleton()
                    Spacer(Modifier.height(20.dp))
                    val r = activity?.retention
                    val kept = buildString {
                        append(if (r?.schoolYear == true) ta.activityKeptYear else ta.activityKept(r?.activeDays ?: 30))
                        if (activity?.usageConsent == true) {
                            append(' ')
                            append(if (r?.schoolYear == true) ta.activityKeptYearConsent else ta.activityKeptConsent(r?.usage ?: 30))
                        }
                    }
                    Text(kept, style = PokyhType.body.copy(fontSize = 13.sp, lineHeight = 21.sp), color = colors.textTertiary)
                    Spacer(Modifier.height(12.dp))
                    GridLegend()
                }
                if (activity != null) {
                    Spacer(Modifier.height(24.dp))
                    WebButton(t.moreStats, onClick = onOpenStats, variant = WebButtonVariant.Tone, trailingIcon = PokyhIcons.forward)
                }
            }
        }
    }
}

/** One figure on its own tile: what it counts, the number large under it. */
@Composable
private fun Figure(label: String, value: String?, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(colors.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = PokyhType.body.copy(fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold), color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value == null) {
            SkeletonBlock(height = 26.dp, width = 56.dp)
        } else {
            Text(
                value,
                style = PokyhType.body.copy(fontSize = 28.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em).monospacedDigits(),
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

/** Said while the personal statistics are off: what is missing because of it, and where they go on. */
@Composable
private fun StatsOffNotice(onOpenSettings: () -> Unit) {
    val tone = PokyhDecorative.butter(PokyhTheme.colors.isDark)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(tone.fill).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(tone.ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(PokyhIcons.tabGrades, contentDescription = null, tint = tone.ink, modifier = Modifier.size(21.dp))
        }
        Column {
            Text(I18n.s.profile.statsOffTitle, style = SettingsType.rowTitle, color = tone.ink)
            Spacer(Modifier.height(4.dp))
            Text(I18n.s.profile.statsOffText, style = PokyhType.body.copy(fontSize = 14.5.sp, lineHeight = 23.sp), color = tone.ink.copy(alpha = 0.85f))
        }
        WebButton(I18n.s.account.consentLink, onClick = onOpenSettings, variant = WebButtonVariant.Ink, small = true)
    }
}

/** Activity strength 0–4: minutes when known, else sign-ins (the web's `levelOf`). */
private fun levelOf(day: ApiActivityDay): Int {
    if (!day.active) return 0
    val minutes = day.activeSeconds / 60.0
    if (minutes > 0) return if (minutes >= 45) 4 else if (minutes >= 20) 3 else if (minutes >= 5) 2 else 1
    return when {
        day.logins >= 3 -> 4
        day.logins == 2 -> 3
        day.logins == 1 -> 2
        else -> 1
    }
}

@Composable
private fun levelColors(): List<Color> {
    val ink = PokyhTheme.colors.textPrimary
    return listOf(ink.copy(alpha = 0.07f), Brand.accent.copy(alpha = 0.28f), Brand.accent.copy(alpha = 0.48f), Brand.accent.copy(alpha = 0.72f), Brand.accent)
}

/** "Weniger ▢▢▢▢▢ Mehr". */
@Composable
private fun GridLegend() {
    val style = PokyhType.body.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val color = PokyhTheme.colors.textTertiary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(I18n.s.account.less, style = style, color = color, modifier = Modifier.padding(end = 4.dp))
        levelColors().forEach { Box(Modifier.size(14.dp).background(it, RoundedCornerShape(4.dp))) }
        Text(I18n.s.account.more, style = style, color = color, modifier = Modifier.padding(start = 4.dp))
    }
}

/** The grid as it loads: 14 weeks of blank squares. */
@Composable
private fun ActivityGridSkeleton(weeks: Int = 14) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(weeks) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(7) { SkeletonBlock(height = 20.dp, width = 20.dp, shape = RoundedCornerShape(6.dp)) }
            }
        }
    }
}

/** The tooltip over a square: centred above it, kept inside the window. */
private class AbovePosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * The profile's activity, drawn the way GitHub draws contributions (the web's `ActivityGrid`):
 * one column per week (Monday on top), one square per day, the stronger the indigo the more was
 * going on, today ringed. Tap a day for its figures in a tooltip above it. The squares come in
 * column by column. A whole school year draws smaller squares; it opens at today and scrolls back.
 */
@Composable
fun ActivityGrid(days: List<ApiActivityDay>) {
    val colors = PokyhTheme.colors
    val parsed = remember(days) { days.mapNotNull { d -> runCatching { LocalDate.parse(d.date) }.getOrNull()?.let { it to d } } }
    if (parsed.isEmpty()) return
    val lead = parsed.first().first.dayOfWeek.value - 1 // Monday = 0
    val cells: List<Pair<LocalDate, ApiActivityDay>?> = List(lead) { null } + parsed
    val weeks = cells.chunked(7)
    val dense = weeks.size > 20
    val cell = if (dense) 13.dp else 20.dp
    val gap = if (dense) 3.dp else 5.dp
    val radius = if (dense) 4.dp else 6.dp
    var selected by remember(days) { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(selected) {
        if (selected != null) {
            kotlinx.coroutines.delay(3500)
            selected = null
        }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(weeks.size) { scroll.scrollTo(scroll.maxValue) }
    val levels = levelColors()
    val labelStyle = PokyhType.body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
    val stagger = minOf(28, 900 / maxOf(1, weeks.size))
    val ring = colors.textPrimary.copy(alpha = 0.35f)
    val canvas = colors.card
    val density = LocalDensity.current
    val pitch = cell + gap

    // The month each column starts.
    val months = remember(weeks) {
        var last = -1
        weeks.mapIndexedNotNull { col, week ->
            val first = week.firstNotNullOfOrNull { it?.first } ?: return@mapIndexedNotNull null
            if (first.monthValue == last) null else { last = first.monthValue; col to first.monthValue }
        }
    }

    Row(Modifier.horizontalScroll(scroll).padding(bottom = 4.dp)) {
        // Mon, Wed, Fri beside the rows.
        Box(Modifier.width(36.dp).padding(top = 24.dp)) {
            listOf(0, 2, 4).forEach { row ->
                Text(
                    I18n.s.common.weekdaysShort[row].removeSuffix("."),
                    style = labelStyle,
                    color = colors.textTertiary,
                    modifier = Modifier.offset(y = pitch * row + cell / 2 - 8.dp),
                )
            }
        }
        Column {
            Box(Modifier.width(pitch * weeks.size).height(16.dp)) {
                months.forEach { (col, month) ->
                    Text(
                        I18n.s.common.monthsShort[month - 1],
                        style = labelStyle,
                        color = colors.textTertiary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.offset(x = pitch * col),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                weeks.forEachIndexed { col, week ->
                    var shown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay((col * stagger).toLong())
                        shown = true
                    }
                    val grow by animateFloatAsState(if (shown) 1f else 0f, tween(500), label = "cellIn")
                    Column(
                        Modifier.graphicsLayer { alpha = grow; scaleX = 0.6f + 0.4f * grow; scaleY = 0.6f + 0.4f * grow },
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        week.forEach { entry ->
                            if (entry == null) {
                                Spacer(Modifier.size(cell))
                            } else {
                                val (date, day) = entry
                                val isToday = entry === cells.last()
                                val shape = RoundedCornerShape(radius)
                                Box(
                                    Modifier
                                        .size(cell)
                                        .drawBehind {
                                            if (isToday) {
                                                val out = with(density) { 2.75.dp.toPx() }
                                                drawRoundRect(
                                                    color = canvas,
                                                    topLeft = Offset(-out + 1f, -out + 1f),
                                                    size = Size(size.width + 2 * out - 2f, size.height + 2 * out - 2f),
                                                    cornerRadius = CornerRadius(radius.toPx() + out),
                                                    style = Stroke(with(density) { 2.dp.toPx() }),
                                                )
                                                drawRoundRect(
                                                    color = ring,
                                                    topLeft = Offset(-out, -out),
                                                    size = Size(size.width + 2 * out, size.height + 2 * out),
                                                    cornerRadius = CornerRadius(radius.toPx() + out),
                                                    style = Stroke(with(density) { 1.5.dp.toPx() }),
                                                )
                                            }
                                        }
                                        .clip(shape)
                                        .background(levels[levelOf(day)])
                                        .clickable { selected = if (selected == date) null else date },
                                ) {
                                    if (selected == date) {
                                        Popup(
                                            popupPositionProvider = remember { AbovePosition(with(density) { 6.dp.roundToPx() }) },
                                            onDismissRequest = { selected = null },
                                            properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
                                        ) {
                                            Text(
                                                describeDay(date, day),
                                                style = PokyhType.body.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                                                color = if (colors.isDark) colors.textPrimary else colors.bg,
                                                modifier = Modifier
                                                    .shadow(6.dp, RoundedCornerShape(10.dp))
                                                    .background(if (colors.isDark) Color(0xFF2E2E34) else colors.textPrimary, RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
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
}

private fun describeDay(date: LocalDate, day: ApiActivityDay): String {
    val c = I18n.s.common
    val label = "${c.weekdaysShort[date.dayOfWeek.value - 1]}, ${c.dayMonthShort(date.dayOfMonth, date.monthValue)}"
    if (!day.active) return "$label · ${I18n.s.profile.notOnline}"
    val parts = mutableListOf(label)
    if (day.logins > 0) parts += I18n.s.profile.loginCount(day.logins)
    if (day.activeSeconds >= 60) parts += I18n.s.profile.inPokyh(durationText(day.activeSeconds.toLong()))
    if (parts.size == 1) parts += I18n.s.profile.online
    return parts.joinToString(" · ")
}

private fun durationText(seconds: Long): String {
    val m = Math.round(seconds / 60.0)
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}

private fun daysText(n: Int): String = I18n.s.common.days(n)

// ── Statistiken ─────────────────────────────────────────────────────────────

/** "Statistiken": how POKYH is used, only visible to the account itself. */
@Composable
fun StatsScreen(onNavigateBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.activity.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadActivity() }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(title = I18n.s.profile.statsTitle, eyebrow = I18n.s.profile.statsOnlyYou, nav = TopBarNav.Back(onNavigateBack))
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ProfileViewModel.ActivityState.Loaded -> StatsContent(s.activity)
                is ProfileViewModel.ActivityState.Failed -> ErrorStateView(
                    message = I18n.s.profile.activityFailed,
                    onRetry = { viewModel.loadActivity(force = true) },
                )
                is ProfileViewModel.ActivityState.Unavailable -> ErrorStateView(
                    message = I18n.s.profile.statsNeedAccount,
                )
                ProfileViewModel.ActivityState.Loading -> Column(
                    Modifier.padding(horizontal = PokyhSpacing.screenH),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                        StatCardSkeleton(Modifier.weight(1f))
                        StatCardSkeleton(Modifier.weight(1f))
                    }
                    StatCardSkeleton(tall = true)
                }
            }
        }
    }
}

@Composable
private fun StatsContent(a: ApiAccountActivity) {
    val consent = a.usageConsent
    val withTime = a.days.count { it.activeSeconds > 0 }
    val weekdayLabels = I18n.s.common.weekdaysShort
    val onlyWithConsent = I18n.s.profile.onlyWithConsent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
    ) {
        if (!consent) {
            PokyhInlineNotice(
                icon = PokyhIcons.privacy,
                text = I18n.s.profile.statsOffNotice,
                modifier = Modifier.fadeIn(),
            )
        }

        Column(Modifier.fadeIn(20), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                Figure(I18n.s.profile.daysOnline, a.totals.activeDays.toString(), I18n.s.profile.currentStreakIs(daysText(a.totals.currentStreak)), Modifier.weight(1f))
                Figure(I18n.s.profile.logins, a.totals.logins.toString(), I18n.s.profile.longestStreakIs(daysText(a.totals.longestStreak)), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                Figure(
                    I18n.s.profile.usageTime,
                    if (consent) durationText(a.totals.activeSeconds) else "–",
                    if (consent) I18n.s.profile.visits(a.totals.visits) else onlyWithConsent,
                    Modifier.weight(1f),
                )
                Figure(
                    I18n.s.profile.perDay,
                    if (consent && withTime > 0) durationText(a.totals.activeSeconds / withTime) else "–",
                    if (consent) I18n.s.profile.screensOpened(a.totals.screens) else null,
                    Modifier.weight(1f),
                )
            }
        }

        PokyhSection(title = I18n.s.profile.whenOnline, modifier = Modifier.fadeIn(40)) {
            PokyhCard {
                Text(I18n.s.profile.whenOnlineLead, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                Spacer(Modifier.size(PokyhSpacing.md))
                if (consent && a.hours.size >= 23) {
                    val values = a.hours.subList(6, 23)
                    Columns(
                        values = values,
                        labels = (6..22).map { if (it % 3 == 0) it.toString() else "" },
                        highlight = values.indexOf(values.max()).takeIf { values.max() > 0 },
                    )
                } else {
                    Text(onlyWithConsent, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                }
            }
        }

        PokyhSection(title = I18n.s.profile.weekdays, modifier = Modifier.fadeIn(60)) {
            PokyhCard {
                Text(I18n.s.profile.weekdaysLead, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                Spacer(Modifier.size(PokyhSpacing.md))
                if (a.weekdays.size == 7) {
                    Columns(
                        values = a.weekdays,
                        labels = weekdayLabels,
                        highlight = a.weekdays.indexOf(a.weekdays.max()).takeIf { a.weekdays.max() > 0 },
                    )
                }
            }
        }

        PokyhSection(title = I18n.s.profile.devices, modifier = Modifier.fadeIn(80)) {
            PokyhCard {
                Text(I18n.s.profile.devicesLead, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                Spacer(Modifier.size(PokyhSpacing.md))
                Bars(
                    listOf(
                        Triple(I18n.s.profile.phone, a.platforms.phone, daysText(a.platforms.phone)),
                        Triple(I18n.s.profile.computer, a.platforms.desktop, daysText(a.platforms.desktop)),
                        Triple(I18n.s.profile.app, a.platforms.app, daysText(a.platforms.app)),
                        Triple(I18n.s.profile.browser, a.platforms.web, daysText(a.platforms.web)),
                    ),
                )
            }
        }

        PokyhSection(title = I18n.s.profile.topPages, modifier = Modifier.fadeIn(100)) {
            PokyhCard {
                Text(I18n.s.profile.topPagesLead, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                Spacer(Modifier.size(PokyhSpacing.md))
                when {
                    !consent -> Text(onlyWithConsent, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                    a.pages.isEmpty() -> Text("–", style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                    else -> Bars(a.pages.map { Triple(pageName(it.page), it.views, I18n.s.profile.views(it.views)) })
                }
            }
        }

        PokyhSection(title = I18n.s.profile.created, modifier = Modifier.fadeIn(120)) {
            PokyhCard {
                val items = buildList {
                    add(I18n.s.profile.todosDone to a.created.todosDone.toString())
                    add(I18n.s.profile.todosOpen to a.created.todosOpen.toString())
                    add(I18n.s.profile.reminders to a.created.reminders.toString())
                    add(I18n.s.profile.comments to a.created.comments.toString())
                    add(I18n.s.profile.ratings to a.created.ratings.toString())
                    a.memberSince?.let { since ->
                        runCatching { LocalDate.parse(since.take(10)) }.getOrNull()?.let {
                            add(I18n.s.profile.memberSince to "${I18n.s.common.months[it.monthValue - 1].replaceFirstChar { c -> c.uppercase() }} ${it.year}")
                        }
                    }
                }
                items.chunked(2).forEachIndexed { row, pair ->
                    if (row > 0) Spacer(Modifier.size(PokyhSpacing.lg))
                    Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.lg)) {
                        pair.forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                PokyhLabel(label)
                                Text(value, style = PokyhType.statMedium.monospacedDigits(), color = PokyhTheme.colors.textPrimary)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Text(
            text = I18n.s.profile.statsFooter,
            style = PokyhType.caption,
            color = PokyhTheme.colors.textTertiary,
        )
    }
}

@Composable
private fun Figure(label: String, value: String, sub: String?, modifier: Modifier = Modifier) {
    PokyhCard(modifier = modifier) {
        PokyhLabel(label)
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(value, style = PokyhType.statMedium.monospacedDigits(), color = PokyhTheme.colors.textPrimary, maxLines = 1)
        if (sub != null) {
            Spacer(Modifier.size(PokyhSpacing.xxs))
            Text(sub, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
        }
    }
}

/** Vertical bars with a label under each; the largest is the full height and drawn solid. */
@Composable
private fun Columns(values: List<Int>, labels: List<String>, highlight: Int?) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { i, v ->
            val target = if (grown) (v.toFloat() / max).coerceAtLeast(if (v > 0) 0.05f else 0.02f) else 0.02f
            val fraction by animateFloatAsState(target, tween(700, delayMillis = i * 18), label = "bar")
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .background(
                                if (i == highlight) Brand.accent else Brand.accent.copy(alpha = 0.38f),
                                RoundedCornerShape(5.dp),
                            ),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(labels.getOrElse(i) { "" }, style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Horizontal bars for a few named values. */
@Composable
private fun Bars(rows: List<Triple<String, Int, String>>) {
    val max = (rows.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
        rows.forEachIndexed { i, (label, value, text) ->
            val fraction by animateFloatAsState(if (grown) value.toFloat() / max else 0f, tween(700, delayMillis = i * 60), label = "bar")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(label, style = PokyhType.subheadline, color = PokyhTheme.colors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).background(PokyhTheme.colors.textPrimary.copy(alpha = 0.08f), PokyhShapes.pill)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Brand.accent, PokyhShapes.pill))
                }
            }
        }
    }
}

private fun pageName(page: String): String = I18n.s.profile.pageNames[page.trim('/')] ?: page

// ── WebUntis-Passwort ändern ────────────────────────────────────────────────

/** The old password, the new one twice; WebUntis checks the old one and its own rules. */
@Composable
fun ChangePasswordSheet(viewModel: ProfileViewModel, onDismiss: () -> Unit) {
    val state by viewModel.password.collectAsStateWithLifecycle()
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    LaunchedEffect(Unit) { viewModel.resetPasswordState() }
    LaunchedEffect(state.done) {
        if (state.done) {
            kotlinx.coroutines.delay(1400)
            dismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
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
                    icon = PokyhIcons.close,
                    contentDescription = I18n.s.common.close,
                    onClick = ::dismiss,
                    tinted = true,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Text(
                    I18n.s.profile.changePassword,
                    style = PokyhType.headline,
                    color = PokyhTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).padding(horizontal = PokyhSpacing.md),
                )
            }
            if (state.done) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    Box(Modifier.size(64.dp).accentSurface(PokyhShapes.xl), contentAlignment = Alignment.Center) {
                        Icon(PokyhIcons.check, null, tint = Brand.success, modifier = Modifier.size(30.dp))
                    }
                    Text(I18n.s.profile.passwordChanged, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                    Text(
                        I18n.s.profile.passwordChangedText,
                        style = PokyhType.footnote,
                        color = PokyhTheme.colors.textSecondary,
                    )
                }
            } else {
                Text(
                    I18n.s.profile.passwordLead,
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.textSecondary,
                )
                PokyhTextField(old, { old = it }, label = I18n.s.profile.currentPassword, isPassword = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                PokyhTextField(new, { new = it }, label = I18n.s.profile.newPassword, isPassword = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                PokyhTextField(confirm, { confirm = it }, label = I18n.s.profile.repeatPassword, isPassword = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, style = PokyhType.footnote, color = Brand.danger) }
                PokyhPrimaryButton(
                    text = I18n.s.profile.changePassword,
                    icon = PokyhIcons.password,
                    loading = state.saving,
                    onClick = { viewModel.changePassword(old, new, confirm) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
