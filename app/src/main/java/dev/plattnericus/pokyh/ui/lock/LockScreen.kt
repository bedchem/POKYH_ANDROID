package dev.plattnericus.pokyh.ui.lock
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.R
import dev.plattnericus.pokyh.core.biometric.BiometricAuthenticator
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.ui.components.PokyhAvatar
import dev.plattnericus.pokyh.ui.login.LoginScreen
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.centeredForm
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.pressable
import dev.plattnericus.pokyh.ui.theme.smoothCorner
import kotlinx.coroutines.launch

/**
 * LockView.swift, ported. Auto-attempts biometric unlock once on first composition; after 3
 * failed attempts (or with >1 saved account) shows an account chooser, and past 3 failures also
 * offers a password-login fallback. The "Konto hinzufügen" / "Mit Passwort anmelden" flows open
 * [LoginScreen] `isAdditional = true` in a full-screen [Dialog] — the Android stand-in for iOS's
 * `.sheet(isPresented: $app.showAddAccount)`. `showAddAccount` has no public setter on [dev.
 * plattnericus.pokyh.state.AppState] (unlike SwiftUI's two-way `Binding`), so the dialog's open
 * flag is owned locally here: opened whenever [LockViewModel.showAddAccount] turns true (an
 * explicit tap, or the no-saved-password fallback from [LockViewModel.unlock]) and closed locally
 * by [LoginScreen]'s own `onCancel` (fired on "Abbrechen" or once a login inside the sheet
 * succeeds) — see LoginScreen.kt's kdoc for the other half of this handshake.
 */
@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val statusTextRaw by viewModel.statusText.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val defaultUsername by viewModel.defaultUsername.collectAsStateWithLifecycle()

    var appeared by rememberSaveable { mutableStateOf(false) }
    var failures by rememberSaveable { mutableIntStateOf(0) }
    var showAddAccountSheet by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? FragmentActivity
    val biometricAuthenticator = remember(activity) { activity?.let { BiometricAuthenticator(it) } }
    val biometricAvailable = viewModel.biometricAvailable && biometricAuthenticator != null

    val showFallback = failures >= 3

    fun attemptUnlock(username: String? = null) {
        val authenticator = biometricAuthenticator
        if (authenticator != null && biometricAvailable) {
            authenticator.authenticate(
                title = "Entsperren",
                subtitle = "Mit Biometrie anmelden",
                onSuccess = {
                    scope.launch {
                        val ok = viewModel.unlock(username)
                        if (!ok) failures++
                    }
                },
                onError = { _ -> failures++ },
                onFailed = {},
            )
        } else {
            // No usable biometric — same outcome as a failed attempt (pushes into the
            // account-chooser/password fallback instead of silently logging in unverified).
            failures++
        }
    }

    // LockView.swift `.task { if !appeared { appeared = true; await attemptUnlock() } }`.
    LaunchedEffect(Unit) {
        if (!appeared) {
            appeared = true
            attemptUnlock()
        }
    }

    // AppState.unlockWithBiometrics -> loginSaved's "no saved password" fallback flips
    // `showAddAccount` true on its own (tapping an account row that needs a fresh password).
    LaunchedEffect(Unit) {
        viewModel.showAddAccount.collect { if (it) showAddAccountSheet = true }
    }

    val statusText = when {
        busy -> statusTextRaw.ifEmpty { "Anmelden…" }
        showFallback -> "Biometrie fehlgeschlagen.\nWähle ein Konto oder melde dich mit Passwort an."
        else -> "Mit Biometrie entsperren"
    }

    val bgColor = PokyhTheme.colors.bg
    val gradient = remember(bgColor) {
        Brush.linearGradient(
            colorStops = arrayOf(
                0f to Brand.accent.copy(alpha = 0.28f),
                0.5f to Brand.accentSoft.copy(alpha = 0.12f),
                1f to bgColor,
            ),
        )
    }

    Box(Modifier.fillMaxSize().background(gradient)) {
        Column(
            modifier = Modifier.fillMaxSize().centeredForm().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            LockLogo(size = 96.dp, modifier = Modifier.fadeIn())

            Spacer(Modifier.height(20.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fadeIn(delayMillis = 50),
            ) {
                Text("POKYH", style = PokyhType.wordmark, color = PokyhTheme.colors.textPrimary)
                Crossfade(targetState = statusText, label = "lockStatus") { text ->
                    Text(
                        text = text,
                        style = PokyhType.subheadline,
                        color = if (showFallback) Brand.danger else PokyhTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            if (busy) {
                CircularProgressIndicator(
                    color = Brand.accent,
                    modifier = Modifier.size(40.dp).padding(vertical = 12.dp),
                )
            } else {
                UnlockButton(
                    showFallback = showFallback,
                    onClick = { attemptUnlock() },
                    modifier = Modifier.fadeIn(delayMillis = 100),
                )
            }

            if (accounts.size > 1 || showFallback) {
                Spacer(Modifier.height(26.dp))
                AccountChooser(
                    accounts = accounts,
                    defaultUsername = defaultUsername,
                    onSelect = { attemptUnlock(it) },
                    modifier = Modifier.fadeIn(delayMillis = 150),
                )
            }

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp).fadeIn(delayMillis = 200),
            ) {
                if (showFallback) {
                    Button(
                        onClick = { viewModel.addAccount(); showAddAccountSheet = true },
                        shape = PokyhShapes.r16,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.accent, contentColor = androidx.compose.ui.graphics.Color.White),
                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth().height(48.dp),
                    ) {
                        Icon(PokyhIcons.key_fill, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mit Passwort anmelden", style = PokyhType.subheadline.copy(fontWeight = FontWeight.SemiBold))
                    }
                }

                val addInteraction = remember { MutableInteractionSource() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .pressable(addInteraction)
                        .clickable(interactionSource = addInteraction, indication = null) {
                            viewModel.addAccount()
                            showAddAccountSheet = true
                        }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    Icon(PokyhIcons.person_badge_plus, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(16.dp))
                    Text("Anderes Konto hinzufügen", style = PokyhType.subheadline.copy(fontWeight = FontWeight.Medium), color = Brand.accent)
                }
            }
        }
    }

    if (showAddAccountSheet) {
        Dialog(
            onDismissRequest = { showAddAccountSheet = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            LoginScreen(isAdditional = true, onCancel = { showAddAccountSheet = false })
        }
    }
}

@Composable
private fun UnlockButton(showFallback: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = PokyhShapes.r16,
        colors = ButtonDefaults.buttonColors(containerColor = Brand.accent, contentColor = androidx.compose.ui.graphics.Color.White),
        modifier = modifier.widthIn(max = 280.dp).fillMaxWidth().height(56.dp).padding(horizontal = 24.dp),
    ) {
        Icon(PokyhIcons.faceid, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (showFallback) "Erneut versuchen" else "Entsperren", style = PokyhType.headline)
    }
}

@Composable
private fun AccountChooser(
    accounts: List<SavedAccount>,
    defaultUsername: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Konto wählen", style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
        accounts.forEach { acc ->
            val interactionSource = remember(acc.username) { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .cardSurface(PokyhShapes.r14)
                    .pressable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) { onSelect(acc.username) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PokyhAvatar(url = acc.imageUrl, name = acc.username, size = 34.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            acc.username,
                            style = PokyhType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            color = PokyhTheme.colors.textPrimary,
                        )
                        if (acc.username == defaultUsername) {
                            Box(
                                modifier = Modifier
                                    .background(Brand.accent.copy(alpha = 0.2f), CircleShape)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    "Standard",
                                    style = PokyhType.caption2.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = Brand.accent,
                                )
                            }
                        }
                    }
                    Text(acc.displayName, style = PokyhType.caption2, color = PokyhTheme.colors.textSecondary)
                }
                Icon(PokyhIcons.faceid, contentDescription = null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** AppLogo (LockView.swift) — same composited launcher gradient+glyph stand-in
 * [dev.plattnericus.pokyh.ui.login.LoginScreen]'s private `LoginLogo` uses, at LockView's larger
 * 96dp default size. */
@Composable
private fun LockLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(smoothCorner(size * 0.26f)),
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
