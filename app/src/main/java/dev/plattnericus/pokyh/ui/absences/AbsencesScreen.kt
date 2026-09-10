@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.absences
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.PokyhType.medium
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.slideInTrailing
import java.util.Locale

/** AbsencesView.swift, ported. Fehlstunden werden über den echten Stundenplan berechnet, nicht
 * über das `hours`-Feld der API — siehe [AbsencesViewModel] für die exakte Portierung der Logik. */
@Composable
fun AbsencesScreen(viewModel: AbsencesViewModel = hiltViewModel()) {
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
        rate < 5.0 -> Brand.tint
        rate < 15.0 -> Brand.warning
        else -> Brand.danger
    }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Abwesenheiten", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
                actions = {
                    TextButton(onClick = viewModel::toggleExact) {
                        Icon(
                            imageVector = if (exact) PokyhIcons.clock_badge_checkmark else PokyhIcons.clock,
                            contentDescription = null,
                            tint = PokyhTheme.colors.textPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (exact) "Exakt" else "Gerundet",
                            style = PokyhType.caption.medium(),
                            color = PokyhTheme.colors.textPrimary,
                        )
                    }
                    Box {
                        Text(
                            text = "$year/${(year + 1) % 100}",
                            style = PokyhType.subheadline.semibold(),
                            color = PokyhTheme.colors.textPrimary,
                            modifier = Modifier
                                .slideInTrailing()
                                .clickable { yearMenuExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                            viewModel.availableYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y/${(y + 1) % 100}") },
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
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OverviewCard(
                        totalLabel = fmt(totalMinutes),
                        excusedLabel = fmt(excusedMinutes),
                        unexcusedLabel = fmt(unexcusedMinutes),
                        rate = rate,
                        rateColor = rateColor,
                    )
                    EmptyStateView(
                        icon = PokyhIcons.checkmark_seal_fill,
                        title = "Keine Fehlstunden",
                        subtitle = "Du hast keine Abwesenheiten in diesem Schuljahr.",
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    OverviewCard(
                        totalLabel = fmt(totalMinutes),
                        excusedLabel = fmt(excusedMinutes),
                        unexcusedLabel = fmt(unexcusedMinutes),
                        rate = rate,
                        rateColor = rateColor,
                    )
                    groupAbsencesByMonth(absences, minutesMap).forEach { group ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(group.label, style = PokyhType.subheadline.semibold(), color = PokyhTheme.colors.textPrimary)
                                Text(fmt(group.totalMinutes), style = PokyhType.subheadline, color = PokyhTheme.colors.textSecondary)
                            }
                            group.entries.sortedByDescending { it.startDate }.forEach { a ->
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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokyhTheme.colors.surface, PokyhShapes.r18)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Fehlstunden gesamt", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
                Text(totalLabel, style = PokyhType.absencesTotal, color = PokyhTheme.colors.textPrimary)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MiniStat(excusedLabel, "Entschuldigt", Brand.tint)
                MiniStat(unexcusedLabel, "Unentschuldigt", Brand.danger)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Fehlquote", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
                Spacer(Modifier.weight(1f))
                Text(
                    text = String.format(Locale.ROOT, "%.1f%%", rate),
                    style = PokyhType.caption.semibold(),
                    color = rateColor,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(PokyhTheme.colors.card, RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((rate / 100.0).coerceIn(0.0, 1.0).toFloat())
                        .fillMaxHeight()
                        .background(rateColor, RoundedCornerShape(50)),
                )
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = PokyhType.absencesMini, color = color)
        Text(label, style = PokyhType.caption2, color = PokyhTheme.colors.textSecondary)
    }
}

@Composable
private fun AbsenceRow(absence: AbsenceEntry, label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(radius = 14.dp)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(dateText(absence), style = PokyhType.subheadline.medium(), color = PokyhTheme.colors.textPrimary)
                val sub = subText(absence)
                if (sub.isNotEmpty()) {
                    Text(sub, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = PokyhType.subheadline.semibold(), color = PokyhTheme.colors.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = if (absence.isExcused) PokyhIcons.checkmark_circle_fill else PokyhIcons.xmark_circle_fill,
                        contentDescription = null,
                        tint = if (absence.isExcused) Brand.tint else Brand.danger,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = if (absence.isExcused) "entschuldigt" else "offen",
                        style = PokyhType.caption2.bold(),
                        color = if (absence.isExcused) Brand.tint else Brand.danger,
                    )
                }
            }
        }
        absence.reasonName?.takeIf { it.isNotEmpty() }?.let { InfoLine("Grund", it) }
        absence.note?.takeIf { it.isNotEmpty() }?.let { InfoLine("Text", it) }
    }
}

@Composable
private fun InfoLine(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokyhTheme.colors.cardAlt, PokyhShapes.r8)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = PokyhType.caption2.semibold(), color = PokyhTheme.colors.textTertiary)
        Text(value, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary, modifier = Modifier.weight(1f))
    }
}

private fun dateText(a: AbsenceEntry): String =
    if (a.startDate != a.endDate) "${Fmt.dateShort(a.startDate)} – ${Fmt.dateShort(a.endDate)}" else Fmt.dateShort(a.startDate)

private fun subText(a: AbsenceEntry): String {
    val parts = mutableListOf<String>()
    if (a.startTime != 0 || a.endTime != 0) parts.add("${Fmt.time(a.startTime)} – ${Fmt.time(a.endTime)}")
    a.subjectName?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    a.teacherName?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    return parts.joinToString(" · ")
}
