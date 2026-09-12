package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

/**
 * Five tappable stars plus the average and count. Pass `onRate = null` for a read-only display.
 *
 * Filled stars use the [PhFill] weight and empty ones the regular weight — the one place in the
 * app besides the nav bar and status badges where the weight change carries state, and the
 * reason the two weights exist at all.
 */
@Composable
fun StarRating(
    average: Double,
    count: Int,
    modifier: Modifier = Modifier,
    myRating: Int? = null,
    onRate: ((Int) -> Unit)? = null,
) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        repeat(5) { index ->
            val filled = index < (myRating ?: 0)
            Icon(
                imageVector = if (filled) PokyhIcons.starFilled else PokyhIcons.starEmpty,
                contentDescription = if (onRate != null) "${index + 1} Sterne" else null,
                tint = if (filled) Brand.star else colors.textTertiary,
                modifier = Modifier
                    .size(26.dp)
                    .then(
                        if (onRate != null) {
                            Modifier.clickable(interactionSource, indication = null) { onRate(index + 1) }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(String.format("%.1f", average), style = PokyhType.headline, color = colors.textPrimary)
        Text("($count)", style = PokyhType.footnote, color = colors.textSecondary)
    }
}

/** The compact read-only form, for dish cards and home rows. */
@Composable
fun MiniStars(average: Double, count: Int, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val rounded = kotlin.math.round(average).toInt()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(5) { index ->
            Icon(
                imageVector = if (index < rounded) PokyhIcons.starFilled else PokyhIcons.starEmpty,
                contentDescription = null,
                tint = if (index < rounded) Brand.star else colors.textTertiary,
                modifier = Modifier.size(13.dp),
            )
        }
        Spacer(Modifier.size(PokyhSpacing.xs))
        Text("($count)", style = PokyhType.caption2, color = colors.textTertiary)
    }
}
