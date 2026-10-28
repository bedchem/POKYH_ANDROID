package dev.plattnericus.pokyh.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.data.model.MessagePreview
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.ui.components.InitialAvatar
import dev.plattnericus.pokyh.ui.components.MiniStarsSlot
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.components.TileRowSkeleton
import dev.plattnericus.pokyh.ui.messages.MessageFormat
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.pressHighlight

// The web's dashboard blocks (components/dashboard/widgets.tsx, app/[lang]/(app)/home/page.tsx):
// the greeting with the two actions, the next exam as the page's one coloured block, the latest
// messages, and today's (or the next) Mensa with its photo.

private val BlockShape = RoundedCornerShape(26.dp)

/** A row that shows a soft fill when pressed, as the web's rows do on hover. */
@Composable
private fun Modifier.rowPress(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .clip(RoundedCornerShape(18.dp))
        .pressHighlight(interaction, RoundedCornerShape(18.dp))
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/**
 * "Guten Morgen, Anna" — the name in the accent — with the day and the class under it, and the
 * web's two actions beside (wide) or below (phones): write a message, report an absence.
 */
@Composable
internal fun HomeGreeting(
    greeting: String,
    firstName: String,
    dateLine: String,
    canReport: Boolean,
    showActions: Boolean,
    wide: Boolean,
    onWrite: () -> Unit,
    onReport: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val t = strings().home
    val heading = buildAnnotatedString {
        append(greeting)
        if (firstName.isNotEmpty()) {
            append(", ")
            withStyle(SpanStyle(color = Brand.accent)) { append(firstName) }
        }
    }
    val actions: @Composable (Modifier) -> Unit = { m ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
            HomeActionButton(t.writeMessage, PokyhIcons.compose, tone = true, onClick = onWrite, modifier = if (wide) Modifier else Modifier.weight(1f))
            if (canReport) HomeActionButton(t.reportAbsence, PokyhIcons.notAStudent, tone = false, onClick = onReport, modifier = if (wide) Modifier else Modifier.weight(1f))
        }
    }
    if (wide) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(heading, style = PokyhType.display.copy(fontSize = 44.sp, lineHeight = 46.sp), color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                Text(dateLine, style = PokyhType.body.copy(fontSize = 17.sp), color = colors.textSecondary)
            }
            if (showActions) actions(Modifier)
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(heading, style = PokyhType.display, color = colors.textPrimary)
            Text(dateLine, style = PokyhType.callout, color = colors.textSecondary)
            if (showActions) {
                Spacer(Modifier.height(12.dp))
                actions(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun HomeActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val fill = if (tone) colors.accentTint else colors.cardAlt
    val ink = if (tone) colors.accentText else colors.textPrimary
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(PokyhShapes.pill)
            .background(fill)
            .pressHighlight(interaction, PokyhShapes.pill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(text, style = PokyhType.callout.semibold(), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The web's offline line under the greeting: a butter pill. */
@Composable
internal fun HomeOfflinePill() {
    val tone = PokyhDecorative.butter(PokyhTheme.colors.isDark)
    Row(
        Modifier.clip(PokyhShapes.pill).background(tone.fill).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(PokyhIcons.offline, contentDescription = null, tint = tone.ink, modifier = Modifier.size(16.dp))
        Text(strings().home.offlineLine, style = PokyhType.footnote.semibold(), color = tone.ink)
    }
}

/**
 * The next exam as the page's coloured block (the web's `ExamCard`): its day as a chip, how soon
 * set large, the subject underlined, the time and the room. Without one, a tick and the sentence.
 */
@Composable
internal fun ExamBlock(exam: TimetableEntry?, loaded: Boolean, unknown: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = strings().home
    val c = strings().common
    val tone = PokyhDecorative.butter(PokyhTheme.colors.isDark)
    PokyhSection(modifier = modifier, title = t.sectionExam) {
        val block = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 220.dp)
            .clip(BlockShape)
            .background(tone.fill)
        when {
            !loaded -> Column(block.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                SkeletonBlock(height = 24.dp, width = 110.dp)
                SkeletonBlock(height = 44.dp, width = 150.dp)
                SkeletonBlock(height = 20.dp, width = 200.dp)
            }
            exam == null -> Column(block.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                Icon(if (unknown) PokyhIcons.offline else PokyhIcons.ok, contentDescription = null, tint = tone.ink, modifier = Modifier.size(32.dp))
                Text(if (unknown) t.examOffline else t.noExam, style = PokyhType.title2.copy(fontWeight = FontWeight.ExtraBold), color = tone.ink)
            }
            else -> {
                val d = exam.date
                val date = runCatching { java.time.LocalDate.of(d / 10000, d / 100 % 100, d % 100) }.getOrNull()
                val today = java.time.LocalDate.now()
                val days = date?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) } ?: 0
                val whenText = when {
                    days <= 0 -> t.today
                    days == 1L -> t.tomorrow
                    else -> t.inDays(days)
                }
                val chip = date?.let { "${c.weekdaysShort[it.dayOfWeek.ordinal]}, ${c.dayMonthShort(it.dayOfMonth, it.monthValue)}" }.orEmpty()
                Column(
                    block.clickable(onClick = onOpen).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        chip,
                        style = PokyhType.footnote.bold(),
                        color = tone.ink,
                        modifier = Modifier.clip(PokyhShapes.pill).background(tone.ink.copy(alpha = 0.13f)).padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    Column {
                        Text(whenText, style = PokyhType.display.copy(fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold), color = tone.ink)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            exam.subjectLong.ifEmpty { exam.subjectName.ifEmpty { t.exam } },
                            style = PokyhType.title2.copy(textDecoration = TextDecoration.Underline),
                            color = tone.ink,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOfNotNull(exam.startTime.takeIf { it > 0 }?.let { Fmt.time(it) }, exam.roomName.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = PokyhType.callout.semibold(),
                            color = tone.ink.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

/** The four latest messages of the inbox (the web's `MessageList`). */
@Composable
internal fun MessagesBlock(
    loading: Boolean,
    messages: List<MessagePreview>,
    unavailable: Boolean,
    onAll: () -> Unit,
    onOpen: (MessagePreview) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings().home
    val colors = PokyhTheme.colors
    PokyhSection(modifier = modifier, title = strings().nav.messages, trailing = { PokyhTextButton(text = t.all, onClick = onAll) }) {
        when {
            loading && messages.isEmpty() -> TileRowSkeleton(rows = 3)
            messages.isEmpty() -> HomeStateCard(if (unavailable) PixelSprite.Unplugged else PixelSprite.Mail, if (unavailable) t.loadFailed else t.noMessages)
            else -> PokyhCard(padding = PokyhSpacing.sm) {
                messages.take(4).forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().rowPress { onOpen(m) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        InitialAvatar(name = m.senderName, size = 44.dp, color = colors.textSecondary)
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.subject.ifBlank { strings().messages.noSubject },
                                style = if (m.isRead) PokyhType.callout else PokyhType.callout.bold(),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(m.senderName.ifBlank { strings().messages.unknown }, style = PokyhType.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(MessageFormat.dateLabel(m.sentDate), style = PokyhType.caption, color = colors.textTertiary)
                            if (!m.isRead) Box(Modifier.size(10.dp).clip(PokyhShapes.pill).background(Brand.accent))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Today's (or the next) dishes (the web's `MensaCard`): the first one large with its photo, its
 * stars and its diet, the others as rows.
 */
@Composable
internal fun MensaBlock(
    loading: Boolean,
    week: List<HomeViewModel.MensaWeekDay>,
    ratings: Map<String, DishRatingsData>,
    ratingsLoading: Boolean,
    ratingsUnavailable: Boolean,
    onAll: () -> Unit,
    onOpen: (Dish) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings().home
    val colors = PokyhTheme.colors
    val day = week.firstOrNull { !it.isPast && it.dishes.isNotEmpty() }
    if (!loading && day == null) return
    val title = when {
        day == null || day.isToday -> t.mensaToday
        else -> "${t.mensaNext} · ${strings().common.weekdays[day.date.dayOfWeek.ordinal]}"
    }
    PokyhSection(modifier = modifier, title = title, trailing = { PokyhTextButton(text = strings().nav.mensa, onClick = onAll) }) {
        if (day == null) {
            TileRowSkeleton(rows = 1)
            return@PokyhSection
        }
        val dishes = day.dishes.take(3)
        val lead = dishes.first()
        val butter = PokyhDecorative.butter(colors.isDark)
        PokyhCard(padding = PokyhSpacing.sm) {
            Row(
                Modifier.fillMaxWidth().rowPress { onOpen(lead) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.width(128.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(butter.fill), contentAlignment = Alignment.Center) {
                    if (!lead.imageUrl.isNullOrBlank()) {
                        AsyncImage(model = lead.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(PokyhIcons.mensa, contentDescription = null, tint = butter.ink.copy(alpha = 0.6f), modifier = Modifier.size(30.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(lead.name, style = PokyhType.headline.bold(), color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    RatingRow(ratings[lead.id], ratingsLoading, ratingsUnavailable)
                    dietOf(lead)?.let { diet ->
                        val sage = PokyhDecorative.sage(colors.isDark)
                        Text(diet, style = PokyhType.caption.bold(), color = sage.ink, modifier = Modifier.clip(PokyhShapes.pill).background(sage.fill).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            dishes.drop(1).forEach { d ->
                Row(
                    Modifier.fillMaxWidth().rowPress { onOpen(d) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(butter.fill), contentAlignment = Alignment.Center) {
                        if (!d.imageUrl.isNullOrBlank()) {
                            AsyncImage(model = d.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(PokyhIcons.mensa, contentDescription = null, tint = butter.ink.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = PokyhType.callout.semibold(), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        RatingRow(ratings[d.id], ratingsLoading, ratingsUnavailable)
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingRow(rating: DishRatingsData?, loading: Boolean, unavailable: Boolean) {
    val colors = PokyhTheme.colors
    val count = rating?.count ?: 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (count > 0 || loading) {
            MiniStarsSlot(loading = loading && rating == null, average = rating?.average ?: 0.0, count = count, unknown = unavailable && rating == null, height = 14.dp)
        }
        if (count > 0) {
            Text(Fmt.fixed(rating!!.average, 1), style = PokyhType.footnote.semibold().monospacedDigits(), color = colors.textPrimary)
            Text("($count)", style = PokyhType.footnote, color = colors.textSecondary)
        } else if (!loading) {
            Text(strings().home.noRatingYet, style = PokyhType.footnote, color = colors.textSecondary)
        }
    }
}

/** The web's `dietOf`: vegan before vegetarian, from the dish's tags. */
private fun dietOf(d: Dish): String? {
    val tags = d.tags.map { it.lowercase() }
    return when {
        tags.any { it.contains("vegan") } -> dev.plattnericus.pokyh.core.i18n.I18n.s.home.vegan
        tags.any { it.contains("vegetar") } -> dev.plattnericus.pokyh.core.i18n.I18n.s.home.vegetarian
        else -> null
    }
}

/**
 * Home for an account without WebUntis (web home/page.tsx): the school's blocks need a school
 * account, so a periwinkle card says so and leads to what works without one.
 */
@Composable
internal fun NoUntisBlock(onTodos: () -> Unit, onReminders: () -> Unit, modifier: Modifier = Modifier) {
    val t = strings().home
    val tone = PokyhDecorative.periwinkle(PokyhTheme.colors.isDark)
    Column(
        modifier.fillMaxWidth().clip(BlockShape).background(tone.fill).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t.untisTitle, style = PokyhType.title2.copy(fontWeight = FontWeight.ExtraBold), color = tone.ink)
            Text(t.untisText, style = PokyhType.callout, color = tone.ink.copy(alpha = 0.85f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(strings().school.todos to onTodos, strings().school.reminders to onReminders).forEach { (label, go) ->
                Box(
                    Modifier.weight(1f).clip(PokyhShapes.pill).background(tone.ink).clickable(onClick = go).padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = tone.fill)
                }
            }
        }
    }
}
