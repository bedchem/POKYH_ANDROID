package dev.plattnericus.pokyh.ui.theme

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
 * iOS draws every card/button/sheet with `RoundedRectangle(cornerRadius:, style: .continuous)`
 * — a "squircle" corner, visibly flatter/smoother than a plain circular-arc rounded corner
 * (Compose's default [androidx.compose.foundation.shape.RoundedCornerShape]). This is the one
 * shape used everywhere in the app so that parity holds at every radius (7dp grid cells up to
 * 24dp login card) without pulling in an extra shapes library.
 *
 * Implemented as a per-corner superellipse (Lamé curve) sweep: at [exponent] == 2 this is
 * mathematically a plain circular corner; iOS's continuous corner is well approximated by
 * exponent ~4–5, which is the default here.
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

/** Shorthand matching the iOS call-site style `RoundedRectangle(cornerRadius: r, style: .continuous)`. */
fun smoothCorner(radius: Dp): Shape = SmoothCornerShape(radius)

/**
 * Precomputed shapes at every radius the iOS app actually uses (Theme.swift / *View.swift),
 * so screens share instances instead of allocating a new [SmoothCornerShape] per recomposition.
 */
object PokyhShapes {
    val r7 = SmoothCornerShape(7.dp)    // timetable grid cell
    val r8 = SmoothCornerShape(8.dp)    // skeleton block, absence info line
    val r10 = SmoothCornerShape(10.dp)  // day-selector pill, grade/target input, message attachment
    val r11 = SmoothCornerShape(11.dp)  // home shortcut icon tile
    val r12 = SmoothCornerShape(12.dp)  // slot row, small info cards, comment row/composer, login field
    val r14 = SmoothCornerShape(14.dp)  // skeleton rows, subject/absence/classreg rows, lock account row
    val r16 = SmoothCornerShape(16.dp)  // cardSurface() default, lesson-detail glass card
    val r18 = SmoothCornerShape(18.dp)  // dish card, grades average card, absences overview, class header
    val r20 = SmoothCornerShape(20.dp)  // switching overlay
    val r24 = SmoothCornerShape(24.dp)  // login form card
}
