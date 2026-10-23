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
import dev.plattnericus.pokyh.data.model.AbsenceMeta
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.storage.OfflineStore
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.data.model.AbsenceReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.builtins.ListSerializer

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

/**
 * Missed lesson time, as the web shows it: the minutes are real clock time spent in lessons
 * (breaks are never counted), so they are shown in 60-minute hours — Mo 07:50-16:45 minus 1h 15m
 * of breaks is 7h 40m, not "9h 10m" in 50-minute units. exact = true -> "Xh Ym" (0m omitted);
 * false -> rounded hours "Xh".
 */
fun formatAbsenceMinutes(m: Int, exact: Boolean): String {
    if (!exact) return "${Math.round(m / 60.0)}h"
    val h = m / 60
    val min = m % 60
    return if (min == 0) "${h}h" else "${h}h ${min}m"
}

data class MonthGroup(val id: String, val label: String, val entries: List<AbsenceEntry>, val totalMinutes: Int)

/** Gruppiert nach Jahr-Monat des Startdatums, absteigend sortiert; "Oktober 2026" in der Sprache der App. */
fun groupAbsencesByMonth(entries: List<AbsenceEntry>, minutesMap: Map<Int, Int>): List<MonthGroup> {
    val months = dev.plattnericus.pokyh.core.i18n.I18n.s.common.months
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
        val month = (parts[1].toIntOrNull() ?: 1).coerceIn(1, 12)
        val total = es.sumOf { minutesMap[it.id] ?: (it.hours * 50) }
        val name = months[month - 1].replaceFirstChar { it.uppercase() }
        MonthGroup(id = key, label = "$name $year", entries = es, totalMinutes = total)
    }.sortedByDescending { it.id }
}

/**
 * The newest excuse message that covers this absence, if one was sent (the web's
 * `sentExcuseFor`). It counts when its span overlaps the absence, so a school shortening the
 * absence afterwards doesn't lose it.
 */
fun sentExcuseFor(a: AbsenceEntry, excuses: List<UntisClient.SentExcuse>): UntisClient.SentExcuse? {
    val span = dev.plattnericus.pokyh.data.untis.ExcuseMessage.span(a)
    var found: UntisClient.SentExcuse? = null
    for (e in excuses) {
        if (e.start < span.end && e.end > span.start && (found == null || e.sentAt > found.sentAt)) found = e
    }
    return found
}
/** Port of `AbsencesView`'s `@State`/`load()` (AbsencesView.swift). */
@HiltViewModel
class AbsencesViewModel @Inject constructor(
    private val appState: AppState,
    private val untisClient: UntisClient,
    private val offlineStore: OfflineStore,
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

    // ── Melden, entschuldigen, Entschuldigungsschreiben ─────────────────────
    // Like WebUntis: only who may report absences (guardians, students of age) reports and
    // excuses them, decided by WebUntis' own flag (UntisClient.canReportAbsence).

    private val _canReport = MutableStateFlow(false)
    val canReport: StateFlow<Boolean> = _canReport.asStateFlow()

    /** One open report or excuse sheet: WebUntis' reasons and presets, then the save. */
    data class FormState(
        val meta: AbsenceMeta? = null,
        val metaFailed: Boolean = false,
        /** Today's first and last lesson (HHMM), when WebUntis gives no preset range. */
        val todayRange: Pair<Int, Int>? = null,
        /** The class teachers an excuse goes to; null while loading. */
        val teachers: List<String>? = null,
        /** WebUntis wouldn't say who the class teachers are (the excuse may still go through). */
        val teachersFailed: Boolean = false,
        val saving: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
        /** What a sent excuse did: to whom, and whom it missed. */
        val excuse: UntisClient.ExcuseResult? = null,
    )

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form.asStateFlow()

    data class LetterState(val loading: Boolean = false, val error: String? = null)

    private val _letter = MutableStateFlow(LetterState())
    val letter: StateFlow<LetterState> = _letter.asStateFlow()

    // Excuses sent to the class teachers, from "Gesendet" (stored copy first), and those sent
    // just now; what "Gesendet" has wins, it knows the message to open.
    private val _excuses = MutableStateFlow<List<UntisClient.SentExcuse>>(emptyList())
    private val _sentNow = MutableStateFlow<List<UntisClient.SentExcuse>>(emptyList())
    val excuses: StateFlow<List<UntisClient.SentExcuse>> = combine(_excuses, _sentNow) { stored, now ->
        stored + now.filter { n -> stored.none { it.start == n.start && it.end == n.end } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // After the flows above: init blocks run in declaration order.
    init {
        checkCanReport()
        loadExcuses()
    }

    private fun checkCanReport() {
        val session = appState.session.value ?: return
        if (!session.hasUntis || appState.isOffline.value) return
        viewModelScope.launch {
            _canReport.value = runCatching { untisClient.canReportAbsence(session) }.getOrDefault(false)
        }
    }

    /** Not needed for the list itself, so it never holds it up; without it the buttons simply stay. */
    private fun loadExcuses() {
        val session = appState.session.value ?: return
        if (!session.hasUntis) return
        viewModelScope.launch {
            val key = "excuses-${session.studentId}"
            val serializer = ListSerializer(UntisClient.SentExcuse.serializer())
            runCatching { offlineStore.peek(key, serializer) }.getOrNull()?.let { _excuses.value = it.value }
            runCatching { offlineStore.load(key, serializer) { untisClient.sentExcuses(session) } }
                .getOrNull()?.let { _excuses.value = it.value }
        }
    }

    /** A fresh form: reasons and presets from WebUntis, today's lessons from the stored week. */
    fun openForm(forExcuse: Boolean = false) {
        val session = appState.session.value ?: return
        _form.value = FormState()
        viewModelScope.launch {
            val meta = runCatching { untisClient.absenceMeta(session) }.getOrNull()
            val today = dev.plattnericus.pokyh.core.util.todayLocalDate()
            val week = runCatching {
                offlineStore.peek(
                    key = "tt-${session.studentId}-${mondayOfWeek(today)}",
                    serializer = ListSerializer(TimetableEntry.serializer()),
                )?.value
            }.getOrNull().orEmpty()
            val lessons = week.filter { it.date == today.toYyyyMMdd() && !it.isCancelled }
            val range = if (lessons.isEmpty()) null else lessons.minOf { it.startTime } to lessons.maxOf { it.endTime }
            _form.value = _form.value.copy(meta = meta, metaFailed = meta == null, todayRange = range)
        }
        if (forExcuse) {
            viewModelScope.launch {
                val teachers = runCatching {
                    untisClient.messageRecipients(session).filter { it.isClassTeacher }.distinctBy { it.id }.map { it.name }
                }
                _form.value = _form.value.copy(
                    teachers = teachers.getOrNull() ?: emptyList(),
                    teachersFailed = teachers.isFailure,
                )
            }
        }
    }

    fun report(startDate: Int, startTime: Int, endDate: Int, endTime: Int, reasonId: Int, text: String) =
        save { session -> untisClient.reportAbsence(session, startDate, startTime, endDate, endTime, reasonId, text.trim()) }

    /**
     * "Entschuldigen": a message with the reason and the note to the class teachers, in the app's
     * language (see [UntisClient.excuseByMessage]). The sheet stays open on a partial send, so the
     * names it missed can be read.
     */
    fun excuse(absences: List<AbsenceEntry>, reason: AbsenceReason, text: String) {
        val session = appState.session.value ?: return
        if (_form.value.saving || _form.value.done) return
        _form.value = _form.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val result = untisClient.excuseByMessage(session, absences, reason, text.trim(), I18n.lang.value)
                _form.value = _form.value.copy(saving = false, done = true, excuse = result)
                result.excuse?.let { sent -> _sentNow.value = _sentNow.value + sent }
                // Saved in WebUntis instead (no message allowed): the absence itself changed.
                if (!result.viaMessage) load() else loadExcuses()
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _form.value = _form.value.copy(
                    saving = false,
                    error = if (e.isSessionExpired) I18n.s.common.sessionExpired else e.message,
                )
            } catch (e: Exception) {
                _form.value = _form.value.copy(saving = false, error = I18n.s.absences.exSendFailed)
            }
        }
    }

    /** Reports success only when WebUntis did, then reloads the list. */
    private fun save(write: suspend (UserSession) -> Unit) {
        val session = appState.session.value ?: return
        if (_form.value.saving || _form.value.done) return
        _form.value = _form.value.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                write(session)
                _form.value = _form.value.copy(saving = false, done = true)
                load()
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _form.value = _form.value.copy(
                    saving = false,
                    error = if (e.isSessionExpired) I18n.s.common.sessionExpired else e.message,
                )
            } catch (e: Exception) {
                _form.value = _form.value.copy(saving = false, error = I18n.s.common.failedLater)
            }
        }
    }

    fun formError(message: String) {
        _form.value = _form.value.copy(error = message)
    }

    /** The year's excuse letter for its open absences, as WebUntis prints it; [onFile] opens it. */
    fun excuseLetter(onFile: (UntisClient.DownloadedAttachment) -> Unit) {
        val session = appState.session.value ?: return
        if (_letter.value.loading) return
        val y = _year.value
        val open = _absences.value.filter { !it.isExcused }.map { it.id }
        _letter.value = LetterState(loading = true)
        viewModelScope.launch {
            try {
                val file = untisClient.excuseLetter(session, y * 10000 + 901, (y + 1) * 10000 + 630, open)
                _letter.value = LetterState()
                onFile(file)
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _letter.value = LetterState(error = e.message)
            } catch (e: Exception) {
                _letter.value = LetterState(error = I18n.s.absences.letterFailed)
            }
        }
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

    /** "Im Stundenplan ansehen": switch to the Stundenplan tab at the week of [dateNum]. */
    fun openInTimetable(dateNum: Int) = appState.openTimetableAt(dateNum)

    private fun load() {
        val session = appState.session.value ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val y = _year.value
                // On screen straight away from the last copy; the request replaces it when it lands.
                runCatching {
                    offlineStore.peek(
                        key = "absences-${session.studentId}-$y",
                        serializer = ListSerializer(AbsenceEntry.serializer()),
                    )
                }.getOrNull()?.let { cached ->
                    if (cached.value.isNotEmpty()) {
                        _absences.value = cached.value
                        _loading.value = false
                    }
                }
                val parsed = offlineStore.load(
                    key = "absences-${session.studentId}-$y",
                    serializer = ListSerializer(AbsenceEntry.serializer()),
                ) {
                    untisClient.absences(
                        session,
                        session.studentId,
                        SchoolDates.yearStart(y).toString(),
                        SchoolDates.yearEnd(y).toString(),
                    )
                }.value
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
                _error.value = if (e.isSessionExpired) dev.plattnericus.pokyh.core.i18n.I18n.s.common.sessionExpired else e.message
            } catch (e: Exception) {
                _error.value = e.message ?: dev.plattnericus.pokyh.core.i18n.I18n.s.common.unknownError
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
                    // The Stundenplan tab's per-week key — offline, stored weeks still count.
                    offlineStore.load(
                        key = "tt-${session.studentId}-$w",
                        serializer = ListSerializer(TimetableEntry.serializer()),
                    ) { untisClient.timetable(session, session.studentId, w) }.value
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
