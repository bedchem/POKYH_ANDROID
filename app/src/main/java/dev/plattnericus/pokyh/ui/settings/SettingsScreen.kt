@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.Lang
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.update.AppUpdater
import dev.plattnericus.pokyh.ui.components.IconTile
import dev.plattnericus.pokyh.ui.components.PixelSpriteArt
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhRowMenuCaret
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.StatusLabel
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.login.LoginViewModel
import dev.plattnericus.pokyh.ui.login.PasswordResetSheet
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.profile.ChangePasswordSheet
import dev.plattnericus.pokyh.ui.profile.PokyhDialog
import dev.plattnericus.pokyh.ui.profile.ProfileViewModel
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn

private const val LEGAL_BASE_URL = "https://pokyh.com/legal?view="
private const val IMPRESSUM_URL = "${LEGAL_BASE_URL}impressum"
private const val PRIVACY_URL = "${LEGAL_BASE_URL}datenschutz"
private const val LEARN_PRIVACY_URL = "${LEGAL_BASE_URL}learn"
private const val SUPPORT_EMAIL = "contact@pokyh.com"

/** The support page ("POKYH unterstützen"), as the web's NEXT_PUBLIC_SUPPORT_URL default. */
private const val SUPPORT_URL = "https://buymeacoffee.com/plattnericus"

/**
 * Einstellungen — their own page now, as on the web (`app/[lang]/(app)/settings`), apart from
 * the profile: how the app looks and which language it speaks, the WebUntis password, storage,
 * supporting POKYH, the legal pages and the version, and signing out.
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
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
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
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClearData by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var resettingPassword by remember { mutableStateOf(false) }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PokyhTheme.colors.bg,
        topBar = { PokyhTopBar(title = t.title, eyebrow = t.lead, nav = TopBarNav.Back(onNavigateBack)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().appBackground().padding(padding),
            contentPadding = PaddingValues(start = PokyhSpacing.screenH, end = PokyhSpacing.screenH, bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
        ) {
            item(key = "appearance") {
                PokyhSection(title = t.appearance, modifier = Modifier.fadeIn()) {
                    PokyhCard(padding = 0.dp) {
                        MenuRow(
                            title = t.theme,
                            icon = PokyhIcons.appearance,
                            value = themeLabel(themeMode),
                            options = PokyhThemeMode.entries.map { it to themeLabel(it) },
                            selected = themeMode,
                            onSelect = viewModel::setThemeMode,
                        )
                        PokyhRowSeparator()
                        // The phone's language by default; a choice here overrides it.
                        MenuRow(
                            title = t.language,
                            subtitle = t.languageHint,
                            icon = PokyhIcons.language,
                            value = languageChoice?.nativeName ?: t.languageSystem,
                            options = listOf<Lang?>(null) .plus(Lang.entries).map { it to (it?.nativeName ?: t.languageSystem) },
                            selected = languageChoice,
                            onSelect = { I18n.choose(context, it) },
                        )
                    }
                }
            }
            if (session?.hasUntis == true) {
                item(key = "account") {
                    PokyhSection(title = t.accountSecurity, modifier = Modifier.fadeIn(30)) {
                        PokyhCard(padding = 0.dp) {
                            PokyhRow(
                                title = t.changePassword,
                                subtitle = t.changePasswordHint,
                                leading = { RowGlyph(PokyhIcons.password) },
                                onClick = { changingPassword = true },
                            )
                            PokyhRowSeparator()
                            PokyhRow(
                                title = t.forgotPassword,
                                subtitle = t.forgotPasswordHint,
                                leading = { RowGlyph(PokyhIcons.messages) },
                                onClick = { resettingPassword = true },
                            )
                            PokyhRowSeparator()
                            PokyhRow(
                                title = t.profile,
                                subtitle = t.profileHint,
                                leading = { RowGlyph(PokyhIcons.person) },
                                onClick = { onNavigate(PokyhDestinations.PROFILE) },
                            )
                        }
                    }
                }
            }
            item(key = "storage") {
                PokyhSection(title = t.storage, modifier = Modifier.fadeIn(60)) {
                    PokyhCard(padding = 0.dp) {
                        PokyhRow(
                            title = t.clearCache,
                            subtitle = t.clearCacheHint,
                            leading = { IconTile(icon = PokyhIcons.clearCache, color = Brand.accent, size = 38.dp) },
                            trailing = {
                                when {
                                    clearing == ProfileViewModel.ClearKind.Cache -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.accent)
                                    cacheCleared -> StatusLabel(text = t.cleared, color = Brand.success, icon = PokyhIcons.ok)
                                    cacheSize != null -> Text(formatBytes(cacheSize!!), style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                                }
                            },
                            onClick = viewModel::clearCache,
                            enabled = clearing == null,
                            showChevron = false,
                        )
                        PokyhRowSeparator()
                        PokyhRow(
                            title = t.clearAll,
                            titleColor = Brand.danger,
                            subtitle = t.clearAllHint,
                            leading = { IconTile(icon = PokyhIcons.delete, color = Brand.danger, size = 38.dp) },
                            trailing = {
                                if (clearing == ProfileViewModel.ClearKind.All) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.danger)
                                }
                            },
                            onClick = { confirmClearData = true },
                            enabled = clearing == null,
                            showChevron = false,
                        )
                    }
                }
            }
            item(key = "support") { SupportBanner(onOpen = { open(SUPPORT_URL) }, modifier = Modifier.fadeIn(80)) }
            item(key = "about") {
                PokyhSection(title = t.about, modifier = Modifier.fadeIn(100)) {
                    PokyhCard(padding = 0.dp) {
                        PokyhRow(title = t.imprint, leading = { RowGlyph(PokyhIcons.document) }, onClick = { open(IMPRESSUM_URL) })
                        PokyhRowSeparator()
                        PokyhRow(title = t.privacy, leading = { RowGlyph(PokyhIcons.privacy) }, onClick = { open(PRIVACY_URL) })
                        PokyhRowSeparator()
                        PokyhRow(title = t.learnPrivacy, leading = { RowGlyph(PokyhIcons.privacy) }, onClick = { open(LEARN_PRIVACY_URL) })
                        PokyhRowSeparator()
                        PokyhRow(
                            title = t.contactSupport,
                            leading = { RowGlyph(PokyhIcons.messages) },
                            onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))) },
                        )
                        PokyhRowSeparator()
                        PokyhRow(
                            title = t.checkUpdates,
                            leading = { RowGlyph(PokyhIcons.download) },
                            onClick = viewModel::checkForUpdates,
                            enabled = updateCheck != AppUpdater.ManualCheck.Checking,
                            showChevron = false,
                            trailing = {
                                when (updateCheck) {
                                    AppUpdater.ManualCheck.Checking -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.accent)
                                    AppUpdater.ManualCheck.UpToDate -> StatusLabel(text = t.upToDate, color = Brand.success, icon = PokyhIcons.ok)
                                    AppUpdater.ManualCheck.Failed -> StatusLabel(text = t.updateFailed, color = Brand.warning, icon = PokyhIcons.warningBadge)
                                    AppUpdater.ManualCheck.Idle -> Unit
                                }
                            },
                        )
                        PokyhRowSeparator()
                        PokyhRow(
                            title = t.version,
                            leading = { RowGlyph(PokyhIcons.info) },
                            showChevron = false,
                            trailing = {
                                Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                            },
                        )
                    }
                }
            }
            if (session != null) {
                item(key = "logout") {
                    PokyhCard(modifier = Modifier.fadeIn(115), padding = 0.dp) {
                        PokyhRow(
                            title = t.logout,
                            titleColor = Brand.danger,
                            leading = { Icon(PokyhIcons.signOut, contentDescription = null, tint = Brand.danger, modifier = Modifier.size(20.dp)) },
                            onClick = { confirmLogout = true },
                            showChevron = false,
                        )
                    }
                }
            }
            item(key = "footer") {
                Text(t.footer, style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary, modifier = Modifier.fillMaxWidth().fadeIn(130))
            }
        }
    }

    if (confirmLogout && session != null) {
        PokyhDialog(
            title = t.logoutTitle,
            body = t.logoutBody,
            onDismiss = { confirmLogout = false },
            confirmLabel = t.logout,
            confirmColor = Brand.danger,
            onConfirm = {
                confirmLogout = false
                viewModel.logoutCurrentAccount(onDone = onNavigateBack)
            },
        )
    }
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
        PasswordResetSheet(viewModel = loginViewModel, initialUsername = session?.username.orEmpty(), onDismiss = { resettingPassword = false })
    }
}

/** A setting with a few choices: the current one beside it, the choices in a menu. */
@Composable
private fun <T> MenuRow(
    title: String,
    icon: ImageVector,
    value: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PokyhRow(
            title = title,
            subtitle = subtitle,
            leading = { RowGlyph(icon) },
            onClick = { expanded = true },
            // A menu, not a push — so a caret rather than PokyhRow's navigation chevron.
            showChevron = false,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                    Text(value, style = PokyhType.subheadline, color = PokyhTheme.colors.textSecondary, maxLines = 1)
                    PokyhRowMenuCaret(expanded = expanded)
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (option, label) ->
                DropdownMenuItem(
                    text = { Text(label, style = PokyhType.body) },
                    trailingIcon = {
                        if (option == selected) {
                            Icon(PokyhIcons.check, contentDescription = null, tint = PokyhTheme.colors.accentText, modifier = Modifier.size(16.dp))
                        }
                    },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun themeLabel(mode: PokyhThemeMode): String {
    val t = strings().settings
    return when (mode) {
        PokyhThemeMode.System -> t.system
        PokyhThemeMode.Light -> t.light
        PokyhThemeMode.Dark -> t.dark
    }
}

/** A row's leading glyph, at one size and one tint throughout the settings lists. */
@Composable
private fun RowGlyph(icon: ImageVector) {
    Icon(imageVector = icon, contentDescription = null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(20.dp))
}

/**
 * "POKYH unterstützen" — the web's `SupportBanner`: a mug of coffee in pixels and a plain link to
 * the support page. Only a link: nothing of the support site loads until it is opened.
 */
@Composable
fun SupportBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = strings().settings
    val tone = PokyhDecorative.butter(PokyhTheme.colors.isDark)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PokyhShapes.xxl)
            .background(tone.fill)
            .padding(PokyhSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        PixelSpriteArt(PixelSprite.Coffee, width = 120.dp)
        Text(t.supportTitle, style = PokyhType.title2, color = tone.ink)
        Text(t.supportText, style = PokyhType.callout, color = tone.ink.copy(alpha = 0.85f))
        Spacer(Modifier.size(PokyhSpacing.xs))
        Row(
            modifier = Modifier
                .clip(PokyhShapes.md)
                .background(tone.ink)
                .clickable(onClick = onOpen)
                .padding(horizontal = PokyhSpacing.lg, vertical = PokyhSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        ) {
            Icon(PokyhIcons.support, contentDescription = null, tint = tone.fill, modifier = Modifier.size(18.dp))
            Text(t.supportAction, style = PokyhType.headline, color = tone.fill)
            Icon(PokyhIcons.openTile, contentDescription = null, tint = tone.fill, modifier = Modifier.size(15.dp))
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
