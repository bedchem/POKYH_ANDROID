package dev.plattnericus.pokyh.ui.lock

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.biometric.BiometricAuthenticator
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.ui.components.MiniBadge
import dev.plattnericus.pokyh.ui.components.InitialAvatar
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhMark
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.login.AddAccountDialog
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.centeredForm
import dev.plattnericus.pokyh.ui.theme.fadeIn
import kotlinx.coroutines.launch

/**
 * Sperrbildschirm. Auto-attempts biometric unlock once on first composition. The saved-accounts
 * list and the "Manuell anmelden" fallback are always shown together (not gated behind a
 * failed-attempt count) — only the status copy above them reacts to repeated biometric failures.
 *
 * Shares [LoginScreen]'s calm auth layout: mark, wordmark, status line, then the accounts as one
 * grouped card. The accounts used to carry their own neutral-gray row color, the only place in
 * the app with a bespoke surface — they're standard rows now.
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

    // See LoginScreen — these two screens are outside the tab shell's Scaffold and own their insets.
    Box(Modifier.fillMaxSize().background(PokyhTheme.colors.bg).statusBarsPadding()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .centeredForm()
                .padding(horizontal = PokyhSpacing.screenH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            PokyhMark(size = 84.dp, modifier = Modifier.fadeIn())
            Spacer(Modifier.height(PokyhSpacing.xl))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                modifier = Modifier.fadeIn(delayMillis = 50),
            ) {
                Text("POKYH", style = PokyhType.wordmark, color = PokyhTheme.colors.textPrimary)
                Crossfade(targetState = statusText, label = "lockStatus") { text ->
                    Text(
                        text = text,
                        style = PokyhType.callout,
                        color = if (showFallback) Brand.danger else PokyhTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(PokyhSpacing.xxxl))

            if (busy) {
                CircularProgressIndicator(color = Brand.accent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(PokyhSpacing.xl))
            }

            if (accounts.isNotEmpty()) {
                AccountChooser(
                    accounts = accounts,
                    defaultUsername = defaultUsername,
                    onSelect = { attemptUnlock(it) },
                    modifier = Modifier.fadeIn(delayMillis = 150),
                )
                Spacer(Modifier.height(PokyhSpacing.xl))
                OrDivider(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth()
                        .fadeIn(delayMillis = 150),
                )
                Spacer(Modifier.height(PokyhSpacing.xl))
            }

            PokyhSecondaryButton(
                text = "Manuell anmelden",
                onClick = { viewModel.addAccount(); showAddAccountSheet = true },
                icon = PokyhIcons.password,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .fadeIn(delayMillis = 150),
            )

            Spacer(Modifier.weight(1f))

            PokyhTextButton(
                text = "Anderes Konto hinzufügen",
                icon = PokyhIcons.addAccount,
                onClick = { viewModel.addAccount(); showAddAccountSheet = true },
                modifier = Modifier
                    .padding(bottom = PokyhSpacing.xxl)
                    .fadeIn(delayMillis = 200),
            )
        }
    }

    if (showAddAccountSheet) {
        AddAccountDialog(onDismiss = { showAddAccountSheet = false })
    }
}

@Composable
private fun OrDivider(modifier: Modifier = Modifier) {
    val lineColor = PokyhTheme.colors.border
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = lineColor)
        Text("oder", style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
        HorizontalDivider(modifier = Modifier.weight(1f), color = lineColor)
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
        modifier = modifier.widthIn(max = 340.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        PokyhLabel("Gespeicherte Konten")
        PokyhListCard(items = accounts) { acc ->
            PokyhRow(
                title = acc.username,
                subtitle = acc.displayName,
                onClick = { onSelect(acc.username) },
                showChevron = false,
                // No session exists yet on the Lock screen, so there are no headers to authorize a
                // WebUntis image with — a saved account always shows its initial here.
                leading = { InitialAvatar(name = acc.username, size = 36.dp) },
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                    ) {
                        if (acc.username == defaultUsername) {
                            MiniBadge("Standard", Brand.accent, icon = PokyhIcons.starFilled)
                        }
                        Icon(
                            imageVector = PokyhIcons.biometrics,
                            contentDescription = null,
                            tint = PokyhTheme.colors.textTertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
        }
    }
}
