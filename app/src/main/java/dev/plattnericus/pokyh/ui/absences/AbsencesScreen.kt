@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.absences

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhStat
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.StatusLabel
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.insetSurface
import dev.plattnericus.pokyh.ui.theme.slideInTrailing
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Abwesenheiten. The overview card is the hero (rate ring + total, then the excused/unexcused
 * split), and the entries below are grouped by month — one [PokyhListCard] per month, with the
 * month's own total in the section header rather than repeated on every row.
 *
 * Fehlstunden werden über den echten Stundenplan berechnet, nicht über das `hours`-Feld der
 * API — siehe [AbsencesViewModel] für die exakte Portierung der Logik.
 */
@Composable
fun AbsencesScreen(onNavigateBack: () -> Unit = {}, viewModel: AbsencesViewModel = hiltViewModel()) {
    val year by viewModel.year.collectAsStateWithLifecycle()
    val exact by viewModel.exact.collectAsStateWithLifecycle()
    val absences by viewModel.absences.collectAsStateWithLifecycle()
    val minutesMap by viewModel.minutesMap.collectAsStateWithLifecycle()
    val totalPossibleMins by viewModel.totalPossibleMins.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var yearMenuExpanded by remember { mutableStateOf(false) }

    fun getMin(e: AbsenceEntry) = minutesMap[e.id] ?: (e.hours * 50)
    fun fmt(m: Int) = formatAbsenceMinutes(m, exact)

    val totalMinutes = absences.sumOf(::getMin)
    val excusedMinutes = absences.filter { it.isExcused }.sumOf(::getMin)
    val unexcusedMinutes = totalMinutes - excusedMinutes
    val rate = if (totalPossibleMins > 0) {
        (totalMinutes.toDouble() / totalPossibleMins * 100).coerceAtMost(100.0)
    } else {
        0.0
    }
    val rateColor = when {
        rate < 5.0 -> Brand.success
        rate < 15.0 -> Brand.warning
        else -> Brand.danger
    }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Abwesenheiten",
                eyebrow = "Schuljahr $year/${(year + 1) % 100}",
                nav = TopBarNav.Back(onNavigateBack),
                actions = {
                    PokyhTextButton(
                        text = if (exact) "Exakt" else "Gerundet",
                        icon = if (exact) PokyhIcons.clockExact else PokyhIcons.clock,
                        onClick = viewModel::toggleExact,
                        color = PokyhTheme.colors.textSecondary,
                    )
                    Box(modifier = Modifier.slideInTrailing()) {
                        PokyhTextButton(
                            text = "$year/${(year + 1) % 100}",
                            onClick = { yearMenuExpanded = true },
                            color = PokyhTheme.colors.textPrimary,
                        )
                        DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                            viewModel.availableYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y/${(y + 1) % 100}", style = PokyhType.body) },
                                    onClick = {
                                        yearMenuExpanded = false
                                        viewModel.selectYear(y)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)

                absences.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = PokyhSpacing.screenH),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
                ) {
                    OverviewCard(
                        totalLabel = fmt(totalMinutes),
                        excusedLabel = fmt(excusedMinutes),
                        unexcusedLabel = fmt(unexcusedMinutes),
                        rate = rate,
                        rateColor = rateColor,
                    )
                    EmptyStateView(
                        icon = PokyhIcons.verified,
                        title = "Keine Fehlstunden",
                        subtitle = "Du hast keine Abwesenheiten in diesem Schuljahr.",
                        modifier = Modifier.weight(1f),
                    )
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PokyhSpacing.screenH)
                        .padding(bottom = PokyhSpacing.xxxl),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
                ) {
                    OverviewCard(
                        totalLabel = fmt(totalMinutes),
                        excusedLabel = fmt(excusedMinutes),
                        unexcusedLabel = fmt(unexcusedMinutes),
                        rate = rate,
                        rateColor = rateColor,
                        modifier = Modifier.fadeIn(),
                    )
                    groupAbsencesByMonth(absences, minutesMap).forEachIndexed { idx, group ->
                        PokyhSection(
                            title = group.label,
                            trailing = { PokyhLabel(fmt(group.totalMinutes)) },
                            modifier = Modifier.fadeIn(40 + idx * 30),
                        ) {
                            PokyhListCard(items = group.entries.sortedByDescending { it.startDate }) { a ->
                                AbsenceRow(absence = a, label = fmt(getMin(a)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    totalLabel: String,
    excusedLabel: String,
    unexcusedLabel: String,
    rate: Double,
    rateColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xl),
        ) {
            FehlquoteRing(rate = rate, rateColor = rateColor, modifier = Modifier.size(96.dp))
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
                PokyhLabel("Fehlstunden gesamt")
                Text(totalLabel, style = PokyhType.statMedium, color = colors.textPrimary)
            }
        }
        Spacer(Modifier.size(PokyhSpacing.xl))
        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xxxl)) {
            PokyhStat(excusedLabel, "Entschuldigt", valueColor = Brand.success)
            PokyhStat(unexcusedLabel, "Unentschuldigt", valueColor = Brand.danger)
        }
    }
}

/**
 * A segmented ring for the Fehlquote — short rounded ticks around a circle, filled clockwise
 * from the top. A ring rather than a bar because the number in the middle is the reading and the
 * ring is the context; a linear bar would need its own label to say what 100% means.
 */
@Composable
private fun FehlquoteRing(rate: Double, rateColor: Color, modifier: Modifier = Modifier) {
    val segments = 28
    val filledFraction = (rate / 100.0).coerceIn(0.0, 1.0)
    val filledCount = (filledFraction * segments).roundToInt().let { if (rate > 0.0) it.coerceAtLeast(1) else 0 }
    val trackColor = PokyhTheme.colors.cardAlt

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 5.dp.toPx()
            val tickLength = size.minDimension * 0.16f
            val radius = size.minDimension / 2f - tickLength / 2f - strokeWidth / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            for (i in 0 until segments) {
                val angle = (-90f + i * (360f / segments)) * (PI.toFloat() / 180f)
                val dir = Offset(cos(angle), sin(angle))
                val start = center + dir * (radius - tickLength / 2f)
                val end = center + dir * (radius + tickLength / 2f)
                drawLine(
                    color = if (i < filledCount) rateColor else trackColor,
                    start = start,
                    end = end,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.ROOT, "%.1f%%", rate),
                style = PokyhType.statSmall,
                color = rateColor,
            )
            Text("Quote", style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
        }
    }
}

@Composable
private fun AbsenceRow(absence: AbsenceEntry, label: String) {
    val colors = PokyhTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PokyhSpacing.card),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
                Text(dateText(absence), style = PokyhType.headline, color = colors.textPrimary)
                val sub = subText(absence)
                if (sub.isNotEmpty()) {
                    Text(sub, style = PokyhType.footnote, color = colors.textSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                Text(label, style = PokyhType.headline, color = colors.textSecondary)
                StatusLabel(
                    text = if (absence.isExcused) "entschuldigt" else "offen",
                    color = if (absence.isExcused) Brand.success else Brand.danger,
                    icon = if (absence.isExcused) PokyhIcons.ok else PokyhIcons.failed,
                )
            }
        }
        absence.reasonName?.takeIf { it.isNotEmpty() }?.let { InfoLine("Grund", it) }
        absence.note?.takeIf { it.isNotEmpty() }?.let { InfoLine("Text", it) }
    }
}

/** A recessed strip for a row's extra detail — [insetSurface], so it reads as nested inside the
 * row rather than as another surface stacked on it. */
@Composable
private fun InfoLine(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .insetSurface(PokyhShapes.xs)
            .padding(horizontal = PokyhSpacing.md, vertical = PokyhSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Text(title, style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
        Text(
            text = value,
            style = PokyhType.caption,
            color = PokyhTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun dateText(a: AbsenceEntry): String =
    if (a.startDate != a.endDate) {
        "${Fmt.dateShort(a.startDate)} – ${Fmt.dateShort(a.endDate)}"
    } else {
        Fmt.dateShort(a.startDate)
    }

private fun subText(a: AbsenceEntry): String {
    val parts = mutableListOf<String>()
    if (a.startTime != 0 || a.endTime != 0) parts.add("${Fmt.time(a.startTime)} – ${Fmt.time(a.endTime)}")
    a.subjectName?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    a.teacherName?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    return parts.joinToString(" · ")
}
