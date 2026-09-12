@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.classroom

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
import dev.plattnericus.pokyh.data.model.ClassregEvent
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

private val Weekdays = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private val MonthsShort = listOf(
    "Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez",
)

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
 *
 * Klassendienste and Hausaufgaben have no data source in this app yet (see [ClassOverview]), so
 * they always show their empty state.
 */
@Composable
fun ClassScreen(
    onNavigateBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: ClassViewModel = hiltViewModel(),
) {
    val klass by viewModel.klass.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val hasBackend = viewModel.hasBackend
    val klasseName = viewModel.klasseName

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = if (klasseName.isNotEmpty()) "Klasse $klasseName" else "Meine Klasse",
                eyebrow = "Schuljahr ${viewModel.schoolYear} / ${viewModel.schoolYear + 1}",
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
        ) {
            // ── 0. Beitrittscode ────────────────────────────────────────────
            // Top of the page on purpose: it's the one thing on this screen you open it to read
            // out to someone, so it should never need scrolling to.
            klass?.let { c -> JoinCodeCard(code = c.code, modifier = Modifier.fadeIn()) }

            // ── 1. Neue Klassenbuch Einträge ────────────────────────────────
            OverviewSection(
                title = "Neue Klassenbuch Einträge",
                subtitle = "Letzte 3 Monate",
                onSeeAll = { onNavigate(PokyhDestinations.CLASSREG_EVENTS) },
                emptyText = "Keine Einträge in den letzten 3 Monaten",
                items = overview.recentEvents,
                modifier = Modifier.fadeIn(),
            ) { event -> ClassregEventRow(event) }

            // ── 2. Offene Abwesenheiten ─────────────────────────────────────
            OverviewSection(
                title = "Offene Abwesenheiten",
                onSeeAll = { onNavigate(PokyhDestinations.ABSENCES) },
                emptyText = "Keine offenen Abwesenheiten",
                items = overview.openAbsences.take(6),
                footer = {
                    val extra = overview.openAbsences.size - 6
                    if (extra > 0) {
                        PokyhTextButton(
                            text = "+$extra weitere",
                            onClick = { onNavigate(PokyhDestinations.ABSENCES) },
                        )
                    }
                },
                modifier = Modifier.fadeIn(40),
            ) { absence -> OpenAbsenceRow(absence) }

            // ── 3. Klassendienste ───────────────────────────────────────────
            OverviewSection(
                title = "Klassendienste",
                emptyText = "Keine Klassendienste eingetragen",
                items = emptyList<Unit>(),
                modifier = Modifier.fadeIn(80),
            ) {}

            // ── 4. Prüfungen ────────────────────────────────────────────────
            WeekSplitSection(
                title = "Prüfungen",
                thisWeekEmpty = "Keine Prüfungen diese Woche",
                nextWeekEmpty = "Keine Prüfungen nächste Woche",
                bothEmpty = "Keine Prüfungen in Zukunft",
                thisWeek = overview.examsThisWeek,
                nextWeek = overview.examsNextWeek,
                modifier = Modifier.fadeIn(120),
            ) { exam -> ExamRow(exam) }

            // ── 5. Hausaufgaben ─────────────────────────────────────────────
            WeekSplitSection(
                title = "Hausaufgaben",
                thisWeekEmpty = "Keine Hausaufgaben diese Woche",
                nextWeekEmpty = "Keine Hausaufgaben nächste Woche",
                bothEmpty = "Keine Hausaufgaben in Zukunft",
                thisWeek = emptyList<Unit>(),
                nextWeek = emptyList<Unit>(),
                modifier = Modifier.fadeIn(160),
            ) {}

            // ── 6. Klasse & Mitglieder ──────────────────────────────────────
            when {
                !hasBackend -> Box(Modifier.fillMaxWidth().height(240.dp)) {
                    BackendUnavailableView(feature = "Die Klassenliste", status = backendStatus)
                }
                error != null -> Box(Modifier.fillMaxWidth().height(240.dp)) {
                    ErrorStateView(message = error!!, onRetry = viewModel::retry)
                }
                klass != null -> {
                    val c = klass!!
                    Column(modifier = Modifier.fadeIn(200)) {
                        PokyhSectionHeader(
                            title = "Mitglieder",
                            trailing = { PokyhLabel("${c.members.size}") },
                        )
                        Spacer(Modifier.size(PokyhSpacing.headerContent))
                        PokyhCard(padding = 0.dp) {
                            c.members.forEachIndexed { index, member ->
                                if (index > 0) PokyhRowSeparator(startInset = 0.dp)
                                MemberExpandableRow(member)
                            }
                        }
                    }
                }
                loading -> Unit
            }
        }
    }
}

// ── Section shells ──────────────────────────────────────────────────────────

/**
 * A titled block with an optional "Alle ›" link, rendering [items] inside one card — or a single
 * quiet line when there are none.
 */
@Composable
private fun <T> OverviewSection(
    title: String,
    emptyText: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = PokyhType.title2, color = PokyhTheme.colors.textPrimary)
                if (subtitle != null) {
                    Spacer(Modifier.size(2.dp))
                    PokyhLabel(subtitle)
                }
            }
            if (onSeeAll != null) {
                PokyhTextButton(text = "Alle", icon = PokyhIcons.chevronRight, onClick = onSeeAll)
            }
        }
        Spacer(Modifier.size(PokyhSpacing.headerContent))
        if (items.isEmpty()) {
            EmptyLine(emptyText)
        } else {
            PokyhCard(padding = 0.dp) {
                items.forEachIndexed { index, item ->
                    if (index > 0) PokyhRowSeparator(startInset = 0.dp)
                    itemContent(item)
                }
            }
            if (footer != null) {
                Spacer(Modifier.size(PokyhSpacing.xs))
                footer()
            }
        }
    }
}

/**
 * A section split into "Diese Woche" / "Nächste Woche".
 *
 * When BOTH weeks are empty the split collapses to a single [bothEmpty] line: two week labels
 * over two identical "nothing here" rows is four lines to say one thing, and it makes a quiet
 * fortnight look like a layout problem. As soon as either week has something, the split comes
 * back so you can tell which week it lands in.
 */
@Composable
private fun <T> WeekSplitSection(
    title: String,
    thisWeekEmpty: String,
    nextWeekEmpty: String,
    bothEmpty: String,
    thisWeek: List<T>,
    nextWeek: List<T>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        PokyhSectionHeader(title = title)
        Spacer(Modifier.size(PokyhSpacing.headerContent))
        if (thisWeek.isEmpty() && nextWeek.isEmpty()) {
            EmptyLine(bothEmpty)
        } else {
            WeekBlock(label = "Diese Woche", emptyText = thisWeekEmpty, items = thisWeek, itemContent = itemContent)
            Spacer(Modifier.size(PokyhSpacing.lg))
            WeekBlock(label = "Nächste Woche", emptyText = nextWeekEmpty, items = nextWeek, itemContent = itemContent)
        }
    }
}

@Composable
private fun <T> WeekBlock(
    label: String,
    emptyText: String,
    items: List<T>,
    itemContent: @Composable (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PokyhLabel(label)
        Spacer(Modifier.size(PokyhSpacing.sm))
        if (items.isEmpty()) {
            EmptyLine(emptyText)
        } else {
            PokyhCard(padding = 0.dp) {
                items.forEachIndexed { index, item ->
                    if (index > 0) PokyhRowSeparator(startInset = 0.dp)
                    itemContent(item)
                }
            }
        }
    }
}

/** The "nothing here" line — recessed rather than a card, so an empty section reads as absence
 * of content instead of as content. */
@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = PokyhType.footnote,
        color = PokyhTheme.colors.textTertiary,
        modifier = Modifier
            .fillMaxWidth()
            .nestedSurface(PokyhShapes.lg)
            .padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.lg),
    )
}

// ── Rows ────────────────────────────────────────────────────────────────────

/** Day/month on the left, subject + text + author on the right, category chip trailing. */
@Composable
private fun ClassregEventRow(event: ClassregEvent) {
    val colors = PokyhTheme.colors
    val date = event.createDate.toLocalDate()
    val accent = when {
        event.categoryName.lowercase().let { it.contains("täuschung") || it.contains("betrug") } -> Brand.danger
        event.categoryName.lowercase().contains("vermerk") -> Brand.warning
        else -> Brand.accent
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "${date.dayOfMonth}.",
                style = PokyhType.headline.monospacedDigits(),
                color = colors.textPrimary,
            )
            Text(
                text = MonthsShort[date.monthNumber - 1],
                style = PokyhType.caption2,
                color = colors.textTertiary,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (event.subjectName.isNotEmpty()) {
                Text(event.subjectName, style = PokyhType.headline, color = colors.textPrimary)
            }
            val body = event.text.ifEmpty { event.eventReasonName }
            if (body.isNotEmpty()) {
                Text(body, style = PokyhType.callout, color = colors.textSecondary)
            }
            if (event.creatorName.isNotEmpty()) {
                Text(event.creatorName, style = PokyhType.caption2, color = colors.textTertiary)
            }
        }
        if (event.categoryName.isNotEmpty()) {
            TagChip(text = event.categoryName, color = accent)
        }
    }
}

/** An unexcused absence: a danger dot, the date range, and an "Offen" chip. */
@Composable
private fun OpenAbsenceRow(absence: AbsenceEntry) {
    val colors = PokyhTheme.colors
    val range = if (absence.startDate != absence.endDate) {
        "${Fmt.dateShort(absence.startDate)} – ${Fmt.dateShort(absence.endDate)}"
    } else {
        Fmt.dateShort(absence.startDate)
    }
    val sub = absence.subjectName?.takeIf { it.isNotEmpty() }
        ?: absence.reasonName?.takeIf { it.isNotEmpty() }

    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(Brand.danger, PokyhShapes.pill))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(range, style = PokyhType.headline.monospacedDigits(), color = colors.textPrimary)
            if (sub != null) {
                Text(sub, style = PokyhType.footnote, color = colors.textSecondary)
            }
        }
        TagChip(text = "Offen", color = Brand.danger)
    }
}

/** Weekday + day number on the left, subject/time/room on the right, "Prüfung" chip trailing. */
@Composable
private fun ExamRow(exam: TimetableEntry) {
    val colors = PokyhTheme.colors
    val date = exam.date.toLocalDate()
    val room = exam.roomName.takeIf { it.isNotEmpty() }
    val time = "${Fmt.time(exam.startTime)} – ${Fmt.time(exam.endTime)}" + (if (room != null) " · $room" else "")

    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Weekdays[(date.dayOfWeek.isoDayNumber - 1).coerceIn(0, 6)],
                style = PokyhType.caption,
                color = colors.textTertiary,
            )
            Text(
                text = "${date.dayOfMonth}.",
                style = PokyhType.headline.monospacedDigits(),
                color = colors.textPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = exam.subjectLong.ifEmpty { exam.subjectName.ifEmpty { "Prüfung" } },
                style = PokyhType.headline,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(time, style = PokyhType.footnote, color = colors.textSecondary)
            exam.examDescription?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = PokyhType.caption, color = colors.textTertiary)
            }
        }
        TagChip(text = "Prüfung", color = Brand.warning)
    }
}


/** The join code, as the one accent-tinted element on the page — it's the thing people read out. */
@Composable
private fun JoinCodeCard(code: String, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PokyhLabel("Beitrittscode")
                Text(
                    text = "Zum Teilen mit Klassenkamerad:innen",
                    style = PokyhType.caption,
                    color = colors.textTertiary,
                )
            }
            Text(
                text = code,
                style = PokyhType.title3.copy(fontFamily = FontFamily.Monospace),
                color = colors.accentText,
                modifier = Modifier
                    .accentSurface(PokyhShapes.pill)
                    .padding(horizontal = PokyhSpacing.lg, vertical = PokyhSpacing.sm),
            )
        }
    }
}

/** A member row that expands to show what the class record actually knows about them. */
@Composable
private fun MemberExpandableRow(member: ApiClassMember) {
    val colors = PokyhTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(140),
        label = "memberChevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressHighlight(interactionSource, shape = null)
                .clickable(interactionSource = interactionSource, indication = null) { expanded = !expanded }
                .heightIn(min = 60.dp)
                .padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        ) {
            InitialAvatar(name = member.username, size = 38.dp)
            Text(
                text = member.username,
                style = PokyhType.body,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = PokyhIcons.expandMenu,
                contentDescription = if (expanded) "Einklappen" else "Aufklappen",
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp).rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = animateFadeIn(tween(140)) + expandVertically(tween(260)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(260)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedSurface(shape = androidx.compose.ui.graphics.RectangleShape)
                    .padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                MemberDetail("Benutzername", member.username)
                member.joinedAt?.takeIf { it.isNotEmpty() }?.let {
                    MemberDetail("Beigetreten", it.take(10))
                }
                MemberDetail("ID", member.stableUid)
            }
        }
    }
}

@Composable
private fun MemberDetail(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
        Text(
            text = label,
            style = PokyhType.caption,
            color = PokyhTheme.colors.textTertiary,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value,
            style = PokyhType.caption,
            color = PokyhTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
