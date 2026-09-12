@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.mensa

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import dev.plattnericus.pokyh.ui.components.MediaCardSkeleton
import dev.plattnericus.pokyh.ui.components.MiniStars
import dev.plattnericus.pokyh.ui.components.PokyhBleedCard
import dev.plattnericus.pokyh.ui.components.PokyhSectionHeader
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.profile.CurrentUserAvatar
import dev.plattnericus.pokyh.ui.profile.rememberUnreadMessageCount
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.subjectColor

/**
 * Mensa — dishes grouped by day. The one screen in the app built out of image cards rather than
 * list rows, because the photo is most of what you're choosing by; each dish is genuinely its
 * own object, so here a card per dish is right where a grouped list would be wrong.
 */
@Composable
fun MensaScreen(
    onDishClick: (String) -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: MensaViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Mensa",
                nav = TopBarNav.None,
                actions = {
                    TabRootActions(
                        avatarContent = { CurrentUserAvatar() },
                        unreadMessages = rememberUnreadMessageCount(),
                        onMessages = { onNavigate(PokyhDestinations.MESSAGES) },
                        onProfile = { onNavigate(PokyhDestinations.PROFILE) },
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                ui.loading && ui.groups.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = PokyhSpacing.screenH),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
                ) {
                    repeat(2) { MediaCardSkeleton() }
                }

                ui.error != null && ui.groups.isEmpty() ->
                    ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)

                ui.groups.isEmpty() -> EmptyStateView(
                    icon = PokyhIcons.mensa,
                    title = "Kein Speiseplan",
                    subtitle = "Aktuell ist kein Menü verfügbar.",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = PokyhSpacing.screenH,
                        end = PokyhSpacing.screenH,
                        bottom = PokyhSpacing.xxxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
                ) {
                    items(ui.groups, key = { it.date.toString() }) { group ->
                        Column(
                            modifier = Modifier.fadeIn(),
                            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                        ) {
                            PokyhSectionHeader(title = group.label)
                            group.dishes.forEach { dish ->
                                DishCard(
                                    dish = dish,
                                    ratings = ui.ratings[dish.id],
                                    onClick = { onDishClick(dish.id) },
                                )
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
    PokyhBleedCard(onClick = onClick) {
        DishImage(dish = dish, height = 150.dp)
        Column(
            modifier = Modifier.padding(PokyhSpacing.card),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dish.category.isNotEmpty()) {
                    TagChip(text = dish.category.uppercase(), color = subjectColor(dish.category))
                }
                Spacer(Modifier.weight(1f))
                val price = dish.price
                if (price != null && price > 0) {
                    Text(String.format("%.2f €", price), style = PokyhType.caption, color = colors.textSecondary)
                }
            }
            Text(dish.name, style = PokyhType.title3, color = colors.textPrimary, modifier = Modifier.fillMaxWidth())
            val desc = dish.description
            if (!desc.isNullOrEmpty()) {
                Text(
                    text = desc,
                    style = PokyhType.footnote,
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

// ── Bild (geteilt mit DishDetailScreen) ─────────────────────────────────────

/**
 * A fixed-size band the image fills regardless of its native aspect ratio (crop + clip), with a
 * category/name-hashed gradient placeholder while loading, on error, or when the dish simply has
 * no image. The placeholder carries the app's own [PokyhIcons.dish] glyph rather than an
 * illustration, so a missing photo still looks like part of the product.
 */
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
                listOf(
                    subjectColor(dish.category).copy(alpha = 0.34f),
                    subjectColor(dish.name).copy(alpha = 0.20f),
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PokyhIcons.dish,
            contentDescription = null,
            tint = PokyhTheme.colors.card.copy(alpha = 0.85f),
            modifier = Modifier.size(44.dp),
        )
    }
}
