@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.grades

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.GradeMath
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.storage.GradeDraftEntry
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.medium
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.gradeColor

/**
 * Fach-Detail mit Notenrechner + Zielnote-Rechner — port of `GradeSubjectView`, 1:1.
 * `newGradeInput`/`targetInput` are pure UI-local text, exactly like the Swift `@State` strings;
 * everything that must survive navigation (removed/added draft grades) lives in the ViewModel,
 * persisted through [dev.plattnericus.pokyh.data.storage.PreferencesStore].
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
            TopAppBar(
                title = { Text(subject?.subjectName ?: "Fach", style = PokyhType.headline) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(PokyhIcons.arrow_left, contentDescription = "Zurück", tint = PokyhTheme.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                subject == null -> EmptyStateView(icon = PokyhIcons.chart_bar_fill, title = "Fach nicht gefunden")
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

private data class TeacherRow(val id: Int, val label: String, val date: Int, val value: Double, val removed: Boolean)

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
            TeacherRow(id = g.id, label = gradeDisplay(g), date = g.date, value = GradeMath.round2(g.markDisplayValue), removed = g.id in removedIds)
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
        subject.grades.filter { it.id !in removedIds }.sortedBy { it.date }.map { GradeMath.round2(it.markDisplayValue) } + draft.customGrades
    }
    val targetResult = remember(targetInput, liveValues) { GradeMath.target(targetInput, liveValues) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AverageCard(
            avg = liveAvg, hasDraft = hasDraft, positive = positive, negative = negative,
            count = liveValues.size, modifier = Modifier.fadeIn(),
        )
        if (chronological.size >= 2) TrendChart(chronological, modifier = Modifier.fadeIn(delayMillis = 50))
        GradesCard(
            teacherRows = teacherRows,
            customGrades = draft.customGrades,
            newGradeInput = newGradeInput,
            onNewGradeInputChange = onNewGradeInputChange,
            onAddCustom = onAddCustom,
            onToggleTeacherGrade = onToggleTeacherGrade,
            onRemoveCustom = onRemoveCustom,
            modifier = Modifier.fadeIn(delayMillis = 80),
        )
        TargetCard(
            targetInput = targetInput, onTargetInputChange = onTargetInputChange,
            targetResult = targetResult, liveAvg = liveAvg,
            modifier = Modifier.fadeIn(delayMillis = 120),
        )
        if (hasDraft) {
            OutlinedButton(
                onClick = onReset,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand.danger),
                border = BorderStroke(1.dp, Brand.danger),
                modifier = Modifier.fillMaxWidth().fadeIn(delayMillis = 150),
            ) {
                Icon(PokyhIcons.arrow_counterclockwise, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Rechner zurücksetzen")
            }
        }
    }
}

// ── Schnitt ───────────────────────────────────────────────────────────────

@Composable
private fun AverageCard(avg: Double, hasDraft: Boolean, positive: Int, negative: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().cardSurface(radius = 18.dp).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (hasDraft) "Schnitt (mit Rechner)" else "Schnitt", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
            Text(
                text = if (avg > 0) Fmt.num(avg) else "–",
                style = PokyhType.subjectAverage,
                color = if (avg > 0) gradeColor(avg) else PokyhTheme.colors.textSecondary,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("$positive positiv", Brand.tint)
            Pill("$negative negativ", Brand.danger)
            Text("$count Noten", style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text = text,
        style = PokyhType.caption2.semibold(),
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ── Noten + Notenrechner ─────────────────────────────────────────────────

@Composable
private fun GradesCard(
    teacherRows: List<TeacherRow>,
    customGrades: List<Double>,
    newGradeInput: String,
    onNewGradeInputChange: (String) -> Unit,
    onAddCustom: () -> Unit,
    onToggleTeacherGrade: (Int) -> Unit,
    onRemoveCustom: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().cardSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Noten", style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
        teacherRows.forEach { row ->
            GradeRow(
                value = row.value, label = row.label, sub = Fmt.dateFull(row.date),
                removed = row.removed, isCustom = false,
                onAction = { onToggleTeacherGrade(row.id) },
            )
        }
        customGrades.forEachIndexed { idx, value ->
            GradeRow(
                value = value, label = "Eigene Note", sub = "Notenrechner",
                removed = false, isCustom = true,
                onAction = { onRemoveCustom(idx) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newGradeInput,
                onValueChange = onNewGradeInputChange,
                modifier = Modifier.weight(1f).background(PokyhTheme.colors.cardAlt, PokyhShapes.r10),
                placeholder = { Text("Note (1–10)", style = PokyhType.subheadline) },
                textStyle = PokyhType.subheadline,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                ),
            )
            Button(
                onClick = onAddCustom,
                enabled = GradeMath.parseGradeInput(newGradeInput) != null,
                colors = ButtonDefaults.buttonColors(containerColor = Brand.accent),
            ) {
                Icon(PokyhIcons.plus, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Hinzufügen")
            }
        }
    }
}

@Composable
private fun GradeRow(value: Double, label: String, sub: String, removed: Boolean, isCustom: Boolean, onAction: () -> Unit) {
    val bubbleColor = if (removed) PokyhTheme.colors.textTertiary else gradeColor(value)
    val digits = if (value == kotlin.math.round(value)) 0 else 1
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(bubbleColor.copy(alpha = 0.18f), CircleShape)
                .then(
                    if (removed) {
                        Modifier.border(1.dp, PokyhTheme.colors.textTertiary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(Fmt.num(value, digits = digits), style = PokyhType.headline.monospacedDigits(), color = bubbleColor)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = label,
                style = PokyhType.subheadline.medium(),
                color = if (removed) PokyhTheme.colors.textTertiary else PokyhTheme.colors.textPrimary,
                textDecoration = if (removed) TextDecoration.LineThrough else null,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isCustom) Icon(PokyhIcons.function, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(11.dp))
                Text(sub, style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
            }
        }
        IconButton(onClick = onAction, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (isCustom) PokyhIcons.trash else if (removed) PokyhIcons.arrow_uturn_backward else PokyhIcons.xmark,
                contentDescription = null,
                tint = if (isCustom) Brand.danger else if (removed) Brand.accent else PokyhTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Zielnote-Rechner ─────────────────────────────────────────────────────

@Composable
private fun TargetCard(
    targetInput: String,
    onTargetInputChange: (String) -> Unit,
    targetResult: GradeMath.TargetResult?,
    liveAvg: Double,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().cardSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PokyhIcons.target, contentDescription = null, tint = PokyhTheme.colors.textPrimary, modifier = Modifier.size(17.dp))
            Text("Zielnote-Rechner", style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
        }
        OutlinedTextField(
            value = targetInput,
            onValueChange = onTargetInputChange,
            modifier = Modifier.fillMaxWidth().background(PokyhTheme.colors.cardAlt, PokyhShapes.r10),
            placeholder = { Text("Zielnote (z. B. 7)", style = PokyhType.subheadline) },
            textStyle = PokyhType.subheadline,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
            ),
        )
        when {
            targetResult != null -> Text(
                text = targetText(targetResult, liveAvg),
                style = PokyhType.subheadline,
                color = targetColor(targetResult),
            )
            targetInput.isNotEmpty() -> Text(
                text = "Gib eine gültige Note (1–10) ein.",
                style = PokyhType.caption,
                color = PokyhTheme.colors.textTertiary,
            )
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
private fun targetColor(r: GradeMath.TargetResult): Color = when (r.status) {
    GradeMath.TargetStatus.REACHED -> Brand.tint
    GradeMath.TargetStatus.IMPOSSIBLE -> Brand.danger
    GradeMath.TargetStatus.REACHABLE -> PokyhTheme.colors.textSecondary
}
