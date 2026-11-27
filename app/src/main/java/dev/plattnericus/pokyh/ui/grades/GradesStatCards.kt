package dev.plattnericus.pokyh.ui.grades

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
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
import dev.plattnericus.pokyh.core.i18n.strings
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width

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

/** How many recent entries the card shows — the same three at either size. */
private const val RecentCount = 3

// ── The web's card pieces (grades/page.tsx, inline styles) ──────────────────

/**
 * The cards as square tiles, two to a row (GradesScreen's grid) — the pushed 2.1 sizes: 12dp in,
 * a one-line title with the pill beside it, a 28sp number, the diagram taking the middle and the
 * two foot facts stacked at the bottom, so everything fits the square at about 160dp.
 */
internal val LocalStatCompact = androidx.compose.runtime.staticCompositionLocalOf { false }

/** `.card`: the surface, 22dp in (12 as a tile), 26dp corners as a squircle. */
@Composable
private fun GradeCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = dev.plattnericus.pokyh.ui.theme.SmoothCornerShape(if (LocalStatCompact.current) 22.dp else 26.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(PokyhTheme.colors.card)
            .padding(if (LocalStatCompact.current) PokyhSpacing.md else 22.dp),
        content = content,
    )
}

/** A tile's middle: whatever the card is about, centred between header and foot (the pushed `CardBody`). */
@Composable
private fun ColumnScope.TileBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/** The web's `--g-good` (`--success-ink`): green text that reads on the page in both themes. */
@Composable
private fun goodInk(): Color = if (PokyhTheme.colors.isDark) Color(0xFF34D399) else Color(0xFF047857)

/** `.pill`: 12.5sp at 700, 4/10 in; the inset tone, or sage / blush for a trend. */
@Composable
private fun StatPill(
    text: String,
    color: Color = PokyhTheme.colors.textSecondary,
    fill: Color = PokyhTheme.colors.cardAlt,
) {
    val tile = LocalStatCompact.current
    Text(
        text = text,
        style = PokyhType.caption.copy(fontSize = if (tile) 11.sp else 12.5.sp, lineHeight = if (tile) 14.sp else 16.sp, fontWeight = FontWeight.Bold),
        color = color,
        maxLines = 1,
        modifier = Modifier
            .background(fill, PokyhShapes.pill)
            .padding(horizontal = if (tile) 8.dp else 10.dp, vertical = if (tile) 3.dp else 4.dp),
    )
}

/** `.card-hd`: the title (15 / 700) and the sub (13, ink-2) on the left, the pill on the right; 16dp under it. */
@Composable
private fun CardHeader(title: String, subtitle: String, pill: (@Composable () -> Unit)? = null) {
    if (LocalStatCompact.current) {
        // A tile (the pushed sizes): the title on one line, shrinking to fit, the pill beside it.
        // The sub only restates the title, so the square leaves it out.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        ) {
            PokyhFittedText(
                text = title,
                style = PokyhType.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em),
                color = PokyhTheme.colors.textPrimary,
                maxLines = 1,
                minScale = 0.68f,
                modifier = Modifier.weight(1f),
            )
            if (pill != null) pill()
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = PokyhType.headline.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em),
                color = PokyhTheme.colors.textPrimary,
            )
            Spacer(Modifier.size(2.dp))
            Text(subtitle, style = PokyhType.caption.copy(fontSize = 13.sp, lineHeight = 18.sp), color = PokyhTheme.colors.textSecondary)
        }
        if (pill != null) pill()
    }
}

/** `.kpi-value`: 46sp at 800, tight (34 when compact). */
private val KpiValueFull = PokyhType.statLarge.copy(fontSize = 46.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em)
private val KpiValue: androidx.compose.ui.text.TextStyle
    @Composable get() = if (LocalStatCompact.current) KpiValueFull.copy(fontSize = 28.sp, lineHeight = 30.sp) else KpiValueFull

/** A size of the ratio's numbers, scaled down with the card. */
@Composable
private fun kpiSize(full: Int) = if (LocalStatCompact.current) (full * 0.61f).sp else full.sp

/** `.kpi-foot`: two facts at either end, 13sp in ink-2, 16dp above. */
@Composable
private fun KpiFoot(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    if (LocalStatCompact.current) {
        // A tile: the two facts stacked at the bottom, label left and value right.
        Column(Modifier.fillMaxWidth().padding(top = PokyhSpacing.sm), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            left()
            right()
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        left()
        right()
    }
}

/** A fact of the foot: label then value (`Vormonat 6,98`), or value then label when [valueFirst]. */
@Composable
private fun FootFact(label: String, value: String, valueColor: Color = PokyhTheme.colors.textSecondary, valueBold: Boolean = false, valueFirst: Boolean = false) {
    if (LocalStatCompact.current) {
        val small = PokyhType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp)
        // The label takes all the room the value leaves (no cut "Last mon…"), and every value
        // ends at the card's right edge in tabular digits, so the two stack in a clean column.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = small, color = PokyhTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                value,
                style = small.copy(fontWeight = if (valueBold) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.End).monospacedDigits(),
                color = valueColor,
                maxLines = 1,
            )
        }
        return
    }
    val base = PokyhType.caption.copy(fontSize = 13.sp, lineHeight = 18.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (valueFirst) FootValue(value, base, valueColor, valueBold)
        Text(label, style = base, color = PokyhTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!valueFirst) FootValue(value, base, valueColor, valueBold)
    }
}

@Composable
private fun FootValue(value: String, base: TextStyle, color: Color, bold: Boolean) {
    Text(value, style = base.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal).monospacedDigits(), color = color, maxLines = 1)
}

// ── 1. Durchschnittsnote ────────────────────────────────────────────────────

/** The average in ink, the trend pill, Vormonat / Beste Note, then the sparkline in the accent. */
@Composable
internal fun AverageStatCard(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    val g = strings().grades
    val colors = PokyhTheme.colors
    val improving = if (dev.plattnericus.pokyh.core.util.GradeScales.current.higherIsBetter) dashboard.delta >= 0 else dashboard.delta <= 0
    val dark = colors.isDark
    GradeCard(modifier) {
        CardHeader(
            title = g.avgTitle,
            subtitle = g.avgSub,
            pill = {
                StatPill(
                    text = "${if (improving) "↗" else "↘"} ${Fmt.num(absDelta(dashboard.delta))}",
                    color = if (improving) goodInk() else colors.dangerText,
                    fill = if (improving) dev.plattnericus.pokyh.ui.theme.PokyhDecorative.sage(dark).fill else dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(dark).fill,
                )
            },
        )
        val value: @Composable () -> Unit = {
            Text(
                text = if (dashboard.hasGrades) Fmt.num(dashboard.overallAvg) else "-",
                style = KpiValue.monospacedDigits(),
                color = colors.textPrimary,
            )
        }
        val foot: @Composable () -> Unit = {
            KpiFoot(
                left = { FootFact(g.prevMonth, if (dashboard.hasGrades) Fmt.num(dashboard.previousMonthAvg) else "-") },
                right = { FootFact(g.bestGrade, if (dashboard.bestGrade > 0) Fmt.num(dashboard.bestGrade) else "-") },
            )
        }
        if (LocalStatCompact.current) {
            // A tile: number and line in the middle, the facts at the bottom (pushed layout).
            TileBody {
                value()
                Spacer(Modifier.size(PokyhSpacing.sm))
                Sparkline(dashboard = dashboard, lineColor = Brand.accent, modifier = Modifier.fillMaxWidth().height(34.dp))
            }
            foot()
        } else {
            value()
            foot()
            Sparkline(dashboard = dashboard, lineColor = Brand.accent, modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(64.dp))
        }
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
    // A tap on the line shows that month and the average so far (web tooltip).
    var picked by androidx.compose.runtime.remember(dashboard) { androidx.compose.runtime.mutableStateOf<SparkPoint?>(null) }
    var boxPx by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val boxW = with(androidx.compose.ui.platform.LocalDensity.current) { boxPx.toDp() }
    Box(modifier.onSizeChanged { boxPx = it.width }) {
    Canvas(
        modifier = Modifier.matchParentSize().pointerInput(dashboard) {
            detectTapGestures { tap ->
                val sx = size.width / SparkGeometry.WIDTH
                val hit = dashboard.sparkPoints.minByOrNull { kotlin.math.abs(it.x * sx - tap.x) }
                picked = if (hit == picked) null else hit
            }
        },
    ) {
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
            drawCircle(color = lineColor.copy(alpha = if (p == picked) 1f else 0.45f), radius = (if (p == picked) 3.5f else 2f) * sy, center = Offset(p.x * sx, p.y * sy))
        }
    }
    picked?.let { p ->
        val x = boxW * (p.x / SparkGeometry.WIDTH)
        Text(
            p.label + " · Ø " + dev.plattnericus.pokyh.core.util.Fmt.num(p.avg),
            style = PokyhType.caption2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = PokyhTheme.colors.textPrimary,
            maxLines = 1,
            modifier = Modifier
                .offset(x = (x - 60.dp).coerceIn(0.dp, (boxW - 120.dp).coerceAtLeast(0.dp)), y = (-4).dp)
                .width(120.dp)
                .background(PokyhTheme.colors.nested, PokyhShapes.sm)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
    }
}

// ── 2. Notenverhältnis ──────────────────────────────────────────────────────

/** Passed over failed (`.ratio`), the bar (`.ratio-bar`) and how many either side of 6. */
@Composable
internal fun RatioStatCard(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    val g = strings().grades
    val colors = PokyhTheme.colors
    GradeCard(modifier) {
        CardHeader(title = g.ratioTitle, subtitle = g.ratioSub, pill = { StatPill("${dashboard.passRate.roundToInt()} %") })
        val tile = LocalStatCompact.current
        val numbers: @Composable () -> Unit = {
            Row(horizontalArrangement = Arrangement.spacedBy(if (tile) 6.dp else 10.dp)) {
                Text("${dashboard.pos}", style = KpiValue.monospacedDigits(), color = goodInk(), modifier = Modifier.alignByBaseline())
                Text("/", style = KpiValue.copy(fontSize = kpiSize(26), lineHeight = kpiSize(26), fontWeight = FontWeight.Normal, letterSpacing = 0.sp), color = colors.textTertiary, modifier = Modifier.alignByBaseline())
                Text("${dashboard.neg}", style = KpiValue.copy(fontSize = kpiSize(34), lineHeight = kpiSize(34)).monospacedDigits(), color = colors.dangerText, modifier = Modifier.alignByBaseline())
            }
        }
        if (tile) {
            // A tile: numbers and bar in the middle, the facts at the bottom (pushed layout).
            TileBody {
                numbers()
                RatioBar(passRate = dashboard.passRate, hasGrades = dashboard.hasGrades, modifier = Modifier.padding(top = PokyhSpacing.md))
            }
        } else {
            numbers()
            RatioBar(passRate = dashboard.passRate, hasGrades = dashboard.hasGrades, modifier = Modifier.padding(top = 18.dp))
        }
        KpiFoot(
            left = { FootFact(g.above6, "${dashboard.pos}", valueColor = goodInk(), valueBold = true, valueFirst = true) },
            right = { FootFact(g.below6, "${dashboard.neg}", valueColor = colors.dangerText, valueBold = true, valueFirst = true) },
        )
    }
}

/** `.ratio-bar`: 8dp, two rounded segments 3dp apart, success and danger. */
@Composable
private fun RatioBar(passRate: Double, hasGrades: Boolean, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().height(8.dp).clip(PokyhShapes.pill),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (!hasGrades) {
            Box(Modifier.fillMaxSize().background(colors.cardAlt, PokyhShapes.pill))
            return@Row
        }
        val positive = (passRate / 100.0).toFloat().coerceIn(0f, 1f)
        if (positive > 0f) Box(Modifier.weight(positive).fillMaxSize().background(Brand.success, PokyhShapes.pill))
        if (positive < 1f) Box(Modifier.weight(1f - positive).fillMaxSize().background(Brand.danger, PokyhShapes.pill))
    }
}

// ── 3. Notenverteilung ──────────────────────────────────────────────────────

/** The donut (`.donut-wrap`, 110 × 110 on the web), Modus and Median under it. */
@Composable
internal fun DistributionStatCard(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    val g = strings().grades
    GradeCard(modifier) {
        CardHeader(title = g.distTitle, subtitle = g.distSub, pill = { StatPill("σ ${Fmt.num(dashboard.sigma)}") })
        // In a tile the ring takes the room between header and foot, without its leader labels
        // (at that size they would be about 3sp) — the pushed tile. Tapping an arc still shows its count.
        val tile = LocalStatCompact.current
        Box(
            modifier = if (tile) Modifier.fillMaxWidth().weight(1f).padding(top = PokyhSpacing.sm) else Modifier.fillMaxWidth().padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            DistributionDonut(
                dashboard = dashboard,
                showLabels = !tile,
                modifier = if (tile) Modifier.fillMaxHeight().aspectRatio(1f) else Modifier.size(132.dp),
            )
        }
        KpiFoot(
            left = { FootFact(g.mode, if (dashboard.hasGrades) "${dashboard.mode}" else "-") },
            right = { FootFact(g.median, if (dashboard.hasGrades) Fmt.num(dashboard.median) else "-") },
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
    // The grade whose arc was tapped: it stays bright, the others fade, and its count sits in
    // the hole of the ring — the web's hover, as a tap. Tapping it again (or beside the ring)
    // lets go.
    var selected by remember(segments) { mutableStateOf<Int?>(null) }
    val hint = strings().grades.distHint

    Canvas(
        modifier = modifier
            .semantics { contentDescription = hint }
            .pointerInput(segments) {
                detectTapGestures { pos ->
                    val scale = minOf(size.width, size.height) / DonutGeometry.VIEWPORT
                    val dx = pos.x - DonutGeometry.CX * scale
                    val dy = pos.y - DonutGeometry.CY * scale
                    // The hit band is wider than the ring (the web's 24-unit hit stroke).
                    val r = kotlin.math.hypot(dx, dy) / scale
                    if (r < DonutGeometry.RADIUS - 14 || r > DonutGeometry.RADIUS + 14) {
                        selected = null
                        return@detectTapGestures
                    }
                    var a = kotlin.math.atan2(dy, dx)
                    if (a < -kotlin.math.PI.toFloat() / 2) a += 2 * kotlin.math.PI.toFloat()
                    val hit = segments.firstOrNull { a >= it.startAngle - 0.02f && a <= it.startAngle + it.sweep + 0.02f }
                    selected = if (hit == null || hit.grade == selected) null else hit.grade
                }
            },
    ) {
        val scale = size.minDimension / DonutGeometry.VIEWPORT
        fun p(xy: Pair<Float, Float>) = Offset(xy.first * scale, xy.second * scale)
        val center = Offset(DonutGeometry.CX * scale, DonutGeometry.CY * scale)
        val radius = DonutGeometry.RADIUS * scale
        val stroke = DonutGeometry.STROKE * scale
        fun alphaOf(grade: Int) = if (selected == null || selected == grade) 1f else 0.35f

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
            val color = distributionColor(seg.grade).copy(alpha = alphaOf(seg.grade))
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

        // The tapped grade's count in the hole: "4×", in its colour.
        segments.firstOrNull { it.grade == selected }?.let { seg ->
            val layout = measurer.measure(
                text = "${seg.count}×",
                style = TextStyle(
                    fontFamily = PokyhFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = maxOf(14f * scale, 13.dp.toPx()).toSp(),
                    color = distributionColor(seg.grade),
                ),
            )
            drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
        }

        // Leader line + grade number, only where the segment is wide enough to label.
        if (!showLabels) return@Canvas
        segments.filter { it.sweep >= DonutGeometry.LABEL_MIN_SWEEP }.forEach { seg ->
            val color = distributionColor(seg.grade).copy(alpha = alphaOf(seg.grade))
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

/** The web's tones for a recent mark: sage for a pass, butter below, blush when critical. */
@Composable
private fun recentTone(band: GradeBand): dev.plattnericus.pokyh.ui.theme.DecorativeTone {
    val dark = PokyhTheme.colors.isDark
    return when (band) {
        GradeBand.Excellent, GradeBand.Positive -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.sage(dark)
        GradeBand.Negative -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.butter(dark)
        GradeBand.Critical -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(dark)
        GradeBand.Neutral -> dev.plattnericus.pokyh.ui.theme.DecorativeTone(PokyhTheme.colors.cardAlt, PokyhTheme.colors.textPrimary)
    }
}

/** The newest entries (`.recent`): subject and date, the mark in its tone. */
@Composable
internal fun RecentStatCard(dashboard: GradesDashboard, modifier: Modifier = Modifier) {
    val g = strings().grades
    val colors = PokyhTheme.colors
    GradeCard(modifier) {
        CardHeader(title = g.recentTitle, subtitle = g.recentSub(dashboard.recent.size))
        if (LocalStatCompact.current) {
            // A tile (pushed layout): three one-line rows sharing the height in equal bands, so
            // they reach the bottom edge and can never run past it.
            if (dashboard.recent.isEmpty()) {
                TileBody {
                    Text(g.recentEmpty, style = PokyhType.footnote, color = colors.textTertiary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                return@GradeCard
            }
            val shown = dashboard.recent.take(RecentCount)
            Column(Modifier.fillMaxWidth().weight(1f).padding(top = PokyhSpacing.sm)) {
                // Keyed by the grade: a mark that just came in slides in on its own, the
                // others keep their state.
                shown.forEachIndexed { i, item ->
                    androidx.compose.runtime.key(item.id) { RecentTileRow(item, i, Modifier.weight(1f)) }
                }
                if (shown.size < RecentCount) Spacer(Modifier.weight((RecentCount - shown.size).toFloat()))
            }
            return@GradeCard
        }
        if (dashboard.recent.isEmpty()) {
            Text(g.recentEmpty, style = PokyhType.footnote.copy(fontSize = 14.sp), color = colors.textTertiary)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                dashboard.recent.take(RecentCount).forEach { item -> RecentRow(item) }
            }
        }
    }
}

/**
 * A tile's recent entry: the subject over its date ("18.11.26"), the mark in its tone on the
 * right. It eases in (fade and a short slide, one row after the other on the first show) and a
 * new mark arriving later does the same on its own.
 */
@Composable
private fun RecentTileRow(item: RecentGradeItem, index: Int, modifier: Modifier) {
    val colors = PokyhTheme.colors
    val reduced = dev.plattnericus.pokyh.ui.pixel.rememberReducedMotion()
    val appear = remember { androidx.compose.animation.core.Animatable(if (reduced) 1f else 0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        kotlinx.coroutines.delay(90L * index)
        appear.animateTo(1f, androidx.compose.animation.core.tween(560, easing = androidx.compose.animation.core.CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * 14.dp.toPx()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                item.subject,
                style = PokyhType.footnote.copy(fontWeight = FontWeight(650), lineHeight = 17.sp),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Fmt.dateShort(item.date), style = PokyhType.caption2.copy(lineHeight = 13.sp).monospacedDigits(), color = colors.textTertiary, maxLines = 1)
        }
        val tone = recentTone(gradeBand(item.numeric))
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 38.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tone.fill)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                Fmt.num(item.numeric),
                style = PokyhType.footnote.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold).monospacedDigits(),
                color = tone.ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentRow(item: RecentGradeItem) {
    val colors = PokyhTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                item.subject,
                style = PokyhType.headline.copy(fontSize = 14.5.sp, lineHeight = 19.sp, fontWeight = FontWeight(650)),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(Fmt.dateShort(item.date), style = PokyhType.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp).monospacedDigits(), color = colors.textTertiary)
        }
        val tone = recentTone(gradeBand(item.numeric))
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 44.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tone.fill)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                Fmt.num(item.numeric),
                style = PokyhType.subheadline.copy(fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold).monospacedDigits(),
                color = tone.ink,
                maxLines = 1,
            )
        }
    }
}

/** A compact label for the screen header's eyebrow. */
@Composable
internal fun DashboardEyebrow(dashboard: GradesDashboard) {
    val g = strings().grades
    val text = if (dashboard.hasGrades) {
        val stand = if (dashboard.latestGradeDate > 0) formatUntisDateLong(dashboard.latestGradeDate) else "—"
        g.summary(stand, dashboard.subjectCount, dashboard.allCount)
    } else {
        g.noGradesYear
    }
    PokyhLabel(text)
}
