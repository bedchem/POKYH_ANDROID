package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.plattnericus.pokyh.ui.theme.PokyhElevation
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.floatingSurface
import kotlin.math.max

/** Space between the screen's edges and a dropdown that would reach them. */
private val EdgeMargin = 12.dp

/** Space between the button and the panel below it. */
private val AnchorGap = 10.dp

/**
 * A panel that drops down from a header button, as the web's account menu and bell do
 * (components/app/AppShell.tsx `AccountMenu`, Bell.tsx): right-aligned under the button, never
 * past the screen's edges, fading in from slightly above (the web's `lang-menu` animation).
 * Closes on a tap elsewhere and on back. Place it next to the button, in the same parent.
 *
 * [width] null: as wide as the screen allows (the bell on a phone), up to [maxWidth].
 */
@Composable
fun HeaderDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 272.dp,
    maxWidth: Dp = 392.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) { visible.targetState = expanded }
    if (!visible.currentState && !visible.targetState) return

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenW = config.screenWidthDp.dp
    val panelW = minOf(width ?: (screenW - EdgeMargin * 2), screenW - EdgeMargin * 2, maxWidth)
    val position = remember(density) {
        with(density) { DropdownPosition(AnchorGap.roundToPx(), EdgeMargin.roundToPx()) }
    }

    val transition = rememberTransition(visible, label = "dropdown")
    val progress by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 220 else PokyhMotion.durationFast) },
        label = "dropdownIn",
    ) { if (it) 1f else 0f }

    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = modifier
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * -4.dp.toPx()
                    val scale = 0.98f + 0.02f * progress
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .width(panelW)
                .heightIn(max = config.screenHeightDp.dp * 0.72f)
                .floatingSurface(shape = DropdownShape, color = PokyhTheme.colors.card, elevation = PokyhElevation.level4)
                .padding(6.dp),
            content = content,
        )
    }
}

private val DropdownShape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)

/** Below the anchor, its right edge on the anchor's, kept inside the window. */
private class DropdownPosition(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = max(margin, windowSize.width - margin - popupContentSize.width)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(margin, maxX)
        return IntOffset(x, anchorBounds.bottom + gap)
    }
}
