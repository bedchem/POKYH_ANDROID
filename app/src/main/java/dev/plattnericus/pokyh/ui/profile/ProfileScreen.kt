@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.ui.components.MiniBadge
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhRowMenuCaret
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhSectionHeader
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.components.PokyhTileRow
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.StatusLabel
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.components.UntisAvatar
import dev.plattnericus.pokyh.ui.components.UntisImageAuth
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn

private const val PRIVACY_URL = "https://pokyh.com/datenschutz"
private const val TERMS_URL = "https://pokyh.com/nutzungsbedingungen"
private const val SUPPORT_EMAIL = "support@pokyh.com"

/**
 * Profil — a pushed full-screen route (from the header actions on every tab root).
 *
 * Everything settings-shaped is a grouped [PokyhCard] of [PokyhRow]s rather than one card per
 * setting, which is what the screen used to be: eleven cards stacked down the page, each with
 * its own shadow. The accounts list keeps standalone [PokyhTileRow]s, because a row there is a
 * *selectable object* with its own active state and overflow menu, not a setting.
 */
@Composable
fun ProfileScreen(
    onNavigateUp: () -> Unit,
    onConnectionStatus: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val accountsWithPassword by viewModel.accountsWithPassword.collectAsStateWithLifecycle()
    val defaultUsername by viewModel.defaultUsername.collectAsStateWithLifecycle()
    val switchingUsername by viewModel.switchingUsername.collectAsStateWithLifecycle()
    val refreshingUsername by viewModel.refreshingUsername.collectAsStateWithLifecycle()
    val switchError by viewModel.switchError.collectAsStateWithLifecycle()
    val clearing by viewModel.clearing.collectAsStateWithLifecycle()

    var confirmLogout by remember { mutableStateOf(false) }
    var confirmClearData by remember { mutableStateOf(false) }
    var accountToRemove by remember { mutableStateOf<SavedAccount?>(null) }
    var renameTarget by remember { mutableStateOf<SavedAccount?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PokyhTheme.colors.bg,
        topBar = { PokyhTopBar(title = "Profil", nav = TopBarNav.Back(onNavigateUp)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().appBackground().padding(padding),
            contentPadding = PaddingValues(
                start = PokyhSpacing.screenH,
                end = PokyhSpacing.screenH,
                bottom = PokyhSpacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
        ) {
            if (session != null) {
                item(key = "header") { HeaderSection(session!!, Modifier.fadeIn()) }
                item(key = "konto") {
                    KontoSection(
                        session = session!!,
                        backendStatus = backendStatus,
                        onOpenConnectionStatus = onConnectionStatus,
                        modifier = Modifier.fadeIn(delayMillis = 30),
                    )
                }
            }
            item(key = "darstellung") {
                DarstellungSection(
                    themeMode = themeMode,
                    onSelect = viewModel::setThemeMode,
                    modifier = Modifier.fadeIn(delayMillis = 60),
                )
            }

            item(key = "konten_title") {
                PokyhSectionHeader(title = "Konten", modifier = Modifier.fadeIn(delayMillis = 80))
            }
            items(accounts, key = { it.username }) { acc ->
                AccountRow(
                    account = acc,
                    isActive = acc.username == session?.username,
                    isDefault = acc.username == defaultUsername,
                    hasPassword = acc.username in accountsWithPassword,
                    isSwitching = acc.username == switchingUsername,
                    isRefreshing = acc.username == refreshingUsername,
                    isAnyBusy = switchingUsername != null,
                    activeAuth = UntisImageAuth.of(session),
                    onClick = { viewModel.switchAccount(acc.username) { onNavigateUp() } },
                    onToggleDefault = {
                        viewModel.setDefaultAccount(if (acc.username == defaultUsername) null else acc.username)
                    },
                    onRefresh = { viewModel.refreshAccount(acc.username) },
                    onRename = { renameText = acc.nickname.orEmpty(); renameTarget = acc },
                    onSignOut = { viewModel.signOutAccount(acc.username) },
                    onRemove = { accountToRemove = acc },
                    modifier = Modifier.fadeIn(delayMillis = 90),
                )
            }
            item(key = "add_account") {
                PokyhTileRow(onClick = viewModel::addAccount, modifier = Modifier.fadeIn(delayMillis = 100)) {
                    Icon(
                        imageVector = PokyhIcons.addAccount,
                        contentDescription = null,
                        tint = PokyhTheme.colors.accentText,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Konto hinzufügen",
                        style = PokyhType.body,
                        color = PokyhTheme.colors.accentText,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "danger") {
                DangerSection(
                    clearing = clearing,
                    onLogout = { confirmLogout = true },
                    onClearData = { confirmClearData = true },
                    modifier = Modifier.fadeIn(delayMillis = 110),
                )
            }

            item(key = "about") {
                PokyhSection(title = "Über & Rechtliches", modifier = Modifier.fadeIn(delayMillis = 120)) {
                    PokyhCard(padding = 0.dp) {
                        PokyhRow(
                            title = "Datenschutzerklärung",
                            leading = { RowGlyph(PokyhIcons.privacy) },
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) },
                        )
                        PokyhRowSeparator()
                        PokyhRow(
                            title = "Nutzungsbedingungen",
                            leading = { RowGlyph(PokyhIcons.document) },
                            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))) },
                        )
                        PokyhRowSeparator()
                        PokyhRow(
                            title = "Support kontaktieren",
                            leading = { RowGlyph(PokyhIcons.messages) },
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")))
                            },
                        )
                        PokyhRowSeparator()
                        PokyhRow(
                            title = "Version",
                            leading = { RowGlyph(PokyhIcons.info) },
                            showChevron = false,
                            trailing = {
                                Text(
                                    text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                    style = PokyhType.footnote,
                                    color = PokyhTheme.colors.textSecondary,
                                )
                            },
                        )
                    }
                }
            }

            item(key = "footer") {
                Text(
                    text = "POKYH · Nicht offiziell mit der LBS Brixen oder WebUntis verbunden.",
                    style = PokyhType.caption2,
                    color = PokyhTheme.colors.textTertiary,
                    modifier = Modifier.fillMaxWidth().fadeIn(delayMillis = 130),
                )
            }
        }
    }

    if (confirmLogout && session != null) {
        LogoutConfirmDialog(
            onDismiss = { confirmLogout = false },
            onLogoutOnly = {
                confirmLogout = false
                viewModel.logoutCurrentAccount(alsoRemoveFromDevice = false, onDone = onNavigateUp)
            },
            onLogoutAndRemove = {
                confirmLogout = false
                viewModel.logoutCurrentAccount(alsoRemoveFromDevice = true, onDone = onNavigateUp)
            },
        )
    }

    accountToRemove?.let { acc ->
        PokyhDialog(
            title = "Konto entfernen?",
            body = "Die gespeicherten Anmeldedaten von ${acc.username} werden vom Gerät gelöscht.",
            onDismiss = { accountToRemove = null },
            confirmLabel = "Konto entfernen",
            confirmColor = Brand.danger,
            onConfirm = { viewModel.removeAccount(acc.username); accountToRemove = null },
        )
    }

    if (confirmClearData) {
        PokyhDialog(
            title = "Cache & alle Daten löschen?",
            body = "Alle gespeicherten Konten, Anmeldedaten, der Offline-Stundenplan, der Noten-Cache und " +
                "sämtliche App-Einstellungen werden entfernt. Du musst dich danach neu anmelden.",
            onDismiss = { confirmClearData = false },
            confirmLabel = "Alles löschen",
            confirmColor = Brand.danger,
            onConfirm = { confirmClearData = false; viewModel.clearAllData(onDone = onNavigateUp) },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            shape = PokyhShapes.xl,
            containerColor = PokyhTheme.colors.card,
            title = { Text("Konto umbenennen", style = PokyhType.title3, color = PokyhTheme.colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                    Text(
                        text = "Vergib einen eigenen Namen für ${target.username} (nur lokal sichtbar).",
                        style = PokyhType.footnote,
                        color = PokyhTheme.colors.textSecondary,
                    )
                    PokyhTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        placeholder = "Spitzname",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                PokyhTextButton(
                    text = "Speichern",
                    onClick = { viewModel.renameAccount(target.username, renameText); renameTarget = null },
                )
            },
            dismissButton = {
                Row {
                    if (!target.nickname.isNullOrEmpty()) {
                        PokyhTextButton(
                            text = "Entfernen",
                            color = Brand.danger,
                            onClick = { viewModel.renameAccount(target.username, null); renameTarget = null },
                        )
                    }
                    PokyhTextButton(
                        text = "Abbrechen",
                        color = PokyhTheme.colors.textSecondary,
                        onClick = { renameTarget = null },
                    )
                }
            },
        )
    }

    switchError?.let { message ->
        PokyhDialog(
            title = "Fehler beim Kontowechsel",
            body = message,
            onDismiss = viewModel::dismissSwitchError,
            confirmLabel = "OK",
            onConfirm = viewModel::dismissSwitchError,
        )
    }
}

/**
 * The small avatar/person toolbar action other screens embed to open [ProfileScreen]. Reuses
 * [ProfileViewModel] purely to read the current session; every instance shares the same
 * [dev.plattnericus.pokyh.state.AppState] singleton underneath.
 */
@Composable
fun ProfileToolbarAction(onClick: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val s = session
    if (s != null) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            CurrentUserAvatar(size = 28.dp)
        }
    } else {
        PokyhIconButton(
            icon = PokyhIcons.profile,
            contentDescription = "Profil",
            onClick = onClick,
            tinted = true,
        )
    }
}

// ── Sections ────────────────────────────────────────────────────────────────

/** The one place in the app with a centered layout: an identity block reads as a portrait, and
 * centering it is what makes the rest of the screen read as *settings about* that person. */
@Composable
private fun HeaderSection(session: UserSession, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        UntisAvatar(
            rawImageUrl = session.imageUrl,
            name = session.personName ?: session.username,
            auth = UntisImageAuth.of(session),
            size = 88.dp,
        )
        Spacer(Modifier.size(PokyhSpacing.md))
        Text(
            text = session.personName ?: session.username,
            style = PokyhType.title1,
            color = PokyhTheme.colors.textPrimary,
        )
        Text(
            text = if (session.isParent) "Erziehungsberechtigt" else "Schüler/in",
            style = PokyhType.callout,
            color = PokyhTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun KontoSection(
    session: UserSession,
    backendStatus: BackendStatus,
    onOpenConnectionStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhSection(title = "Konto", modifier = modifier) {
        PokyhCard(padding = 0.dp) {
            PokyhRow(
                title = session.username,
                subtitle = "Benutzername",
                leading = { RowGlyph(PokyhIcons.person) },
                showChevron = false,
            )
            if (session.klasseName.isNotEmpty()) {
                PokyhRowSeparator()
                PokyhRow(
                    title = session.klasseName,
                    subtitle = "Klasse",
                    leading = { RowGlyph(PokyhIcons.classMembers) },
                    showChevron = false,
                )
            }
            PokyhRowSeparator()
            PokyhRow(
                title = "LBS Brixen",
                subtitle = "Schule",
                leading = { RowGlyph(PokyhIcons.school) },
                showChevron = false,
            )
            PokyhRowSeparator()
            PokyhRow(
                title = "POKYH-Konto",
                leading = { RowGlyph(PokyhIcons.info) },
                onClick = onOpenConnectionStatus,
                trailing = { BackendStatusBadge(backendStatus) },
            )
        }
    }
}

/** A row's leading glyph, at one size and one tint throughout the settings lists. */
@Composable
private fun RowGlyph(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = PokyhTheme.colors.textTertiary,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun BackendStatusBadge(status: BackendStatus) {
    val connected = status is BackendStatus.Ok
    StatusLabel(
        text = status.uiLabel(),
        color = if (connected) Brand.success else Brand.warning,
        icon = if (connected) PokyhIcons.ok else PokyhIcons.warningBadge,
    )
}

@Composable
private fun DarstellungSection(
    themeMode: PokyhThemeMode,
    onSelect: (PokyhThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    PokyhSection(title = "Darstellung", modifier = modifier) {
        PokyhCard(padding = 0.dp) {
            Box {
                PokyhRow(
                    title = "Erscheinungsbild",
                    leading = { RowGlyph(PokyhIcons.appearance) },
                    onClick = { expanded = true },
                    // A menu, not a push — so a caret rather than PokyhRow's navigation chevron.
                    showChevron = false,
                    trailing = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                        ) {
                            StatusLabel(
                                text = themeMode.label(),
                                color = PokyhTheme.colors.textSecondary,
                                icon = themeMode.icon(),
                            )
                            PokyhRowMenuCaret(expanded = expanded)
                        }
                    },
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    PokyhThemeMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label(), style = PokyhType.body) },
                            leadingIcon = { Icon(mode.icon(), contentDescription = null) },
                            trailingIcon = {
                                if (mode == themeMode) {
                                    Icon(
                                        imageVector = PokyhIcons.check,
                                        contentDescription = null,
                                        tint = PokyhTheme.colors.accentText,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                            onClick = { onSelect(mode); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

private fun PokyhThemeMode.label(): String = when (this) {
    PokyhThemeMode.System -> "System"
    PokyhThemeMode.Light -> "Hell"
    PokyhThemeMode.Dark -> "Dunkel"
}

private fun PokyhThemeMode.icon(): ImageVector = when (this) {
    PokyhThemeMode.System -> PokyhIcons.themeSystem
    PokyhThemeMode.Light -> PokyhIcons.themeLight
    PokyhThemeMode.Dark -> PokyhIcons.themeDark
}

/** The two destructive actions in one card, with the explanation as the row's own subtitle
 * rather than as loose text under a card. */
@Composable
private fun DangerSection(
    clearing: Boolean,
    onLogout: () -> Unit,
    onClearData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhCard(modifier = modifier, padding = 0.dp) {
        PokyhRow(
            title = "Abmelden",
            titleColor = Brand.danger,
            leading = {
                Icon(
                    imageVector = PokyhIcons.signOut,
                    contentDescription = null,
                    tint = Brand.danger,
                    modifier = Modifier.size(20.dp),
                )
            },
            onClick = onLogout,
            showChevron = false,
        )
        PokyhRowSeparator()
        PokyhRow(
            title = "Cache & Daten löschen",
            titleColor = Brand.danger,
            subtitle = "Entfernt alle Konten, den Offline-Stundenplan, den Noten-Cache und alle App-Daten " +
                "von diesem Gerät.",
            leading = {
                if (clearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Brand.danger,
                    )
                } else {
                    Icon(
                        imageVector = PokyhIcons.delete,
                        contentDescription = null,
                        tint = Brand.danger,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            onClick = onClearData,
            enabled = !clearing,
            showChevron = false,
        )
    }
}

// ── Accounts ────────────────────────────────────────────────────────────────

@Composable
private fun AccountRow(
    account: SavedAccount,
    isActive: Boolean,
    isDefault: Boolean,
    hasPassword: Boolean,
    isSwitching: Boolean,
    isRefreshing: Boolean,
    isAnyBusy: Boolean,
    activeAuth: UntisImageAuth?,
    onClick: () -> Unit,
    onToggleDefault: () -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onSignOut: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    val secondary = if (!account.nickname.isNullOrEmpty()) {
        "${account.username} · ${account.displayName}"
    } else {
        account.displayName
    }

    PokyhTileRow(
        modifier = modifier,
        onClick = if (isAnyBusy) null else onClick,
    ) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (isActive) {
                Box(Modifier.size(44.dp).border(2.dp, Brand.accent, PokyhShapes.pill))
            }
            // Only the *active* account's picture can load — the session headers belong to the
            // signed-in user, so other rows fall back to their initial (which is what the web
            // does too: it only ever proxies the logged-in user's image).
            UntisAvatar(
                rawImageUrl = if (isActive) account.imageUrl else null,
                name = account.title,
                auth = activeAuth,
                size = 36.dp,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                Text(
                    text = account.title,
                    style = PokyhType.headline,
                    color = colors.textPrimary,
                )
                if (isDefault) MiniBadge("Standard", Brand.accent, icon = PokyhIcons.starFilled)
                if (!hasPassword) MiniBadge("Passwort nötig", Brand.warning)
            }
            Text(secondary, style = PokyhType.footnote, color = colors.textSecondary)
        }
        when {
            isSwitching || isRefreshing -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Brand.accent,
            )
            isActive -> Icon(
                imageVector = PokyhIcons.ok,
                contentDescription = "Aktives Konto",
                tint = Brand.accent,
                modifier = Modifier.size(22.dp),
            )
        }
        AccountOverflowMenu(
            isDefault = isDefault,
            hasPassword = hasPassword,
            enabled = !isAnyBusy,
            onToggleDefault = onToggleDefault,
            onRefresh = onRefresh,
            onRename = onRename,
            onSignOut = onSignOut,
            onRemove = onRemove,
        )
    }
}

@Composable
private fun AccountOverflowMenu(
    isDefault: Boolean,
    hasPassword: Boolean,
    enabled: Boolean,
    onToggleDefault: () -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onSignOut: () -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PokyhIconButton(
            icon = PokyhIcons.more,
            contentDescription = "Mehr",
            onClick = { expanded = true },
            enabled = enabled,
            tint = PokyhTheme.colors.textSecondary,
            size = 32.dp,
            iconSize = 18.dp,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (isDefault) "Als Standard entfernen" else "Als Standard festlegen",
                        style = PokyhType.body,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isDefault) PokyhIcons.starEmpty else PokyhIcons.starFilled,
                        contentDescription = null,
                    )
                },
                onClick = { expanded = false; onToggleDefault() },
            )
            DropdownMenuItem(
                text = { Text("Konto aktualisieren", style = PokyhType.body) },
                leadingIcon = { Icon(PokyhIcons.refresh, contentDescription = null) },
                onClick = { expanded = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text("Umbenennen", style = PokyhType.body) },
                leadingIcon = { Icon(PokyhIcons.edit, contentDescription = null) },
                onClick = { expanded = false; onRename() },
            )
            if (hasPassword) {
                DropdownMenuItem(
                    text = { Text("Abmelden", style = PokyhType.body) },
                    leadingIcon = { Icon(PokyhIcons.signOut, contentDescription = null) },
                    onClick = { expanded = false; onSignOut() },
                )
            }
            DropdownMenuItem(
                text = { Text("Account entfernen", style = PokyhType.body, color = Brand.danger) },
                leadingIcon = { Icon(PokyhIcons.delete, contentDescription = null, tint = Brand.danger) },
                onClick = { expanded = false; onRemove() },
            )
        }
    }
}

// ── Dialogs ─────────────────────────────────────────────────────────────────

/**
 * The app's one confirmation dialog shape. Stock [AlertDialog] shows generic Material chrome —
 * its own radius, its own container color, its own button typography — so it's re-skinned here
 * once with the app's shape, surface and text buttons, and every confirmation goes through it.
 */
@Composable
fun PokyhDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmColor: Color = PokyhTheme.colors.accentText,
    dismissLabel: String? = "Abbrechen",
    extraAction: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PokyhShapes.xl,
        containerColor = PokyhTheme.colors.card,
        title = { Text(title, style = PokyhType.title3, color = PokyhTheme.colors.textPrimary) },
        text = { Text(body, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary) },
        confirmButton = { PokyhTextButton(text = confirmLabel, color = confirmColor, onClick = onConfirm) },
        dismissButton = {
            Row {
                if (extraAction != null) extraAction()
                if (dismissLabel != null) {
                    PokyhTextButton(
                        text = dismissLabel,
                        color = PokyhTheme.colors.textSecondary,
                        onClick = onDismiss,
                    )
                }
            }
        },
    )
}

@Composable
private fun LogoutConfirmDialog(
    onDismiss: () -> Unit,
    onLogoutOnly: () -> Unit,
    onLogoutAndRemove: () -> Unit,
) {
    PokyhDialog(
        title = "Wirklich abmelden?",
        body = "Du kannst dich abmelden oder das gespeicherte Konto ganz vom Gerät entfernen.",
        onDismiss = onDismiss,
        confirmLabel = "Abmelden",
        confirmColor = Brand.danger,
        onConfirm = onLogoutOnly,
        extraAction = {
            PokyhTextButton(text = "Abmelden & löschen", color = Brand.danger, onClick = onLogoutAndRemove)
        },
    )
}
