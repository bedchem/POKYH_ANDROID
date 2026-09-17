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
enum class AbsenceMark(val label: String) {
    PRE_EXCUSED("Vorentschuldigung"),
    EXCUSED("Entschuldigt"),
    ABSENT("Gefehlt"),
}

/** One continuous stretch of a day covered by the same [AbsenceMark] — what the overlay draws. */
data class AbsenceBand(val startMinute: Int, val endMinute: Int, val mark: AbsenceMark)

object AbsenceMarks {

    /** Lessons closer than this still share one band — a break is not the end of an absence. */
    private const val MERGE_GAP_MINUTES = 30

    private const val MINUTES_PER_DAY = 1440

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
    ): AbsenceMark? {
        val lessonStart = dateNum.toLong() * MINUTES_PER_DAY + startMinute
        val lessonEnd = dateNum.toLong() * MINUTES_PER_DAY + endMinute
        val covering = absences.filter { a ->
            if (a.startDate == 0 || a.endDate == 0) return@filter false
            val absStart = a.startDate.toLong() * MINUTES_PER_DAY + if (a.startTime > 0) Fmt.minutes(a.startTime) else 0
            val absEnd = a.endDate.toLong() * MINUTES_PER_DAY + if (a.endTime > 0) Fmt.minutes(a.endTime) else MINUTES_PER_DAY
            lessonStart < absEnd && lessonEnd > absStart
        }
        if (covering.isEmpty()) return null
        val over = dateNum < nowDateNum || (dateNum == nowDateNum && endMinute <= nowMinute)
        return when {
            !over -> AbsenceMark.PRE_EXCUSED
            covering.all { it.isExcused } -> AbsenceMark.EXCUSED
            else -> AbsenceMark.ABSENT
        }
    }

    /**
     * Bands for one day, from its lessons' time ranges. Cancelled lessons are left out by the
     * caller — nobody is absent from a lesson that doesn't happen.
     */
    fun bands(
        absences: List<AbsenceEntry>,
        dateNum: Int,
        lessons: List<Pair<Int, Int>>,
        nowDateNum: Int,
        nowMinute: Int,
    ): List<AbsenceBand> {
        if (absences.isEmpty()) return emptyList()
        val marked = lessons
            .filter { (s, e) -> e > s }
            .sortedBy { it.first }
            .mapNotNull { (s, e) -> markFor(absences, dateNum, s, e, nowDateNum, nowMinute)?.let { AbsenceBand(s, e, it) } }
        val out = mutableListOf<AbsenceBand>()
        for (b in marked) {
            val last = out.lastOrNull()
            if (last != null && last.mark == b.mark && b.startMinute <= last.endMinute + MERGE_GAP_MINUTES) {
                out[out.size - 1] = last.copy(endMinute = maxOf(last.endMinute, b.endMinute))
            } else {
                out += b
            }
        }
        return out
    }
}
