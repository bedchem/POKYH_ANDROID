@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.GradeMath
import dev.plattnericus.pokyh.data.model.GradeEntry
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhStat
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.home.GradeBubble
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.profile.CurrentUserAvatar
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.gradeColor
import dev.plattnericus.pokyh.ui.theme.slideInTrailing
import dev.plattnericus.pokyh.ui.theme.subjectColor

/**
 * Grades. The overall average is the screen's one hero number, then the newest entries, then
 * every subject as a row of one grouped list — so the page reads average, recent, all, which is
 * the order the question actually gets asked in.
 */
@Composable
fun GradesScreen(
    onSubjectClick: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: GradesViewModel = hiltViewModel(),
) {
    val year by viewModel.year.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Noten",
                eyebrow = "Schuljahr $year/${(year + 1) % 100}",
                nav = TopBarNav.None,
                actions = {
                    YearMenu(
                        year = year,
                        availableYears = viewModel.availableYears,
                        onSelect = viewModel::selectYear,
                    )
                    TabRootActions(
                        avatarContent = { CurrentUserAvatar() },
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
                subjects.isEmpty() -> EmptyStateView(
                    icon = PokyhIcons.grades,
                    title = "Keine Noten",
                    subtitle = "Für dieses Schuljahr liegen noch keine Noten vor.",
                )
                else -> GradesContent(
                    subjects = subjects,
                    sort = sort,
                    onSortChange = viewModel::selectSort,
                    onSubjectClick = onSubjectClick,
                )
            }
        }
    }
}

@Composable
private fun GradesContent(
    subjects: List<SubjectGrades>,
    sort: GradesViewModel.SortMode,
    onSortChange: (GradesViewModel.SortMode) -> Unit,
    onSubjectClick: (Int) -> Unit,
) {
    // Gesamtschnitt = flacher Mittelwert über ALLE Einzelnoten (jede Note gleich gewichtet) —
    // NICHT der Mittelwert der Fach-Schnitte.
    val overallAverage = remember(subjects) {
        GradeMath.averageOf(subjects.flatMap { s -> s.grades.map { it.markDisplayValue } }.filter { it > 0 })
    }
    // „Zuletzt hinzugefügt" = nach Noten-ID absteigend (zuletzt eingetragen), NICHT nach Datum.
    val recent = remember(subjects) {
        subjects
            .flatMap { s ->
                s.grades
                    .filter { it.markDisplayValue > 0 }
                    .map { RecentItem(it.id, s.subjectName, it.markDisplayValue, it.date) }
            }
            .sortedByDescending { it.id }
            .take(3)
    }
    val sortedSubjects = remember(subjects, sort) {
        when (sort) {
            GradesViewModel.SortMode.NAME -> subjects.sortedBy { it.subjectName.lowercase() }
            GradesViewModel.SortMode.AVG_DESC -> subjects.sortedByDescending { it.average }
            GradesViewModel.SortMode.AVG_ASC -> subjects.sortedBy { it.average }
            GradesViewModel.SortMode.RECENT -> subjects.sortedByDescending { s -> s.grades.maxOfOrNull { it.date } ?: 0 }
        }
    }
    val totalGrades = remember(subjects) { subjects.sumOf { it.grades.size } }

    // Column (not Lazy) — every subject renders immediately, none deferred to scroll.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
    ) {
        AverageCard(
            overallAverage = overallAverage,
            subjectCount = subjects.size,
            gradeCount = totalGrades,
            modifier = Modifier.fadeIn(),
        )

        if (recent.isNotEmpty()) {
            PokyhSection(title = "Zuletzt hinzugefügt", modifier = Modifier.fadeIn(delayMillis = 50)) {
                PokyhListCard(items = recent) { item ->
                    PokyhRow(
                        title = item.subject,
                        subtitle = Fmt.dateFull(item.date),
                        showChevron = false,
                        leading = { GradeBubble(item.value) },
                    )
                }
            }
        }

        PokyhSection(
            title = "Fächer",
            trailing = { SortMenu(sort = sort, onSortChange = onSortChange) },
            modifier = Modifier.fadeIn(delayMillis = 80),
        ) {
            PokyhListCard(items = sortedSubjects) { subject ->
                SubjectRow(
                    subject = subject,
                    onClick = { onSubjectClick(subject.lessonId) },
                )
            }
        }
    }
}

private data class RecentItem(val id: Int, val subject: String, val value: Double, val date: Int)

/**
 * The screen's hero: the overall average at [PokyhType.statLarge] in its own grade color, with
 * the two counts as quiet [PokyhStat]s beside it. One big number, nothing else competing.
 */
@Composable
private fun AverageCard(
    overallAverage: Double,
    subjectCount: Int,
    gradeCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                PokyhLabel("Durchschnitt · alle Fächer")
                Spacer(Modifier.size(PokyhSpacing.sm))
                Text(
                    text = if (overallAverage > 0) Fmt.num(overallAverage) else "–",
                    style = PokyhType.statLarge,
                    color = if (overallAverage > 0) gradeColor(overallAverage) else colors.textSecondary,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
            ) {
                PokyhStat("$subjectCount", "Fächer", alignment = Alignment.End)
                PokyhStat("$gradeCount", "Noten", alignment = Alignment.End)
            }
        }
    }
}

@Composable
private fun SortMenu(sort: GradesViewModel.SortMode, onSortChange: (GradesViewModel.SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PokyhTextButton(
            text = sort.label,
            icon = PokyhIcons.sort,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                    onClick = { onSortChange(mode); expanded = false },
                )
            }
        }
    }
}

/**
 * A subject row. Leading dot in the subject's own color (the cross-platform hashed
 * [subjectColor]), the average trailing in its grade color — the two colors that mean something
 * here, and nothing else tinted.
 */
@Composable
fun SubjectRow(subject: SubjectGrades, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    val teacherSuffix = if (subject.teacherName.isEmpty()) "" else " · ${subject.teacherName}"
    PokyhRow(
        title = subject.subjectName,
        subtitle = "${subject.grades.size} Noten$teacherSuffix",
        modifier = modifier,
        onClick = onClick,
        leading = {
            Box(
                Modifier
                    .size(10.dp)
                    .background(subjectColor(subject.subjectName), PokyhShapes.pill),
            )
        },
        trailing = {
            Text(
                text = if (subject.average > 0) Fmt.num(subject.average) else "–",
                style = PokyhType.statSmall.monospacedDigits(),
                color = if (subject.average > 0) gradeColor(subject.average) else colors.textSecondary,
            )
        },
    )
}

@Composable
private fun YearMenu(year: Int, availableYears: List<Int>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.slideInTrailing()) {
        PokyhTextButton(
            text = "$year/${(year + 1) % 100}",
            onClick = { expanded = true },
            color = PokyhTheme.colors.textPrimary,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableYears.forEach { y ->
                DropdownMenuItem(
                    text = { Text("$y/${(y + 1) % 100}", style = PokyhType.body) },
                    onClick = { onSelect(y); expanded = false },
                )
            }
        }
    }
}

/** `gradeDisplay(_:)` — falls back through text -> markName -> examType -> "Prüfung". */
fun gradeDisplay(g: GradeEntry): String {
    val raw = g.text.ifEmpty { g.markName.ifEmpty { g.examType } }
    return raw.trim().ifEmpty { "Prüfung" }
}
