package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.insetSurface
import dev.plattnericus.pokyh.ui.theme.shimmer

/**
 * Five tappable stars plus the average and count. Pass `onRate = null` for a read-only display.
 *
 * Filled stars use the [PhFill] weight and empty ones the regular weight — the one place in the
 * app besides the nav bar and status badges where the weight change carries state, and the
 * reason the two weights exist at all.
 *
 * **Rating is immediate.** Each star springs as it fills, and the average and count crossfade to
 * their new values in the same beat, because the view model applies the vote locally before the
 * request goes out. The motion is what tells you the tap landed; waiting for a round trip to
 * animate would make a good connection feel like a slow one and a slow one feel broken.
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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        repeat(5) { index ->
            RatingStar(
                filled = index < (myRating ?: 0),
                index = index,
                onRate = onRate,
            )
        }
        Spacer(Modifier.size(PokyhSpacing.sm))
        // The two numbers animate as one block: the average moving without its count is a value
        // changing, the pair moving together is a vote being counted.
        AnimatedContent(
            targetState = average to count,
            transitionSpec = {
                (fadeIn(tween(PokyhMotion.durationFast)) togetherWith fadeOut(tween(PokyhMotion.durationFast)))
            },
            label = "ratingSummary",
        ) { (avg, n) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
                Text(String.format("%.1f", avg), style = PokyhType.headline, color = colors.textPrimary)
                Text("($n)", style = PokyhType.footnote, color = colors.textSecondary)
            }
        }
    }
}

/**
 * One star. Springs past its final size as it fills and settles back — a short overshoot, only
 * on the way in, so giving a rating has a physical answer and clearing one doesn't bounce.
 */
@Composable
private fun RatingStar(filled: Boolean, index: Int, onRate: ((Int) -> Unit)?) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (filled) 1f else 0.88f,
        animationSpec = if (filled) {
            spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium)
        } else {
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
        },
        label = "starScale",
    )
    Icon(
        imageVector = if (filled) PokyhIcons.starFilled else PokyhIcons.starEmpty,
        contentDescription = if (onRate != null) "${index + 1} Sterne" else null,
        tint = if (filled) Brand.star else PokyhTheme.colors.textTertiary,
        modifier = Modifier
            .size(26.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onRate != null) {
                    Modifier.clickable(interactionSource, indication = null) { onRate(index + 1) }
                } else {
                    Modifier
                },
            ),
    )
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
                modifier = Modifier.size(MiniStarSize),
            )
        }
        Spacer(Modifier.size(PokyhSpacing.xs))
        Text("($count)", style = PokyhType.caption2, color = colors.textTertiary)
    }
}

private val MiniStarSize = 13.dp

/**
 * The placeholder [MiniStars] leaves room for while its ratings are still in flight.
 *
 * Exactly the footprint of the real thing — five stars at [MiniStarSize], the same gaps, a count
 * pill the width of "(12)" — so when the numbers land they fill a space that was already there
 * instead of appearing out of nowhere and pushing the dish name up. That pop was the whole
 * complaint, and no amount of fade-in fixes it: the fix is for the row to be the right height
 * from the first frame.
 */
@Composable
fun MiniStarsPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(5) {
            Box(
                Modifier
                    .size(MiniStarSize)
                    .insetSurface(PokyhShapes.xs)
                    .shimmer(),
            )
        }
        Spacer(Modifier.size(PokyhSpacing.xs))
        Box(
            Modifier
                .width(22.dp)
                .height(9.dp)
                .insetSurface(PokyhShapes.xs)
                .shimmer(),
        )
    }
}

/**
 * [MiniStars] or its placeholder, whichever the state calls for — and **nothing collapses**.
 *
 * The three cases a dish card actually has are "ratings still loading", "rated" and "nobody has
 * rated this yet", and the third one is why this takes a fixed [height]: an unrated dish used to
 * render no row at all, so a card grew the moment a single rating arrived for it. Reserving the
 * line in all three cases keeps a list of dish cards still while it loads.
 */
@Composable
fun MiniStarsSlot(
    loading: Boolean,
    average: Double,
    count: Int,
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
) {
    Box(modifier = modifier.height(height), contentAlignment = Alignment.CenterStart) {
        when {
            loading -> MiniStarsPlaceholder()
            count > 0 -> MiniStars(average = average, count = count)
            else -> Text(
                text = "Noch keine Bewertung",
                style = PokyhType.caption2,
                color = PokyhTheme.colors.textTertiary,
            )
        }
    }
}
