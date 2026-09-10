package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.cardSurface

/** Port of `Card<Content>` — the generic padded-card wrapper used all over the app. */
@Composable
fun PokyhCard(
    modifier: Modifier = Modifier,
    radius: Dp = 16.dp,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(radius)
            .padding(padding),
    ) {
        content()
    }
}
