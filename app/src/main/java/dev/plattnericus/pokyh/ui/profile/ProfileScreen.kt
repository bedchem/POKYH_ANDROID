@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.profile

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.ui.components.MiniBadge
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhRow
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
import dev.plattnericus.pokyh.ui.components.avatarCacheKey
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.popIn
import dev.plattnericus.pokyh.ui.theme.pressable

/**
 * Profil — a pushed full-screen route (from the header actions on every tab root): who is signed
 * in, their account, the activity, and the accounts saved on the device. The settings have their
 * own page now, as on the web ([dev.plattnericus.pokyh.ui.settings.SettingsScreen]); a row near
 * the top leads there.
 *
 * Account details are a grouped [PokyhCard] of [PokyhRow]s; the accounts list keeps standalone
 * [PokyhTileRow]s, because a row there is a *selectable object* with its own active state and
 * overflow menu, not a setting.
 */
@Composable
fun ProfileScreen(
    onNavigateUp: () -> Unit,
    onConnectionStatus: () -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val t = strings().profile
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val accountsWithPassword by viewModel.accountsWithPassword.collectAsStateWithLifecycle()
    val defaultUsername by viewModel.defaultUsername.collectAsStateWithLifecycle()
    val switchingUsername by viewModel.switchingUsername.collectAsStateWithLifecycle()
    val refreshingUsername by viewModel.refreshingUsername.collectAsStateWithLifecycle()
    val switchError by viewModel.switchError.collectAsStateWithLifecycle()

    var confirmLogout by remember { mutableStateOf(false) }
    var accountToRemove by remember { mutableStateOf<SavedAccount?>(null) }
    var renameTarget by remember { mutableStateOf<SavedAccount?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = t.title,
                nav = TopBarNav.Back(onNavigateUp),
                actions = {
                    PokyhIconButton(icon = PokyhIcons.settings, contentDescription = t.settings, onClick = onOpenSettings, tinted = true)
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().appBackground().padding(padding),
            contentPadding = PaddingValues(start = PokyhSpacing.screenH, end = PokyhSpacing.screenH, bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
        ) {
            if (session != null) {
                item(key = "header") { HeaderSection(session!!, Modifier.fadeIn()) }
            }
            item(key = "settings") {
                PokyhCard(modifier = Modifier.fadeIn(delayMillis = 20), padding = 0.dp) {
                    PokyhRow(
                        title = t.settings,
                        subtitle = t.settingsHint,
                        leading = { RowGlyph(PokyhIcons.settings) },
                        onClick = onOpenSettings,
                    )
                }
            }
            if (session != null) {
                item(key = "konto") {
                    KontoSection(
                        session = session!!,
                        backendStatus = backendStatus,
                        onOpenConnectionStatus = onConnectionStatus,
                        modifier = Modifier.fadeIn(delayMillis = 30),
                    )
                }
                item(key = "aktivitaet") {
                    ActivitySection(viewModel = viewModel, onOpenStats = onOpenStats, modifier = Modifier.fadeIn(delayMillis = 45))
                }
            }

            item(key = "konten_title") {
                PokyhSectionHeader(title = t.accounts, modifier = Modifier.fadeIn(delayMillis = 80))
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
                    Icon(imageVector = PokyhIcons.addAccount, contentDescription = null, tint = PokyhTheme.colors.accentText, modifier = Modifier.size(20.dp))
                    Text(text = t.addAccount, style = PokyhType.body, color = PokyhTheme.colors.accentText, modifier = Modifier.weight(1f))
                }
            }
            // Signing out at the very bottom too, not only in the settings.
            if (session != null) {
                item(key = "logout") {
                    PokyhCard(modifier = Modifier.fadeIn(delayMillis = 110), padding = 0.dp) {
                        PokyhRow(
                            title = strings().settings.logout,
                            titleColor = Brand.danger,
                            leading = { Icon(PokyhIcons.signOut, contentDescription = null, tint = Brand.danger, modifier = Modifier.size(20.dp)) },
                            onClick = { confirmLogout = true },
                            showChevron = false,
                        )
                    }
                }
            }
        }
    }

    if (confirmLogout && session != null) {
        PokyhDialog(
            title = strings().settings.logoutTitle,
            body = strings().settings.logoutBody,
            onDismiss = { confirmLogout = false },
            confirmLabel = strings().settings.logout,
            confirmColor = Brand.danger,
            onConfirm = {
                confirmLogout = false
                viewModel.logoutCurrentAccount(onDone = onNavigateUp)
            },
        )
    }

    accountToRemove?.let { acc ->
        PokyhDialog(
            title = t.removeAccountTitle,
            body = t.removeAccountBody(acc.username),
            onDismiss = { accountToRemove = null },
            confirmLabel = t.removeAccountConfirm,
            confirmColor = Brand.danger,
            onConfirm = { viewModel.removeAccount(acc.username); accountToRemove = null },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            shape = PokyhShapes.xl,
            containerColor = PokyhTheme.colors.card,
            title = { Text(t.renameTitle, style = PokyhType.title3, color = PokyhTheme.colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                    Text(text = t.renameBody(target.username), style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                    PokyhTextField(value = renameText, onValueChange = { renameText = it }, placeholder = t.nickname, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                PokyhTextButton(
                    text = strings().common.save,
                    onClick = { viewModel.renameAccount(target.username, renameText); renameTarget = null },
                )
            },
            dismissButton = {
                Row {
                    if (!target.nickname.isNullOrEmpty()) {
                        PokyhTextButton(
                            text = t.remove,
                            color = Brand.danger,
                            onClick = { viewModel.renameAccount(target.username, null); renameTarget = null },
                        )
                    }
                    PokyhTextButton(text = strings().common.cancel, color = PokyhTheme.colors.textSecondary, onClick = { renameTarget = null })
                }
            },
        )
    }

    switchError?.let { message ->
        PokyhDialog(
            title = t.switchErrorTitle,
            body = message,
            onDismiss = viewModel::dismissSwitchError,
            confirmLabel = strings().common.ok,
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
    if (session != null) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CurrentUserAvatar(size = 28.dp)
        }
    } else {
        PokyhIconButton(icon = PokyhIcons.profile, contentDescription = strings().profile.title, onClick = onClick, tinted = true)
    }
}

// ── Sections ────────────────────────────────────────────────────────────────

/** The one place in the app with a centered layout: an identity block reads as a portrait.
 * The portrait itself is tappable and opens at full size. */
@Composable
private fun HeaderSection(session: UserSession, modifier: Modifier = Modifier) {
    val t = strings().profile
    var avatarOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        UntisAvatar(
            rawImageUrl = session.imageUrl,
            name = session.personName ?: session.username,
            auth = UntisImageAuth.of(session),
            cacheKey = avatarCacheKey(session.username),
            size = 88.dp,
            modifier = Modifier
                .clip(PokyhShapes.pill)
                .pressable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClickLabel = t.openPicture, onClick = { avatarOpen = true }),
        )
        Spacer(Modifier.size(PokyhSpacing.md))
        Text(text = session.personName ?: session.username, style = PokyhType.title1, color = PokyhTheme.colors.textPrimary)
        Text(text = if (session.isParent) t.parent else t.student, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
    }

    if (avatarOpen) {
        AvatarViewerDialog(session = session, onDismiss = { avatarOpen = false })
    }
}

/** The profile picture at full size on a dimmed backdrop; anywhere outside the portrait dismisses it. */
@Composable
private fun AvatarViewerDialog(session: UserSession, onDismiss: () -> Unit) {
    val dismissSource = remember { MutableInteractionSource() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(interactionSource = dismissSource, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xl),
                modifier = Modifier.padding(PokyhSpacing.xxxl).popIn(),
            ) {
                UntisAvatar(
                    rawImageUrl = session.imageUrl,
                    name = session.personName ?: session.username,
                    auth = UntisImageAuth.of(session),
                    cacheKey = avatarCacheKey(session.username),
                    size = 280.dp,
                )
                Text(text = session.personName ?: session.username, style = PokyhType.title2, color = Color.White)
            }
        }
    }
}

@Composable
private fun KontoSection(
    session: UserSession,
    backendStatus: BackendStatus,
    onOpenConnectionStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings().profile
    PokyhSection(title = t.account, modifier = modifier) {
        PokyhCard(padding = 0.dp) {
            PokyhRow(title = session.username, subtitle = t.username, leading = { RowGlyph(PokyhIcons.person) }, showChevron = false)
            if (session.klasseName.isNotEmpty()) {
                PokyhRowSeparator()
                PokyhRow(title = session.klasseName, subtitle = t.klasse, leading = { RowGlyph(PokyhIcons.classMembers) }, showChevron = false)
            }
            PokyhRowSeparator()
            PokyhRow(title = t.schoolName, subtitle = t.school, leading = { RowGlyph(PokyhIcons.school) }, showChevron = false)
            PokyhRowSeparator()
            PokyhRow(
                title = t.pokyhAccount,
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
    Icon(imageVector = icon, contentDescription = null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(20.dp))
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
    val t = strings().profile
    val colors = PokyhTheme.colors
    val secondary = if (!account.nickname.isNullOrEmpty()) "${account.username} · ${account.displayName}" else account.displayName

    PokyhTileRow(modifier = modifier, onClick = if (isAnyBusy) null else onClick) {
        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (isActive) Box(Modifier.size(44.dp).border(2.dp, Brand.accent, PokyhShapes.pill))
            // Only the *active* account's picture can load live — the session headers belong to the
            // signed-in user. Other rows show the copy stored the last time they were active.
            UntisAvatar(
                rawImageUrl = if (isActive) account.imageUrl else null,
                name = account.title,
                auth = activeAuth,
                cacheKey = avatarCacheKey(account.username),
                size = 36.dp,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                Text(text = account.title, style = PokyhType.headline, color = colors.textPrimary)
                if (isDefault) MiniBadge(t.standard, Brand.accent, icon = PokyhIcons.starFilled)
                if (!hasPassword) MiniBadge(t.needsPassword, Brand.warning)
            }
            Text(secondary, style = PokyhType.footnote, color = colors.textSecondary)
        }
        when {
            isSwitching || isRefreshing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.accent)
            isActive -> Icon(imageVector = PokyhIcons.ok, contentDescription = t.activeAccount, tint = Brand.accent, modifier = Modifier.size(22.dp))
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
    val t = strings().profile
    var expanded by remember { mutableStateOf(false) }
    Box {
        PokyhIconButton(
            icon = PokyhIcons.more,
            contentDescription = strings().common.more,
            onClick = { expanded = true },
            enabled = enabled,
            tint = PokyhTheme.colors.textSecondary,
            size = 32.dp,
            iconSize = 18.dp,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = if (isDefault) t.unsetDefault else t.setDefault, style = PokyhType.body) },
                leadingIcon = { Icon(imageVector = if (isDefault) PokyhIcons.starEmpty else PokyhIcons.starFilled, contentDescription = null) },
                onClick = { expanded = false; onToggleDefault() },
            )
            DropdownMenuItem(
                text = { Text(t.refreshAccount, style = PokyhType.body) },
                leadingIcon = { Icon(PokyhIcons.refresh, contentDescription = null) },
                onClick = { expanded = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text(t.rename, style = PokyhType.body) },
                leadingIcon = { Icon(PokyhIcons.edit, contentDescription = null) },
                onClick = { expanded = false; onRename() },
            )
            if (hasPassword) {
                DropdownMenuItem(
                    text = { Text(t.signOut, style = PokyhType.body) },
                    leadingIcon = { Icon(PokyhIcons.signOut, contentDescription = null) },
                    onClick = { expanded = false; onSignOut() },
                )
            }
            DropdownMenuItem(
                text = { Text(t.removeAccount, style = PokyhType.body, color = Brand.danger) },
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
    dismissLabel: String? = I18n.s.common.cancel,
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
                    PokyhTextButton(text = dismissLabel, color = PokyhTheme.colors.textSecondary, onClick = onDismiss)
                }
            }
        },
    )
}
