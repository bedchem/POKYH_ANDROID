@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.bell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.bell.BellFeed
import dev.plattnericus.pokyh.core.bell.BellStore
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.ui.components.PixelSpriteArt
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.components.UnreadBadge
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class BellViewModel @Inject constructor(val store: BellStore) : ViewModel()

/**
 * The bell beside the messages in a tab's header, as on the web: a count of what is new since
 * the bell was last opened (a short ring when it goes up), and a sheet with the streak on top,
 * then what is due soon, then what happened lately. Opening it counts everything as seen; what
 * was new keeps its dot while the sheet stays open. Each item can be cleared away, or all at
 * once. Seen and cleared count on every device of the account, web included.
 */
@Composable
fun NotificationBell(onNavigate: (String) -> Unit, viewModel: BellViewModel = hiltViewModel()) {
    val store = viewModel.store
    val state by store.state.collectAsStateWithLifecycle()
    val t = strings().bell
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { store.start() }

    // The bell rings once whenever the count goes up.
    val ring = remember { Animatable(0f) }
    var lastCount by remember { mutableStateOf(state.newCount) }
    LaunchedEffect(state.newCount) {
        if (state.newCount > lastCount) {
            for (angle in listOf(14f, -12f, 9f, -6f, 3f, 0f)) ring.animateTo(angle, tween(70))
        }
        lastCount = state.newCount
    }

    Box {
        PokyhIconButton(
            icon = PokyhIcons.notifications,
            contentDescription = if (state.newCount > 0) t.labelNew(state.newCount) else t.title,
            onClick = {
                open = true
                store.refresh()
            },
            tinted = true,
            iconSize = 19.dp,
            modifier = Modifier.rotate(ring.value),
        )
        if (state.newCount > 0) {
            UnreadBadge(count = state.newCount, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
        }
    }

    if (open) {
        BellSheet(
            state = state,
            onDismiss = {
                open = false
                store.seeAll()
            },
            onPick = { item ->
                open = false
                store.seeAll()
                onNavigate(routeOf(item.target))
            },
            onClear = store::clear,
            onSeen = store::seeAll,
        )
    }
}

private fun routeOf(target: BellFeed.BellTarget): String = when (target) {
    is BellFeed.BellTarget.Grade -> PokyhDestinations.gradeSubject(target.lessonId, SchoolDates.currentSchoolYear)
    is BellFeed.BellTarget.Message -> PokyhDestinations.messageDetail(target.id, reply = true)
    BellFeed.BellTarget.Reminders -> PokyhDestinations.REMINDERS
    BellFeed.BellTarget.Todos -> PokyhDestinations.TODOS
    BellFeed.BellTarget.Profile -> PokyhDestinations.PROFILE
}

@Composable
private fun BellSheet(
    state: BellStore.State,
    onDismiss: () -> Unit,
    onPick: (BellFeed.Item) -> Unit,
    onClear: (List<String>) -> Unit,
    onSeen: () -> Unit,
) {
    val t = strings().bell
    val colors = PokyhTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    // What was new when the sheet opened keeps its dot while it is open.
    val shown = remember { state.fresh }
    LaunchedEffect(Unit) { onSeen() }
    // Rows on their way out (cleared), so they can slide away before they leave the feed.
    var leaving by remember { mutableStateOf(setOf<String>()) }
    fun clearAway(keys: List<String>) {
        if (keys.isEmpty()) return
        leaving = leaving + keys
        scope.launch {
            delay(320)
            onClear(keys)
            leaving = leaving - keys.toSet()
        }
    }
    val items = state.feed.all
    val staying = items.filter { it.key !in leaving }
    val newShown = staying.count { it.key in shown || it.key in state.fresh }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        shape = PokyhShapes.topXxl,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.lg).padding(bottom = PokyhSpacing.xxl)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Text(t.title, style = PokyhType.title3, color = colors.textPrimary)
                if (newShown > 0) {
                    Spacer(Modifier.width(PokyhSpacing.sm))
                    Text(t.newCount(newShown), style = PokyhType.footnote.semibold(), color = colors.accentText)
                }
                Spacer(Modifier.weight(1f))
                if (staying.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(PokyhShapes.pill)
                            .clickable { clearAway(staying.map { it.key }) }
                            .padding(horizontal = PokyhSpacing.md, vertical = PokyhSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(PokyhIcons.delete, contentDescription = null, tint = Brand.danger, modifier = Modifier.size(15.dp))
                        Text(t.clearAll, style = PokyhType.footnote.semibold(), color = colors.textSecondary)
                    }
                }
            }
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                state.streak?.let { StreakCard(it) }
                when {
                    state.loading -> repeat(3) {
                        Row(Modifier.padding(vertical = PokyhSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            SkeletonBlock(height = 40.dp, width = 40.dp, shape = PokyhShapes.md)
                            Spacer(Modifier.width(PokyhSpacing.md))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SkeletonBlock(height = 12.dp, width = 180.dp)
                                SkeletonBlock(height = 10.dp, width = 110.dp)
                            }
                        }
                    }
                    items.isEmpty() -> Column(
                        Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PixelSpriteArt(PixelSprite.Bell, width = 148.dp)
                        Spacer(Modifier.height(PokyhSpacing.sm))
                        Text(t.emptyTitle, style = PokyhType.headline, color = colors.textPrimary)
                        Text(t.emptyText, style = PokyhType.footnote, color = colors.textSecondary)
                    }
                    else -> {
                        state.feed.current.forEach { item ->
                            BellRow(item, state.now, fresh = item.key in shown || item.key in state.fresh, leaving = item.key in leaving, onPick = { onPick(item) }, onClear = { clearAway(listOf(item.key)) })
                        }
                        if (state.feed.current.isNotEmpty() && state.feed.recent.isNotEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.sm).height(1.dp).background(colors.separator))
                        }
                        state.feed.recent.forEach { item ->
                            BellRow(item, state.now, fresh = item.key in shown || item.key in state.fresh, leaving = item.key in leaving, onPick = { onPick(item) }, onClear = { clearAway(listOf(item.key)) })
                        }
                    }
                }
            }
        }
    }
}

/** The streak on top: how many days in a row, the longest, and the last week as squares. */
@Composable
private fun StreakCard(streak: BellStore.StreakView) {
    val t = strings().bell
    val colors = PokyhTheme.colors
    val tone = PokyhDecorative.butter(colors.isDark)
    val todayActive = streak.week.lastOrNull()?.second ?: false
    val title = when (streak.days) {
        0 -> t.streakNone
        1 -> t.streakOne
        else -> t.streakDays(streak.days)
    }
    val sub = if (!todayActive && streak.days > 0) t.streakKeep else t.streakLongest(maxOf(streak.longest, streak.days))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PokyhSpacing.sm)
            .clip(PokyhShapes.lg)
            .background(colors.cardAlt)
            .padding(PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        Box(Modifier.size(44.dp).clip(PokyhShapes.md).background(tone.fill), contentAlignment = Alignment.Center) {
            Icon(if (streak.days > 0) PokyhIcons.streak else PokyhIcons.streakOut, contentDescription = null, tint = tone.ink, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = PokyhType.headline.monospacedDigits(), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = PokyhType.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            streak.week.forEachIndexed { i, (_, active) ->
                Box(
                    Modifier
                        .size(width = 9.dp, height = if (i == streak.week.lastIndex) 18.dp else 14.dp)
                        .clip(PokyhShapes.xs)
                        .background(if (active) Brand.accent else colors.textPrimary.copy(alpha = 0.1f)),
                )
            }
        }
    }
}

@Composable
private fun BellRow(item: BellFeed.Item, now: Long, fresh: Boolean, leaving: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    val t = strings().bell
    val colors = PokyhTheme.colors
    val at = whenText(item, now)
    val dark = colors.isDark
    val (tile, title, subParts) = when (item.kind) {
        BellFeed.Kind.Grade -> {
            val v = item.value ?: 0.0
            val tone = when {
                v >= 6.4 -> PokyhDecorative.sage(dark)
                v >= 6 -> PokyhDecorative.butter(dark)
                else -> PokyhDecorative.blush(dark)
            }
            Triple(TileSpec(text = Fmt.num(v), fill = tone.fill, ink = tone.ink), t.gradeNew(item.title), listOf(item.sub, at))
        }
        BellFeed.Kind.Message -> {
            val tone = PokyhDecorative.periwinkle(dark)
            Triple(TileSpec(icon = PokyhIcons.messages, fill = tone.fill, ink = tone.ink), item.title.ifEmpty { t.noSubject }, listOf(item.sub, at))
        }
        BellFeed.Kind.Reminder -> {
            val tone = PokyhDecorative.blush(dark)
            Triple(TileSpec(icon = PokyhIcons.reminderNew, fill = tone.fill, ink = tone.ink), item.title, listOf(t.reminderBy(item.sub), at))
        }
        BellFeed.Kind.ReminderSoon -> {
            val tone = PokyhDecorative.blush(dark)
            Triple(TileSpec(icon = PokyhIcons.reminderSoon, fill = tone.fill, ink = tone.ink), item.title, listOf("${t.reminderSoon} $at", item.sub))
        }
        BellFeed.Kind.Todo -> {
            val tone = PokyhDecorative.sage(dark)
            Triple(TileSpec(icon = PokyhIcons.todos, fill = tone.fill, ink = tone.ink), item.title, listOf(if (item.overdue) "${t.todoOverdue} · $at" else "${t.todoDue} $at"))
        }
        BellFeed.Kind.Streak -> {
            val tone = PokyhDecorative.butter(dark)
            Triple(TileSpec(icon = PokyhIcons.streak, fill = tone.fill, ink = tone.ink), t.milestone(item.value?.toInt() ?: 0), listOf(t.milestoneSub, at))
        }
        BellFeed.Kind.StreakLost -> Triple(
            TileSpec(icon = PokyhIcons.streakOut, fill = colors.cardAlt, ink = colors.textTertiary),
            t.streakLost(item.value?.toInt() ?: 0),
            listOf(t.streakLostSub, at),
        )
    }
    AnimatedVisibility(visible = !leaving, exit = fadeOut(tween(200)) + shrinkVertically(tween(300))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PokyhShapes.md)
                .clickable(onClick = onPick)
                .padding(vertical = PokyhSpacing.sm, horizontal = PokyhSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        ) {
            Box(Modifier.size(40.dp).clip(PokyhShapes.md).background(tile.fill), contentAlignment = Alignment.Center) {
                if (tile.icon != null) Icon(tile.icon, contentDescription = null, tint = tile.ink, modifier = Modifier.size(19.dp))
                else Text(tile.text.orEmpty(), style = PokyhType.footnote.semibold().monospacedDigits(), color = tile.ink, maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (fresh) PokyhType.callout.semibold() else PokyhType.callout,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subParts.filter { it.isNotEmpty() }.joinToString(" · "),
                    style = PokyhType.caption,
                    color = if (item.overdue) Brand.danger else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (fresh) Box(Modifier.size(8.dp).clip(PokyhShapes.pill).background(Brand.accent))
            PokyhIconButton(
                icon = PokyhIcons.close,
                contentDescription = t.clear,
                onClick = onClear,
                tint = colors.textTertiary,
                size = 32.dp,
                iconSize = 14.dp,
            )
        }
    }
}

private class TileSpec(val icon: ImageVector? = null, val text: String? = null, val fill: Color, val ink: Color)


/** "vor 5 Min.", "in 2 Std.", "morgen 08:00", "Gestern", "12. Okt." */
@Composable
private fun whenText(item: BellFeed.Item, now: Long): String {
    val t = strings().bell
    val c = strings().common
    val day = 86_400_000L
    val days = ((BellFeed.startOfDay(now) - BellFeed.startOfDay(item.at)).toDouble() / day).roundToLong()
    val date = Instant.ofEpochMilli(item.at).atZone(BellFeed.zone).toLocalDate()
    fun dateText() = c.dayMonthShort(date.dayOfMonth, date.monthValue)
    if (item.allDay) {
        return when {
            days <= 0 -> t.today
            days == 1L -> t.yesterday
            days < 7 -> c.daysAgo(days)
            else -> dateText()
        }
    }
    val time = Instant.ofEpochMilli(item.at).atZone(BellFeed.zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    val ahead = item.at - now
    if (ahead > 0) {
        return when {
            ahead < 3_600_000 -> t.inMinutes(maxOf(1, (ahead / 60_000.0).roundToLong()))
            days == 0L -> if (item.kind == BellFeed.Kind.Todo) t.todayAt(time) else t.inHours((ahead / 3_600_000.0).roundToLong())
            days == -1L -> t.tomorrowAt(time)
            else -> "${dateText()} $time"
        }
    }
    val min = -ahead / 60_000
    if (item.kind == BellFeed.Kind.Todo) return if (days == 0L) t.todayAt(time) else if (days == 1L) t.yesterdayAt(time) else dateText()
    return when {
        min < 1 -> c.justNow
        min < 60 -> c.minutesAgo(min)
        days == 0L -> c.hoursAgo(min / 60)
        days == 1L -> t.yesterdayAt(time)
        days < 7 -> c.daysAgo(days)
        else -> dateText()
    }
}
