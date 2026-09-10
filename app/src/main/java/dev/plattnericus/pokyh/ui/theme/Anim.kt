package dev.plattnericus.pokyh.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Motion tokens ported from Theme.swift's `PressableStyle` / `fadeIn` / `popIn` /
 * `slideInTrailing` / Shimmer. Spring "response/dampingFraction" params are translated to
 * Compose's stiffness/dampingRatio using the standard conversion
 * `stiffness = (2*PI/response)^2`, `dampingRatio` carried over directly.
 */
private fun springOf(response: Float, dampingFraction: Float) = spring<Float>(
    dampingRatio = dampingFraction,
    stiffness = ((2 * Math.PI) / response).let { (it * it).toFloat() },
)

/** `.buttonStyle(.pressable)` — scale to 0.96 while pressed, `spring(response: 0.3, dampingFraction: 0.6)`. */
fun Modifier.pressable(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = springOf(response = 0.3f, dampingFraction = 0.6f),
        label = "pressable",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * `.fadeIn(delay:)` — opacity 0→1, translateY 10dp→0, `easeOut(duration: 0.34)` + delay.
 * List rows stagger this by `index * 30..40ms`, matching iOS's `idx * 0.03...0.04`.
 */
@Composable
fun Modifier.fadeIn(delayMillis: Int = 0): Modifier {
    var shown by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = shown,
        animationSpec = tween(durationMillis = 340, delayMillis = delayMillis, easing = LinearOutSlowInEasing),
        label = "fadeIn",
    )
    LaunchedEffect(Unit) { shown = 1f }
    return this
        .graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 10.dp.toPx()
        }
}

/** `.popIn()` — scale 0.5→1 + fade, `spring(response: 0.4, dampingFraction: 0.55)`, delay 50ms. */
@Composable
fun Modifier.popIn(): Modifier {
    var shown by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = shown,
        animationSpec = springOf(response = 0.4f, dampingFraction = 0.55f),
        label = "popIn",
    )
    LaunchedEffect(Unit) { shown = 1f }
    return this.graphicsLayer {
        alpha = progress
        scaleX = 0.5f + progress * 0.5f
        scaleY = 0.5f + progress * 0.5f
    }
}

/**
 * `.slideInTrailing()` — offsetX 44dp→0, blur 5dp→0 (API 31+ only, no-op below), scale
 * 0.92→1 anchored trailing, `spring(response: 0.5, dampingFraction: 0.62)`, delay 80ms.
 * Used for the year-picker menu label appearing top-right in Grades/Absences/Classreg.
 */
@Composable
fun Modifier.slideInTrailing(): Modifier {
    var shown by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = shown,
        animationSpec = springOf(response = 0.5f, dampingFraction = 0.62f),
        label = "slideInTrailing",
    )
    LaunchedEffect(Unit) { shown = 1f }
    return this.graphicsLayer {
        alpha = progress
        translationX = (1f - progress) * 44.dp.toPx()
        scaleX = 0.92f + progress * 0.08f
        scaleY = 0.92f + progress * 0.08f
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f) // trailing anchor
    }
}

/**
 * `.slideIn(active:)` — externally driven (bound to a boolean), animates once when it flips,
 * not on every data refresh. x 90dp→0, scale 0.65→1, rotation 14°→0 anchored trailing.
 */
@Composable
fun Modifier.slideIn(active: Boolean): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = springOf(response = 0.6f, dampingFraction = 0.48f),
        label = "slideIn",
    )
    return this.graphicsLayer {
        alpha = progress
        translationX = (1f - progress) * 90.dp.toPx()
        scaleX = 0.65f + progress * 0.35f
        scaleY = 0.65f + progress * 0.35f
        rotationZ = (1f - progress) * 14f
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
    }
}

/** Shimmer sweep for skeleton loaders — white 18% band, 1.5x width, linear 1.3s loop. */
@Composable
fun Modifier.shimmer(): Modifier = composed {
    var widthPx by remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerPhase",
    )
    this
        .onGloballyPositioned { widthPx = it.size.width.toFloat() }
        .drawWithShimmer(phase, widthPx)
}

private fun Modifier.drawWithShimmer(phase: Float, widthPx: Float): Modifier = composed {
    drawWithContent {
        drawContent()
        if (widthPx <= 0f) return@drawWithContent
        val bandWidth = widthPx * 1.5f
        val x = phase * bandWidth
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                start = Offset(x, 0f),
                end = Offset(x + bandWidth, 0f),
            ),
        )
    }
}

/** Theme light/dark crossfade veil — see PokyhApp.kt for the phase-switch usage. */
val ThemeCrossfadeDurationMillis = 220
