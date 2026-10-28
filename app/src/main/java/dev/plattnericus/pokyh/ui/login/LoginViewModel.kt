package dev.plattnericus.pokyh.ui.login

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.plattnericus.pokyh.data.model.School
import dev.plattnericus.pokyh.data.storage.SchoolChoice

/** LoginView.swift's `app.busy` / `app.statusText` / `app.error` / `app.showAddAccount`, bundled
 * for `collectAsStateWithLifecycle()` at the one call site that needs all four together. */
data class LoginUiState(
    val busy: Boolean = false,
    val statusText: String = "",
    val error: String? = null,
    val showAddAccount: Boolean = false,
)

/**
 * LoginView.swift has no view model of its own on iOS (it talks to the injected `AppState`
 * directly) — this exists only so [LoginScreen] doesn't have to inject the [AppState] singleton
 * itself, and so the one-shot biometric-availability check (`app.biometricAvailable` on iOS,
 * backed there by `LAContext.canEvaluatePolicy`) has somewhere to live that doesn't need an
 * Activity — `BiometricManager.canAuthenticate` only needs a [Context], unlike the actual prompt
 * (which [dev.plattnericus.pokyh.core.biometric.BiometricAuthenticator] triggers from the UI layer).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val appState: AppState,
    private val untisClient: UntisClient,
    private val backendClient: dev.plattnericus.pokyh.data.backend.BackendClient,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val appContext: Context = context

    // ── Die Schule (step one, as on the web) ────────────────────────────────

    /** The school picked last time on this device, until the end of the school year. */
    val rememberedSchool: School? = SchoolChoice.read(context)

    /** The school a saved account signs in at (re-entering its password). */
    fun hasAccount(username: String): Boolean = appState.accounts.value.any { it.username.equals(username, ignoreCase = true) }

    fun schoolOf(username: String): School? = appState.accounts.value.firstOrNull { it.username.equals(username, ignoreCase = true) }?.school

    sealed interface Search {
        data object Idle : Search
        data object Searching : Search
        data class Result(val answer: dev.plattnericus.pokyh.data.backend.BackendClient.SchoolSearch) : Search
    }

    private val _search = MutableStateFlow<Search>(Search.Idle)
    val search: StateFlow<Search> = _search.asStateFlow()
    private var searchJob: kotlinx.coroutines.Job? = null

    /** Searches a moment after the last key, as the web does. */
    fun searchSchools(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) { _search.value = Search.Idle; return }
        if (q.length < 3) { _search.value = Search.Result(dev.plattnericus.pokyh.data.backend.BackendClient.SchoolSearch.Short); return }
        _search.value = Search.Searching
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            _search.value = Search.Result(backendClient.searchSchools(q))
        }
    }

    fun forgetSchool() = SchoolChoice.forget(appContext)

    fun pickSchool(school: dev.plattnericus.pokyh.data.model.School) = SchoolChoice.save(appContext, school)

    private val _loginOptions = MutableStateFlow<dev.plattnericus.pokyh.data.backend.BackendClient.LoginOptions?>(null)

    /** How the chosen school signs in; null until known (the password form, as always). */
    val loginOptions: StateFlow<dev.plattnericus.pokyh.data.backend.BackendClient.LoginOptions?> = _loginOptions.asStateFlow()

    fun loadLoginOptions(school: dev.plattnericus.pokyh.data.model.School?) {
        _loginOptions.value = null
        if (school == null) return
        viewModelScope.launch { _loginOptions.value = backendClient.loginOptions(school) }
    }

    // ── Passwort vergessen ──────────────────────────────────────────────────

    data class ResetState(val sending: Boolean = false, val error: String? = null, val sent: Boolean = false)

    private val _reset = MutableStateFlow(ResetState())
    val reset: StateFlow<ResetState> = _reset.asStateFlow()

    fun resetResetState() {
        _reset.value = ResetState()
    }

    /** WebUntis e-mails a reset link to the address stored for the account (UntisClient.requestPasswordReset). */
    fun requestPasswordReset(username: String, email: String, school: dev.plattnericus.pokyh.data.model.School? = null) {
        if (_reset.value.sending) return
        _reset.value = ResetState(sending = true)
        viewModelScope.launch {
            _reset.value = try {
                untisClient.requestPasswordReset(username, email, school ?: SchoolChoice.read(appContext) ?: dev.plattnericus.pokyh.data.model.School.Default)
                ResetState(sent = true)
            } catch (e: AppError) {
                ResetState(error = e.message)
            } catch (e: Exception) {
                ResetState(error = dev.plattnericus.pokyh.core.i18n.I18n.s.login.resetUnreachable)
            }
        }
    }

    /** LoginView.swift `app.biometricAvailable` — gates the "Mit Biometrie speichern" toggle. */
    // The same check the lock screen uses: on Android 9/10 (many Huawei and Honor phones) the
    // device code can't be combined with biometrics, and asking for both reported "unavailable".
    val biometricAvailable: Boolean = dev.plattnericus.pokyh.core.biometric.biometricUnlockAvailable(context)

    val uiState: StateFlow<LoginUiState> = combine(
        appState.busy, appState.statusText, appState.error, appState.showAddAccount,
    ) { busy, statusText, error, showAddAccount ->
        LoginUiState(busy = busy, statusText = statusText, error = error, showAddAccount = showAddAccount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoginUiState())

    /** LoginView.swift `app.prefillUsername` — set by [AppState] when a saved-account re-login
     * needs a fresh password (Konto-Wechsel/Lock-Fallback ohne gespeichertes Passwort). */
    val prefillUsername: StateFlow<String?> = appState.prefillUsername

    /** LoginView.swift `submit()`. Never called with blank fields — the submit button is
     * disabled in that case, matching iOS's `.disabled(... || username.isEmpty || password.isEmpty)`. */
    fun submit(username: String, password: String, saveCredentials: Boolean, school: dev.plattnericus.pokyh.data.model.School? = null) {
        if (username.isBlank() || password.isBlank()) return
        school?.let { SchoolChoice.save(appContext, it) }
        viewModelScope.launch { appState.login(username, password, saveCredentials, school) }
    }

    /** LoginView.swift `.onAppear { app.error = nil }`. */
    fun clearError() = appState.clearError()
}
