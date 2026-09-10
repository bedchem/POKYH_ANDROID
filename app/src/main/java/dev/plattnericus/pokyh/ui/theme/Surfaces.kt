package dev.plattnericus.pokyh.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Direct port of Theme.swift `cardSurface(radius:)`:
 *   .background(Palette.card, in: RoundedRectangle(cornerRadius: r, style: .continuous))
 *   .overlay(RoundedRectangle(...).strokeBorder(Palette.border, lineWidth: 0.5))
 *
 * One flat fill + a 0.5dp border — deliberately **no shadow/elevation** anywhere. The whole
 * app has exactly one shadow (the offline banner); a Material `Card`'s default tonal
 * elevation would visibly diverge from iOS here, so screens use this modifier, not `Card`.
 */
fun Modifier.cardSurface(radius: Dp = 16.dp): Modifier = composed {
    val colors = PokyhTheme.colors
    val shape = smoothCorner(radius)
    this
        .background(colors.card, shape)
        .border(0.5.dp, colors.border, shape)
}

/** Same recipe against an arbitrary shape (used where a caller already has a shared
 * [PokyhShapes] instance and wants to avoid re-allocating a shape per radius). */
@Composable
fun Modifier.cardSurface(shape: Shape): Modifier {
    val colors = PokyhTheme.colors
    return this
        .background(colors.card, shape)
        .border(0.5.dp, colors.border, shape)
}
