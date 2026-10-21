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
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

// The profile's activity and the statistics behind it, as on the web app (profile and
// profile/stats): everything comes from GET /users/me/activity, which only shows what the POKYH
// server already keeps. Time in POKYH and the most used pages need the personal usage statistics.

private val German = Locale.GERMAN

// ── Aktivität im Profil ─────────────────────────────────────────────────────

/** "Aktivität": the grid of the last weeks, three figures and the way to the statistics. */
@Composable
fun ActivitySection(viewModel: ProfileViewModel, onOpenStats: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.activity.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadActivity() }
    if (state is ProfileViewModel.ActivityState.Unavailable) return

    PokyhSection(title = "Aktivität", modifier = modifier) {
        PokyhCard(padding = 0.dp) {
            Column(Modifier.padding(PokyhSpacing.card), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                Text("An welchen Tagen du POKYH genutzt hast.", style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                when (val s = state) {
                    is ProfileViewModel.ActivityState.Loaded -> {
                        ActivityGrid(days = s.activity.days)
                        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xl)) {
                            SmallFigure(s.activity.totals.activeDays.toString(), "Tage online")
                            SmallFigure(s.activity.totals.logins.toString(), "Anmeldungen")
                            SmallFigure(daysText(s.activity.totals.currentStreak), "Aktuelle Serie")
                        }
                    }
                    is ProfileViewModel.ActivityState.Failed -> Text(
                        "Deine Aktivität kann gerade nicht geladen werden.",
                        style = PokyhType.callout,
                        color = PokyhTheme.colors.textSecondary,
                    )
                    else -> SkeletonBlock(height = 128.dp)
                }
            }
            PokyhRowSeparator()
            PokyhRow(
                title = "Mehr Statistiken",
                leading = { Icon(PokyhIcons.gradeTrend, null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(20.dp)) },
                onClick = onOpenStats,
            )
        }
    }
}

@Composable
private fun SmallFigure(value: String, label: String) {
    Column {
        Text(value, style = PokyhType.headline.monospacedDigits(), color = PokyhTheme.colors.textPrimary)
        Text(label, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
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

/**
 * One column per week (Monday on top), one square per day, the stronger the indigo the more was
 * going on, as GitHub draws contributions. Opens at today and scrolls back; a tap on a day shows
 * its figures underneath.
 */
@Composable
fun ActivityGrid(days: List<ApiActivityDay>) {
    val colors = PokyhTheme.colors
    val parsed = remember(days) { days.mapNotNull { d -> runCatching { LocalDate.parse(d.date) }.getOrNull()?.let { it to d } } }
    if (parsed.isEmpty()) return
    val lead = parsed.first().first.dayOfWeek.value - 1 // Monday = 0
    val cells: List<Pair<LocalDate, ApiActivityDay>?> = List(lead) { null } + parsed
    val weeks = cells.chunked(7)
    var selected by remember(days) { mutableStateOf<Pair<LocalDate, ApiActivityDay>?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(weeks.size) { scroll.scrollTo(scroll.maxValue) }

    val cell = 14.dp
    val gap = 3.dp
    val levelColors = listOf(
        colors.textPrimary.copy(alpha = 0.07f),
        Brand.accent.copy(alpha = 0.28f),
        Brand.accent.copy(alpha = 0.48f),
        Brand.accent.copy(alpha = 0.72f),
        Brand.accent,
    )

    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
        Row {
            Column(Modifier.padding(top = 18.dp).width(24.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
                listOf("Mo", "", "Mi", "", "Fr", "", "").forEach { label ->
                    Box(Modifier.height(cell), contentAlignment = Alignment.CenterStart) {
                        if (label.isNotEmpty()) Text(label, style = PokyhType.caption2, color = colors.textTertiary)
                    }
                }
            }
            Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(gap)) {
                var lastMonth = -1
                weeks.forEach { week ->
                    val firstDay = week.firstNotNullOfOrNull { it?.first }
                    val monthLabel = firstDay?.takeIf { it.monthValue != lastMonth }?.also { lastMonth = it.monthValue }
                        ?.month?.getDisplayName(TextStyle.SHORT, German)?.removeSuffix(".")
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        Box(Modifier.height(15.dp).width(cell)) {
                            if (monthLabel != null) {
                                Text(monthLabel, style = PokyhType.caption2, color = colors.textTertiary, maxLines = 1, softWrap = false)
                            }
                        }
                        week.forEach { entry ->
                            val shape = RoundedCornerShape(4.dp)
                            if (entry == null) {
                                Spacer(Modifier.size(cell))
                            } else {
                                val isSelected = selected?.first == entry.first
                                Box(
                                    Modifier
                                        .size(cell)
                                        .background(levelColors[levelOf(entry.second)], shape)
                                        .then(if (isSelected) Modifier.background(colors.textPrimary.copy(alpha = 0.25f), shape) else Modifier)
                                        .clickable { selected = if (isSelected) null else entry },
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            text = selected?.let { (date, day) -> describeDay(date, day) } ?: "Tippe auf einen Tag für Details.",
            style = PokyhType.caption,
            color = if (selected != null) colors.textSecondary else colors.textTertiary,
        )
    }
}

private fun describeDay(date: LocalDate, day: ApiActivityDay): String {
    val label = date.format(DateTimeFormatter.ofPattern("EE, d. MMM", German))
    if (!day.active) return "$label · Nicht online"
    val parts = mutableListOf(label)
    if (day.logins > 0) parts += if (day.logins == 1) "1 Anmeldung" else "${day.logins} Anmeldungen"
    if (day.activeSeconds >= 60) parts += "${durationText(day.activeSeconds.toLong())} in POKYH"
    if (parts.size == 1) parts += "Online"
    return parts.joinToString(" · ")
}

private fun durationText(seconds: Long): String {
    val m = Math.round(seconds / 60.0)
    return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
}

private fun daysText(n: Int): String = if (n == 1) "1 Tag" else "$n Tage"

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
            PokyhTopBar(title = "Statistiken", eyebrow = "Nur für dich sichtbar", nav = TopBarNav.Back(onNavigateBack))
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is ProfileViewModel.ActivityState.Loaded -> StatsContent(s.activity)
                is ProfileViewModel.ActivityState.Failed -> ErrorStateView(
                    message = "Deine Aktivität kann gerade nicht geladen werden.",
                    onRetry = { viewModel.loadActivity(force = true) },
                )
                is ProfileViewModel.ActivityState.Unavailable -> ErrorStateView(
                    message = "Statistiken gibt es mit einem POKYH-Konto, das gerade verbunden ist.",
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
    val weekdayLabels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    val onlyWithConsent = "Nur mit persönlicher Nutzungsstatistik."

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
                text = "Persönliche Statistiken sind ausgeschaltet. Darum siehst du hier nur, an welchen Tagen du online " +
                    "warst, nicht wie lange und was du am meisten öffnest. Einschalten kannst du sie auf pokyh.com " +
                    "unter Einstellungen → Datenschutz.",
                modifier = Modifier.fadeIn(),
            )
        }

        Column(Modifier.fadeIn(20), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                Figure("Tage online", a.totals.activeDays.toString(), "Aktuelle Serie: ${daysText(a.totals.currentStreak)}", Modifier.weight(1f))
                Figure("Anmeldungen", a.totals.logins.toString(), "Längste Serie: ${daysText(a.totals.longestStreak)}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                Figure(
                    "Nutzungszeit",
                    if (consent) durationText(a.totals.activeSeconds) else "–",
                    if (consent) "Besuche: ${a.totals.visits}" else onlyWithConsent,
                    Modifier.weight(1f),
                )
                Figure(
                    "Ø pro Tag",
                    if (consent && withTime > 0) durationText(a.totals.activeSeconds / withTime) else "–",
                    if (consent) "Geöffnete Seiten: ${a.totals.screens}" else null,
                    Modifier.weight(1f),
                )
            }
        }

        PokyhSection(title = "Wann du online bist", modifier = Modifier.fadeIn(40)) {
            PokyhCard {
                Text("Nutzungszeit nach Uhrzeit.", style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
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

        PokyhSection(title = "Wochentage", modifier = Modifier.fadeIn(60)) {
            PokyhCard {
                Text("An welchen Wochentagen du online warst.", style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
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

        PokyhSection(title = "Geräte", modifier = Modifier.fadeIn(80)) {
            PokyhCard {
                Text("Tage, an denen du das jeweilige Gerät genutzt hast.", style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                Spacer(Modifier.size(PokyhSpacing.md))
                Bars(
                    listOf(
                        Triple("Handy", a.platforms.phone, daysText(a.platforms.phone)),
                        Triple("Computer", a.platforms.desktop, daysText(a.platforms.desktop)),
                        Triple("App", a.platforms.app, daysText(a.platforms.app)),
                        Triple("Browser", a.platforms.web, daysText(a.platforms.web)),
                    ),
                )
            }
        }

        PokyhSection(title = "Meistgenutzte Seiten", modifier = Modifier.fadeIn(100)) {
            PokyhCard {
                Text("Auf der Website, in den letzten Wochen.", style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                Spacer(Modifier.size(PokyhSpacing.md))
                when {
                    !consent -> Text(onlyWithConsent, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                    a.pages.isEmpty() -> Text("–", style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                    else -> Bars(a.pages.map { Triple(pageName(it.page), it.views, "${it.views} Aufrufe") })
                }
            }
        }

        PokyhSection(title = "Was du erstellt hast", modifier = Modifier.fadeIn(120)) {
            PokyhCard {
                val items = buildList {
                    add("To-dos erledigt" to a.created.todosDone.toString())
                    add("To-dos offen" to a.created.todosOpen.toString())
                    add("Erinnerungen" to a.created.reminders.toString())
                    add("Kommentare" to a.created.comments.toString())
                    add("Mensa-Bewertungen" to a.created.ratings.toString())
                    a.memberSince?.let { since ->
                        runCatching { LocalDate.parse(since.take(10)) }.getOrNull()?.let {
                            add("Dabei seit" to it.format(DateTimeFormatter.ofPattern("MMMM yyyy", German)))
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
            text = "Gezeigt wird, was der POKYH-Server ohnehin speichert (Datenschutzerklärung). Nur du siehst diese Übersicht.",
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

/** A web page path as the name the app uses for it. */
private fun pageName(page: String): String = when (page.trim('/')) {
    "home" -> "Home"
    "timetable" -> "Stundenplan"
    "grades" -> "Noten"
    "messages" -> "Nachrichten"
    "absences" -> "Abwesenheiten"
    "classregevents" -> "Klassenbuch"
    "todos" -> "To-dos"
    "reminders" -> "Erinnerungen"
    "class" -> "Klasse"
    "canteen", "mensa" -> "Mensa"
    "school" -> "Schule"
    "profile" -> "Profil"
    "settings" -> "Einstellungen"
    else -> page
}

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
                    contentDescription = "Schließen",
                    onClick = ::dismiss,
                    tinted = true,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Text(
                    "Passwort ändern",
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
                    Text("Passwort geändert.", style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                    Text(
                        "Melde dich ab jetzt überall mit dem neuen Passwort an.",
                        style = PokyhType.footnote,
                        color = PokyhTheme.colors.textSecondary,
                    )
                }
            } else {
                Text(
                    "Das ist dein WebUntis-Passwort. Es geht nur an WebUntis, POKYH speichert es nicht auf dem Server.",
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.textSecondary,
                )
                PokyhTextField(old, { old = it }, label = "Aktuelles Passwort", isPassword = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                PokyhTextField(new, { new = it }, label = "Neues Passwort", isPassword = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                PokyhTextField(confirm, { confirm = it }, label = "Neues Passwort wiederholen", isPassword = true, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, style = PokyhType.footnote, color = Brand.danger) }
                PokyhPrimaryButton(
                    text = "Passwort ändern",
                    icon = PokyhIcons.password,
                    loading = state.saving,
                    onClick = { viewModel.changePassword(old, new, confirm) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
