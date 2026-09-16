package dev.plattnericus.pokyh.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.toLocalDate
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.ui.components.AsOfLabel
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.IconTile
import dev.plattnericus.pokyh.ui.components.MensaDayCardSkeleton
import dev.plattnericus.pokyh.ui.components.MiniStarsSlot
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhFeatureTile
import dev.plattnericus.pokyh.ui.components.PokyhFittedText
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhInlineNotice
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTileRow
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TileRowSkeleton
import dev.plattnericus.pokyh.ui.components.rememberReorderState
import dev.plattnericus.pokyh.ui.components.reorderable
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.profile.CurrentUserAvatar
import dev.plattnericus.pokyh.ui.profile.rememberUnreadMessageCount
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.DecorativeTone
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.gradeColor
import dev.plattnericus.pokyh.ui.theme.insetSurface
import dev.plattnericus.pokyh.ui.theme.nestedSurface
import dev.plattnericus.pokyh.ui.theme.statusTone
import dev.plattnericus.pokyh.ui.theme.subjectTone
import kotlin.math.round
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Home. The one screen with a personal greeting instead of a
 * [dev.plattnericus.pokyh.ui.components.PokyhTopBar] title, so the greeting itself carries the
 * screen's [PokyhType.display] heading and hosts the Messages/Profile actions.
 *
 * **The order of the blocks belongs to the user.** They come from [HomeLayout]; the pencil in
 * the greeting row arms arrange mode *on this screen*, where a long press lifts a card and
 * moves it. Nothing can be switched off — the default order is the day in the order it
 * happens: quick links, next exam, today’s lessons, today’s menu, newest grades.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val arranging by viewModel.editing.collectAsStateWithLifecycle()

    // Back leaves arrange mode rather than the tab — the same expectation any editable
    // surface sets, and cheaper than making the user find the Fertig button again.
    BackHandler(enabled = arranging) { viewModel.setEditing(false) }

    Box(modifier = Modifier.fillMaxSize().appBackground()) {
        if (state.error != null) {
            ErrorStateView(message = state.error!!, onRetry = viewModel::retry)
            return@Box
        }

        HomeContent(
            state = state,
            layout = layout,
            arranging = arranging,
            onNavigate = onNavigate,
            onSetArranging = viewModel::setEditing,
            onOrderChanged = viewModel::setOrder,
            viewModel = viewModel,
        )
    }
}

/**
 * Home, and the same Home while it is being rearranged.
 *
 * **There is no editor screen any more.** Arranging used to crossfade Home out for a list of
 * labelled rows, which meant choosing an order for blocks you could not see — the thing being
 * arranged was a name, not the card you actually recognise. Here the blocks are the real ones,
 * with their real content, and they move where they sit.
 *
 * **Nothing drags until the pencil says so.** A long press is also how the app scrolls, taps
 * and opens things, and a Home screen whose cards come loose under a resting thumb would be a
 * worse screen the other 99% of the time. The pencil arms it; then a long press lifts a card.
 */
@Composable
private fun HomeContent(
    state: HomeViewModel.UiState,
    layout: HomeLayout,
    arranging: Boolean,
    onNavigate: (String) -> Unit,
    onSetArranging: (Boolean) -> Unit,
    onOrderChanged: (List<HomeSection>) -> Unit,
    viewModel: HomeViewModel,
) {
    val saved = layout.visible
    val listState = rememberLazyListState()

    // **The order being dragged lives here, not in DataStore.** Each swap used to go out to
    // disk and come back through a Flow before the list saw it, so the neighbour stepped
    // aside a beat after the finger had already passed it — the lag *was* the bad feel. The
    // screen now reorders itself on the spot and writes once, when the card is set down.
    var working by remember { mutableStateOf(saved) }
    val reorder = rememberReorderState(listState) { from, to ->
        // ReorderState speaks in LazyColumn indices, and index 0 is the greeting, not a card.
        // Shift into `working`'s indices, and refuse any swap with the greeting itself —
        // otherwise every move lands one slot off and a card can be dropped above the header.
        val f = from - HeaderItems
        val t = to - HeaderItems
        if (f in working.indices && t in working.indices) {
            working = working.toMutableList().also { it.add(t, it.removeAt(f)) }
            true
        } else {
            false
        }
    }

    // Adopt an order that changed elsewhere — keyed on the *saved* order alone.
    //
    // It used to be keyed on the drag state as well, and that was the snap-back: the moment the
    // finger lifted, this ran with the saved order still being the old one (the write had only
    // just been sent), put the list back how it was, and then the write landed and put it
    // forward again. Keyed on `saved` only, releasing a card changes nothing here; when the
    // write comes back it matches `working` already and there is nothing to move.
    LaunchedEffect(saved) {
        if (!reorder.isDragging && reorder.settlingKey == null) working = saved
    }
    // One write per drag, on release.
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(reorder.isDragging) {
        if (wasDragging && !reorder.isDragging) onOrderChanged(working)
        wasDragging = reorder.isDragging
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = PokyhSpacing.screenH,
            end = PokyhSpacing.screenH,
            top = PokyhSpacing.lg,
            bottom = PokyhSpacing.xxxl,
        ),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
        // Dragging a card while the list is also flinging under it puts the two in a fight
        // neither wins; the drag owns the scroll for its duration.
        userScrollEnabled = !reorder.isDragging,
    ) {
        item(key = "greeting") {
            GreetingHeader(
                session = state.session,
                arranging = arranging,
                onMessages = { onNavigate(PokyhDestinations.MESSAGES) },
                onProfile = { onNavigate(PokyhDestinations.PROFILE) },
                onToggleArrange = { onSetArranging(!arranging) },
                modifier = Modifier.fadeIn(),
            )
        }

        itemsIndexed(working, key = { _, section -> section.name }) { index, section ->
            HomeSectionContent(
                section = section,
                state = state,
                onNavigate = onNavigate,
                viewModel = viewModel,
                modifier = Modifier
                    // Every card that is *not* in the hand slides to its new place instead
                    // of teleporting there. Without it the list re-lays out in one frame and
                    // the card you dragged past appears to blink to the other side.
                    .animateItem(
                        // …except the card in the hand. The reorder already pins it to the
                        // finger, and a placement animation on top pulls it the other way on
                        // every swap — that tug is the wobble.
                        placementSpec = if (reorder.activeKey == section.name) {
                            null
                        } else {
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = IntOffset.VisibilityThreshold,
                            )
                        },
                    )
                    // The stagger is by *position on screen*, not by section identity, so a
                    // reordered Home still settles top to bottom.
                    .fadeIn(40 + index * 40)
                    .then(
                        if (arranging) {
                            Modifier.reorderable(reorder, section.name)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}
/** Every Home block dispatches from here — see [HomeSection]. */
@Composable
private fun HomeSectionContent(
    section: HomeSection,
    state: HomeViewModel.UiState,
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    when (section) {
        HomeSection.Shortcuts -> ShortcutsGrid(
            onGrades = viewModel::selectGradesTab,
            onAbsences = { onNavigate(PokyhDestinations.ABSENCES) },
            onTodos = { onNavigate(PokyhDestinations.TODOS) },
            onReminders = { onNavigate(PokyhDestinations.REMINDERS) },
            modifier = modifier,
        )

        HomeSection.Exam -> {
            val exam = state.nextExam
            when {
                exam != null -> ExamCard(exam = exam, modifier = modifier)
                state.examsLoaded -> NoExamCard(modifier = modifier)
                else -> Unit
            }
        }

        HomeSection.Today -> TodaySection(
            loading = state.loadingToday,
            slots = state.todaySlots,
            savedAt = state.todaySavedAt,
            stale = state.todayStale,
            modifier = modifier,
        )

        HomeSection.Mensa -> MensaWeekSection(
            loading = state.loadingMensa,
            week = state.mensaWeek,
            weekLabel = state.mensaWeekLabel,
            savedAt = state.mensaSavedAt,
            stale = state.mensaStale,
            ratings = state.dishRatings,
            ratingsLoading = state.loadingDishRatings,
            onClick = viewModel::selectMensaTab,
            modifier = modifier,
        )

        // Always rendered, empty or not. It used to vanish when there was nothing in it,
        // which made a block disappear from the middle of Home for a reason the reader
        // could not see — and left a gap in an order they had arranged themselves.
        HomeSection.Grades -> GradesSection(
            loading = state.loadingGrades,
            grades = state.recentGrades,
            modifier = modifier,
        )
    }
}

// ── Greeting ────────────────────────────────────────────────────────────────

/**
 * Two [PokyhType.display] lines — the greeting in the text color, the name in the accent — so
 * the page opens on a heading with some personality rather than a generic bar. The class/school
 * line under it is the eyebrow's job, set small and quiet.
 */
@Composable
private fun GreetingHeader(
    session: UserSession?,
    arranging: Boolean,
    onMessages: () -> Unit,
    onProfile: () -> Unit,
    onToggleArrange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    val greeting = remember { currentGreeting() }
    val firstName = remember(session) { firstNameOf(session) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        ) {
            if (arranging) {
                // The one line of instruction the mode needs, in the space the class label
                // usually has. Without it the pencil arms something invisible.
                PokyhLabel("Karte gedrückt halten zum Verschieben", modifier = Modifier.weight(1f))
            } else if (session != null) {
                val klasse = session.klasseName.ifEmpty { "LBS Brixen" }
                PokyhLabel("$klasse · LBS Brixen", modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (arranging) {
                PokyhTextButton(text = "Fertig", onClick = onToggleArrange)
            } else {
                PokyhIconButton(
                    icon = PokyhIcons.edit,
                    contentDescription = "Startseite anordnen",
                    onClick = onToggleArrange,
                    tinted = true,
                )
                TabRootActions(
                    avatarContent = { CurrentUserAvatar() },
                    unreadMessages = rememberUnreadMessageCount(),
                    onMessages = onMessages,
                    onProfile = onProfile,
                )
            }
        }
        Spacer(Modifier.size(PokyhSpacing.md))
        Text("$greeting,", style = PokyhType.display, color = colors.textPrimary)
        Text(
            text = firstName.ifEmpty { "Willkommen" },
            style = PokyhType.display,
            color = Brand.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
private fun currentGreeting(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 5..10 -> "Guten Morgen"
        in 11..16 -> "Hallo"
        in 17..21 -> "Guten Abend"
        else -> "Hallo"
    }
}

private fun firstNameOf(session: UserSession?): String {
    val personName = session?.personName?.trim()
    if (!personName.isNullOrEmpty()) {
        val first = personName.split(" ").firstOrNull { it.isNotEmpty() }
        if (!first.isNullOrEmpty()) return first
    }
    return session?.username ?: ""
}

// ── Editor ──────────────────────────────────────────────────────────────────

/**
 * The Home editor: every block as one row, long-press to drag, tap the eye to show or hide.
 *
 * It shows **compact rows rather than the live blocks**, which is the one design decision here
 * worth defending. Dragging the real sections sounds more direct, but Home's blocks range from a
 * 60dp notice to a 300dp tile grid: you could not see more than two at once, and a drag would
 * mean scrolling half the screen per position. A row per block puts the whole layout on one
 * screen, which is what makes "move Mensa above Heute" a single gesture. Each row still says
 * what its block is, so nothing has to be remembered.
 *
 * Hidden rows stay in place, dimmed, instead of moving to a separate list — so switching one
 * back on is one tap and it reappears exactly where the row sits.
 */
// ── Shortcuts ───────────────────────────────────────────────────────────────

private data class ShortcutItem(
    val title: String,
    val tone: DecorativeTone,
    val glyph: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Four destinations, one per [PokyhDecorative] tone, in a fixed order — so a tile keeps its
 * color for good and the grid is learnable by color as well as by label.
 */
@Composable
private fun ShortcutsGrid(
    onGrades: () -> Unit,
    onAbsences: () -> Unit,
    onTodos: () -> Unit,
    onReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = PokyhTheme.colors.isDark
    val items = listOf(
        ShortcutItem("Noten", PokyhDecorative.periwinkle(isDark), PokyhIcons.decorSubjects, onGrades),
        ShortcutItem("Abwesenheiten", PokyhDecorative.butter(isDark), PokyhIcons.decorAbsences, onAbsences),
        ShortcutItem("Todos", PokyhDecorative.sage(isDark), PokyhIcons.decorTodos, onTodos),
        ShortcutItem("Erinnerungen", PokyhDecorative.blush(isDark), PokyhIcons.decorReminders, onReminders),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                rowItems.forEach { item ->
                    PokyhFeatureTile(
                        title = item.title,
                        tone = item.tone,
                        glyph = item.glyph,
                        onClick = item.onClick,
                        minHeight = 132.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ── Nächste Schularbeit ─────────────────────────────────────────────────────

@Composable
private fun ExamCard(exam: TimetableEntry, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier, padding = PokyhSpacing.row) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        ) {
            IconTile(icon = PokyhIcons.exam, color = Brand.warning)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs),
            ) {
                PokyhLabel("Nächste Schularbeit")
                val subject = exam.subjectLong.ifEmpty { exam.subjectName.ifEmpty { "Prüfung" } }
                Text(subject, style = PokyhType.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${examWhen(exam.date)} · ${Fmt.time(exam.startTime)}",
                    style = PokyhType.caption,
                    color = Brand.warning,
                )
            }
        }
    }
}

@Composable
private fun NoExamCard(modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier, padding = PokyhSpacing.row) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        ) {
            IconTile(icon = PokyhIcons.noExam, color = Brand.success)
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
                PokyhLabel("Nächste Schularbeit")
                Text("Keine Tests in Zukunft", style = PokyhType.headline, color = colors.textPrimary)
            }
        }
    }
}

private fun examWhen(dateNum: Int): String {
    val date = dateNum.toLocalDate()
    val today = todayLocalDate()
    return when {
        date == today -> "Heute"
        date == today.plus(1, DateTimeUnit.DAY) -> "Morgen"
        else -> "in ${today.daysUntil(date)} Tagen · ${Fmt.dateShort(dateNum)}"
    }
}

// ── Heute (Unterricht) ──────────────────────────────────────────────────────

@Composable
private fun TodaySection(
    loading: Boolean,
    slots: List<MergedSlot>,
    savedAt: Long,
    stale: Boolean,
    modifier: Modifier = Modifier,
) {
    PokyhSection(
        modifier = modifier,
        title = "Heute",
        trailing = { AsOfLabel(savedAt = savedAt, stale = stale) },
    ) {
        when {
            loading -> TileRowSkeleton(rows = 2)
            slots.isEmpty() -> PokyhInlineNotice(
                icon = PokyhIcons.noExam,
                text = "Heute kein Unterricht",
                tint = Brand.success,
            )
            else -> slots.forEachIndexed { idx, slot ->
                HomeSlotRow(slot = slot, modifier = Modifier.fadeIn(idx * 30))
            }
        }
    }
}

/**
 * A lesson as its own row-surface rather than a grouped-list row: lessons are distinct events
 * with their own accent bar, and each one being its own object is what makes the accent read.
 * The Timetable day view uses the same shape and the same [subjectTone] bar, so the two screens
 * agree.
 */
@Composable
private fun HomeSlotRow(slot: MergedSlot, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val isDark = colors.isDark
    val display = slot.display
    val tone = when (slot.kind) {
        SlotKind.CANCELLED -> statusTone(Brand.danger, isDark)
        SlotKind.EXAM -> statusTone(Brand.warning, isDark)
        SlotKind.REPLACEMENT -> statusTone(Brand.orange, isDark)
        SlotKind.EVENT -> statusTone(Brand.accentSoft, isDark)
        SlotKind.NORMAL -> subjectTone(display.subjectName, isDark)
    }
    PokyhTileRow(modifier = modifier) {
        Box(Modifier.width(4.dp).height(40.dp).background(tone.bar, PokyhShapes.xs))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            val subject = display.subjectName.ifEmpty { display.subjectLong.ifEmpty { "Veranstaltung" } }
            Text(subject, style = PokyhType.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val teacherRoom = listOf(display.teacherName, display.roomName)
                .filter { it.isNotEmpty() }
                .joinToString(" · ")
            if (teacherRoom.isNotEmpty()) {
                Text(teacherRoom, style = PokyhType.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            text = "${Fmt.time(display.startTime)}\n${Fmt.time(display.endTime)}",
            style = PokyhType.caption2.monospacedDigits(),
            color = colors.textTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

// ── Mensa-Woche ─────────────────────────────────────────────────────────────

/** A day column is wide enough for a dish photo next to a two-line dish name, and narrow enough
 * that the next day always peeks in — which is what says "this scrolls". */
private val MensaDayWidth = 212.dp

/**
 * The week's menu as a horizontal strip of day cards: Monday to Friday, each with its dishes'
 * photo, name and rating.
 *
 * Sideways rather than stacked, because five days of three dishes is far more than the home
 * screen can spend vertically — and a week reads across anyway. Every card spells out its
 * weekday *and* date, so no column is ambiguous once the strip has been scrolled.
 *
 * **All five cards are the same height**, set by [IntrinsicSize.Min] on the strip. Today's card
 * used to carry an extra line (the weekday, under the word "Heute") which made it visibly taller
 * than its neighbours and left the row looking like it had been knocked askew. "Heute" and the
 * weekday now share one line, and the strip measures itself to its tallest card so a day with
 * one dish still lines up with a day that has three.
 *
 * **The strip opens on today**, not on Monday. Monday's menu is the answer to "what's for lunch"
 * on exactly one day of the week; every other day it was a card you had to scroll past, and by
 * Thursday today's card started off screen entirely. Scrolling rather than reordering keeps the
 * week in week order — the days already gone are still there, one swipe back — and it costs
 * nothing on Friday, where the scroll simply runs out at the end of the strip and Friday lands
 * flush to the right edge.
 */
@Composable
private fun MensaWeekSection(
    loading: Boolean,
    week: List<HomeViewModel.MensaWeekDay>,
    weekLabel: String,
    savedAt: Long,
    stale: Boolean,
    ratings: Map<String, DishRatingsData>,
    ratingsLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhSection(
        modifier = modifier,
        title = "Mensa",
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsOfLabel(savedAt = savedAt, stale = stale)
                if (weekLabel.isNotEmpty()) PokyhLabel(weekLabel)
            }
        },
    ) {
        when {
            loading && week.isEmpty() -> Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap),
            ) {
                repeat(2) { MensaDayCardSkeleton(width = MensaDayWidth) }
            }

            week.none { it.dishes.isNotEmpty() } ->
                PokyhInlineNotice(icon = PokyhIcons.mensa, text = "Kein Speiseplan verfügbar")

            else -> {
                val scrollState = rememberScrollState()
                val todayIndex = week.indexOfFirst { it.isToday }
                val density = LocalDensity.current
                val stride = with(density) { (MensaDayWidth + PokyhSpacing.rowGap).roundToPx() }
                // `scrollTo` clamps to the strip's own maximum, which is what makes Friday work:
                // there is nothing after it to scroll to, so the card ends up flush right instead
                // of the strip trying to put it at the leading edge over empty space.
                LaunchedEffect(todayIndex, stride, scrollState.maxValue) {
                    if (todayIndex > 0) scrollState.scrollTo(todayIndex * stride)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap),
                ) {
                    week.forEach { day ->
                        MensaDayCard(
                            day = day,
                            ratings = ratings,
                            ratingsLoading = ratingsLoading,
                            onClick = onClick,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MensaDayCard(
    day: HomeViewModel.MensaWeekDay,
    ratings: Map<String, DishRatingsData>,
    ratingsLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    // A day already past stays in the strip (the week is the point) but steps back in tone.
    val dayColor = when {
        day.isToday -> colors.accentText
        day.isPast -> colors.textTertiary
        else -> colors.textPrimary
    }
    PokyhCard(
        modifier = modifier.width(MensaDayWidth),
        padding = PokyhSpacing.md,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
        ) {
            Text(
                // One line for every card, today included — see the section doc.
                text = if (day.isToday) "Heute · ${day.weekday.take(2)}" else day.weekday,
                style = PokyhType.subheadline.semibold(),
                color = dayColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = day.dateLabel,
                style = PokyhType.caption2.monospacedDigits(),
                color = if (day.isToday) colors.accentText else colors.textTertiary,
                maxLines = 1,
            )
        }

        // Today gets a hairline accent rule instead of an extra line of text: it marks the card
        // without changing its height.
        Spacer(Modifier.size(PokyhSpacing.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (day.isToday) Brand.accent else colors.separator,
                    PokyhShapes.pill,
                ),
        )
        Spacer(Modifier.size(PokyhSpacing.md))

        if (day.dishes.isEmpty()) {
            Text(
                text = "Kein Menü",
                style = PokyhType.footnote,
                color = colors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedSurface(PokyhShapes.md)
                    .padding(horizontal = PokyhSpacing.md, vertical = PokyhSpacing.lg),
            )
        } else {
            day.dishes.take(MaxDishesPerDay).forEachIndexed { index, dish ->
                if (index > 0) Spacer(Modifier.size(PokyhSpacing.md))
                MensaDayDish(dish = dish, rating = ratings[dish.id], ratingsPending = ratingsLoading)
            }
            val extra = day.dishes.size - MaxDishesPerDay
            if (extra > 0) {
                Spacer(Modifier.size(PokyhSpacing.sm))
                Text(
                    text = "+$extra weitere",
                    style = PokyhType.caption,
                    color = colors.accentText,
                )
            }
        }
    }
}

/** Three dishes is what fits a card without the strip becoming a wall; the rest are counted. */
private const val MaxDishesPerDay = 3

@Composable
private fun MensaDayDish(dish: Dish, rating: DishRatingsData?, ratingsPending: Boolean) {
    val colors = PokyhTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(PokyhShapes.sm)
                .insetSurface(PokyhShapes.sm),
        ) {
            if (!dish.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = dish.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    PokyhIcons.dish,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.align(Alignment.Center).size(20.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            Text(
                text = dish.name,
                style = PokyhType.footnote,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Reserved whether or not this dish has a rating yet, so the strip doesn't re-flow
            // when the ratings land a moment after the menu.
            MiniStarsSlot(
                loading = rating == null && ratingsPending,
                average = rating?.average ?: 0.0,
                count = rating?.count ?: 0,
                height = 14.dp,
            )
        }
    }
}

// ── Zuletzt eingetragene Noten ──────────────────────────────────────────────

@Composable
private fun GradesSection(
    loading: Boolean,
    grades: List<HomeViewModel.RecentGrade>,
    modifier: Modifier = Modifier,
) {
    PokyhSection(modifier = modifier, title = "Zuletzt eingetragen") {
        when {
            loading -> TileRowSkeleton(rows = 2)
            // A block that renders nothing is a hole in an order the user arranged
            // themselves — and at the start of a school year, which is exactly when there
            // are no grades, it would be a hole for weeks. The placeholder keeps the shape
            // of the screen and says why it is empty.
            grades.isEmpty() -> PokyhInlineNotice(
                icon = PokyhIcons.tabGrades,
                text = "Noch keine Noten eingetragen",
            )
            else -> PokyhListCard(items = grades) { grade -> GradeRow(grade) }
        }
    }
}

@Composable
private fun GradeRow(grade: HomeViewModel.RecentGrade) {
    PokyhRow(
        title = grade.subject,
        subtitle = Fmt.dateFull(grade.date),
        showChevron = false,
        leading = { GradeBubble(grade.value) },
    )
}

/** The grade number in a tinted disc — the app's one way of showing a single grade, shared by
 * Home, Grades and the subject detail's calculator. */
@Composable
internal fun GradeBubble(
    value: Double,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val color = if (muted) PokyhTheme.colors.textTertiary else gradeColor(value)
    val digits = if (value == round(value)) 0 else 1
    Box(
        modifier = modifier
            .size(44.dp)
            .background(color.copy(alpha = 0.14f), PokyhShapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Fmt.num(value, digits = digits),
            style = PokyhType.statSmall,
            color = color,
        )
    }
}

/** Items in Home's LazyColumn above the first card — the greeting. See [HomeContent]. */
private const val HeaderItems = 1
