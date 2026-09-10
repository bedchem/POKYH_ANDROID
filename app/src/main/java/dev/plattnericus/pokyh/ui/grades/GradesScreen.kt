@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.GradeMath
import dev.plattnericus.pokyh.data.model.GradeEntry
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.gradeColor
import dev.plattnericus.pokyh.ui.theme.pressable
import dev.plattnericus.pokyh.ui.theme.slideInTrailing
import dev.plattnericus.pokyh.ui.theme.subjectColor
import kotlin.math.min

/** Port of `GradesView`. */
@Composable
fun GradesScreen(
    onSubjectClick: (Int) -> Unit,
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
            TopAppBar(
                title = { Text("Noten", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
                actions = {
                    YearMenu(
                        year = year,
                        availableYears = viewModel.availableYears,
                        onSelect = viewModel::selectYear,
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
                    icon = PokyhIcons.chart_bar_fill,
                    title = "Keine Noten",
                    subtitle = "Für dieses Schuljahr liegen keine Noten vor.",
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
        subjects.flatMap { s -> s.grades.filter { it.markDisplayValue > 0 }.map { RecentItem(it.id, s.subjectName, it.markDisplayValue, it.date) } }
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

    // VStack (not Lazy) — every subject renders immediately, none deferred to scroll.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AverageCard(overallAverage = overallAverage, subjectCount = subjects.size, gradeCount = totalGrades, modifier = Modifier.fadeIn())
        if (recent.isNotEmpty()) RecentCard(recent, modifier = Modifier.fadeIn(delayMillis = 50))
        SortBar(sort = sort, onSortChange = onSortChange, modifier = Modifier.fadeIn(delayMillis = 80))
        sortedSubjects.forEachIndexed { idx, subject ->
            SubjectRow(
                subject = subject,
                modifier = Modifier.fadeIn(delayMillis = min(500, 100 + idx * 25)),
                onClick = { onSubjectClick(subject.lessonId) },
            )
        }
    }
}

private data class RecentItem(val id: Int, val subject: String, val value: Double, val date: Int)

@Composable
private fun AverageCard(overallAverage: Double, subjectCount: Int, gradeCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().cardSurface(radius = 18.dp).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Durchschnittsnote", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
            Text("Alle Fächer", style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
            Text(
                text = if (overallAverage > 0) Fmt.num(overallAverage) else "–",
                style = PokyhType.gradeAverage,
                color = if (overallAverage > 0) gradeColor(overallAverage) else PokyhTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$subjectCount Fächer", style = PokyhType.subheadline.copy(fontWeight = FontWeight.Medium), color = PokyhTheme.colors.textPrimary)
            Text("$gradeCount Noten", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun RecentCard(items: List<RecentItem>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().cardSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(PokyhIcons.clock_arrow_circlepath, contentDescription = null, tint = PokyhTheme.colors.textSecondary, modifier = Modifier.size(13.dp))
            Text("Zuletzt hinzugefügt", style = PokyhType.caption.copy(fontWeight = FontWeight.Bold), color = PokyhTheme.colors.textSecondary)
        }
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(42.dp).background(gradeColor(item.value).copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(Fmt.num(item.value, digits = 1), style = PokyhType.headline.monospacedDigits(), color = gradeColor(item.value))
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(item.subject, style = PokyhType.subheadline.copy(fontWeight = FontWeight.Medium), color = PokyhTheme.colors.textPrimary)
                    Text(Fmt.dateFull(item.date), style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun SortBar(sort: GradesViewModel.SortMode, onSortChange: (GradesViewModel.SortMode) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Fächer", style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
        Spacer(Modifier.weight(1f))
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickableNoRipple { expanded = true }.padding(4.dp),
            ) {
                Icon(PokyhIcons.arrow_up_arrow_down, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(14.dp))
                Text(sort.label, style = PokyhType.caption.copy(fontWeight = FontWeight.Medium), color = Brand.accent)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GradesViewModel.SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (sort == mode) Icon(PokyhIcons.checkmark, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(14.dp))
                                Text(mode.label)
                            }
                        },
                        onClick = { onSortChange(mode); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
fun SubjectRow(subject: SubjectGrades, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(radius = 14.dp)
            .pressable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(12.dp).background(subjectColor(subject.subjectName), CircleShape))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(subject.subjectName, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
            val teacherSuffix = if (subject.teacherName.isEmpty()) "" else " · ${subject.teacherName}"
            Text("${subject.grades.size} Noten$teacherSuffix", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
        }
        Text(
            text = if (subject.average > 0) Fmt.num(subject.average) else "–",
            style = PokyhType.title3.bold().monospacedDigits(),
            color = if (subject.average > 0) gradeColor(subject.average) else PokyhTheme.colors.textSecondary,
        )
        Icon(PokyhIcons.chevron_right, contentDescription = null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun YearMenu(year: Int, availableYears: List<Int>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.slideInTrailing()) {
        TextButton(onClick = { expanded = true }) {
            Text("$year/${(year + 1) % 100}", style = PokyhType.subheadline, color = PokyhTheme.colors.textPrimary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableYears.forEach { y ->
                DropdownMenuItem(
                    text = { Text("$y/${(y + 1) % 100}") },
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

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
