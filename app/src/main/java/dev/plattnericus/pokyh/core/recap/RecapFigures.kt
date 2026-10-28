package dev.plattnericus.pokyh.core.recap

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * A recap's WebUntis figures — the web's `lib/recap-figures.ts`: what the timetable, absences,
 * grades, exams, homework and class register of a period add up to. Worked out here, on the
 * device, from what [RecapData] loads with the student's own session; nothing of it is sent
 * anywhere.
 *
 *   lessons in school  every lesson of the period that has ended and was not cancelled
 *                      (lessons at the same time count once)
 *   missed minutes     the school's own ("Fehlzeiten") where it gives them, else worked out from
 *                      the timetable, else lesson hours × the school's usual lesson length
 *   absence rate       missed minutes against the lesson minutes, only when every week of the
 *                      period could be loaded
 */
object RecapFigures {
    const val LESSON = 0
    const val CANCELLED = 1
    const val SUBSTITUTION = 2
    const val EXAM = 3

    /** A lesson: minutes of the day, subject, teacher(s), room, state (LESSON … EXAM). */
    data class Lesson(val start: Int, val end: Int, val subject: String, val teachers: String, val room: String, val state: Int)

    /** A week of the timetable: when it was loaded, its lessons per day (YYYYMMDD). */
    class Week(val at: Long, val days: Map<Int, List<Lesson>>)

    data class Ranked(val name: String, val value: Int, val lessons: Int)

    class School(
        val days: Int,
        val lessons: Int,
        val minutes: Int,
        val cancelled: Int,
        val substitutions: Int,
        val subjects: List<Ranked>,
        val teachers: List<Ranked>,
        val rooms: List<Ranked>,
        val longestDay: Triple<Int, Int, Int>?,
        val earliest: Pair<Int, Int>?,
        val latest: Pair<Int, Int>?,
    )

    class Absences(
        val entries: Int,
        val openEntries: Int,
        val missedMinutes: Int,
        val excusedMinutes: Int,
        val possibleMinutes: Int?,
        val rate: Double?,
        val missedDays: Int,
        val bySubject: List<Ranked>,
        val byWeekday: IntArray,
    )

    class SubjectAverage(val name: String, val average: Double, val count: Int)

    class Grades(
        val count: Int,
        val average: Double,
        val positive: Int,
        val negative: Int,
        val subjects: List<SubjectAverage>,
        val best: SubjectAverage?,
        /** The top mark: value, subject, date. */
        val top: Triple<Double, String, Int>?,
    )

    class Untis(
        val complete: Boolean,
        val weeksLoaded: Int,
        val weeksTotal: Int,
        val school: School?,
        val absences: Absences?,
        val grades: Grades?,
        val exams: Int?,
        val homework: Int?,
        val classreg: Int?,
    )

    class AbsenceIn(val id: Int, val startDate: Int, val endDate: Int, val startTime: Int, val endTime: Int, val isExcused: Boolean, val hours: Int, val subjectName: String?)
    class TimeIn(val absenceId: Int, val minutes: Int, val subject: String, val excused: Boolean, val date: Int)
    class GradeSubjectIn(val subjectName: String, val grades: List<Pair<Int, Double>>)

    class Sources(
        val from: Int,
        val to: Int,
        val now: LocalDateTime,
        val weeks: Map<String, Week>,
        val absences: List<AbsenceIn>?,
        val times: List<TimeIn>?,
        val grades: List<GradeSubjectIn>?,
        val homework: List<Pair<Int, Int>>?,
        val classreg: List<Int>?,
    )

    fun dateOfNum(n: Int): LocalDate = LocalDate.of(n / 10000, n / 100 % 100, n % 100)
    fun numOf(d: LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    fun numOfIso(iso: String) = iso.replace("-", "").toInt()
    fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    /** Monday = 0 … Sunday = 6. */
    fun weekdayOfNum(n: Int) = dateOfNum(n).dayOfWeek.ordinal

    /** The Mondays (YYYY-MM-DD) of the weeks from [from] up to the earlier of [to] and today. */
    fun periodWeeks(from: Int, to: Int, now: LocalDateTime): List<String> {
        val end = minOf(to, numOf(now.toLocalDate()))
        if (end < from) return emptyList()
        val last = dateOfNum(end)
        val out = ArrayList<String>()
        var d = mondayOf(dateOfNum(from))
        while (!d.isAfter(last)) { out.add(d.toString()); d = d.plusWeeks(1) }
        return out
    }

    fun toMinutes(t: Int): Int = when {
        t == 0 -> 0
        t > 2359 -> t
        t % 100 > 59 -> t
        else -> t / 100 * 60 + t % 100
    }

    private class Tally(var value: Int = 0, var lessons: Int = 0)

    private fun ranked(map: Map<String, Tally>, limit: Int = 5): List<Ranked> =
        map.entries.map { Ranked(it.key, it.value.value, it.value.lessons) }
            .sortedWith(compareByDescending<Ranked> { it.value }.thenByDescending { it.lessons }.thenBy { it.name })
            .take(limit)

    private fun bump(map: MutableMap<String, Tally>, name: String, value: Int) {
        if (name.isEmpty()) return
        val e = map.getOrPut(name) { Tally() }
        e.value += value
        e.lessons += 1
    }

    private fun count(map: MutableMap<String, Tally>, name: String, value: Int, lessons: Int) {
        val key = name.trim()
        if (key.isEmpty()) return
        val e = map.getOrPut(key) { Tally() }
        e.value += value
        e.lessons += lessons
    }

    /** Lessons at the same time once: merged [start, end] spans. */
    private fun mergedSpans(lessons: List<Lesson>): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (l in lessons.sortedBy { it.start }) {
            val last = out.lastOrNull()
            if (last != null && l.start < last[1]) last[1] = maxOf(last[1], l.end) else out.add(intArrayOf(l.start, l.end))
        }
        return out
    }

    private class Day(val date: Int, val lessons: List<Lesson>)

    private fun periodDays(src: Sources): List<Day> {
        val today = numOf(src.now.toLocalDate())
        val clock = src.now.hour * 60 + src.now.minute
        val until = minOf(src.to, today)
        val out = ArrayList<Day>()
        for (week in src.weeks.values) for ((date, lessons) in week.days) {
            if (date < src.from || date > until) continue
            out.add(Day(date, if (date < today) lessons else lessons.filter { it.end <= clock }))
        }
        return out.sortedBy { it.date }
    }

    /** The length of one lesson in the period: the most common length up to an hour; 50 without any. */
    private fun singleLesson(days: List<Day>): Int {
        val lengths = HashMap<Int, Int>()
        for (d in days) for (l in d.lessons) if (l.end - l.start <= 60) lengths.merge(l.end - l.start, 1, Int::plus)
        var best = 50
        var most = 0
        for ((length, n) in lengths) if (n > most || (n == most && length > best)) { best = length; most = n }
        return best
    }

    private fun periodsOf(l: Lesson, single: Int) = maxOf(1, Math.round((l.end - l.start).toDouble() / single).toInt())

    private fun distinct(lessons: List<Lesson>): List<Lesson> {
        val seen = HashSet<String>()
        return lessons.filter { seen.add("${it.start}-${it.end}-${it.subject}") }
    }

    private fun schoolFigures(days: List<Day>): School {
        val single = singleLesson(days)
        val subjects = LinkedHashMap<String, Tally>()
        val teachers = LinkedHashMap<String, Tally>()
        val rooms = LinkedHashMap<String, Tally>()
        var lessons = 0
        var minutes = 0
        var cancelled = 0
        var substitutions = 0
        var schoolDays = 0
        var longest: Triple<Int, Int, Int>? = null
        var earliest: Pair<Int, Int>? = null
        var latest: Pair<Int, Int>? = null
        for (day in days) {
            val all = distinct(day.lessons)
            for (l in all) if (l.state == CANCELLED) cancelled += periodsOf(l, single)
            val held = all.filter { it.state != CANCELLED }
            if (held.isEmpty()) continue
            schoolDays += 1
            for (l in held) {
                val n = periodsOf(l, single)
                lessons += n
                if (l.state == SUBSTITUTION) substitutions += n
                count(subjects, l.subject, l.end - l.start, n)
                for (t in l.teachers.split(", ")) count(teachers, t, n, n)
                for (r in l.room.split(", ")) count(rooms, r, n, n)
            }
            val spans = mergedSpans(held)
            minutes += spans.sumOf { it[1] - it[0] }
            val start = spans.first()[0]
            val end = spans.last()[1]
            if (longest == null || end - start > longest.third - longest.second) longest = Triple(day.date, start, end)
            if (earliest == null || start < earliest.second) earliest = day.date to start
            if (latest == null || end > latest.second) latest = day.date to end
        }
        return School(schoolDays, lessons, minutes, cancelled, substitutions, ranked(subjects), ranked(teachers), ranked(rooms), longest, earliest, latest)
    }

    private fun usualLessonMinutes(days: List<Day>): Int {
        val lengths = HashMap<Int, Int>()
        for (d in days) for (s in mergedSpans(d.lessons.filter { it.state != CANCELLED })) lengths.merge(s[1] - s[0], 1, Int::plus)
        var best = 50
        var most = 0
        for ((length, n) in lengths) if (n > most) { best = length; most = n }
        return best
    }

    private class Missed(val day: Int, val subject: String, val minutes: Int)

    private fun absenceFromLessons(a: AbsenceIn, src: Sources, until: Int): List<Missed>? {
        val out = ArrayList<Missed>()
        val multiDay = a.startDate != a.endDate
        val absStart = toMinutes(a.startTime)
        val absEnd = toMinutes(a.endTime)
        val first = maxOf(a.startDate, src.from)
        val last = minOf(a.endDate, src.to, until)
        if (first > last) return out
        var d = runCatching { dateOfNum(first) }.getOrNull() ?: return null
        var n = 0
        while (numOf(d) <= last && n < 420) {
            val week = src.weeks[mondayOf(d).toString()] ?: return null
            val day = numOf(d)
            val held = week.days[day].orEmpty().filter { it.state != CANCELLED }
            for (s in mergedSpans(held)) {
                var from = s[0]
                var to = s[1]
                if (absStart > 0 && (!multiDay || day == a.startDate)) from = maxOf(from, absStart)
                if (absEnd > 0 && (!multiDay || day == a.endDate)) to = minOf(to, absEnd)
                if (to <= from) continue
                var subject = ""
                var most = 0
                for (l in held) {
                    val overlap = minOf(to, l.end) - maxOf(from, l.start)
                    if (overlap > most) { most = overlap; subject = l.subject }
                }
                out.add(Missed(day, subject, to - from))
            }
            d = d.plusDays(1)
            n++
        }
        return out
    }

    private fun absenceFigures(src: Sources, days: List<Day>, complete: Boolean): Absences? {
        if (src.absences == null && src.times == null) return null
        val until = minOf(src.to, numOf(src.now.toLocalDate()))
        fun inPeriod(d: Int) = d >= src.from && d <= until
        val absences = src.absences.orEmpty().filter { it.endDate >= src.from && it.startDate <= until }
        val official = LinkedHashMap<Int, MutableList<TimeIn>>()
        for (l in src.times.orEmpty()) {
            if (!inPeriod(l.date) || l.minutes <= 0) continue
            official.getOrPut(l.absenceId) { ArrayList() }.add(l)
        }
        val bySubject = LinkedHashMap<String, Tally>()
        val byWeekday = IntArray(7)
        val missedDays = HashSet<Int>()
        var missed = 0
        var excused = 0
        fun add(day: Int, subject: String, minutes: Int, isExcused: Boolean) {
            if (minutes <= 0) return
            missed += minutes
            if (isExcused) excused += minutes
            bump(bySubject, subject.ifEmpty { "—" }, minutes)
            byWeekday[weekdayOfNum(day)] += minutes
            missedDays.add(day)
        }
        val lessonLength = usualLessonMinutes(days)
        val counted = HashSet<Int>()
        for (a in absences) {
            counted.add(a.id)
            val own = official[a.id]
            if (own != null) {
                for (l in own) add(l.date, l.subject, l.minutes, a.isExcused)
                continue
            }
            val pieces = absenceFromLessons(a, src, until)
            if (pieces != null) for (p in pieces) add(p.day, p.subject, p.minutes, a.isExcused)
            else if (inPeriod(a.startDate)) add(a.startDate, a.subjectName.orEmpty(), a.hours * lessonLength, a.isExcused)
        }
        for ((id, own) in official) {
            if (id in counted) continue
            for (l in own) add(l.date, l.subject, l.minutes, l.excused)
        }
        val possible = if (complete) days.sumOf { d -> mergedSpans(d.lessons.filter { it.state != CANCELLED }).sumOf { it[1] - it[0] } } else null
        val officialOnly = src.absences == null
        return Absences(
            entries = if (officialOnly) official.size else absences.size,
            openEntries = if (officialOnly) official.values.count { own -> own.any { !it.excused } } else absences.count { !it.isExcused },
            missedMinutes = missed,
            excusedMinutes = excused,
            possibleMinutes = possible,
            rate = possible?.takeIf { it > 0 }?.let { minOf(100.0, missed.toDouble() / it * 100) },
            missedDays = missedDays.size,
            bySubject = ranked(bySubject),
            byWeekday = byWeekday,
        )
    }

    private fun gradeFigures(src: Sources): Grades? {
        val input = src.grades ?: return null
        val values = ArrayList<Double>()
        val subjects = ArrayList<SubjectAverage>()
        var top: Triple<Double, String, Int>? = null
        val bySubject = LinkedHashMap<String, MutableList<Pair<Int, Double>>>()
        for (s in input) bySubject.getOrPut(s.subjectName) { ArrayList() }.addAll(s.grades)
        for ((name, grades) in bySubject) {
            val own = grades.filter { it.first >= src.from && it.first <= src.to && it.second > 0 }
            if (own.isEmpty()) continue
            subjects.add(SubjectAverage(name, own.sumOf { it.second } / own.size, own.size))
            for ((date, v) in own) {
                values.add(v)
                if (top == null || v > top.first || (v == top.first && date > top.third)) top = Triple(v, name, date)
            }
        }
        if (values.isEmpty()) return Grades(0, 0.0, 0, 0, emptyList(), null, null)
        subjects.sortWith(compareByDescending<SubjectAverage> { it.average }.thenByDescending { it.count }.thenBy { it.name })
        val best = subjects.firstOrNull { it.count >= 2 } ?: subjects.firstOrNull()
        return Grades(values.size, values.average(), values.count { it >= 6 }, values.count { it < 6 }, subjects, best, top)
    }

    fun untis(src: Sources): Untis {
        val mondays = periodWeeks(src.from, src.to, src.now)
        val loaded = mondays.count { it in src.weeks }
        val complete = mondays.isNotEmpty() && loaded == mondays.size
        val days = periodDays(src)
        val today = numOf(src.now.toLocalDate())
        fun within(date: Int) = date >= src.from && date <= minOf(src.to, today)
        // The exams of the period, from the timetable: each exam lesson once.
        val exams = src.weeks.values.flatMap { w -> w.days.entries.filter { within(it.key) }.flatMap { (d, ls) -> distinct(ls.filter { it.state == EXAM }).map { "$d-${it.start}-${it.subject}" } } }.toSet().size
        return Untis(
            complete = complete,
            weeksLoaded = loaded,
            weeksTotal = mondays.size,
            school = if (loaded > 0) schoolFigures(days) else null,
            absences = absenceFigures(src, days, complete),
            grades = gradeFigures(src),
            exams = if (loaded > 0) exams else null,
            homework = src.homework?.filter { within(it.second) }?.map { it.first }?.toSet()?.size,
            classreg = src.classreg?.count { within(it) },
        )
    }
}
