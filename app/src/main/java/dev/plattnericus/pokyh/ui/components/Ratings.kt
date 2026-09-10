package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

/**
 * Port of `StarRating` — 5 tappable stars plus the running average + count. Pass `onRate = null`
 * for a read-only display (still shown at full 22dp size); [DishDetailScreen] passes a real
 * callback and applies the tap optimistically before refetching, exactly like iOS.
 */
@Composable
fun StarRating(
    average: Double,
    count: Int,
    modifier: Modifier = Modifier,
    myRating: Int? = null,
    onRate: ((Int) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i ->
            val filled = i < (myRating ?: 0)
            Icon(
                imageVector = if (filled) PokyhIcons.star_fill else PokyhIcons.star_outline,
                contentDescription = null,
                tint = if (filled) Brand.star else PokyhTheme.colors.textTertiary,
                modifier = Modifier
                    .size(22.dp)
                    .let {
                        if (onRate != null) it.clickable(interactionSource, indication = null) { onRate(i + 1) } else it
                    },
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        Text(String.format("%.1f", average), style = PokyhType.subheadline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = PokyhTheme.colors.textPrimary)
        Text(" ($count)", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
    }
}

/** Port of `MiniStars` — compact read-only version for dish cards / home rows. */
@Composable
fun MiniStars(average: Double, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            val filled = i < kotlin.math.round(average).toInt()
            Icon(
                imageVector = if (filled) PokyhIcons.star_fill else PokyhIcons.star_outline,
                contentDescription = null,
                tint = if (filled) Brand.star else PokyhTheme.colors.textTertiary,
                modifier = Modifier.size(11.dp),
            )
        }
        Text(" ($count)", style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
    }
}
