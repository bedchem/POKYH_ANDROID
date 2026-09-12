@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.grades

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.GradeMath
import dev.plattnericus.pokyh.data.storage.GradeDraftEntry
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhDestructiveButton
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.home.GradeBubble
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.gradeColor

/**
 * Fach-Detail with the grade calculator and the target-grade calculator.
 *
 * `newGradeInput`/`targetInput` are UI-local text; everything that must survive navigation
 * (removed/added draft grades) lives in the ViewModel, persisted through
 * [dev.plattnericus.pokyh.data.storage.PreferencesStore].
 *
 * Layout mirrors [GradesScreen]: hero average, then the trend, then the grade list, then the
 * calculators — so drilling into a subject feels like the same screen zoomed in.
 */
@Composable
fun GradeSubjectScreen(
    onNavigateBack: () -> Unit,
    viewModel: GradeSubjectViewModel = hiltViewModel(),
) {
    val subject by viewModel.subject.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    var newGradeInput by remember { mutableStateOf("") }
    var targetInput by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = subject?.subjectName ?: "Fach",
                eyebrow = subject?.teacherName?.ifEmpty { null },
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                subject == null -> EmptyStateView(icon = PokyhIcons.grades, title = "Fach nicht gefunden")
                else -> GradeSubjectContent(
                    subject = subject!!,
                    draft = draft,
                    newGradeInput = newGradeInput,
                    onNewGradeInputChange = { newGradeInput = it },
                    targetInput = targetInput,
                    onTargetInputChange = { targetInput = it },
                    onAddCustom = { viewModel.addCustomGrade(newGradeInput); newGradeInput = "" },
                    onRemoveCustom = viewModel::removeCustomGrade,
                    onToggleTeacherGrade = viewModel::toggleTeacherGradeRemoved,
                    onReset = { viewModel.resetDraft(); newGradeInput = ""; targetInput = "" },
                )
            }
        }
    }
}

private data class TeacherRow(
    val id: Int,
    val label: String,
    val date: Int,
    val value: Double,
    val removed: Boolean,
)

@Composable
private fun GradeSubjectContent(
    subject: SubjectGrades,
    draft: GradeDraftEntry,
    newGradeInput: String,
    onNewGradeInputChange: (String) -> Unit,
    targetInput: String,
    onTargetInputChange: (String) -> Unit,
    onAddCustom: () -> Unit,
    onRemoveCustom: (Int) -> Unit,
    onToggleTeacherGrade: (Int) -> Unit,
    onReset: () -> Unit,
) {
    val removedIds = remember(draft) { draft.removedTeacherGradeIds.toSet() }
    val teacherRows = remember(subject, removedIds) {
        subject.grades.sortedByDescending { it.date }.map { g ->
            TeacherRow(
                id = g.id,
                label = gradeDisplay(g),
                date = g.date,
                value = GradeMath.round2(g.markDisplayValue),
                removed = g.id in removedIds,
            )
        }
    }
    val liveValues = remember(teacherRows, draft.customGrades) {
        teacherRows.filter { !it.removed }.map { it.value } + draft.customGrades
    }
    val liveAvg = remember(liveValues) { GradeMath.averageOf(liveValues) }
    val positive = remember(liveValues) { liveValues.count { it >= 6 } }
    val negative = remember(liveValues) { liveValues.count { it < 6 } }
    val hasDraft = !draft.isEmpty

    // Chronologisch (Lehrernoten nach Datum + eigene am Ende) für den Trend.
    val chronological = remember(subject, removedIds, draft.customGrades) {
        subject.grades
            .filter { it.id !in removedIds }
            .sortedBy { it.date }
            .map { GradeMath.round2(it.markDisplayValue) } + draft.customGrades
    }
    val targetResult = remember(targetInput, liveValues) { GradeMath.target(targetInput, liveValues) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
    ) {
        AverageCard(
            avg = liveAvg,
            hasDraft = hasDraft,
            positive = positive,
            negative = negative,
            count = liveValues.size,
            modifier = Modifier.fadeIn(),
        )

        if (chronological.size >= 2) {
            TrendChart(chronological, modifier = Modifier.fadeIn(delayMillis = 50))
        }

        GradesSection(
            teacherRows = teacherRows,
            customGrades = draft.customGrades,
            newGradeInput = newGradeInput,
            onNewGradeInputChange = onNewGradeInputChange,
            onAddCustom = onAddCustom,
            onToggleTeacherGrade = onToggleTeacherGrade,
            onRemoveCustom = onRemoveCustom,
            modifier = Modifier.fadeIn(delayMillis = 80),
        )

        TargetSection(
            targetInput = targetInput,
            onTargetInputChange = onTargetInputChange,
            targetResult = targetResult,
            liveAvg = liveAvg,
            modifier = Modifier.fadeIn(delayMillis = 120),
        )

        if (hasDraft) {
            PokyhDestructiveButton(
                text = "Rechner zurücksetzen",
                icon = PokyhIcons.reset,
                onClick = onReset,
                modifier = Modifier.fillMaxWidth().fadeIn(delayMillis = 150),
            )
        }
    }
}

// ── Schnitt ─────────────────────────────────────────────────────────────────

@Composable
private fun AverageCard(
    avg: Double,
    hasDraft: Boolean,
    positive: Int,
    negative: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                PokyhLabel(if (hasDraft) "Schnitt · mit Rechner" else "Schnitt")
                Spacer(Modifier.size(PokyhSpacing.sm))
                Text(
                    text = if (avg > 0) Fmt.num(avg) else "–",
                    style = PokyhType.statLarge,
                    color = if (avg > 0) gradeColor(avg) else colors.textSecondary,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                TagChip("$positive positiv", Brand.success)
                TagChip("$negative negativ", Brand.danger)
                Text("$count Noten", style = PokyhType.caption2, color = colors.textTertiary)
            }
        }
    }
}

// ── Noten + Notenrechner ────────────────────────────────────────────────────

@Composable
private fun GradesSection(
    teacherRows: List<TeacherRow>,
    customGrades: List<Double>,
    newGradeInput: String,
    onNewGradeInputChange: (String) -> Unit,
    onAddCustom: () -> Unit,
    onToggleTeacherGrade: (Int) -> Unit,
    onRemoveCustom: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhSection(title = "Noten", modifier = modifier) {
        PokyhListCard(items = teacherRows) { row ->
            GradeRow(
                value = row.value,
                label = row.label,
                sub = Fmt.dateFull(row.date),
                removed = row.removed,
                isCustom = false,
                onAction = { onToggleTeacherGrade(row.id) },
            )
        }
        if (customGrades.isNotEmpty()) {
            PokyhListCard(items = customGrades.withIndex().toList()) { (idx, value) ->
                GradeRow(
                    value = value,
                    label = "Eigene Note",
                    sub = "Notenrechner",
                    removed = false,
                    isCustom = true,
                    onAction = { onRemoveCustom(idx) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PokyhTextField(
                value = newGradeInput,
                onValueChange = onNewGradeInputChange,
                placeholder = "Note (1–10)",
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            PokyhPrimaryButton(
                text = "Hinzufügen",
                icon = PokyhIcons.add,
                onClick = onAddCustom,
                enabled = GradeMath.parseGradeInput(newGradeInput) != null,
            )
        }
    }
}

@Composable
private fun GradeRow(
    value: Double,
    label: String,
    sub: String,
    removed: Boolean,
    isCustom: Boolean,
    onAction: () -> Unit,
) {
    val colors = PokyhTheme.colors
    PokyhRow(
        title = label,
        subtitle = sub,
        titleColor = if (removed) colors.textTertiary else colors.textPrimary,
        titleDecoration = if (removed) TextDecoration.LineThrough else null,
        showChevron = false,
        leading = { GradeBubble(value = value, muted = removed) },
        trailing = {
            PokyhIconButton(
                icon = when {
                    isCustom -> PokyhIcons.delete
                    removed -> PokyhIcons.undo
                    else -> PokyhIcons.close
                },
                contentDescription = when {
                    isCustom -> "Eigene Note entfernen"
                    removed -> "Note wieder einrechnen"
                    else -> "Note aus dem Rechner nehmen"
                },
                onClick = onAction,
                tint = when {
                    isCustom -> Brand.danger
                    removed -> colors.accentText
                    else -> colors.textTertiary
                },
                size = 32.dp,
                iconSize = 18.dp,
            )
        },
    )
}

// ── Zielnote-Rechner ────────────────────────────────────────────────────────

@Composable
private fun TargetSection(
    targetInput: String,
    onTargetInputChange: (String) -> Unit,
    targetResult: GradeMath.TargetResult?,
    liveAvg: Double,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    PokyhSection(title = "Zielnote", modifier = modifier) {
        PokyhCard {
            PokyhTextField(
                value = targetInput,
                onValueChange = onTargetInputChange,
                placeholder = "Zielnote (z. B. 7)",
                leadingIcon = PokyhIcons.target,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            when {
                targetResult != null -> {
                    Spacer(Modifier.size(PokyhSpacing.md))
                    Text(
                        text = targetText(targetResult, liveAvg),
                        style = PokyhType.callout,
                        color = targetColor(targetResult),
                    )
                }
                targetInput.isNotEmpty() -> {
                    Spacer(Modifier.size(PokyhSpacing.md))
                    Text(
                        text = "Gib eine gültige Note (1–10) ein.",
                        style = PokyhType.footnote,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

private fun targetText(r: GradeMath.TargetResult, liveAvg: Double): String = when (r.status) {
    GradeMath.TargetStatus.REACHED -> "Du hast die Zielnote ${Fmt.num(r.target)} bereits erreicht."
    GradeMath.TargetStatus.IMPOSSIBLE -> "Die Zielnote ${Fmt.num(r.target)} ist nicht erreichbar."
    GradeMath.TargetStatus.REACHABLE -> {
        val verb = if (liveAvg < r.target) "mindestens" else "höchstens"
        val noun = if (r.count == 1) "Note" else "Noten"
        "Du brauchst ${r.count} $noun mit $verb ${Fmt.num(r.needed)}, um ${Fmt.num(r.target)} zu erreichen."
    }
}

@Composable
private fun targetColor(r: GradeMath.TargetResult) = when (r.status) {
    GradeMath.TargetStatus.REACHED -> Brand.success
    GradeMath.TargetStatus.IMPOSSIBLE -> Brand.danger
    GradeMath.TargetStatus.REACHABLE -> PokyhTheme.colors.textSecondary
}
