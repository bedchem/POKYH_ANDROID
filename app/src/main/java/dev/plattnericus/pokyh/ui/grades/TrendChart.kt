package dev.plattnericus.pokyh.ui.grades
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.gradeColor
import kotlin.math.roundToInt

private val ChartHeight = 130.dp
private val YTicks = listOf(10.0, 8.0, 6.0, 4.0, 2.0)

/**
 * Notenverlauf sparkline — Canvas port of SwiftUI `TrendChart` (raw grades + running average +
 * linear-regression trend line). Caller only renders this when there are >= 2 [values] (matches
 * `if chronological.count >= 2 { TrendChart(...) }` at the GradeSubjectView call site).
 */
@Composable
fun TrendChart(values: List<Double>, modifier: Modifier = Modifier) {
    val cumulativeAverages = remember(values) {
        var sum = 0.0
        values.mapIndexed { i, v -> sum += v; sum / (i + 1) }
    }
    val finalAvg = cumulativeAverages.lastOrNull() ?: 0.0
    val avgColor = gradeColor(finalAvg)
    val regression = remember(values) { linearRegression(values) }
    val trendArrow = when {
        regression == null -> "→"
        regression.first > 0.05 -> "↗"
        regression.first < -0.05 -> "↘"
        else -> "→"
    }

    val separatorColor = PokyhTheme.colors.separator
    val textTertiary = PokyhTheme.colors.textTertiary
    val gradePts = remember(values) { pointsFor(values) }
    val avgPts = remember(cumulativeAverages) { pointsFor(cumulativeAverages) }

    Column(
        modifier = modifier.fillMaxWidth().cardSurface().padding(PokyhSpacing.card),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        // Header + legend.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PokyhIcons.gradeTrend, contentDescription = null, tint = PokyhTheme.colors.textSecondary, modifier = Modifier.size(13.dp))
            PokyhLabel("Notenverlauf", color = PokyhTheme.colors.textSecondary)
            Spacer(Modifier.weight(1f))
            LegendDot(Brand.accent.copy(alpha = 0.6f), "Noten")
            LegendDot(avgColor, "Ø")
            LegendDot(Brand.orange, "Trend $trendArrow")
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Y-Achse (Note): 5 ticks evenly spread over the chart height, same approximation the
            // iOS VStack-with-Spacer layout uses.
            Column(
                modifier = Modifier.width(16.dp).height(ChartHeight),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                YTicks.forEach { t -> Text(Fmt.num(t, digits = 0), style = PokyhType.caption2, color = textTertiary) }
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).height(ChartHeight)) {
                val density = LocalDensity.current
                val wPx = with(density) { maxWidth.toPx() }
                val hPx = with(density) { maxHeight.toPx() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    fun px(p: RelPoint) = Offset(p.x * w, yFor(p.y, h))

                    // Hilfslinien je Note.
                    YTicks.forEach { t ->
                        val y = yFor(t, h)
                        val isSix = t == 6.0
                        drawLine(
                            color = (if (isSix) Brand.tint else separatorColor).copy(alpha = if (isSix) 0.5f else 0.4f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = (if (isSix) 1f else 0.5f).dp.toPx(),
                            pathEffect = if (isSix) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null,
                        )
                    }

                    // Einzelnoten – feine Linie + Punkte.
                    if (gradePts.isNotEmpty()) {
                        val path = Path().apply {
                            val first = px(gradePts.first())
                            moveTo(first.x, first.y)
                            gradePts.drop(1).forEach { val o = px(it); lineTo(o.x, o.y) }
                        }
                        drawPath(path, color = Brand.accent.copy(alpha = 0.45f), style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        gradePts.forEach { drawCircle(color = Brand.accent, radius = 2.5.dp.toPx(), center = px(it)) }
                    }

                    // Kumulativer Durchschnitt (laufender Mittelwert).
                    if (avgPts.isNotEmpty()) {
                        val path = Path().apply {
                            val first = px(avgPts.first())
                            moveTo(first.x, first.y)
                            avgPts.drop(1).forEach { val o = px(it); lineTo(o.x, o.y) }
                        }
                        drawPath(path, color = avgColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    // Trend-„Stich": dünne lineare Regression.
                    regression?.let { (slope, intercept) ->
                        val y0 = yFor(intercept, h)
                        val y1 = yFor(slope * (values.size - 1) + intercept, h)
                        drawLine(
                            color = Brand.orange.copy(alpha = 0.85f),
                            start = Offset(0f, y0),
                            end = Offset(w, y1),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
                        )
                    }

                    // Endpunkt = aktueller Gesamtschnitt.
                    avgPts.lastOrNull()?.let { drawCircle(color = avgColor, radius = 3.5.dp.toPx(), center = px(it)) }
                }

                // Label „Ø X" — SwiftUI `.position()` centers the view AT the point; Compose's
                // `offset` anchors the top-left instead, so this measures the badge first, then
                // places it centered at the same (clamped) pixel position.
                avgPts.lastOrNull()?.let { last ->
                    CenteredBadge(xPx = last.x * wPx, yPx = yFor(last.y, hPx), color = avgColor, label = "Ø ${Fmt.num(finalAvg)}")
                }
            }
        }

        // X-Achse (Reihenfolge der Noten).
        Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp)) {
            Text("Note 1", style = PokyhType.caption2, color = textTertiary)
            Spacer(Modifier.weight(1f))
            Text("Note ${values.size}", style = PokyhType.caption2, color = textTertiary)
        }
    }
}

/** Measures the "Ø X" capsule, then places its CENTER at the clamped pixel position
 * `(min(maxWidth - 22dp, xPx), max(9dp, yPx - 12dp))` — the same clamp as SwiftUI's
 * `.position(x: min(w - 22, last.x), y: max(9, last.y - 12))`. */
@Composable
private fun CenteredBadge(xPx: Float, yPx: Float, color: Color, label: String) {
    Layout(content = {
        Box(modifier = Modifier.background(color, PokyhShapes.pill).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(label, style = PokyhType.badgeChip, color = Brand.onAccent)
        }
    }) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val clampedX = minOf((constraints.maxWidth - 22.dp.roundToPx()).toFloat(), xPx)
        val clampedY = maxOf(9.dp.roundToPx().toFloat(), yPx - 12.dp.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative((clampedX - placeable.width / 2f).roundToInt(), (clampedY - placeable.height / 2f).roundToInt())
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(6.dp).background(color, PokyhShapes.pill))
        Text(text, style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
    }
}

/** A value already mapped to `x` in `[0,1]` and `y` as the raw grade/average (1..10). */
private data class RelPoint(val x: Float, val y: Double)

/** Maps a value series evenly over `x`, matching SwiftUI `TrendChart.points(_:in:)`. */
private fun pointsFor(values: List<Double>): List<RelPoint> = when {
    values.isEmpty() -> emptyList()
    values.size == 1 -> listOf(RelPoint(0.5f, values[0]))
    else -> values.mapIndexed { idx, v -> RelPoint(idx.toFloat() / (values.size - 1).toFloat(), v) }
}

/** Note (1..10, clamped) -> Y pixel position within a chart of height `h`. */
private fun yFor(v: Double, h: Float): Float = h - ((v.coerceIn(1.0, 10.0) - 1.0) / 9.0 * h).toFloat()

/** Linear regression (x = index, y = grade) for the trend line, matching `TrendChart.regression`. */
private fun linearRegression(values: List<Double>): Pair<Double, Double>? {
    val n = values.size
    if (n < 2) return null
    val meanX = (n - 1) / 2.0
    val meanY = values.sum() / n
    var num = 0.0
    var den = 0.0
    for (i in 0 until n) {
        val dx = i - meanX
        num += dx * (values[i] - meanY)
        den += dx * dx
    }
    if (den == 0.0) return null
    val slope = num / den
    return slope to (meanY - slope * meanX)
}
