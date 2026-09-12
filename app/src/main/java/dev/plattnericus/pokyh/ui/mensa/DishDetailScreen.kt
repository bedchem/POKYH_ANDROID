@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.mensa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.ui.components.CommentSection
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.LoadingStateView
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhStat
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.StarRating
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.subjectColor

/** One dish: image, rating, nutrients, allergens, comments. */
@Composable
fun DishDetailScreen(
    onBack: () -> Unit,
    viewModel: DishDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = PokyhTheme.colors
    val dish = ui.dish

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = colors.bg,
        topBar = {
            PokyhTopBar(
                title = dish?.name ?: "Gericht",
                eyebrow = dish?.category?.ifEmpty { null },
                nav = TopBarNav.Back(onBack),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                dish == null && ui.loading -> LoadingStateView()
                dish == null -> ErrorStateView(message = ui.error ?: "Gericht nicht gefunden.")
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PokyhSpacing.screenH)
                        .padding(bottom = PokyhSpacing.xxxl),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
                ) {
                    DishImage(
                        dish = dish,
                        height = 210.dp,
                        modifier = Modifier.clip(PokyhShapes.xxl).fadeIn(),
                    )

                    Column(
                        modifier = Modifier.fadeIn(delayMillis = 40),
                        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                    ) {
                        if (dish.category.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                                TagChip(
                                    text = dish.category.uppercase(),
                                    color = subjectColor(dish.category),
                                    large = true,
                                )
                                val price = dish.price
                                if (price != null && price > 0) {
                                    TagChip(
                                        text = String.format("%.2f €", price),
                                        color = colors.textSecondary,
                                        large = true,
                                    )
                                }
                            }
                        }
                        val desc = dish.description
                        if (!desc.isNullOrEmpty()) {
                            Text(desc, style = PokyhType.body, color = colors.textSecondary)
                        }
                    }

                    PokyhSection(title = "Bewertung", modifier = Modifier.fadeIn(delayMillis = 80)) {
                        PokyhCard {
                            StarRating(
                                average = ui.ratings.average,
                                count = ui.ratings.count,
                                myRating = ui.ratings.myRating,
                                onRate = viewModel::rate,
                            )
                        }
                    }

                    NutrientsSection(dish, modifier = Modifier.fadeIn(delayMillis = 120))

                    if (dish.allergens.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                            PokyhLabel("Allergene")
                            Text(
                                text = dish.allergens.joinToString(", "),
                                style = PokyhType.footnote,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    CommentSection(
                        title = "Kommentare",
                        comments = ui.comments,
                        currentUserId = viewModel.currentUserId,
                        onAdd = viewModel::addComment,
                        onDelete = viewModel::deleteComment,
                        modifier = Modifier.fadeIn(delayMillis = 160),
                    )
                }
            }
        }
    }
}

/** kcal/Protein/KH/Fett as equal-width [PokyhStat]s in one card — only shown when at least one
 * nutrient value is present. */
@Composable
private fun NutrientsSection(dish: Dish, modifier: Modifier = Modifier) {
    val items = buildList {
        dish.calories?.let { add(it.toInt().toString() to "kcal") }
        dish.protein?.let { add("${it.toInt()}g" to "Protein") }
        dish.carbs?.let { add("${it.toInt()}g" to "KH") }
        dish.fat?.let { add("${it.toInt()}g" to "Fett") }
    }
    if (items.isEmpty()) return

    PokyhSection(title = "Nährwerte", modifier = modifier) {
        PokyhCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                items.forEach { (value, label) ->
                    PokyhStat(
                        value = value,
                        label = label,
                        modifier = Modifier.weight(1f),
                        alignment = Alignment.CenterHorizontally,
                    )
                }
            }
        }
    }
}
