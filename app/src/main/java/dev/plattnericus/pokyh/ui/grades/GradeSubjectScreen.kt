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
import dev.plattnericus.pokyh.core.i18n.GradesStrings
import dev.plattnericus.pokyh.core.i18n.strings
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
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.gradeColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.theme.PokyhShapes

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
                title = subject?.subjectName ?: strings().grades.subject,
                // The teacher, and when the latest grade came (web subtitle).
                eyebrow = listOfNotNull(
                    subject?.teacherName?.ifEmpty { null },
                    subject?.grades?.filter { it.date > 0 }?.maxOfOrNull { it.date }?.let { strings().grades.lastGrade + " " + Fmt.dateFull(it) },
                ).joinToString(" · ").ifEmpty { null },
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                subject == null -> EmptyStateView(
                    sprite = PixelSprite.Search,
                    title = strings().grades.subjectNotFound,
                    action = { PokyhTextButton(text = strings().grades.allSubjects, onClick = onNavigateBack) },
                )
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
                    onAddValue = viewModel::addCustomValue,
                )
            }
        }
    }
}

private data class TeacherRow(
    val id: Long,
    val label: String,
    val date: Int,
    val value: Double,
    val removed: Boolean,
)

/** The web's three tones for a grade: from 6.4 sage, from 6 butter, below blush. */
@Composable
private fun gradeTone(v: Double): dev.plattnericus.pokyh.ui.theme.DecorativeTone {
    val dark = PokyhTheme.colors.isDark
    return when {
        v >= 6.4 -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.sage(dark)
        v >= 6 -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.butter(dark)
        else -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(dark)
    }
}

/** "heute", "gestern", "vor 3 Tagen" … (web relativeDay). */
private fun relativeDay(date: Int): String {
    val d = runCatching { java.time.LocalDate.of(date / 10000, date / 100 % 100, date % 100) }.getOrNull() ?: return ""
    val diff = java.time.temporal.ChronoUnit.DAYS.between(d, java.time.LocalDate.now()).toInt()
    val t = dev.plattnericus.pokyh.core.i18n.I18n.s.grades
    return when {
        diff == 0 -> t.relToday
        diff == 1 -> t.relYesterday
        diff < 7 -> t.daysAgo(diff)
        diff < 30 -> t.weeksAgo(Math.round(diff / 7.0).toInt())
        diff < 365 -> t.monthsAgo(Math.round(diff / 30.0).toInt())
        else -> t.yearsAgo(Math.round(diff / 365.0).toInt())
    }
}

/** What a grade field takes, as on the web: up to two digits, a comma or point, two decimals. */
private fun gradeTyping(v: String) = Regex("^\\d{0,2}([.,]\\d{0,2})?$").matches(v)

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
    onToggleTeacherGrade: (Long) -> Unit,
    onReset: () -> Unit,
    onAddValue: (Double) -> Unit,
) {
    val removedIds = remember(draft) { draft.removedTeacherGradeIds.toSet() }
    val teacherRows = remember(subject, removedIds) {
        subject.grades.filter { it.date > 0 }.sortedByDescending { it.date }.map { g ->
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
    val teacherAvg = remember(teacherRows) { GradeMath.averageOf(teacherRows.map { it.value }) }
    val positive = remember(liveValues) { liveValues.count { it >= 6 } }
    val negative = remember(liveValues) { liveValues.count { it < 6 } }
    val hasDraft = !draft.isEmpty

    // Chronologically: the teacher's grades by date, the own ones after them.
    val chronological = remember(teacherRows, draft.customGrades) {
        teacherRows.filter { !it.removed }.sortedBy { it.date }.map { TrendPoint(it.value, it.date, it.label, own = false) } +
            draft.customGrades.map { TrendPoint(it, null, dev.plattnericus.pokyh.core.i18n.I18n.s.grades.ownGrade, own = true) }
    }
    val trend = remember(chronological) {
        if (chronological.size < 2) 0 else (chronological.last().value - chronological.first().value).let { if (kotlin.math.abs(it) < 0.3) 0 else if (it > 0) 1 else -1 }
    }
    val targetResult = remember(targetInput, liveValues) { GradeMath.target(targetInput, liveValues) }
    val wide = dev.plattnericus.pokyh.ui.theme.widthClass() != dev.plattnericus.pokyh.ui.theme.WidthClass.Compact

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
    ) {
        val cards: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> AverageCard(liveAvg, hasDraft, liveValues, teacherAvg, trend, m) },
            { m -> RatioCard(positive, negative, m) },
            { m -> TargetCard(targetInput, onTargetInputChange, targetResult, m) },
        )
        if (wide) {
            Row(Modifier.fillMaxWidth().fadeIn(), horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                cards.forEach { it(Modifier.weight(1f)) }
            }
        } else {
            Column(Modifier.fadeIn(), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) { cards.forEach { it(Modifier.fillMaxWidth()) } }
        }

        PokyhSection(
            title = strings().grades.trendTitle,
            trailing = if (hasDraft) { { PokyhTextButton(text = strings().grades.reset, icon = PokyhIcons.reset, onClick = onReset) } } else null,
            modifier = Modifier.fadeIn(delayMillis = 50),
        ) {
            if (chronological.isEmpty()) {
                Text(strings().grades.noGradesYet, style = PokyhType.callout, color = PokyhTheme.colors.textTertiary)
            } else {
                TrendChart(chronological)
            }
            Text(strings().grades.trendSub, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary, modifier = Modifier.padding(top = PokyhSpacing.xs))
        }

        GradesSection(
            teacherRows = teacherRows,
            onToggleTeacherGrade = onToggleTeacherGrade,
            modifier = Modifier.fadeIn(delayMillis = 80),
        )

        CalcSection(
            customGrades = draft.customGrades,
            newGradeInput = newGradeInput,
            onNewGradeInputChange = onNewGradeInputChange,
            onAddCustom = onAddCustom,
            onAddValue = onAddValue,
            onRemoveCustom = onRemoveCustom,
            modifier = Modifier.fadeIn(delayMillis = 110),
        )
    }
}

// ── Schnitt · Notenverhältnis · Was brauche ich? ───────────────────────────

@Composable
private fun AverageCard(avg: Double, hasDraft: Boolean, values: List<Double>, teacherAvg: Double, trend: Int, modifier: Modifier = Modifier) {
    val g = strings().grades
    val dark = PokyhTheme.colors.isDark
    val tone = if (avg > 0) gradeTone(avg) else dev.plattnericus.pokyh.ui.theme.DecorativeTone(PokyhTheme.colors.card, PokyhTheme.colors.textPrimary)
    val chip = when (trend) {
        1 -> Triple(g.rising, PokyhIcons.trendUp, dev.plattnericus.pokyh.ui.theme.PokyhDecorative.sage(dark))
        -1 -> Triple(g.falling, PokyhIcons.trendUp, dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(dark))
        else -> Triple(g.stable, PokyhIcons.trendFlat, dev.plattnericus.pokyh.ui.theme.DecorativeTone(PokyhTheme.colors.nested, PokyhTheme.colors.textSecondary))
    }
    val delta = if (avg > 0 && teacherAvg > 0) avg - teacherAvg else 0.0
    Column(
        modifier.background(tone.fill, PokyhShapes.xxl).padding(22.dp).defaultMinSize(minHeight = 150.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(g.average, style = PokyhType.callout.copy(fontWeight = FontWeight.Bold), color = tone.ink)
                Text(g.gradeCount(values.size) + if (hasDraft) " · " + g.simulation else "", style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold), color = tone.ink.copy(alpha = 0.75f))
            }
            Row(
                Modifier.background(chip.third.fill, PokyhShapes.pill).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                androidx.compose.material3.Icon(chip.second, contentDescription = null, tint = chip.third.ink, modifier = Modifier.size(14.dp).then(if (trend == -1) Modifier.rotate(90f) else Modifier))
                Text(chip.first, style = PokyhType.caption.copy(fontWeight = FontWeight.Bold), color = chip.third.ink)
            }
        }
        Spacer(Modifier.size(PokyhSpacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(if (avg > 0) Fmt.num(avg) else "—", style = PokyhType.statLarge, color = tone.ink, modifier = Modifier.weight(1f))
            when {
                hasDraft && kotlin.math.abs(delta) > 0.005 -> Column(horizontalAlignment = Alignment.End) {
                    Text(g.teacherAvg + " " + Fmt.num(teacherAvg), style = PokyhType.caption.copy(fontWeight = FontWeight.SemiBold), color = tone.ink.copy(alpha = 0.8f))
                    Text((if (delta >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(delta)), style = PokyhType.callout.copy(fontWeight = FontWeight.ExtraBold), color = tone.ink)
                }
                values.isNotEmpty() -> Text(g.best(Fmt.num(values.max())), style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold), color = tone.ink.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun RatioCard(positive: Int, negative: Int, modifier: Modifier = Modifier) {
    val g = strings().grades
    val colors = PokyhTheme.colors
    val total = positive + negative
    val rate = if (total > 0) positive * 100.0 / total else 0.0
    Column(
        modifier.background(colors.card, PokyhShapes.xxl).padding(22.dp).defaultMinSize(minHeight = 150.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(g.ratioTitle, style = PokyhType.callout.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                Text(g.ratioSub, style = PokyhType.footnote, color = colors.textSecondary)
            }
            Text("${Math.round(rate)} %", style = PokyhType.caption.copy(fontWeight = FontWeight.Bold), color = colors.textSecondary, modifier = Modifier.background(colors.nested, PokyhShapes.pill).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        Spacer(Modifier.size(PokyhSpacing.lg))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$positive", style = PokyhType.statLarge, color = Brand.success)
            Text("/", style = PokyhType.title2, color = colors.textTertiary)
            Text("$negative", style = PokyhType.title1.copy(fontWeight = FontWeight.ExtraBold), color = Brand.danger)
        }
        Spacer(Modifier.size(PokyhSpacing.md))
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (total == 0) Box(Modifier.weight(1f).fillMaxHeight().background(colors.nested, PokyhShapes.pill))
            if (positive > 0) Box(Modifier.weight(positive.toFloat()).fillMaxHeight().background(Brand.success, PokyhShapes.pill))
            if (negative > 0) Box(Modifier.weight(negative.toFloat()).fillMaxHeight().background(Brand.danger, PokyhShapes.pill))
        }
    }
}

@Composable
private fun TargetCard(targetInput: String, onTargetInputChange: (String) -> Unit, r: GradeMath.TargetResult?, modifier: Modifier = Modifier) {
    val g = strings().grades
    val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.periwinkle(PokyhTheme.colors.isDark)
    Column(
        modifier.background(tone.fill, PokyhShapes.xxl).padding(22.dp).defaultMinSize(minHeight = 150.dp),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(g.whatDoINeed, style = PokyhType.callout.copy(fontWeight = FontWeight.Bold), color = tone.ink)
                Text(g.targetCalc, style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold), color = tone.ink.copy(alpha = 0.75f))
            }
            androidx.compose.material3.Icon(PokyhIcons.target, contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.foundation.text.BasicTextField(
                value = targetInput,
                onValueChange = { if (gradeTyping(it)) onTargetInputChange(it) },
                singleLine = true,
                textStyle = PokyhType.title2.copy(fontWeight = FontWeight.ExtraBold, color = tone.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tone.ink),
                modifier = Modifier.width(96.dp).background(tone.ink.copy(alpha = 0.12f), PokyhShapes.lg).padding(vertical = 12.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (targetInput.isEmpty()) Text(g.targetShort, style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = tone.ink.copy(alpha = 0.55f))
                        inner()
                    }
                },
            )
            androidx.compose.material3.Icon(PokyhIcons.chevronRight, contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                when {
                    r == null -> Text("-", style = PokyhType.title1.copy(fontWeight = FontWeight.ExtraBold), color = tone.ink)
                    r.status == GradeMath.TargetStatus.REACHED -> Text("✓", style = PokyhType.title1.copy(fontWeight = FontWeight.ExtraBold), color = tone.ink)
                    r.status == GradeMath.TargetStatus.IMPOSSIBLE -> Text("✕", style = PokyhType.title1.copy(fontWeight = FontWeight.ExtraBold), color = tone.ink)
                    else -> {
                        if (r.count > 1) Text("${r.count}× ", style = PokyhType.headline, color = tone.ink.copy(alpha = 0.75f))
                        Text(Fmt.num(r.needed), style = PokyhType.title1.copy(fontWeight = FontWeight.ExtraBold), color = tone.ink)
                    }
                }
            }
        }
        Text(
            when {
                r == null -> g.hintEnter
                r.status == GradeMath.TargetStatus.REACHED -> g.hintReached
                r.status == GradeMath.TargetStatus.IMPOSSIBLE -> g.hintImpossible
                r.count == 1 -> g.hintNext
                else -> g.hintMany(r.count, Fmt.num(r.needed))
            },
            style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = tone.ink.copy(alpha = 0.8f),
        )
    }
}

// ── Notenliste + Mittelwert-Rechner ──────────────────────────────────────────

@Composable
private fun GradesSection(teacherRows: List<TeacherRow>, onToggleTeacherGrade: (Long) -> Unit, modifier: Modifier = Modifier) {
    val g = strings().grades
    PokyhSection(
        title = g.gradeList,
        trailing = { Text(g.activeExcluded(teacherRows.count { !it.removed }, teacherRows.count { it.removed }), style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold), color = PokyhTheme.colors.textSecondary) },
        modifier = modifier,
    ) {
        if (teacherRows.isEmpty()) {
            Text(g.noGradesYet, style = PokyhType.callout, color = PokyhTheme.colors.textTertiary)
        } else {
            PokyhListCard(items = teacherRows) { row -> TeacherGradeRow(row, onToggle = { onToggleTeacherGrade(row.id) }) }
        }
    }
}

@Composable
private fun TeacherGradeRow(row: TeacherRow, onToggle: () -> Unit) {
    val colors = PokyhTheme.colors
    val tone = gradeTone(row.value)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.card, vertical = 10.dp).then(if (row.removed) Modifier.alpha(0.45f) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        Text(
            Fmt.num(row.value),
            style = PokyhType.callout.copy(fontWeight = FontWeight.ExtraBold, textDecoration = if (row.removed) TextDecoration.LineThrough else null),
            color = if (row.removed) colors.textTertiary else tone.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.defaultMinSize(minWidth = 52.dp).background(if (row.removed) colors.nested else tone.fill, PokyhShapes.md).padding(horizontal = 8.dp, vertical = 10.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(row.label, style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(Fmt.dateShort(row.date) + " · " + relativeDay(row.date), style = PokyhType.footnote, color = colors.textSecondary)
        }
        PokyhIconButton(
            icon = if (row.removed) PokyhIcons.undo else PokyhIcons.hidePassword,
            contentDescription = if (row.removed) strings().grades.restoreGrade else strings().grades.excludeGrade,
            onClick = onToggle,
            tint = colors.textTertiary,
            size = 36.dp,
            iconSize = 17.dp,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CalcSection(
    customGrades: List<Double>,
    newGradeInput: String,
    onNewGradeInputChange: (String) -> Unit,
    onAddCustom: () -> Unit,
    onAddValue: (Double) -> Unit,
    onRemoveCustom: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val g = strings().grades
    val colors = PokyhTheme.colors
    PokyhSection(title = g.avgCalc, modifier = modifier) {
        Column(Modifier.fillMaxWidth().background(colors.card, PokyhShapes.xl).padding(PokyhSpacing.card), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Text(g.avgCalcSub, style = PokyhType.footnote, color = colors.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                PokyhTextField(
                    value = newGradeInput,
                    onValueChange = { if (gradeTyping(it)) onNewGradeInputChange(it) },
                    placeholder = g.examplePlaceholder,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (GradeMath.parseGradeInput(newGradeInput) != null) onAddCustom() }),
                )
                PokyhIconButton(
                    icon = PokyhIcons.add,
                    contentDescription = g.add,
                    onClick = onAddCustom,
                    enabled = GradeMath.parseGradeInput(newGradeInput) != null,
                    tint = Brand.onAccent,
                    containerColor = Brand.accent,
                )
            }
            // Quick test: a tap adds the grade (in its tone).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (v in 4..10) {
                    val tone = gradeTone(v.toDouble())
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(PokyhShapes.md).background(tone.fill).clickable { onAddValue(v.toDouble()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$v", style = PokyhType.callout.copy(fontWeight = FontWeight.ExtraBold), color = tone.ink)
                    }
                }
            }
            if (customGrades.isEmpty()) {
                Text(g.noOwnGrades, style = PokyhType.footnote, color = colors.textTertiary)
            } else {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    customGrades.forEachIndexed { idx, value ->
                        Row(
                            Modifier.clip(PokyhShapes.pill).background(colors.nested).clickable { onRemoveCustom(idx) }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(Fmt.num(value), style = PokyhType.footnote.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                            androidx.compose.material3.Icon(PokyhIcons.close, contentDescription = g.removeOwnGrade, tint = colors.textTertiary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}
