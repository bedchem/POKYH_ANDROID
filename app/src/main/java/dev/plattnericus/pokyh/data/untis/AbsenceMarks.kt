package dev.plattnericus.pokyh.data.untis

import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.AbsenceEntry

/**
 * What the timetable says about a lesson the student is (or was) absent from.
 *
 * Derived from the Abwesenheiten list alone, so the grid knows without that tab ever being opened:
 *
 *  - [PRE_EXCUSED] — the absence covers a lesson that has not ended yet: reported in advance.
 *  - [EXCUSED] — over, and WebUntis marks the absence excused.
 *  - [ABSENT] — over and not (yet) excused. Flips to [EXCUSED] on the next load once it is.
 */
enum class AbsenceMark(val label: String, val shortLabel: String) {
    PRE_EXCUSED("Vorentschuldigung", "Vorentsch."),
    EXCUSED("Entschuldigt", "Entsch."),
    ABSENT("Unentschuldigt", "Unentsch."),
}

/** One continuous stretch of a day covered by the same [AbsenceMark] — what the overlay draws. */
data class AbsenceBand(val startMinute: Int, val endMinute: Int, val mark: AbsenceMark)

/** A lesson's absence status and the Abwesenheiten behind it — for the lesson detail sheet. */
data class LessonAbsence(val mark: AbsenceMark, val covering: List<AbsenceEntry>)

object AbsenceMarks {

    private const val MINUTES_PER_DAY = 1440

    /**
     * A lesson is only marked when the absence covers at least this much of it (or all of it, for
     * a shorter lesson) — arriving two minutes late shouldn't draw a sliver over the lesson.
     */
    private const val MIN_COVERED_MINUTES = 15

    /**
     * The mark for a lesson on [dateNum] from [startMinute] to [endMinute], or null when no
     * absence touches it. [nowDateNum]/[nowMinute] are the school's clock.
     */
    fun markFor(
        absences: List<AbsenceEntry>,
        dateNum: Int,
        startMinute: Int,
        endMinute: Int,
        nowDateNum: Int,
        nowMinute: Int,
    ): AbsenceMark? = coverage(absences, dateNum, startMinute, endMinute, nowDateNum, nowMinute)?.first?.mark

    /** The absence status of one lesson and the absences behind it, or null when none covers it. */
    fun lessonAbsence(
        absences: List<AbsenceEntry>,
        dateNum: Int,
        startMinute: Int,
        endMinute: Int,
        nowDateNum: Int,
        nowMinute: Int,
    ): LessonAbsence? = coverage(absences, dateNum, startMinute, endMinute, nowDateNum, nowMinute)
        ?.let { (band, covering) -> LessonAbsence(band.mark, covering) }

    /** "10:00 – 13:05", or with dates when the absence spans several days. */
    fun rangeText(a: AbsenceEntry): String {
        fun hhmm(t: Int) = Fmt.minutes(t).let { "%02d:%02d".format(it / 60, it % 60) }
        fun ddmm(d: Int) = "%02d.%02d.".format(d % 100, (d / 100) % 100)
        val from = if (a.startTime > 0) hhmm(a.startTime) else ""
        val to = if (a.endTime > 0) hhmm(a.endTime) else ""
        if (a.startDate == a.endDate) return if (from.isNotEmpty() && to.isNotEmpty()) "$from – $to" else "Ganzer Tag"
        return "${ddmm(a.startDate)}${if (from.isNotEmpty()) " $from" else ""} – ${ddmm(a.endDate)}${if (to.isNotEmpty()) " $to" else ""}"
    }

    /**
     * The mark for one lesson plus the part of it the absence actually covers (minutes of the
     * day): an absence from 10:00 over a 09:30–10:20 lesson covers 10:00–10:20, not the lesson.
     */
    private fun coverage(
        absences: List<AbsenceEntry>,
        dateNum: Int,
        startMinute: Int,
        endMinute: Int,
        nowDateNum: Int,
        nowMinute: Int,
    ): Pair<AbsenceBand, List<AbsenceEntry>>? {
        val dayStart = dateNum.toLong() * MINUTES_PER_DAY
        val lessonStart = dayStart + startMinute
        val lessonEnd = dayStart + endMinute
        var from = Long.MAX_VALUE
        var to = Long.MIN_VALUE
        val covering = absences.filter { a ->
            if (a.startDate == 0 || a.endDate == 0) return@filter false
            val absStart = a.startDate.toLong() * MINUTES_PER_DAY + if (a.startTime > 0) Fmt.minutes(a.startTime) else 0
            val absEnd = a.endDate.toLong() * MINUTES_PER_DAY + if (a.endTime > 0) Fmt.minutes(a.endTime) else MINUTES_PER_DAY
            val coveredFrom = maxOf(lessonStart, absStart)
            val coveredTo = minOf(lessonEnd, absEnd)
            if (coveredTo - coveredFrom < minOf(MIN_COVERED_MINUTES.toLong(), lessonEnd - lessonStart)) return@filter false
            from = minOf(from, coveredFrom)
            to = maxOf(to, coveredTo)
            true
        }
        if (covering.isEmpty()) return null
        val over = dateNum < nowDateNum || (dateNum == nowDateNum && endMinute <= nowMinute)
        val mark = when {
            !over -> AbsenceMark.PRE_EXCUSED
            covering.all { it.isExcused } -> AbsenceMark.EXCUSED
            else -> AbsenceMark.ABSENT
        }
        return AbsenceBand((from - dayStart).toInt(), (to - dayStart).toInt(), mark) to covering
    }

    /**
     * Bands for one day, from exactly where the absence starts to where it ends (never beyond the
     * lessons it covers). Cancelled lessons are left out by the caller — nobody is absent from a
     * lesson that doesn't happen.
     *
     * A band runs on through breaks and free periods of any length — only a lesson that was
     * attended ends it. Merging by a maximum gap instead broke the band at lunch and at every
     * free period.
     */
    fun bands(
        absences: List<AbsenceEntry>,
        dateNum: Int,
        lessons: List<Pair<Int, Int>>,
        nowDateNum: Int,
        nowMinute: Int,
    ): List<AbsenceBand> {
        if (absences.isEmpty()) return emptyList()
        val out = mutableListOf<AbsenceBand>()
        var open = false
        for ((from, to) in lessons.filter { (s, e) -> e > s }.sortedBy { it.first }) {
            val covered = coverage(absences, dateNum, from, to, nowDateNum, nowMinute)?.first
            if (covered == null) {
                open = false // a lesson that was attended — the stretch ends here
                continue
            }
            val last = out.lastOrNull()
            if (open && last != null && last.mark == covered.mark) {
                out[out.size - 1] = last.copy(endMinute = maxOf(last.endMinute, covered.endMinute))
            } else {
                out += covered
                open = true
            }
        }
        return out
    }
}
