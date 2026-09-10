package dev.plattnericus.pokyh.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.core.util.isoWeekNumber
import dev.plattnericus.pokyh.core.util.mondayOfWeek
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.TimetableSlots
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.navigation.AppTab
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * HomeView.swift, ported. No repository layer — timetable/grades/exams load only with a linked
 * WebUntis account ([UserSession.hasUntis]); the mensa menu always loads from the POKYH backend.
 * Mirrors the Swift `loadAll()`'s `async let` fan-out via [coroutineScope] + [async].
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appState: AppState,
    private val untisClient: UntisClient,
    private val backendClient: BackendClient,
) : ViewModel() {

    /** `HomeView.RecentGrade` — a flattened, most-recent-first grade entry for the "Zuletzt
     * eingetragen" section. */
    data class RecentGrade(val id: Int, val subject: String, val value: Double, val date: Int)

    data class UiState(
        val session: UserSession? = null,
        val loadingToday: Boolean = true,
        val todaySlots: List<MergedSlot> = emptyList(),
        val loadingGrades: Boolean = true,
        val recentGrades: List<RecentGrade> = emptyList(),
        val loadingMensa: Boolean = true,
        val todayDishes: List<Dish> = emptyList(),
        val dishDayLabel: String = "",
        val examsLoaded: Boolean = false,
        val nextExam: TimetableEntry? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState(session = appState.session.value))
    val uiState: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { appState.session.collect { s -> _state.update { it.copy(session = s) } } }
        viewModelScope.launch { loadAll() }
        // Silently re-check on every background→foreground return (AppState.resumeSignal) — a
        // WebUntis session that expired while backgrounded surfaces via loadToday's
        // AppError.isSessionExpired catch instead of leaving stale data on screen.
        viewModelScope.launch {
            var seen = appState.resumeSignal.value
            appState.resumeSignal.collect { signal ->
                if (signal != seen) {
                    seen = signal
                    loadAll()
                }
            }
        }
    }

    /** Bound to [dev.plattnericus.pokyh.ui.components.ErrorStateView]'s retry action. */
    fun retry() {
        viewModelScope.launch { loadAll() }
    }

    /** Shortcuts card "Noten" — switches the bottom tab instead of pushing a route (SchoolHubViewModel does the same). */
    fun selectGradesTab() = appState.selectTab(AppTab.Grades)

    /** Mensa block tap — switches to the Mensa tab instead of pushing a route. */
    fun selectMensaTab() = appState.selectTab(AppTab.Mensa)

    private suspend fun loadAll() {
        _state.update { it.copy(error = null) }
        val session = appState.session.value
        try {
            coroutineScope {
                // Mensa kommt aus dem Backend -> immer; Stundenplan/Noten/Prüfungen nur mit
                // verknüpftem WebUntis-Konto.
                val mensaJob = async { loadMensa() }
                if (session?.hasUntis == true) {
                    val todayJob = async { loadToday(session) }
                    val gradesJob = async { loadGrades(session) }
                    val examsJob = async { loadExams(session) }
                    todayJob.await()
                    gradesJob.await()
                    examsJob.await()
                } else {
                    _state.update { it.copy(loadingToday = false, loadingGrades = false, examsLoaded = true) }
                }
                mensaJob.await()
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Unbekannter Fehler.") }
        }
    }

    private suspend fun loadToday(session: UserSession) {
        _state.update { it.copy(loadingToday = true) }
        try {
            val monday = SchoolDates.mondayIso()
            val all = untisClient.timetable(session, session.studentId, monday)
            val today = SchoolDates.todayNum()
            val slots = TimetableSlots.buildSlots(all.filter { it.date == today })
            _state.update { it.copy(todaySlots = slots, loadingToday = false) }
        } catch (e: AppError) {
            if (e.isSessionExpired) appState.handleSessionExpired()
            _state.update { it.copy(loadingToday = false) }
        } catch (e: Exception) {
            _state.update { it.copy(loadingToday = false) }
        }
    }

    private suspend fun loadGrades(session: UserSession) {
        _state.update { it.copy(loadingGrades = true) }
        try {
            val subjects = untisClient.grades(session, session.studentId, null)
            val items = subjects.flatMap { subj ->
                subj.grades.filter { it.markDisplayValue > 0 }
                    .map { g -> RecentGrade(id = g.id, subject = subj.subjectName, value = g.markDisplayValue, date = g.date) }
            }.sortedByDescending { it.id }.take(3)
            _state.update { it.copy(recentGrades = items, loadingGrades = false) }
        } catch (e: Exception) {
            _state.update { it.copy(loadingGrades = false) }
        }
    }

    /** No dedicated `upcomingExams` endpoint on the Android client — samples the next six weeks'
     * timetables in parallel and keeps the earliest `isExam` entry, mirroring what
     * `UntisClient.swift`'s `upcomingExams` (start = today, end = +40 days) resolves to. */
    private suspend fun loadExams(session: UserSession) {
        try {
            val today = SchoolDates.todayNum()
            val startMonday = mondayOfWeek(todayLocalDate())
            val weekStarts = (0..5).map { startMonday.plus(it * 7, DateTimeUnit.DAY) }
            val entries = coroutineScope {
                weekStarts.map { monday ->
                    async {
                        runCatching { untisClient.timetable(session, session.studentId, monday.toString()) }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll()
            }.flatten()
            val next = entries
                .filter { it.isExam && it.date >= today }
                .sortedWith(compareBy({ it.date }, { it.startTime }))
                .firstOrNull()
            _state.update { it.copy(nextExam = next, examsLoaded = true) }
        } catch (e: Exception) {
            _state.update { it.copy(examsLoaded = true) }
        }
    }

    private suspend fun loadMensa() {
        _state.update { it.copy(loadingMensa = true) }
        try {
            val dishes = backendClient.dishes()
            val day = mensaDays(dishes).firstOrNull()
            _state.update {
                it.copy(
                    todayDishes = day?.dishes.orEmpty(),
                    dishDayLabel = day?.date?.let(::labelFor).orEmpty(),
                    loadingMensa = false,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(loadingMensa = false) }
        }
    }

    // ── Mensa-Datumslogik — Port von `MensaSchedule.days`/`HomeView.labelFor` (MensaView.swift,
    // HomeView.swift): strikt ab heute, aufsteigend; kein Rückfall auf vergangene Tage. ─────────

    private data class DayGroup(val date: LocalDate, val dishes: List<Dish>)

    private fun mensaDays(dishes: List<Dish>): List<DayGroup> {
        val today = todayLocalDate()
        val byDay = linkedMapOf<LocalDate, MutableList<Dish>>()
        for (d in dishes) {
            val date = runCatching { LocalDate.parse(d.date.take(10)) }.getOrNull() ?: continue
            byDay.getOrPut(date) { mutableListOf() }.add(d)
        }
        return byDay.keys.filter { it >= today }.sorted().map { DayGroup(it, byDay.getValue(it)) }
    }

    private val weekdayNamesDe = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
    private val monthAbbrevDe = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")

    private fun labelFor(date: LocalDate): String {
        val today = todayLocalDate()
        if (date == today) return "Heute"
        if (date == today.plus(1, DateTimeUnit.DAY)) return "Morgen"
        val sameWeek = isoWeekNumber(date) == isoWeekNumber(today) && date.year == today.year
        val weekday = weekdayNamesDe[date.dayOfWeek.ordinal]
        return if (sameWeek) weekday else "$weekday, ${date.dayOfMonth}. ${monthAbbrevDe[date.monthNumber - 1]}"
    }
}
