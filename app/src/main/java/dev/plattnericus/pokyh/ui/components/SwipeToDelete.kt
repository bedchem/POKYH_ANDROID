package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhType

/**
 * What's revealed behind a row as it's swiped away. Shared by Todos and Erinnerungen, which
 * previously each drew their own — same color and icon, different radius and padding, which was
 * visible as soon as you swiped a row on both screens.
 *
 * Labelled as well as iconed, and at the row's own radius ([PokyhShapes.lg]) so the red reveals
 * *inside* the row's footprint rather than as a square block behind a rounded row.
 */
@Composable
fun SwipeToDeleteBackground(
    direction: SwipeToDismissBoxValue,
    modifier: Modifier = Modifier,
) {
    val fromEnd = direction == SwipeToDismissBoxValue.EndToStart
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Brand.danger, PokyhShapes.lg)
            .padding(horizontal = PokyhSpacing.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fromEnd) Arrangement.End else Arrangement.Start,
    ) {
        if (fromEnd) {
            DeleteLabel()
            DeleteIcon()
        } else {
            DeleteIcon()
            DeleteLabel()
        }
    }
}

@Composable
private fun DeleteIcon() {
    Icon(
        imageVector = PokyhIcons.delete,
        contentDescription = "Löschen",
        tint = Brand.onAccent,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun DeleteLabel() {
    Text(
        text = "Löschen",
        style = PokyhType.caption,
        color = Brand.onAccent,
        modifier = Modifier.padding(horizontal = PokyhSpacing.sm),
    )
}
