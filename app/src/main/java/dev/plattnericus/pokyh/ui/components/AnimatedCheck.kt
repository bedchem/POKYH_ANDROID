package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The tick, drawn rather than revealed.
 *
 * A checkmark that fades in is a picture of a result; a checkmark whose stroke is *drawn* is the
 * result happening, and that is the difference between an icon appearing and the sign-in
 * finishing. It is the one moment in the app where a confirmation is worth animating: everything
 * else the user did on purpose and already knows the outcome of.
 *
 * The stroke is measured with [PathMeasure] and cut to [progress], so the tip moves at the speed
 * the easing dictates instead of the two segments snapping in one after the other.
 */
@Composable
fun AnimatedCheck(
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    durationMillis: Int = 420,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val w = size.minDimension
        // The three points of a tick, in fractions of the box — the same proportions Phosphor's
        // own `Check` uses, so this reads as the app's checkmark and not a different one.
        val path = Path().apply {
            moveTo(w * 0.22f, w * 0.52f)
            lineTo(w * 0.42f, w * 0.72f)
            lineTo(w * 0.78f, w * 0.30f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * progress.value, drawn, true)
        drawPath(
            path = drawn,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * The waiting state: a single arc sweeping a full circle.
 *
 * Material's [androidx.compose.material3.CircularProgressIndicator] is a hard blue ring that
 * belongs to a different app's design language — it is the one Material component that shows
 * through POKYH's own surfaces and reads as unfinished. This is the same idea in the brand's
 * terms: a soft track at low opacity so the shape is there even at the start of the sweep, and
 * one rounded arc travelling around it.
 *
 * It sits in the same box the [AnimatedCheck] lands in, so the transition from "signing in" to
 * "signed in" is a swap inside one circle rather than two unrelated shapes.
 */
@Composable
fun BrandSpinner(
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle",
    )
    // A sweep that breathes between a short arc and most of the ring, so the motion reads as
    // progress rather than as a rigid shape being spun.
    val sweep by transition.animateFloat(
        initialValue = 30f,
        targetValue = 260f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val diameter = size.minDimension - stroke
        drawCircle(
            color = color.copy(alpha = 0.18f),
            radius = diameter / 2f,
            style = Stroke(width = stroke),
        )
        rotate(angle) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
