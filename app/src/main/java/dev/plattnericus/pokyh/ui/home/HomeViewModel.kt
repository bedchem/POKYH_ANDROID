package dev.plattnericus.pokyh.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.images.ImagePrefetcher
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.core.util.mondayOfWeek
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.storage.OfflineStore
import dev.plattnericus.pokyh.data.storage.PreferencesStore
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.builtins.ListSerializer

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
    private val preferencesStore: PreferencesStore,
    private val offlineStore: OfflineStore,
    private val imagePrefetcher: ImagePrefetcher,
) : ViewModel() {

    /** `HomeView.RecentGrade` — a flattened, most-recent-first grade entry for the "Zuletzt
     * eingetragen" section. */
    data class RecentGrade(val id: Int, val subject: String, val value: Double, val date: Int)

    data class UiState(
        val session: UserSession? = null,
        val loadingToday: Boolean = true,
        val todaySlots: List<MergedSlot> = emptyList(),
        /** When today's lessons were produced, and whether they came off the disk. */
        val todaySavedAt: Long = 0L,
        val todayStale: Boolean = false,
        val loadingGrades: Boolean = true,
        val recentGrades: List<RecentGrade> = emptyList(),
        val loadingMensa: Boolean = true,
        val mensaWeek: List<MensaWeekDay> = emptyList(),
        val mensaWeekLabel: String = "",
        /** When the menu was produced, and whether it came off the disk. */
        val mensaSavedAt: Long = 0L,
        val mensaStale: Boolean = false,
        val dishRatings: Map<String, DishRatingsData> = emptyMap(),
        /**
         * True until the batch of star ratings for this week's dishes has resolved one way or
         * the other.
         *
         * An explicit flag rather than `dishRatings.isEmpty()`, which cannot tell "not fetched
         * yet" from "fetched, and nothing is rated" — and would leave the placeholder
         * shimmering forever in the second case.
         */
        val loadingDishRatings: Boolean = true,
        val examsLoaded: Boolean = false,
        val nextExam: TimetableEntry? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState(session = appState.session.value))
    val uiState: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Which blocks Home shows, and in what order — see [HomeLayout].
     *
     * Its own flow rather than a field of [UiState]: the layout is a persisted preference that
     * changes when the user edits it, while [UiState] is reloaded data. Keeping them apart means
     * a refresh can't clobber an edit, and an edit doesn't make the screen look like it reloaded.
     */
    val layout: StateFlow<HomeLayout> = preferencesStore.homeLayout
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeLayout())

    /**
     * True while the Home editor is open.
     *
     * Deliberately *not* persisted: edit mode is something you are doing, not something you have
     * set, and an app that reopened into edit mode would be answering a question nobody asked.
     */
    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    fun setEditing(value: Boolean) { _editing.value = value }

    /**
     * Reorder, by index into [HomeLayout.sanitized].
     *
     * Writes on every swap as the drag passes over a neighbour, not once when the finger lifts.
     * DataStore coalesces the writes, and it means an edit survives the app being killed
     * mid-gesture — which, on a screen whose whole job is arranging things, is the one moment
     * losing state would be most annoying.
     */
    /**
     * Persist an arrangement, once.
     *
     * **Called when the card is set down, not while it moves.** Every swap used to write
     * straight to DataStore and wait for the flow to come back around, which put a disk
     * round-trip inside the drag loop: the neighbour only stepped aside once the write had
     * landed, so the reorder visibly lagged the finger. The screen now owns the live order
     * and hands it over at the end — one write per drag instead of one per swap.
     */
    fun setOrder(sections: List<HomeSection>) {
        val next = HomeLayout(order = sections.map { it.name })
        if (next.order == layout.value.sanitized.map { it.name }) return
        viewModelScope.launch { preferencesStore.setHomeLayout(next) }
    }

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
            val result = offlineStore.load(
                key = "home-week-${session.studentId}-$monday",
                serializer = ListSerializer(TimetableEntry.serializer()),
            ) { untisClient.timetable(session, session.studentId, monday) }
            val all = result.value
            val today = SchoolDates.todayNum()
            val slots = TimetableSlots.buildSlots(all.filter { it.date == today })
            _state.update {
                it.copy(
                    todaySlots = slots,
                    loadingToday = false,
                    todaySavedAt = result.savedAt,
                    todayStale = result.stale,
                )
            }
            prefetchSubjectImages(all.filter { it.date >= today })
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
            val mensa = backendClient.dishesWithFreshness()
            val week = mensaWeek(mensa.dishes)
            _state.update {
                it.copy(
                    mensaWeek = week,
                    mensaWeekLabel = if (week.any { day -> day.isToday }) "Diese Woche" else "Nächste Woche",
                    loadingMensa = false,
                    mensaSavedAt = mensa.savedAt,
                    mensaStale = mensa.stale,
                )
            }
            loadDishRatings(week.flatMap { day -> day.dishes }.map { dish -> dish.id })
            // Home shows three dishes per day at thumbnail size; the Mensa tab and the dish
            // sheet show the same photos full-width. Pulling them now — while this screen is
            // already talking to the server — is what makes those screens instant, and what
            // leaves them with pictures at all when the connection is gone later.
            imagePrefetcher.prefetch(week.flatMap { day -> day.dishes }.mapNotNull { it.imageUrl })
        } catch (e: Exception) {
            _state.update { it.copy(loadingMensa = false) }
        }
    }

    /**
     * Pull the header images for the subjects in today's and tomorrow's lessons.
     *
     * Only those: the backend has an image per subject and a school year's worth is a lot of
     * megabytes to fetch speculatively. The lessons a user is about to tap on are the next two
     * days' — that is what the Home list shows and what the timetable opens on.
     */
    private fun prefetchSubjectImages(entries: List<TimetableEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val keys = runCatching { backendClient.subjectImageKeys() }.getOrNull() ?: return@launch
            val urls = entries
                .map { backendClient.subjectImageKeyOf(it.subjectLong, it.subjectName) }
                .filter { it.isNotEmpty() && it in keys }
                .distinct()
                .map { backendClient.subjectImageUrl(it) }
            imagePrefetcher.prefetch(
                urls = urls,
                headers = backendClient.apiKeyHeaderName to backendClient.apiKeyHeaderValue,
            )
        }
    }

    /** The star averages for the week's dishes in one batch call, so the home strip can show a
     * rating per dish without a request per card. Needs a POKYH backend token; without one the
     * cards simply show no stars. */
    private suspend fun loadDishRatings(ids: List<String>) {
        val unique = ids.distinct()
        val token = appState.session.value?.apiToken
        if (unique.isEmpty() || token == null) {
            // Nothing to fetch, or no backend token to fetch it with — either way the stars are
            // as resolved as they are going to get, so stop the placeholder.
            _state.update { it.copy(loadingDishRatings = false) }
            return
        }
        // Whatever the shared cache already knows, immediately — the Mensa tab and Home look at
        // the same dishes, so arriving from there usually means the stars are already known.
        val cached = backendClient.cachedRatings(unique)
        if (cached.isNotEmpty()) {
            _state.update { it.copy(dishRatings = it.dishRatings + cached, loadingDishRatings = false) }
        }
        runCatching { backendClient.dishRatingsBatch(unique, token) }
            .onSuccess { batch ->
                _state.update { it.copy(dishRatings = it.dishRatings + batch, loadingDishRatings = false) }
            }
            .onFailure { _state.update { it.copy(loadingDishRatings = false) } }
    }

    // ── Mensa-Wochenlogik ────────────────────────────────────────────────────

    /** One weekday column of the home screen's menu strip. */
    data class MensaWeekDay(
        val date: LocalDate,
        val weekday: String,
        /** `dd.MM.` — the date is spelled out on every card, so no column is ambiguous. */
        val dateLabel: String,
        val isToday: Boolean,
        val isPast: Boolean,
        val dishes: List<Dish>,
    )

    /**
     * Monday–Friday of the current week — and from Saturday on, of the *next* one, because a
     * menu for a week that is already over is never the answer to "what's for lunch".
     *
     * Days without a menu are kept rather than dropped: the strip is a week, and a gap in it is
     * information ("no menu on Wednesday"), not something to hide by shifting the other days.
     */
    private fun mensaWeek(dishes: List<Dish>): List<MensaWeekDay> {
        val today = todayLocalDate()
        val weekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
        val monday = mondayOfWeek(today).let { if (weekend) it.plus(7, DateTimeUnit.DAY) else it }

        val byDay = linkedMapOf<LocalDate, MutableList<Dish>>()
        for (dish in dishes) {
            val date = runCatching { LocalDate.parse(dish.date.take(10)) }.getOrNull() ?: continue
            byDay.getOrPut(date) { mutableListOf() }.add(dish)
        }

        return (0..4).map { offset ->
            val date = monday.plus(offset, DateTimeUnit.DAY)
            MensaWeekDay(
                date = date,
                weekday = weekdayNamesDe[date.dayOfWeek.ordinal],
                dateLabel = "%02d.%02d.".format(date.dayOfMonth, date.monthNumber),
                isToday = date == today,
                isPast = date < today,
                dishes = byDay[date].orEmpty(),
            )
        }
    }

    private val weekdayNamesDe = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
}
