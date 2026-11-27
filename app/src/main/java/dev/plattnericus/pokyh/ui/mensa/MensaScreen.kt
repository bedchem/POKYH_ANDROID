@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.mensa

import dev.plattnericus.pokyh.ui.theme.pressHighlight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.aspectRatio
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
import dev.plattnericus.pokyh.ui.components.MiniStarsSlot
import dev.plattnericus.pokyh.ui.components.PokyhBleedCard
import dev.plattnericus.pokyh.ui.components.PokyhSectionHeader
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
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
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import dev.plattnericus.pokyh.ui.theme.wideContent

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

    // Coming back to this tab re-checks that there is something to show. The tab shell keeps
    // this screen's ViewModel alive across switches, so without it an empty state that happened
    // once stayed empty until the app restarted — which is how "Mensa is sometimes blank after
    // leaving Home" looked from the outside. Cheap: with days on screen this returns at once,
    // and the TTL cache absorbs the rest.
    LaunchedEffect(Unit) { viewModel.onAppear() }

    Scaffold(
        modifier = Modifier.wideContent(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            // Only the web's top bar stays; "Mensa" and its lead scroll with the plan (MensaIntro).
            PokyhTopBar(
                fillActions = true,
                title = null,
                nav = TopBarNav.None,
                actions = {
                    TabRootActions(
                        avatarContent = { CurrentUserAvatar() },
                        unreadMessages = rememberUnreadMessageCount(),
                        onMessages = { onNavigate(PokyhDestinations.MESSAGES) },
                        onProfile = { onNavigate(PokyhDestinations.PROFILE) },
                        onNavigate = onNavigate,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                ui.loading && ui.groups.isEmpty() -> MensaLoading()

                ui.error != null && ui.groups.isEmpty() ->
                    ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)

                ui.groups.isEmpty() && viewModel.mensaAvailable.collectAsStateWithLifecycle().value == false -> EmptyStateView(
                    sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Empty,
                    title = strings().mensa.noMensaTitle,
                    subtitle = strings().mensa.noMensaText,
                )

                ui.groups.isEmpty() -> EmptyStateView(
                    sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Empty,
                    title = strings().mensa.emptyTitle,
                    // In a break, which one and until when, as the web says it above the plan.
                    subtitle = MensaSchedule.breakOn(ui.breaks)
                        ?.let { strings().mensa.breakNow(MensaSchedule.breakName(it), MensaSchedule.breakDate(it.end)) }
                        ?: strings().mensa.emptySubtitle,
                )

                else -> MensaPlan(
                    groups = ui.groups,
                    breaks = ui.breaks,
                    ratings = ui.ratings,
                    ratingsLoading = ui.ratingsLoading,
                    ratingsUnavailable = ui.ratingsUnavailable,
                    onDishClick = onDishClick,
                )
            }
        }
    }
}

// ── Der Plan, wie im Web ────────────────────────────────────────────────────

/** The dish served large: the first with a photo, else the first (the web's `leadDish`). */
private fun leadDish(dishes: List<Dish>): Dish? = dishes.firstOrNull { !it.imageUrl.isNullOrBlank() } ?: dishes.firstOrNull()

/** Monday of the date's week as a count of weeks, to group the days by week. */
private fun weekIndex(d: kotlinx.datetime.LocalDate): Long =
    (d.toEpochDays().toLong() - d.dayOfWeek.ordinal) / 7

/**
 * The web's Mensa: the next dish served as pixel art between fork and knife, what it is and how
 * the school rated it; the day's other dishes as chips on small plates; then the whole plan by
 * week, each dish on a small pixel plate. Every dish opens its own page.
 */
@Composable
private fun MensaPlan(
    groups: List<MensaSchedule.DayGroup>,
    breaks: List<dev.plattnericus.pokyh.data.model.MenuBreak>,
    ratings: Map<String, DishRatingsData>,
    ratingsLoading: Boolean,
    ratingsUnavailable: Boolean,
    onDishClick: (String) -> Unit,
) {
    val t = strings().mensa
    val colors = PokyhTheme.colors
    val next = groups.first()
    val served = leadDish(next.dishes) ?: return
    val today = todayLocalDate()
    val thisWeek = weekIndex(today)
    val weeks = groups.groupBy { weekIndex(it.date) }.toList()
    val c = strings().common
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = PokyhSpacing.screenH, end = PokyhSpacing.screenH, bottom = 96.dp),
    ) {
        item(key = "intro") { MensaIntro(Modifier.fadeIn()) }
        // The break the menu is in, above it all: which one and until when (the web's `breakNow`).
        MensaSchedule.breakOn(breaks, today)?.let { b ->
            item(key = "breakNow") {
                Text(
                    t.breakNow(MensaSchedule.breakName(b), MensaSchedule.breakDate(b.end)),
                    style = PokyhType.headline.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 32.dp).fadeIn(),
                )
            }
        }
        item(key = "served") {
            // The next dish, served (MensaClient.tsx): the plate, then 32dp to the text column.
            Column(Modifier.fadeIn()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(PokyhShapes.lg)
                        .clickable { onDishClick(served.id) },
                ) {
                    PixelPlate(style = foodStyleOf(served), seed = served.id, photo = served.imageUrl, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(32.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(MensaSchedule.label(next.date), style = PokyhType.callout.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textTertiary)
                    Text(
                        served.name,
                        style = PokyhType.display.copy(fontSize = 32.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.045).em),
                        color = colors.textPrimary,
                    )
                    served.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = PokyhType.body.copy(fontSize = 17.sp, lineHeight = 28.sp), color = colors.textSecondary)
                    }
                    RatingLine(ratings[served.id], loading = ratingsLoading, unknown = ratingsUnavailable)
                    if (served.tags.isNotEmpty()) DietTags(served.tags, limit = 4)
                    KeyButton(text = t.rateAndComment, onClick = { onDishClick(served.id) }, modifier = Modifier.padding(top = 8.dp))
                    val others = next.dishes.filter { it != served }
                    if (others.isNotEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            others.forEach { d ->
                                Row(
                                    modifier = Modifier
                                        .clip(PokyhShapes.pill)
                                        .background(colors.card)
                                        .clickable { onDishClick(d.id) }
                                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PixelPlate(style = foodStyleOf(d), seed = d.id, photo = d.imageUrl, size = PlateSize.Plate, modifier = Modifier.width(36.dp))
                                    Text(d.name, style = PokyhType.subheadline.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "allDays") {
            Text(
                t.allDays,
                style = PokyhType.largeTitle.copy(fontSize = 30.sp, lineHeight = 31.sp, letterSpacing = (-0.045).em),
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 96.dp),
            )
        }
        weeks.forEachIndexed { i, (w, days) ->
            // The breaks the plan waits out before this week (the web's `breaksBetween`).
            val after = if (i == 0) today else weeks[i - 1].second.last().date
            MensaSchedule.breaksBetween(breaks, after, days.first().date).forEach { b ->
                item(key = "break-${b.start}") {
                    Text(
                        t.breakRange(MensaSchedule.breakName(b), MensaSchedule.breakDate(b.start), MensaSchedule.breakDate(b.end)),
                        style = PokyhType.callout.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
            }
            item(key = "week-$w") {
                val first = days.first().date
                val title = when (w) {
                    thisWeek -> t.thisWeek
                    thisWeek + 1 -> t.nextWeek
                    else -> t.fromDate(c.dayMonth(first.dayOfMonth, first.monthNumber))
                }
                Text(title, style = PokyhType.callout.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = colors.textTertiary, modifier = Modifier.padding(top = 40.dp, bottom = 16.dp))
            }
            days.forEach { day ->
                day.dishes.forEach { dish ->
                    item(key = "${day.date}:${dish.id}") {
                        PlanRow(
                            dish = dish,
                            dayLabel = MensaSchedule.label(day.date),
                            rating = ratings[dish.id],
                            ratingsLoading = ratingsLoading,
                            ratingsUnavailable = ratingsUnavailable,
                            onClick = { onDishClick(dish.id) },
                            modifier = Modifier.padding(bottom = 12.dp).fadeIn(),
                        )
                    }
                }
            }
        }
    }
}

/** One dish of the plan: its small plate, the day, the name, the rating. */
@Composable
private fun PlanRow(
    dish: Dish,
    dayLabel: String,
    rating: DishRatingsData?,
    ratingsLoading: Boolean,
    ratingsUnavailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    val shape = dev.plattnericus.pokyh.ui.theme.SmoothCornerShape(40.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    // The web's plan row: a `.card` (surface, 24dp corners), 12dp in and 20dp on the right, the
    // plate 72dp, 16dp to the day, the name at 17 bold and the rating.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.card)
            .pressHighlight(interaction, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PixelPlate(style = foodStyleOf(dish), seed = dish.id, photo = dish.imageUrl, size = PlateSize.Plate, modifier = Modifier.width(72.dp))
        Column(Modifier.weight(1f)) {
            Text(dayLabel.replaceFirstChar { it.uppercase() }, style = PokyhType.footnote.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = colors.textTertiary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                dish.name,
                style = PokyhType.headline.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            RatingLine(rating, loading = ratingsLoading, unknown = ratingsUnavailable)
        }
    }
}

/** The web's page intro (components/site/PageIntro.tsx, in the app frame): "Mensa" at 42 and the lead. */
@Composable
private fun MensaIntro(modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    Column(modifier.padding(top = 16.dp, bottom = 56.dp)) {
        Text(
            strings().nav.mensa,
            style = PokyhType.display.copy(fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em),
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(28.dp))
        Text(strings().mensa.pageLead, style = PokyhType.body.copy(fontSize = 18.sp, lineHeight = 29.sp), color = colors.textSecondary)
    }
}

/**
 * The web's plain "key" button, large (globals.css `.key.key-lg` on the site): ink on paper in the
 * light theme, indigo in the dark one; 17sp at 650, an arrow after the text.
 */
@Composable
private fun KeyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val dark = PokyhTheme.colors.isDark
    val bg = if (dark) Color(0xFF4D45AD) else Color(0xFF1C1A17)
    val fg = if (dark) Color.White else Color(0xFFF5F2ED)
    val shape = dev.plattnericus.pokyh.ui.theme.SmoothCornerShape(28.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 56.dp)
            .clip(shape)
            .background(bg)
            .pressHighlight(interaction, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 30.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = PokyhType.headline.copy(fontSize = 17.sp, fontWeight = FontWeight(650), letterSpacing = (-0.01).em), color = fg)
        Text("→", style = PokyhType.headline.copy(fontSize = 17.sp, fontWeight = FontWeight(650)), color = fg)
    }
}

/** The stars, and the average with how many voted — or that nobody has (the web's `RatingLine`). */
@Composable
private fun RatingLine(rating: DishRatingsData?, loading: Boolean, unknown: Boolean) {
    val t = strings().mensa
    val colors = PokyhTheme.colors
    val count = rating?.count ?: 0
    // The web's `RatingLine`: 15dp stars, then 14sp in the second ink, 8dp apart.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniStarsSlot(loading = loading && rating == null, average = rating?.average ?: 0.0, count = count, unknown = unknown && rating == null)
        if (count > 0) {
            val avg = Fmt.fixed(rating!!.average, 1)
            Text("$avg · ${if (count == 1) t.ratingOne else t.ratingMany(count)}", style = PokyhType.footnote.copy(fontSize = 14.sp).monospacedDigits(), color = colors.textSecondary)
        } else if (!loading && !unknown) {
            Text(t.noRatingYet, style = PokyhType.footnote.copy(fontSize = 14.sp), color = colors.textSecondary)
        }
    }
}

/** While the menu loads: the table laid where the next dish will be served, the rows holding their place. */
@Composable
private fun MensaLoading() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = PokyhSpacing.screenH),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        MensaIntro(Modifier.padding(bottom = 0.dp))
        PixelPlate(style = FoodStyle.Pasta, seed = "loading", empty = true, modifier = Modifier.fillMaxWidth())
        SkeletonBlock(height = 16.dp, width = 96.dp)
        SkeletonBlock(height = 34.dp, width = 280.dp)
        SkeletonBlock(height = 20.dp, width = 176.dp)
        Spacer(Modifier.height(PokyhSpacing.xl))
        repeat(3) { i ->
            PokyhCard(padding = PokyhSpacing.md) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.lg)) {
                    PixelPlate(style = FoodStyle.Pasta, seed = "loading-$i", empty = true, size = PlateSize.Plate, modifier = Modifier.width(72.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                        SkeletonBlock(height = 12.dp, width = 96.dp)
                        SkeletonBlock(height = 18.dp, width = 200.dp)
                    }
                }
            }
        }
    }
}

// ── Gericht-Karte (Liste, schreibgeschützte Sterne) ─────────────────────────

/**
 * One dish.
 *
 * The rating line is a [MiniStarsSlot] rather than a conditional [MiniStars]: it holds its
 * height whether the stars are loading, present or absent, so a list of cards doesn't shuffle as
 * the ratings arrive a moment after the dishes.
 */
@Composable
private fun DishCard(
    dish: Dish,
    ratings: DishRatingsData?,
    ratingsLoading: Boolean,
    ratingsUnavailable: Boolean,
    onClick: () -> Unit,
) {
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
            MiniStarsSlot(
                loading = ratingsLoading && ratings == null,
                average = ratings?.average ?: 0.0,
                count = ratings?.count ?: 0,
                unknown = ratingsUnavailable && ratings == null,
            )
        }
    }
}

// ── Bild (geteilt mit DishDetailScreen) ─────────────────────────────────────

/**
 * The dish photo as it is (the web's `Food`): POKYH's own photos are a plate from above on a
 * transparent ground, so it stands bare, whole and square, never cropped or framed. Without a
 * photo, or when it can't be loaded, the pixel plate serves the dish.
 */
@Composable
fun DishPhoto(dish: Dish, modifier: Modifier = Modifier) {
    val url = dish.imageUrl
    var failed by remember(url) { mutableStateOf(false) }
    if (url.isNullOrBlank() || failed) {
        PixelPlate(style = foodStyleOf(dish), seed = dish.id, modifier = modifier.fillMaxWidth())
        return
    }
    coil3.compose.AsyncImage(
        model = dev.plattnericus.pokyh.core.images.DishImages.url(url, dev.plattnericus.pokyh.core.images.DishImages.TABLE),
        contentDescription = dish.name,
        contentScale = ContentScale.Fit,
        onError = { failed = true },
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
    )
}

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
