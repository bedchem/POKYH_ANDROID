package dev.plattnericus.pokyh.data.sync

import dev.plattnericus.pokyh.core.status.NetworkMonitor
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.storage.DiskCache
import dev.plattnericus.pokyh.state.AppState
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A Todo or Erinnerung created without a server to send it to. */
@Serializable
data class OutboxItem(
    val id: String,
    /** The account it was created in — only that account's session may send it. */
    val username: String,
    val kind: Kind,
    val title: String,
    /** Todo `details` / Erinnerung `body`. */
    val text: String,
    /** Todo `dueAt` / Erinnerung `remindAt`. */
    val date: String?,
    val createdAt: Long,
) {
    @Serializable
    enum class Kind { Todo, Reminder }
}

/**
 * New Todos and Erinnerungen that are waiting for a connection.
 *
 * **Sending does not depend on a screen being open.** The outbox watches the session and the
 * network itself: the moment there is a POKYH token (an offline session upgrading, or a normal
 * sign-in) or the phone comes back online, it sends whatever is queued for that account. The
 * screens only read [pending] to show the queued items in their lists.
 *
 * Persisted on disk, so a queued item survives the app being closed before the internet returns.
 * A request the server *rejects* is dropped rather than retried forever — only a connection
 * failure keeps an item queued.
 */
@Singleton
class Outbox @Inject constructor(
    private val diskCache: DiskCache,
    private val backendClient: BackendClient,
    private val appState: AppState,
    private val networkMonitor: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val serializer = ListSerializer(OutboxItem.serializer())

    private val _pending = MutableStateFlow<List<OutboxItem>>(emptyList())
    val pending: StateFlow<List<OutboxItem>> = _pending.asStateFlow()

    private var started = false

    /** Called once from the Application. Loads the queue and starts watching for a chance to send. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            _pending.value = diskCache.read(KEY, serializer).orEmpty()
            combine(appState.session, networkMonitor.online) { s, online -> Triple(s?.username, s?.apiToken, online) }
                .distinctUntilChanged()
                .collect { (_, token, online) -> if (token != null && online) flush() }
        }
    }

    fun enqueue(kind: OutboxItem.Kind, username: String, title: String, text: String, date: String?) {
        val item = OutboxItem(
            id = UUID.randomUUID().toString(),
            username = username.trim().lowercase(),
            kind = kind,
            title = title,
            text = text,
            date = date,
            createdAt = System.currentTimeMillis(),
        )
        scope.launch {
            mutex.withLock { persist(_pending.value + item) }
            // Maybe the connection is already back — no reason to wait for the next change.
            flush()
        }
    }

    fun remove(id: String) {
        scope.launch { mutex.withLock { persist(_pending.value.filterNot { it.id == id }) } }
    }

    /** Send everything queued for the signed-in account. Safe to call any time. */
    fun flush() {
        scope.launch {
            mutex.withLock {
                val session = appState.session.value ?: return@withLock
                val token = session.apiToken ?: return@withLock
                val mine = _pending.value.filter { it.username == session.username.trim().lowercase() }
                for (item in mine) {
                    val sent = try {
                        when (item.kind) {
                            OutboxItem.Kind.Todo ->
                                backendClient.createTodo(session.username, item.title, item.text, item.date, token)
                            OutboxItem.Kind.Reminder -> {
                                // No class yet (still resolving, or none at all): keep it for later.
                                val classId = session.classId ?: continue
                                backendClient.createReminder(classId, item.title, item.text, item.date.orEmpty(), token)
                            }
                        }
                        true
                    } catch (e: Exception) {
                        if (e.isConnectionProblem()) return@withLock // still offline — keep the rest
                        true // rejected by the server: drop it rather than retry forever
                    }
                    if (sent) persist(_pending.value.filterNot { it.id == item.id })
                }
            }
        }
    }

    /** "Alle Daten löschen" — the queue goes with everything else. */
    fun clear() {
        scope.launch { mutex.withLock { persist(emptyList()) } }
    }

    private suspend fun persist(list: List<OutboxItem>) {
        _pending.value = list
        diskCache.write(KEY, list, serializer)
    }

    /** Worth retrying later: no connection, the server is struggling, or the token expired
     * (a fresh sign-in brings a new one). Anything else is the request itself being refused. */
    private fun Throwable.isConnectionProblem(): Boolean {
        if (this is IOException) return true
        if (this !is AppError) return false
        val code = httpCode
        return isNetwork || code == null || code >= 500 || code == 401 || code == 408 || code == 429
    }

    companion object {
        /** File name `outbox_v1.json` — "Cache leeren" keeps it (it is not a cache). */
        const val KEY = "outbox-v1"
    }
}
