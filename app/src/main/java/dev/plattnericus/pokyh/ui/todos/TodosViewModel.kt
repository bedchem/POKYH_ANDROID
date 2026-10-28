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
        /** The last add failed (not a dropped connection): the sheet stays open and says so. */
        val addError: String? = null,
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
                            todos = stored?.value.orEmpty().arranged(),
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
                _ui.update { it.copy(loading = false, error = null, todos = list.arranged(), offlineCopy = false) }
                offlineStore.save(cacheKey(username), list, ListSerializer(ApiTodo.serializer()))
            }
    }

    private suspend fun load(username: String, token: String) {
        _ui.update { it.copy(loading = it.todos.isEmpty(), error = null) }
        runCatching { backendClient.todos(username, token) }
            .onSuccess { list ->
                _ui.update { it.copy(loading = false, error = null, todos = list.arranged(), offlineCopy = false) }
                offlineStore.save(cacheKey(username), list, ListSerializer(ApiTodo.serializer()))
            }
            .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message ?: dev.plattnericus.pokyh.core.i18n.I18n.s.common.unknownError) } }
    }

    /** Retry button / pull-to-refresh — the manual fallback for a dead SSE stream. */
    fun refresh() {
        val s = session.value ?: return
        val token = s.apiToken ?: return
        viewModelScope.launch { load(s.username, token) }
    }

    /** Ticks it off (or opens it again) at once; put back if the server says no. */
    fun toggle(todo: ApiTodo) {
        val s = session.value ?: return
        val token = s.apiToken ?: return
        val done = !todo.done
        val doneAt = if (done) java.time.Instant.now().toString() else null
        _ui.update { st -> st.copy(todos = st.todos.map { if (it.id == todo.id) it.copy(done = done, doneAt = doneAt) else it }.arranged()) }
        viewModelScope.launch {
            runCatching { backendClient.updateTodo(s.username, todo.id, done, token, doneAt) }
                .onFailure { _ui.update { st -> st.copy(todos = st.todos.map { if (it.id == todo.id) todo else it }.arranged()) } }
        }
    }

    fun clearAddError() = _ui.update { it.copy(addError = null) }

    /** Saves a change at once here; the server's copy comes back through the live stream. */
    fun editTodo(todo: ApiTodo, title: String, details: String, dueAt: String?, onDone: () -> Unit) {
        val s = session.value ?: return
        val token = s.apiToken ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true, addError = null) }
            runCatching { backendClient.editTodo(s.username, todo.id, title, details, dueAt, token) }
                .onSuccess {
                    _ui.update { st -> st.copy(submitting = false, todos = st.todos.map { if (it.id == todo.id) it.copy(title = title, details = details, dueAt = dueAt) else it }.arranged()) }
                    onDone()
                }
                .onFailure { e -> _ui.update { it.copy(submitting = false, addError = e.message ?: dev.plattnericus.pokyh.core.i18n.I18n.s.tasks.saveFailed) } }
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
            _ui.update { it.copy(submitting = true, addError = null) }
            val failure = runCatching { backendClient.createTodo(s.username, title, details, dueAt, token) }
                .exceptionOrNull()
            // The connection dropped mid-request: don't lose what was typed.
            if (failure is AppError && failure.isNetwork) {
                outbox.enqueue(OutboxItem.Kind.Todo, s.username, title, details, dueAt)
            } else if (failure != null) {
                // Refused: the sheet stays open with what was typed, and says so (as the web).
                _ui.update { it.copy(submitting = false, addError = failure.message?.takeIf { m -> m.isNotBlank() } ?: dev.plattnericus.pokyh.core.i18n.I18n.s.tasks.saveFailed) }
                return@launch
            }
            _ui.update { it.copy(submitting = false) }
            onDone()
            load(s.username, token)
        }
    }

    /**
     * As on the web: a done item leaves the list a day after it was ticked off; the open ones by
     * due date (none last), then newest first; the done ones after them, last ticked off first.
     */
    private fun List<ApiTodo>.arranged(): List<ApiTodo> {
        val now = System.currentTimeMillis()
        fun ms(raw: String?) = raw?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() ?: runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        val kept = filter { !it.done || (ms(it.doneAt)?.let { at -> now - at < 86_400_000L } ?: true) }
        val open = kept.filter { !it.done }.sortedWith(compareBy<ApiTodo> { ms(it.dueAt) ?: Long.MAX_VALUE }.thenByDescending { ms(it.createdAt) ?: 0L })
        val done = kept.filter { it.done }.sortedByDescending { ms(it.doneAt) ?: 0L }
        return open + done
    }
}
