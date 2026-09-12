package dev.plattnericus.pokyh.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.MessageAttachment
import dev.plattnericus.pokyh.data.model.MessageDetail
import dev.plattnericus.pokyh.data.model.MessageFolder
import dev.plattnericus.pokyh.data.model.MessagePreview
import dev.plattnericus.pokyh.data.model.MessageRecipient
import dev.plattnericus.pokyh.data.model.OutgoingAttachment
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
 * list state, the single-message detail state and the compose sheet are independent slices
 * below.
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
        val markingAll: Boolean = false,
    ) {
        val messages: List<MessagePreview> get() = cache[folder].orEmpty()

        /** Full skeleton only when the folder has never loaded before (Swift `firstLoad`). */
        val firstLoad: Boolean get() = cache[folder] == null && loadingFolders.contains(folder)

        /** Unread only exists in the inbox — sent/draft messages have no read state. */
        val unreadIds: List<Int>
            get() = if (folder == MessageFolder.Inbox) messages.filterNot { it.isRead }.map { it.id } else emptyList()
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
                if (e.isSessionExpired) appState.handleSessionExpired()
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
        appState.messageWasRead()
        viewModelScope.launch { runCatching { untisClient.markMessageRead(session, msg.id) } }
    }

    /**
     * "Alle als gelesen" — flips every unread inbox message at once. Optimistic like [markRead]:
     * the list and the nav badge update immediately, the backend calls run in parallel behind it.
     */
    fun markAllRead() {
        val session = appState.session.value ?: return
        val state = _list.value
        val ids = state.unreadIds
        if (ids.isEmpty() || state.markingAll) return

        _list.update { st ->
            val updated = st.cache[MessageFolder.Inbox]?.map { if (it.isRead) it else it.copy(isRead = true) }
            st.copy(
                markingAll = true,
                cache = if (updated == null) st.cache else st.cache + (MessageFolder.Inbox to updated),
            )
        }
        appState.messageWasRead(ids.size)
        viewModelScope.launch {
            runCatching { untisClient.markAllMessagesRead(session, ids) }
            _list.update { it.copy(markingAll = false) }
        }
    }

    // ── Detail (MessageDetailScreen) ────────────────────────────────────────

    data class DetailUiState(
        val loading: Boolean = true,
        val error: String? = null,
        val detail: MessageDetail? = null,
        /** The message said it has attachments but didn't list them — they're being re-fetched. */
        val attachmentsLoading: Boolean = false,
        val downloadingKey: String? = null,
        val downloadError: String? = null,
    )

    private val _detail = MutableStateFlow(DetailUiState())
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    fun loadDetail(id: Int) {
        val session = appState.session.value ?: return
        _detail.value = DetailUiState(loading = true)
        viewModelScope.launch {
            try {
                val d = untisClient.messageDetail(session, id)
                _detail.value = DetailUiState(loading = false, detail = d, attachmentsLoading = d.attachments.isEmpty())
                // Some instances answer the detail endpoint without the attachment list; the
                // dedicated endpoint still has it (same fallback as the web frontend).
                if (d.attachments.isEmpty()) {
                    val extra = runCatching { untisClient.messageAttachments(session, d.id) }.getOrDefault(emptyList())
                    _detail.update { st ->
                        val current = st.detail ?: return@update st.copy(attachmentsLoading = false)
                        st.copy(detail = current.copy(attachments = extra), attachmentsLoading = false)
                    }
                }
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                val msg = if (e.isSessionExpired) "Sitzung abgelaufen. Bitte erneut anmelden." else e.message
                _detail.value = DetailUiState(loading = false, error = msg)
            } catch (e: Exception) {
                _detail.value = DetailUiState(loading = false, error = e.message ?: "Unbekannter Fehler.")
            }
        }
    }

    /** Identifies one attachment row while it downloads (ids alone aren't unique across shapes). */
    fun attachmentKey(attachment: MessageAttachment): String =
        "${attachment.id}:${attachment.storageId}:${attachment.name}"

    /**
     * Fetches an attachment's bytes; [onFile] then hands them to the system viewer. Keeping the
     * `Context` work in the UI layer is deliberate — the view model stays Android-framework free.
     */
    fun downloadAttachment(
        attachment: MessageAttachment,
        onFile: (UntisClient.DownloadedAttachment) -> Unit,
    ) {
        val session = appState.session.value ?: return
        val messageId = _detail.value.detail?.id ?: return
        if (_detail.value.downloadingKey != null) return
        _detail.update { it.copy(downloadingKey = attachmentKey(attachment), downloadError = null) }
        viewModelScope.launch {
            val file = runCatching { untisClient.downloadAttachment(session, messageId, attachment) }.getOrNull()
            _detail.update {
                it.copy(
                    downloadingKey = null,
                    downloadError = if (file == null) "Der Anhang konnte nicht geladen werden." else null,
                )
            }
            if (file != null) onFile(file)
        }
    }

    // ── Compose (Mitteilung an Lehrkraft) ───────────────────────────────────

    data class ComposeUiState(
        val open: Boolean = false,
        val recipients: List<MessageRecipient> = emptyList(),
        val recipientsFailed: Boolean = false,
        val selected: List<MessageRecipient> = emptyList(),
        val subject: String = "",
        val content: String = "",
        val files: List<OutgoingAttachment> = emptyList(),
        val sending: Boolean = false,
        val savingDraft: Boolean = false,
        val error: String? = null,
        val sent: Boolean = false,
        val draftSaved: Boolean = false,
    ) {
        val busy: Boolean get() = sending || savingDraft || sent || draftSaved
    }

    private val _compose = MutableStateFlow(ComposeUiState())
    val compose: StateFlow<ComposeUiState> = _compose.asStateFlow()

    fun openCompose() {
        _compose.value = ComposeUiState(open = true)
        loadRecipients()
    }

    fun closeCompose() {
        _compose.update { it.copy(open = false) }
    }

    private fun loadRecipients() {
        val session = appState.session.value ?: return
        viewModelScope.launch {
            try {
                val list = untisClient.messageRecipients(session)
                _compose.update { it.copy(recipients = list, recipientsFailed = false) }
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _compose.update { it.copy(recipientsFailed = true) }
            } catch (_: Exception) {
                _compose.update { it.copy(recipientsFailed = true) }
            }
        }
    }

    fun toggleRecipient(recipient: MessageRecipient) = _compose.update { st ->
        val isSelected = st.selected.any { it.id == recipient.id && it.type == recipient.type }
        st.copy(
            selected = if (isSelected) {
                st.selected.filterNot { it.id == recipient.id && it.type == recipient.type }
            } else {
                st.selected + recipient
            },
            error = null,
        )
    }

    fun setSubject(value: String) = _compose.update { it.copy(subject = value.take(255), error = null) }

    fun setContent(value: String) = _compose.update { it.copy(content = value.take(10_000), error = null) }

    fun addFiles(files: List<OutgoingAttachment>) = _compose.update { st ->
        val known = st.files.map { "${it.name}:${it.bytes.size}" }.toSet()
        st.copy(files = st.files + files.filterNot { "${it.name}:${it.bytes.size}" in known }, error = null)
    }

    fun removeFile(index: Int) = _compose.update { st ->
        st.copy(files = st.files.filterIndexed { i, _ -> i != index })
    }

    fun fileError(message: String) = _compose.update { it.copy(error = message) }

    /** Sends the message, then reloads the open folder so a "Gesendet" view is up to date. */
    fun send() {
        val state = _compose.value
        if (state.busy) return
        when {
            state.selected.isEmpty() -> return fileError("Bitte mindestens eine Lehrkraft wählen.")
            state.subject.isBlank() -> return fileError("Bitte einen Betreff eingeben.")
            state.content.isBlank() -> return fileError("Bitte einen Text eingeben.")
        }
        submit(asDraft = false)
    }

    fun saveDraft() {
        val state = _compose.value
        if (state.busy) return
        if (state.subject.isBlank() && state.content.isBlank() && state.selected.isEmpty() && state.files.isEmpty()) {
            return fileError("Bitte gib einen Betreff, Text, Empfänger oder Anhang an.")
        }
        submit(asDraft = true)
    }

    private fun submit(asDraft: Boolean) {
        val session = appState.session.value ?: return
        val state = _compose.value
        _compose.update { it.copy(sending = !asDraft, savingDraft = asDraft, error = null) }
        viewModelScope.launch {
            try {
                untisClient.sendMessage(
                    session = session,
                    subject = state.subject.trim(),
                    content = state.content.trim(),
                    recipients = state.selected,
                    attachments = state.files,
                    asDraft = asDraft,
                )
                _compose.update {
                    it.copy(sending = false, savingDraft = false, sent = !asDraft, draftSaved = asDraft)
                }
                load(_list.value.folder, force = true)
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _compose.update {
                    it.copy(
                        sending = false,
                        savingDraft = false,
                        error = if (e.isSessionExpired) "Sitzung abgelaufen. Bitte erneut anmelden." else e.message,
                    )
                }
            } catch (e: Exception) {
                _compose.update { it.copy(sending = false, savingDraft = false, error = e.message ?: "Unbekannter Fehler.") }
            }
        }
    }
}
