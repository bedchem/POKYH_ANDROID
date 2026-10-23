package dev.plattnericus.pokyh.core.bell

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * What the bell shows — the web's `lib/bell-feed.ts`: what happened lately across POKYH (new
 * grades and messages, reminders a classmate put up), to-dos and reminders coming due, the streak
 * reaching a milestone or running out; which of it the account hasn't looked at yet, and what it
 * cleared away. Pure: the inputs arrive already read (see [BellStore]).
 */
object BellFeed {
    private const val DAY = 86_400_000L

    /** How far back the feed reaches. */
    const val FEED_DAYS = 14L
    /** To-dos and reminders join the feed this long before they are due. */
    const val SOON_MS = DAY
    /** On a source's first load, only what is younger than this counts as new. */
    const val FRESH_MS = 3 * DAY
    /** Streak lengths worth a line of their own. */
    val STREAK_MILESTONES = listOf(3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 250, 300, 365)
    /** How many seen (and cleared) keys are remembered, newest first. */
    private const val SEEN_LIMIT = 400

    val zone: ZoneId get() = ZoneId.systemDefault()

    enum class Source { Grades, Messages, Reminders, Todos, Streak }
    enum class Kind { Grade, Message, Reminder, ReminderSoon, Todo, Streak, StreakLost }

    data class GradeIn(val id: Long, val subject: String, val lessonId: Int, val value: Double, val date: Int, val examType: String, val enteredAt: Long)
    data class MessageIn(val id: Int, val subject: String, val sender: String, val sent: Long, val read: Boolean)
    data class ReminderIn(val id: String, val title: String, val remindAt: Long, val createdAt: Long, val by: String, val mine: Boolean)
    data class TodoIn(val id: String, val title: String, val dueAt: Long?, val done: Boolean)
    /** A run of active days: its length and its first and last day (local midnight). */
    data class StreakRun(val days: Int, val start: Long, val end: Long)
    /** The running streak: its length and the last day it counts; [lost]: the last one that ran out. */
    data class StreakIn(val days: Int, val end: Long, val lost: StreakRun?)

    /** Each source is null until it has been read. */
    data class Input(
        val grades: List<GradeIn>? = null,
        val messages: List<MessageIn>? = null,
        val reminders: List<ReminderIn>? = null,
        val todos: List<TodoIn>? = null,
        val streak: StreakIn? = null,
        val streakRead: Boolean = false,
    )

    data class Item(
        /** Stable, for remembering what was seen. */
        val key: String,
        val source: Source,
        val kind: Kind,
        /** When it happened, or when it is due. */
        val at: Long,
        /** [at] is a day, not a moment. */
        val allDay: Boolean = false,
        val title: String,
        val sub: String = "",
        /** Where it leads (see [BellTarget]). */
        val target: BellTarget,
        /** The mark of a grade, the days of a streak. */
        val value: Double? = null,
        val overdue: Boolean = false,
        /** Nothing to tell (a message already read): never counts as new. */
        val settled: Boolean = false,
    )

    sealed interface BellTarget {
        data class Grade(val lessonId: Int) : BellTarget
        data class Message(val id: Int) : BellTarget
        data object Reminders : BellTarget
        data object Todos : BellTarget
        data object Profile : BellTarget
    }

    data class Feed(val current: List<Item>, val recent: List<Item>) {
        val all: List<Item> get() = current + recent
    }

    data class SeenState(val keys: List<String> = emptyList(), val sources: List<Source> = emptyList(), val cleared: List<String> = emptyList())

    fun untisDay(date: Int): Long = runCatching {
        LocalDate.of(date / 10000, (date / 100) % 100, date % 100).atStartOfDay(zone).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    fun startOfDay(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    /** Same calendar day [n] days later (daylight saving keeps the midnight). */
    fun addDays(ms: Long, n: Long): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().plusDays(n).atStartOfDay(zone).toInstant().toEpochMilli()

    /** An ISO date-time (with or without a zone) as epoch ms; null when it can't be read. */
    fun parseTime(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDate.parse(s.take(10)).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
    }

    /** The latest milestone the running streak has passed, and the day it did. */
    private fun milestone(streak: StreakIn): Triple<Int, Long, Long>? {
        val days = STREAK_MILESTONES.lastOrNull { it <= streak.days } ?: return null
        val start = addDays(streak.end, -(streak.days - 1).toLong())
        return Triple(days, addDays(start, (days - 1).toLong()), start)
    }

    /**
     * The latest streak of at least the first milestone that ran out: a whole day without POKYH
     * after it (a quiet today doesn't end one yet, as on the backend).
     */
    fun lostStreak(active: List<Long>, today: Long): StreakRun? {
        val runs = mutableListOf<StreakRun>()
        for (day in active.distinct().sorted()) {
            val run = runs.lastOrNull()
            if (run != null && addDays(run.end, 1) == day) runs[runs.size - 1] = run.copy(days = run.days + 1, end = day)
            else runs.add(StreakRun(1, day, day))
        }
        return runs.lastOrNull { it.days >= STREAK_MILESTONES.first() && addDays(it.end, 2) <= today }
    }

    fun build(input: Input, now: Long): Feed {
        val since = addDays(startOfDay(now), -FEED_DAYS)
        val current = mutableListOf<Item>()
        val recent = mutableListOf<Item>()

        for (g in input.grades.orEmpty()) {
            // When it was entered, when that is known; else the day of the exam.
            val entered = g.enteredAt > 0
            val at = if (entered) g.enteredAt else untisDay(g.date)
            if (g.value <= 0 || at < since || at > now) continue
            recent += Item("grade:${g.id}", Source.Grades, Kind.Grade, at, allDay = !entered, title = g.subject, sub = g.examType, target = BellTarget.Grade(g.lessonId), value = g.value)
        }
        for (m in input.messages.orEmpty()) {
            if (m.sent <= 0 || m.sent < since) continue
            recent += Item("msg:${m.id}", Source.Messages, Kind.Message, m.sent, title = m.subject, sub = m.sender, target = BellTarget.Message(m.id), settled = m.read)
        }
        for (r in input.reminders.orEmpty()) {
            if (r.remindAt > now && r.remindAt <= now + SOON_MS) {
                current += Item("rem-soon:${r.id}", Source.Reminders, Kind.ReminderSoon, r.remindAt, title = r.title, sub = if (r.mine) "" else r.by, target = BellTarget.Reminders)
            } else if (!r.mine && r.createdAt in since..now && r.remindAt > now) {
                recent += Item("rem:${r.id}", Source.Reminders, Kind.Reminder, r.createdAt, title = r.title, sub = r.by, target = BellTarget.Reminders)
            }
        }
        for (t in input.todos.orEmpty()) {
            val due = t.dueAt ?: continue
            if (t.done || due < since || due > now + SOON_MS) continue
            current += Item("todo:${t.id}:$due", Source.Todos, Kind.Todo, due, title = t.title, target = BellTarget.Todos, overdue = due < now)
        }
        input.streak?.let { streak ->
            milestone(streak)?.let { (days, at, start) ->
                if (at >= since) recent += Item("streak:$start:$days", Source.Streak, Kind.Streak, at, allDay = true, title = "", target = BellTarget.Profile, value = days.toDouble())
            }
            // Gone at the end of the first day missed.
            streak.lost?.let { lost ->
                val at = addDays(lost.end, 2)
                if (at in since..now) recent += Item("streak-lost:${lost.start}:${lost.days}", Source.Streak, Kind.StreakLost, at, allDay = true, title = "", target = BellTarget.Profile, value = lost.days.toDouble())
            }
        }
        return Feed(current.sortedBy { it.at }, recent.sortedByDescending { it.at }.take(30))
    }

    fun withoutCleared(feed: Feed, cleared: List<String>): Feed {
        if (cleared.isEmpty()) return feed
        val gone = cleared.toHashSet()
        return Feed(feed.current.filterNot { it.key in gone }, feed.recent.filterNot { it.key in gone })
    }

    private fun merge(front: List<String>, rest: List<String>): List<String> = LinkedHashSet(front + rest).toList().take(SEEN_LIMIT)

    /** These items are cleared away (and count as seen). */
    fun clearKeys(state: SeenState, keys: List<String>): SeenState =
        if (keys.isEmpty()) state else state.copy(keys = merge(keys, state.keys), cleared = merge(keys, state.cleared))

    fun isNew(item: Item, seen: Set<String>): Boolean = !item.settled && item.key !in seen

    /** Keys of the items in the feed not seen yet. */
    fun freshKeys(feed: Feed, seen: SeenState): Set<String> {
        val keys = seen.keys.toHashSet()
        return feed.all.filter { isNew(it, keys) }.map { it.key }.toSet()
    }

    /**
     * A source read for the first time on this device doesn't flood the bell: what is older than
     * [FRESH_MS] counts as seen. Due to-dos and reminders stay new, they still want doing.
     */
    fun settleSeen(state: SeenState, feed: Feed, loaded: List<Source>, now: Long): SeenState {
        val first = loaded.filter { it !in state.sources }
        if (first.isEmpty()) return state
        val old = feed.recent.filter { it.source in first && it.at < now - FRESH_MS }.map { it.key }
        return state.copy(keys = merge(old, state.keys), sources = state.sources + first)
    }

    /** Everything in the feed now counts as seen. */
    fun markSeen(state: SeenState, feed: Feed): SeenState = state.copy(keys = merge(feed.all.map { it.key }, state.keys))

    /** The account's marks, from any device, joined into this device's. */
    fun mergeRemote(state: SeenState, seen: List<String>, cleared: List<String>): SeenState =
        state.copy(keys = merge(state.keys, seen), cleared = merge(state.cleared, cleared))
}
