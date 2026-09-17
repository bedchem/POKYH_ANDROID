package dev.plattnericus.pokyh.state

import android.os.SystemClock
import dev.plattnericus.pokyh.core.notifications.PokyhNotifications
import dev.plattnericus.pokyh.core.widgets.WidgetDataBridge
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.MessageFolder
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.core.status.PokyhService
import dev.plattnericus.pokyh.core.status.NetworkMonitor
import dev.plattnericus.pokyh.core.status.ServiceHealth
import dev.plattnericus.pokyh.core.status.ServiceState
import dev.plattnericus.pokyh.core.status.unreachable
import dev.plattnericus.pokyh.data.storage.DiskCache
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import dev.plattnericus.pokyh.data.storage.SecureCredentialStore
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.ui.navigation.AppTab
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Central app state (Store.swift `AppState`): auth phase, multi-account, the offline-restore
 * race, auto-lock. A plain Hilt singleton — deliberately **not** a ViewModel — so every screen's
 * `@HiltViewModel` can inject this exact same instance; `AppState` outlives any one screen or
 * back-stack entry, same as `@StateObject private var app = AppState()` living above the whole
 * `WindowGroup` on iOS.
 *
 * Store split across the two real data-layer stores (not one do-everything credential store):
 *  - [SecureCredentialStore]: ONLY the encrypted username/password blob (Keystore AES-GCM).
 *  - [PreferencesStore]: the account LIST (`accounts`), `defaultAccount`, `lastActive`, `themeMode`
 *    — plain DataStore-backed bookkeeping, no secrets.
 */
@Singleton
class AppState @Inject constructor(
    private val untisClient: UntisClient,
    private val backendClient: BackendClient,
    private val credentialStore: SecureCredentialStore,
    private val prefsStore: PreferencesStore,
    private val diskCache: DiskCache,
    private val notifications: PokyhNotifications,
    private val widgetDataBridge: WidgetDataBridge,
    private val serviceHealth: ServiceHealth,
    private val networkMonitor: NetworkMonitor,
) {
    /** `AppState.Phase` (Store.swift) — which top-level screen currently owns the UI. */
    sealed interface Phase {
        data object Login : Phase
        data object Lock : Phase
        data object Authed : Phase
    }

    // ── Coroutine scopes ──────────────────────────────────────────────────────
    /** Drives ordinary state-mutating work. Lives as long as the process (this is a singleton). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Hosts logins that must keep running after an offline-timeout race gives up waiting on
     * them — NEVER cancelled by [withTimeoutOrNull] losing the race (Store.swift `race(_:timeout:)`
     * "der Login-Task läuft bei Timeout weiter"). */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Published state ──────────────────────────────────────────────────────

    /** Resolved once accounts have loaded from disk — see [isReady]. Login until proven otherwise. */
    private val _phase = MutableStateFlow<Phase>(Phase.Login)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    private val _accounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    /**
     * Unread inbox count, for the badge on the Messages action in every tab-root header.
     *
     * Populated by [syncNotifications] (which already fetches the inbox to decide what to notify
     * about, so this costs nothing extra) and decremented locally by [messageWasRead] when the
     * user opens one — otherwise the badge would sit there stale until the next sync.
     */
    private val _unreadMessages = MutableStateFlow(0)
    val unreadMessages: StateFlow<Int> = _unreadMessages.asStateFlow()

    /** Called when messages are opened or marked read, so the badge drops immediately. */
    fun messageWasRead(count: Int = 1) {
        _unreadMessages.value = (_unreadMessages.value - count).coerceAtLeast(0)
    }

    /** Usernames with a stored password — sync snapshot for UI badges ("Passwort nötig"),
     * refreshed whenever [accounts] changes or a credential is saved/deleted. */
    private val _accountsWithPassword = MutableStateFlow<Set<String>>(emptySet())

    private val _defaultUsername = MutableStateFlow<String?>(null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Offline mode active (cached data shown, real login didn't make it in time). */
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    /**
     * Which servers are answering, for the app-wide status strip.
     *
     * Re-exposed from [ServiceHealth] here because the shell already takes an `AppState` and
     * nothing else about it is a ViewModel — threading a second singleton through the composable
     * tree to say one thing would be ceremony.
     */
    val serviceStates: StateFlow<Map<PokyhService, ServiceState>> get() = serviceHealth.states

    /** Whether the phone itself has a usable network — see [NetworkMonitor]. */
    val deviceOnline: StateFlow<Boolean> get() = networkMonitor.online

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** LoginView.swift `.onAppear { app.error = nil }` — called when [dev.plattnericus.pokyh.ui.
     * login.LoginScreen] (re)appears, so a stale error from a previous failed attempt doesn't
     * flash on a freshly (re)opened "Konto hinzufügen" sheet. */
    fun clearError() {
        _error.value = null
    }

    private val _selectedTab = MutableStateFlow(AppTab.Home)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    private val _backendStatus = MutableStateFlow<BackendStatus>(BackendStatus.Unknown)
    val backendStatus: StateFlow<BackendStatus> = _backendStatus.asStateFlow()

    /** Bumped whenever the Timetable tab is (re-)selected → the screen jumps back to "today". */
    private val _timetableHomeSignal = MutableStateFlow(0)
    val timetableHomeSignal: StateFlow<Int> = _timetableHomeSignal.asStateFlow()

    private val _themeMode = MutableStateFlow(PokyhThemeMode.System)
    val themeMode: StateFlow<PokyhThemeMode> = _themeMode.asStateFlow()

    /** Add-account / re-enter-password sheet, mirroring iOS's `showAddAccount`/`prefillUsername`. */
    private val _showAddAccount = MutableStateFlow(false)
    val showAddAccount: StateFlow<Boolean> = _showAddAccount.asStateFlow()

    private val _prefillUsername = MutableStateFlow<String?>(null)
    val prefillUsername: StateFlow<String?> = _prefillUsername.asStateFlow()

    /** Flips true once accounts + theme have loaded — MainActivity keeps the splash screen up
     * until then so the very first frame never flashes the wrong phase/light-dark scheme. */
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** One-shot signal for MainActivity to launch the POST_NOTIFICATIONS system prompt — flipped
     * true by [onAuthenticated] the first time ever (mirrors iOS's `pokyh_notif_asked` UserDefaults
     * flag/`requestNotificationPermissionIfNeeded`), back to false once MainActivity has acted on it. */
    private val _requestNotifPermission = MutableStateFlow(false)
    val requestNotifPermission: StateFlow<Boolean> = _requestNotifPermission.asStateFlow()

    fun notifPermissionRequested() {
        _requestNotifPermission.value = false
    }

    init {
        scope.launch { prefsStore.themeMode.collect { _themeMode.value = it } }
        scope.launch { prefsStore.defaultAccount.collect { _defaultUsername.value = it } }
        scope.launch {
            prefsStore.accounts.collect { list ->
                _accounts.value = list
                _accountsWithPassword.value = list.map { it.username }
                    .filter { credentialStore.hasCredentialsSync(it) }
                    .toSet()
            }
        }
        scope.launch {
            // Wait for the first real snapshot before deciding Login vs. Lock, so a returning
            // user with saved accounts never flashes the Login screen first.
            val first = prefsStore.accounts.first()
            _phase.value = if (first.isEmpty()) Phase.Login else Phase.Lock
            _isReady.value = true
        }
        // Back online while signed in on the stored session → sign in for real in the background,
        // so the POKYH token (and with it the outbox) comes back without the user doing anything.
        scope.launch {
            var wasOnline = networkMonitor.online.value
            networkMonitor.online.collect { online ->
                // Requests that failed while the phone had no network say nothing about the
                // servers. Without this the banner kept claiming "WebUntis antwortet nicht" after
                // the connection came back, until some screen happened to load again.
                if (online && !wasOnline) serviceHealth.forgetOutages()
                wasOnline = online
                if (online) retryOfflineLogin()
            }
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * New/manual login (LoginView "Anmelden").
     *
     * **Falls back to the cached session only for an account this device already knows, and only
     * when the typed password matches the stored one.** A brand-new account has nothing cached
     * and nothing to check a password against, so it waits for the real round-trip however long
     * that takes — but someone who types the right password for an account they are already
     * signed into should not be locked out of their own timetable because WebUntis is down. That
     * is the same trust the lock screen already places in the Keystore-encrypted store when it
     * restores a session behind a fingerprint; typing the password is, if anything, the stronger
     * proof of the two.
     */
    suspend fun login(username: String, password: String, save: Boolean = true) {
        val stored = credentialStore.getCredentials(username)?.second
        // The verifier covers a signed-out account: its password is gone, the hash is not.
        val known = (stored != null && stored == password) || credentialStore.verifyOfflinePassword(username, password)
        performLogin(username, password, save, allowOffline = known)
    }

    /** Re-login for an already-saved account (Face-ID unlock, silent relaunch) — races
     * [OFFLINE_TIMEOUT_MS] against the real login when a usable cached snapshot exists. */
    private suspend fun loginSaved(username: String) {
        val password = credentialStore.getCredentials(username)?.second
        if (password == null) {
            // No password stored (was signed out) → offer password re-entry.
            _prefillUsername.value = username
            _showAddAccount.value = true
            _busy.value = false
            _statusText.value = ""
            return
        }
        performLogin(username, password, save = false, allowOffline = true)
    }

    /** Call once the UI has already run [dev.plattnericus.pokyh.core.biometric.
     * BiometricAuthenticator] successfully — `AppState` has no Activity/Fragment context of its
     * own to host the biometric prompt, so that step happens at the UI layer, not here. Loads
     * the target account's session (racing the offline timeout like any saved-account login) and
     * unlocks into it. */
    suspend fun unlockWithBiometrics(username: String? = null) {
        _error.value = null
        val target = username ?: _defaultUsername.value ?: _accounts.value.firstOrNull()?.username
        if (target == null) {
            _phase.value = Phase.Login
            return
        }
        scope.launch { prefsStore.setLastActive(target) }
        loginSaved(target)
    }

    private suspend fun performLogin(username: String, password: String, save: Boolean, allowOffline: Boolean = false) {
        // Started from the add-account/re-auth sheet? Then the sheet-dismiss sequencing in
        // [finalize] applies (Store.swift `viaSheet`).
        val viaSheet = _showAddAccount.value
        _busy.value = true
        _error.value = null
        _statusText.value = "Verbinde mit WebUntis…"

        val snap: UserSession? = if (allowOffline) diskCache.read(sessionKey(username), UserSession.serializer())?.takeIf { it.studentId > 0 } else null
        // A fingerprint unlock right after returning to the app beats Android releasing the
        // network, and that login failed instantly into "Offline angemeldet" on working Wi-Fi.
        networkMonitor.awaitSettled()
        val startedAt = System.currentTimeMillis()
        val untisAnswered = java.util.concurrent.atomic.AtomicBoolean(false)
        val loginDeferred = backgroundScope.async { buildSession(username, password, untisAnswered) }

        if (snap == null) {
            // No offline candidate → just wait for the real answer, however long it takes.
            try {
                finalize(loginDeferred.await(), save, password, viaSheet)
            } catch (e: Throwable) {
                // Say which of the two it was. “Keine Verbindung” on an account that simply
                // has no stored copy yet reads as a bug in the app; the real answer is that
                // there is nothing to open offline until it has been opened online once.
                _error.value = if (e.isNetworkError() && !allowOffline) {
                    "Ohne Internet lässt sich nur ein Konto öffnen, das auf diesem Gerät schon " +
                        "einmal angemeldet war — mit dem dort gespeicherten Passwort."
                } else {
                    errorMessage(e)
                }
            }
            _busy.value = false
            _statusText.value = ""
            return
        }

        // **WebUntis answering means the connection is fine**, so a slow POKYH backend (a cold
        // server, a long token exchange) gets extra time instead of dropping a working phone into
        // offline mode — that is how "Offline angemeldet" used to appear on good Wi-Fi.
        val raced = withTimeoutOrNull(OFFLINE_TIMEOUT_MS) { runCatching { loginDeferred.await() } }
            ?: if (untisAnswered.get()) {
                withTimeoutOrNull(BACKEND_GRACE_MS) { runCatching { loginDeferred.await() } }
            } else {
                null
            }
        if (raced == null) {
            // Timed out → offline snapshot now; the real login keeps running in [backgroundScope]
            // and silently upgrades the session if/when it eventually succeeds.
            enterOffline(snap, viaSheet, startedAt)
            offlineLogin = OfflineLogin(snap.username, password, save)
            reconnectJob = backgroundScope.launch {
                runCatching { loginDeferred.await() }.getOrNull()?.let { upgradeFromBackground(it, save, password) }
            }
            return
        }
        raced.fold(
            onSuccess = { s -> finalize(s, save, password, viaSheet) },
            onFailure = { e ->
                // Fast network failure (e.g. airplane mode) + cache → also go offline.
                if (e.isNetworkError()) {
                    enterOffline(snap, viaSheet, startedAt)
                    offlineLogin = OfflineLogin(snap.username, password, save)
                } else {
                    _error.value = errorMessage(e)
                }
            },
        )
        _busy.value = false
        _statusText.value = ""
    }

    /** WebUntis login → POKYH-only backend fallback → POKYH backend account. Sets [backendStatus]. */
    private suspend fun buildSession(
        username: String,
        password: String,
        untisAnswered: java.util.concurrent.atomic.AtomicBoolean? = null,
    ): UserSession {
        var s: UserSession
        var backendOnly = false
        try {
            s = untisClient.login(username, password)
            untisAnswered?.set(true)
        } catch (untisError: Throwable) {
            // No (working) WebUntis account → try a direct POKYH-backend login with the same
            // credentials (the backend also knows pure-POKYH accounts).
            s = try {
                val backend = backendClient.login(username, password)
                _backendStatus.value = BackendStatus.Ok
                backendOnly = true
                backendOnlySession(backend)
            } catch (backendError: Throwable) {
                // Both failed. The backend message is more informative for a POKYH account
                // (e.g. "invalid credentials"); on a pure network failure, pass the original
                // WebUntis error through instead.
                throw if (backendError.isNetworkError()) untisError else backendError
            }
        }

        // Resolve/create the POKYH backend account for student AND parent sessions (never
        // teacher/admin). A backend-only session above already has its tokens.
        if (!backendOnly) {
            s = if (s.isStudent || s.isParent) {
                val role = if (s.isParent) "parent" else "student"
                when (val outcome = backendClient.loginWithUntis(s.username, s.klasseId, s.klasseName, role)) {
                    is BackendClient.UntisLoginResult.Ok -> {
                        var upgraded = s.copy(apiToken = outcome.token, apiRefresh = outcome.refresh)
                        runCatching { backendClient.me(outcome.token) }.getOrNull()?.let { user ->
                            upgraded = upgraded.copy(stableUid = user.stableUid, classId = user.classId)
                        }
                        _backendStatus.value = BackendStatus.Ok
                        upgraded
                    }
                    is BackendClient.UntisLoginResult.NoClass -> {
                        _backendStatus.value = BackendStatus.NoClass
                        s
                    }
                    is BackendClient.UntisLoginResult.Failed -> {
                        _backendStatus.value = BackendStatus.Failed(outcome.message)
                        s
                    }
                }
            } else {
                _backendStatus.value = BackendStatus.NotStudent
                s
            }
        }
        return s
    }

    /** Successful online login: persist account + offline snapshot, then swap in the session. */
    private suspend fun finalize(s: UserSession, save: Boolean, password: String, viaSheet: Boolean) {
        if (save) {
            credentialStore.saveCredentials(s.username, password)
            upsertAccount(SavedAccount(username = s.username, displayName = s.klasseName.ifEmpty { s.username }, nickname = null, imageUrl = s.imageUrl))
        } else {
            // Silent re-login: still backfill the profile image so the account list / lock
            // screen show the cached picture even when the password wasn't re-saved.
            updateAccountImage(s.username, s.imageUrl)
            credentialStore.ensureOfflineVerifier(s.username, password)
        }
        prefsStore.setLastActive(s.username)
        if (s.hasUntis) diskCache.write(sessionKey(s.username), offlineSnapshot(s), UserSession.serializer())

        if (viaSheet) {
            // Close the add-account sheet and let its dismiss animation finish BEFORE swapping the
            // session — swapping too early collides with the sheet's own transition (Store.swift).
            _showAddAccount.value = false
            _busy.value = false
            _statusText.value = ""
            delay(420)
        }
        _session.value = s
        _phase.value = Phase.Authed
        _showAddAccount.value = false
        _isOffline.value = false
        onAuthenticated()
    }

    // ── Nach erfolgreichem Login: Benachrichtigungen einrichten ───────────────
    // Store.swift `onAuthenticated()` — bewusst erst NACH dem Login (UI bereits aktiv), nicht
    // beim App-Start.
    private fun onAuthenticated() {
        scope.launch {
            if (!prefsStore.notifAsked.first()) {
                prefsStore.setNotifAsked(true)
                _requestNotifPermission.value = true
            }
        }
        scope.launch { syncNotifications() }
    }

    /** Store.swift `syncNotifications()` — messages + reminders on every call (throttled to at
     * most once/minute), grades + cancelled-lesson checks additionally throttled to every 30 min
     * (heavier: grades load per-subject). [lastNotifSync]/[lastHeavySync] reset with the process,
     * same as iOS's in-memory `Date.distantPast`-seeded timestamps. */
    private var lastNotifSync = 0L
    private var lastHeavySync = 0L

    private suspend fun syncNotifications() {
        val s = _session.value ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotifSync <= NOTIF_SYNC_THROTTLE_MS) return
        lastNotifSync = now

        if (s.hasUntis) {
            runCatching { untisClient.messages(s, MessageFolder.Inbox) }.getOrNull()
                ?.let { _unreadMessages.value = notifications.checkNewMessages(it) }
        }
        val token = s.apiToken
        val classId = s.classId
        if (token != null && classId != null) {
            runCatching { backendClient.reminders(classId, token) }.getOrNull()
                ?.let { notifications.scheduleReminders(it) }
        }

        if (s.hasUntis && now - lastHeavySync > HEAVY_SYNC_INTERVAL_MS) {
            lastHeavySync = now
            if (s.isStudent) {
                runCatching { untisClient.grades(s, s.studentId, null) }.getOrNull()?.let {
                    notifications.checkNewGrades(it)
                    if (isDefaultAccountActive()) widgetDataBridge.publishGrades(it)
                }
            }
            runCatching { untisClient.timetable(s, s.studentId, SchoolDates.mondayIso()) }.getOrNull()
                ?.let { notifications.checkTimetableChanges(it) }
        }
    }

    /** A WebUntis call came back with an expired/invalid session (`AppError.sessionExpired`,
     * detected by [dev.plattnericus.pokyh.data.untis.UntisClient]'s HTML/auth-error sniffing) —
     * drop the live session and bounce to Lock (saved accounts exist → biometric re-login) or
     * Login (none saved). Store.swift `handleSessionExpired()`, called from every screen's
     * WebUntis load catch block the same way. */
    fun handleSessionExpired() {
        offlineLogin = null
        _session.value = null
        _isOffline.value = false
        _phase.value = if (_accounts.value.isEmpty()) Phase.Login else Phase.Lock
    }

    /** Switch into offline mode: show the cached (token-less) session. */
    private fun enterOffline(snap: UserSession, viaSheet: Boolean, loginStartedAt: Long) {
        _busy.value = false
        _statusText.value = ""
        if (viaSheet) _showAddAccount.value = false
        _session.value = snap
        _phase.value = Phase.Authed
        _isOffline.value = true
        // The snapshot carries no backend token, so without this every POKYH feature fell through
        // to "benötigt ein POKYH-Konto" — true of no account that has ever signed in online.
        _backendStatus.value = if (snap.isStudent || snap.isParent) BackendStatus.Offline else BackendStatus.NotStudent
        // Reaching this line *means* WebUntis did not answer the login — either it failed or it
        // ran past the offline timeout. Say so straight away instead of waiting for the
        // interceptor's second strike, which offline may never arrive: the banner explaining why
        // this session is cached has to be on screen from the moment the session is.
        serviceHealth.markDown(PokyhService.UNTIS, "Anmeldung nicht möglich", unlessOkSince = loginStartedAt)
        scope.launch { prefsStore.setLastActive(snap.username) }
        keepReconnecting()
    }

    private var healJob: Job? = null

    /**
     * Retry the real sign-in on a backoff for as long as the session is offline.
     *
     * The other triggers — the network coming back, the app returning to the foreground — only
     * fire on a *change*. A login that failed while the network already counted as online (a
     * slow wake from Doze, a stale DNS answer, a server hiccup) had no change left to wait for,
     * so the app sat in "Offline angemeldet" on working Wi-Fi until it was closed and reopened.
     */
    private fun keepReconnecting() {
        if (healJob?.isActive == true) return
        healJob = scope.launch {
            var wait = RECONNECT_FIRST_MS
            while (_isOffline.value) {
                delay(wait)
                wait = (wait * 2).coerceAtMost(RECONNECT_MAX_MS)
                if (backgroundedAt != null || !networkMonitor.usableNow()) continue
                retryOfflineLogin()
                reconnectJob?.join()
            }
        }
    }

    /** What an offline session was opened with — kept **in memory only**, so a reconnect can sign
     * in for real without asking again. Never written anywhere; gone with the process. */
    private data class OfflineLogin(val username: String, val password: String, val save: Boolean)

    private var offlineLogin: OfflineLogin? = null
    private var reconnectJob: Job? = null

    /**
     * Try the real sign-in again for the offline session.
     *
     * Before this, the only upgrade path was the one login that lost the offline race — and in
     * flight mode that one fails at once, so the session stayed offline (no POKYH token, nothing
     * syncing) until the user signed in again by hand. Called whenever the network comes back
     * and when the app returns to the foreground.
     */
    fun retryOfflineLogin() {
        if (!_isOffline.value) return
        val login = offlineLogin ?: return
        if (_session.value?.username != login.username) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = backgroundScope.launch {
            runCatching { buildSession(login.username, login.password) }.getOrNull()
                ?.let { upgradeFromBackground(it, login.save, login.password) }
        }
    }

    /** The background login that lost the offline race finally came back — upgrade silently,
     * without disrupting whatever the user is currently doing. */
    private suspend fun upgradeFromBackground(s: UserSession, save: Boolean, password: String) {
        if (_session.value?.username != s.username) return // Account switched away meanwhile?
        offlineLogin = null
        if (save) {
            credentialStore.saveCredentials(s.username, password)
            upsertAccount(SavedAccount(username = s.username, displayName = s.klasseName.ifEmpty { s.username }, nickname = null, imageUrl = s.imageUrl))
        }
        if (s.hasUntis) diskCache.write(sessionKey(s.username), offlineSnapshot(s), UserSession.serializer())
        _session.value = s
        _isOffline.value = false
        // The login that just succeeded went through WebUntis; clear the "Anmeldung nicht
        // möglich" mark from going offline instead of waiting for the next screen to load.
        if (s.hasUntis) serviceHealth.reportOk(PokyhService.UNTIS)
        onAuthenticated()
    }

    // ── Offline helpers ──────────────────────────────────────────────────────

    private fun sessionKey(username: String) = "session-${username.lowercase()}"

    /** Token-less snapshot for the offline restore — NEVER persist tokens/cookies to disk; a
     * real re-login brings those back, offline only needs the display data + cache keys. */
    private fun offlineSnapshot(s: UserSession): UserSession =
        s.copy(sessionId = "", bearerToken = "", apiToken = null, apiRefresh = null, stableUid = null, classId = null)

    /** Pure POKYH-backend session (no WebUntis) — WebUntis fields stay neutral → `hasUntis == false`. */
    private fun backendOnlySession(backend: dev.plattnericus.pokyh.data.model.AuthResponse): UserSession = UserSession(
        sessionId = "", bearerToken = "", studentId = 0, klasseId = 0,
        klasseName = backend.user.webuntisKlasseName ?: "",
        username = backend.user.username,
        personName = null, personType = null,
        isParent = backend.user.role == "parent",
        apiToken = backend.token, apiRefresh = backend.refreshToken,
        stableUid = backend.user.stableUid, classId = backend.user.classId,
        imageUrl = null,
    )

    /**
     * A login failure, phrased so it points at something the reader can act on.
     *
     * A raw `UnknownHostException` or an OkHttp timeout message on the login screen reads as
     * "the app is broken", when the actual situation is one of three very different things —
     * the phone has no connection, WebUntis is down, or the credentials are wrong — and only the
     * last one is answered by trying again. [ServiceHealth] already knows which server stopped
     * answering, so the message says it.
     */
    private fun errorMessage(e: Throwable): String {
        if (e is AppError) return e.message ?: "Unbekannter Fehler."
        if (!e.isNetworkError()) return e.message ?: "Unbekannter Fehler."
        val down = serviceHealth.states.value.unreachable()
        return when {
            down.isEmpty() -> "Keine Verbindung. Prüfe dein WLAN oder mobiles Netz."
            else -> "${down.joinToString(" & ") { it.label }} ist nicht erreichbar. " +
                "Versuch es später noch einmal."
        }
    }

    /**
     * Was this a connectivity failure?
     *
     * **Both forms count.** The clients catch [IOException] and rethrow it as an [AppError]
     * carrying a readable message, so by the time a login failure reaches here the original
     * type is usually gone. Testing only `is IOException` is what stopped a saved account from
     * signing in offline: the fallback to the stored session never fired, and the screen showed
     * the words “Keine Verbindung” while refusing to act on it.
     */
    private fun Throwable.isNetworkError(): Boolean = this is IOException || (this is AppError && isNetwork)

    // ── Account list bookkeeping (PreferencesStore-backed) ───────────────────

    private suspend fun upsertAccount(account: SavedAccount) {
        val current = _accounts.value.toMutableList()
        val idx = current.indexOfFirst { it.username == account.username }
        if (idx >= 0) {
            // Preserve a locally-set nickname across re-logins.
            current[idx] = account.copy(nickname = current[idx].nickname)
        } else {
            current.add(account)
        }
        prefsStore.setAccounts(current)
    }

    private fun updateAccountImage(username: String, imageUrl: String?) {
        if (imageUrl == null) return
        scope.launch {
            val updated = _accounts.value.map { if (it.username == username) it.copy(imageUrl = imageUrl) else it }
            prefsStore.setAccounts(updated)
        }
    }

    /** Usernames a profile-image lookup has already been attempted for this process, so a screen
     * that re-composes (or five tab roots showing the same avatar) can't fan out into repeated
     * app-data fetches. A failed attempt counts — retrying per recomposition would be worse than
     * showing initials. */
    private val profileImageAttempted = mutableSetOf<String>()

    /**
     * Resolves the signed-in user's WebUntis profile picture if login didn't already capture it.
     *
     * Login only fetches app-data when it still needs something (an unresolved class, a guardian
     * with no student list), so a normal student session arrives with `imageUrl == null` even
     * though the account has a picture. This fills that in on demand — once per account per
     * process — and updates both the live session and the saved-account entry.
     *
     * Safe to call from UI composition: it returns immediately when there's nothing to do.
     */
    fun ensureProfileImageUrl() {
        val current = _session.value ?: return
        if (current.imageUrl != null) return
        if (!current.hasUntis || current.sessionId.isBlank()) return
        if (!profileImageAttempted.add(current.username)) return

        scope.launch {
            val resolved = runCatching { untisClient.fetchProfileImageUrl(current) }.getOrNull()
            if (resolved.isNullOrBlank()) return@launch
            // Account may have been switched away while app-data was in flight.
            val latest = _session.value ?: return@launch
            if (latest.username != current.username) return@launch
            _session.value = latest.copy(imageUrl = resolved)
            updateAccountImage(latest.username, resolved)
        }
    }

    // ── Account switching / management ──────────────────────────────────────

    /** Clean switch with a loading state; the caller (UI) is expected to have already confirmed
     * biometrics before calling this, mirroring [unlockWithBiometrics]. */
    suspend fun switchAccount(username: String) {
        if (username == _session.value?.username) return
        if (!accountHasPassword(username)) {
            // No saved password → straight to password re-entry (no biometrics needed for that).
            _prefillUsername.value = username
            _showAddAccount.value = true
            return
        }
        _busy.value = true
        _statusText.value = "Bestätige Identität…"
        loginSaved(username)
    }

    fun accountHasPassword(username: String): Boolean = credentialStore.hasCredentialsSync(username)

    /** Widgets show the **default account**'s data — with none set, the single-user case falls
     * back to whichever account is currently active. Store.swift `isDefaultAccountActive`. */
    fun isDefaultAccountActive(): Boolean {
        val active = _session.value?.username ?: return false
        return _defaultUsername.value == null || _defaultUsername.value == active
    }

    /** Opens the "add account" flow — a screens-phase LoginScreen reacts to [showAddAccount]. */
    fun addAccount(prefillUsername: String? = null) {
        _prefillUsername.value = prefillUsername
        _showAddAccount.value = true
    }

    /** Signs an account out (drops only its saved password) — the account stays listed. */
    fun logout(username: String) {
        scope.launch { credentialStore.deleteCredentials(username) }
        _accountsWithPassword.value = _accountsWithPassword.value - username
        if (_session.value?.username == username) {
            offlineLogin = null
            _session.value = null
            _isOffline.value = false
            // The next account gets a clean slate: this one's outage is not theirs, and an
            // amber bar left over from a signed-out session would be explaining nothing.
            serviceHealth.reset()
            _phase.value = if (_accounts.value.isEmpty()) Phase.Login else Phase.Lock
        }
    }

    /** Removes an account from the device entirely. */
    fun removeAccount(username: String) {
        scope.launch {
            credentialStore.deleteCredentials(username)
            credentialStore.deleteOfflineVerifier(username)
            prefsStore.setAccounts(_accounts.value.filterNot { it.username == username })
        }
        if (_session.value?.username == username) {
            _session.value = null
            _isOffline.value = false
            serviceHealth.reset()
        }
    }

    /**
     * Everything was just wiped from the device — drop every trace of the signed-in state and go
     * to Login.
     *
     * [removeAccount] alone was not enough: it nulls the session but leaves [phase] at Authed,
     * so "Alle Daten löschen" left the user inside the app with no session behind it.
     */
    fun resetAfterDataWipe() {
        offlineLogin = null
        _session.value = null
        _isOffline.value = false
        _backendStatus.value = BackendStatus.Unknown
        _accounts.value = emptyList()
        _accountsWithPassword.value = emptySet()
        _defaultUsername.value = null
        _unreadMessages.value = 0
        _showAddAccount.value = false
        _error.value = null
        _selectedTab.value = AppTab.Home
        serviceHealth.reset()
        _phase.value = Phase.Login
    }

    /** Widgets/notifications use the default account's data; `null` clears it (single-user case
     * then just falls back to whichever account is active). */
    fun setDefaultAccount(username: String?) {
        scope.launch { prefsStore.setDefaultAccount(username) }
    }

    /** Sets/clears (`null`/blank) a local nickname for an account. */
    fun renameAccount(username: String, nickname: String?) {
        scope.launch {
            val updated = _accounts.value.map {
                if (it.username == username) it.copy(nickname = nickname?.trim()?.takeIf { n -> n.isNotEmpty() }) else it
            }
            prefsStore.setAccounts(updated)
        }
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    /** RootTabView's `tabSelection` binding, ported: selecting Timetable always bumps
     * [timetableHomeSignal] — even on a re-tap of an already-selected tab — so the screen jumps
     * back to "today". */
    fun selectTab(tab: AppTab) {
        if (tab == AppTab.Timetable) _timetableHomeSignal.value += 1
        _selectedTab.value = tab
    }

    private val _timetableJump = MutableStateFlow<Int?>(null)

    /** A pending "show the timetable at this date" (yyyyMMdd), consumed by the timetable screen. */
    val timetableJump: StateFlow<Int?> = _timetableJump.asStateFlow()

    /**
     * Switch to the Stundenplan tab showing the week (and, in day mode, the day) of [dateNum].
     * Deliberately does not bump [timetableHomeSignal] — that would jump straight back to today.
     */
    fun openTimetableAt(dateNum: Int) {
        _timetableJump.value = dateNum
        _selectedTab.value = AppTab.Timetable
    }

    fun consumeTimetableJump() {
        _timetableJump.value = null
    }

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun setThemeMode(mode: PokyhThemeMode) {
        _themeMode.value = mode
        scope.launch { prefsStore.setThemeMode(mode) }
    }

    // ── Auto-lock after time spent backgrounded ───────────────────────────────

    private var backgroundedAt: Long? = null

    /** Bumped on every "was backgrounded, now foregrounded" transition that DIDN'T hard-lock —
     * [HomeViewModel] reloads on each bump so a WebUntis session that quietly expired while the
     * app sat in the background/app-switcher gets caught (→ [handleSessionExpired]) as soon as
     * the user comes back, instead of only on the next manual pull-to-refresh. No iOS equivalent
     * (Store.swift only re-checks the hard-lock timer on `appDidBecomeActive`) — an Android-side
     * strengthening of the same "don't leave a dead session on screen" intent. */
    private val _resumeSignal = MutableStateFlow(0)
    val resumeSignal: StateFlow<Int> = _resumeSignal.asStateFlow()

    /** Wired to `ON_STOP` by MainActivity via `ProcessLifecycleOwner`. */
    fun onAppBackgrounded() {
        networkMonitor.onBackgrounded()
        backgroundedAt = if (_phase.value == Phase.Authed) SystemClock.elapsedRealtime() else null
    }

    /** Wired to `ON_START`. Force-locks if the app spent >= [AUTO_LOCK_INTERVAL_MS] backgrounded,
     * otherwise bumps [resumeSignal] so still-authed screens can silently revalidate. */
    fun onAppForegrounded() {
        networkMonitor.onForegrounded()
        val since = backgroundedAt
        backgroundedAt = null
        if (since == null || _phase.value != Phase.Authed) return
        if (SystemClock.elapsedRealtime() - since >= AUTO_LOCK_INTERVAL_MS) {
            _phase.value = Phase.Lock
        } else {
            // Android releases a backgrounded app's network a moment after it is back on screen;
            // reloading before that just fails and leaves the old data (and a banner) up.
            scope.launch {
                networkMonitor.awaitSettled()
                _resumeSignal.value += 1
                retryOfflineLogin()
            }
        }
    }

    private companion object {
        /** Time without a login answer before falling back to the offline snapshot. */
        const val OFFLINE_TIMEOUT_MS = 5_000L

        /** Extra wait once WebUntis has answered but the POKYH backend has not yet. */
        const val BACKEND_GRACE_MS = 15_000L

        /** Backoff for [keepReconnecting]: first retry, doubling up to the cap. */
        const val RECONNECT_FIRST_MS = 3_000L
        const val RECONNECT_MAX_MS = 60_000L

        /** Time spent backgrounded before auto-locking — 10 minutes, same as iOS. */
        const val AUTO_LOCK_INTERVAL_MS = 600_000L

        /** [syncNotifications] throttle — at most once/minute, same as iOS's `lastNotifSync`. */
        const val NOTIF_SYNC_THROTTLE_MS = 60_000L

        /** Grades/cancelled-lesson check throttle within [syncNotifications] — every 30 min,
         * same as iOS's `heavySyncInterval` (grades load per-subject, too heavy for the 60-s tick). */
        const val HEAVY_SYNC_INTERVAL_MS = 1_800_000L
    }
}
