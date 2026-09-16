package dev.plattnericus.pokyh.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.images.ImagePrefetcher
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.core.util.mondayOfWeek
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.data.model.ApiTodo
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.storage.OfflineStore
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import dev.plattnericus.pokyh.data.sync.Outbox
import dev.plattnericus.pokyh.data.sync.OutboxItem
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.TimetableSlots
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.navigation.AppTab
import dev.plattnericus.pokyh.ui.reminders.visibleReminders
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val outbox: Outbox,
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
        /** Today's lessons could not be loaded at all — "kein Unterricht" would be a guess. */
        val todayUnavailable: Boolean = false,
        val loadingGrades: Boolean = true,
        val recentGrades: List<RecentGrade> = emptyList(),
        /** The grades could not be loaded at all — "noch keine Noten" would be a guess. */
        val gradesUnavailable: Boolean = false,
        /** At least one of the sampled weeks could not be loaded, so "no exams" is unknown. */
        val examsUnknown: Boolean = false,
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
        /** Ratings could not be fetched — a dish without one is unknown, not unrated. */
        val dishRatingsUnavailable: Boolean = false,
        /** Today's raw entries, for "Jetzt & gleich" (which needs start/end, not merged slots). */
        val todayEntries: List<TimetableEntry> = emptyList(),
        /** Cancellations, substitutions and room/teacher changes for today and the next school day. */
        val changes: List<TimetableEntry> = emptyList(),
        val changesLoaded: Boolean = false,
        val changesUnknown: Boolean = false,
        val gradeAverage: Double? = null,
        val gradeCount: Int = 0,
        val weakestSubject: Pair<String, Double>? = null,
        val averageLoaded: Boolean = false,
        val openTodos: List<ApiTodo> = emptyList(),
        val todosLoaded: Boolean = false,
        /** Nothing loaded and nothing stored — the widget says so instead of "keine Todos". */
        val todosUnavailable: Boolean = false,
        val upcomingReminders: List<ApiReminder> = emptyList(),
        val remindersLoaded: Boolean = false,
        val remindersUnavailable: Boolean = false,
        /** Created offline, waiting in the outbox. */
        val pendingTodos: Int = 0,
        val pendingReminders: Int = 0,
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
     * Persist an arrangement, once.
     *
     * **Called when the card is set down, not while it moves.** Every swap used to write
     * straight to DataStore and wait for the flow to come back around, which put a disk
     * round-trip inside the drag loop: the neighbour only stepped aside once the write had
     * landed, so the reorder visibly lagged the finger. The screen now owns the live order
     * and hands it over at the end — one write per drag instead of one per swap.
     */
    fun setOrder(sections: List<HomeSection>) {
        val next = layout.value.withOrder(sections)
        if (next.order == layout.value.visible.map { it.name }) return
        viewModelScope.launch { preferencesStore.setHomeLayout(next) }
    }

    /** Arrange mode's remove badge — the widget moves to "Widgets hinzufügen". */
    fun removeSection(section: HomeSection) {
        viewModelScope.launch { preferencesStore.setHomeLayout(layout.value.removed(section)) }
    }

    /** "Widgets hinzufügen" — the widget goes back on Home, at the bottom. */
    fun addSection(section: HomeSection) {
        viewModelScope.launch { preferencesStore.setHomeLayout(layout.value.added(section)) }
    }

    fun resetLayout() {
        viewModelScope.launch { preferencesStore.setHomeLayout(HomeLayout()) }
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
        // A vote cast on a dish page (or taken back) shows on the Home menu strip right away.
        viewModelScope.launch {
            backendClient.ratingUpdates.collect { updates ->
                if (updates.isNotEmpty()) _state.update { it.copy(dishRatings = it.dishRatings + updates) }
            }
        }
        // An offline session that just got its token back: the POKYH widgets can load for real.
        viewModelScope.launch {
            appState.session.map { it?.apiToken != null }.distinctUntilChanged().collect { hasToken ->
                if (hasToken) loadBackendWidgets()
            }
        }
        // Queued Todos/Erinnerungen: count them, and reload once one has gone out.
        viewModelScope.launch {
            outbox.pending.collect { items ->
                val user = appState.session.value?.username?.trim()?.lowercase()
                val mine = items.filter { it.username == user }
                val todos = mine.count { it.kind == OutboxItem.Kind.Todo }
                val reminders = mine.count { it.kind == OutboxItem.Kind.Reminder }
                val sent = todos < _state.value.pendingTodos || reminders < _state.value.pendingReminders
                _state.update { it.copy(pendingTodos = todos, pendingReminders = reminders) }
                if (sent) loadBackendWidgets()
            }
        }
    }

    /**
     * "Offene Todos" and "Anstehende Erinnerungen" — from the server when there is a token,
     * otherwise from the copy the Todos/Erinnerungen screens stored (same disk keys).
     */
    private suspend fun loadBackendWidgets() {
        val session = appState.session.value ?: return
        val user = session.username.lowercase()
        val token = session.apiToken
        val todoKey = "todos-$user"
        val reminderKey = "reminders-$user"
        val todoSerializer = ListSerializer(ApiTodo.serializer())
        val reminderSerializer = ListSerializer(ApiReminder.serializer())

        val todos: List<ApiTodo>? = if (token != null) {
            runCatching { offlineStore.load(todoKey, todoSerializer) { backendClient.todos(session.username, token) }.value }.getOrNull()
        } else {
            offlineStore.peek(todoKey, todoSerializer)?.value
        }
        _state.update {
            it.copy(
                openTodos = todos.orEmpty().filter { t -> !t.done },
                todosLoaded = true,
                todosUnavailable = todos == null,
            )
        }

        val classId = session.classId
        val reminders: List<ApiReminder>? = if (token != null && classId != null) {
            runCatching {
                offlineStore.load(reminderKey, reminderSerializer) { backendClient.reminders(classId, token) }.value
            }.getOrNull()
        } else {
            offlineStore.peek(reminderKey, reminderSerializer)?.value
        }
        _state.update {
            it.copy(
                upcomingReminders = reminders?.let { list -> visibleReminders(list) }.orEmpty(),
                remindersLoaded = true,
                remindersUnavailable = reminders == null,
            )
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

    /** "Jetzt & gleich" / "Vertretungen" taps — straight to the Stundenplan tab. */
    fun selectTimetableTab() = appState.selectTab(AppTab.Timetable)

    private suspend fun loadAll() {
        _state.update { it.copy(error = null) }
        val session = appState.session.value
        try {
            coroutineScope {
                // Mensa kommt aus dem Backend -> immer; Stundenplan/Noten/Prüfungen nur mit
                // verknüpftem WebUntis-Konto.
                val mensaJob = async { loadMensa() }
                val backendJob = async { loadBackendWidgets() }
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
                backendJob.await()
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
                key = "tt-${session.studentId}-$monday",
                serializer = ListSerializer(TimetableEntry.serializer()),
            ) { untisClient.timetable(session, session.studentId, monday) }
            val all = result.value
            val today = SchoolDates.todayNum()
            val slots = TimetableSlots.buildSlots(all.filter { it.date == today })
            _state.update {
                it.copy(
                    todayEntries = all.filter { e -> e.date == today },
                    todaySlots = slots,
                    loadingToday = false,
                    todaySavedAt = result.savedAt,
                    todayStale = result.stale,
                    // A restored week with nothing in it proves nothing about today.
                    todayUnavailable = result.stale && all.isEmpty(),
                )
            }
            prefetchSubjectImages(all.filter { it.date >= today })
        } catch (e: AppError) {
            if (e.isSessionExpired) appState.handleSessionExpired()
            _state.update { it.copy(loadingToday = false, todaySlots = emptyList(), todayUnavailable = true) }
        } catch (e: Exception) {
            _state.update { it.copy(loadingToday = false, todaySlots = emptyList(), todayUnavailable = true) }
        }
    }

    private suspend fun loadGrades(session: UserSession) {
        _state.update { it.copy(loadingGrades = true) }
        try {
            // Same key as the Noten tab, so either screen having loaded online is enough.
            val subjects = offlineStore.load(
                key = "grades-${session.studentId}-${SchoolDates.currentSchoolYear}",
                serializer = ListSerializer(SubjectGrades.serializer()),
            ) { untisClient.grades(session, session.studentId, null) }.value
            val items = subjects.flatMap { subj ->
                subj.grades.filter { it.markDisplayValue > 0 }
                    .map { g -> RecentGrade(id = g.id, subject = subj.subjectName, value = g.markDisplayValue, date = g.date) }
            }.sortedByDescending { it.id }.take(3)
            val values = subjects.flatMap { s -> s.grades.map { g -> g.markDisplayValue } }.filter { v -> v > 0 }
            val weakest = subjects
                .mapNotNull { s ->
                    val v = s.grades.map { g -> g.markDisplayValue }.filter { x -> x > 0 }
                    if (v.isEmpty()) null else s.subjectName to v.average()
                }
                .minByOrNull { it.second }
            _state.update {
                it.copy(
                    gradeAverage = if (values.isEmpty()) null else values.average(),
                    gradeCount = values.size,
                    weakestSubject = weakest,
                    averageLoaded = true,
                )
            }
            _state.update { it.copy(recentGrades = items, loadingGrades = false, gradesUnavailable = false) }
        } catch (e: Exception) {
            _state.update { it.copy(loadingGrades = false, gradesUnavailable = it.recentGrades.isEmpty(), averageLoaded = true) }
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
            val weeks = coroutineScope {
                weekStarts.map { monday ->
                    async {
                        // Shares the Stundenplan tab's per-week cache key, so offline this sees
                        // every week that was ever opened there.
                        runCatching {
                            offlineStore.load(
                                key = "tt-${session.studentId}-$monday",
                                serializer = ListSerializer(TimetableEntry.serializer()),
                            ) { untisClient.timetable(session, session.studentId, monday.toString()) }
                        }.getOrNull()
                    }
                }.awaitAll()
            }
            val next = weeks.filterNotNull().flatMap { it.value }
                .filter { it.isExam && it.date >= today }
                .sortedWith(compareBy({ it.date }, { it.startTime }))
                .firstOrNull()
            val unknown = weeks.any { it == null || (it.stale && it.value.isEmpty()) }
            val all = weeks.filterNotNull().flatMap { it.value }
            val nextDay = all.map { it.date }.filter { it > today }.minOrNull()
            val changeDays = listOfNotNull(today, nextDay)
            val changes = all
                .filter { it.date in changeDays && it.isChange() }
                .sortedWith(compareBy({ it.date }, { it.startTime }))
            _state.update {
                it.copy(
                    nextExam = next,
                    examsLoaded = true,
                    examsUnknown = unknown,
                    changes = changes,
                    changesLoaded = true,
                    changesUnknown = weeks.firstOrNull() == null,
                )
            }
        } catch (e: Exception) {
            _state.update { it.copy(examsLoaded = true, examsUnknown = true, changesLoaded = true, changesUnknown = true) }
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
            // as resolved as they are going to get, so stop the placeholder. Offline, show the
            // stars stored last time and mark the rest unknown rather than unrated.
            val offline = token == null && appState.isOffline.value
            val stored = if (offline) backendClient.storedRatings(unique) else emptyMap()
            _state.update {
                it.copy(dishRatings = stored + it.dishRatings, loadingDishRatings = false, dishRatingsUnavailable = offline)
            }
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
                _state.update {
                    it.copy(dishRatings = it.dishRatings + batch, loadingDishRatings = false, dishRatingsUnavailable = false)
                }
            }
            .onFailure {
                val stored = backendClient.storedRatings(unique)
                _state.update {
                    it.copy(dishRatings = stored + it.dishRatings, loadingDishRatings = false, dishRatingsUnavailable = true)
                }
            }
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

/** Is this lesson not happening as timetabled — cancelled, substituted, added, or moved? */
internal fun TimetableEntry.isChange(): Boolean =
    isCancelled || isSubstitution || isAdditional ||
        addedTeachers.isNotEmpty() || addedRooms.isNotEmpty() || addedSubjects.isNotEmpty()
