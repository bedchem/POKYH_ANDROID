package dev.plattnericus.pokyh.ui.absences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.core.util.mondayOfWeek
import dev.plattnericus.pokyh.core.util.toLocalDate
import dev.plattnericus.pokyh.core.util.toYyyyMMdd
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

// ─── Abwesenheitsberechnung — 1:1 portiert aus AbsencesView.swift ───────────────────────────
// Die Fehlstunden werden NICHT aus dem von WebUntis gelieferten `hours`-Feld gerechnet,
// sondern exakt über den Stundenplan: pro Abwesenheit werden die tatsächlichen Unterrichts-
// minuten in deren Zeitfenster aufsummiert. Zusätzlich werden alle möglichen Unterrichts-
// minuten seit Schuljahresbeginn summiert (berücksichtigt Ferien, Feiertage, persönlichen
// Stundenplan) → daraus die Fehlquote. Eine „Schulstunde" zählt dabei als 50 Minuten.

private data class DaySlot(val startMins: Int, val endMins: Int)

/** WebUntis-Zeitwert → Minuten seit Mitternacht. Werte können als Minuten (z. B. 475 = 07:55)
 * ODER als HHMM (z. B. 755 = 07:55) kommen. Erkennung: sind die letzten zwei Stellen > 59,
 * kann es kein gültiges HHMM sein. */
private fun toMinutesAbs(t: Int): Int {
    if (t == 0) return 0
    if (t > 2359) return t
    if (t % 100 > 59) return t
    return (t / 100) * 60 + (t % 100)
}

/** 0 = Sonntag … 6 = Samstag (wie JavaScripts `Date.getDay`). kotlinx.datetime orders Monday=0. */
private fun jsWeekday(d: LocalDate): Int = (d.dayOfWeek.ordinal + 1) % 7

/** Je ein Montag pro Kalenderwoche von Schuljahresbeginn (1. Sept) bis `now`. */
private fun weeksForSchoolYear(now: LocalDate, sep: LocalDate): List<String> {
    var d = mondayOfWeek(sep)
    val out = mutableListOf<String>()
    while (d <= now) {
        out.add(d.toString())
        d = d.plus(7, DateTimeUnit.DAY)
    }
    return out
}

/** Je ein Montag pro Kalenderwoche, die sich mit einer Abwesenheit überschneidet. */
private fun weeksForAbsences(absences: List<AbsenceEntry>): List<String> {
    val set = mutableSetOf<String>()
    for (a in absences) {
        var d = a.startDate.toLocalDate()
        val end = a.endDate.toLocalDate()
        while (d <= end) {
            set.add(mondayOfWeek(d).toString())
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
    return set.toList()
}

/** Exakte Unterrichtsminuten einer Abwesenheit über den Stundenplan. */
private fun calcAbsenceMinutes(entry: AbsenceEntry, dateMap: Map<Int, List<DaySlot>>): Int {
    var total = 0
    var d = entry.startDate.toLocalDate()
    val end = entry.endDate.toLocalDate()
    val isMultiDay = entry.startDate != entry.endDate
    val absStartMin = toMinutesAbs(entry.startTime)
    val absEndMin = toMinutesAbs(entry.endTime)

    while (d <= end) {
        val dow = jsWeekday(d)
        if (dow != 0 && dow != 6) {
            val dateNum = d.toYyyyMMdd()
            for (slot in dateMap[dateNum].orEmpty()) {
                // Gezählte Dauer auf das Abwesenheitsfenster beschneiden.
                var countStart = slot.startMins
                var countEnd = slot.endMins
                if (isMultiDay) {
                    if (absStartMin > 0 && dateNum == entry.startDate) countStart = maxOf(countStart, absStartMin)
                    if (absEndMin > 0 && dateNum == entry.endDate) countEnd = minOf(countEnd, absEndMin)
                } else {
                    if (absStartMin > 0) countStart = maxOf(countStart, absStartMin)
                    if (absEndMin > 0) countEnd = minOf(countEnd, absEndMin)
                }
                if (countEnd > countStart) total += countEnd - countStart
            }
        }
        d = d.plus(1, DateTimeUnit.DAY)
    }
    return total
}

/** exact = true -> "Xh Ym" (0m omitted); false -> gerundete Stunden "Xh". */
fun formatAbsenceMinutes(m: Int, exact: Boolean): String {
    if (!exact) return "${Math.round(m / 50.0)}h"
    val h = m / 50
    val min = m % 50
    return if (min == 0) "${h}h" else "${h}h ${min}m"
}

private val monthNamesDE = listOf("Jän", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

data class MonthGroup(val id: String, val label: String, val entries: List<AbsenceEntry>, val totalMinutes: Int)

/** Gruppiert nach Jahr-Monat des Startdatums, absteigend sortiert. */
fun groupAbsencesByMonth(entries: List<AbsenceEntry>, minutesMap: Map<Int, Int>): List<MonthGroup> {
    val map = linkedMapOf<String, MutableList<AbsenceEntry>>()
    for (e in entries) {
        val s = e.startDate.toString()
        if (s.length != 8) continue
        val key = "${s.substring(0, 4)}-${s.substring(4, 6)}"
        map.getOrPut(key) { mutableListOf() }.add(e)
    }
    return map.map { (key, es) ->
        val parts = key.split("-")
        val year = parts[0]
        val month = parts[1].toIntOrNull() ?: 1
        val total = es.sumOf { minutesMap[it.id] ?: (it.hours * 50) }
        MonthGroup(id = key, label = "${monthNamesDE[month - 1]} $year", entries = es, totalMinutes = total)
    }.sortedByDescending { it.id }
}

/** Port of `AbsencesView`'s `@State`/`load()` (AbsencesView.swift). */
@HiltViewModel
class AbsencesViewModel @Inject constructor(
    private val appState: AppState,
    private val untisClient: UntisClient,
) : ViewModel() {

    /** Available school years for the picker menu, newest first — `SchoolDates.availableYears`. */
    val availableYears: List<Int> = SchoolDates.availableYears

    private val _year = MutableStateFlow(SchoolDates.currentSchoolYear)
    val year: StateFlow<Int> = _year.asStateFlow()

    private val _exact = MutableStateFlow(false)
    val exact: StateFlow<Boolean> = _exact.asStateFlow()

    private val _absences = MutableStateFlow<List<AbsenceEntry>>(emptyList())
    val absences: StateFlow<List<AbsenceEntry>> = _absences.asStateFlow()

    private val _minutesMap = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val minutesMap: StateFlow<Map<Int, Int>> = _minutesMap.asStateFlow()

    private val _totalPossibleMins = MutableStateFlow(1)
    val totalPossibleMins: StateFlow<Int> = _totalPossibleMins.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    fun toggleExact() {
        _exact.value = !_exact.value
    }

    /** Menu selection ("Button(\"\(y)/\(...)\") { year = y; Task { await load() } }"). */
    fun selectYear(y: Int) {
        if (y == _year.value) return
        _year.value = y
        load()
    }

    fun retry() = load()

    private fun load() {
        val session = appState.session.value ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val y = _year.value
                val parsed = untisClient.absences(
                    session,
                    session.studentId,
                    SchoolDates.yearStart(y).toString(),
                    SchoolDates.yearEnd(y).toString(),
                )
                _absences.value = parsed

                // Schuljahres-Zeitraum für das gewählte Jahr.
                val sep = (y * 10000 + 901).toLocalDate() // 1. Sept
                val now = if (y == SchoolDates.currentSchoolYear) {
                    dev.plattnericus.pokyh.core.util.todayLocalDate()
                } else {
                    ((y + 1) * 10000 + 630).toLocalDate() // 30. Juni Folgejahr
                }

                // Stundenplan für Abwesenheits-Wochen + alle Schuljahres-Wochen laden
                // (für einen korrekten Nenner der Fehlquote).
                val weeks = (weeksForAbsences(parsed) + weeksForSchoolYear(now, sep)).toSet().toList()
                val dateMap = buildDateMap(session, weeks)

                // Exakte Minuten je Abwesenheit.
                val mins = mutableMapOf<Int, Int>()
                for (e in parsed) mins[e.id] = calcAbsenceMinutes(e, dateMap)
                _minutesMap.value = mins

                // Alle möglichen Unterrichtsminuten seit Schuljahresbeginn.
                val sepNum = sep.toYyyyMMdd()
                val nowNum = now.toYyyyMMdd()
                var possible = 0
                for ((dateNum, slots) in dateMap) {
                    if (dateNum in sepNum..nowNum) {
                        for (slot in slots) possible += slot.endMins - slot.startMins
                    }
                }
                _totalPossibleMins.value = maxOf(1, possible)
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _error.value = if (e.isSessionExpired) "Sitzung abgelaufen. Bitte erneut anmelden." else e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "Unbekannter Fehler."
            } finally {
                _loading.value = false
            }
        }
    }

    /** Lädt die Wochen-Stundenpläne nebenläufig und baut eine Karte dateNum → Unterrichts-Slots
     * (dedupliziert nach Startzeit, abgesagte und fachlose Einträge übersprungen). */
    private suspend fun buildDateMap(session: UserSession, weeks: List<String>): Map<Int, List<DaySlot>> = coroutineScope {
        val deferred = weeks.map { w ->
            async {
                try {
                    untisClient.timetable(session, session.studentId, w)
                } catch (e: AppError) {
                    if (e.isSessionExpired) throw e else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        val raw = mutableMapOf<Int, MutableMap<Int, DaySlot>>()
        for (entries in deferred.awaitAll()) {
            for (e in entries) {
                if (e.isCancelled) continue
                if (e.subjectName.isEmpty() && e.subjectLong.isEmpty()) continue
                val startMins = Fmt.minutes(e.startTime)
                val endMins = Fmt.minutes(e.endTime)
                // Parallele Fächer (gleicher Beginn) zählen als eine Stunde.
                val dayMap = raw.getOrPut(e.date) { mutableMapOf() }
                if (!dayMap.containsKey(startMins)) dayMap[startMins] = DaySlot(startMins, endMins)
            }
        }
        raw.mapValues { it.value.values.toList() }
    }
}
