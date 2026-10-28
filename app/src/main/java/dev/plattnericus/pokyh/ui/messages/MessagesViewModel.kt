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
import dev.plattnericus.pokyh.data.storage.OfflineStore
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

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
    private val offlineStore: OfflineStore,
) : ViewModel() {

    // ── Folder list (MessagesView) ──────────────────────────────────────────

    data class ListUiState(
        val folder: MessageFolder = MessageFolder.Inbox,
        val cache: Map<MessageFolder, List<MessagePreview>> = emptyMap(),
        val loadingFolders: Set<MessageFolder> = emptySet(),
        val error: String? = null,
        val markingAll: Boolean = false,
        /** The search: the loaded list filtered at once, WebUntis' own results after a pause. */
        val query: String = "",
        val searchResults: List<MessagePreview>? = null,
        val searching: Boolean = false,
        /** Select mode (long press): what is picked to delete. */
        val selected: Set<Int> = emptySet(),
        val deleting: Boolean = false,
    ) {
        val messages: List<MessagePreview>
            get() {
                val all = cache[folder].orEmpty()
                val q = query.trim()
                if (q.isEmpty() || folder == MessageFolder.Drafts) return all
                val words = MessageSearch.words(q)
                val local = all.filter { MessageSearch.matches(it, words) }
                val remote = searchResults.orEmpty().filter { r -> local.none { it.id == r.id } }
                return local + remote
            }
        val selecting: Boolean get() = selected.isNotEmpty()

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
        _list.update { it.copy(folder = folder, selected = emptySet(), searchResults = null) }
        load(folder)
        if (_list.value.query.isNotBlank()) setQuery(_list.value.query)
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun setQuery(value: String) {
        _list.update { it.copy(query = value, searchResults = null) }
        searchJob?.cancel()
        val q = value.trim()
        val folder = _list.value.folder
        val session = appState.session.value ?: return
        if (q.length < 2 || folder == MessageFolder.Drafts) return
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(350)
            _list.update { it.copy(searching = true) }
            val found = runCatching { untisClient.messages(session, folder, q) }.getOrNull()
            _list.update { if (it.query.trim() == q && it.folder == folder) it.copy(searchResults = found, searching = false) else it.copy(searching = false) }
        }
    }

    fun toggleSelected(id: Int) = _list.update { st ->
        st.copy(selected = if (id in st.selected) st.selected - id else st.selected + id)
    }

    fun selectAll() = _list.update { st ->
        val ids = st.messages.map { it.id }.toSet()
        st.copy(selected = if (st.selected.containsAll(ids)) emptySet() else ids)
    }

    fun clearSelection() = _list.update { it.copy(selected = emptySet()) }

    /** Deletes what is selected; what WebUntis refused stays (and stays selected). */
    fun deleteSelected() {
        val session = appState.session.value ?: return
        val st = _list.value
        val ids = st.selected.toList()
        if (ids.isEmpty() || st.deleting) return
        _list.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            val deleted = runCatching { untisClient.deleteMessages(session, ids) }.getOrElse { emptyList() }
            _list.update { s ->
                s.copy(
                    deleting = false,
                    selected = s.selected - deleted.toSet(),
                    cache = s.cache.mapValues { (_, list) -> list.filterNot { it.id in deleted } },
                    searchResults = s.searchResults?.filterNot { it.id in deleted },
                    error = if (deleted.size < ids.size) dev.plattnericus.pokyh.core.i18n.I18n.s.messages.deleteFailed else null,
                )
            }
        }
    }

    /** One message from its page; [onDone] when it's gone. */
    fun deleteMessage(id: Int, onDone: () -> Unit) {
        val session = appState.session.value ?: return
        viewModelScope.launch {
            val deleted = runCatching { untisClient.deleteMessages(session, listOf(id)) }.getOrElse { emptyList() }
            if (id in deleted) onDone() else _detail.update { it.copy(deleteFailed = true) }
        }
    }

    fun retry() = load(_list.value.folder, force = true)

    private fun load(folder: MessageFolder, force: Boolean = false) {
        val session = appState.session.value ?: return
        _list.update { it.copy(loadingFolders = it.loadingFolders + folder, error = null) }
        viewModelScope.launch {
            // The stored folder at once; the fresh one replaces it.
            if (_list.value.cache[folder] == null) {
                runCatching {
                    offlineStore.peek("messages-${session.username.lowercase()}-${folder.rawValue}", ListSerializer(MessagePreview.serializer()))
                }.getOrNull()?.let { stored -> _list.update { st -> if (st.cache[folder] == null) st.copy(cache = st.cache + (folder to stored.value)) else st } }
            }
            try {
                val result = offlineStore.load(
                    key = "messages-${session.username.lowercase()}-${folder.rawValue}",
                    serializer = ListSerializer(MessagePreview.serializer()),
                ) { untisClient.messages(session, folder) }.value
                _list.update { it.copy(cache = it.cache + (folder to result), loadingFolders = it.loadingFolders - folder) }
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _list.update {
                    it.copy(
                        loadingFolders = it.loadingFolders - folder,
                        error = if (e.isSessionExpired) dev.plattnericus.pokyh.core.i18n.I18n.s.common.sessionExpired else e.message,
                    )
                }
            } catch (e: Exception) {
                _list.update { it.copy(loadingFolders = it.loadingFolders - folder, error = e.message ?: dev.plattnericus.pokyh.core.i18n.I18n.s.common.unknownError) }
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
        appState.messageWasRead(ids = listOf(msg.id))
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
        appState.messageWasRead(ids.size, ids)
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
        val deleteFailed: Boolean = false,
        val confirming: Boolean = false,
        val confirmFailed: Boolean = false,
        val revoking: Boolean = false,
        val revokeFailed: Boolean = false,
        /** The read status sheet: null closed; empty list while it loads. */
        val readStatus: List<dev.plattnericus.pokyh.data.model.ReadStatusRecipient>? = null,
        val readStatusLoading: Boolean = false,
        val readStatusFailed: Boolean = false,
    )

    private val _detail = MutableStateFlow(DetailUiState())
    val detail: StateFlow<DetailUiState> = _detail.asStateFlow()

    fun loadDetail(id: Int, outgoing: Boolean = false) {
        val session = appState.session.value ?: return
        _detail.value = DetailUiState(loading = true)
        viewModelScope.launch {
            try {
                val d = untisClient.messageDetail(session, id, outgoing)
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
                val msg = if (e.isSessionExpired) dev.plattnericus.pokyh.core.i18n.I18n.s.common.sessionExpired else e.message
                _detail.value = DetailUiState(loading = false, error = msg)
            } catch (e: Exception) {
                _detail.value = DetailUiState(loading = false, error = e.message ?: dev.plattnericus.pokyh.core.i18n.I18n.s.common.unknownError)
            }
        }
    }

    /** "Tippen zum erneuten Laden": the attachment list again. */
    fun reloadAttachments() {
        val session = appState.session.value ?: return
        val d = _detail.value.detail ?: return
        _detail.update { it.copy(attachmentsLoading = true) }
        viewModelScope.launch {
            val extra = runCatching { untisClient.messageAttachments(session, d.id) }.getOrDefault(emptyList())
            _detail.update { st -> st.copy(detail = st.detail?.copy(attachments = extra), attachmentsLoading = false) }
        }
    }

    fun confirmRead() {
        val session = appState.session.value ?: return
        val d = _detail.value.detail ?: return
        _detail.update { it.copy(confirming = true, confirmFailed = false) }
        viewModelScope.launch {
            runCatching { untisClient.confirmMessageRead(session, d.id) }
                .onSuccess {
                    _detail.update { st ->
                        st.copy(confirming = false, detail = st.detail?.copy(confirmation = dev.plattnericus.pokyh.data.model.ReadConfirmation(false, java.time.OffsetDateTime.now().toString(), null)))
                    }
                }
                .onFailure { _detail.update { it.copy(confirming = false, confirmFailed = true) } }
        }
    }

    fun openReadStatus() {
        val session = appState.session.value ?: return
        val d = _detail.value.detail ?: return
        _detail.update { it.copy(readStatus = emptyList(), readStatusLoading = true, readStatusFailed = false) }
        viewModelScope.launch {
            runCatching { untisClient.messageReadStatus(session, d.id) }
                .onSuccess { list -> _detail.update { it.copy(readStatus = list, readStatusLoading = false) } }
                .onFailure { _detail.update { it.copy(readStatusLoading = false, readStatusFailed = true) } }
        }
    }

    fun closeReadStatus() = _detail.update { it.copy(readStatus = null) }

    fun revoke() {
        val session = appState.session.value ?: return
        val d = _detail.value.detail ?: return
        _detail.update { it.copy(revoking = true, revokeFailed = false) }
        viewModelScope.launch {
            runCatching { untisClient.revokeMessage(session, d.id) }
                .onSuccess { _detail.update { st -> st.copy(revoking = false, detail = st.detail?.copy(canRevoke = false, isRevoked = true)) } }
                .onFailure { _detail.update { it.copy(revoking = false, revokeFailed = true) } }
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
                    downloadError = if (file == null) dev.plattnericus.pokyh.core.i18n.I18n.s.messages.attachmentFailed else null,
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
        /** Answering this message: WebUntis fixes the recipient (the sender) and prefills the subject. */
        val replyTo: Int? = null,
        /** Going on with this draft: saved in its place, deleted once sent. */
        val draftId: Int? = null,
        val draftLoading: Boolean = false,
        val replyRecipient: String? = null,
        val replyLoading: Boolean = false,
    ) {
        val busy: Boolean get() = sending || savingDraft || sent || draftSaved || replyLoading || draftLoading
        val isReply: Boolean get() = replyTo != null
    }

    private val _compose = MutableStateFlow(ComposeUiState())
    val compose: StateFlow<ComposeUiState> = _compose.asStateFlow()

    fun openCompose() {
        _compose.value = ComposeUiState(open = true)
        loadRecipients()
    }

    /** "Antworten": the reply form from WebUntis, which also says whether replies are allowed. */
    fun openReply(messageId: Int) {
        val session = appState.session.value ?: return
        _compose.value = ComposeUiState(open = true, replyTo = messageId, replyLoading = true)
        viewModelScope.launch {
            try {
                val form = untisClient.replyForm(session, messageId)
                _compose.update {
                    it.copy(replyLoading = false, replyRecipient = form.recipientName, subject = form.subject.take(255))
                }
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _compose.update {
                    it.copy(
                        replyLoading = false,
                        error = if (e.isSessionExpired) dev.plattnericus.pokyh.core.i18n.I18n.s.common.sessionExpired else e.message,
                    )
                }
            } catch (e: Exception) {
                _compose.update { it.copy(replyLoading = false, error = dev.plattnericus.pokyh.core.i18n.I18n.s.messages.replyFailed) }
            }
        }
    }

    /** A draft opens in the form again, to go on with it (as on the web). */
    fun openDraft(id: Int) {
        val session = appState.session.value ?: return
        _compose.value = ComposeUiState(open = true, draftId = id, draftLoading = true)
        loadRecipients()
        viewModelScope.launch {
            try {
                val d = untisClient.draft(session, id)
                _compose.update { it.copy(draftLoading = false, subject = d.subject.take(255), content = d.content.take(10_000), selected = d.recipients.take(1)) }
            } catch (e: AppError) {
                if (e.isSessionExpired) appState.handleSessionExpired()
                _compose.update { it.copy(draftLoading = false, error = e.message) }
            } catch (e: Exception) {
                _compose.update { it.copy(draftLoading = false, error = e.message ?: dev.plattnericus.pokyh.core.i18n.I18n.s.common.unknownError) }
            }
        }
    }

    fun deleteDraft() {
        val session = appState.session.value ?: return
        val id = _compose.value.draftId ?: return
        viewModelScope.launch {
            val deleted = runCatching { untisClient.deleteMessages(session, listOf(id)) }.getOrElse { emptyList() }
            if (id in deleted) {
                _compose.update { it.copy(open = false) }
                _list.update { s -> s.copy(cache = s.cache.mapValues { (_, list) -> list.filterNot { it.id == id } }) }
            } else {
                _compose.update { it.copy(error = dev.plattnericus.pokyh.core.i18n.I18n.s.messages.deleteFailed) }
            }
        }
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
        st.copy(selected = if (isSelected) emptyList() else listOf(recipient), error = null)
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
            state.isReply && state.replyRecipient == null -> return fileError(dev.plattnericus.pokyh.core.i18n.I18n.s.messages.noReply)
            !state.isReply && state.selected.isEmpty() -> return fileError(dev.plattnericus.pokyh.core.i18n.I18n.s.messages.pickTeacher)
            state.subject.isBlank() -> return fileError(dev.plattnericus.pokyh.core.i18n.I18n.s.messages.needSubject)
            state.content.isBlank() -> return fileError(dev.plattnericus.pokyh.core.i18n.I18n.s.messages.needText)
        }
        submit(asDraft = false)
    }

    fun saveDraft() {
        val state = _compose.value
        if (state.busy) return
        if (state.subject.isBlank() && state.content.isBlank() && state.selected.isEmpty() && state.files.isEmpty()) {
            return fileError(dev.plattnericus.pokyh.core.i18n.I18n.s.messages.needSomething)
        }
        submit(asDraft = true)
    }

    private fun submit(asDraft: Boolean) {
        val session = appState.session.value ?: return
        val state = _compose.value
        _compose.update { it.copy(sending = !asDraft, savingDraft = asDraft, error = null) }
        viewModelScope.launch {
            try {
                val replyTo = state.replyTo
                if (replyTo != null && !asDraft) {
                    untisClient.sendReply(
                        session = session,
                        messageId = replyTo,
                        subject = state.subject.trim(),
                        content = state.content.trim(),
                        attachments = state.files,
                    )
                } else {
                    untisClient.sendMessage(
                        session = session,
                        subject = state.subject.trim(),
                        content = state.content.trim(),
                        recipients = state.selected,
                        attachments = state.files,
                        asDraft = asDraft,
                        draftId = state.draftId,
                    )
                }
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
                        error = if (e.isSessionExpired) dev.plattnericus.pokyh.core.i18n.I18n.s.common.sessionExpired else e.message,
                    )
                }
            } catch (e: Exception) {
                _compose.update { it.copy(sending = false, savingDraft = false, error = e.message ?: dev.plattnericus.pokyh.core.i18n.I18n.s.common.unknownError) }
            }
        }
    }
}

/** The web's message search: accent-folded, every word must appear in subject, sender or preview. */
internal object MessageSearch {
    private fun fold(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun words(q: String): List<String> = fold(q).split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(m: dev.plattnericus.pokyh.data.model.MessagePreview, words: List<String>): Boolean {
        val hay = fold(m.subject + " " + m.senderName + " " + m.contentPreview)
        return words.all { it in hay }
    }
}
