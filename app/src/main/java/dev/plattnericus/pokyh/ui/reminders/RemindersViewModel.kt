package dev.plattnericus.pokyh.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.notifications.PokyhNotifications
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.backend.SseClient
import dev.plattnericus.pokyh.data.model.ApiReminder
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
 * RemindersView.swift, ported. Backend-only feature (empty-session/no-class states are handled
 * by [dev.plattnericus.pokyh.ui.components.BackendUnavailableView] / the "Keine Klasse" empty
 * state in the screen). [UserSession.classId] already carries the POKYH class id resolved at
 * login, so unlike the iOS source (which re-fetches `myClass` here) this skips straight to
 * loading reminders for it. Loads once via [BackendClient.reminders], then keeps it live via
 * [SseClient.sseReminders]; if that stream never connects (or drops for good) the last
 * successfully fetched list just stays on screen — [refresh] is the manual fallback.
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val appState: AppState,
    private val backendClient: BackendClient,
    private val sseClient: SseClient,
    private val notifications: PokyhNotifications,
    private val offlineStore: OfflineStore,
    private val outbox: Outbox,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val reminders: List<ApiReminder> = emptyList(),
        val submitting: Boolean = false,
        /** Signed in offline and [reminders] is the copy stored on this device (read-only). */
        val offlineCopy: Boolean = false,
        /** Signed in offline: new Erinnerungen go to the outbox instead of the server. */
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
                val classId = s?.classId
                if (token == null || classId == null) {
                    // Offline session (no token, no class id in the snapshot): show what this
                    // device stored last time, if anything.
                    val stored = if (s != null && appState.isOffline.value) {
                        offlineStore.peek(cacheKey(s.username), ListSerializer(ApiReminder.serializer()))
                    } else {
                        null
                    }
                    _ui.update {
                        UiState(
                            loading = false,
                            reminders = stored?.value.orEmpty(),
                            offlineCopy = stored != null,
                            offline = s != null && token == null && appState.isOffline.value,
                            pending = it.pending,
                        )
                    }
                    return@collectLatest
                }
                _ui.update { it.copy(offline = false) }
                streamJob = launch { runFor(classId, token) }
            }
        }
    }

    init {
        // Queued Erinnerungen for this account; when one leaves the queue it was sent, so reload to
        // show the server's copy in its place.
        viewModelScope.launch {
            combine(outbox.pending, appState.session) { items, s ->
                val user = s?.username?.trim()?.lowercase()
                items.filter { it.kind == OutboxItem.Kind.Reminder && it.username == user }
            }.collect { mine ->
                val sentSome = mine.size < _ui.value.pending.size
                _ui.update { it.copy(pending = mine) }
                if (sentSome) refresh()
            }
        }
    }

    fun deletePending(item: OutboxItem) = outbox.remove(item.id)

    private fun cacheKey(username: String) = "reminders-${username.lowercase()}"

    private suspend fun store(list: List<ApiReminder>) {
        val username = appState.session.value?.username ?: return
        offlineStore.save(cacheKey(username), list, ListSerializer(ApiReminder.serializer()))
    }

    private suspend fun runFor(classId: String, token: String) {
        load(classId, token)
        sseClient.sseReminders(classId, token)
            .catch { /* stream dropped for good — last loaded list stays, user can pull to refresh */ }
            .collect { list ->
                _ui.update { it.copy(loading = false, error = null, reminders = list, offlineCopy = false) }
                notifications.scheduleReminders(list)
                store(list)
            }
    }

    private suspend fun load(classId: String, token: String) {
        _ui.update { it.copy(loading = it.reminders.isEmpty(), error = null) }
        runCatching { backendClient.reminders(classId, token) }
            .onSuccess { list ->
                _ui.update { it.copy(loading = false, error = null, reminders = list, offlineCopy = false) }
                notifications.scheduleReminders(list)
                store(list)
            }
            .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message ?: "Unbekannter Fehler.") } }
    }

    /** Retry button / pull-to-refresh — the manual fallback for a dead SSE stream. */
    fun refresh() {
        val s = session.value ?: return
        val token = s.apiToken ?: return
        val classId = s.classId ?: return
        viewModelScope.launch { load(classId, token) }
    }

    fun addReminder(title: String, body: String, remindAt: String, onDone: () -> Unit) {
        val s = session.value ?: return
        if (title.isBlank()) return
        val token = s.apiToken
        if (token == null) {
            // No server to send it to (offline session): queue it; the class id is taken from the
            // real session when it goes out.
            if (!appState.isOffline.value) return
            outbox.enqueue(OutboxItem.Kind.Reminder, s.username, title, body, remindAt)
            onDone()
            return
        }
        val classId = s.classId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true) }
            val failure = runCatching { backendClient.createReminder(classId, title, body, remindAt, token) }
                .exceptionOrNull()
            // The connection dropped mid-request: don't lose what was typed.
            if (failure is AppError && failure.isNetwork) {
                outbox.enqueue(OutboxItem.Kind.Reminder, s.username, title, body, remindAt)
            }
            _ui.update { it.copy(submitting = false) }
            onDone()
            load(classId, token)
        }
    }

    fun delete(reminder: ApiReminder) {
        val s = session.value ?: return
        val token = s.apiToken ?: return
        val classId = s.classId ?: return
        // Optimistic removal so the swipe-dismiss doesn't snap back while the request is in flight.
        _ui.update { it.copy(reminders = it.reminders.filterNot { r -> r.id == reminder.id }) }
        viewModelScope.launch {
            runCatching { backendClient.deleteReminder(classId, reminder.id, token) }
            load(classId, token)
        }
    }
}
