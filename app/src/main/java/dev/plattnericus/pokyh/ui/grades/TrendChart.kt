package dev.plattnericus.pokyh.ui.grades

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.gradeColor
import kotlin.math.floor

/** One grade on the trend: a teacher's (with its date) or one of the student's own to try out. */
data class TrendPoint(val value: Double, val date: Int?, val label: String, val own: Boolean)

/**
 * The grades over the school year, as the web's TrendChart: each grade a dot (below 6 red, the
 * student's own hollow on a dashed line), the running average as a line, the band under 6
 * tinted, dates along the bottom. The axis runs from min(4, the lowest grade) to 10 with a line
 * at every grade. Tapping a dot shows its card: date · what it was · grade · average so far.
 */
@Composable
fun TrendChart(points: List<TrendPoint>, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val running = remember(points) {
        var sum = 0.0
        points.mapIndexed { i, p -> sum += p.value; sum / (i + 1) }
    }
    val lowest = points.minOfOrNull { it.value } ?: 6.0
    val yMin = floor(minOf(4.0, lowest)).coerceAtLeast(1.0)
    val yMax = 10.0
    val axisW = 22.dp
    val chartH = 200.dp
    val avgColor = gradeColor(running.lastOrNull() ?: 6.0)

    Column(modifier.fillMaxWidth().background(colors.card, PokyhShapes.xl).padding(PokyhSpacing.card), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(chartH)) {
            val plotW = constraints.maxWidth.toFloat()
            Canvas(
                Modifier.fillMaxWidth().height(chartH).pointerInput(points) {
                    detectTapGestures { tap ->
                        val w = size.width - axisW.toPx()
                        val hit = points.indices.minByOrNull { i -> kotlin.math.abs(xOf(i, points.size, w, axisW.toPx()) - tap.x) }
                        selected = if (hit == selected) null else hit
                    }
                },
            ) {
                val left = axisW.toPx()
                val w = size.width - left
                val h = size.height
                fun y(v: Double) = (h - (v.coerceIn(yMin, yMax) - yMin) / (yMax - yMin) * h).toFloat()
                // The band under 6.
                drawRect(Brand.danger.copy(alpha = 0.07f), topLeft = Offset(left, y(6.0)), size = Size(w, h - y(6.0)))
                // A line at every grade.
                var g = yMin
                while (g <= yMax + 0.001) {
                    drawLine(colors.textTertiary.copy(alpha = if (g == 6.0) 0.45f else 0.15f), Offset(left, y(g)), Offset(size.width, y(g)), strokeWidth = if (g == 6.0) 1.5.dp.toPx() else 1.dp.toPx())
                    g += 1.0
                }
                // The running average.
                if (points.size > 1) {
                    val path = Path()
                    running.forEachIndexed { i, a -> val p = Offset(xOf(i, points.size, w, left), y(a)); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                    drawPath(path, avgColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                // The grades: the own ones on a dashed line from the last teacher grade.
                val firstOwn = points.indexOfFirst { it.own }
                if (firstOwn > 0) {
                    val path = Path()
                    for (i in (firstOwn - 1) until points.size) {
                        val p = Offset(xOf(i, points.size, w, left), y(points[i].value))
                        if (i == firstOwn - 1) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(path, colors.textTertiary, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                }
                points.forEachIndexed { i, p ->
                    val c = Offset(xOf(i, points.size, w, left), y(p.value))
                    val tone = if (p.value < 6) Brand.danger else colors.textPrimary
                    val r = if (selected == i) 6.dp.toPx() else 4.5.dp.toPx()
                    if (p.own) {
                        drawCircle(colors.card, r, c)
                        drawCircle(tone, r, c, style = Stroke(width = 2.dp.toPx()))
                    } else {
                        drawCircle(tone, r, c)
                    }
                }
            }
            // The axis' grades.
            var g = yMin
            while (g <= yMax + 0.001) {
                val top = chartH * ((yMax - g) / (yMax - yMin)).toFloat()
                Text(
                    g.toInt().toString(),
                    style = PokyhType.caption2,
                    color = colors.textTertiary,
                    modifier = Modifier.width(axisW).offset(y = top - 7.dp),
                )
                g += 1.0
            }
            // The tapped grade's card.
            selected?.let { i ->
                val p = points[i]
                val w = plotW - with(androidx.compose.ui.platform.LocalDensity.current) { axisW.toPx() }
                val xPx = xOf(i, points.size, w, with(androidx.compose.ui.platform.LocalDensity.current) { axisW.toPx() })
                val xDp = with(androidx.compose.ui.platform.LocalDensity.current) { xPx.toDp() }
                Column(
                    Modifier
                        .offset(x = (xDp - 70.dp).coerceIn(0.dp, maxWidth - 140.dp), y = 0.dp)
                        .width(140.dp)
                        .background(colors.nested, PokyhShapes.md)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(listOfNotNull(p.date?.let { Fmt.dateShort(it) }, p.label.ifBlank { null }).joinToString(" · "), style = PokyhType.caption2, color = colors.textSecondary, maxLines = 2)
                    Text(Fmt.num(p.value), style = PokyhType.headline.copy(fontWeight = FontWeight.ExtraBold), color = gradeColor(p.value))
                    Text("Ø " + Fmt.num(running[i]), style = PokyhType.caption2, color = colors.textTertiary)
                }
            }
        }
        // Dates along the bottom: the first, the middle and the last grade's.
        val dated = points.withIndex().filter { it.value.date != null }
        if (dated.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = axisW)) {
                val shown = listOfNotNull(dated.first(), dated.getOrNull(dated.size / 2)?.takeIf { dated.size > 2 }, dated.last().takeIf { dated.size > 1 })
                shown.forEachIndexed { idx, iv ->
                    Text(
                        Fmt.dateShort(iv.value.date!!),
                        style = PokyhType.caption2,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f),
                        textAlign = when (idx) {
                            0 -> androidx.compose.ui.text.style.TextAlign.Start
                            shown.lastIndex -> androidx.compose.ui.text.style.TextAlign.End
                            else -> androidx.compose.ui.text.style.TextAlign.Center
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(avgColor, PokyhShapes.pill))
            Text(strings().grades.average, style = PokyhType.caption, color = colors.textSecondary)
            if (points.any { it.own }) {
                Box(Modifier.size(10.dp).background(colors.card, PokyhShapes.pill).padding(2.dp).background(colors.textTertiary, PokyhShapes.pill))
                Text(strings().grades.ownGrade, style = PokyhType.caption, color = colors.textSecondary)
            }
        }
    }
}

private fun xOf(i: Int, n: Int, width: Float, left: Float): Float {
    val pad = 12f
    if (n <= 1) return left + width / 2
    return left + pad + (width - 2 * pad) * i / (n - 1)
}
