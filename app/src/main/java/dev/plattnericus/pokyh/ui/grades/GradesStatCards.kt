package dev.plattnericus.pokyh.ui.grades

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhFittedText
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhFontFamily
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
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

/**
 * How a stat card is pitched.
 *
 * [Full] is the one-per-row card. [Compact] is the same card at half the width, for the 2x2
 * grid at the top of the screen, and it is a *re-pitch, not a subset*: every card keeps its
 * headline number, its diagram and both its footer facts. What changes is the type step, the
 * diagram's size and the footer's direction — two label/value pairs that sit side by side at
 * full width stack at half width, because at ~150dp they would each have about forty points to
 * live in.
 *
 * Keeping the content identical is the point. A compact card that quietly dropped the median or
 * the sparkline would make the 2x2 grid a worse view of the same data rather than a denser one,
 * and the reader would have no way to know something was missing.
 */
private enum class StatSize {
    Full,
    Compact,
    ;

    val compact: Boolean get() = this == Compact

    /** Padding inside the card. A square tile is about 150dp across on a phone, so 20dp of
     * padding on each side would be a quarter of it; 12dp leaves the content somewhere to go. */
    val padding: Dp get() = if (compact) PokyhSpacing.md else PokyhSpacing.card

    /** The card's headline number. */
    val valueStyle: TextStyle
        get() = if (compact) PokyhType.statMedium else PokyhType.statLarge

    /** The gap between a card's blocks. */
    val blockGap: Dp get() = if (compact) PokyhSpacing.md else PokyhSpacing.lg
}

/** How many recent entries the card shows — the same three at either size. */
private const val RecentCount = 3

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
        maxLines = 1,
        modifier = modifier
            .background(fill, PokyhShapes.pill)
            .padding(horizontal = PokyhSpacing.sm, vertical = 3.dp),
    )
}

/**
 * Title + subtitle on the left, pill on the right — the header every card shares.
 *
 * Compact drops the *subtitle* only, and only because it is the one line that restates what the
 * title already says ("Durchschnittsnote" / "Alle Fächer · gewichtet"). The pill stays: it
 * carries a number nothing else on the card does.
 */
@Composable
private fun CardHeader(
    title: String,
    subtitle: String,
    size: StatSize,
    pill: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = if (size.compact) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // **One line when compact**, and that is what makes the auto-sizing work.
            // [PokyhFittedText] shrinks until the text fits the lines it is allowed; at two
            // lines "Durchschnittsnote" already "fits" — split across them, mid-word — so it
            // never shrank. Given a single line it has to, and it does.
            PokyhFittedText(
                text = title,
                style = if (size.compact) PokyhType.footnote.semibold() else PokyhType.headline,
                color = PokyhTheme.colors.textPrimary,
                maxLines = if (size.compact) 1 else 2,
                minScale = if (size.compact) 0.68f else 0.78f,
            )
            if (!size.compact) {
                Text(subtitle, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
            }
        }
        if (pill != null) pill()
    }
}

/** Two facts, side by side at full width and stacked when compact — see [StatSize]. */
@Composable
private fun CardFooter(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    size: StatSize,
    leftValueColor: Color = PokyhTheme.colors.textPrimary,
    rightValueColor: Color = PokyhTheme.colors.textPrimary,
) {
    if (size.compact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs),
        ) {
            FooterFact(leftLabel, leftValue, leftValueColor, fill = true)
            FooterFact(rightLabel, rightValue, rightValueColor, fill = true)
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FooterFact(leftLabel, leftValue, leftValueColor)
            Spacer(Modifier.weight(1f))
            FooterFact(rightLabel, rightValue, rightValueColor)
        }
    }
}

@Composable
private fun FooterFact(label: String, value: String, valueColor: Color, fill: Boolean = false) {
    Row(
        modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PokyhType.caption,
            color = PokyhTheme.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Stacked (compact) facts push their value to the right edge so the two lines form a
        // column of values; side-by-side facts stay tight to their label.
        if (fill) Spacer(Modifier.weight(1f))
        Text(value, style = PokyhType.caption.monospacedDigits(), color = valueColor, maxLines = 1)
    }
}

// ── 1. Durchschnittsnote ────────────────────────────────────────────────────

/**
 * The hero: the overall average, a trend pill vs. the previous month, the Vormonat/Beste Note
 * footer, and the cumulative-average sparkline.
 */
@Composable
internal fun AverageStatCard(
    dashboard: GradesDashboard,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val size = if (compact) StatSize.Compact else StatSize.Full
    val colors = PokyhTheme.colors
    val improving = dashboard.delta >= 0
    val trendColor = if (improving) Brand.success else Brand.danger

    PokyhCard(modifier = modifier, padding = size.padding) {
        CardHeader(
            title = "Durchschnittsnote",
            subtitle = "Alle Fächer · gewichtet",
            size = size,
            pill = {
                StatPill(
                    text = "${if (improving) "↗" else "↘"} ${Fmt.num(absDelta(dashboard.delta))}",
                    color = trendColor,
                    fill = trendColor.copy(alpha = 0.14f),
                )
            },
        )
        CardBody(size) {
            Text(
                text = if (dashboard.hasGrades) Fmt.num(dashboard.overallAvg) else "–",
                style = size.valueStyle,
                color = if (dashboard.hasGrades) bandColor(gradeBand(dashboard.overallAvg)) else colors.textTertiary,
            )
            Spacer(Modifier.size(PokyhSpacing.sm))
            Sparkline(
                dashboard = dashboard,
                lineColor = if (dashboard.hasGrades) bandColor(gradeBand(dashboard.overallAvg)) else colors.textTertiary,
                modifier = Modifier.fillMaxWidth().height(if (compact) 34.dp else 64.dp),
            )
        }
        CardFooter(
            leftLabel = "Vormonat",
            leftValue = if (dashboard.hasGrades) Fmt.num(dashboard.previousMonthAvg) else "–",
            rightLabel = "Beste Note",
            rightValue = if (dashboard.bestGrade > 0) Fmt.num(dashboard.bestGrade) else "—",
            size = size,
        )
    }
}

/**
 * The middle of a card: whatever the card is actually about, centred in the space left between
 * its header and its footer.
 *
 * Compact cards are square (see `StatCardGrid`), and a square is more room than a headline
 * number needs. Letting the middle take the slack — rather than stacking everything under the
 * header and leaving the bottom half blank — is what stops a card with no data yet from looking
 * like it failed to load. At full width the card is sized by its content, so the weight is a
 * no-op there and the layout is unchanged.
 */
@Composable
private fun ColumnScope.CardBody(size: StatSize, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = if (size.compact) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
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
internal fun RatioStatCard(
    dashboard: GradesDashboard,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val size = if (compact) StatSize.Compact else StatSize.Full
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier, padding = size.padding) {
        CardHeader(
            title = "Notenverhältnis",
            subtitle = "Genügend · Ungenügend",
            size = size,
            pill = { StatPill("${dashboard.passRate.roundToInt()} %") },
        )
        CardBody(size) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(if (compact) PokyhSpacing.sm else PokyhSpacing.md),
            ) {
                Text("${dashboard.pos}", style = size.valueStyle, color = Brand.success)
                Text(
                    text = "/",
                    style = (if (compact) PokyhType.title3 else PokyhType.title1).copy(fontWeight = FontWeight.Light),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(bottom = if (compact) 3.dp else 6.dp),
                )
                Text(
                    text = "${dashboard.neg}",
                    style = if (compact) PokyhType.statSmall else PokyhType.statMedium,
                    color = Brand.danger,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(Modifier.size(size.blockGap))
            RatioBar(passRate = dashboard.passRate, hasGrades = dashboard.hasGrades)
        }
        Spacer(Modifier.size(PokyhSpacing.md))
        CardFooter(
            leftLabel = "über 6.0",
            leftValue = "${dashboard.pos}",
            leftValueColor = Brand.success,
            rightLabel = "unter 6.0",
            rightValue = "${dashboard.neg}",
            rightValueColor = Brand.danger,
            size = size,
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
internal fun DistributionStatCard(
    dashboard: GradesDashboard,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val size = if (compact) StatSize.Compact else StatSize.Full
    PokyhCard(modifier = modifier, padding = size.padding) {
        CardHeader(
            title = "Notenverteilung",
            subtitle = "Häufigkeit pro Note",
            size = size,
            pill = { StatPill("σ ${Fmt.num(dashboard.sigma)}") },
        )
        CardBody(size) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DistributionDonut(
                    dashboard = dashboard,
                    // In the square tile the ring gets whatever is left between header and
                    // footer — around 60dp — and its leader-line labels are dropped there. They
                    // are drawn in the ring's own viewBox units, so at that size they would be
                    // about 3sp: a ring of smudges.
                    showLabels = !compact,
                    modifier = if (compact) {
                        Modifier.fillMaxHeight().aspectRatio(1f)
                    } else {
                        Modifier.size(168.dp)
                    },
                )
                // Nothing in the hole of the ring. A grade total used to sit there, on the
                // argument that every other card leads with a number — but it is the one figure
                // on the card that the arcs are not about, it took the centre of the eye from
                // the shape that is, and the screen's own eyebrow already counts the grades.
            }
        }
        Spacer(Modifier.size(PokyhSpacing.md))
        CardFooter(
            leftLabel = "Modus",
            leftValue = if (dashboard.hasGrades) "${dashboard.mode}" else "—",
            rightLabel = "Median",
            rightValue = if (dashboard.hasGrades) Fmt.num(dashboard.median) else "—",
            size = size,
        )
    }
}

@Composable
private fun DistributionDonut(
    dashboard: GradesDashboard,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
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
        if (!showLabels) return@Canvas
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
internal fun RecentStatCard(
    dashboard: GradesDashboard,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val size = if (compact) StatSize.Compact else StatSize.Full
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier, padding = size.padding) {
        CardHeader(
            title = "Kürzlich hinzugefügt",
            subtitle = "Letzte ${dashboard.recent.size} Einträge",
            size = size,
        )
        if (dashboard.recent.isEmpty()) {
            CardBody(size) {
                Text(
                    text = "Noch keine Noten erfasst.",
                    style = PokyhType.footnote,
                    color = colors.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (compact) TextAlign.Center else TextAlign.Start,
                )
            }
            return@PokyhCard
        }
        Spacer(Modifier.size(size.blockGap))
        // **All three, in the square tile too, sharing its height in equal bands.** Two used to
        // be the compact limit because the third row ran past the bottom edge and was clipped
        // mid-line — each entry stacked its date under its subject between two 12dp gaps and a
        // rule, about 48dp of tile per grade. A compact entry is now one line, and the three of
        // them are laid out by `weight` rather than stacked at the top: the list takes whatever
        // the header leaves and divides it, so the rows reach the bottom edge instead of
        // crowding into the upper half, and they cannot overrun it however tall the tile is or
        // how large the system font is set.
        //
        // The weights are compact-only, and have to be. The full-width card has no fixed height
        // — it wraps its content — so there is no leftover space for a weight to divide, and
        // asking for one would collapse every row to nothing.
        val shown = dashboard.recent.take(RecentCount)
        Column(modifier = if (compact) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()) {
            shown.forEachIndexed { index, item ->
                if (index > 0) {
                    if (!compact) Spacer(Modifier.size(PokyhSpacing.md))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
                    if (!compact) Spacer(Modifier.size(PokyhSpacing.md))
                }
                Row(
                    modifier = if (compact) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
                ) {
                    if (compact) {
                        Text(
                            text = item.subject,
                            style = PokyhType.footnote.semibold(),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = Fmt.dateShort(item.date),
                            style = PokyhType.caption2.monospacedDigits(),
                            color = colors.textTertiary,
                            maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                    } else {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = item.subject,
                                style = PokyhType.headline,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = Fmt.dateShort(item.date),
                                style = PokyhType.caption2.monospacedDigits(),
                                color = colors.textTertiary,
                            )
                        }
                    }
                    Text(
                        text = Fmt.num(item.numeric),
                        style = (if (compact) PokyhType.footnote.semibold() else PokyhType.title3).monospacedDigits(),
                        color = bandColor(gradeBand(item.numeric)),
                        maxLines = 1,
                    )
                }
            }
            // Fewer than three entries still take one band each from the top: the empty bands
            // stay reserved, so a single grade sits in the first row instead of being stretched
            // across the whole tile and landing vertically centered.
            if (compact && shown.size < RecentCount) {
                Spacer(Modifier.weight((RecentCount - shown.size).toFloat()))
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
