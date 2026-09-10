package dev.plattnericus.pokyh.ui.mensa

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.backend.SseClient
import dev.plattnericus.pokyh.data.model.ApiComment
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.CommentUiItem
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Gemeinsame Mensa-Datums-Logik (`MensaSchedule` in MensaView.swift): ab dem aktuellen Datum
 * (heute) alle Tage aufsteigend. Liegen keine heutigen/zukünftigen Gerichte vor, bleibt es leer
 * (kein Rückfall auf alte Tage).
 */
object MensaSchedule {
    data class DayGroup(val date: LocalDate, val label: String, val dishes: List<Dish>)

    fun days(dishes: List<Dish>): List<DayGroup> {
        val today = todayLocalDate()
        val byDay = LinkedHashMap<LocalDate, MutableList<Dish>>()
        for (dish in dishes) {
            val date = parseDishDate(dish.date) ?: continue
            byDay.getOrPut(date) { mutableListOf() }.add(dish)
        }
        return byDay.keys.sorted()
            .filter { it >= today }
            .map { date -> DayGroup(date, label(date, today), byDay.getValue(date)) }
    }

    private fun parseDishDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()

    private fun label(date: LocalDate, today: LocalDate): String {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        return when (date) {
            today -> "Heute"
            tomorrow -> "Morgen"
            else -> "${weekdayName(date.dayOfWeek)}, ${date.dayOfMonth}. ${monthName(date.month)}"
        }
    }

    private fun weekdayName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Montag"
        DayOfWeek.TUESDAY -> "Dienstag"
        DayOfWeek.WEDNESDAY -> "Mittwoch"
        DayOfWeek.THURSDAY -> "Donnerstag"
        DayOfWeek.FRIDAY -> "Freitag"
        DayOfWeek.SATURDAY -> "Samstag"
        DayOfWeek.SUNDAY -> "Sonntag"
        else -> ""
    }

    private fun monthName(month: Month): String = when (month) {
        Month.JANUARY -> "Januar"
        Month.FEBRUARY -> "Februar"
        Month.MARCH -> "März"
        Month.APRIL -> "April"
        Month.MAY -> "Mai"
        Month.JUNE -> "Juni"
        Month.JULY -> "Juli"
        Month.AUGUST -> "August"
        Month.SEPTEMBER -> "September"
        Month.OCTOBER -> "Oktober"
        Month.NOVEMBER -> "November"
        Month.DECEMBER -> "Dezember"
        else -> ""
    }
}

/** MensaView.swift, ported — the list of upcoming days + dishes, plus a shared ratings map
 * (`ratings: [String: DishRatingsData]`) so cards can show [dev.plattnericus.pokyh.ui.components.
 * MiniStars] without a per-card network call. */
@HiltViewModel
class MensaViewModel @Inject constructor(
    private val appState: AppState,
    private val backendClient: BackendClient,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val groups: List<MensaSchedule.DayGroup> = emptyList(),
        val ratings: Map<String, DishRatingsData> = emptyMap(),
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.groups.isEmpty(), error = null) }
            runCatching { backendClient.dishes() }
                .onSuccess { dishes ->
                    _ui.update { it.copy(loading = false, error = null, groups = MensaSchedule.days(dishes)) }
                    loadRatings(dishes.map { it.id })
                }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message ?: "Unbekannter Fehler.") } }
        }
    }

    private suspend fun loadRatings(ids: List<String>) {
        val token = appState.session.value?.apiToken ?: return
        if (ids.isEmpty()) return
        runCatching { backendClient.dishRatingsBatch(ids, token) }
            .onSuccess { batch -> _ui.update { it.copy(ratings = batch) } }
    }

    /** Retry button / pull-to-refresh. */
    fun refresh() = load()
}

/** DishDetailView.swift, ported — one dish (fetched from the same cached [BackendClient.dishes]
 * list the list screen uses, keyed by the `dishId` nav arg), its rating, and its comment thread.
 * Ratings/comments go live via SSE when a POKYH backend token is available; otherwise they stay
 * at whatever a one-shot fetch returned (or empty, for a token-less offline session). */
@HiltViewModel
class DishDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appState: AppState,
    private val backendClient: BackendClient,
    private val sseClient: SseClient,
) : ViewModel() {

    private val dishId: String = savedStateHandle.get<String>("dishId").orEmpty()

    data class UiState(
        val loading: Boolean = true,
        val dish: Dish? = null,
        val ratings: DishRatingsData = DishRatingsData(emptyMap(), null),
        val comments: List<CommentUiItem> = emptyList(),
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** [dev.plattnericus.pokyh.ui.components.CommentSection]'s `currentUserId` — the POKYH
     * `stableUid` comments are keyed by; empty (no delete button ever matches) without one. */
    val currentUserId: String get() = appState.session.value?.stableUid ?: appState.session.value?.username.orEmpty()

    private var liveJob: Job? = null

    init {
        viewModelScope.launch { loadDish() }
        viewModelScope.launch {
            appState.session.collectLatest { s ->
                liveJob?.cancel()
                val token = s?.apiToken ?: return@collectLatest
                liveJob = launch { runLive(token) }
            }
        }
    }

    private suspend fun runLive(token: String) {
        loadRatingsAndComments(token)
        coroutineScope {
            launch {
                sseClient.sseDishRatings(dishId, token)
                    .catch { /* stream dropped for good — last fetched state stays */ }
                    .collect { dto -> _ui.update { it.copy(ratings = DishRatingsData(dto.ratings, dto.myRating)) } }
            }
            launch {
                sseClient.sseDishComments(dishId, token)
                    .catch { }
                    .collect { list -> _ui.update { it.copy(comments = list.map { c -> c.toUi() }) } }
            }
        }
    }

    private suspend fun loadDish() {
        _ui.update { it.copy(loading = true, error = null) }
        runCatching { backendClient.dishes() }
            .onSuccess { list ->
                val dish = list.firstOrNull { it.id == dishId }
                _ui.update { it.copy(loading = false, dish = dish, error = if (dish == null) "Gericht nicht gefunden." else null) }
            }
            .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message ?: "Unbekannter Fehler.") } }
    }

    private suspend fun loadRatingsAndComments(token: String) {
        runCatching { backendClient.dishRatings(dishId, token) }
            .onSuccess { r -> _ui.update { it.copy(ratings = r) } }
        runCatching { backendClient.dishComments(dishId, token) }
            .onSuccess { list -> _ui.update { it.copy(comments = list.map { c -> c.toUi() }) } }
    }

    /** Optimistic local update on tap, then refetch — port of DishDetailView's `onRate` closure
     * (`d.myRating = stars; ...; ratings[dish.id] = fresh`). No-ops without a backend token. */
    fun rate(stars: Int) {
        val token = appState.session.value?.apiToken ?: return
        _ui.update { it.copy(ratings = it.ratings.copy(myRating = stars)) }
        viewModelScope.launch {
            runCatching { backendClient.rateDish(dishId, stars, token) }
            runCatching { backendClient.dishRatings(dishId, token) }
                .onSuccess { fresh -> _ui.update { it.copy(ratings = fresh) } }
        }
    }

    fun addComment(body: String) {
        val token = appState.session.value?.apiToken ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            runCatching { backendClient.createDishComment(dishId, body, token) }
            runCatching { backendClient.dishComments(dishId, token) }
                .onSuccess { list -> _ui.update { it.copy(comments = list.map { c -> c.toUi() }) } }
        }
    }

    fun deleteComment(item: CommentUiItem) {
        val token = appState.session.value?.apiToken ?: return
        _ui.update { it.copy(comments = it.comments.filterNot { c -> c.id == item.id }) }
        viewModelScope.launch {
            runCatching { backendClient.deleteDishComment(dishId, item.id, token) }
            runCatching { backendClient.dishComments(dishId, token) }
                .onSuccess { list -> _ui.update { it.copy(comments = list.map { c -> c.toUi() }) } }
        }
    }
}

private fun ApiComment.toUi(): CommentUiItem = CommentUiItem(
    id = id,
    authorId = stableUid,
    authorName = username,
    body = body,
    createdAtEpochMs = parseEpochMs(createdAt) ?: 0L,
    editedAtEpochMs = updatedAt?.takeIf { it != createdAt }?.let(::parseEpochMs),
)

private fun parseEpochMs(raw: String): Long? = runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull()
