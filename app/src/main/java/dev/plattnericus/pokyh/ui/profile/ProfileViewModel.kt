package dev.plattnericus.pokyh.ui.profile

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import dev.plattnericus.pokyh.data.storage.SecureCredentialStore
import dev.plattnericus.pokyh.data.sync.Outbox
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import java.io.File
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backs both [ProfileScreen] and [ConnectionStatusScreen] — ProfileView.swift / the nested
 * ConnectionStatusView don't have their own view models on iOS (they read `app` directly), so
 * this exists purely to keep [AppState] plumbing + the few operations `AppState` doesn't expose
 * (per-row metadata refresh, cache wipe, diagnostics) out of the composables.
 *
 * A couple of iOS `AppState` members (`defaultUsername`, `refreshAccount`, `clearAllData`,
 * `classDiagnostics`) aren't part of the Android `AppState`'s public surface documented in
 * state/AppState.kt — those are reimplemented here directly against [PreferencesStore] /
 * [SecureCredentialStore] / [UntisClient] / [BackendClient], which this file is allowed to inject.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    val appState: AppState,
    private val untisClient: UntisClient,
    private val backendClient: BackendClient,
    private val secureCredentialStore: SecureCredentialStore,
    private val prefsStore: PreferencesStore,
    private val outbox: Outbox,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val session: StateFlow<UserSession?> = appState.session
    val backendStatus: StateFlow<BackendStatus> = appState.backendStatus
    val themeMode: StateFlow<PokyhThemeMode> = appState.themeMode

    private val _defaultUsername = MutableStateFlow<String?>(null)
    val defaultUsername: StateFlow<String?> = _defaultUsername.asStateFlow()

    private val _accountsWithPassword = MutableStateFlow<Set<String>>(emptySet())

    /** Local-only display overrides from [refreshAccount] (fresh class name / avatar for a
     * saved account) — never written back into [AppState], which owns the persisted list. */
    private val _accountOverrides = MutableStateFlow<Map<String, SavedAccount>>(emptyMap())

    private val _switchingUsername = MutableStateFlow<String?>(null)
    val switchingUsername: StateFlow<String?> = _switchingUsername.asStateFlow()

    private val _refreshingUsername = MutableStateFlow<String?>(null)
    val refreshingUsername: StateFlow<String?> = _refreshingUsername.asStateFlow()

    private val _switchError = MutableStateFlow<String?>(null)
    val switchError: StateFlow<String?> = _switchError.asStateFlow()

    private val _clearing = MutableStateFlow<ClearKind?>(null)
    val clearing: StateFlow<ClearKind?> = _clearing.asStateFlow()

    /** Sorted like ProfileView.swift `sortedAccounts`: Standard-Konto zuerst, dann das aktive
     * Konto, danach alphabetisch — mit lokalen [refreshAccount]-Overrides eingemischt. */
    val accounts: StateFlow<List<SavedAccount>> = combine(
        appState.accounts, appState.session, _defaultUsername, _accountOverrides,
    ) { list, activeSession, defaultUser, overrides ->
        list.map { acc ->
            overrides[acc.username]?.let { ov -> acc.copy(displayName = ov.displayName, imageUrl = ov.imageUrl) } ?: acc
        }.sortedWith(
            compareByDescending<SavedAccount> { it.username == defaultUser }
                .thenByDescending { it.username == activeSession?.username }
                .thenBy { it.title.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accountsWithPassword: StateFlow<Set<String>> = _accountsWithPassword.asStateFlow()

    init {
        viewModelScope.launch { prefsStore.defaultAccount.collect { _defaultUsername.value = it } }
        viewModelScope.launch {
            appState.accounts.collect { list ->
                _accountsWithPassword.value = list.map { it.username }
                    .filter { secureCredentialStore.getCredentials(it) != null }
                    .toSet()
            }
        }
    }

    // ── Konto-Aktionen ───────────────────────────────────────────────────────

    fun switchAccount(username: String, onSwitched: () -> Unit) {
        if (_switchingUsername.value != null) return
        viewModelScope.launch {
            _switchingUsername.value = username
            appState.switchAccount(username)
            _switchingUsername.value = null
            if (appState.session.value?.username == username) {
                onSwitched()
            } else if (!appState.showAddAccount.value) {
                appState.error.value?.let { _switchError.value = it }
            }
        }
    }

    fun dismissSwitchError() {
        _switchError.value = null
    }

    /** `null` entfernt das Standard-Konto (ProfileView.swift `app.defaultUsername = nil`). */
    fun setDefaultAccount(username: String?) {
        appState.setDefaultAccount(username)
        viewModelScope.launch { prefsStore.setDefaultAccount(username) }
    }

    fun renameAccount(username: String, nickname: String?) = appState.renameAccount(username, nickname)

    fun signOutAccount(username: String) = appState.logout(username)

    fun removeAccount(username: String) = appState.removeAccount(username)

    fun addAccount() = appState.addAccount()

    fun setThemeMode(mode: PokyhThemeMode) = appState.setThemeMode(mode)

    /** Best-effort Metadaten-Refresh für ein gespeichertes Konto — meldet sich mit dem
     * gespeicherten Passwort erneut an, um Klassennamen/Avatar aufzufrischen, OHNE die aktive
     * Sitzung zu wechseln (ProfileView.swift `refreshAccount` für ein nicht-aktives Konto). */
    fun refreshAccount(username: String) {
        if (_refreshingUsername.value != null) return
        viewModelScope.launch {
            _refreshingUsername.value = username
            try {
                val creds = secureCredentialStore.getCredentials(username)
                if (creds == null) {
                    _switchError.value = "Kein gespeichertes Passwort für $username."
                } else {
                    val fresh = untisClient.login(creds.first, creds.second)
                    val keptNickname = accounts.value.firstOrNull { it.username == username }?.nickname
                    _accountOverrides.value = _accountOverrides.value + (
                        username to SavedAccount(
                            username = username,
                            displayName = fresh.klasseName.ifEmpty { username },
                            nickname = keptNickname,
                            imageUrl = fresh.imageUrl,
                        )
                        )
                }
            } catch (e: Exception) {
                _switchError.value = e.message ?: "Konto konnte nicht aktualisiert werden."
            } finally {
                _refreshingUsername.value = null
            }
        }
    }

    /**
     * Section 5 "Abmelden" — beendet **nur die Sitzung**. Das gespeicherte Konto bleibt auf dem
     * Gerät, damit die nächste Anmeldung wieder per Biometrie geht.
     *
     * Hatte früher ein `alsoRemoveFromDevice`-Flag, über das der Abmelden-Dialog die
     * Zugangsdaten gleich mitlöschen konnte. Das ist weg, und zwar in der API und nicht nur im
     * Dialog: eine nicht umkehrbare Aktion gehört nicht als Nebenausgang an eine alltägliche.
     * Wer ein Konto wirklich entfernen will, nimmt [removeAccount] in der Kontoliste (mit
     * eigener Rückfrage) oder [clearAllData].
     */
    fun logoutCurrentAccount(onDone: () -> Unit) {
        val username = appState.session.value?.username ?: return
        appState.logout(username)
        onDone()
    }

    /** Section 6 "Cache & Daten löschen" — ProfileView.swift/Store.swift `clearAllData()`, per
     * öffentlicher [AppState]-API nachgebaut: jedes Konto einzeln entfernen räumt Sitzung +
     * Phase (→ Login) mit auf, sobald die Liste leer ist. */
    fun clearAllData() {
        if (_clearing.value != null) return
        viewModelScope.launch {
            _clearing.value = ClearKind.All
            runCatching { backendClient.clearCaches() }
            runCatching { secureCredentialStore.deleteAll() }
            outbox.clear()
            runCatching { prefsStore.clearAll() }
            appState.setThemeMode(PokyhThemeMode.System)
            _accountOverrides.value = emptyMap()
            clearDiskCaches(keepSignIn = false)
            _clearing.value = null
            // Last, so nothing above races a screen that is already gone: this swaps the root to
            // Login, which is what actually signs the user out.
            appState.resetAfterDataWipe()
        }
    }

    /**
     * "Cache leeren" — the stored copies of timetable, grades, menu and images go; accounts,
     * passwords, settings and the session stay. Everything removed comes back on the next load.
     */
    fun clearCache() {
        if (_clearing.value != null) return
        viewModelScope.launch {
            _clearing.value = ClearKind.Cache
            runCatching { backendClient.clearCaches() }
            clearDiskCaches(keepSignIn = true)
            refreshCacheSize()
            _clearing.value = null
            _cacheCleared.value = true
        }
    }

    enum class ClearKind { Cache, All }

    private val _cacheSize = MutableStateFlow<Long?>(null)
    /** Bytes the removable cache takes up, or null while it is being measured. */
    val cacheSize: StateFlow<Long?> = _cacheSize.asStateFlow()

    private val _cacheCleared = MutableStateFlow(false)
    /** True right after "Cache leeren" finished, until the size is measured again. */
    val cacheCleared: StateFlow<Boolean> = _cacheCleared.asStateFlow()

    fun refreshCacheSize() {
        _cacheCleared.value = false
        viewModelScope.launch {
            _cacheSize.value = withContext(Dispatchers.IO) {
                cacheDirs().sumOf { dir -> dir.walkBottomUp().filter { it.isFile && !keepOnCacheClear(it) }.sumOf { it.length() } }
            }
        }
    }

    private fun cacheDirs(): List<File> =
        listOf(File(context.filesDir, "offline"), File(context.filesDir, "images"), context.cacheDir)

    /**
     * What "Cache leeren" must leave alone: the per-account session snapshot is what lets a saved
     * account sign in offline, the widget snapshots are what the home-screen widgets draw, and
     * `images/` holds only profile pictures, which can only be re-fetched with a live session.
     * The outbox holds Todos and Erinnerungen that were never sent.
     * None of them is a cache in the reader's sense, and losing them looks like a bug.
     */
    private fun keepOnCacheClear(file: File): Boolean =
        file.parentFile?.name == "images" ||
            (file.parentFile?.name == "offline" && (file.name.startsWith("session_") || file.name.startsWith("widget_") || file.name.startsWith("outbox_")))

    private suspend fun clearDiskCaches(keepSignIn: Boolean) = withContext(Dispatchers.IO) {
        // DiskCache/ImageDiskCache expose no purge-all of their own — their storage convention
        // (filesDir/"offline", filesDir/"images") is documented on those classes, so it's safe
        // to sweep the directories directly here rather than touching those files.
        for (dir in cacheDirs()) {
            runCatching {
                dir.walkBottomUp()
                    .filter { it != dir && (it.isDirectory || !keepSignIn || !keepOnCacheClear(it)) }
                    .forEach { it.delete() }
            }
        }
    }

    // ── Konto & Verbindung (ConnectionStatusScreen) ─────────────────────────

    private val _diagnostics = MutableStateFlow<String?>(null)
    val diagnostics: StateFlow<String?> = _diagnostics.asStateFlow()

    private val _loadingDiagnostics = MutableStateFlow(false)
    val loadingDiagnostics: StateFlow<Boolean> = _loadingDiagnostics.asStateFlow()

    fun loadDiagnosticsIfNeeded() {
        if (_diagnostics.value != null || _loadingDiagnostics.value) return
        reloadDiagnostics()
    }

    /** ConnectionStatusView.swift `reload()` — nur die Diagnose-Ausgabe wird neu ermittelt,
     * `backendStatus` selbst ändert sich erst wieder mit einer echten Anmeldung. */
    fun reloadDiagnostics() {
        val s = appState.session.value ?: return
        viewModelScope.launch {
            _loadingDiagnostics.value = true
            _diagnostics.value = buildDiagnostics(s, appState.backendStatus.value)
            _loadingDiagnostics.value = false
        }
    }

    private suspend fun buildDiagnostics(s: UserSession, status: BackendStatus): String {
        val backendReachable = s.apiToken?.takeIf { it.isNotEmpty() }?.let { token ->
            runCatching { backendClient.me(token) }.isSuccess
        }
        return buildString {
            appendLine("username=${s.username}")
            appendLine("studentId=${s.studentId}  personType=${s.personType ?: "—"}")
            appendLine("klasseId=${s.klasseId}  klasseName=${s.klasseName.ifEmpty { "—" }}")
            appendLine("isParent=${s.isParent}  isStudent=${s.isStudent}  hasUntis=${s.hasUntis}")
            appendLine("stableUid=${if (s.stableUid.isNullOrEmpty()) "—" else "vorhanden"}")
            appendLine("classId=${s.classId ?: "—"}")
            appendLine("apiToken=${if (s.apiToken.isNullOrEmpty()) "nein" else "vorhanden"}")
            appendLine()
            appendLine("backendStatus=${status.diagnosticLabel()}")
            if (backendReachable != null) appendLine("backendErreichbar=${if (backendReachable) "ja" else "nein"}")
            appendLine()
            append("App-Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }
}

/** ProfileView.swift/Store.swift `BackendStatus.label` — human-readable German status text. */
internal fun BackendStatus.uiLabel(): String = when (this) {
    is BackendStatus.Unknown -> "Unbekannt"
    is BackendStatus.Ok -> "Verbunden"
    is BackendStatus.NotStudent -> "Nur für Schülerkonten"
    is BackendStatus.NoClass -> "Keine Klasse gefunden"
    is BackendStatus.Failed -> "Nicht verbunden – $message"
    is BackendStatus.Offline -> "Offline"
}

private fun BackendStatus.diagnosticLabel(): String = when (this) {
    is BackendStatus.Unknown -> "unknown"
    is BackendStatus.Ok -> "connected"
    is BackendStatus.NotStudent -> "notStudent"
    is BackendStatus.NoClass -> "noClass"
    is BackendStatus.Failed -> "failed($message)"
    is BackendStatus.Offline -> "offline"
}
