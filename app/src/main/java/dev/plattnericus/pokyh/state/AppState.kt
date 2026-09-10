package dev.plattnericus.pokyh.state

import android.os.SystemClock
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.storage.DiskCache
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import dev.plattnericus.pokyh.data.storage.SecureCredentialStore
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.ui.navigation.AppTab
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
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

    /** Usernames with a stored password — sync snapshot for UI badges ("Passwort nötig"),
     * refreshed whenever [accounts] changes or a credential is saved/deleted. */
    private val _accountsWithPassword = MutableStateFlow<Set<String>>(emptySet())

    private val _defaultUsername = MutableStateFlow<String?>(null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Offline mode active (cached data shown, real login didn't make it in time). */
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /** New/manual login (LoginView "Anmelden"). Never offline-falls-back — there is nothing
     * cached yet for a brand-new account, so this always waits for the real round-trip. */
    suspend fun login(username: String, password: String, save: Boolean = true) {
        performLogin(username, password, save, allowOffline = false)
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
        val loginDeferred = backgroundScope.async { buildSession(username, password) }

        if (snap == null) {
            // No offline candidate → just wait for the real answer, however long it takes.
            try {
                finalize(loginDeferred.await(), save, password, viaSheet)
            } catch (e: Throwable) {
                _error.value = errorMessage(e)
            }
            _busy.value = false
            _statusText.value = ""
            return
        }

        val raced = withTimeoutOrNull(OFFLINE_TIMEOUT_MS) { runCatching { loginDeferred.await() } }
        if (raced == null) {
            // Timed out → offline snapshot now; the real login keeps running in [backgroundScope]
            // and silently upgrades the session if/when it eventually succeeds.
            enterOffline(snap, viaSheet)
            backgroundScope.launch {
                runCatching { loginDeferred.await() }.getOrNull()?.let { upgradeFromBackground(it, save, password) }
            }
            return
        }
        raced.fold(
            onSuccess = { s -> finalize(s, save, password, viaSheet) },
            onFailure = { e ->
                // Fast network failure (e.g. airplane mode) + cache → also go offline.
                if (e.isNetworkError()) enterOffline(snap, viaSheet) else _error.value = errorMessage(e)
            },
        )
        _busy.value = false
        _statusText.value = ""
    }

    /** WebUntis login → POKYH-only backend fallback → POKYH backend account. Sets [backendStatus]. */
    private suspend fun buildSession(username: String, password: String): UserSession {
        var s: UserSession
        var backendOnly = false
        try {
            s = untisClient.login(username, password)
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
    }

    /** Switch into offline mode: show the cached (token-less) session. */
    private fun enterOffline(snap: UserSession, viaSheet: Boolean) {
        _busy.value = false
        _statusText.value = ""
        if (viaSheet) _showAddAccount.value = false
        _session.value = snap
        _phase.value = Phase.Authed
        _isOffline.value = true
        scope.launch { prefsStore.setLastActive(snap.username) }
    }

    /** The background login that lost the offline race finally came back — upgrade silently,
     * without disrupting whatever the user is currently doing. */
    private suspend fun upgradeFromBackground(s: UserSession, save: Boolean, password: String) {
        if (_session.value?.username != s.username) return // Account switched away meanwhile?
        if (save) {
            credentialStore.saveCredentials(s.username, password)
            upsertAccount(SavedAccount(username = s.username, displayName = s.klasseName.ifEmpty { s.username }, nickname = null, imageUrl = s.imageUrl))
        }
        if (s.hasUntis) diskCache.write(sessionKey(s.username), offlineSnapshot(s), UserSession.serializer())
        _session.value = s
        _isOffline.value = false
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

    private fun errorMessage(e: Throwable): String = (e as? AppError)?.message ?: e.message ?: "Unbekannter Fehler."

    private fun Throwable.isNetworkError(): Boolean = this is IOException

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

    /** Opens the "add account" flow — a screens-phase LoginScreen reacts to [showAddAccount]. */
    fun addAccount() {
        _prefillUsername.value = null
        _showAddAccount.value = true
    }

    /** Signs an account out (drops only its saved password) — the account stays listed. */
    fun logout(username: String) {
        scope.launch { credentialStore.deleteCredentials(username) }
        _accountsWithPassword.value = _accountsWithPassword.value - username
        if (_session.value?.username == username) {
            _session.value = null
            _isOffline.value = false
            _phase.value = if (_accounts.value.isEmpty()) Phase.Login else Phase.Lock
        }
    }

    /** Removes an account from the device entirely. */
    fun removeAccount(username: String) {
        scope.launch {
            credentialStore.deleteCredentials(username)
            prefsStore.setAccounts(_accounts.value.filterNot { it.username == username })
        }
        if (_session.value?.username == username) {
            _session.value = null
            _isOffline.value = false
        }
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

    // ── Theme ─────────────────────────────────────────────────────────────────

    fun setThemeMode(mode: PokyhThemeMode) {
        _themeMode.value = mode
        scope.launch { prefsStore.setThemeMode(mode) }
    }

    // ── Auto-lock after time spent backgrounded ───────────────────────────────

    private var backgroundedAt: Long? = null

    /** Wired to `ON_STOP` by MainActivity via `ProcessLifecycleOwner`. */
    fun onAppBackgrounded() {
        backgroundedAt = if (_phase.value == Phase.Authed) SystemClock.elapsedRealtime() else null
    }

    /** Wired to `ON_START`. Force-locks if the app spent >= [AUTO_LOCK_INTERVAL_MS] backgrounded. */
    fun onAppForegrounded() {
        val since = backgroundedAt
        backgroundedAt = null
        if (since == null || _phase.value != Phase.Authed) return
        if (SystemClock.elapsedRealtime() - since >= AUTO_LOCK_INTERVAL_MS) {
            _phase.value = Phase.Lock
        }
    }

    private companion object {
        /** Time without a login answer before falling back to the offline snapshot. */
        const val OFFLINE_TIMEOUT_MS = 5_000L

        /** Time spent backgrounded before auto-locking — 10 minutes, same as iOS. */
        const val AUTO_LOCK_INTERVAL_MS = 600_000L
    }
}
