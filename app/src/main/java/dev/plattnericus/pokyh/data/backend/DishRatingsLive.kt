package dev.plattnericus.pokyh.data.backend

import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn

/**
 * Live ratings of every dish, from anyone on any platform.
 *
 * Mensa and Home each collect [updates] while they are alive; they share **one** SSE connection,
 * which closes a few seconds after the last of them goes away. Each event lands in
 * [BackendClient.publishRating], so every screen reading [BackendClient.ratingUpdates] — the
 * Mensa list, the Home menu strip — shows a vote cast in the browser or on another phone the
 * moment it is saved, not when the ratings cache runs out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DishRatingsLive @Inject constructor(
    appState: AppState,
    backendClient: BackendClient,
    sseClient: SseClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val updates: SharedFlow<Pair<String, DishRatingsData>> = appState.session
        .map { it?.apiToken }
        .distinctUntilChanged()
        .flatMapLatest { token -> if (token == null) emptyFlow() else sseClient.sseAllDishRatings(token) }
        .map { event -> event.dishId to DishRatingsData(event.ratings, event.myRating) }
        .onEach { (dishId, data) -> backendClient.publishRating(dishId, data) }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L))
}
