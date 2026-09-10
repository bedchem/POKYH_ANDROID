package dev.plattnericus.pokyh.ui.login
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.R
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.centeredForm
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.smoothCorner

/**
 * LoginView.swift, ported. `isAdditional` mirrors the sheet-presented "Konto hinzufügen" /
 * "Mit Passwort anmelden" flow from ProfileView/LockView: [AppState.addAccount] flips
 * `showAddAccount` true, whatever hosts this screen in that case shows it (dialog/bottom
 * sheet) while `showAddAccount` is true, and [onCancel] fires both on an explicit "Abbrechen"
 * tap AND automatically once [AppState.finalize] flips `showAddAccount` back to false after a
 * successful login — the same signal `.sheet(isPresented: $app.showAddAccount)` reacts to on iOS.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // LoginView.swift `.onAppear { if let u = app.prefillUsername { username = u; ...; focus = .pass } }`
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

    val bgColor = PokyhTheme.colors.bg
    val gradient = remember(bgColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Brand.accent.copy(alpha = 0.22f),
                0.5f to Brand.accentSoft.copy(alpha = 0.10f),
                1f to bgColor,
            ),
        )
    }

    Box(Modifier.fillMaxSize().background(gradient)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            topBar = {
                if (isAdditional) {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            TextButton(onClick = onCancel) {
                                Text("Abbrechen", color = Brand.accent)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .centeredForm()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (isAdditional) 24.dp else 64.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fadeIn(),
                ) {
                    LoginLogo(size = 84.dp)
                    Text("POKYH", style = PokyhType.wordmark, color = PokyhTheme.colors.textPrimary)
                    Text(
                        "Schulapp für die LBS Brixen",
                        style = PokyhType.subheadline,
                        color = PokyhTheme.colors.textSecondary,
                    )
                }

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .cardSurface(radius = 24.dp)
                        .padding(20.dp)
                        .fadeIn(delayMillis = 50),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    LoginField(
                        placeholder = "Benutzername",
                        value = username,
                        onValueChange = { username = it },
                        icon = PokyhIcons.person_fill,
                        isPassword = false,
                        imeAction = ImeAction.Next,
                        onImeAction = { passwordFocusRequester.requestFocus() },
                    )
                    LoginField(
                        placeholder = "Passwort",
                        value = password,
                        onValueChange = { password = it },
                        icon = PokyhIcons.lock,
                        isPassword = true,
                        imeAction = ImeAction.Go,
                        onImeAction = { submit() },
                        focusRequester = passwordFocusRequester,
                    )

                    if (viewModel.biometricAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    PokyhIcons.faceid,
                                    contentDescription = null,
                                    tint = PokyhTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text("Mit Biometrie speichern", style = PokyhType.subheadline, color = PokyhTheme.colors.textPrimary)
                            }
                            Switch(
                                checked = saveCredentials,
                                onCheckedChange = { saveCredentials = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = Brand.accent, checkedThumbColor = Color.White),
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = uiState.error != null,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                    ) {
                        Text(
                            text = uiState.error.orEmpty(),
                            style = PokyhType.footnote,
                            color = Brand.danger,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    val fieldsEmpty = username.isBlank() || password.isBlank()
                    Button(
                        onClick = { submit() },
                        enabled = !uiState.busy && !fieldsEmpty,
                        shape = PokyhShapes.r16,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Brand.accent,
                            contentColor = Color.White,
                            disabledContainerColor = Brand.accent,
                            disabledContentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .alpha(if (fieldsEmpty) 0.6f else 1f),
                    ) {
                        if (uiState.busy) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (uiState.busy) uiState.statusText.ifEmpty { "Anmelden…" } else "Anmelden",
                            style = PokyhType.headline,
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "Melde dich mit deinem WebUntis-Konto an.",
                    style = PokyhType.caption,
                    color = PokyhTheme.colors.textTertiary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun LoginField(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    isPassword: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val colors = PokyhTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(colors.cardAlt, PokyhShapes.r12)
            .padding(horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = PokyhType.body, color = colors.textTertiary)
            }
            var fieldModifier = Modifier.fillMaxWidth()
            if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = PokyhType.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(Brand.accent),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = imeAction,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onGo = { onImeAction() },
                ),
                modifier = fieldModifier,
            )
        }
    }
}

/** AppLogo (LockView.swift) — no dedicated "AppLogo" asset ships on Android yet, so the launcher's
 * gradient + glyph (`ic_launcher_background`/`_foreground`) stand in, composited manually at the
 * same 0.26x-radius squircle iOS uses (the adaptive-icon XML itself isn't a `painterResource`-
 * loadable format — only its two layer drawables are). */
@Composable
private fun LoginLogo(size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(smoothCorner(size * 0.26f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
