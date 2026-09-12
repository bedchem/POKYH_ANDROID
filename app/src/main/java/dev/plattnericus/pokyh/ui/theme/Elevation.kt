package dev.plattnericus.pokyh.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation scale — four levels, and the level says what the thing *is*, not how pretty it
 * should look.
 *
 * Ordinary content (cards, rows) is almost flat: separation comes from the
 * [PokyhColors.bg] -> [PokyhColors.card] tone step, and the shadow only softens the edge so a
 * white card doesn't look pasted onto the warm canvas. Visible lift is reserved for things that
 * genuinely float above the page — the nav bar, banners, sheets, overlays. Stacking real
 * shadows on ordinary cards is what made the old screens look busy.
 *
 * Shadow color is a warm umber rather than black, so any shadow that does register reads soft.
 * Dark mode halves card elevation and raises alpha: a shadow barely shows against near-black,
 * so dark mode leans on the card/cardAlt tone step instead.
 *
 * Caveat: [androidx.compose.ui.draw.shadow]'s tinted `ambientColor`/`spotColor` only apply on
 * API 28+. On minSdk 26/27 they silently fall back to an untinted shadow — still renders, just
 * not warm-tinted. Not worth working around.
 */
object PokyhElevation {
    /** Cards and rows — an edge-softener, not a shadow you notice. */
    val level1 = 2.dp

    /** A surface that should read as picked up off the page: a pressed/dragged row, a FAB. */
    val level2 = 6.dp

    /** Genuinely floating chrome: the bottom nav, the offline banner. */
    val level3 = 14.dp

    /** Modal layers: sheets, dialogs, the account-switching overlay. */
    val level4 = 24.dp
}

private val ShadowWarm = Color(0xFF3A2E1F)

fun shadowAmbientColor(isDark: Boolean): Color =
    if (isDark) Color.Black.copy(alpha = 0.30f) else ShadowWarm.copy(alpha = 0.07f)

fun shadowSpotColor(isDark: Boolean): Color =
    if (isDark) Color.Black.copy(alpha = 0.40f) else ShadowWarm.copy(alpha = 0.12f)

/** Card elevation halves in dark mode — see the file doc. */
fun cardElevation(isDark: Boolean, base: Dp = PokyhElevation.level1): Dp = if (isDark) base / 2 else base
