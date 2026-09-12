package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.insetSurface
import dev.plattnericus.pokyh.ui.theme.shimmer

/**
 * Loading placeholders that are the *shape of the content that's coming* — same surface, same
 * radius, same row height, same padding as the real thing. A skeleton that doesn't match its
 * content makes the screen jump when data lands, which is worse than a spinner; these don't.
 *
 * Blocks use the recessed [insetSurface] fill, so a skeleton inside a card reads as part of the
 * card rather than as a second card.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    width: Dp? = null,
    shape: Shape = PokyhShapes.xs,
) {
    val sized = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(modifier = sized.height(height).insetSurface(shape).shimmer())
}

/** Circular placeholder for an avatar or icon tile. */
@Composable
fun SkeletonCircle(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).insetSurface(PokyhShapes.pill).shimmer())
}

/**
 * The placeholder for a [PokyhListCard] — one card, hairline-separated rows — which is what most
 * of the app's lists resolve to.
 */
@Composable
fun ListSkeleton(modifier: Modifier = Modifier, rows: Int = 6) {
    PokyhListCard(
        items = (0 until rows).toList(),
        modifier = modifier.padding(horizontal = PokyhSpacing.screenH, vertical = PokyhSpacing.md),
    ) {
        SkeletonRowContent()
    }
}

@Composable
private fun SkeletonRowContent() {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = PokyhSpacing.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        SkeletonCircle(size = 40.dp)
        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
            SkeletonBlock(height = 13.dp, width = 150.dp)
            SkeletonBlock(height = 10.dp, width = 100.dp)
        }
    }
}

/**
 * The placeholder for a list of standalone [PokyhTileRow]s — the lesson rows on Home and in the
 * Timetable day view, which each carry a leading accent bar.
 */
@Composable
fun TileRowSkeleton(modifier: Modifier = Modifier, rows: Int = 3) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap)) {
        repeat(rows) {
            PokyhTileRow {
                Box(Modifier.width(4.dp).height(40.dp).insetSurface(PokyhShapes.xs).shimmer())
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                ) {
                    SkeletonBlock(height = 13.dp, width = 140.dp)
                    SkeletonBlock(height = 10.dp, width = 90.dp)
                }
                SkeletonBlock(height = 12.dp, width = 38.dp)
            }
        }
    }
}

/** The placeholder for the Mensa dish cards — image band plus two text lines. */
@Composable
fun MediaCardSkeleton(modifier: Modifier = Modifier, imageHeight: Dp = 150.dp) {
    PokyhBleedCard(modifier = modifier) {
        Box(Modifier.fillMaxWidth().height(imageHeight).insetSurface(PokyhShapes.xs).shimmer())
        Column(
            modifier = Modifier.padding(PokyhSpacing.card),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        ) {
            SkeletonBlock(height = 11.dp, width = 70.dp)
            SkeletonBlock(height = 16.dp, width = 180.dp)
            SkeletonBlock(height = 12.dp, width = 230.dp)
        }
    }
}
