package dev.plattnericus.pokyh.ui.mensa

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.backend.DishRatingsLive
import dev.plattnericus.pokyh.data.backend.SseClient
import dev.plattnericus.pokyh.data.model.ApiComment
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.CommentUiItem
import javax.inject.Inject
import kotlin.time.Clock
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
    private val dishRatingsLive: DishRatingsLive,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val groups: List<MensaSchedule.DayGroup> = emptyList(),
        val ratings: Map<String, DishRatingsData> = emptyMap(),
        /**
         * True until the first ratings answer for the dishes on screen has landed.
         *
         * Drives the star placeholder: without it the cards render with no stars and the stars
         * then pop in a beat later, which reads as a glitch rather than as loading. Separate
         * from [loading], because dishes and ratings are two round trips and the dishes always
         * win — the cards are on screen while the stars are still coming.
         */
        val ratingsLoading: Boolean = true,
        /** Ratings could not be fetched — a dish without one is unknown, not unrated. */
        val ratingsUnavailable: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        // Whatever is on disk from last time, on screen before the first request finishes.
        viewModelScope.launch {
            backendClient.cachedDishesOrNull()?.let { cached ->
                _ui.update { state ->
                    if (state.groups.isNotEmpty()) {
                        state
                    } else {
                        state.copy(groups = MensaSchedule.days(cached), loading = false, ratings = state.ratings)
                    }
                }
                seedRatingsFromCache(cached.map { it.id })
            }
        }
        load()
        // Reload on every background -> foreground return, the same way HomeViewModel does.
        // A menu that was loaded yesterday is no longer today's.
        viewModelScope.launch {
            var seen = appState.resumeSignal.value
            appState.resumeSignal.collect { signal ->
                if (signal != seen) {
                    seen = signal
                    load()
                }
            }
        }
        // A vote cast on the detail page (or taken back) shows here the moment it happens.
        viewModelScope.launch {
            backendClient.ratingUpdates.collect { updates ->
                if (updates.isNotEmpty()) _ui.update { it.copy(ratings = it.ratings + updates) }
            }
        }
        // Votes from anyone else — browser or another phone — arrive through the same map.
        viewModelScope.launch { dishRatingsLive.updates.collect { } }
        // Ratings need a backend token. On a cold start the session often resolves *after* the
        // dishes do, so fetching once on load would leave the stars permanently empty.
        viewModelScope.launch {
            appState.session.collectLatest { session ->
                if (session?.apiToken == null) return@collectLatest
                val ids = _ui.value.groups.flatMap { it.dishes }.map { it.id }
                if (ids.isEmpty()) return@collectLatest
                loadRatings(ids)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = it.groups.isEmpty(), error = null) }
            runCatching { backendClient.dishes() }
                .onSuccess { dishes ->
                    _ui.update { it.copy(loading = false, error = null, groups = MensaSchedule.days(dishes)) }
                    loadRatings(dishes.map { it.id })
                }
                .onFailure { e ->
                    _ui.update {
                        // Only surface the failure when there is nothing to show. With cached
                        // days on screen a dropped request is not the user's problem.
                        if (it.groups.isNotEmpty()) {
                            it.copy(loading = false)
                        } else {
                            it.copy(loading = false, error = e.message ?: "Unbekannter Fehler.")
                        }
                    }
                    // The stored menu is on screen — give it its stored stars, or say they are
                    // unknown, instead of leaving every dish at "Noch keine Bewertung".
                    val ids = _ui.value.groups.flatMap { it.dishes }.map { it.id }
                    if (ids.isNotEmpty()) loadRatings(ids)
                }
        }
    }

    /**
     * Called when the Mensa tab comes back into view.
     *
     * Belt and braces for the case this screen used to fail at: coming back from another tab
     * with an empty list and no request in flight, and simply sitting there. If there is
     * nothing on screen, load; if there is, the TTL cache makes this close to free.
     */
    fun onAppear() {
        if (_ui.value.groups.isEmpty()) load()
    }

    private fun seedRatingsFromCache(ids: List<String>) {
        val cached = backendClient.cachedRatings(ids)
        if (cached.isEmpty()) return
        _ui.update { it.copy(ratings = it.ratings + cached, ratingsLoading = false) }
    }

    private suspend fun loadRatings(ids: List<String>) {
        if (ids.isEmpty()) {
            _ui.update { it.copy(ratingsLoading = false) }
            return
        }
        seedRatingsFromCache(ids)
        val token = appState.session.value?.apiToken
        if (token == null) {
            // No backend token: stop the placeholder rather than shimmering forever. Offline,
            // show the stars stored last time and mark the rest unknown rather than unrated.
            val offline = appState.isOffline.value
            val stored = if (offline) backendClient.storedRatings(ids) else emptyMap()
            _ui.update {
                it.copy(ratings = stored + it.ratings, ratingsLoading = false, ratingsUnavailable = offline)
            }
            return
        }
        runCatching { backendClient.dishRatingsBatch(ids, token) }
            .onSuccess { batch ->
                _ui.update { it.copy(ratings = it.ratings + batch, ratingsLoading = false, ratingsUnavailable = false) }
            }
            .onFailure {
                val stored = backendClient.storedRatings(ids)
                _ui.update { it.copy(ratings = stored + it.ratings, ratingsLoading = false, ratingsUnavailable = true) }
            }
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
        /** Offline and nothing stored for this dish — the rating is unknown, not zero. */
        val ratingsUnavailable: Boolean = false,
        /** Rating and commenting need the POKYH server. */
        val canInteract: Boolean = true,
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
                val token = s?.apiToken
                if (token == null) {
                    // No server to ask: stored stars if this dish was ever rated-loaded here,
                    // otherwise "unknown" — never a confident 0,0 (0).
                    val offline = s != null && appState.isOffline.value
                    val stored = if (offline) backendClient.storedRatings(listOf(dishId))[dishId] else null
                    _ui.update {
                        it.copy(
                            ratings = stored ?: it.ratings,
                            ratingsUnavailable = offline && stored == null,
                            canInteract = false,
                        )
                    }
                    return@collectLatest
                }
                _ui.update { it.copy(ratingsUnavailable = false, canInteract = true) }
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
                    .collect { dto -> applyServerRatings(DishRatingsData(dto.ratings, dto.myRating)) }
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
            .onSuccess { r -> applyServerRatings(r) }
        runCatching { backendClient.dishComments(dishId, token) }
            .onSuccess { list -> _ui.update { it.copy(comments = list.map { c -> c.toUi() }) } }
    }

    /**
     * Rate the dish. The stars, the average and the count all move **on tap**; the request
     * follows.
     *
     * Before, only `myRating` was applied locally, so the filled stars changed instantly while
     * the average next to them kept the old value until a round trip finished — the one number
     * the tap was supposed to change was the last to move. Writing the vote into the local
     * ratings map fixes the average and the count too, and mirrors it into the shared cache so
     * the Mensa list and Home show the same thing without refetching.
     *
     * **Until the server has answered, the vote stays on screen.** A server answer that arrives
     * in the meantime — the stream's push, the first load finishing late — was taken from before
     * the vote and used to wipe it, which is why the count sometimes did not go up. While a vote
     * is in flight, [applyServerRatings] lays it over whatever comes in. Once the request
     * returns the server is the source of truth again: accepted, the refetch shows the real
     * numbers; rejected, the vote is taken back.
     */
    fun rate(stars: Int) {
        val token = appState.session.value?.apiToken ?: return
        val seq = ++voteSeq
        val before = _ui.value.ratings
        pendingVote = stars
        val optimistic = withVote(before, stars)
        _ui.update { it.copy(ratings = optimistic) }
        backendClient.publishRating(dishId, optimistic)
        viewModelScope.launch {
            val sent = runCatching { backendClient.rateDish(dishId, stars, token) }.isSuccess
            if (seq != voteSeq) return@launch // a newer tap owns the screen now
            pendingVote = null
            val fresh = runCatching { backendClient.dishRatings(dishId, token, force = true) }.getOrNull()
            when {
                fresh != null -> applyServerRatings(fresh)
                // Not counted and no truth to show: back to how it was before the tap.
                !sent -> {
                    _ui.update { it.copy(ratings = before) }
                    backendClient.publishRating(dishId, before)
                }
            }
        }
    }

    /** The vote currently on its way to the server, if any — see [rate]. */
    private var pendingVote: Int? = null
    private var voteSeq = 0

    private val userKey: String get() = currentUserId.ifEmpty { LOCAL_VOTE_KEY }

    /** Server ratings, with an in-flight vote laid over them. Also published for the list screens. */
    private fun applyServerRatings(server: DishRatingsData) {
        val shown = pendingVote?.let { withVote(server, it) } ?: server
        _ui.update { it.copy(ratings = shown) }
        backendClient.publishRating(dishId, shown)
    }

    /**
     * [data] with the current user's vote set to [stars].
     *
     * If the server already counts a vote from this user ([DishRatingsData.myRating] set), that
     * vote is *replaced* — found by the user's key, or failing that by its value — so changing a
     * rating moves the average without adding a phantom vote to the count.
     */
    private fun withVote(data: DishRatingsData, stars: Int): DishRatingsData {
        val ratings = data.ratings.toMutableMap()
        val existingKey = when {
            userKey in ratings -> userKey
            data.myRating != null -> ratings.entries.firstOrNull { it.value == data.myRating!!.toDouble() }?.key
            else -> null
        }
        ratings[existingKey ?: userKey] = stars.toDouble()
        return data.copy(ratings = ratings, myRating = stars)
    }


    /**
     * Post a comment. It appears in the thread immediately, under the current user's name, and
     * is replaced by the server's copy when the request returns.
     *
     * The pending row carries a [PENDING_COMMENT_PREFIX] id so a failed post can be rolled back
     * without touching anything real, and so the refetch (which brings the server's own id)
     * doesn't leave a duplicate behind.
     */
    fun addComment(body: String) {
        val token = appState.session.value?.apiToken ?: return
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return

        val pendingId = PENDING_COMMENT_PREFIX + Clock.System.now().toEpochMilliseconds()
        val pending = CommentUiItem(
            id = pendingId,
            authorId = currentUserId,
            authorName = appState.session.value?.username.orEmpty(),
            body = trimmed,
            createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
        )
        _ui.update { it.copy(comments = it.comments + pending) }

        viewModelScope.launch {
            val posted = runCatching { backendClient.createDishComment(dishId, trimmed, token) }
            if (posted.isFailure) {
                _ui.update { it.copy(comments = it.comments.filterNot { c -> c.id == pendingId }) }
                return@launch
            }
            runCatching { backendClient.dishComments(dishId, token) }
                .onSuccess { list -> _ui.update { it.copy(comments = list.map { c -> c.toUi() }) } }
                // Refetch failed but the post did not: swap the placeholder for the real
                // comment rather than dropping a comment that exists.
                .onFailure {
                    val real = posted.getOrNull()?.toUi() ?: return@onFailure
                    _ui.update { state ->
                        state.copy(comments = state.comments.map { c -> if (c.id == pendingId) real else c })
                    }
                }
        }
    }

    fun deleteComment(item: CommentUiItem) {
        val token = appState.session.value?.apiToken ?: return
        val before = _ui.value.comments
        _ui.update { it.copy(comments = it.comments.filterNot { c -> c.id == item.id }) }
        viewModelScope.launch {
            val deleted = runCatching { backendClient.deleteDishComment(dishId, item.id, token) }
            if (deleted.isFailure) {
                // Put it back — a comment that silently vanished and then reappeared on the next
                // visit is worse than one that never left.
                _ui.update { it.copy(comments = before) }
                return@launch
            }
            runCatching { backendClient.dishComments(dishId, token) }
                .onSuccess { list -> _ui.update { it.copy(comments = list.map { c -> c.toUi() }) } }
        }
    }
}

/** Id prefix for a comment that exists only locally until the server confirms it. */
private const val PENDING_COMMENT_PREFIX = "pending-"

/** Map key for an optimistic vote when the session has no stable user id to key it by. */
private const val LOCAL_VOTE_KEY = "__local__"

private fun ApiComment.toUi(): CommentUiItem = CommentUiItem(
    id = id,
    authorId = stableUid,
    authorName = username,
    body = body,
    createdAtEpochMs = parseEpochMs(createdAt) ?: 0L,
    editedAtEpochMs = updatedAt?.takeIf { it != createdAt }?.let(::parseEpochMs),
)

private fun parseEpochMs(raw: String): Long? = runCatching { Instant.parse(raw).toEpochMilliseconds() }.getOrNull()
