package dev.plattnericus.pokyh.ui.components
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.shimmer
import dev.plattnericus.pokyh.ui.theme.smoothCorner

/** Port of UIComponents.swift `SkeletonBlock`. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
    radius: Dp = 8.dp,
) {
    val m = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        modifier = m
            .height(height)
            .background(PokyhTheme.colors.cardAlt, smoothCorner(radius))
            .shimmer(),
    )
}

/** Port of `ListSkeleton` — a stack of avatar+2-line skeleton rows. */
@Composable
fun ListSkeleton(modifier: Modifier = Modifier, rows: Int = 6) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(PokyhTheme.colors.cardAlt, CircleShape)
                        .shimmer(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(height = 13.dp, width = 160.dp)
                    SkeletonBlock(height = 10.dp, width = 110.dp)
                }
            }
        }
    }
}

/** Port of `TimetableSkeleton` — 7 bar+2-line rows, used while the week/day view first loads. */
@Composable
fun TimetableSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(7) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PokyhTheme.colors.card, PokyhShapes.r12)
                    .padding(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .width(5.dp)
                        .height(42.dp)
                        .background(PokyhTheme.colors.cardAlt, smoothCorner(3.dp))
                        .shimmer(),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(height = 13.dp, width = 130.dp)
                    SkeletonBlock(height = 10.dp, width = 90.dp)
                }
                SkeletonBlock(height = 24.dp, width = 40.dp)
            }
        }
    }
}
