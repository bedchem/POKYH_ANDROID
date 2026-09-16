package dev.plattnericus.pokyh.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.data.model.ApiTodo
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.ui.components.IconTile
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhInlineNotice
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.StatusLabel
import dev.plattnericus.pokyh.ui.components.TileRowSkeleton
import dev.plattnericus.pokyh.ui.reminders.dueText
import dev.plattnericus.pokyh.ui.reminders.parseRemindAt
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.gradeColor
import java.util.Calendar
import kotlinx.coroutines.delay

// The optional Home widgets — see [HomeSection]. Each one says "offline, not stored" rather than
// "nothing there" when its data could not be loaded, like every other block on Home.

/**
 * The school's clock, not the phone's.
 *
 * WebUntis times are local to the school (LBS Brixen), so "how long until the bell" has to be
 * counted in that zone. Using the device zone showed a phone set to UTC (like the emulator) two
 * hours behind — "noch 13 Min" for a lesson that had ended long ago.
 */
private val SchoolZone: java.util.TimeZone = java.util.TimeZone.getTimeZone("Europe/Rome")

private fun schoolCalendar(): Calendar = Calendar.getInstance(SchoolZone)

private const val OFFLINE_WIDGET_TEXT = "Offline – noch nicht auf diesem Gerät gespeichert"

/** Minutes since midnight, re-read every 20 seconds so countdowns move on their own. */
@Composable
private fun rememberMinuteOfDay(): Int {
    fun now(): Int = schoolCalendar().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    var minute by remember { mutableIntStateOf(now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            minute = now()
        }
    }
    return minute
}

private fun durationText(minutes: Int): String = when {
    minutes < 60 -> "$minutes Min"
    minutes % 60 == 0 -> "${minutes / 60} Std"
    else -> "${minutes / 60} Std ${minutes % 60} Min"
}

private fun TimetableEntry.subjectLabel(): String =
    subjectLong.ifEmpty { subjectName.ifEmpty { note ?: "Stunde" } }

// ── Jetzt & gleich ──────────────────────────────────────────────────────────

@Composable
internal fun NowNextWidget(
    loading: Boolean,
    entries: List<TimetableEntry>,
    unavailable: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minute = rememberMinuteOfDay()
    // Parallel lessons (same start) count once; cancelled ones are not "now".
    val lessons = remember(entries) {
        entries.filter { !it.isCancelled }.distinctBy { it.startTime }.sortedBy { it.startTime }
    }
    val current = lessons.firstOrNull { Fmt.minutes(it.startTime) <= minute && minute < Fmt.minutes(it.endTime) }
    val next = lessons.firstOrNull { Fmt.minutes(it.startTime) > minute }

    PokyhSection(modifier = modifier, title = HomeSection.NowNext.title) {
        when {
            loading -> TileRowSkeleton(rows = 1)
            unavailable && lessons.isEmpty() ->
                PokyhInlineNotice(icon = PokyhIcons.offline, text = OFFLINE_WIDGET_TEXT, tint = Brand.warning)
            lessons.isEmpty() ->
                PokyhInlineNotice(icon = PokyhIcons.noExam, text = "Heute kein Unterricht", tint = Brand.success)
            current == null && next == null ->
                PokyhInlineNotice(icon = PokyhIcons.noExam, text = "Unterricht für heute vorbei", tint = Brand.success)
            else -> PokyhCard(onClick = onOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                    if (current != null) {
                        val start = Fmt.minutes(current.startTime)
                        val end = Fmt.minutes(current.endTime)
                        val progress = ((minute - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
                        LessonLine(
                            label = "Jetzt",
                            entry = current,
                            trailing = "noch ${durationText(end - minute)}",
                            accent = Brand.accent,
                        )
                        // A thin bar is enough to read "how far through" at a glance.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(PokyhTheme.colors.cardAlt, PokyhShapes.pill),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .background(Brand.accent, PokyhShapes.pill),
                            )
                        }
                    }
                    if (current != null && next != null) PokyhRowSeparator(startInset = 0.dp)
                    if (next != null) {
                        val gap = Fmt.minutes(next.startTime) - minute
                        LessonLine(
                            label = if (current == null) "Als Nächstes" else "Danach",
                            entry = next,
                            trailing = "in ${durationText(gap)} · ${Fmt.time(next.startTime)}",
                            accent = Brand.orange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonLine(label: String, entry: TimetableEntry, trailing: String, accent: androidx.compose.ui.graphics.Color) {
    val colors = PokyhTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
        Box(Modifier.width(4.dp).height(40.dp).background(accent, PokyhShapes.xs))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            PokyhLabel(label)
            Text(entry.subjectLabel(), style = PokyhType.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOf(entry.roomName, entry.teacherName).filter { it.isNotEmpty() }.joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = PokyhType.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(trailing, style = PokyhType.caption.monospacedDigits(), color = accent)
    }
}

// ── Vertretungen & Entfälle ─────────────────────────────────────────────────

@Composable
internal fun ChangesWidget(
    loaded: Boolean,
    unknown: Boolean,
    changes: List<TimetableEntry>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { todayNum() }
    PokyhSection(modifier = modifier, title = HomeSection.Changes.title) {
        when {
            !loaded -> TileRowSkeleton(rows = 2)
            unknown && changes.isEmpty() ->
                PokyhInlineNotice(icon = PokyhIcons.offline, text = OFFLINE_WIDGET_TEXT, tint = Brand.warning)
            changes.isEmpty() ->
                PokyhInlineNotice(icon = PokyhIcons.noExam, text = "Keine Änderungen für heute und morgen", tint = Brand.success)
            else -> PokyhCard(onClick = onOpen, padding = 0.dp) {
                changes.take(5).forEachIndexed { index, entry ->
                    if (index > 0) PokyhRowSeparator()
                    ChangeRow(entry, isToday = entry.date == today)
                }
                if (changes.size > 5) {
                    PokyhRowSeparator()
                    Text(
                        text = "+${changes.size - 5} weitere im Stundenplan",
                        style = PokyhType.footnote,
                        color = PokyhTheme.colors.accentText,
                        modifier = Modifier.padding(PokyhSpacing.card),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangeRow(entry: TimetableEntry, isToday: Boolean) {
    val colors = PokyhTheme.colors
    val (kind, color) = when {
        entry.isCancelled -> "Entfall" to Brand.danger
        entry.isSubstitution || entry.addedTeachers.isNotEmpty() -> "Vertretung" to Brand.orange
        entry.addedRooms.isNotEmpty() -> "Raumänderung" to Brand.warning
        entry.isAdditional -> "Zusätzlich" to Brand.accent
        else -> "Geändert" to Brand.warning
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        Box(Modifier.width(4.dp).height(36.dp).background(color, PokyhShapes.xs))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            Text(entry.subjectLabel(), style = PokyhType.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when {
                entry.isCancelled -> listOf(entry.teacherName, entry.roomName)
                entry.addedRooms.isNotEmpty() && !entry.isSubstitution -> listOf("Raum ${entry.addedRooms.joinToString(", ")}")
                else -> listOf(entry.teacherName, entry.roomName)
            }.filter { it.isNotEmpty() }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = PokyhType.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            Text(kind, style = PokyhType.caption, color = color)
            Text(
                text = "${if (isToday) "Heute" else "Morgen"} · ${Fmt.time(entry.startTime)}",
                style = PokyhType.caption2.monospacedDigits(),
                color = colors.textTertiary,
            )
        }
    }
}

private fun todayNum(): Int = schoolCalendar().let {
    it.get(Calendar.YEAR) * 10000 + (it.get(Calendar.MONTH) + 1) * 100 + it.get(Calendar.DAY_OF_MONTH)
}

// ── Notenschnitt ────────────────────────────────────────────────────────────

@Composable
internal fun AverageWidget(
    loaded: Boolean,
    average: Double?,
    count: Int,
    weakest: Pair<String, Double>?,
    unavailable: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    PokyhSection(modifier = modifier, title = HomeSection.Average.title) {
        when {
            !loaded -> TileRowSkeleton(rows = 1)
            average == null && unavailable ->
                PokyhInlineNotice(icon = PokyhIcons.offline, text = OFFLINE_WIDGET_TEXT, tint = Brand.warning)
            average == null ->
                PokyhInlineNotice(icon = PokyhIcons.tabGrades, text = "Noch keine Noten in diesem Schuljahr")
            else -> PokyhCard(onClick = onOpen) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.lg)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
                        PokyhLabel("Gesamtschnitt")
                        Text(Fmt.num(average), style = PokyhType.statLarge.monospacedDigits(), color = gradeColor(average))
                        Text("aus $count Noten", style = PokyhType.footnote, color = colors.textSecondary)
                    }
                    if (weakest != null) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
                            PokyhLabel("Schwächstes Fach")
                            Text(
                                weakest.first,
                                style = PokyhType.headline,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            StatusLabel(
                                text = Fmt.num(weakest.second),
                                color = gradeColor(weakest.second),
                                icon = PokyhIcons.gradeTrend,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Offene Todos ────────────────────────────────────────────────────────────

@Composable
internal fun OpenTodosWidget(
    loaded: Boolean,
    todos: List<ApiTodo>,
    pending: Int,
    unavailable: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhSection(
        modifier = modifier,
        title = HomeSection.OpenTodos.title,
        trailing = { PokyhTextButton(text = "Alle", onClick = onOpen) },
    ) {
        when {
            !loaded -> TileRowSkeleton(rows = 2)
            todos.isEmpty() && pending == 0 && unavailable ->
                PokyhInlineNotice(icon = PokyhIcons.offline, text = OFFLINE_WIDGET_TEXT, tint = Brand.warning)
            todos.isEmpty() && pending == 0 ->
                PokyhInlineNotice(icon = PokyhIcons.noExam, text = "Alles erledigt", tint = Brand.success)
            else -> PokyhCard(onClick = onOpen, padding = 0.dp) {
                todos.take(3).forEachIndexed { index, todo ->
                    if (index > 0) PokyhRowSeparator()
                    CompactItemRow(
                        glyph = PokyhIcons.radioOff,
                        tint = PokyhTheme.colors.textTertiary,
                        title = todo.title,
                        trailing = todo.dueAt?.let { due -> parseRemindAt(due)?.let { dueText(it) } },
                    )
                }
                val more = todos.size - 3
                if (more > 0 || pending > 0) {
                    if (todos.isNotEmpty()) PokyhRowSeparator()
                    FooterLine(more = more, pending = pending)
                }
            }
        }
    }
}

// ── Anstehende Erinnerungen ─────────────────────────────────────────────────

@Composable
internal fun UpcomingRemindersWidget(
    loaded: Boolean,
    reminders: List<ApiReminder>,
    pending: Int,
    unavailable: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhSection(
        modifier = modifier,
        title = HomeSection.UpcomingReminders.title,
        trailing = { PokyhTextButton(text = "Alle", onClick = onOpen) },
    ) {
        when {
            !loaded -> TileRowSkeleton(rows = 2)
            reminders.isEmpty() && pending == 0 && unavailable ->
                PokyhInlineNotice(icon = PokyhIcons.offline, text = OFFLINE_WIDGET_TEXT, tint = Brand.warning)
            reminders.isEmpty() && pending == 0 ->
                PokyhInlineNotice(icon = PokyhIcons.reminders, text = "Keine anstehenden Erinnerungen")
            else -> PokyhCard(onClick = onOpen, padding = 0.dp) {
                reminders.take(3).forEachIndexed { index, reminder ->
                    if (index > 0) PokyhRowSeparator()
                    CompactItemRow(
                        glyph = PokyhIcons.reminders,
                        tint = Brand.orange,
                        title = reminder.title,
                        trailing = parseRemindAt(reminder.remindAt)?.let { dueText(it) },
                    )
                }
                val more = reminders.size - 3
                if (more > 0 || pending > 0) {
                    if (reminders.isNotEmpty()) PokyhRowSeparator()
                    FooterLine(more = more, pending = pending)
                }
            }
        }
    }
}

@Composable
private fun CompactItemRow(
    glyph: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    trailing: String?,
) {
    val colors = PokyhTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        IconTile(icon = glyph, color = tint, size = 30.dp)
        Text(title, style = PokyhType.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = PokyhType.caption, color = Brand.orange)
        }
    }
}

@Composable
private fun FooterLine(more: Int, pending: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (more > 0) {
            Text("+$more weitere", style = PokyhType.footnote, color = PokyhTheme.colors.accentText, modifier = Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f))
        }
        if (pending > 0) {
            StatusLabel(
                text = if (pending == 1) "1 wartet auf Internet" else "$pending warten auf Internet",
                color = Brand.warning,
                icon = PokyhIcons.pendingSync,
            )
        }
    }
}