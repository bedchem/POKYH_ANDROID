@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.absences

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhSegmentedControl
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The absence calculator — the web's public tool (`components/tools/AbsenceCalculator.tsx`,
 * `lib/school-math.ts`): absences in percent and the hours left before the limit (25 %, the
 * three-quarter rule, by default). Opened from the Abwesenheiten page it is filled in with the
 * year's own numbers so far; everything stays in this screen, nothing is stored or sent.
 */
@Composable
fun AbsenceCalculatorScreen(
    absent: String,
    total: String,
    onNavigateBack: () -> Unit,
) {
    val t = strings().absences
    val dec = strings().common.decimal
    fun shown(v: String) = v.replace('.', dec).removeSuffix("${dec}0")
    val prefilled = total.isNotEmpty()
    var absentInput by rememberSaveable { mutableStateOf(shown(absent)) }
    var weeklyMode by rememberSaveable { mutableStateOf(!prefilled) }
    var totalInput by rememberSaveable { mutableStateOf(shown(total)) }
    var weeklyInput by rememberSaveable { mutableStateOf("") }
    var weeksInput by rememberSaveable { mutableStateOf("") }
    var limitInput by rememberSaveable { mutableStateOf("25") }

    val absentHours = parseAmount(absentInput)
    val weekly = parseAmount(weeklyInput)
    val weeks = parseAmount(weeksInput)
    val yearHours = if (weeklyMode) (if (weekly != null && weeks != null) weekly * weeks else null) else parseAmount(totalInput)
    val limit = parseAmount(limitInput)
    val stats = if (absentHours != null && yearHours != null && limit != null) absenceStats(absentHours, yearHours, minOf(limit, 100.0)) else null
    val tooMany = absentHours != null && yearHours != null && yearHours > 0 && absentHours > yearHours

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = { PokyhTopBar(title = t.calc, nav = TopBarNav.Back(onNavigateBack)) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
        ) {
            Text(t.calcLead, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
            ResultCard(stats = stats, limit = limit, tooMany = tooMany, modifier = Modifier.fadeIn())
            PokyhCard(modifier = Modifier.fadeIn(40)) {
                Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg)) {
                    NumberField(t.calcAbsent, absentInput, invalid = (absentInput.isNotBlank() && absentHours == null) || tooMany) { absentInput = it }
                    PokyhLabel(t.calcTotalMode, color = PokyhTheme.colors.textSecondary)
                    PokyhSegmentedControl(
                        options = listOf(true, false),
                        selected = weeklyMode,
                        onSelect = { weeklyMode = it },
                        label = { if (it) t.calcModeWeekly else t.calcModeTotal },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (weeklyMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                            NumberField(t.calcWeekly, weeklyInput, invalid = weeklyInput.isNotBlank() && weekly == null, modifier = Modifier.weight(1f)) { weeklyInput = it }
                            NumberField(t.calcWeeks, weeksInput, invalid = weeksInput.isNotBlank() && weeks == null, modifier = Modifier.weight(1f)) { weeksInput = it }
                        }
                        if (yearHours != null && yearHours > 0) {
                            Text(t.calcYear(formatNumber(yearHours, 1)), style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary)
                        }
                    } else {
                        NumberField(t.calcTotal, totalInput, invalid = totalInput.isNotBlank() && parseAmount(totalInput) == null) { totalInput = it }
                    }
                    NumberField(t.calcLimit, limitInput, invalid = limit == null || limit > 100, suffix = "%") { limitInput = it }
                }
            }
            if (prefilled) Text(t.calcFromYear, style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary)
            PokyhCard(modifier = Modifier.fadeIn(80)) {
                Text(t.calcRuleTitle, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                Spacer(Modifier.size(PokyhSpacing.sm))
                Text(t.calcRule, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
            }
            Text(t.calcPrivacy, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    invalid: Boolean,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    onChange: (String) -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
        PokyhTextField(
            value = value,
            onValueChange = { onChange(it.take(7)) },
            label = label,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailing = suffix?.let { { Text(it, style = PokyhType.body, color = PokyhTheme.colors.textTertiary) } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (invalid) Box(Modifier.fillMaxWidth().height(2.dp).clip(PokyhShapes.pill).background(Brand.danger))
    }
}

@Composable
private fun ResultCard(stats: AbsenceStats?, limit: Double?, tooMany: Boolean, modifier: Modifier = Modifier) {
    val t = strings().absences
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier) {
        if (stats == null) {
            Text(if (tooMany) t.calcInvalid else t.calcEmpty, style = PokyhType.body, color = colors.textTertiary)
            return@PokyhCard
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Text("${formatNumber(stats.absentPct, 1)} %", style = PokyhType.statLarge.monospacedDigits(), color = colors.textPrimary)
            Text(t.calcResult, style = PokyhType.headline, color = colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.size(PokyhSpacing.lg))
        // The bar runs to twice the limit, so the limit's marker sits in the middle.
        val span = maxOf((limit ?: 25.0) * 2, stats.absentPct, 1.0)
        val tone = when {
            limit == null -> colors.textTertiary
            stats.hoursLeft < 0 -> Brand.danger
            stats.absentPct >= limit * 0.6 -> Brand.warning
            else -> PokyhDecorative.sage(colors.isDark).ink
        }
        val fill by animateFloatAsState((stats.absentPct / span).toFloat().coerceIn(0f, 1f), tween(500), label = "calcBar")
        BoxWithConstraints(Modifier.fillMaxWidth().height(20.dp)) {
            Box(Modifier.fillMaxWidth().height(12.dp).offset(y = 4.dp).clip(PokyhShapes.pill).background(colors.cardAlt)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).clip(PokyhShapes.pill).background(tone))
            }
            if (limit != null) {
                val at = (limit / span).toFloat().coerceIn(0f, 1f)
                Box(Modifier.offset(x = maxWidth * at - 1.dp).width(2.dp).fillMaxHeight().clip(PokyhShapes.pill).background(colors.textPrimary))
            }
        }
        Spacer(Modifier.size(PokyhSpacing.md))
        Text(t.calcPresent(formatNumber(100 - stats.absentPct, 1)), style = PokyhType.footnote, color = colors.textSecondary)
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(
            text = when {
                stats.hoursLeft > 0 -> t.calcLeft(formatNumber(stats.hoursLeft.toDouble(), 0))
                stats.hoursLeft == 0L -> t.calcAtLimit
                else -> t.calcOver(formatNumber((-stats.hoursLeft).toDouble(), 0))
            },
            style = PokyhType.headline,
            color = colors.textPrimary,
        )
    }
}

/** Absences against the lessons of the year (the web's `absenceStats`). */
private data class AbsenceStats(val absentPct: Double, val hoursLeft: Long)

private fun absenceStats(absentHours: Double, totalHours: Double, limitPct: Double): AbsenceStats? {
    if (!(totalHours > 0) || !(absentHours >= 0) || absentHours > totalHours || limitPct !in 0.0..100.0) return null
    val allowed = totalHours * limitPct / 100
    val left = allowed - absentHours
    return AbsenceStats(
        absentPct = absentHours / totalHours * 100,
        hoursLeft = if (left >= 0) floor(left + 1e-9).toLong() else -ceil(-left - 1e-9).toLong(),
    )
}

/** A non-negative number as typed, with a comma or a point; null when empty or invalid. */
private fun parseAmount(input: String): Double? {
    val text = input.trim().replace(',', '.')
    if (text.isEmpty()) return null
    val v = text.toDoubleOrNull() ?: return null
    return if (v.isFinite() && v >= 0) v else null
}

@Composable
private fun formatNumber(v: Double, digits: Int): String {
    val s = String.format(Locale.ROOT, "%.${digits}f", v)
    val trimmed = if (digits > 0) s.trimEnd('0').trimEnd('.') else s
    return trimmed.replace('.', strings().common.decimal)
}
