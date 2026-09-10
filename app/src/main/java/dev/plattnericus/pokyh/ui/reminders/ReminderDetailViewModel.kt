package dev.plattnericus.pokyh.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.backend.SseClient
import dev.plattnericus.pokyh.data.model.ApiComment
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.UserSession
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

/**
 * ReminderDetailView.swift, ported. iOS receives the already-loaded `ApiReminder` + `classId`
 * straight from `RemindersView`'s `NavigationLink`; the Android nav graph only carries the
 * `reminderId` string arg (see `PokyhDestinations.REMINDER_DETAIL`), so this re-derives both from
 * [BackendClient.reminders] (there is no single-reminder GET) and keeps them live via
 * [SseClient.sseReminders] / [SseClient.sseReminderComments].
 */
@HiltViewModel
class ReminderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appState: AppState,
    private val backendClient: BackendClient,
    private val sseClient: SseClient,
) : ViewModel() {

    private val reminderId: String = checkNotNull(savedStateHandle["reminderId"])

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val reminder: ApiReminder? = null,
        val comments: List<CommentUiItem> = emptyList(),
        val currentUserId: String = "",
        val isAdmin: Boolean = false,
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
                if (token == null) {
                    _ui.value = UiState(loading = false)
                    return@collectLatest
                }
                if (classId == null) {
                    _ui.value = UiState(loading = false, error = "Keine Klasse gefunden.")
                    return@collectLatest
                }
                _ui.update { it.copy(currentUserId = s.stableUid ?: s.username) }
                streamJob = launch { runFor(classId, token) }
            }
        }
    }

    private suspend fun runFor(classId: String, token: String) = coroutineScope {
        runCatching { backendClient.me(token) }.onSuccess { user -> _ui.update { it.copy(isAdmin = user.isAdmin) } }
        launch {
            loadReminder(classId, token)
            sseClient.sseReminders(classId, token)
                .catch { /* stream dropped for good — last loaded reminder stays on screen */ }
                .collect { list ->
                    list.find { it.id == reminderId }
                        ?.let { r -> _ui.update { it.copy(loading = false, error = null, reminder = r) } }
                }
        }
        launch {
            loadComments(classId, token)
            sseClient.sseReminderComments(reminderId, token)
                .catch { /* stream dropped for good — last loaded comments stay on screen */ }
                .collect { list -> _ui.update { it.copy(comments = list.map { c -> c.toCommentUiItem() }) } }
        }
    }

    private suspend fun loadReminder(classId: String, token: String) {
        runCatching { backendClient.reminders(classId, token) }
            .onSuccess { list ->
                val r = list.find { it.id == reminderId }
                _ui.update {
                    it.copy(loading = false, error = if (r == null) "Erinnerung nicht gefunden." else null, reminder = r ?: it.reminder)
                }
            }
            .onFailure { e -> _ui.update { it.copy(loading = false, error = e.message ?: "Unbekannter Fehler.") } }
    }

    private suspend fun loadComments(classId: String, token: String) {
        runCatching { backendClient.reminderComments(classId, reminderId, token) }
            .onSuccess { list -> _ui.update { it.copy(comments = list.map { c -> c.toCommentUiItem() }) } }
    }

    /** Retry button — the manual fallback for a dead SSE stream / failed initial load. */
    fun refresh() {
        val s = appState.session.value ?: return
        val token = s.apiToken ?: return
        val classId = s.classId ?: return
        viewModelScope.launch {
            loadReminder(classId, token)
            loadComments(classId, token)
        }
    }

    fun addComment(body: String) {
        val s = appState.session.value ?: return
        val token = s.apiToken ?: return
        val classId = s.classId ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            runCatching { backendClient.createReminderComment(classId, reminderId, body, token) }
            loadComments(classId, token)
        }
    }

    fun deleteComment(comment: CommentUiItem) {
        val s = appState.session.value ?: return
        val token = s.apiToken ?: return
        val classId = s.classId ?: return
        // Optimistic removal so the UI doesn't wait on the round-trip.
        _ui.update { it.copy(comments = it.comments.filterNot { c -> c.id == comment.id }) }
        viewModelScope.launch {
            runCatching { backendClient.deleteReminderComment(classId, reminderId, comment.id, token) }
            loadComments(classId, token)
        }
    }

    private fun ApiComment.toCommentUiItem(): CommentUiItem = CommentUiItem(
        id = id,
        authorId = stableUid,
        authorName = username,
        body = body,
        createdAtEpochMs = (parseRemindAt(createdAt) ?: java.time.Instant.EPOCH).toEpochMilli(),
        editedAtEpochMs = updatedAt?.takeIf { it != createdAt }?.let { parseRemindAt(it)?.toEpochMilli() },
    )
}
