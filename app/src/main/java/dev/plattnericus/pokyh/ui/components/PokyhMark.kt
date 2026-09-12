package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.plattnericus.pokyh.R

/**
 * The real POKYH brand mark (clock ring + "P" monogram + fork/knife) — the maintainer's own
 * source PNG (`pokyh_logo.png`), not a hand-drawn vector recreation, so it's pixel-identical to
 * the canonical icon everywhere it's used. Self-contained — bakes in its own rounded-square
 * background — so it drops in anywhere at any size.
 */
@Composable
fun PokyhMark(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.pokyh_logo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}
