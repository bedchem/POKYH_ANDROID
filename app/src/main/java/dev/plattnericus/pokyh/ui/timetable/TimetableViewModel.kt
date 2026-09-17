package dev.plattnericus.pokyh.ui.timetable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.core.util.mondayOfWeek
import dev.plattnericus.pokyh.core.widgets.WidgetDataBridge
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.storage.OfflineStore
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.shareIcs
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** `TimetableView.Mode` (Woche/Tag segmented control). */
enum class TimetableMode(val label: String) { WEEK("Woche"), DAY("Tag") }

/** One week page's load state — mirrors `WeekTimetablePage`'s `entries/loading/error` triple,
 * but cached per [weekOffset] here so paging never re-shows a spinner for an already-loaded week. */
sealed interface WeekPageState {
    data object Loading : WeekPageState
    data class Data(
        val entries: List<TimetableEntry>,
        /** Epoch millis this copy was produced — what the "Stand …" label reads. */
        val savedAt: Long = 0L,
        /** True when WebUntis couldn't be reached and this came off the disk. */
        val stale: Boolean = false,
    ) : WeekPageState
    data class Error(val message: String) : WeekPageState
}

data class TimetableUiState(
    val mode: TimetableMode = TimetableMode.WEEK,
    val weekOffset: Int = 0,
    val selectedDay: Int = 0,
    val exporting: Boolean = false,
)

/** Pager span in weeks. Wide enough that every school year in [SchoolDates.availableYears] is
 * reachable from the year picker (four years back ≈ 210 weeks). Pages are lazy, so it costs nothing. */
const val TIMETABLE_PAGE_SPAN = 220
const val TIMETABLE_PAGE_COUNT = TIMETABLE_PAGE_SPAN * 2 + 1

private val DAY_LABELS = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa")
private val MONTH_ABBR = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

/**
 * Port of `TimetableView` + `WeekTimetablePage`'s state/data logic (TimetableView.swift). The
 * week-page cache + preload-radius pattern is reproduced with a plain in-memory map here since
 * [UntisClient] (unlike its iOS counterpart) has no built-in per-session timetable cache.
 */
@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val appState: AppState,
    private val untisClient: UntisClient,
    private val backendClient: BackendClient,
    private val widgetDataBridge: WidgetDataBridge,
    private val offlineStore: OfflineStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(TimetableUiState())
    val ui: StateFlow<TimetableUiState> = _ui.asStateFlow()

    private val _pages = MutableStateFlow<Map<Int, WeekPageState>>(emptyMap())
    val pages: StateFlow<Map<Int, WeekPageState>> = _pages.asStateFlow()

    /**
     * This student's Abwesenheiten per school year, for the Vorentschuldigung / Entschuldigt /
     * Unentschuldigt overlay. Loaded here on its own — the Abwesenheiten tab never has to be opened
     * first — and shares that tab's offline key, so either screen refreshes the other's copy.
     */
    private val _absences = MutableStateFlow<Map<Int, List<AbsenceEntry>>>(emptyMap())
    val absences: StateFlow<Map<Int, List<AbsenceEntry>>> = _absences.asStateFlow()

    private val _detail = MutableStateFlow<MergedSlot?>(null)
    val detail: StateFlow<MergedSlot?> = _detail.asStateFlow()

    /**
     * The subject keys the POKYH backend has a header image for.
     *
     * Held as a set of keys rather than a set of URLs because the backend has no placeholder: a
     * request for a subject it has never seen is an error, not a default image. Knowing which
     * keys exist is what lets the detail sheet choose between a photo and its monogram fallback
     * *before* it asks for anything — same contract the web frontend works to.
     */
    private val _subjectImageKeys = MutableStateFlow<Set<String>>(emptySet())
    val subjectImageKeys: StateFlow<Set<String>> = _subjectImageKeys.asStateFlow()

    /**
     * The image URL for a subject, or null when the backend has none for it.
     *
     * Paired with [subjectImageHeaders] — the route rejects the key as a query parameter, so the
     * URL alone will not load.
     */
    fun subjectImageUrl(subjectLong: String, subjectName: String): String? {
        val key = backendClient.subjectImageKeyOf(subjectLong, subjectName)
        if (key.isEmpty() || key !in _subjectImageKeys.value) return null
        return backendClient.subjectImageUrl(key)
    }

    /** The headers a subject-image request has to carry. */
    fun subjectImageHeaders(): Pair<String, String> =
        backendClient.apiKeyHeaderName to backendClient.apiKeyHeaderValue

    /** Monday of the CURRENT calendar week (ISO-8601, Monday-first) — every [weekOffset] is
     * relative to this, exactly like iOS's `thisMonday`. */
    private val thisMonday: LocalDate by lazy {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber - 1))
    }

    init {
        selectInitialDay()
        ensureWeek(0)
        prefetchAdjacent(0)
        viewModelScope.launch { _subjectImageKeys.value = backendClient.subjectImageKeys() }
        loadWeekAbsences(0)
        loadAbsences(SchoolDates.currentSchoolYear)
        // "Im Stundenplan ansehen" from the Abwesenheiten screen: go to that date's week and day.
        viewModelScope.launch {
            appState.timetableJump.collect { dateNum ->
                if (dateNum != null) {
                    appState.consumeTimetableJump()
                    goToDate(dateNum)
                }
            }
        }
        // Coming back to the app re-reads them, so an absence excused in the meantime shows as
        // "Entschuldigt" instead of "Unentschuldigt".
        viewModelScope.launch {
            var seen = appState.resumeSignal.value
            appState.resumeSignal.collect { signal ->
                if (signal != seen) {
                    seen = signal
                    loadAbsences(schoolYearOf(_ui.value.weekOffset))
                }
            }
        }
        // `timetableHomeSignal` is bumped on every Timetable-tab (re-)selection (AppState.selectTab)
        // — jump back to "today", mirroring `.onChange(of: app.timetableHomeSignal)`.
        viewModelScope.launch {
            var seen = appState.timetableHomeSignal.value
            appState.timetableHomeSignal.collect { signal ->
                if (signal != seen) {
                    seen = signal
                    goToday()
                    // Back on the tab, e.g. after excusing an absence in the Abwesenheiten screen.
                    loadAbsences(SchoolDates.currentSchoolYear)
                }
            }
        }
    }

    // ── Date helpers ─────────────────────────────────────────────────────────

    fun mondayOf(offset: Int): LocalDate = thisMonday.plus(DatePeriod(days = offset * 7))
    fun dateOf(offset: Int, dayIndex: Int): LocalDate = mondayOf(offset).plus(DatePeriod(days = dayIndex))
    fun dateNumOf(date: LocalDate): Int = date.year * 10000 + date.monthNumber * 100 + date.dayOfMonth
    fun dateNumOf(offset: Int, dayIndex: Int): Int = dateNumOf(dateOf(offset, dayIndex))
    fun todayDateNum(): Int = dateNumOf(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val dayLabels: List<String> get() = DAY_LABELS

    fun entriesForDay(entries: List<TimetableEntry>, offset: Int, dayIndex: Int): List<TimetableEntry> {
        val num = dateNumOf(offset, dayIndex)
        return entries.filter { it.date == num }
    }

    fun weekNumber(offset: Int): Int = isoWeekNumber(mondayOf(offset))

    private fun isoWeekNumber(date: LocalDate): Int {
        val dow = date.dayOfWeek.isoDayNumber
        val thursday = date.plus(DatePeriod(days = 4 - dow))
        val yearStart = LocalDate(thursday.year, 1, 1)
        val dayOfYear = thursday.toEpochDays() - yearStart.toEpochDays() + 1
        return ((dayOfYear - 1) / 7 + 1).toInt()
    }

    /** The school year (starting 1 September) the week at [offset] belongs to — Thursday decides,
     * like the ISO week number, so a week straddling 1 September counts toward the new year. */
    fun schoolYearOf(offset: Int): Int {
        val thursday = dateOf(offset, 3)
        return if (thursday.monthNumber >= 9) thursday.year else thursday.year - 1
    }

    val availableSchoolYears: List<Int> get() = SchoolDates.availableYears

    /** Year picker: the current school year goes back to today, any other one to its first week. */
    fun selectSchoolYear(year: Int) {
        if (year == SchoolDates.currentSchoolYear) {
            goToday()
            return
        }
        var start = LocalDate(year, 9, 1)
        if (start.dayOfWeek.isoDayNumber >= 6) start = start.plus(DatePeriod(days = 8 - start.dayOfWeek.isoDayNumber))
        val offset = ((mondayOfWeek(start).toEpochDays() - thisMonday.toEpochDays()) / 7).toInt()
        _ui.update { it.copy(selectedDay = 0) }
        setWeekOffset(offset.coerceIn(-TIMETABLE_PAGE_SPAN, TIMETABLE_PAGE_SPAN))
    }

    /** Shows the week containing [dateNum] (yyyyMMdd) with that weekday selected for day mode. */
    fun goToDate(dateNum: Int) {
        val date = runCatching { LocalDate(dateNum / 10000, (dateNum / 100) % 100, dateNum % 100) }.getOrNull() ?: return
        val offset = ((mondayOfWeek(date).toEpochDays() - thisMonday.toEpochDays()) / 7).toInt()
            .coerceIn(-TIMETABLE_PAGE_SPAN, TIMETABLE_PAGE_SPAN)
        _ui.update { it.copy(selectedDay = (date.dayOfWeek.isoDayNumber - 1).coerceIn(0, 5)) }
        setWeekOffset(offset)
    }

    fun rangeText(offset: Int): String {
        val start = mondayOf(offset)
        val end = dateOf(offset, 5)
        val startStr = "${start.dayOfMonth}. ${MONTH_ABBR[start.monthNumber - 1]}"
        val endStr = "${end.dayOfMonth}. ${MONTH_ABBR[end.monthNumber - 1]} ${end.year}"
        return "$startStr \u2013 $endStr"
    }

    private fun selectInitialDay() {
        val todayNum = todayDateNum()
        val idx = (0..5).firstOrNull { dateNumOf(0, it) == todayNum } ?: 0
        _ui.update { it.copy(selectedDay = idx) }
    }

    // ── Week paging / cache ──────────────────────────────────────────────────

    /** Called when a pager page becomes the current page — loads it (spinner only if not
     * cached yet) and refreshes the header's [TimetableUiState.weekOffset]. */
    fun onWeekPageVisible(offset: Int) {
        _ui.update { it.copy(weekOffset = offset) }
        val year = schoolYearOf(offset)
        loadWeekAbsences(offset)
        if (year !in _absences.value) loadAbsences(year)
        ensureWeek(offset)
        prefetchAdjacent(offset)
    }

    private fun ensureWeek(offset: Int, force: Boolean = false) {
        val cached = _pages.value[offset]
        if (cached is WeekPageState.Data && !force) {
            // Already have data → revalidate silently in the background (no spinner flash).
            viewModelScope.launch { fetchWeek(offset, silent = true) }
            return
        }
        _pages.update { it + (offset to WeekPageState.Loading) }
        viewModelScope.launch { fetchWeek(offset, silent = false) }
    }

    /** Preload radius 2 around [center] → a 5-week window is always cached, matching iOS's
     * `preloadRadius`/`prefetchAdjacent`. Only fetches weeks not already cached. */
    private fun prefetchAdjacent(center: Int) {
        for (d in -2..2) {
            if (d == 0) continue
            val off = center + d
            if (off < -TIMETABLE_PAGE_SPAN || off > TIMETABLE_PAGE_SPAN) continue
            if (_pages.value[off] == null) {
                _pages.update { it + (off to WeekPageState.Loading) }
                viewModelScope.launch { fetchWeek(off, silent = false) }
            }
        }
    }

    /** `tt-<studentId>-<monday>` — one file per week per account, so switching accounts or
     * paging back never serves someone else's week. */
    private fun weekCacheKey(studentId: Int, offset: Int) = "tt-$studentId-${mondayOf(offset)}"

    private suspend fun fetchWeek(offset: Int, silent: Boolean) {
        val session = appState.session.value ?: return
        if (!session.hasUntis) {
            _pages.update { it + (offset to WeekPageState.Data(emptyList())) }
            return
        }
        val key = weekCacheKey(session.studentId, offset)
        try {
            val result = offlineStore.load(key, ListSerializer(TimetableEntry.serializer())) {
                untisClient.timetable(session, session.studentId, mondayOf(offset).toString())
            }
            val entries = result.value
            _pages.update { it + (offset to WeekPageState.Data(entries, result.savedAt, result.stale)) }
            // Only a live answer is worth telling the rest of the app about: reporting subjects
            // from a restored week would ask the backend to generate images it was already asked
            // for, and republishing it to the widget would overwrite a fresher snapshot with an
            // older one just because this screen happened to open offline.
            if (!result.stale) {
                reportSubjects(entries)
                // Current week of the default account → widget stays fresh (WidgetBridge.publish).
                if (offset == 0 && appState.isDefaultAccountActive()) widgetDataBridge.publishTimetable(entries)
            }
        } catch (e: Exception) {
            if (e is AppError && e.isSessionExpired) {
                appState.handleSessionExpired()
                return
            }
            if (!silent) {
                val message = (e as? AppError)?.message ?: e.message ?: "Unbekannter Fehler."
                _pages.update { it + (offset to WeekPageState.Error(message)) }
            }
        }
    }

    /**
     * Tells the backend which subjects this timetable contains, so it can generate images for any
     * it doesn't have yet, then re-reads the key list in case it just gained some.
     *
     * Fire-and-forget, and [BackendClient.reportSubjects] itself only acts once per process — it
     * is a hint to the server, and nothing on screen waits for it.
     */
    private fun reportSubjects(entries: List<TimetableEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            backendClient.reportSubjects(entries.map { it.subjectName to it.subjectLong })
            _subjectImageKeys.value = backendClient.subjectImageKeys()
        }
    }

    fun retryWeek(offset: Int) = ensureWeek(offset, force = true)

    /**
     * The Abwesenheiten of one week, straight from WebUntis.
     *
     * A school year walks 100 entries per page and takes noticeably longer than the week of
     * timetable it is drawn over, so the visible week is fetched on its own: one page, landing with
     * the lessons. [loadAbsences] then fills in the rest of the year behind it.
     */
    private fun loadWeekAbsences(offset: Int) {
        val session = appState.session.value ?: return
        if (!session.hasUntis) return
        val year = schoolYearOf(offset)
        viewModelScope.launch {
            val monday = mondayOf(offset)
            val saturday = monday.plus(DatePeriod(days = 5))
            val list = runCatching {
                untisClient.absences(session, session.studentId, dateNumOf(monday).toString(), dateNumOf(saturday).toString())
            }.getOrNull() ?: return@launch
            _absences.update { current ->
                val merged = LinkedHashMap<Int, AbsenceEntry>()
                current[year].orEmpty().forEach { merged[it.id] = it }
                list.forEach { merged[it.id] = it }
                current + (year to merged.values.toList())
            }
        }
    }

    private fun loadAbsences(year: Int) {
        val session = appState.session.value ?: return
        if (!session.hasUntis) return
        val key = "absences-${session.studentId}-$year"
        viewModelScope.launch {
            // The stored copy first: the Abwesenheiten request walks a whole school year page by
            // page, so waiting for it would leave the grid without its overlay for a second or two
            // every time. It is replaced by the fresh answer below as soon as that lands.
            if (year !in _absences.value) {
                runCatching { offlineStore.peek(key, ListSerializer(AbsenceEntry.serializer())) }
                    .getOrNull()
                    ?.let { cached -> _absences.update { it + (year to cached.value) } }
            }
            try {
                val list = offlineStore.load(
                    key = key,
                    serializer = ListSerializer(AbsenceEntry.serializer()),
                ) {
                    untisClient.absences(
                        session,
                        session.studentId,
                        SchoolDates.yearStart(year).toString(),
                        SchoolDates.yearEnd(year).toString(),
                    )
                }.value
                _absences.update { it + (year to list) }
            } catch (_: Exception) {
                // No overlay rather than a wrong one; the timetable itself reports a session expiry.
            }
        }
    }

    // ── Mode / navigation ────────────────────────────────────────────────────

    fun setMode(mode: TimetableMode) = _ui.update { it.copy(mode = mode) }

    fun goToday() {
        _ui.update { it.copy(weekOffset = 0) }
        selectInitialDay()
        ensureWeek(0)
        prefetchAdjacent(0)
    }

    /** Day-mode swipe/chevron forward — wraps into the next week at Saturday. */
    fun goNextDay() {
        val s = _ui.value
        if (s.selectedDay < 5) {
            _ui.update { it.copy(selectedDay = it.selectedDay + 1) }
        } else {
            _ui.update { it.copy(weekOffset = it.weekOffset + 1, selectedDay = 0) }
            ensureWeek(_ui.value.weekOffset)
            prefetchAdjacent(_ui.value.weekOffset)
        }
    }

    /** Day-mode swipe/chevron backward — wraps into the previous week at Monday. */
    fun goPrevDay() {
        val s = _ui.value
        if (s.selectedDay > 0) {
            _ui.update { it.copy(selectedDay = it.selectedDay - 1) }
        } else {
            _ui.update { it.copy(weekOffset = it.weekOffset - 1, selectedDay = 5) }
            ensureWeek(_ui.value.weekOffset)
            prefetchAdjacent(_ui.value.weekOffset)
        }
    }

    fun selectDay(index: Int) = _ui.update { it.copy(selectedDay = index) }

    /** Week-mode chevrons — used directly by the header when the pager itself owns the swipe. */
    fun setWeekOffset(offset: Int) {
        _ui.update { it.copy(weekOffset = offset) }
        ensureWeek(offset)
        prefetchAdjacent(offset)
    }

    // ── Detail sheet ─────────────────────────────────────────────────────────

    fun showDetail(slot: MergedSlot) { _detail.value = slot }
    fun dismissDetail() { _detail.value = null }

    // ── .ics export ──────────────────────────────────────────────────────────

    /** "Diese Woche exportieren" — exports the week currently shown in the header. */
    fun exportWeek(context: Context) {
        val entries = (_pages.value[_ui.value.weekOffset] as? WeekPageState.Data)?.entries.orEmpty()
        if (entries.isEmpty()) return
        shareIcs(context, "pokyh_stundenplan.ics", buildIcs(entries, "POKYH Stundenplan"))
    }

    /** "Prüfungen exportieren" — [UntisClient] has no dedicated `upcomingExams` endpoint (unlike
     * iOS's `UntisClient.shared.upcomingExams`), so this scans the next [EXAM_LOOKAHEAD_WEEKS]
     * weeks' timetables (reusing the cache where possible) and collects exam entries from today on. */
    fun exportExams(context: Context) {
        val session = appState.session.value ?: return
        viewModelScope.launch {
            _ui.update { it.copy(exporting = true) }
            val todayNum = todayDateNum()
            val exams = mutableListOf<TimetableEntry>()
            for (w in 0..EXAM_LOOKAHEAD_WEEKS) {
                val cached = _pages.value[w] as? WeekPageState.Data
                val weekEntries = cached?.entries
                    ?: runCatching { untisClient.timetable(session, session.studentId, mondayOf(w).toString()) }.getOrNull()
                    ?: emptyList()
                exams += weekEntries.filter { it.isExam && it.date >= todayNum }
            }
            _ui.update { it.copy(exporting = false) }
            if (exams.isNotEmpty()) shareIcs(context, "pokyh_pruefungen.ics", buildIcs(exams, "POKYH Prüfungen"))
        }
    }

    private companion object {
        const val EXAM_LOOKAHEAD_WEEKS = 12
    }
}

// ── Minimal .ics builder (VCALENDAR/VEVENT per lesson) ──────────────────────

private fun icsDateTime(dateYyyyMMdd: Int, timeHHmm: Int): String {
    val y = dateYyyyMMdd / 10000
    val m = (dateYyyyMMdd / 100) % 100
    val d = dateYyyyMMdd % 100
    val h = timeHHmm / 100
    val mi = timeHHmm % 100
    return "%04d%02d%02dT%02d%02d00".format(y, m, d, h, mi)
}

private fun icsEscape(s: String): String =
    s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

private fun buildIcs(entries: List<TimetableEntry>, calendarName: String): String {
    val sb = StringBuilder()
    sb.append("BEGIN:VCALENDAR\r\n")
    sb.append("VERSION:2.0\r\n")
    sb.append("PRODID:-//POKYH//Android//DE\r\n")
    sb.append("CALSCALE:GREGORIAN\r\n")
    sb.append("X-WR-CALNAME:${icsEscape(calendarName)}\r\n")
    for (e in entries.sortedWith(compareBy({ it.date }, { it.startTime }))) {
        val summary = e.subjectName.ifEmpty { e.note ?: "Stunde" }
        sb.append("BEGIN:VEVENT\r\n")
        sb.append("UID:${e.id}-${e.date}-${e.startTime}@pokyh.com\r\n")
        sb.append("DTSTART:${icsDateTime(e.date, e.startTime)}\r\n")
        sb.append("DTEND:${icsDateTime(e.date, e.endTime)}\r\n")
        sb.append("SUMMARY:${icsEscape(summary)}\r\n")
        if (e.roomName.isNotEmpty()) sb.append("LOCATION:${icsEscape(e.roomName)}\r\n")
        val descParts = listOfNotNull(
            e.teacherName.takeIf { it.isNotEmpty() }?.let { "Lehrer: $it" },
            e.examDescription?.takeIf { it.isNotEmpty() },
            e.note?.takeIf { it.isNotEmpty() },
        )
        if (descParts.isNotEmpty()) sb.append("DESCRIPTION:${icsEscape(descParts.joinToString("\\n"))}\r\n")
        sb.append("END:VEVENT\r\n")
    }
    sb.append("END:VCALENDAR\r\n")
    return sb.toString()
}
