package dev.plattnericus.pokyh.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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

/**
 * The narrowest layout width the screens are designed for. Every tile, row and chip is laid out
 * to fit at this width with the default font size.
 */
private const val DesignMinWidthDp = 380f

/** Above this system font scale text stops getting bigger inside the app — see [ProvideResponsiveDensity]. */
private const val MaxFontScale = 1.3f

/** The furthest the app shrinks. Past this touch targets and text get too small, and the
 * component-level fixes (fitted text, rows that stack their trailing label) take over. */
private const val MinScale = 0.85f

/**
 * Makes the whole app fit narrow phones, large "Display size" settings and big system fonts.
 *
 * **Why the app scales instead of every screen re-flowing.** On a phone whose effective width is
 * below [DesignMinWidthDp] — a small phone, or a normal one with display size turned up — every
 * two-up tile, every row with a label on the right and every stat card loses the room its German
 * compounds need, and Android's line breaker then cuts them mid-word ("Abwesenheit / en",
 * "Erscheinungsbil / d"). Shrinking dp for that phone makes the app lay out exactly as it does on
 * the design width, just physically a little smaller, which is what the system's own display
 * size slider does anyway.
 *
 * Both factors count, because both eat the same room: the width that matters for text is the
 * screen width *divided by* the font scale. The font scale is also capped at [MaxFontScale] —
 * past that, labels in fixed-width places cannot fit whatever the layout does.
 */
@Composable
fun ProvideResponsiveDensity(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val fontScale = base.fontScale.coerceAtMost(MaxFontScale)
    // Only ever shrinks: a wide phone at default font size keeps its dp exactly as is.
    val textAwareWidth = widthDp / fontScale.coerceAtLeast(1f)
    val scale = if (widthDp <= 0f) 1f else (textAwareWidth / DesignMinWidthDp).coerceIn(MinScale, 1f)
    val density = Density(base.density * scale, fontScale)
    CompositionLocalProvider(
        LocalDensity provides density,
        content = content,
    )
}
