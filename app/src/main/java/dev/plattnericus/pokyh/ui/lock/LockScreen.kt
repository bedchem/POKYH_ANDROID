package dev.plattnericus.pokyh.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn as animateFadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.ContextWrapper
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
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
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
    val activity = LocalContext.current.findFragmentActivity()
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

            UnlockMark(busy = busy, modifier = Modifier.fadeIn())
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

            // While an unlock is in flight the screen is the mark, the wordmark and the status
            // line — nothing else. Leaving the account list, the "oder" rule and "Manuell
            // anmelden" on screen offered three things to tap that would all be ignored, and
            // made a two-second wait look like a menu. They fade back in if it fails.
            AnimatedVisibility(
                visible = !busy,
                enter = animateFadeIn(tween(PokyhMotion.durationStandard)) +
                    expandVertically(tween(PokyhMotion.durationStandard)),
                exit = fadeOut(tween(PokyhMotion.durationFast)) +
                    shrinkVertically(tween(PokyhMotion.durationStandard)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = !busy,
                enter = animateFadeIn(tween(PokyhMotion.durationStandard)),
                exit = fadeOut(tween(PokyhMotion.durationFast)),
            ) {
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
    }

    if (showAddAccountSheet) {
        AddAccountDialog(onDismiss = { showAddAccountSheet = false })
    }
}

/**
 * The mark, with the unlock in progress drawn *around* it.
 *
 * The old loading state was a stock spinner parked in the gap below the wordmark: it appeared
 * from nothing, pushed the whole column down as it did, and sat unrelated to anything. This
 * puts the waiting where the eye already is — two counter-rotating arcs sweeping the mark, plus
 * a slow breath on the mark itself — so the screen doesn't reflow at all when an unlock starts,
 * and the motion reads as "this is working on it" rather than "something new appeared".
 *
 * Every value is an [animateFloatAsState] toward a target rather than a start/stop animation, so
 * an unlock that resolves in 200ms eases out instead of cutting.
 */
@Composable
private fun UnlockMark(busy: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "unlock")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "unlockSweep",
    )
    // Ring opacity and the mark's breath both follow the flag, so they ease in and out with it.
    val progress by animateFloatAsState(
        targetValue = if (busy) 1f else 0f,
        animationSpec = tween(PokyhMotion.durationStandard, easing = FastOutSlowInEasing),
        label = "unlockProgress",
    )
    val breath by animateFloatAsState(
        targetValue = if (busy) 1.04f else 1f,
        animationSpec = PokyhMotion.springSmooth(),
        label = "unlockBreath",
    )

    val markSize = 84.dp
    val ringInset = PokyhSpacing.md
    val accent = Brand.accent

    Box(
        modifier = modifier.size(markSize + ringInset * 2),
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0.01f) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                // Track, then two arcs turning opposite ways — a single arc at this diameter
                // reads as a slowly tipping line rather than as motion.
                drawArc(
                    color = accent.copy(alpha = 0.14f * progress),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent.copy(alpha = progress),
                    startAngle = sweep,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent.copy(alpha = 0.45f * progress),
                    startAngle = -sweep * 0.65f + 180f,
                    sweepAngle = 52f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        PokyhMark(
            size = markSize,
            modifier = Modifier.graphicsLayer { scaleX = breath; scaleY = breath },
        )
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

/**
 * `LocalContext` is not necessarily the Activity itself — Compose can hand back a
 * `ContextWrapper` (theme, locale, config wrappers), and a plain `as?` cast then quietly yields
 * null. That is silent: no biometric authenticator, no prompt, no error. Walk the wrapper chain
 * instead of casting.
 */
private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
