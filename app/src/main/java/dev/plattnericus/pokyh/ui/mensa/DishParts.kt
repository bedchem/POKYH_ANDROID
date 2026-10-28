package dev.plattnericus.pokyh.ui.mensa

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

// The dish page's blocks as on the web (components/mensa/DishView.tsx and parts.tsx).

/** Vegan first, then vegetarian, then the rest (web `prioritizedTags`). */
internal fun prioritizedTags(tags: List<String>): List<String> = tags.sortedBy {
    val t = it.lowercase()
    when {
        "vegan" in t -> 0
        "vegetar" in t -> 1
        else -> 2
    }
}

/** A diet tag's colour: sage for vegan and vegetarian, butter gluten-free, periwinkle halal. */
@Composable
internal fun DietTag(tag: String) {
    val dark = PokyhTheme.colors.isDark
    val t = tag.lowercase()
    val tone = when {
        "vegan" in t || "vegetar" in t -> PokyhDecorative.sage(dark)
        "glutenfrei" in t -> PokyhDecorative.butter(dark)
        "halal" in t -> PokyhDecorative.periwinkle(dark)
        else -> null
    }
    Text(
        tag,
        style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = tone?.ink ?: PokyhTheme.colors.textSecondary,
        modifier = Modifier
            .clip(PokyhShapes.pill)
            .background(tone?.fill ?: PokyhTheme.colors.nested)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DietTags(tags: List<String>, modifier: Modifier = Modifier, limit: Int = Int.MAX_VALUE) {
    if (tags.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        prioritizedTags(tags).take(limit).forEach { DietTag(it) }
    }
}

/** "Heute", "Morgen" or "Mittwoch, 29. Oktober": the day the dish is served. */
internal fun dishDayLabel(dish: Dish): String {
    val date = runCatching { java.time.LocalDate.parse(dish.date.take(10)) }.getOrNull() ?: return ""
    val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), date)
    val t = I18n.s.mensa
    return when (days) {
        0L -> t.today
        1L -> t.tomorrow
        else -> java.time.format.DateTimeFormatter.ofPattern(I18n.s.mensa.dayPattern, I18n.lang.value.locale).format(date)
            .replaceFirstChar { it.titlecase(I18n.lang.value.locale) }
    }
}

/** Under the name: ★ 4,2 (12) · 650 kcal. */
@Composable
internal fun DishMetaLine(dish: Dish, ratings: DishRatingsData, unknown: Boolean) {
    val colors = PokyhTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(PokyhIcons.starFilled, contentDescription = null, tint = Brand.star, modifier = Modifier.size(15.dp))
        Text(
            if (ratings.count > 0 && !unknown) Fmt.fixed(ratings.average, 1) else "–",
            style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Text("(${if (unknown) "–" else ratings.count})", style = PokyhType.callout, color = colors.textSecondary)
        dish.calories?.let {
            Text("·", style = PokyhType.callout, color = colors.textSecondary)
            Text("${it.toInt()} kcal", style = PokyhType.callout, color = colors.textSecondary)
        }
    }
}

/**
 * The rating as on the web: the average large with its stars and count, the 5→1 bars beside it,
 * and under them "Deine Bewertung" with the five stars to tap and the word for the chosen one.
 */
@Composable
internal fun DishRatingBlock(ratings: DishRatingsData, onRate: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val t = strings().mensa
    val values = ratings.ratings.values
    val count = values.size
    val bins = listOf(5, 4, 3, 2, 1).map { s -> values.count { Math.round(it).toInt() == s } }
    val maxBin = maxOf(1, bins.maxOrNull() ?: 0)
    val labels = t.rateLabels
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.widthIn(min = 88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (count > 0) Fmt.fixed(ratings.average, 1) else "–",
                    style = PokyhType.display.copy(fontSize = 46.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold),
                    color = colors.textPrimary,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    for (s in 1..5) {
                        val on = s <= Math.round(ratings.average)
                        Icon(if (on) PokyhIcons.starFilled else PokyhIcons.starEmpty, contentDescription = null, tint = if (on) Brand.star else colors.textTertiary, modifier = Modifier.size(13.dp))
                    }
                }
                Text(
                    when (count) {
                        0 -> t.noRatingYet
                        1 -> t.ratingOne
                        else -> t.ratingMany(count)
                    },
                    style = PokyhType.caption2,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bins.forEachIndexed { i, n ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${5 - i}", style = PokyhType.caption2, color = colors.textTertiary, modifier = Modifier.width(12.dp))
                        Box(Modifier.weight(1f).height(6.dp).clip(PokyhShapes.pill).background(colors.nested)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(n / maxBin.toFloat()).clip(PokyhShapes.pill).background(Brand.star))
                        }
                        Text("$n", style = PokyhType.caption2, color = colors.textTertiary, modifier = Modifier.width(22.dp))
                    }
                }
            }
        }
        val mine = ratings.myRating ?: 0
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.nested).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.yourRating, style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
                Text(
                    if (onRate == null) t.offlineRating else labels.getOrNull(mine - 1) ?: " ",
                    style = PokyhType.footnote,
                    color = colors.textSecondary,
                )
            }
            if (onRate != null) Row {
                for (s in 1..5) {
                    val on = s <= mine
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(PokyhShapes.pill)
                            .clickable { onRate(s) }
                            .semantics { contentDescription = "$s/5 · ${labels.getOrNull(s - 1).orEmpty()}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (on) PokyhIcons.starFilled else PokyhIcons.starEmpty, contentDescription = null, tint = if (on) Brand.star else colors.textTertiary, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

/** kcal and the macros as tiles, each with its colour, and the macros' shares as one bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DishNutrition(dish: Dish, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val t = strings().mensa
    val dark = colors.isDark
    val macros = listOfNotNull(
        dish.protein?.let { Triple(t.protein, it, PokyhDecorative.blush(dark).ink) },
        dish.carbs?.let { Triple(t.carbsLong, it, PokyhDecorative.butter(dark).ink) },
        dish.fat?.let { Triple(t.fat, it, PokyhDecorative.periwinkle(dark).ink) },
    )
    if (dish.calories == null && macros.isEmpty()) return
    val total = macros.sumOf { it.second }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dish.calories?.let { NutrientTile("${it.toInt()}", "kcal", null, Modifier.weight(1f)) }
            macros.forEach { (label, value, tone) -> NutrientTile("${value.toInt()} g", label, tone, Modifier.weight(1f)) }
        }
        if (total > 0) {
            Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                macros.forEach { (_, value, tone) ->
                    Box(Modifier.weight((value / total).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().clip(PokyhShapes.pill).background(tone))
                }
            }
        }
    }
}

@Composable
private fun NutrientTile(value: String, label: String, dot: Color?, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(colors.nested).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(value, style = PokyhType.headline.copy(fontWeight = FontWeight.ExtraBold), color = colors.textPrimary, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dot != null) Box(Modifier.size(8.dp).clip(PokyhShapes.pill).background(dot))
            Text(label, style = PokyhType.caption2, color = colors.textSecondary, maxLines = 1)
        }
    }
}

/** The allergens as chips on a fill tone (no outlines). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AllergenChips(allergens: List<String>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        allergens.forEach {
            Text(
                it,
                style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = PokyhTheme.colors.textPrimary,
                modifier = Modifier.clip(PokyhShapes.pill).background(PokyhTheme.colors.nested).padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
