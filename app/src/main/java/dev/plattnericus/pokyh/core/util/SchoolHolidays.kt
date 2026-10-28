package dev.plattnericus.pokyh.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.data.json.JsonDyn
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** One of the school's breaks: days as YYYY-MM-DD, and the real names WebUntis has for it. */
@Serializable
data class SchoolHoliday(val start: String, val end: String, val names: List<String> = emptyList())

/**
 * The school's breaks by name ("Herbstferien") on the days without lessons — the web's
 * lib/holidays.ts and the backend's webuntis/holidays.ts. Schools often call them only
 * "Ferien3" or "8.12." in WebUntis, so a break without a real name is named by when it is:
 * the holidays of the Italian (and South Tyrolean) school year and the breaks around them.
 */
object SchoolHolidays {
    private val _list = MutableStateFlow<List<SchoolHoliday>>(emptyList())
    val list: StateFlow<List<SchoolHoliday>> = _list.asStateFlow()

    fun set(holidays: List<SchoolHoliday>) {
        _list.value = holidays
    }

    // "Ferien3", "Feiertag", "8.12.", "25/5/2026": a placeholder, not a name.
    private fun isPlaceholder(name: String): Boolean {
        val n = name.trim()
        return n.isEmpty() ||
            Regex("^(ferien|feiertag|holiday|vacanz[ae]|frei)\\s*\\d*$", RegexOption.IGNORE_CASE).matches(n) ||
            Regex("^[\\d./\\s-]+$").matches(n)
    }

    private fun ymd(n: Int?): String? {
        val s = n?.toString() ?: return null
        return if (s.length == 8) "${s.take(4)}-${s.substring(4, 6)}-${s.substring(6, 8)}" else null
    }

    /** JSON-RPC getHolidays → breaks from [fromIso] on, overlapping and adjoining ones merged. */
    fun read(result: JsonDyn, fromIso: String): List<SchoolHoliday> {
        val entries = result.array.mapNotNull { r ->
            val start = ymd(r["startDate"].int) ?: return@mapNotNull null
            val end = ymd(r["endDate"].int) ?: return@mapNotNull null
            if (end < start) return@mapNotNull null
            val names = listOfNotNull(r["longName"].string, r["name"].string).map { it.trim() }.filter { !isPlaceholder(it) }.distinct()
            SchoolHoliday(start, end, names)
        }.filter { it.end >= fromIso }.sortedWith(compareBy({ it.start }, { it.end }))
        val merged = mutableListOf<SchoolHoliday>()
        for (h in entries) {
            val last = merged.lastOrNull()
            if (last != null && h.start <= LocalDate.parse(last.end).plusDays(1).toString()) {
                merged[merged.size - 1] = last.copy(end = maxOf(last.end, h.end), names = (last.names + h.names).distinct())
            } else {
                merged += h
            }
        }
        return merged
    }

    /** Easter Sunday of a year (anonymous Gregorian algorithm). */
    private fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    private val FIXED = mapOf(
        "01-01" to "newYear", "01-06" to "epiphany", "04-25" to "liberation", "05-01" to "labour",
        "06-02" to "republic", "08-15" to "assumption", "11-01" to "allSaints", "12-08" to "immaculate",
        "12-25" to "christmasDay", "12-26" to "stStephen",
    )

    private fun publicHolidayOn(day: LocalDate): String? {
        FIXED["%02d-%02d".format(day.monthValue, day.dayOfMonth)]?.let { return it }
        return when (ChronoUnit.DAYS.between(easterSunday(day.year), day)) {
            1L -> "easterMonday"
            39L -> "ascension"
            50L -> "pentecost"
            60L -> "corpusChristi"
            else -> null
        }
    }

    /** What kind of break it is (web `holidayKey`). */
    fun keyOf(h: SchoolHoliday): String {
        val start = LocalDate.parse(h.start)
        val end = LocalDate.parse(h.end)
        val length = ChronoUnit.DAYS.between(start, end) + 1
        fun covers(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
        fun inside(month: Int, day: Int) = listOf(start.year, start.year + 1).any { y -> covers(LocalDate.of(y, month, day)) }
        if (inside(12, 25) || inside(1, 1)) return if (length >= 3) "christmas" else if (inside(12, 25)) "christmasDay" else "newYear"
        val easter = easterSunday(start.year)
        if (length >= 3 && (covers(easter) || covers(easter.plusDays(1)))) return "easter"
        if (length >= 3) {
            val month = start.monthValue
            if (length >= 21 && month in 6..8) return "summer"
            if (month == 10 || month == 11) return "autumn"
            if (month == 2 || (month == 3 && start.dayOfMonth <= 7)) return "carnival"
        }
        var d = start
        while (!d.isAfter(end)) {
            publicHolidayOn(d)?.let { return it }
            d = d.plusDays(1)
        }
        return if (length >= 3) "break" else "dayOff"
    }

    /** The break a YYYYMMDD day falls in, if any. */
    fun on(dayNum: Int, holidays: List<SchoolHoliday> = _list.value): SchoolHoliday? {
        val iso = ymd(dayNum) ?: return null
        return holidays.firstOrNull { it.start <= iso && iso <= it.end }
    }

    /** Its real name where WebUntis has one, else the name of when it is. */
    fun nameOf(h: SchoolHoliday): String = h.names.firstOrNull() ?: I18n.s.timetable.holidayNames[keyOf(h)] ?: I18n.s.timetable.holiday
}

/** The name of the break [dayNum] falls in ("Herbstferien"), or null; follows the loaded list. */
@Composable
fun holidayNameOn(dayNum: Int): String? {
    val list by SchoolHolidays.list.collectAsState()
    return SchoolHolidays.on(dayNum, list)?.let { SchoolHolidays.nameOf(it) }
}
