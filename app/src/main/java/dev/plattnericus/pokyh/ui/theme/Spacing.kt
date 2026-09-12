package dev.plattnericus.pokyh.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale — a 4dp grid, no value off it anywhere in the app.
 *
 * The named constants below the scale are the *decisions*: they're what make two screens line
 * up without either screen knowing about the other. Use those first; drop to a raw step
 * ([xs]…[xxxl]) only for spacing inside one component.
 */
object PokyhSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 40.dp

    // ── Decisions ────────────────────────────────────────────────────────────

    /** Horizontal inset from the screen edge to content. Every screen, no exceptions. */
    val screenH = xl

    /** Gap between two top-level sections of a screen. Generous on purpose — this is most of
     * what makes the layout breathe. */
    val section = xxl

    /** Padding inside a card. */
    val card = xl

    /** Padding inside a compact row (a list row, a card in a 2-up grid). */
    val row = lg

    /** Gap between sibling rows in a list. */
    val rowGap = sm

    /** Gap between a leading icon/avatar and the text next to it. */
    val iconText = md

    /** Gap between a section header and the content under it. */
    val headerContent = md
}
