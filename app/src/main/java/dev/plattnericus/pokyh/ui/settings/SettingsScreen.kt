@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.Lang
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.update.AppUpdater
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.DonePill
import dev.plattnericus.pokyh.ui.components.PixelSpriteArt
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.SecurityRow
import dev.plattnericus.pokyh.ui.components.SettingsGroup
import dev.plattnericus.pokyh.ui.components.SettingsGroupGap
import dev.plattnericus.pokyh.ui.components.SettingsItem
import dev.plattnericus.pokyh.ui.components.SettingsLinkItem
import dev.plattnericus.pokyh.ui.components.SettingsPageHeader
import dev.plattnericus.pokyh.ui.components.SettingsRule
import dev.plattnericus.pokyh.ui.components.SettingsType
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.components.WebButton
import dev.plattnericus.pokyh.ui.components.WebButtonVariant
import dev.plattnericus.pokyh.ui.components.WebSegmented
import dev.plattnericus.pokyh.ui.components.WebSwitch
import dev.plattnericus.pokyh.ui.login.LoginViewModel
import dev.plattnericus.pokyh.ui.login.PasswordResetSheet
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.pixel.PixelIconName
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.profile.ChangePasswordSheet
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.ui.profile.PokyhDialog
import dev.plattnericus.pokyh.ui.profile.ProfileViewModel
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LEGAL_BASE_URL = "https://pokyh.com/legal?view="
private const val IMPRESSUM_URL = "${LEGAL_BASE_URL}impressum"
private const val PRIVACY_URL = "${LEGAL_BASE_URL}datenschutz"
private const val LEARN_PRIVACY_URL = "${LEGAL_BASE_URL}learn"
private const val FAQ_URL = "https://pokyh.com/faq"
private const val WEBSITE_URL = "https://pokyh.com"
private const val SOURCE_URL = "https://github.com/bedchem/POKYH_ANDROID"
private const val SUPPORT_EMAIL = "contact@pokyh.com"

/** The support page ("POKYH unterstützen"), as the web's NEXT_PUBLIC_SUPPORT_URL default. */
private const val SUPPORT_URL = "https://buymeacoffee.com/plattnericus"

/**
 * Einstellungen, set out as the web's (app/[lang]/(app)/settings): the page's title and what it
 * is for, then each group's name over its settings as rows under thin rules — no boxes around
 * them. In order: how POKYH looks, the account and its security (e-mail, password, two-factor
 * sign-in, devices, signing in on a computer, its WebUntis settings), notifications, privacy
 * (with the data download), storage, supporting POKYH, and about — with signing out at its end.
 *
 * Beyond the web: signing in on a computer by scanning its code, the update check, contacting
 * support and wiping everything off the phone — what only the app does.
 *
 * It shares [ProfileViewModel] with the profile (the theme, the cache, the update check and
 * signing out all live there); a second instance for this route is fine, every one of them reads
 * the same [dev.plattnericus.pokyh.state.AppState] underneath.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val t = strings().settings
    val ta = strings().account
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val clearing by viewModel.clearing.collectAsStateWithLifecycle()
    val cacheSize by viewModel.cacheSize.collectAsStateWithLifecycle()
    val cacheCleared by viewModel.cacheCleared.collectAsStateWithLifecycle()
    val updateCheck by viewModel.updateCheck.collectAsStateWithLifecycle()
    val languageChoice by I18n.choice.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
        viewModel.clearUpdateCheckResult()
    }
    var confirmClearData by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var resettingPassword by remember { mutableStateOf(false) }
    val untis = session?.hasUntis == true
    val account = rememberUntisAccount(viewModel.appState, enabled = untis)
    val backend = remember { dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, PrivacyEntryPoint::class.java).backendClient() }
    var deviceCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(session?.apiToken != null) {
        if (session?.apiToken != null) {
            deviceCount = runCatching { viewModel.appState.withFreshApiToken { backend.deviceSessions(it) }.size }.getOrNull()
        }
    }
    val webSignIn = rememberWebSignIn(viewModel.appState)
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PokyhTheme.colors.bg,
        topBar = { PokyhTopBar(title = null, nav = TopBarNav.Back(onNavigateBack)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().appBackground().padding(padding),
            contentPadding = PaddingValues(start = PokyhSpacing.screenH, end = PokyhSpacing.screenH, bottom = 80.dp),
        ) {
            item(key = "header") { SettingsPageHeader(t.title, ta.settingsLead, Modifier.fadeIn()) }

            item(key = "appearance") {
                SettingsGroup(title = t.appearance, modifier = Modifier.fadeIn(20)) {
                    SettingsRule()
                    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                        Text(t.theme, style = SettingsType.rowTitle, color = PokyhTheme.colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(ta.themeHint, style = SettingsType.rowHint, color = PokyhTheme.colors.textSecondary)
                        Spacer(Modifier.height(16.dp))
                        ThemeChooser(themeMode, onSelect = viewModel::setThemeMode)
                    }
                    // The phone's language by default; a choice here overrides it.
                    SettingsItem(title = t.language, hint = t.languageHint) {
                        WebSegmented(
                            value = languageChoice,
                            options = listOf<Lang?>(null).plus(Lang.entries).map { it to (it?.nativeName ?: t.languageSystem) },
                            onChange = { I18n.choose(context, it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (session != null) item(key = "account") {
                SettingsGroup(title = t.accountSecurity, modifier = Modifier.padding(top = SettingsGroupGap).fadeIn(30)) {
                    if (untis) {
                        UntisEmailRow(account, viewModel.appState)
                        UntisPasswordRow(account, onChange = { changingPassword = true }, onForgot = { resettingPassword = true })
                        UntisTwoFactorRow(account, viewModel.appState)
                    }
                    if (session?.apiToken != null) {
                        SecurityRow(
                            icon = PixelIconName.Devices,
                            title = t.devicesTitle,
                            value = deviceCount?.let { if (it <= 1) ta.devicesOne else ta.devicesCount(it) },
                            hint = t.devicesHint,
                            actions = {
                                WebButton(ta.devicesView, onClick = { onNavigate(PokyhDestinations.DEVICES) }, variant = WebButtonVariant.Tone, trailingIcon = PokyhIcons.forward)
                            },
                        )
                    }
                    SecurityRow(
                        icon = PixelIconName.Qr,
                        title = t.webSignIn,
                        hint = t.webSignInHint,
                        actions = {
                            WebButton(ta.webSignInScan, onClick = { webSignIn.scan() }, variant = WebButtonVariant.Tone, icon = PokyhIcons.webSignIn, loading = webSignIn.working)
                        },
                    )
                    if (untis) {
                        UntisAccountItems(account)
                        SettingsLinkItem(title = t.profile, hint = t.profileHint, onClick = { onNavigate(PokyhDestinations.PROFILE) })
                    }
                    // A teacher has no POKYH account, so there is nothing to delete on the server.
                    if (session?.apiToken != null && backendStatus !is BackendStatus.NotStudent) {
                        DeleteAccountItem(viewModel.appState, onDeleted = onNavigateBack, onOpenWebsite = { open(WEBSITE_URL) })
                    }
                }
            }

            item(key = "notifications") {
                SettingsGroup(title = t.notifications, modifier = Modifier.padding(top = SettingsGroupGap).fadeIn(40)) {
                    PushItem()
                }
            }

            if (session?.apiToken != null) item(key = "privacy") {
                PrivacyGroup(viewModel.appState, Modifier.padding(top = SettingsGroupGap).fadeIn(50), onPolicy = { open(PRIVACY_URL) }, onLearn = { open(LEARN_PRIVACY_URL) })
            }

            item(key = "storage") {
                SettingsGroup(title = t.storage, modifier = Modifier.padding(top = SettingsGroupGap).fadeIn(60)) {
                    SettingsItem(
                        title = ta.clearCacheTitle,
                        hint = ta.clearCacheHint + (cacheSize?.takeIf { !cacheCleared }?.let { " · " + formatBytes(it) } ?: ""),
                    ) {
                        if (cacheCleared) {
                            DonePill(ta.cleared)
                        } else {
                            WebButton(ta.clearCacheTitle, onClick = viewModel::clearCache, variant = WebButtonVariant.Quiet, loading = clearing == ProfileViewModel.ClearKind.Cache, enabled = clearing == null)
                        }
                    }
                    SettingsItem(title = t.clearAll, hint = t.clearAllHint, danger = true) {
                        WebButton(t.clearAllConfirm, onClick = { confirmClearData = true }, variant = WebButtonVariant.Danger, icon = PokyhIcons.delete, loading = clearing == ProfileViewModel.ClearKind.All, enabled = clearing == null)
                    }
                }
            }

            item(key = "support") { SupportBanner(onOpen = { open(SUPPORT_URL) }, modifier = Modifier.padding(top = SettingsGroupGap).fadeIn(80)) }

            item(key = "about") {
                SettingsGroup(title = t.about, modifier = Modifier.padding(top = SettingsGroupGap).fadeIn(100)) {
                    SettingsItem(title = t.version, inline = true) {
                        Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = PokyhType.body.copy(fontSize = 15.sp, fontFamily = FontFamily.Monospace), color = PokyhTheme.colors.textSecondary)
                    }
                    SettingsItem(
                        title = t.checkUpdates,
                        hint = when (updateCheck) {
                            AppUpdater.ManualCheck.UpToDate -> t.upToDate
                            AppUpdater.ManualCheck.Failed -> t.updateFailed
                            else -> null
                        },
                    ) {
                        if (updateCheck == AppUpdater.ManualCheck.UpToDate) {
                            DonePill(t.upToDate)
                        } else {
                            WebButton(ta.checkButton, onClick = viewModel::checkForUpdates, variant = WebButtonVariant.Quiet, icon = PokyhIcons.download, loading = updateCheck == AppUpdater.ManualCheck.Checking)
                        }
                    }
                    SettingsLinkItem(title = ta.legal, onClick = { open(IMPRESSUM_URL) }, external = true)
                    SettingsLinkItem(title = ta.faq, onClick = { open(FAQ_URL) }, external = true)
                    SettingsLinkItem(title = t.contactSupport, onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))) } }, external = true)
                    SettingsLinkItem(title = ta.source, onClick = { open(SOURCE_URL) }, external = true)
                    if (session != null) {
                        LogoutItem(onLogout = { viewModel.logoutCurrentAccount(onDone = onNavigateBack) })
                    }
                }
            }

            item(key = "footer") {
                Text(t.footer, style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary, modifier = Modifier.fillMaxWidth().padding(top = 40.dp).fadeIn(130))
            }
        }
    }

    WebSignInDialogs(webSignIn)
    if (confirmClearData) {
        PokyhDialog(
            title = t.clearAllTitle,
            body = t.clearAllBody,
            onDismiss = { confirmClearData = false },
            confirmLabel = t.clearAllConfirm,
            confirmColor = Brand.danger,
            onConfirm = { confirmClearData = false; viewModel.clearAllData() },
        )
    }
    if (changingPassword) ChangePasswordSheet(viewModel = viewModel, onDismiss = { changingPassword = false })
    if (resettingPassword) {
        val loginViewModel: LoginViewModel = hiltViewModel()
        PasswordResetSheet(viewModel = loginViewModel, initialUsername = session?.username.orEmpty(), onDismiss = { resettingPassword = false }, school = session?.schoolRef)
    }
}

/**
 * "Konto löschen", as on iOS: one red row; what it deletes and what stays is said in the question
 * it asks. A backend from before deleting existed sends the person to the website instead.
 */
@Composable
private fun DeleteAccountItem(appState: dev.plattnericus.pokyh.state.AppState, onDeleted: () -> Unit, onOpenWebsite: () -> Unit) {
    val ta = strings().account
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var notAvailable by remember { mutableStateOf(false) }

    SettingsItem(title = ta.deleteAccount, hint = ta.deleteAccountHint, danger = true) {
        WebButton(ta.deleteAccount, onClick = { confirming = true }, variant = WebButtonVariant.Danger, icon = PokyhIcons.delete, loading = working, enabled = !working)
    }

    if (confirming) {
        PokyhDialog(
            title = ta.deleteAccountTitle,
            body = ta.deleteAccountBody,
            onDismiss = { confirming = false },
            confirmLabel = ta.deleteAccountConfirm,
            confirmColor = Brand.danger,
            onConfirm = {
                confirming = false
                working = true
                scope.launch {
                    val outcome = appState.deleteAccount()
                    working = false
                    when (outcome) {
                        is dev.plattnericus.pokyh.state.AppState.AccountDeletion.Deleted -> onDeleted()
                        is dev.plattnericus.pokyh.state.AppState.AccountDeletion.NotAvailable -> notAvailable = true
                        is dev.plattnericus.pokyh.state.AppState.AccountDeletion.Failed -> problem = outcome.message
                    }
                }
            },
        )
    }
    problem?.let { message ->
        PokyhDialog(
            title = ta.deleteAccount,
            body = message,
            onDismiss = { problem = null },
            confirmLabel = strings().common.ok,
            onConfirm = { problem = null },
            dismissLabel = null,
        )
    }
    if (notAvailable) {
        PokyhDialog(
            title = ta.deleteAccount,
            body = ta.deleteAccountNotAvailable,
            onDismiss = { notAvailable = false },
            confirmLabel = ta.deleteAccountOpenWebsite,
            onConfirm = { notAvailable = false; onOpenWebsite() },
            dismissLabel = strings().common.ok,
        )
    }
}

/**
 * "Abmelden" at the end, as the web's: the first tap asks "Wirklich abmelden?" in place (with a
 * way back), the second signs out. The question goes away on its own after a while.
 */
@Composable
internal fun LogoutItem(onLogout: () -> Unit, withIcon: Boolean = false) {
    val t = strings().settings
    val ta = strings().account
    var asking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(asking) {
        if (asking) {
            delay(5000)
            asking = false
        }
    }
    SettingsItem(title = t.logout, hint = if (asking) ta.logoutConfirm else ta.logoutHint, danger = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (asking && !busy) WebButton(strings().common.cancel, onClick = { asking = false }, variant = WebButtonVariant.Quiet)
            WebButton(
                if (asking && withIcon) ta.logoutConfirm else t.logout,
                onClick = {
                    if (!asking) {
                        asking = true
                    } else {
                        busy = true
                        onLogout()
                    }
                },
                variant = WebButtonVariant.Danger,
                icon = if (withIcon) PokyhIcons.signOut else null,
                loading = busy,
            )
        }
    }
}

/**
 * The colour scheme as the web chooses it: three small drawings of the app (system is light and
 * dark split on the diagonal), the chosen one ringed in the accent with a tick by its name.
 */
@Composable
private fun ThemeChooser(mode: PokyhThemeMode, onSelect: (PokyhThemeMode) -> Unit) {
    val t = strings().settings
    val colors = PokyhTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(PokyhThemeMode.System to t.system, PokyhThemeMode.Light to t.light, PokyhThemeMode.Dark to t.dark).forEach { (option, label) ->
            val active = mode == option
            Column(
                Modifier
                    .weight(1f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { if (!active) onSelect(option) },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (active) Modifier.border(2.5.dp, Brand.accent, RoundedCornerShape(18.dp)) else Modifier)
                        .padding(4.dp),
                ) {
                    ThemePreview(option, Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(14.dp)))
                }
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (active) Icon(PokyhIcons.check, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(15.dp))
                    Text(
                        label,
                        style = PokyhType.body.copy(fontSize = 14.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                        color = if (active) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private class PreviewColors(val bg: Color, val nav: Color, val card: Color, val tone: Color, val ink: Color)

private val PreviewLight = PreviewColors(Color(0xFFF2EEE7), Color(0xFFE9E4DB), Color(0xFFFBF9F5), Color(0xFFDEDDFB), Color(0xFF1C1A17))
private val PreviewDark = PreviewColors(Color(0xFF111114), Color(0xFF1E1E23), Color(0xFF1B1B1F), Color(0xFF24244C), Color(0xFFF2F2F4))

/** A small drawing of the app in a theme (the web's `ThemePreview`): the side bar, a title, a block, two cards. */
@Composable
private fun ThemePreview(mode: PokyhThemeMode, modifier: Modifier) {
    Canvas(modifier) {
        if (mode == PokyhThemeMode.Dark) drawPreview(PreviewDark) else drawPreview(PreviewLight)
        if (mode == PokyhThemeMode.System) {
            val half = Path().apply {
                moveTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            clipPath(half) { drawPreview(PreviewDark) }
        }
    }
}

private fun DrawScope.drawPreview(c: PreviewColors) {
    val w = size.width
    val h = size.height
    drawRect(c.bg)
    // The side bar, 26% wide: the selected item in the tone, two more as faint lines.
    val navW = w * 0.26f
    drawRect(c.nav, size = Size(navW, h))
    val mx = navW * 0.18f
    fun pill(y: Float, height: Float, color: Color, alpha: Float = 1f) =
        drawRoundRect(color.copy(alpha = alpha), topLeft = Offset(mx, y), size = Size(navW - 2 * mx, height), cornerRadius = CornerRadius(height / 2))
    val y1 = navW * 0.22f
    pill(y1, h * 0.07f, c.tone)
    val y2 = y1 + h * 0.07f + navW * 0.12f
    pill(y2, h * 0.05f, c.ink, 0.25f)
    pill(y2 + h * 0.05f + navW * 0.10f, h * 0.05f, c.ink, 0.25f)
    // The page: a title, a block in the tone, two cards.
    val pad = w * 0.08f
    val x0 = navW + pad
    val innerW = w - navW - 2 * pad
    val innerH = h - 2 * pad
    val gap = innerH * 0.07f
    val titleH = innerH * 0.09f
    drawRoundRect(c.ink.copy(alpha = 0.8f), topLeft = Offset(x0, pad), size = Size(innerW * 0.55f, titleH), cornerRadius = CornerRadius(titleH / 2))
    val rowH = innerH * 0.22f
    val blockY = pad + titleH + gap
    val blockH = innerH - titleH - rowH - 2 * gap
    drawRoundRect(c.tone, topLeft = Offset(x0, blockY), size = Size(innerW, blockH), cornerRadius = CornerRadius(minOf(innerW, blockH) * 0.1f))
    val cardW = (innerW - innerW * 0.07f) / 2
    val cardY = blockY + blockH + gap
    drawRoundRect(c.card, topLeft = Offset(x0, cardY), size = Size(cardW, rowH), cornerRadius = CornerRadius(minOf(cardW, rowH) * 0.18f))
    drawRoundRect(c.card, topLeft = Offset(x0 + cardW + innerW * 0.07f, cardY), size = Size(cardW, rowH), cornerRadius = CornerRadius(minOf(cardW, rowH) * 0.18f))
}

/**
 * Notifications on this phone, as the web's push row: on, a sage "Eingeschaltet"; off, a way to
 * turn them on — the system's question the first time (Android 13 on), its settings after that.
 */
@Composable
private fun PushItem() {
    val t = strings().settings
    val ta = strings().account
    val context = LocalContext.current
    fun enabledNow() = NotificationManagerCompat.from(context).areNotificationsEnabled()
    var enabled by remember { mutableStateOf(enabledNow()) }
    var blocked by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        enabled = enabledNow()
        onPauseOrDispose { }
    }
    fun openSettings() = runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        enabled = enabledNow()
        if (!granted) blocked = true
    }
    SettingsItem(title = ta.push, hint = if (!enabled && blocked) ta.pushBlocked else ta.pushOff) {
        if (enabled) {
            DonePill(ta.pushOn)
        } else {
            WebButton(ta.pushEnable, variant = WebButtonVariant.Tone, onClick = {
                val needsAsking = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (needsAsking && !blocked) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else openSettings()
            })
        }
    }
}

/**
 * "POKYH unterstützen" — the web's `SupportBanner`: a mug of coffee in pixels and a plain link to
 * the support page, on the butter tone. Only a link: nothing of the support site loads until it
 * is opened.
 */
@Composable
fun SupportBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = strings().settings
    val colors = PokyhTheme.colors
    val tone = PokyhDecorative.butter(colors.isDark)
    // bg-ink text-canvas; in the dark theme the butter's ink with the butter on it.
    val (key, onKey) = if (colors.isDark) tone.ink to tone.fill else colors.textPrimary to colors.bg
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(tone.fill)
            .padding(24.dp),
    ) {
        PixelSpriteArt(PixelSprite.Coffee, width = 120.dp)
        Spacer(Modifier.height(20.dp))
        Text(t.supportTitle, style = SettingsType.groupTitle.copy(letterSpacing = (-0.03).em), color = tone.ink)
        Spacer(Modifier.height(6.dp))
        Text(t.supportText, style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 24.sp), color = tone.ink)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(key)
                .clickable(onClick = onOpen)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(PokyhIcons.support, contentDescription = null, tint = onKey, modifier = Modifier.size(18.dp))
            Text(t.supportAction, style = PokyhType.body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = onKey)
            Icon(PokyhIcons.openTile, contentDescription = null, tint = onKey, modifier = Modifier.size(15.dp))
        }
    }
}

/** `0 KB`, `840 KB`, `12,4 MB` — the decimal sign of the app's language, one decimal from MB on. */
private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val dec = I18n.s.common.decimal
    if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.0f KB", kb)
    return String.format(java.util.Locale.ROOT, "%.1f MB", kb / 1024.0).replace('.', dec)
}

/**
 * "Datenschutz", as on the web's settings: the consent to the personal usage statistics (the
 * profile's statistics need it), when the privacy notice was confirmed, the data download, and
 * the privacy policy.
 */
@Composable
private fun PrivacyGroup(appState: AppState, modifier: Modifier = Modifier, onPolicy: () -> Unit, onLearn: () -> Unit) {
    val t = strings().settings
    val ta = strings().account
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backend = remember { dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, PrivacyEntryPoint::class.java).backendClient() }
    // null while it loads; a failed read leaves the switch off and disabled, as the web does.
    var status by remember { mutableStateOf<dev.plattnericus.pokyh.data.backend.BackendClient.PrivacyStatus?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf("idle") } // idle | busy | done
    var exportError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        status = runCatching { appState.withFreshApiToken { backend.privacyStatus(it) } }.getOrNull()
        loaded = true
    }
    SettingsGroup(title = t.privacySection, modifier = modifier) {
        SettingsItem(title = t.usage, hint = t.usageHint, alert = if (error) t.saveError else null) {
            if (!loaded) {
                SkeletonBlock(height = 30.dp, width = 50.dp, shape = CircleShape)
            } else {
                WebSwitch(
                    checked = status?.usageStats == true,
                    enabled = status != null && !saving,
                    onCheckedChange = { on ->
                        saving = true
                        error = false
                        scope.launch {
                            val ok = runCatching { appState.withFreshApiToken { backend.setUsageStats(on, it) } }.isSuccess
                            if (ok) status = status?.copy(usageStats = on) else error = true
                            saving = false
                        }
                    },
                )
            }
        }
        SettingsItem(
            title = t.acknowledged,
            hint = status?.acknowledgedAt?.let { at ->
                runCatching {
                    val l = I18n.lang.value
                    java.time.OffsetDateTime.parse(at).format(java.time.format.DateTimeFormatter.ofPattern(if (l == Lang.De) "d. MMMM yyyy" else "d MMMM yyyy", l.locale))
                }.getOrNull()
            } ?: t.never,
        )
        SettingsItem(title = t.exportTitle, hint = t.exportHint, alert = exportError) {
            if (exporting == "done") {
                DonePill(ta.exportDone)
            } else {
                WebButton(
                    if (exporting == "busy") t.exporting else ta.exportButton,
                    variant = WebButtonVariant.Quiet,
                    icon = PokyhIcons.download,
                    loading = exporting == "busy",
                    onClick = {
                        exporting = "busy"
                        exportError = null
                        scope.launch {
                            runCatching { appState.withFreshApiToken { backend.exportData(it) } }
                                .onSuccess { text ->
                                    val name = "pokyh-daten-" + java.time.LocalDate.now() + ".json"
                                    dev.plattnericus.pokyh.ui.components.openOrShareFile(context, name, "application/json", text.toByteArray())
                                    exporting = "done"
                                }
                                .onFailure { exportError = it.message ?: t.saveError; exporting = "idle" }
                        }
                    },
                )
            }
        }
        SettingsLinkItem(title = ta.policy, onClick = onPolicy, external = true)
        SettingsLinkItem(title = t.learnPrivacy, onClick = onLearn, external = true)
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface PrivacyEntryPoint {
    fun backendClient(): dev.plattnericus.pokyh.data.backend.BackendClient
}
