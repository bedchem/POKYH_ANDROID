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

// The missed minutes and the rate are worked out as on the web — see [AbsenceCalc].

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
        // What WebUntis counted; nothing where it didn't say (as the rows and the web).
        val total = es.sumOf { minutesMap[it.id] ?: 0 }
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
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

    /** The missed minutes and the rate, as the web works them out ([AbsenceCalc]). */
    private val _figures = MutableStateFlow(AbsenceCalc.figures(emptyList(), null, null, SchoolDates.currentSchoolYear))
    val figures: StateFlow<AbsenceCalc.Figures> = _figures.asStateFlow()

    /** The timetable's weeks are being loaded for the rate. */
    private val _calcBusy = MutableStateFlow(false)
    val calcBusy: StateFlow<Boolean> = _calcBusy.asStateFlow()

    /** The rate against the lessons so far (false) or every lesson of the school year (true). */
    private val _wholeYear = MutableStateFlow(false)
    val wholeYear: StateFlow<Boolean> = _wholeYear.asStateFlow()

    private var calendar: AbsenceCalc.LessonCalendar? = null
    private var official: List<AbsenceCalc.OfficialMinute>? = null

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
        /** The class teacher an excuse goes to (one, never the deputy); null while loading. */
        val teachers: List<String>? = null,
        /** WebUntis wouldn't say who the class teachers are (the excuse may still go through). */
        val teachersFailed: Boolean = false,
        /** WebUntis names class teachers, but not which of them is the class teacher proper. */
        val teacherUnclear: Boolean = false,
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
            // Without the dialog's meta, the reasons alone (web: a reasons-only request).
            val fallback = if (meta == null) runCatching { untisClient.absenceReasons(session) }.getOrDefault(emptyList()) else emptyList()
            val shownMeta = meta ?: fallback.takeIf { it.isNotEmpty() }?.let { dev.plattnericus.pokyh.data.model.AbsenceMeta(reasons = it, defaultReasonId = null, defaultDate = null, defaultStartTime = null, defaultEndTime = null) }
            _form.value = _form.value.copy(meta = shownMeta, metaFailed = shownMeta == null, todayRange = range)
        }
        if (forExcuse) {
            viewModelScope.launch {
                val classTeachers = runCatching {
                    untisClient.messageRecipients(session).filter { it.isClassTeacher }.distinctBy { it.id }
                }
                val main = classTeachers.getOrNull()?.let { untisClient.mainClassTeacher(session, it) }
                _form.value = _form.value.copy(
                    teachers = listOfNotNull(main?.name),
                    teachersFailed = classTeachers.isFailure,
                    teacherUnclear = main == null && !classTeachers.getOrNull().isNullOrEmpty(),
                )
            }
        }
    }

    /** Who the absence is reported for (the "Für" row). */
    val personName: String? get() = appState.session.value?.personName

    /** A day's lessons for "Aus Stundenplan wählen": the stored week first, else WebUntis. */
    suspend fun lessonsOn(date: java.time.LocalDate): List<TimetableEntry> {
        val session = appState.session.value ?: return emptyList()
        val monday = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val num = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth
        val week = runCatching {
            offlineStore.load("tt-${session.studentId}-$monday", ListSerializer(TimetableEntry.serializer())) {
                untisClient.timetable(session, session.studentId, monday.toString())
            }.value
        }.getOrDefault(emptyList())
        return week.filter { it.date == num }.distinctBy { it.startTime }.sortedBy { it.startTime }
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
        _wholeYear.value = false
        load()
    }

    fun retry() = load()

    /** "Im Stundenplan ansehen": switch to the Stundenplan tab at the week of [dateNum]. */
    fun openInTimetable(dateNum: Int) = appState.openTimetableAt(dateNum)

    /** A year that is over is the same either way. */
    fun canWhole(): Boolean = _year.value == SchoolDates.currentSchoolYear

    /** The lesson minutes of the whole school year (to June), once every week of it is kept. */
    fun wholeYearMinutes(): Int? = calendar?.takeIf { AbsenceCalc.hasWholeYear(it, _year.value, whole = true) }
        ?.let { AbsenceCalc.possibleMinutes(it, _year.value, whole = true) }?.takeIf { it > 0 }

    /** The lesson minutes so far (to today). */
    fun soFarMinutes(): Int? = calendar?.takeIf { AbsenceCalc.hasWholeYear(it, _year.value, whole = false) }
        ?.let { AbsenceCalc.possibleMinutes(it, _year.value, whole = false) }?.takeIf { it > 0 }

    fun setWholeYear(whole: Boolean) {
        _wholeYear.value = whole
        recompute()
        // The weeks still to come are only loaded for the whole year.
        if (whole && !AbsenceCalc.hasWholeYear(calendar, _year.value, whole = true)) refreshCalendar()
    }

    private fun recompute() {
        _figures.value = AbsenceCalc.figures(_absences.value, calendar, official, _year.value, whole = _wholeYear.value && canWhole())
    }

    /** The weeks of the timetable not kept yet, or due again; a few at a time. */
    fun refreshCalendar() {
        val session = appState.session.value ?: return
        val y = _year.value
        viewModelScope.launch {
            _calcBusy.value = true
            try {
                calendar = AbsenceCalc.refreshCalendar(
                    context = context,
                    studentId = session.studentId,
                    year = y,
                    fetchWeek = { monday -> untisClient.timetable(session, session.studentId, monday) },
                    extraWeeks = AbsenceCalc.absenceWeeks(_absences.value),
                    // The weeks still to come as well, from the start: "Ganzes Jahr" is then
                    // ready the moment it is picked.
                    whole = canWhole(),
                )
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
            } finally {
                _calcBusy.value = false
                if (y == _year.value) recompute()
            }
        }
    }

    private fun loadOfficial(session: UserSession, y: Int) {
        val key = "abstimes-${session.studentId}-$y"
        val serializer = ListSerializer(AbsenceCalc.OfficialMinute.serializer())
        viewModelScope.launch {
            runCatching { offlineStore.peek(key, serializer) }.getOrNull()?.let { official = it.value; recompute() }
            runCatching {
                offlineStore.load(key, serializer) {
                    untisClient.absenceTimes(session, session.studentId, SchoolDates.yearStart(y).toString(), SchoolDates.yearEnd(y).toString())
                }
            }.getOrNull()?.let { if (y == _year.value) { official = it.value; recompute() } }
        }
    }

    private fun load() {
        val session = appState.session.value ?: return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val y = _year.value
                calendar = AbsenceCalc.readCalendar(context, session.studentId, y)
                official = null
                loadOfficial(session, y)
                // On screen straight away from the last copy; the request replaces it when it lands.
                runCatching {
                    offlineStore.peek(
                        key = "absences-${session.studentId}-$y",
                        serializer = ListSerializer(AbsenceEntry.serializer()),
                    )
                }.getOrNull()?.let { cached ->
                    if (cached.value.isNotEmpty()) {
                        _absences.value = cached.value
                        recompute()
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
                recompute()
                _loading.value = false
                // The rate's whole: the year's lessons from the timetable, kept per week.
                refreshCalendar()
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
}
