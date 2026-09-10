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

    private val _clearing = MutableStateFlow(false)
    val clearing: StateFlow<Boolean> = _clearing.asStateFlow()

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

    /** Section 5 "Abmelden": nur diese Sitzung (removeAccount = false) oder zusätzlich das
     * gespeicherte Konto vom Gerät löschen (ProfileView.swift `confirmLogout`-Dialog). */
    fun logoutCurrentAccount(alsoRemoveFromDevice: Boolean, onDone: () -> Unit) {
        val username = appState.session.value?.username ?: return
        if (alsoRemoveFromDevice) appState.removeAccount(username)
        appState.logout(username)
        onDone()
    }

    /** Section 6 "Cache & Daten löschen" — ProfileView.swift/Store.swift `clearAllData()`, per
     * öffentlicher [AppState]-API nachgebaut: jedes Konto einzeln entfernen räumt Sitzung +
     * Phase (→ Login) mit auf, sobald die Liste leer ist. */
    fun clearAllData(onDone: () -> Unit) {
        if (_clearing.value) return
        viewModelScope.launch {
            _clearing.value = true
            runCatching { backendClient.clearCaches() }
            appState.accounts.value.forEach { appState.removeAccount(it.username) }
            runCatching { secureCredentialStore.deleteAll() }
            runCatching { prefsStore.clearAll() }
            appState.setThemeMode(PokyhThemeMode.System)
            _accountOverrides.value = emptyMap()
            clearDiskCaches()
            _clearing.value = false
            onDone()
        }
    }

    private suspend fun clearDiskCaches() = withContext(Dispatchers.IO) {
        // DiskCache/ImageDiskCache expose no purge-all of their own — their storage convention
        // (filesDir/"offline", filesDir/"images") is documented on those classes, so it's safe
        // to sweep the directories directly here rather than touching those files.
        runCatching { File(context.filesDir, "offline").listFiles()?.forEach { it.delete() } }
        runCatching { File(context.filesDir, "images").listFiles()?.forEach { it.delete() } }
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
}

private fun BackendStatus.diagnosticLabel(): String = when (this) {
    is BackendStatus.Unknown -> "unknown"
    is BackendStatus.Ok -> "connected"
    is BackendStatus.NotStudent -> "notStudent"
    is BackendStatus.NoClass -> "noClass"
    is BackendStatus.Failed -> "failed($message)"
}
