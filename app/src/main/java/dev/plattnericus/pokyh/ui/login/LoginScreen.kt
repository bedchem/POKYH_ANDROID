package dev.plattnericus.pokyh.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn as animateFadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.centeredForm
import dev.plattnericus.pokyh.ui.theme.fadeIn

private const val PRIVACY_URL = "https://pokyh.com/legal?view=datenschutz"

/**
 * Anmeldung. `isAdditional` mirrors the sheet-presented "Konto hinzufügen" / "Mit Passwort
 * anmelden" flow from Profil/Lock: [dev.plattnericus.pokyh.state.AppState.addAccount] flips
 * `showAddAccount` true, whatever hosts this screen in that case shows it while `showAddAccount`
 * is true, and [onCancel] fires both on an explicit "Abbrechen" tap AND automatically once
 * `finalize` flips `showAddAccount` back to false after a successful login.
 *
 * Visually the auth screens are the app's calmest surface: mark, wordmark, two fields, one
 * button, all on the plain page canvas with no card. Nothing here competes with the one thing
 * there is to do, and the fields are the app's standard [PokyhTextField] rather than the
 * bespoke neutral-gray input this screen used to carry — which was the only place in the app
 * with its own input color.
 */
@Composable
fun LoginScreen(
    isAdditional: Boolean = false,
    onCancel: () -> Unit = {},
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefillUsername by viewModel.prefillUsername.collectAsStateWithLifecycle()

    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var saveCredentials by rememberSaveable { mutableStateOf(true) }
    var resettingPassword by rememberSaveable { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val t = strings().login

    // What the mascot answers: the focused field, each key, the eye, and events.
    var focus by remember { mutableStateOf<MascotFocus?>(null) }
    var keys by remember { mutableStateOf(0) }
    var showPassword by remember { mutableStateOf(false) }
    var mascotEvent by remember { mutableStateOf<MascotEvent?>(null) }
    // Several characters arriving at once is the phone filling the form in (autofill, a paste):
    // one event for both fields.
    fun typed(old: String, new: String) {
        keys++
        if (new.length - old.length >= 3) {
            val last = mascotEvent
            if (!(last?.kind == MascotEventKind.Autofill && System.currentTimeMillis() - last.n < 1500)) {
                mascotEvent = MascotEvent(MascotEventKind.Autofill, System.currentTimeMillis())
            }
        }
    }
    // A failed attempt: the mascot wobbles.
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) mascotEvent = MascotEvent(MascotEventKind.Error, System.currentTimeMillis())
    }

    // Runs once per screen instance, so a stale error from a previous failed attempt never
    // flashes on a freshly (re)opened "Konto hinzufügen" sheet.
    LaunchedEffect(Unit) { viewModel.clearError() }

    LaunchedEffect(prefillUsername) {
        prefillUsername?.let {
            username = it
            passwordFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(isAdditional, uiState.showAddAccount) {
        if (isAdditional && !uiState.showAddAccount) onCancel()
    }

    fun submit() {
        if (username.isBlank() || password.isBlank()) return
        focusManager.clearFocus()
        viewModel.submit(username, password, saveCredentials)
    }

    // Login/Lock render straight into the window (not inside the tab shell's Scaffold, which is
    // what applies the status-bar inset for every other screen), so they pad for it themselves.
    Box(Modifier.fillMaxSize().background(PokyhTheme.colors.bg).statusBarsPadding()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (isAdditional) {
                    PokyhTopBar(title = null, nav = TopBarNav.Close(onCancel))
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .centeredForm()
                    .padding(horizontal = PokyhSpacing.screenH),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (isAdditional) PokyhSpacing.lg else 40.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
                    modifier = Modifier.fadeIn(),
                ) {
                    // The mascot in place of the logo, as on the web's sign-in page on phones.
                    LoginMascotBadge(
                        state = LoginMascotState(
                            focus = focus,
                            username = username,
                            showPassword = showPassword,
                            loading = uiState.busy,
                            keys = keys,
                            event = mascotEvent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("POKYH", style = PokyhType.wordmark, color = PokyhTheme.colors.textPrimary)
                    Text(
                        text = t.tagline,
                        style = PokyhType.callout,
                        color = PokyhTheme.colors.textSecondary,
                    )
                }

                Spacer(Modifier.height(PokyhSpacing.xxl))

                Column(
                    modifier = Modifier.fillMaxWidth().fadeIn(delayMillis = 50),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    PokyhTextField(
                        value = username,
                        onValueChange = { typed(username, it); username = it },
                        placeholder = t.username,
                        leadingIcon = PokyhIcons.person,
                        modifier = Modifier.fillMaxWidth(),
                        onFocusChange = { has ->
                            if (has) focus = MascotFocus.User else if (focus == MascotFocus.User) focus = null
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { passwordFocusRequester.requestFocus() }),
                    )
                    PokyhTextField(
                        value = password,
                        onValueChange = { typed(password, it); password = it },
                        placeholder = t.password,
                        leadingIcon = PokyhIcons.password,
                        isPassword = true,
                        passwordVisible = showPassword,
                        modifier = Modifier.fillMaxWidth(),
                        focusRequester = passwordFocusRequester,
                        onFocusChange = { has ->
                            if (has) focus = MascotFocus.Password else if (focus == MascotFocus.Password) focus = null
                        },
                        trailing = {
                            PokyhIconButton(
                                icon = if (showPassword) PokyhIcons.hidePassword else PokyhIcons.showPassword,
                                contentDescription = if (showPassword) t.hidePassword else t.showPassword,
                                onClick = { showPassword = !showPassword },
                                tint = PokyhTheme.colors.textTertiary,
                                size = 32.dp,
                                iconSize = 20.dp,
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                    )

                    if (viewModel.biometricAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                            ) {
                                Icon(
                                    imageVector = PokyhIcons.biometrics,
                                    contentDescription = null,
                                    tint = PokyhTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = t.saveBiometric,
                                    style = PokyhType.callout,
                                    color = PokyhTheme.colors.textPrimary,
                                )
                            }
                            Switch(
                                checked = saveCredentials,
                                onCheckedChange = { saveCredentials = it },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = Brand.accent,
                                    checkedThumbColor = Brand.onAccent,
                                ),
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.error != null,
                        enter = animateFadeIn(tween(PokyhMotion.durationFast)),
                        exit = fadeOut(tween(PokyhMotion.durationFast)),
                    ) {
                        Text(
                            text = uiState.error.orEmpty(),
                            style = PokyhType.footnote,
                            color = Brand.danger,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(PokyhSpacing.xs))
                    PokyhPrimaryButton(
                        text = if (uiState.busy) uiState.statusText.ifEmpty { t.signingIn } else t.signIn,
                        onClick = { submit() },
                        enabled = username.isNotBlank() && password.isNotBlank(),
                        loading = uiState.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PokyhTextButton(
                        text = t.forgotPassword,
                        onClick = { resettingPassword = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Spacer(Modifier.height(PokyhSpacing.xxl))
                Text(
                    text = t.hint,
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )
                // Die Datenschutzerklärung dort, wo Daten erhoben werden (Art. 13 DSGVO).
                val uriHandler = LocalUriHandler.current
                Spacer(Modifier.height(PokyhSpacing.xs))
                Text(
                    text = t.privacyNote,
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = PokyhSpacing.xxl),
                )
                Text(
                    text = t.privacyRead,
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.accentText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clickable { uriHandler.openUri(PRIVACY_URL) }
                        .padding(PokyhSpacing.xs),
                )
                Spacer(Modifier.height(PokyhSpacing.huge))
            }
        }
    }

    if (resettingPassword) {
        PasswordResetSheet(viewModel = viewModel, initialUsername = username, onDismiss = { resettingPassword = false })
    }
}

/** The "Konto hinzufügen"/"Mit Passwort anmelden" sheet — a full-screen [Dialog] hosting
 * [LoginScreen] in its `isAdditional` mode. Shared by [dev.plattnericus.pokyh.ui.PokyhApp] and
 * [dev.plattnericus.pokyh.ui.lock.LockScreen], which both used to duplicate this wrapper. */
@Composable
fun AddAccountDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LoginScreen(isAdditional = true, onCancel = onDismiss)
    }
}
