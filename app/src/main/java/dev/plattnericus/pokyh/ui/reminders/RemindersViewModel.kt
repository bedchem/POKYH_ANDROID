package dev.plattnericus.pokyh.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.notifications.PokyhNotifications
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.backend.SseClient
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val reminders: List<ApiReminder> = emptyList(),
        val submitting: Boolean = false,
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
                    _ui.value = UiState(loading = false)
                    return@collectLatest
                }
                streamJob = launch { runFor(classId, token) }
            }
        }
    }

    private suspend fun runFor(classId: String, token: String) {
        load(classId, token)
        sseClient.sseReminders(classId, token)
            .catch { /* stream dropped for good — last loaded list stays, user can pull to refresh */ }
            .collect { list ->
                _ui.update { it.copy(loading = false, error = null, reminders = list) }
                notifications.scheduleReminders(list)
            }
    }

    private suspend fun load(classId: String, token: String) {
        _ui.update { it.copy(loading = it.reminders.isEmpty(), error = null) }
        runCatching { backendClient.reminders(classId, token) }
            .onSuccess { list ->
                _ui.update { it.copy(loading = false, error = null, reminders = list) }
                notifications.scheduleReminders(list)
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
        val token = s.apiToken ?: return
        val classId = s.classId ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true) }
            runCatching { backendClient.createReminder(classId, title, body, remindAt, token) }
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
