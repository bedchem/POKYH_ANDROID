@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.mensa
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.MiniStars
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.pressable
import dev.plattnericus.pokyh.ui.theme.shimmer
import dev.plattnericus.pokyh.ui.theme.subjectColor

/** MensaView.swift, ported — grouped-by-day dish list, read-only stars, pushes [DishDetailScreen]. */
@Composable
fun MensaScreen(
    onDishClick: (String) -> Unit,
    viewModel: MensaViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Mensa", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                ui.loading && ui.groups.isEmpty() -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(3) { MensaSkeletonCard() }
                }
                ui.error != null && ui.groups.isEmpty() -> ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                ui.groups.isEmpty() -> EmptyStateView(
                    icon = PokyhIcons.fork_knife,
                    title = "Kein Speiseplan",
                    subtitle = "Aktuell ist kein Menü verfügbar.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(ui.groups, key = { it.date.toString() }) { group ->
                        Column(modifier = Modifier.fadeIn(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(group.label, style = PokyhType.title3.bold(), color = PokyhTheme.colors.textPrimary)
                            group.dishes.forEach { dish ->
                                DishCard(dish = dish, ratings = ui.ratings[dish.id], onClick = { onDishClick(dish.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Gericht-Karte (Liste, schreibgeschützte Sterne) ─────────────────────────

@Composable
private fun DishCard(dish: Dish, ratings: DishRatingsData?, onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PokyhShapes.r18)
            .cardSurface(PokyhShapes.r18)
            .pressable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        DishImage(dish = dish, height = 160.dp)
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dish.category.isNotEmpty()) {
                    Text(dish.category.uppercase(), style = PokyhType.caption2.bold(), color = Brand.accent)
                }
                Spacer(Modifier.weight(1f))
                val price = dish.price
                if (price != null && price > 0) {
                    Text(String.format("%.2f €", price), style = PokyhType.caption.semibold(), color = colors.textSecondary)
                }
            }
            Text(dish.name, style = PokyhType.headline, color = colors.textPrimary, modifier = Modifier.fillMaxWidth())
            val desc = dish.description
            if (!desc.isNullOrEmpty()) {
                Text(
                    desc,
                    style = PokyhType.subheadline,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (ratings != null && ratings.average > 0) {
                MiniStars(average = ratings.average, count = ratings.count)
            }
        }
    }
}

@Composable
private fun MensaSkeletonCard() {
    Column(modifier = Modifier.fillMaxWidth().clip(PokyhShapes.r18).cardSurface(PokyhShapes.r18)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(PokyhTheme.colors.cardAlt)
                .shimmer(),
        )
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(height = 16.dp, width = 180.dp)
            SkeletonBlock(height = 12.dp, width = 240.dp)
            SkeletonBlock(height = 16.dp, width = 120.dp)
        }
    }
}

// ── Bild (geteilt mit DishDetailScreen) ─────────────────────────────────────

/** Port of `DishImage` — a fixed-size box the image fills regardless of its native aspect ratio
 * (`scaledToFill` + clip); a category/name-hashed gradient placeholder while loading, on error,
 * or when the dish simply has no image. */
@Composable
fun DishImage(dish: Dish, height: Dp, modifier: Modifier = Modifier) {
    val url = dish.imageUrl
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        if (!url.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { DishImagePlaceholder(dish, Modifier.fillMaxSize()) },
                error = { DishImagePlaceholder(dish, Modifier.fillMaxSize()) },
            )
        } else {
            DishImagePlaceholder(dish, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DishImagePlaceholder(dish: Dish, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(subjectColor(dish.category).copy(alpha = 0.5f), subjectColor(dish.name).copy(alpha = 0.3f)),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PokyhIcons.fork_knife,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(32.dp),
        )
    }
}
