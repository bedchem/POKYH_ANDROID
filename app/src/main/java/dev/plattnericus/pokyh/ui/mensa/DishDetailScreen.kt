@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.mensa
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.ui.components.CommentSection
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.StarRating
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.InterFontFamily
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface

/** DishDetailView.swift, ported — image, rating (interactive), nutrients, allergens, comments. */
@Composable
fun DishDetailScreen(
    onBack: () -> Unit,
    viewModel: DishDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = PokyhTheme.colors

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = ui.dish?.name ?: "Gericht",
                        style = PokyhType.headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PokyhIcons.arrow_left, contentDescription = "Zurück", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, titleContentColor = colors.textPrimary),
            )
        },
    ) { innerPadding ->
        val dish = ui.dish
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                dish == null && ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand.accent)
                }
                dish == null -> ErrorStateView(message = ui.error ?: "Gericht nicht gefunden.")
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DishImage(dish = dish, height = 220.dp, modifier = Modifier.clip(PokyhShapes.r18))

                    if (dish.category.isNotEmpty()) {
                        Text(dish.category.uppercase(), style = PokyhType.caption.bold(), color = Brand.accent)
                    }
                    Text(dish.name, style = PokyhType.title2.bold(), color = colors.textPrimary)
                    val desc = dish.description
                    if (!desc.isNullOrEmpty()) {
                        Text(desc, style = PokyhType.body, color = colors.textSecondary)
                    }

                    StarRating(
                        average = ui.ratings.average,
                        count = ui.ratings.count,
                        myRating = ui.ratings.myRating,
                        onRate = viewModel::rate,
                    )

                    NutrientsRow(dish)

                    if (dish.allergens.isNotEmpty()) {
                        Text(
                            "Allergene: ${dish.allergens.joinToString(", ")}",
                            style = PokyhType.caption,
                            color = colors.textTertiary,
                        )
                    }

                    HorizontalDivider(color = colors.separator)

                    CommentSection(
                        title = "Kommentare",
                        comments = ui.comments,
                        currentUserId = viewModel.currentUserId,
                        onAdd = viewModel::addComment,
                        onDelete = viewModel::deleteComment,
                    )
                }
            }
        }
    }
}

/** Port of `DishDetailView.nutrient` — kcal/Protein/KH/Fett, equal-width, only shown when at
 * least one nutrient value is present (matches the iOS `if dish.calories != nil || ...` gate). */
@Composable
private fun NutrientsRow(dish: Dish) {
    val colors = PokyhTheme.colors
    val items = buildList {
        dish.calories?.let { add(it.toInt().toString() to "kcal") }
        dish.protein?.let { add("${it.toInt()}g" to "Protein") }
        dish.carbs?.let { add("${it.toInt()}g" to "KH") }
        dish.fat?.let { add("${it.toInt()}g" to "Fett") }
    }
    if (items.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().cardSurface().padding(14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { (value, label) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(value, style = PokyhType.subheadline.bold(), color = colors.textPrimary)
                Text(label, style = TextStyle(fontFamily = InterFontFamily, fontSize = 10.sp), color = colors.textSecondary)
            }
        }
    }
}
