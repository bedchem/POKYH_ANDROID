package dev.plattnericus.pokyh.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
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
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.IconTile
import dev.plattnericus.pokyh.ui.components.MiniStars
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhFeatureTile
import dev.plattnericus.pokyh.ui.components.PokyhInlineNotice
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTileRow
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TileRowSkeleton
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
import dev.plattnericus.pokyh.ui.theme.softenedFill
import dev.plattnericus.pokyh.ui.theme.subjectColor
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
 * Structure: greeting, the four shortcut tiles, then one [PokyhSection] per data source in the
 * order the day happens — next exam, today's lessons, today's menu, newest grades.
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().appBackground()) {
        if (state.error != null) {
            ErrorStateView(message = state.error!!, onRetry = viewModel::retry)
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(top = PokyhSpacing.lg, bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
        ) {
            GreetingHeader(
                session = state.session,
                onMessages = { onNavigate(PokyhDestinations.MESSAGES) },
                onProfile = { onNavigate(PokyhDestinations.PROFILE) },
                modifier = Modifier.fadeIn(),
            )

            ShortcutsGrid(
                onGrades = viewModel::selectGradesTab,
                onAbsences = { onNavigate(PokyhDestinations.ABSENCES) },
                onTodos = { onNavigate(PokyhDestinations.TODOS) },
                onReminders = { onNavigate(PokyhDestinations.REMINDERS) },
                modifier = Modifier.fadeIn(40),
            )

            val exam = state.nextExam
            if (exam != null) {
                ExamCard(exam = exam, modifier = Modifier.fadeIn(60))
            } else if (state.examsLoaded) {
                NoExamCard(modifier = Modifier.fadeIn(60))
            }

            TodaySection(
                loading = state.loadingToday,
                slots = state.todaySlots,
                modifier = Modifier.fadeIn(80),
            )

            MensaWeekSection(
                loading = state.loadingMensa,
                week = state.mensaWeek,
                weekLabel = state.mensaWeekLabel,
                ratings = state.dishRatings,
                onClick = viewModel::selectMensaTab,
                modifier = Modifier.fadeIn(120),
            )

            if (state.loadingGrades || state.recentGrades.isNotEmpty()) {
                GradesSection(
                    loading = state.loadingGrades,
                    grades = state.recentGrades,
                    modifier = Modifier.fadeIn(160),
                )
            }
        }
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
    onMessages: () -> Unit,
    onProfile: () -> Unit,
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
            if (session != null) {
                val klasse = session.klasseName.ifEmpty { "LBS Brixen" }
                PokyhLabel("$klasse · LBS Brixen", modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            TabRootActions(
                avatarContent = { CurrentUserAvatar() },
                        unreadMessages = rememberUnreadMessageCount(),
                onMessages = onMessages,
                onProfile = onProfile,
            )
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
private fun TodaySection(loading: Boolean, slots: List<MergedSlot>, modifier: Modifier = Modifier) {
    PokyhSection(modifier = modifier, title = "Heute") {
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
 * The Timetable day view uses the same shape, so the two screens agree.
 */
@Composable
private fun HomeSlotRow(slot: MergedSlot, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val display = slot.display
    val accent = when (slot.kind) {
        SlotKind.CANCELLED -> colors.textTertiary
        SlotKind.EXAM -> Brand.warning
        SlotKind.REPLACEMENT -> Brand.orange
        SlotKind.EVENT -> Brand.accentSoft
        SlotKind.NORMAL -> subjectColor(display.subjectName)
    }
    PokyhTileRow(modifier = modifier) {
        Box(Modifier.width(4.dp).height(40.dp).background(accent.softenedFill(), PokyhShapes.xs))
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
 */
@Composable
private fun MensaWeekSection(
    loading: Boolean,
    week: List<HomeViewModel.MensaWeekDay>,
    weekLabel: String,
    ratings: Map<String, DishRatingsData>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhSection(
        modifier = modifier,
        title = "Mensa",
        trailing = if (weekLabel.isEmpty()) null else {
            { PokyhLabel(weekLabel) }
        },
    ) {
        when {
            loading && week.isEmpty() -> TileRowSkeleton(rows = 2)
            week.none { it.dishes.isNotEmpty() } ->
                PokyhInlineNotice(icon = PokyhIcons.mensa, text = "Kein Speiseplan verfügbar")
            else -> Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap),
            ) {
                week.forEach { day ->
                    MensaDayCard(day = day, ratings = ratings, onClick = onClick)
                }
            }
        }
    }
}

@Composable
private fun MensaDayCard(
    day: HomeViewModel.MensaWeekDay,
    ratings: Map<String, DishRatingsData>,
    onClick: () -> Unit,
) {
    val colors = PokyhTheme.colors
    // A day already past stays in the strip (the week is the point) but steps back in tone.
    val dayColor = when {
        day.isToday -> colors.accentText
        day.isPast -> colors.textTertiary
        else -> colors.textPrimary
    }
    PokyhCard(
        modifier = Modifier.width(MensaDayWidth),
        padding = PokyhSpacing.md,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
        ) {
            Text(
                text = if (day.isToday) "Heute" else day.weekday,
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
            )
        }
        if (day.isToday) {
            Text(
                text = day.weekday,
                style = PokyhType.caption2,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = PokyhSpacing.xxs),
            )
        }

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
            day.dishes.take(3).forEachIndexed { index, dish ->
                if (index > 0) Spacer(Modifier.size(PokyhSpacing.md))
                MensaDayDish(dish = dish, rating = ratings[dish.id])
            }
        }
    }
}

@Composable
private fun MensaDayDish(dish: Dish, rating: DishRatingsData?) {
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dish.name,
                style = PokyhType.footnote,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (rating != null && rating.count > 0) {
                Spacer(Modifier.size(PokyhSpacing.xxs))
                MiniStars(average = rating.average, count = rating.count)
            }
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
        if (loading) {
            TileRowSkeleton(rows = 2)
        } else {
            PokyhListCard(items = grades) { grade -> GradeRow(grade) }
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

