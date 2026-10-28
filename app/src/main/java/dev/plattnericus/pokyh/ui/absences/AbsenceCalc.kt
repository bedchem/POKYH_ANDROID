package dev.plattnericus.pokyh.ui.absences

import android.content.Context
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.TimetableEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The absence rate — the web's `lib/absence-calc.ts`: missed lesson minutes against the lesson
 * minutes the school year has had so far. Abwesenheiten and Home's tile both work it out here,
 * from the same data, so they show the same figures.
 *
 * The missed minutes of an absence are the school's own (WebUntis "Fehlzeiten",
 * [dev.plattnericus.pokyh.data.untis.UntisClient.absenceTimes]) where it gives them, else worked
 * out from the timetable, else WebUntis' lesson hours × the school's usual lesson length. The whole
 * is every lesson of the timetable from 1 September that has ended, or, for the whole year, every
 * lesson the timetable has up to 30 June. Nothing about the school's hours is assumed.
 *
 * The timetable takes a request per week, so the lessons of each week are kept on the device. A
 * week that is over is asked for again after a week, the running one after half an hour, one still
 * to come after a day (only the whole year needs those), and a week that could not be loaded is
 * asked for again next time: until every week is there, there is no rate, rather than one counted
 * from a year with weeks missing.
 */
object AbsenceCalc {
    private const val PREFS = "pokyh_abscalc"
    private const val CURRENT_WEEK_TTL = 30L * 60 * 1000
    private const val PAST_WEEK_TTL = 7L * 24 * 60 * 60 * 1000
    private const val FUTURE_WEEK_TTL = 24L * 60 * 60 * 1000
    /** Weeks asked for at the same time; all of them at once get refused. */
    private const val PARALLEL_WEEKS = 6
    /** An absence spanning more than this is taken as a mistake in its dates. */
    private const val MAX_ABSENCE_WEEKS = 60

    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val lock = Mutex()

    /** A week's lessons per day (YYYYMMDD): [start, end] in minutes; lessons at the same time merged. */
    class StoredWeek(val at: Long, val days: Map<Int, List<IntArray>>)

    /** The lessons of a school year, per week (its Monday, YYYY-MM-DD). */
    class LessonCalendar(val weeks: MutableMap<String, StoredWeek> = LinkedHashMap())

    /** The school's own minutes per missed lesson (WebUntis "Fehlzeiten"). */
    @kotlinx.serialization.Serializable
    data class OfficialMinute(
        val absenceId: Int,
        val minutes: Int,
        /** The missed lesson's day (YYYYMMDD), subject and whether it is excused — for the recap. */
        val date: Int = 0,
        val subject: String = "",
        val excused: Boolean = false,
    )

    class Figures(
        /** Missed minutes per absence id. */
        val perAbsence: Map<Int, Int>,
        val minutes: Int,
        val excusedMinutes: Int,
        /** Lesson minutes of the school year so far (or the whole year); null until every week is kept. */
        val possible: Int?,
        val rate: Double?,
    )

    private fun dateOfNum(n: Int): LocalDate = LocalDate.of(n / 10000, n / 100 % 100, n % 100)
    private fun numOf(d: LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    private fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** A WebUntis time in minutes of the day: HHMM, or already minutes when it cannot be HHMM. */
    fun toMinutes(t: Int): Int = when {
        t == 0 -> 0
        t > 2359 -> t
        t % 100 > 59 -> t
        else -> t / 100 * 60 + t % 100
    }

    /** The moment the school year counts up to: now, or the end of 30 June for the whole year and a year that is over. */
    private fun countUntil(year: Int, now: LocalDateTime, whole: Boolean): LocalDateTime {
        val end = LocalDateTime.of(year + 1, 6, 30, 23, 59)
        return if (!whole && now < end) now else end
    }

    /** The Mondays of the weeks from 1 September up to now (`whole`: up to 30 June). */
    fun schoolYearWeeks(year: Int, now: LocalDateTime = LocalDateTime.now(zone), whole: Boolean = false): List<String> {
        val until = countUntil(year, now, whole).toLocalDate()
        val out = ArrayList<String>()
        var d = mondayOf(LocalDate.of(year, 9, 1))
        while (!d.isAfter(until)) { out.add(d.toString()); d = d.plusWeeks(1) }
        return out
    }

    /** The Mondays of the weeks the absences touch. */
    fun absenceWeeks(absences: List<AbsenceEntry>): List<String> {
        val out = LinkedHashSet<String>()
        for (a in absences) {
            val end = runCatching { dateOfNum(a.endDate) }.getOrNull() ?: continue
            var d = runCatching { mondayOf(dateOfNum(a.startDate)) }.getOrNull() ?: continue
            var n = 0
            while (!d.isAfter(end) && n < MAX_ABSENCE_WEEKS) { out.add(d.toString()); d = d.plusWeeks(1); n++ }
        }
        return out.toList()
    }

    private fun mergeSlots(slots: List<IntArray>): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (s in slots.sortedBy { it[0] }) {
            val last = out.lastOrNull()
            // Overlapping only: lessons one after the other stay apart, so each counts once it has ended.
            if (last != null && s[0] < last[1]) last[1] = maxOf(last[1], s[1]) else out.add(intArrayOf(s[0], s[1]))
        }
        return out
    }

    /** A week of the timetable as lessons per day: cancelled lessons and entries without a subject left out. */
    fun lessonsOfWeek(entries: List<TimetableEntry>): Map<Int, List<IntArray>> {
        val byDay = LinkedHashMap<Int, MutableList<IntArray>>()
        for (e in entries) {
            if (e.isCancelled) continue
            if (e.subjectName.isEmpty() && e.subjectLong.isEmpty()) continue
            val start = toMinutes(e.startTime)
            val end = toMinutes(e.endTime)
            if (end <= start) continue
            byDay.getOrPut(e.date) { ArrayList() }.add(intArrayOf(start, end))
        }
        return byDay.mapValues { mergeSlots(it.value) }
    }

    private fun key(studentId: Int, year: Int) = "v2_${studentId}_$year"

    fun readCalendar(context: Context, studentId: Int, year: Int): LessonCalendar? {
        if (studentId <= 0) return null
        return runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(studentId, year), null) ?: return null
            val weeks = JSONObject(raw)
            val cal = LessonCalendar()
            for (monday in weeks.keys()) {
                val w = weeks.getJSONObject(monday)
                val days = HashMap<Int, List<IntArray>>()
                val d = w.getJSONObject("days")
                for (day in d.keys()) {
                    val arr = d.getJSONArray(day)
                    days[day.toInt()] = (0 until arr.length()).map { i -> arr.getJSONArray(i).let { intArrayOf(it.getInt(0), it.getInt(1)) } }
                }
                cal.weeks[monday] = StoredWeek(w.getLong("at"), days)
            }
            cal
        }.getOrNull()
    }

    private fun writeCalendar(context: Context, studentId: Int, year: Int, cal: LessonCalendar) {
        if (studentId <= 0) return
        val weeks = JSONObject()
        for ((monday, w) in cal.weeks) {
            val days = JSONObject()
            for ((day, slots) in w.days) days.put(day.toString(), JSONArray().apply { slots.forEach { put(JSONArray().put(it[0]).put(it[1])) } })
            weeks.put(monday, JSONObject().put("at", w.at).put("days", days))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(studentId, year), weeks.toString()).apply()
    }

    private fun isDue(week: StoredWeek?, monday: String, now: Long): Boolean {
        if (week == null) return true
        val m = LocalDate.parse(monday)
        val starts = m.atStartOfDay(zone).toInstant().toEpochMilli()
        val over = m.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
        // Loaded before the week was over: lessons may have been cancelled since.
        val ttl = if (week.at >= over) PAST_WEEK_TTL else if (now < starts) FUTURE_WEEK_TTL else CURRENT_WEEK_TTL
        return now - week.at > ttl
    }

    /**
     * Loads the weeks of the school year so far (`whole`: up to 30 June, and [extraWeeks]) that
     * are not kept yet or are due again, a few at a time, and keeps them. A week that fails stays
     * as it was. Throws the session's [AppError] when it ran out.
     */
    suspend fun refreshCalendar(
        context: Context,
        studentId: Int,
        year: Int,
        fetchWeek: suspend (monday: String) -> List<TimetableEntry>,
        extraWeeks: List<String> = emptyList(),
        whole: Boolean = false,
    ): LessonCalendar = lock.withLock {
        val cal = withContext(Dispatchers.IO) { readCalendar(context, studentId, year) } ?: LessonCalendar()
        val now = System.currentTimeMillis()
        val due = (schoolYearWeeks(year, whole = whole) + extraWeeks).distinct().filter { isDue(cal.weeks[it], it, now) }
        var expired: AppError? = null
        val queue = ArrayDeque(due)
        coroutineScope {
            (0 until minOf(PARALLEL_WEEKS, due.size)).map {
                async {
                    while (expired == null) {
                        val monday = synchronized(queue) { queue.removeFirstOrNull() } ?: break
                        try {
                            val entries = fetchWeek(monday)
                            synchronized(cal) { cal.weeks[monday] = StoredWeek(now, lessonsOfWeek(entries)) }
                        } catch (e: AppError) {
                            if (e.isSessionExpired) expired = e
                        } catch (_: Exception) {
                            // Stays as it was; asked for again next time.
                        }
                    }
                }
            }.awaitAll()
        }
        if (due.isNotEmpty()) withContext(Dispatchers.IO) { writeCalendar(context, studentId, year, cal) }
        expired?.let { throw it }
        cal
    }

    /** Whether every week of the school year so far (`whole`: up to 30 June) is kept. */
    fun hasWholeYear(cal: LessonCalendar?, year: Int, whole: Boolean = false): Boolean =
        cal != null && schoolYearWeeks(year, whole = whole).all { it in cal.weeks }

    /** Lesson minutes of the school year so far: every lesson from 1 September that has ended (`whole`: up to 30 June). */
    fun possibleMinutes(cal: LessonCalendar, year: Int, whole: Boolean = false): Int {
        val from = year * 10000 + 901
        val until = countUntil(year, LocalDateTime.now(zone), whole)
        val today = numOf(until.toLocalDate())
        val clock = until.hour * 60 + until.minute
        var total = 0
        for (week in cal.weeks.values) for ((day, slots) in week.days) {
            if (day < from || day > today) continue
            for (s in slots) if (day < today || s[1] <= clock) total += s[1] - s[0]
        }
        return total
    }

    /** The lesson minutes an absence covers; null where a week of it is not kept. */
    private fun minutesFromLessons(a: AbsenceEntry, cal: LessonCalendar): Int? {
        val multiDay = a.startDate != a.endDate
        val absStart = toMinutes(a.startTime)
        val absEnd = toMinutes(a.endTime)
        val end = runCatching { dateOfNum(a.endDate) }.getOrNull() ?: return null
        var d = runCatching { dateOfNum(a.startDate) }.getOrNull() ?: return null
        var total = 0
        var n = 0
        while (!d.isAfter(end) && n < MAX_ABSENCE_WEEKS * 7) {
            val week = cal.weeks[mondayOf(d).toString()] ?: return null
            val day = numOf(d)
            for (s in week.days[day].orEmpty()) {
                var from = s[0]
                var to = s[1]
                // Times are those of the first and the last day; days between are whole.
                if (absStart > 0 && (!multiDay || day == a.startDate)) from = maxOf(from, absStart)
                if (absEnd > 0 && (!multiDay || day == a.endDate)) to = minOf(to, absEnd)
                if (to > from) total += to - from
            }
            d = d.plusDays(1)
            n++
        }
        return total
    }

    /** The most common length of a lesson, from the timetable or the school's own minutes; only with neither the usual 50. */
    private fun usualLessonMinutes(cal: LessonCalendar?, official: List<OfficialMinute>?): Int {
        val count = HashMap<Int, Int>()
        for (week in cal?.weeks?.values.orEmpty()) for (slots in week.days.values) for (s in slots) count.merge(s[1] - s[0], 1, Int::plus)
        if (count.isEmpty()) for (l in official.orEmpty()) if (l.minutes > 0) count.merge(l.minutes, 1, Int::plus)
        var best = 50
        var most = 0
        for ((length, n) in count) if (n > most) { best = length; most = n }
        return best
    }

    fun figures(
        absences: List<AbsenceEntry>,
        cal: LessonCalendar?,
        official: List<OfficialMinute>?,
        year: Int,
        whole: Boolean = false,
    ): Figures {
        val fromSchool = HashMap<Int, Int>()
        for (l in official.orEmpty()) fromSchool.merge(l.absenceId, l.minutes, Int::plus)
        val lesson = usualLessonMinutes(cal, official)
        val perAbsence = HashMap<Int, Int>()
        var minutes = 0
        var excused = 0
        for (a in absences) {
            val m = fromSchool[a.id] ?: cal?.let { minutesFromLessons(a, it) } ?: (a.hours * lesson)
            perAbsence[a.id] = m
            minutes += m
            if (a.isExcused) excused += m
        }
        val possible = if (hasWholeYear(cal, year, whole)) possibleMinutes(cal!!, year, whole) else null
        val rate = possible?.takeIf { it > 0 }?.let { minOf(100.0, minutes.toDouble() / it * 100) }
        return Figures(perAbsence, minutes, excused, possible, rate)
    }
}
