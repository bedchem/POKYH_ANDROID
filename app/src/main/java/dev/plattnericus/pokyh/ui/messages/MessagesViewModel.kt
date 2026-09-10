package dev.plattnericus.pokyh.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.MessageDetail
import dev.plattnericus.pokyh.data.model.MessageFolder
import dev.plattnericus.pokyh.data.model.MessagePreview
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MessagesView.swift + MessageDetailScreen, ported. One view model backs both screens (each
 * gets its own instance via `hiltViewModel()` scoped to its own back-stack entry) — the folder
 * list state and the single-message detail state are independent slices below.
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val appState: AppState,
    private val untisClient: UntisClient,
) : ViewModel() {

    // ── Folder list (MessagesView) ──────────────────────────────────────────

    data class ListUiState(
        val folder: MessageFolder = MessageFolder.Inbox,
        val cache: Map<MessageFolder, List<MessagePreview>> = emptyMap(),
        val loadingFolders: Set<MessageFolder> = emptySet(),
        val error: String? = null,
    ) {
        val messages: List<MessagePreview> get() = cache[folder].orEmpty()

        /** Full skeleton only when the folder has never loaded before (Swift `firstLoad`). */
        val firstLoad: Boolean get() = cache[folder] == null && loadingFolders.contains(folder)
    }

    private val _list = MutableStateFlow(ListUiState())
    val list: StateFlow<ListUiState> = _list.asStateFlow()

    init {
        load(_list.value.folder)
    }

    /** Picker selection (Swift `folder` binding) — always (re)loads, silently if already cached. */
    fun selectFolder(folder: MessageFolder) {
        if (folder == _list.value.folder) return
        _list.update { it.copy(folder = folder) }
        load(folder)
    }

    fun retry() = load(_list.value.folder, force = true)

    private fun load(folder: MessageFolder, force: Boolean = false) {
        val session = appState.session.value ?: return
        _list.update { it.copy(loadingFolders = it.loadingFolders + folder, error = null) }
        viewModelScope.launch {
            try {
                val result = untisClient.messages(session, folder)
                _list.update { it.copy(cache = it.cache + (folder to result), loadingFolders = it.loadingFolders - folder) }
            } catch (e: AppError) {
                _list.update {
                    it.copy(
                        loadingFolders = it.loadingFolders - folder,
                        error = if (e.isSessionExpired) "Sitzung abgelaufen. Bitte erneut anmelden." else e.message,
                    )
                }
            } catch (e: Exception) {
                _list.update { it.copy(loadingFolders = it.loadingFolders - folder, error = e.message ?: "Unbekannter Fehler.") }
            }
        }
    }

    /** Row tap on an unread inbox message — optimistic local read-flag + fire-and-forget backend call. */
    fun markRead(msg: MessagePreview) {
        val session = appState.session.value ?: return
        val folder = _list.value.folder
        if (folder != MessageFolder.Inbox || msg.isRead) return
        _list.update { st ->
            val updated = st.cache[folder]?.map { if (it.id == msg.id) it.copy(isRead = true) else it } ?: return@update st
            st.copy(cache = st.cache + (folder to updated))
        }
        viewModelScope.launch { runCatching { untisClient.markMessageRead(session, msg.id) } }
    }

    // ── Detail (MessageDetailScreen) ────────────────────────────────────────

    data class DetailUiState(
        val loading: Boolean = true,
        val error: String? = null,
        val detail: MessageDetail? = null,
    )

    private val _detail = MutableStateFlow(DetailUiState())
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    fun loadDetail(id: Int) {
        val session = appState.session.value ?: return
        _detail.value = DetailUiState(loading = true)
        viewModelScope.launch {
            try {
                val d = untisClient.messageDetail(session, id)
                _detail.value = DetailUiState(loading = false, detail = d)
            } catch (e: AppError) {
                val msg = if (e.isSessionExpired) "Sitzung abgelaufen. Bitte erneut anmelden." else e.message
                _detail.value = DetailUiState(loading = false, error = msg)
            } catch (e: Exception) {
                _detail.value = DetailUiState(loading = false, error = e.message ?: "Unbekannter Fehler.")
            }
        }
    }
}
