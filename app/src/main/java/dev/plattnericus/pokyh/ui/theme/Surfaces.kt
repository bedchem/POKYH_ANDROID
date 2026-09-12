package dev.plattnericus.pokyh.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * The one card recipe: a [PokyhColors.card] fill on a [SmoothCornerShape] squircle, lifted just
 * enough off the warm canvas to read as its own layer, and with **no border in either theme**.
 *
 * The separation is done by tone ([PokyhColors.bg] -> [PokyhColors.card]) plus a whisper of warm
 * shadow — adding an outline on top of that is what made the old screens look boxy. The
 * timetable week grid is the single exception in the app: there a cell's border carries real
 * meaning (cancelled / exam / replacement), so it draws its own.
 *
 * Everything that is a card goes through here or through
 * [dev.plattnericus.pokyh.ui.components.PokyhCard] — never a bare
 * `.background(colors.card, RoundedCornerShape(...))`, which is how five different card styles
 * happened in the first place.
 */
@Composable
fun Modifier.cardSurface(
    shape: Shape = PokyhShapes.xl,
    color: Color? = null,
    elevation: Dp? = null,
): Modifier {
    val colors = PokyhTheme.colors
    val resolved = elevation ?: cardElevation(colors.isDark)
    return this
        .shadow(
            elevation = resolved,
            shape = shape,
            ambientColor = shadowAmbientColor(colors.isDark),
            spotColor = shadowSpotColor(colors.isDark),
            clip = false,
        )
        .background(color ?: colors.card, shape)
}

/**
 * A *recessed* fill for something that sits inside a card: an input, a segmented track, a
 * skeleton block, an image placeholder. The counterpart to [cardSurface] — no shadow, because
 * it's meant to read as below the surface rather than above it.
 */
@Composable
fun Modifier.insetSurface(shape: Shape = PokyhShapes.md, color: Color? = null): Modifier =
    this.background(color ?: PokyhTheme.colors.cardAlt, shape)

/**
 * A panel nested inside a card: an expanded row's detail area, an inline "nothing here" line.
 *
 * Use this rather than [insetSurface] whenever the fill covers a large *area* instead of tracing
 * a control. [PokyhColors.cardAlt] carries the theme's cast, which is correct behind an input or
 * a segmented track but reads as a blue (dark) or tan (light) patch once it fills half a card —
 * [PokyhColors.nested] is hue-free for exactly that reason.
 */
@Composable
fun Modifier.nestedSurface(shape: Shape = PokyhShapes.md): Modifier =
    this.background(PokyhTheme.colors.nested, shape)

/**
 * A soft [Brand.accent]-tinted fill — the accent's quiet form, for a selected chip, an icon
 * tile, or a badge. Distinct from a solid [Brand.accent] fill, which is reserved for the one
 * primary action or the active selection.
 */
@Composable
fun Modifier.accentSurface(shape: Shape = PokyhShapes.md): Modifier =
    this.background(PokyhTheme.colors.accentTint, shape)

/**
 * Chrome that genuinely floats above the page: the bottom nav, the offline banner, the
 * switching overlay's card. Same fill as a card, but at an elevation you're meant to notice.
 */
@Composable
fun Modifier.floatingSurface(
    shape: Shape = PokyhShapes.xl,
    color: Color? = null,
    elevation: Dp = PokyhElevation.level3,
): Modifier {
    val colors = PokyhTheme.colors
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = shadowAmbientColor(colors.isDark),
            spotColor = shadowSpotColor(colors.isDark),
            clip = false,
        )
        .background(color ?: colors.card, shape)
}
