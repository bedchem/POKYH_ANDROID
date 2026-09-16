package dev.plattnericus.pokyh.ui.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.backend.SseClient
import dev.plattnericus.pokyh.data.model.ApiTodo
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.storage.OfflineStore
import dev.plattnericus.pokyh.data.sync.Outbox
import dev.plattnericus.pokyh.data.sync.OutboxItem
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/**
 * TodosView.swift, ported. Backend-only feature (no iOS-side offline fallback needed here — the
 * empty-session case is handled by [dev.plattnericus.pokyh.ui.components.BackendUnavailableView]
 * in the screen). Loads the list once via [BackendClient.todos], then keeps it live via
 * [SseClient.sseTodos]; if that stream never connects (or drops for good) the last successfully
 * fetched list just stays on screen — [refresh] (retry button / pull to refresh) is the fallback.
 */
@HiltViewModel
class TodosViewModel @Inject constructor(
    private val appState: AppState,
    private val backendClient: BackendClient,
    private val sseClient: SseClient,
    private val offlineStore: OfflineStore,
    private val outbox: Outbox,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val todos: List<ApiTodo> = emptyList(),
        val submitting: Boolean = false,
        /** Signed in offline and [todos] is the copy stored on this device (read-only). */
        val offlineCopy: Boolean = false,
        /** Signed in offline: new Todos go to the outbox instead of the server. */
        val offline: Boolean = false,
        /** Created without a connection, not sent yet. */
        val pending: List<OutboxItem> = emptyList(),
    )

    val session: StateFlow<UserSession?> = appState.session
    val backendStatus: StateFlow<BackendStatus> = appState.backendStatus

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var streamJob: Job? = null

    init {
        viewModelScope.launch {
            appState.session.collectLatest { s ->
                streamJob?.cancel()
                val token = s?.apiToken
                if (token == null) {
                    // Offline session: show what this device stored last time, if anything.
                    val stored = if (s != null && appState.isOffline.value) {
                        offlineStore.peek(cacheKey(s.username), ListSerializer(ApiTodo.serializer()))
                    } else {
                        null
                    }
                    _ui.update {
                        UiState(
                            loading = false,
                            todos = stored?.value.orEmpty().undoneFirst(),
                            offlineCopy = stored != null,
                            offline = s != null && appState.isOffline.value,
                            pending = it.pending,
                        )
                    }
                    return@collectLatest
                }
                _ui.update { it.copy(offline = false) }
                streamJob = launch { runFor(s.username, token) }
            }
        }
    }

    init {
        // Queued Todos for this account; when one leaves the queue it was sent, so reload to show
        // the server's copy in its place.
        viewModelScope.launch {
            combine(outbox.pending, appState.session) { items, s ->
                val user = s?.username?.trim()?.lowercase()
                items.filter { it.kind == OutboxItem.Kind.Todo && it.username == user }
            }.collect { mine ->
                val sentSome = mine.size < _ui.value.pending.size
                _ui.update { it.copy(pending = mine) }
                if (sentSome) refresh()
            }
        }
    }

    fun deletePending(item: OutboxItem) = outbox.remove(item.id)

    private fun cacheKey(username: String) = "todos-${username.lowercase()}"

    private suspend fun runFor(username: String, token: String) {
        load(username, token)
        sseClient.sseTodos(token)
            .catch { /* stream dropped for good — last loaded list stays, user can pull to refresh */ }
            .collect { list ->
                _ui.update { it.copy(loading = false, error = null, todos = list.undoneFirst(), offlineCopy = false) }
                offlineStore.save(cacheKey(username), list, ListSerializer(ApiTodo.serializer()))
            }
    }

    private suspend fun load(username: String, token: String) {
        _ui.update { it.copy(loading = it.todos.isEmpty(), error = null) }
        runCatching { backendClient.todos(username, token) }
            .onSuccess { list ->
                _ui.update { it.copy(loading = false, error = null, todos = list.undoneFirst(), offlineCopy = false) }
                offlineStore.save(cacheKey(username), list, ListSerializer(ApiTodo.serializer()))
            }
            .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message ?: "Unbekannter Fehler.") } }
    }

    /** Retry button / pull-to-refresh — the manual fallback for a dead SSE stream. */
    fun refresh() {
        val s = session.value ?: return
        val token = s.apiToken ?: return
        viewModelScope.launch { load(s.username, token) }
    }

    fun toggle(todo: ApiTodo) {
        val s = session.value ?: return
        val token = s.apiToken ?: return
        viewModelScope.launch {
            runCatching { backendClient.updateTodo(s.username, todo.id, !todo.done, token) }
            load(s.username, token)
        }
    }

    fun delete(todo: ApiTodo) {
        val s = session.value ?: return
        val token = s.apiToken ?: return
        // Optimistic removal so the swipe-dismiss doesn't snap back while the request is in flight.
        _ui.update { it.copy(todos = it.todos.filterNot { t -> t.id == todo.id }) }
        viewModelScope.launch {
            runCatching { backendClient.deleteTodo(s.username, todo.id, token) }
            load(s.username, token)
        }
    }

    fun addTodo(title: String, details: String, dueAt: String?, onDone: () -> Unit) {
        val s = session.value ?: return
        if (title.isBlank()) return
        val token = s.apiToken
        if (token == null) {
            // No server to send it to (offline session): queue it, it goes out on reconnect.
            if (!appState.isOffline.value) return
            outbox.enqueue(OutboxItem.Kind.Todo, s.username, title, details, dueAt)
            onDone()
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true) }
            val failure = runCatching { backendClient.createTodo(s.username, title, details, dueAt, token) }
                .exceptionOrNull()
            // The connection dropped mid-request: don't lose what was typed.
            if (failure is AppError && failure.isNetwork) {
                outbox.enqueue(OutboxItem.Kind.Todo, s.username, title, details, dueAt)
            }
            _ui.update { it.copy(submitting = false) }
            onDone()
            load(s.username, token)
        }
    }

    /** `todos.sorted { !$0.done && $1.done }` — undone first, otherwise original order kept. */
    private fun List<ApiTodo>.undoneFirst(): List<ApiTodo> = sortedBy { it.done }
}
