package dev.plattnericus.pokyh.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * A "squircle" corner — a per-corner superellipse (Lamé curve) sweep, visibly flatter and
 * smoother than a plain circular-arc corner ([RoundedCornerShape]). At [exponent] == 2 this is
 * mathematically a circular corner; ~4.4 is the soft, slightly-flattened corner the whole app
 * is drawn with, and is what keeps generous radii from looking like inflated pills.
 *
 * Everything rounded in the app uses this (via [PokyhRadius]/[smoothCorner]) so the corner
 * character is identical at every size, from a 12dp timetable cell to a 28dp hero card.
 */
@Immutable
class SmoothCornerShape(
    private val radius: Dp,
    private val exponent: Float = 4.4f,
    private val steps: Int = 10,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(Offset.Zero, size))

        val w = size.width
        val h = size.height
        val path = Path()

        fun cornerPoints(centerX: Float, centerY: Float, startAngle: Float, endAngle: Float): List<Offset> =
            (0..steps).map { i ->
                val a = startAngle + (endAngle - startAngle) * (i.toFloat() / steps)
                val cosA = cos(a)
                val sinA = sin(a)
                val px = sign(cosA) * abs(cosA).pow(2f / exponent)
                val py = sign(sinA) * abs(sinA).pow(2f / exponent)
                Offset(centerX + r * px, centerY + r * py)
            }

        val halfPi = (PI / 2).toFloat()
        val pi = PI.toFloat()

        path.moveTo(r, 0f)
        path.lineTo(w - r, 0f)
        // Top-right corner: circle center (w-r, r), sweep 270°..360°.
        cornerPoints(w - r, r, pi * 1.5f, pi * 2f).forEach { path.lineTo(it.x, it.y) }
        path.lineTo(w, h - r)
        // Bottom-right corner: circle center (w-r, h-r), sweep 0°..90°.
        cornerPoints(w - r, h - r, 0f, halfPi).forEach { path.lineTo(it.x, it.y) }
        path.lineTo(r, h)
        // Bottom-left corner: circle center (r, h-r), sweep 90°..180°.
        cornerPoints(r, h - r, halfPi, pi).forEach { path.lineTo(it.x, it.y) }
        path.lineTo(0f, r)
        // Top-left corner: circle center (r, r), sweep 180°..270°.
        cornerPoints(r, r, pi, pi * 1.5f).forEach { path.lineTo(it.x, it.y) }
        path.close()

        return Outline.Generic(path)
    }
}

/** A squircle at an arbitrary radius. Prefer a [PokyhShapes] token — this allocates a shape. */
fun smoothCorner(radius: Dp): Shape = SmoothCornerShape(radius)

/**
 * The radius scale. Six steps, each tied to a *kind of thing* rather than to a number, so
 * "what radius does this take?" has one answer and the answer scales with the element:
 * bigger container -> bigger radius, always in this proportion.
 *
 * Reach for the token, never a `.dp` literal. If something seems to need a radius between two
 * steps, it almost certainly belongs to one of the two.
 */
object PokyhRadius {
    /** Inline chips-that-aren't-pills, tiny accent bars, badge corners. */
    val xs = 8.dp

    /** Controls and dense cells: inputs, day pills, timetable grid cells, thumbnails. */
    val sm = 12.dp

    /** Inset blocks inside a card, and small icon tiles. */
    val md = 16.dp

    /** A list row standing on its own on the canvas. */
    val lg = 20.dp

    /** The default card/sheet/grouped-list radius. The app's most common corner. */
    val xl = 24.dp

    /** Hero surfaces: feature tiles, stat cards, images, bottom-sheet tops, the nav bar. */
    val xxl = 28.dp
}

/**
 * Shared shape instances at every step of [PokyhRadius], so screens reuse one object instead of
 * allocating a [SmoothCornerShape] per recomposition.
 */
object PokyhShapes {
    val xs: Shape = SmoothCornerShape(PokyhRadius.xs)
    val sm: Shape = SmoothCornerShape(PokyhRadius.sm)
    val md: Shape = SmoothCornerShape(PokyhRadius.md)
    val lg: Shape = SmoothCornerShape(PokyhRadius.lg)
    val xl: Shape = SmoothCornerShape(PokyhRadius.xl)
    val xxl: Shape = SmoothCornerShape(PokyhRadius.xxl)

    /** Full capsule — buttons, chips, segmented tracks, badges, nav pills. */
    val pill: Shape = RoundedCornerShape(50)

    /** Top-rounded only, for surfaces anchored to the bottom edge (nav bar, sheets). */
    val topXxl: Shape = RoundedCornerShape(topStart = PokyhRadius.xxl, topEnd = PokyhRadius.xxl)
}
