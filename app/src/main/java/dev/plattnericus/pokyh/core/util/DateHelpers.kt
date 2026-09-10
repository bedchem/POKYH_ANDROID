package dev.plattnericus.pokyh.core.util

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Datums-/Kalenderhilfen — 1:1 portiert aus `DateHelpers.swift`, auf Basis von
 * kotlinx.datetime statt `java.time`/`Calendar`. ISO-8601-Woche, firstWeekday = Montag.
 * Modell-Felder (TimetableEntry.date, AbsenceEntry.startDate, …) bleiben rohe
 * `Int` yyyyMMdd — diese Helfer konvertieren nur an den Rändern, wo tatsächlich
 * Datumsarithmetik nötig ist.
 */

/** yyyyMMdd-Int (z. B. `TimetableEntry.date`) -> LocalDate. */
fun Int.toLocalDate(): LocalDate {
    val s = toString().padStart(8, '0')
    return LocalDate(
        year = s.substring(0, 4).toInt(),
        monthNumber = s.substring(4, 6).toInt(),
        dayOfMonth = s.substring(6, 8).toInt(),
    )
}

/** LocalDate -> yyyyMMdd-Int. */
fun LocalDate.toYyyyMMdd(): Int = year * 10000 + monthNumber * 100 + dayOfMonth

/** ISO-Wochentag: Montag = 1 … Sonntag = 7 (kotlinx.datetime.DayOfWeek ist wie java.time geordnet). */
private val LocalDate.isoDayNumber: Int
    get() = dayOfWeek.ordinal + 1

/** Montag der Woche, die `date` enthält (ISO-8601, firstWeekday = 2 = Montag). */
fun mondayOfWeek(date: LocalDate): LocalDate = date.minus(date.isoDayNumber - 1, DateTimeUnit.DAY)

/** Montag…Sonntag der Woche, die `date` enthält. */
fun weekRange(date: LocalDate): ClosedRange<LocalDate> {
    val monday = mondayOfWeek(date)
    return monday..monday.plus(6, DateTimeUnit.DAY)
}

/** ISO-8601-Kalenderwoche (1…53) von `date`. */
fun isoWeekNumber(date: LocalDate): Int {
    val thursday = date.plus(4 - date.isoDayNumber, DateTimeUnit.DAY)
    return (thursday.dayOfYear - 1) / 7 + 1
}

/** Heutiges Datum in der System-Zeitzone. */
fun todayLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** Schuljahr-Kalender — entspricht `enum SchoolDates` aus DateHelpers.swift. */
object SchoolDates {
    fun todayIso(): String = todayLocalDate().toString()
    fun todayNum(): Int = todayLocalDate().toYyyyMMdd()

    /** ISO-String (yyyy-MM-dd) des Montags der Woche, die `date` enthält. */
    fun mondayIso(date: LocalDate = todayLocalDate()): String = mondayOfWeek(date).toString()

    val currentSchoolYear: Int
        get() {
            val today = todayLocalDate()
            return if (today.monthNumber >= 9) today.year else today.year - 1
        }

    /** Schuljahresbeginn (1. September) als yyyyMMdd-Int. */
    fun yearStart(year: Int? = null): Int {
        val y = year ?: currentSchoolYear
        return y * 10000 + 901
    }

    /** Schuljahresende (30. Juni) als yyyyMMdd-Int. */
    fun yearEnd(year: Int? = null): Int {
        val y = year ?: currentSchoolYear
        return (y + 1) * 10000 + 630
    }

    val availableYears: List<Int>
        get() = (0 until 4).map { currentSchoolYear - it }
}
