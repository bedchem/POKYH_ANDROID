package dev.plattnericus.pokyh.ui.home
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.IconTile
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.PokyhType.medium
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.gradeColor
import dev.plattnericus.pokyh.ui.theme.pressable
import dev.plattnericus.pokyh.ui.theme.shimmer
import dev.plattnericus.pokyh.ui.theme.smoothCorner
import dev.plattnericus.pokyh.ui.theme.subjectColor
import kotlin.math.round
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** HomeView.swift, ported. */
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HeaderSection(session = state.session, modifier = Modifier.fadeIn())

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

            MensaSection(
                loading = state.loadingMensa,
                dishes = state.todayDishes,
                dayLabel = state.dishDayLabel,
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

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection(session: UserSession?, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val greeting = remember { currentGreeting() }
    val firstName = remember(session) { firstNameOf(session) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$greeting,", style = PokyhType.largeTitle, color = colors.textPrimary)
        Text(firstName.ifEmpty { "Willkommen" }, style = PokyhType.largeTitle, color = Brand.accent)
        if (session != null) {
            val klasse = session.klasseName.ifEmpty { "LBS Brixen" }
            Text("$klasse · LBS Brixen", style = PokyhType.subheadline, color = colors.textSecondary)
        }
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

// ── Shortcuts ────────────────────────────────────────────────────────────────

private data class ShortcutItem(val title: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

@Composable
private fun ShortcutsGrid(
    onGrades: () -> Unit,
    onAbsences: () -> Unit,
    onTodos: () -> Unit,
    onReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        ShortcutItem("Noten", PokyhIcons.chart_bar_fill, Brand.accent, onGrades),
        ShortcutItem("Abwesenheiten", PokyhIcons.person_fill_xmark, Brand.orange, onAbsences),
        ShortcutItem("Todos", PokyhIcons.checklist, Brand.accentSoft, onTodos),
        ShortcutItem("Erinnerungen", PokyhIcons.bell_fill, Brand.tint, onReminders),
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { item ->
                    val interactionSource = remember { MutableInteractionSource() }
                    ShortcutCard(
                        item = item,
                        interactionSource = interactionSource,
                        modifier = Modifier.weight(1f).pressable(interactionSource),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(item: ShortcutItem, interactionSource: MutableInteractionSource, modifier: Modifier = Modifier) {
    PokyhCard(
        modifier = modifier.clickable(interactionSource = interactionSource, indication = null, onClick = item.onClick),
        padding = 14.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            IconTile(icon = item.icon, color = item.color, size = 40.dp, cornerRadius = 11.dp)
            Text(
                text = item.title,
                style = PokyhType.subheadline.semibold(),
                color = PokyhTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Nächste Schularbeit ────────────────────────────────────────────────────

@Composable
private fun ExamCard(exam: TimetableEntry, modifier: Modifier = Modifier) {
    PokyhCard(modifier = modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = PokyhIcons.pencil_and_list_clipboard, color = Brand.warning, size = 40.dp, cornerRadius = 11.dp)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Nächste Schularbeit", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
                val subject = exam.subjectLong.ifEmpty { exam.subjectName.ifEmpty { "Prüfung" } }
                Text(subject, style = PokyhType.subheadline.semibold(), color = PokyhTheme.colors.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(PokyhIcons.calendar, contentDescription = null, tint = Brand.warning, modifier = Modifier.size(12.dp))
                    Text(
                        "${examWhen(exam.date)} · ${Fmt.time(exam.startTime)}",
                        style = PokyhType.caption2,
                        color = Brand.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoExamCard(modifier: Modifier = Modifier) {
    PokyhCard(modifier = modifier, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = PokyhIcons.checkmark_seal_fill, color = Brand.tint, size = 40.dp, cornerRadius = 11.dp)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Nächste Schularbeit", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
                Text("Keine Tests in Zukunft", style = PokyhType.subheadline.semibold(), color = PokyhTheme.colors.textPrimary)
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

// ── Bausteine ────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = PokyhTheme.colors.textPrimary, modifier = Modifier.size(20.dp))
        Text(title, style = PokyhType.title3, color = PokyhTheme.colors.textPrimary)
    }
}

@Composable
private fun InfoCard(icon: ImageVector, color: Color, text: String) {
    PokyhCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = color)
            Text(text, style = PokyhType.body, color = PokyhTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun SkeletonRows(n: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(n) {
            Row(
                modifier = Modifier.fillMaxWidth().cardSurface(radius = 12.dp).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .width(5.dp)
                        .height(42.dp)
                        .background(PokyhTheme.colors.cardAlt, smoothCorner(3.dp))
                        .shimmer(),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(height = 13.dp, width = 140.dp)
                    SkeletonBlock(height = 10.dp, width = 90.dp)
                }
            }
        }
    }
}

// ── Heute (Unterricht) ──────────────────────────────────────────────────────

@Composable
private fun TodaySection(loading: Boolean, slots: List<MergedSlot>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Heute", PokyhIcons.calendar)
        when {
            loading -> SkeletonRows(2)
            slots.isEmpty() -> InfoCard(PokyhIcons.checkmark_circle_fill, Brand.tint, "Heute kein Unterricht")
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEachIndexed { idx, slot ->
                    HomeSlotRow(slot = slot, modifier = Modifier.fadeIn(idx * 30))
                }
            }
        }
    }
}

/** Lightweight local slot row (bar + subject + teacher/room + time) — a shared `SlotRow` is
 * introduced by the Timetable screen; Home inlines an equivalent instead of depending on it. */
@Composable
private fun HomeSlotRow(slot: MergedSlot, modifier: Modifier = Modifier) {
    val display = slot.display
    val color = when (slot.kind) {
        SlotKind.CANCELLED -> PokyhTheme.colors.textTertiary
        SlotKind.EXAM -> Brand.warning
        SlotKind.REPLACEMENT -> Brand.orange
        SlotKind.EVENT -> Brand.accentSoft
        SlotKind.NORMAL -> subjectColor(display.subjectName)
    }
    PokyhCard(modifier = modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(5.dp).height(44.dp).background(color, smoothCorner(3.dp)))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val subject = display.subjectName.ifEmpty { display.subjectLong.ifEmpty { "Veranstaltung" } }
                Text(subject, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val teacherRoom = listOf(display.teacherName, display.roomName).filter { it.isNotEmpty() }.joinToString(" · ")
                if (teacherRoom.isNotEmpty()) {
                    Text(teacherRoom, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(
                "${Fmt.time(display.startTime)}–${Fmt.time(display.endTime)}",
                style = PokyhType.caption2,
                color = PokyhTheme.colors.textTertiary,
            )
        }
    }
}

// ── Mensa heute ─────────────────────────────────────────────────────────────

@Composable
private fun MensaSection(
    loading: Boolean,
    dishes: List<Dish>,
    dayLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(if (dayLabel.isEmpty()) "Mensa heute" else "Mensa · $dayLabel", PokyhIcons.fork_knife)
        when {
            loading && dishes.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { MensaSkeletonRow() }
            }
            dishes.isEmpty() -> InfoCard(PokyhIcons.fork_knife, Brand.accent, "Kein Speiseplan verfügbar")
            else -> {
                val interactionSource = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                        .pressable(interactionSource),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dishes.take(3).forEach { dish -> MensaDishRow(dish) }
                }
            }
        }
    }
}

@Composable
private fun MensaDishRow(dish: Dish) {
    PokyhCard(padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(smoothCorner(10.dp))
                    .background(PokyhTheme.colors.cardAlt),
            ) {
                if (!dish.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = dish.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (dish.category.isNotEmpty()) {
                    Text(dish.category.uppercase(), style = PokyhType.caption2.bold(), color = Brand.accent)
                }
                Text(
                    dish.name,
                    style = PokyhType.subheadline.medium(),
                    color = PokyhTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MensaSkeletonRow() {
    Row(
        modifier = Modifier.fillMaxWidth().cardSurface(radius = 12.dp).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(56.dp).background(PokyhTheme.colors.cardAlt, smoothCorner(10.dp)).shimmer())
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(height = 11.dp, width = 80.dp)
            SkeletonBlock(height = 13.dp, width = 160.dp)
        }
    }
}

// ── Zuletzt eingetragene Noten ────────────────────────────────────────────────

@Composable
private fun GradesSection(loading: Boolean, grades: List<HomeViewModel.RecentGrade>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("Zuletzt eingetragen", PokyhIcons.chart_bar_fill)
        if (loading) {
            SkeletonRows(2)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grades.forEach { g -> GradeRow(g) }
            }
        }
    }
}

@Composable
private fun GradeRow(g: HomeViewModel.RecentGrade) {
    val color = gradeColor(g.value)
    PokyhCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(42.dp).background(color.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val digits = if (g.value == round(g.value)) 0 else 1
                Text(Fmt.num(g.value, digits = digits), style = PokyhType.headline.monospacedDigits(), color = color)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(g.subject, style = PokyhType.subheadline.medium(), color = PokyhTheme.colors.textPrimary)
                Text(Fmt.dateFull(g.date), style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
            }
        }
    }
}
