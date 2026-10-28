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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight

private const val PRIVACY_URL = "https://pokyh.com/legal/datenschutz"
private const val IMPRINT_URL = "https://pokyh.com/legal/impressum"
private const val TERMS_URL = "https://pokyh.com/legal/nutzungsbedingungen"

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
    // Sign-in in two steps, like WebUntis itself (the web's LoginForm): first the school — any
    // school on WebUntis, through its public search — then the account. The school is remembered
    // until the end of the school year, so the next sign-in starts at the password.
    var school by remember { mutableStateOf(viewModel.rememberedSchool) }
    var schoolQuery by rememberSaveable { mutableStateOf("") }
    val search by viewModel.search.collectAsStateWithLifecycle()
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
    var taps by remember { mutableLongStateOf(0L) }
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
            // A saved account signs in at its own school.
            // An older saved account has no school stored: it was the default one, never
            // whichever was picked last.
            if (viewModel.hasAccount(it)) school = viewModel.schoolOf(it) ?: dev.plattnericus.pokyh.data.model.School.Default
            if (school != null) runCatching { passwordFocusRequester.requestFocus() }
        }
    }
    LaunchedEffect(isAdditional, uiState.showAddAccount) {
        if (isAdditional && !uiState.showAddAccount) onCancel()
    }

    // How the chosen school signs in (Office 365, a password, both), as on the web.
    val loginOptions by viewModel.loginOptions.collectAsStateWithLifecycle()
    var officeOpen by remember { mutableStateOf(false) }
    LaunchedEffect(school) { viewModel.loadLoginOptions(school) }

    fun submit() {
        if (username.isBlank() || password.isBlank()) return
        focusManager.clearFocus()
        viewModel.submit(username, password, saveCredentials, school)
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
            // Wide windows, as the web's desktop sign-in: the mascot large on a panel of its own
            // beside the form; phones get it small above the form.
            val wideLogin = !isAdditional && dev.plattnericus.pokyh.ui.theme.widthClass() == dev.plattnericus.pokyh.ui.theme.WidthClass.Expanded
            val mascotState = LoginMascotState(
                focus = focus,
                username = username,
                showPassword = showPassword,
                loading = uiState.busy,
                keys = keys,
                event = mascotEvent,
            )
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
            if (wideLogin) {
                Box(
                    Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .padding(16.dp)
                        .background(PokyhTheme.colors.card, androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoginMascotBadge(
                        state = mascotState,
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        onTap = { taps++; mascotEvent = MascotEvent(MascotEventKind.Tap, taps) },
                        maxWidth = 560.dp,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(if (wideLogin) 0.45f else 1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .centeredForm(maxWidth = if (wideLogin) 420.dp else 480.dp)
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
                    if (!wideLogin) {
                        LoginMascotBadge(
                            state = mascotState,
                            modifier = Modifier.fillMaxWidth(),
                            onTap = { taps++; mascotEvent = MascotEvent(MascotEventKind.Tap, taps) },
                        )
                    }
                    Column(Modifier.fillMaxWidth()) {
                        Text(t.signIn, style = PokyhType.display.copy(fontSize = 44.sp, lineHeight = 44.sp), color = PokyhTheme.colors.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(t.subtitle, style = PokyhType.callout, color = PokyhTheme.colors.textTertiary)
                    }
                }

                Spacer(Modifier.height(PokyhSpacing.xxl))

                val chosen = school
                if (chosen == null) {
                    SchoolStep(
                        query = schoolQuery,
                        onQuery = { schoolQuery = it; viewModel.searchSchools(it) },
                        search = search,
                        last = viewModel.rememberedSchool,
                        // Remembered as soon as it is picked, as on the web.
                        onPick = { picked -> school = picked; schoolQuery = ""; viewModel.pickSchool(picked) },
                    )
                } else Column(
                    modifier = Modifier.fillMaxWidth().fadeIn(delayMillis = 50),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    // Choosing another keeps this one offered first in the list (web LoginForm).
                    SchoolChip(chosen, onChange = { school = null })
                    if (loginOptions?.office365 == true) {
                        OfficeButton(onClick = { officeOpen = true }, showOr = loginOptions?.password != false)
                    }
                    if (loginOptions?.password != false) {
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
                    }

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

                    if (loginOptions?.password != false) {
                    Spacer(Modifier.height(PokyhSpacing.xs))
                    PokyhPrimaryButton(
                        text = if (uiState.busy) uiState.statusText.ifEmpty { t.signingIn } else t.signIn,
                        onClick = { submit() },
                        enabled = username.isNotBlank() && password.isNotBlank(),
                        loading = uiState.busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    }
                    PokyhTextButton(
                        text = t.forgotPassword,
                        onClick = { resettingPassword = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }

                Spacer(Modifier.height(PokyhSpacing.xxl))
                // The fine print in one line, as on the web: age, then the privacy policy where the
                // data is entered (Art. 13 DSGVO), the imprint and the terms.
                val uriHandler = LocalUriHandler.current
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t.ageShort, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
                    listOf(t.privacyLink to PRIVACY_URL, t.imprint to IMPRINT_URL, t.terms to TERMS_URL).forEach { (label, url) ->
                        Text(
                            label,
                            style = PokyhType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = PokyhTheme.colors.textTertiary,
                            modifier = Modifier.clickable { uriHandler.openUri(url) },
                        )
                    }
                }
                Spacer(Modifier.height(PokyhSpacing.huge))
            }
            }
        }
    }

    if (resettingPassword) {
        PasswordResetSheet(viewModel = viewModel, initialUsername = username, onDismiss = { resettingPassword = false }, school = school)
    }
    val officeSchool = school
    if (officeOpen && officeSchool != null) {
        OfficeSignInSheet(
            school = officeSchool,
            initialUser = username,
            busy = uiState.busy,
            error = uiState.error,
            onSubmit = { u, k ->
                username = u
                viewModel.submit(u, dev.plattnericus.pokyh.data.untis.AppKey.PREFIX + k, saveCredentials, officeSchool)
            },
            onDismiss = { officeOpen = false },
        )
    }
}

/** Initials and a tone per school, so results are told apart at a glance (the web's SchoolBadge). */
@Composable
private fun SchoolBadge(school: dev.plattnericus.pokyh.data.model.School) {
    val words = school.name.split(Regex("[\\s\\-–]+")).filter { w -> w.firstOrNull()?.isLetterOrDigit() == true }
    val initials = (words.take(2).joinToString("") { it.take(1) }.ifEmpty { school.loginName.take(2) }).uppercase()
    val tones = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.all(PokyhTheme.colors.isDark)
    val tone = tones[Math.floorMod(school.loginName.hashCode(), tones.size)]
    Box(
        Modifier.size(40.dp).background(tone.fill, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = PokyhType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = tone.ink)
    }
}

/** A school as a row: its badge, its name and its address. */
@Composable
private fun SchoolRow(school: dev.plattnericus.pokyh.data.model.School, caption: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = PokyhSpacing.md, vertical = PokyhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        SchoolBadge(school)
        Column(Modifier.weight(1f)) {
            if (caption != null) Text(caption, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
            Text(school.name.ifEmpty { school.loginName }, style = PokyhType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = PokyhTheme.colors.textPrimary, maxLines = 2)
            if (school.address.isNotBlank()) Text(school.address, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

/** Step one: the school, found through WebUntis' public school search. */
@Composable
private fun SchoolStep(
    query: String,
    onQuery: (String) -> Unit,
    search: LoginViewModel.Search,
    last: dev.plattnericus.pokyh.data.model.School?,
    onPick: (dev.plattnericus.pokyh.data.model.School) -> Unit,
) {
    val t = dev.plattnericus.pokyh.core.i18n.strings().login
    Column(Modifier.fillMaxWidth().fadeIn(delayMillis = 50), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
        PokyhTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = t.schoolPlaceholder,
            leadingIcon = PokyhIcons.search,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            trailing = if (query.isNotEmpty()) {
                {
                    PokyhIconButton(
                        icon = PokyhIcons.close,
                        contentDescription = t.schoolClear,
                        onClick = { onQuery("") },
                        tint = PokyhTheme.colors.textTertiary,
                        size = 32.dp,
                        iconSize = 16.dp,
                    )
                }
            } else null,
        )
        val message = when (search) {
            is LoginViewModel.Search.Result -> when (val a = search.answer) {
                dev.plattnericus.pokyh.data.backend.BackendClient.SchoolSearch.Short -> t.schoolShort
                dev.plattnericus.pokyh.data.backend.BackendClient.SchoolSearch.TooMany -> t.schoolTooMany
                dev.plattnericus.pokyh.data.backend.BackendClient.SchoolSearch.Rate -> t.schoolRate
                dev.plattnericus.pokyh.data.backend.BackendClient.SchoolSearch.Failed -> t.schoolError
                is dev.plattnericus.pokyh.data.backend.BackendClient.SchoolSearch.Done -> if (a.schools.isEmpty()) t.schoolNone else null
            }
            else -> null
        }
        if (search is LoginViewModel.Search.Searching) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.md), color = Brand.accent)
        }
        if (message != null) Text(message, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary, modifier = Modifier.padding(horizontal = PokyhSpacing.md))
        val results = ((search as? LoginViewModel.Search.Result)?.answer as? dev.plattnericus.pokyh.data.backend.BackendClient.SchoolSearch.Done)?.schools.orEmpty()
        results.forEach { s -> SchoolRow(s, onClick = { onPick(s) }) }
        if (query.isEmpty() && last != null) SchoolRow(last, caption = t.schoolLast, onClick = { onPick(last) })
    }
}

/** The chosen school above the account form, with the way back to choose another. */
@Composable
private fun SchoolChip(school: dev.plattnericus.pokyh.data.model.School, onChange: () -> Unit) {
    val t = dev.plattnericus.pokyh.core.i18n.strings().login
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokyhTheme.colors.cardAlt, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .padding(start = PokyhSpacing.sm, end = PokyhSpacing.xs, top = PokyhSpacing.sm, bottom = PokyhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        SchoolBadge(school)
        Column(Modifier.weight(1f)) {
            Text(school.name.ifEmpty { school.loginName }, style = PokyhType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = PokyhTheme.colors.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(PokyhIcons.locked, contentDescription = null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(11.dp))
                Text(t.serverShort(school.server), style = PokyhType.caption, color = PokyhTheme.colors.textTertiary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        PokyhIconButton(icon = PokyhIcons.close, contentDescription = t.schoolChange, onClick = onChange, tint = PokyhTheme.colors.textTertiary, size = 36.dp, iconSize = 15.dp)
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
