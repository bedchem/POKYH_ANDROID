package dev.plattnericus.pokyh.ui.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.core.biometric.biometricUnlockAvailable
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import dev.plattnericus.pokyh.state.AppState
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * LockView.swift has no view model of its own on iOS (it talks to the injected `AppState`
 * directly) — this exists only so [LockScreen] doesn't have to inject the [AppState] singleton
 * (and [PreferencesStore], for the "Standard" badge — matches [dev.plattnericus.pokyh.ui.profile.
 * ProfileViewModel]'s own `defaultAccount` wiring) itself.
 */
@HiltViewModel
class LockViewModel @Inject constructor(
    private val appState: AppState,
    prefsStore: PreferencesStore,
    @ApplicationContext context: Context,
) : ViewModel() {

    /** Gates whether the "Entsperren" flow even attempts a biometric prompt — asks for exactly
     * the authenticators [dev.plattnericus.pokyh.core.biometric.BiometricAuthenticator] then
     * requests, so this can never report "available" for a prompt that would be rejected. */
    val biometricAvailable: Boolean = biometricUnlockAvailable(context)

    val busy: StateFlow<Boolean> = appState.busy
    val statusText: StateFlow<String> = appState.statusText
    val accounts: StateFlow<List<SavedAccount>> = appState.accounts

    /** LockView.swift's automatic `app.showAddAccount` trigger from [AppState.unlockWithBiometrics]'s
     * no-saved-password fallback (tapping an account without a stored password) — [LockScreen]
     * observes this to open the add-account/password sheet even without an explicit button tap. */
    val showAddAccount: StateFlow<Boolean> = appState.showAddAccount

    /** `PreferencesStore.defaultAccount` — which saved account the "Standard" badge marks
     * (LockView.swift `acc.username == app.defaultUsername`). */
    val defaultUsername: StateFlow<String?> = prefsStore.defaultAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** LockView.swift `app.showAddAccount = true` — opens the add-account/password-login flow;
     * whatever hosts [LockScreen] shows [dev.plattnericus.pokyh.ui.login.LoginScreen] (isAdditional
     * = true) while [dev.plattnericus.pokyh.state.AppState.showAddAccount] stays true. */
    fun addAccount() = appState.addAccount()

    /**
     * Builds the session for [username] (or the default/last-active account) — call ONLY after
     * the UI has already run [dev.plattnericus.pokyh.core.biometric.BiometricAuthenticator]
     * successfully, mirroring `AppState.unlockWithBiometrics`'s own doc comment: `AppState` has
     * no Activity/Fragment context of its own to host the biometric prompt.
     *
     * Success is read back off [AppState.phase] rather than a return value from
     * [AppState.unlockWithBiometrics] itself (LockView.swift `app.unlock(into:)` returns `Bool`;
     * the Android contract's `unlockWithBiometrics` is `Unit`-returning, so this is the
     * equivalent — a successful login/offline-restore always leaves `phase == Authed`).
     */
    suspend fun unlock(username: String? = null): Boolean {
        appState.unlockWithBiometrics(username)
        return appState.phase.value == AppState.Phase.Authed
    }
}
