@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.grades

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.GradeMath
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.model.GradeEntry
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhMenuButton
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSectionHeader
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.profile.CurrentUserAvatar
import dev.plattnericus.pokyh.ui.profile.rememberUnreadMessageCount
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.bandColor
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.nestedSurface
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import dev.plattnericus.pokyh.ui.theme.subjectColor

/**
 * Noten — the web app's dashboard, rebuilt for the phone.
 *
 * Structure follows `app/grades/page.tsx` (github.com/bedchem/pokyh-frontend) exactly: the
 * school-year header with its "Stand … · N Fächer · N Noten" line and year picker, the four stat
 * cards, then the Fächer list with a sort menu, an expand-all toggle, and rows that open in place
 * to reveal their individual grades. Numbers and diagram geometry are the same
 * ([buildGradesDashboard]); the surfaces, type and palette are this app's.
 *
 * The dashboard renders even with zero grades — the cards show placeholders rather than being
 * swapped for an empty state, so the screen looks like itself at the start of a school year.
 */
@Composable
fun GradesScreen(
    onSubjectClick: (lessonId: Int, year: Int) -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: GradesViewModel = hiltViewModel(),
) {
    val year by viewModel.year.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var yearMenuOpen by remember { mutableStateOf(false) }

    val dashboard = remember(subjects) { buildGradesDashboard(subjects, todayLocalDate()) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Schuljahr $year / ${year + 1}",
                nav = TopBarNav.None,
                actions = {
                    Box {
                        PokyhMenuButton(
                            label = "$year/${(year + 1) % 100}",
                            expanded = yearMenuOpen,
                            onClick = { yearMenuOpen = true },
                        )
                        DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                            viewModel.availableYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y / ${y + 1}", style = PokyhType.body) },
                                    trailingIcon = {
                                        if (y == year) {
                                            Icon(
                                                imageVector = PokyhIcons.check,
                                                contentDescription = null,
                                                tint = PokyhTheme.colors.accentText,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    },
                                    onClick = { viewModel.selectYear(y); yearMenuOpen = false },
                                )
                            }
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
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                else -> GradesContent(
                    dashboard = dashboard,
                    subjects = subjects,
                    sort = sort,
                    year = year,
                    onSortChange = viewModel::selectSort,
                    onSubjectClick = onSubjectClick,
                )
            }
        }
    }
}

@Composable
private fun GradesContent(
    dashboard: GradesDashboard,
    subjects: List<SubjectGrades>,
    sort: GradesViewModel.SortMode,
    year: Int,
    onSortChange: (GradesViewModel.SortMode) -> Unit,
    onSubjectClick: (lessonId: Int, year: Int) -> Unit,
) {
    // Same ordering the web applies, including "Letzte Note" (by each subject's newest date).
    //
    // One addition the web doesn't need: subjects with no grades yet are always sorted LAST,
    // whatever the direction. Their average is 0, so "Schlechtester ⌀" would otherwise put every
    // ungraded subject above a genuine 4 — ranking by a number that doesn't exist.
    val sortedSubjects = remember(subjects, sort) {
        val withMeta = subjects.map { s ->
            val vals = s.grades.map { it.markDisplayValue }.filter { it > 0 }
            Triple(s, GradeMath.averageOf(vals), s.grades.maxOfOrNull { it.date } ?: 0)
        }
        val ungradedLast = compareBy<Triple<SubjectGrades, Double, Int>> { it.second <= 0 }
        when (sort) {
            GradesViewModel.SortMode.NAME ->
                withMeta.sortedBy { it.first.subjectName.lowercase() }
            GradesViewModel.SortMode.AVG_DESC ->
                withMeta.sortedWith(ungradedLast.thenByDescending { it.second })
            GradesViewModel.SortMode.AVG_ASC ->
                withMeta.sortedWith(ungradedLast.thenBy { it.second })
            GradesViewModel.SortMode.RECENT ->
                withMeta.sortedWith(ungradedLast.thenByDescending { it.third })
        }.map { it.first to it.second }
    }

    var expanded by remember { mutableStateOf(setOf<Int>()) }
    val allExpanded = sortedSubjects.isNotEmpty() && expanded.size == sortedSubjects.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
    ) {
        DashboardEyebrow(dashboard)

        AverageStatCard(dashboard, modifier = Modifier.fadeIn())
        RatioStatCard(dashboard, modifier = Modifier.fadeIn(40))
        DistributionStatCard(dashboard, modifier = Modifier.fadeIn(80))
        RecentStatCard(dashboard, modifier = Modifier.fadeIn(120))

        Column(modifier = Modifier.fadeIn(160)) {
            PokyhSectionHeader(
                title = "Fächer",
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
                    ) {
                        SortMenu(sort = sort, onSortChange = onSortChange)
                        PokyhTextButton(
                            text = if (allExpanded) "Einklappen" else "Alle",
                            onClick = {
                                expanded = if (allExpanded) {
                                    emptySet()
                                } else {
                                    sortedSubjects.map { it.first.lessonId }.toSet()
                                }
                            },
                        )
                    }
                },
            )
            Spacer(Modifier.size(PokyhSpacing.headerContent))

            if (sortedSubjects.isEmpty()) {
                // Only reachable when WebUntis returns no lessons at all for the year — a
                // subject with zero grades is still listed (see UntisClient.grades).
                PokyhCard {
                    Text(
                        text = "WebUntis hat für dieses Schuljahr keine Fächer gemeldet.",
                        style = PokyhType.footnote,
                        color = PokyhTheme.colors.textTertiary,
                    )
                }
            } else {
                PokyhCard(padding = 0.dp) {
                    sortedSubjects.forEachIndexed { index, (subject, avg) ->
                        if (index > 0) PokyhRowSeparator(startInset = 0.dp)
                        SubjectExpandableRow(
                            subject = subject,
                            average = avg,
                            isExpanded = subject.lessonId in expanded,
                            onToggle = {
                                expanded = if (subject.lessonId in expanded) {
                                    expanded - subject.lessonId
                                } else {
                                    expanded + subject.lessonId
                                }
                            },
                            onOpenDetails = { onSubjectClick(subject.lessonId, year) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(sort: GradesViewModel.SortMode, onSortChange: (GradesViewModel.SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PokyhMenuButton(
            label = sort.label,
            leadingIcon = PokyhIcons.sort,
            expanded = open,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            GradesViewModel.SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label, style = PokyhType.body) },
                    trailingIcon = {
                        if (sort == mode) {
                            Icon(
                                imageVector = PokyhIcons.check,
                                contentDescription = null,
                                tint = PokyhTheme.colors.accentText,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = { onSortChange(mode); open = false },
                )
            }
        }
    }
}

/**
 * A subject row that expands in place to list its grades — the web's `timeline-item`.
 *
 * The leading dot is the subject's own cross-platform hashed [subjectColor], so a subject is
 * recognisable by color here and on the timetable; the average is colored by its [bandColor]
 * band, like every grade value on the screen.
 */
@Composable
private fun SubjectExpandableRow(
    subject: SubjectGrades,
    average: Double,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(PokyhMotion.durationFast),
        label = "subjectChevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressHighlight(interactionSource, shape = null)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                .heightIn(min = 60.dp)
                .padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        ) {
            Box(Modifier.size(10.dp).background(subjectColor(subject.subjectName), PokyhShapes.pill))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                Text(
                    text = subject.subjectName,
                    style = PokyhType.headline,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${subject.grades.size} ${if (subject.grades.size == 1) "Note" else "Noten"}",
                    style = PokyhType.caption,
                    color = colors.textTertiary,
                )
            }
            Text(
                text = if (average > 0) Fmt.num(average) else "–",
                style = PokyhType.title3.monospacedDigits(),
                color = if (average > 0) bandColor(gradeBand(average)) else colors.textSecondary,
            )
            Icon(
                imageVector = PokyhIcons.expandMenu,
                contentDescription = if (isExpanded) "Einklappen" else "Aufklappen",
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp).rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = animateFadeIn(tween(PokyhMotion.durationFast)) +
                expandVertically(tween(PokyhMotion.durationStandard)),
            exit = fadeOut(tween(PokyhMotion.durationFast)) +
                shrinkVertically(tween(PokyhMotion.durationStandard)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedSurface(shape = androidx.compose.ui.graphics.RectangleShape)
                    .padding(horizontal = PokyhSpacing.card)
                    .padding(top = PokyhSpacing.sm, bottom = PokyhSpacing.md),
            ) {
                if (subject.grades.isEmpty()) {
                    Text(
                        text = "Keine Einzelnoten vorhanden.",
                        style = PokyhType.footnote,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(vertical = PokyhSpacing.sm),
                    )
                } else {
                    subject.grades.sortedByDescending { it.date }.forEach { grade ->
                        GradeDetailRow(grade)
                    }
                }
                Spacer(Modifier.size(PokyhSpacing.md))
                // Right-aligned with a real fill: it's the one action in this panel, and left-
                // aligned label-only text read as another grade row.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PokyhSecondaryButton(
                        text = "Details öffnen",
                        icon = PokyhIcons.chevronRight,
                        onClick = onOpenDetails,
                        compact = true,
                    )
                }
            }
        }
    }
}

/** One grade inside an expanded subject: date, what it was, value. */
@Composable
private fun GradeDetailRow(grade: GradeEntry) {
    val colors = PokyhTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        Text(
            text = Fmt.dateShort(grade.date),
            style = PokyhType.caption.monospacedDigits(),
            color = colors.textTertiary,
            modifier = Modifier.width(58.dp),
        )
        Text(
            text = gradeDisplay(grade),
            style = PokyhType.footnote,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = Fmt.num(grade.markDisplayValue),
            style = PokyhType.headline.monospacedDigits(),
            color = bandColor(gradeBand(grade.markDisplayValue)),
        )
    }
}

/**
 * `gradeDisplay(_:)` — falls back through text -> markName -> examType -> "Prüfung", and treats a
 * label that's really just the mark again ("7", "6.5", "7/10") as no label at all, matching the
 * web's `isGradeLike` guard.
 */
fun gradeDisplay(g: GradeEntry): String {
    val raw = g.text.ifEmpty { g.markName.ifEmpty { g.examType } }.trim()
    if (raw.isEmpty()) return "Prüfung"
    val normalized = raw.replace(',', '.').replace(Regex("[−–—]"), "-").trim()
    val gradeLike = Regex("""^(?:\d{1,2}(?:\.\d{1,2})?[+\-]?|\d{1,2}/\d{1,2})$""")
    return if (gradeLike.matches(normalized)) "Prüfung" else raw
}
