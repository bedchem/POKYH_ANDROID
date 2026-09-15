package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
 * Three rules hold the set together, and every screen's skeleton is built only from these
 * pieces rather than from hand-rolled boxes:
 *
 *  1. **One block type.** [SkeletonBlock] and [SkeletonCircle] are the only shapes. They use the
 *     recessed [insetSurface] fill, so a skeleton inside a card reads as part of the card
 *     rather than as a second card, and they all carry the same [shimmer].
 *  2. **One rhythm.** Widths come from [SkeletonWidth] rather than from per-screen literals, so
 *     a "title" line is the same length on every screen and a loading app doesn't look like six
 *     different loading apps.
 *  3. **No spinners inside content.** A spinner says "something is happening somewhere"; a
 *     skeleton says "this is what is coming, and where". Screens that showed a centered
 *     [androidx.compose.material3.CircularProgressIndicator] over an empty page now show the
 *     layout they are about to fill.
 */

/** The line lengths a skeleton is allowed to use. Longest to shortest, by role. */
object SkeletonWidth {
    /** A headline or a person's name. */
    val title: Dp = 148.dp

    /** Secondary metadata under a title. */
    val subtitle: Dp = 96.dp

    /** A full sentence of body copy. */
    val body: Dp = 216.dp

    /** An eyebrow/label. */
    val label: Dp = 64.dp

    /** A trailing value — a time, a grade, a count. */
    val value: Dp = 38.dp
}

/** The line heights, matched to the type scale the real content uses. */
private val LineHeightTitle = 13.dp
private val LineHeightBody = 11.dp
private val LineHeightCaption = 9.dp

@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = LineHeightBody,
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

/** The two-line title/subtitle stack that most rows and cards lead with. */
@Composable
fun SkeletonTextPair(
    modifier: Modifier = Modifier,
    titleWidth: Dp = SkeletonWidth.title,
    subtitleWidth: Dp = SkeletonWidth.subtitle,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
        SkeletonBlock(height = LineHeightTitle, width = titleWidth)
        SkeletonBlock(height = LineHeightCaption, width = subtitleWidth)
    }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = PokyhSpacing.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        SkeletonCircle(size = 40.dp)
        SkeletonTextPair(modifier = Modifier.weight(1f))
        SkeletonBlock(height = LineHeightBody, width = SkeletonWidth.value)
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
                SkeletonTextPair(
                    modifier = Modifier.weight(1f),
                    titleWidth = SkeletonWidth.title,
                    subtitleWidth = SkeletonWidth.subtitle,
                )
                SkeletonBlock(height = LineHeightBody, width = SkeletonWidth.value)
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
            SkeletonBlock(height = LineHeightCaption, width = SkeletonWidth.label)
            SkeletonBlock(height = 16.dp, width = SkeletonWidth.title)
            SkeletonBlock(height = LineHeightBody, width = SkeletonWidth.body)
            // The stars' own line, reserved — see MiniStarsSlot for why it can't collapse.
            MiniStarsPlaceholder()
        }
    }
}

/**
 * The placeholder for one day column of the Home menu strip: the day header, then three dishes
 * as thumbnail + name + stars.
 *
 * Sized from the same [width] the real card uses, so the strip doesn't re-flow horizontally when
 * the menu lands.
 */
@Composable
fun MensaDayCardSkeleton(width: Dp, modifier: Modifier = Modifier, dishes: Int = 3) {
    PokyhCard(modifier = modifier.width(width), padding = PokyhSpacing.md) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(height = LineHeightTitle, width = 72.dp)
            Spacer(Modifier.weight(1f))
            SkeletonBlock(height = LineHeightCaption, width = 34.dp)
        }
        Spacer(Modifier.size(PokyhSpacing.md))
        repeat(dishes) { index ->
            if (index > 0) Spacer(Modifier.size(PokyhSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.size(46.dp).insetSurface(PokyhShapes.sm).shimmer())
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                ) {
                    SkeletonBlock(height = LineHeightBody, width = 110.dp)
                    MiniStarsPlaceholder()
                }
            }
        }
    }
}

/**
 * The placeholder for one of the Noten dashboard's stat cards: header, a big number, and the
 * footer facts. [tall] adds the chart band the Durchschnitt/Verteilung cards carry.
 */
@Composable
fun StatCardSkeleton(modifier: Modifier = Modifier, tall: Boolean = false) {
    PokyhCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            SkeletonTextPair(modifier = Modifier.weight(1f), titleWidth = 104.dp, subtitleWidth = 72.dp)
            SkeletonBlock(height = 14.dp, width = 34.dp, shape = PokyhShapes.pill)
        }
        Spacer(Modifier.size(PokyhSpacing.lg))
        SkeletonBlock(height = 32.dp, width = 78.dp, shape = PokyhShapes.xs)
        if (tall) {
            Spacer(Modifier.size(PokyhSpacing.lg))
            SkeletonBlock(height = 56.dp, shape = PokyhShapes.sm)
        }
        Spacer(Modifier.size(PokyhSpacing.md))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBlock(height = LineHeightCaption, width = 58.dp)
            Spacer(Modifier.weight(1f))
            SkeletonBlock(height = LineHeightCaption, width = 58.dp)
        }
    }
}

/**
 * The placeholder for the timetable week grid: the day header, then blocks scattered down six
 * columns.
 *
 * The blocks are laid out from a fixed pattern rather than at random, because a skeleton that
 * differs every time it appears reads as content — and because a random layout can't be told
 * apart from a real week at a glance, which is exactly the confusion a placeholder must avoid.
 * This replaced a centered spinner on a blank page.
 */
@Composable
fun WeekGridSkeleton(modifier: Modifier = Modifier) {
    // (start fraction, height fraction) per column, of the column's full height.
    val pattern = listOf(
        listOf(0.00f to 0.18f, 0.20f to 0.26f, 0.52f to 0.16f),
        listOf(0.00f to 0.26f, 0.30f to 0.18f, 0.54f to 0.28f),
        listOf(0.06f to 0.20f, 0.30f to 0.34f),
        listOf(0.00f to 0.16f, 0.18f to 0.22f, 0.44f to 0.30f),
        listOf(0.10f to 0.30f, 0.46f to 0.18f),
        listOf(0.00f to 0.22f),
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PokyhSpacing.md, vertical = PokyhSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = PokyhSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Spacer(Modifier.width(36.dp))
            repeat(6) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
                ) {
                    SkeletonBlock(height = LineHeightCaption, width = 18.dp)
                    SkeletonCircle(size = 22.dp)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Column(
                modifier = Modifier.width(36.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxl),
            ) {
                repeat(6) { SkeletonBlock(height = LineHeightCaption, width = 26.dp) }
            }
            pattern.forEach { column ->
                SkeletonDayColumn(blocks = column, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkeletonDayColumn(blocks: List<Pair<Float, Float>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        var consumed = 0f
        blocks.forEach { (start, height) ->
            val gap = (start - consumed).coerceAtLeast(0f)
            if (gap > 0f) Spacer(Modifier.weight(gap))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(height)
                    .padding(vertical = 1.dp)
                    .insetSurface(PokyhShapes.sm)
                    .shimmer(),
            )
            consumed = start + height
        }
        val rest = (1f - consumed).coerceAtLeast(0.001f)
        Spacer(Modifier.weight(rest))
    }
}

/**
 * The placeholder for a pushed detail screen that leads with an image — the Mensa dish page.
 *
 * Mirrors that page's real structure (hero image, chips, copy, the rating card, the nutrients
 * card), so the layout doesn't rearrange itself around the reader when the dish lands. It
 * replaces a centred spinner, which promised nothing and reserved nothing.
 */
@Composable
fun MediaDetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PokyhSpacing.screenH),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
    ) {
        SkeletonBlock(height = 210.dp, shape = PokyhShapes.xxl)
        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                SkeletonBlock(height = 24.dp, width = 82.dp, shape = PokyhShapes.pill)
                SkeletonBlock(height = 24.dp, width = 62.dp, shape = PokyhShapes.pill)
            }
            SkeletonBlock(height = LineHeightBody, width = SkeletonWidth.body)
            SkeletonBlock(height = LineHeightBody, width = SkeletonWidth.title)
        }
        PokyhCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
                repeat(5) { SkeletonBlock(height = 26.dp, width = 26.dp, shape = PokyhShapes.xs) }
                Spacer(Modifier.size(PokyhSpacing.sm))
                SkeletonBlock(height = LineHeightTitle, width = 52.dp)
            }
        }
        PokyhCard {
            SkeletonBlock(height = LineHeightCaption, width = SkeletonWidth.label)
            Spacer(Modifier.size(PokyhSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(4) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                    ) {
                        SkeletonBlock(height = 18.dp, width = 38.dp)
                        SkeletonBlock(height = LineHeightCaption, width = 30.dp)
                    }
                }
            }
        }
    }
}

/**
 * The placeholder for a page of prose — a message, a reminder's body.
 *
 * The lines shorten toward the end of each block, the way a paragraph does. Ruler-straight bars
 * of identical width read as a loading *bar*, not as text that is about to arrive.
 */
@Composable
fun ArticleSkeleton(modifier: Modifier = Modifier, paragraphs: Int = 2) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PokyhSpacing.screenH),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        ) {
            SkeletonCircle(size = 44.dp)
            SkeletonTextPair(modifier = Modifier.weight(1f))
        }
        repeat(paragraphs) {
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                SkeletonBlock(height = LineHeightBody)
                SkeletonBlock(height = LineHeightBody)
                SkeletonBlock(height = LineHeightBody, width = SkeletonWidth.body)
                SkeletonBlock(height = LineHeightBody, width = SkeletonWidth.subtitle)
            }
        }
    }
}

/**
 * A screen-level placeholder for a page built out of stacked cards — used where a whole screen
 * is waiting and there is no more specific shape to promise.
 */
@Composable
fun CardStackSkeleton(modifier: Modifier = Modifier, cards: Int = 3) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PokyhSpacing.screenH),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
    ) {
        repeat(cards) {
            PokyhCard {
                SkeletonTextPair(titleWidth = SkeletonWidth.title, subtitleWidth = SkeletonWidth.subtitle)
                Spacer(Modifier.size(PokyhSpacing.lg))
                SkeletonBlock(height = LineHeightBody, width = SkeletonWidth.body)
            }
        }
    }
}
