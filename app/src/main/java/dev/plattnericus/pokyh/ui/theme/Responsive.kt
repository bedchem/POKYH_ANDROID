package dev.plattnericus.pokyh.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Width adaptation. Material's own 600dp breakpoint (the threshold
 * `WindowWidthSizeClass.Medium` uses) is the gate: compact phones are always below it, large
 * tablets/foldables/desktop windows above — without threading an Activity or WindowSizeClass
 * down through every leaf composable.
 *
 * The layout itself doesn't change across the breakpoint; only the *measure* does. Every screen
 * is a single column at [PokyhSpacing.screenH], and on a wide window that column is capped and
 * centered rather than stretched, because a 1000dp-wide list row is unreadable regardless of how
 * nicely it's styled.
 */
private const val RegularWidthBreakpointDp = 600

@Composable
private fun isRegularWidthClass(): Boolean =
    LocalConfiguration.current.screenWidthDp >= RegularWidthBreakpointDp

/**
 * Caps content to a readable measure and centers it, but only above the breakpoint — a no-op on
 * phones, where the screen is already narrower than the cap. Applied once at the screen root,
 * never per card.
 */
@Composable
fun Modifier.readableContent(): Modifier = if (isRegularWidthClass()) {
    this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp)
} else {
    this
}

/**
 * The same idea at a form's measure, for the Login/Lock screens. Unlike [readableContent] this
 * applies on phones too — `widthIn(max=)` is always a cap and never a forced minimum, so on a
 * narrower phone it's simply a no-op.
 */
@Composable
fun Modifier.centeredForm(maxWidth: Dp = 480.dp): Modifier =
    this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth)

/**
 * What a screen root applies. Currently just [readableContent] — the background *color* comes
 * from the screen's `Scaffold(containerColor = PokyhTheme.colors.bg)`, so it participates in the
 * theme crossfade; painting it here as well would double-draw the canvas on every screen.
 */
@Composable
fun Modifier.appBackground(): Modifier = this.readableContent()
