package dev.plattnericus.pokyh.ui.grades

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhFontFamily
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.bandColor
import dev.plattnericus.pokyh.ui.theme.distributionColor
import kotlin.math.roundToInt

/**
 * The four dashboard cards from the web app's Noten page, rebuilt for the phone.
 *
 * The *diagrams* are 1:1: the sparkline and the donut are drawn from the same geometry the web
 * SVG uses ([SparkGeometry], [DonutGeometry]), scaled to the card. What changes is the styling —
 * the app's own [PokyhCard] surface, type scale, and the softer [distributionColor] palette
 * instead of the web's saturated iOS system colors — and the layout, which stacks into a single
 * column plus a 2-up row instead of the web's four-across grid.
 *
 * Every card renders with zeroed values when there are no grades yet, rather than being replaced
 * by an empty state: the screen should look like itself from the first launch of a school year.
 */

/** The small rounded pill in a card's top-right corner. */
@Composable
private fun StatPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PokyhTheme.colors.textSecondary,
    fill: Color = PokyhTheme.colors.cardAlt,
) {
    Text(
        text = text,
        style = PokyhType.caption2,
        color = color,
        modifier = modifier
            .background(fill, PokyhShapes.pill)
            .padding(horizontal = PokyhSpacing.sm, vertical = 3.dp),
    )
}

/** Title + subtitle on the left, pill on the right — the header every card shares. */
@Composable
private fun CardHeader(
    title: String,
    subtitle: String,
    pill: (@Composable () -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
            Text(subtitle, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
        }
        if (pill != null) pill()
    }
}

/** Two facts on one line, label then value — the footer under a card's headline number. */
@Composable
private fun CardFooter(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    leftValueColor: Color = PokyhTheme.colors.textPrimary,
    rightValueColor: Color = PokyhTheme.colors.textPrimary,
) {
    val colors = PokyhTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FooterFact(leftLabel, leftValue, leftValueColor)
        Spacer(Modifier.weight(1f))
        FooterFact(rightLabel, rightValue, rightValueColor)
    }
}

@Composable
private fun FooterFact(label: String, value: String, valueColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
        Text(label, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
        Text(value, style = PokyhType.caption.monospacedDigits(), color = valueColor)
    }
}

// ── 1. Durchschnittsnote ────────────────────────────────────────────────────

/**
 * The hero: the overall average, a trend pill vs. the previous month, the Vormonat/Beste Note
 * footer, and the cumulative-average sparkline.
 */
@Composable
internal fun AverageStatCard(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val improving = dashboard.delta >= 0
    val trendColor = if (improving) Brand.success else Brand.danger

    PokyhCard(modifier = modifier) {
        CardHeader(
            title = "Durchschnittsnote",
            subtitle = "Alle Fächer · gewichtet",
            pill = {
                StatPill(
                    text = "${if (improving) "↗" else "↘"} ${Fmt.num(absDelta(dashboard.delta))}",
                    color = trendColor,
                    fill = trendColor.copy(alpha = 0.14f),
                )
            },
        )
        Spacer(Modifier.size(PokyhSpacing.lg))
        Text(
            text = if (dashboard.hasGrades) Fmt.num(dashboard.overallAvg) else "–",
            style = PokyhType.statLarge,
            color = if (dashboard.hasGrades) bandColor(gradeBand(dashboard.overallAvg)) else colors.textTertiary,
        )
        Spacer(Modifier.size(PokyhSpacing.md))
        CardFooter(
            leftLabel = "Vormonat",
            leftValue = if (dashboard.hasGrades) Fmt.num(dashboard.previousMonthAvg) else "–",
            rightLabel = "Beste Note",
            rightValue = if (dashboard.bestGrade > 0) Fmt.num(dashboard.bestGrade) else "—",
        )
        Spacer(Modifier.size(PokyhSpacing.lg))
        Sparkline(
            dashboard = dashboard,
            lineColor = if (dashboard.hasGrades) bandColor(gradeBand(dashboard.overallAvg)) else colors.textTertiary,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        )
    }
}

/**
 * The cumulative-average trend, drawn in the web's 200x60 coordinate space and scaled to the
 * available width: a filled gradient area under a 1.5-unit line, with a dot per month.
 *
 * With no data it draws the flat baseline the web draws for a single point, so the card keeps its
 * shape instead of collapsing.
 */
@Composable
private fun Sparkline(
    dashboard: GradesDashboard,
    lineColor: Color,
    modifier: Modifier = Modifier,
) {
    val points = dashboard.spark.points
    Canvas(modifier = modifier) {
        val sx = size.width / SparkGeometry.WIDTH
        val sy = size.height / SparkGeometry.HEIGHT
        fun px(p: Pair<Float, Float>) = Offset(p.first * sx, p.second * sy)

        if (points.isEmpty()) {
            // Empty state: a flat hairline at the vertical middle, so the card still reads as
            // "a chart lives here" rather than as a gap.
            val y = size.height * 0.5f
            drawLine(
                color = lineColor.copy(alpha = 0.28f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.5f * sy,
                cap = StrokeCap.Round,
            )
            return@Canvas
        }

        val linePath = Path().apply {
            points.forEachIndexed { i, p ->
                val o = px(p)
                if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
            }
        }
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.18f), lineColor.copy(alpha = 0f)),
            ),
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 1.5f * sy, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
        dashboard.sparkPoints.forEach { p ->
            drawCircle(color = lineColor.copy(alpha = 0.45f), radius = 2f * sy, center = Offset(p.x * sx, p.y * sy))
        }
    }
}

// ── 2. Notenverhältnis ──────────────────────────────────────────────────────

/** Positive over negative, with the pass-rate bar and the "über/unter 6.0" footer. */
@Composable
internal fun RatioStatCard(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier) {
        CardHeader(
            title = "Notenverhältnis",
            subtitle = "Genügend · Ungenügend",
            pill = { StatPill("${dashboard.passRate.roundToInt()} %") },
        )
        Spacer(Modifier.size(PokyhSpacing.lg))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Text("${dashboard.pos}", style = PokyhType.statLarge, color = Brand.success)
            Text(
                text = "/",
                style = PokyhType.title1.copy(fontWeight = FontWeight.Light),
                color = colors.textTertiary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = "${dashboard.neg}",
                style = PokyhType.statMedium,
                color = Brand.danger,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.size(PokyhSpacing.lg))
        RatioBar(passRate = dashboard.passRate, hasGrades = dashboard.hasGrades)
        Spacer(Modifier.size(PokyhSpacing.md))
        CardFooter(
            leftLabel = "über 6.0",
            leftValue = "${dashboard.pos}",
            leftValueColor = Brand.success,
            rightLabel = "unter 6.0",
            rightValue = "${dashboard.neg}",
            rightValueColor = Brand.danger,
        )
    }
}

/** A two-segment track: success for the pass share, danger for the rest. */
@Composable
private fun RatioBar(passRate: Double, hasGrades: Boolean, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(colors.cardAlt, PokyhShapes.pill),
    ) {
        if (!hasGrades) return@Row
        val positive = (passRate / 100.0).toFloat().coerceIn(0f, 1f)
        if (positive > 0f) {
            Box(
                Modifier
                    .weight(positive)
                    .fillMaxSize()
                    .background(Brand.success, PokyhShapes.pill),
            )
        }
        if (positive < 1f) {
            Box(
                Modifier
                    .weight(1f - positive)
                    .fillMaxSize()
                    .background(Brand.danger, PokyhShapes.pill),
            )
        }
    }
}

// ── 3. Notenverteilung ──────────────────────────────────────────────────────

/** The donut: one arc per grade that occurs, with leader lines and labels, plus Modus/Median. */
@Composable
internal fun DistributionStatCard(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    PokyhCard(modifier = modifier) {
        CardHeader(
            title = "Notenverteilung",
            subtitle = "Häufigkeit pro Note",
            pill = { StatPill("σ ${Fmt.num(dashboard.sigma)}") },
        )
        Spacer(Modifier.size(PokyhSpacing.lg))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DistributionDonut(
                dashboard = dashboard,
                modifier = Modifier.size(168.dp),
            )
        }
        Spacer(Modifier.size(PokyhSpacing.lg))
        CardFooter(
            leftLabel = "Modus",
            leftValue = if (dashboard.hasGrades) "${dashboard.mode}" else "—",
            rightLabel = "Median",
            rightValue = if (dashboard.hasGrades) Fmt.num(dashboard.median) else "—",
        )
    }
}

@Composable
private fun DistributionDonut(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val measurer = rememberTextMeasurer()
    val segments = dashboard.donutSegments

    Canvas(modifier = modifier) {
        val scale = size.minDimension / DonutGeometry.VIEWPORT
        fun p(xy: Pair<Float, Float>) = Offset(xy.first * scale, xy.second * scale)
        val center = Offset(DonutGeometry.CX * scale, DonutGeometry.CY * scale)
        val radius = DonutGeometry.RADIUS * scale
        val stroke = DonutGeometry.STROKE * scale

        if (segments.isEmpty()) {
            // Empty state: the full ring in the inset tone, so the card keeps the donut's
            // silhouette before any grade exists.
            drawCircle(
                color = colors.cardAlt,
                radius = radius,
                center = center,
                style = Stroke(width = stroke),
            )
            return@Canvas
        }

        segments.forEach { seg ->
            val color = distributionColor(seg.grade)
            drawArc(
                color = color,
                startAngle = Math.toDegrees(seg.startAngle.toDouble()).toFloat(),
                sweepAngle = Math.toDegrees(seg.sweep.toDouble()).toFloat(),
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )
        }

        // Leader line + grade number, only where the segment is wide enough to label.
        segments.filter { it.sweep >= DonutGeometry.LABEL_MIN_SWEEP }.forEach { seg ->
            val color = distributionColor(seg.grade)
            drawLine(
                color = color.copy(alpha = 0.8f),
                start = p(seg.tickStart),
                end = p(seg.tickEnd),
                strokeWidth = 1.2f * scale,
            )
            val layout = measurer.measure(
                text = seg.grade.toString(),
                style = TextStyle(
                    fontFamily = PokyhFontFamily,
                    fontWeight = FontWeight.Bold,
                    // `9f * scale` is a PIXEL size (scale converts the web's 110-unit viewBox to
                    // this canvas). `.sp` would re-apply the display density on top of it, which
                    // made the labels ~2.6x too big and collide around the top of the ring —
                    // `.toSp()` converts px to sp instead.
                    fontSize = (9f * scale).toSp(),
                    color = color,
                ),
            )
            val at = p(seg.labelPos)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f),
            )
        }
    }
}

// ── 4. Kürzlich hinzugefügt ─────────────────────────────────────────────────

/** The three newest entries, by grade id (entry order) rather than by date — same as the web. */
@Composable
internal fun RecentStatCard(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier) {
        CardHeader(title = "Kürzlich hinzugefügt", subtitle = "Letzte 3 Einträge")
        Spacer(Modifier.size(PokyhSpacing.lg))
        if (dashboard.recent.isEmpty()) {
            Text(
                text = "Noch keine Noten erfasst.",
                style = PokyhType.footnote,
                color = colors.textTertiary,
            )
            return@PokyhCard
        }
        dashboard.recent.forEachIndexed { index, item ->
            if (index > 0) {
                Spacer(Modifier.size(PokyhSpacing.md))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
                Spacer(Modifier.size(PokyhSpacing.md))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.subject, style = PokyhType.headline, color = colors.textPrimary)
                    Text(Fmt.dateShort(item.date), style = PokyhType.caption, color = colors.textTertiary)
                }
                Text(
                    text = Fmt.num(item.numeric),
                    style = PokyhType.title3.monospacedDigits(),
                    color = bandColor(gradeBand(item.numeric)),
                )
            }
        }
    }
}

/** A compact label for the screen header's eyebrow. */
@Composable
internal fun DashboardEyebrow(dashboard: GradesDashboard) {
    val text = if (dashboard.hasGrades) {
        val stand = if (dashboard.latestGradeDate > 0) {
            formatUntisDateLong(dashboard.latestGradeDate)
        } else {
            "—"
        }
        "Stand $stand · ${dashboard.subjectCount} Fächer · ${dashboard.allCount} Noten"
    } else {
        "Noch keine Noten in diesem Schuljahr"
    }
    PokyhLabel(text)
}
