package dev.plattnericus.pokyh.ui.login

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    @ApplicationContext context: Context,
) : ViewModel() {

    /** LoginView.swift `app.biometricAvailable` — gates the "Mit Biometrie speichern" toggle. */
    val biometricAvailable: Boolean = BiometricManager.from(context)
        .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

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
    fun submit(username: String, password: String, saveCredentials: Boolean) {
        if (username.isBlank() || password.isBlank()) return
        viewModelScope.launch { appState.login(username, password, saveCredentials) }
    }

    /** LoginView.swift `.onAppear { app.error = nil }`. */
    fun clearError() = appState.clearError()
}
