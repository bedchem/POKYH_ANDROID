package dev.plattnericus.pokyh.ui.timetable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.widgets.WidgetDataBridge
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.shareIcs
import javax.inject.Inject
import kotlinx.coroutines.launch
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
    data class Data(val entries: List<TimetableEntry>) : WeekPageState
    data class Error(val message: String) : WeekPageState
}

data class TimetableUiState(
    val mode: TimetableMode = TimetableMode.WEEK,
    val weekOffset: Int = 0,
    val selectedDay: Int = 0,
    val exporting: Boolean = false,
)

/** Pager span, same "feels infinite" radius as iOS `pageSpan`. */
const val TIMETABLE_PAGE_SPAN = 40
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
    private val widgetDataBridge: WidgetDataBridge,
) : ViewModel() {

    private val _ui = MutableStateFlow(TimetableUiState())
    val ui: StateFlow<TimetableUiState> = _ui.asStateFlow()

    private val _pages = MutableStateFlow<Map<Int, WeekPageState>>(emptyMap())
    val pages: StateFlow<Map<Int, WeekPageState>> = _pages.asStateFlow()

    private val _detail = MutableStateFlow<MergedSlot?>(null)
    val detail: StateFlow<MergedSlot?> = _detail.asStateFlow()

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
        // `timetableHomeSignal` is bumped on every Timetable-tab (re-)selection (AppState.selectTab)
        // — jump back to "today", mirroring `.onChange(of: app.timetableHomeSignal)`.
        viewModelScope.launch {
            var seen = appState.timetableHomeSignal.value
            appState.timetableHomeSignal.collect { signal ->
                if (signal != seen) {
                    seen = signal
                    goToday()
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

    private suspend fun fetchWeek(offset: Int, silent: Boolean) {
        val session = appState.session.value ?: return
        if (!session.hasUntis) {
            _pages.update { it + (offset to WeekPageState.Data(emptyList())) }
            return
        }
        try {
            val entries = untisClient.timetable(session, session.studentId, mondayOf(offset).toString())
            _pages.update { it + (offset to WeekPageState.Data(entries)) }
            // Current week of the default account → widget stays fresh (WidgetBridge.publish).
            if (offset == 0 && appState.isDefaultAccountActive()) widgetDataBridge.publishTimetable(entries)
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

    fun retryWeek(offset: Int) = ensureWeek(offset, force = true)

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
