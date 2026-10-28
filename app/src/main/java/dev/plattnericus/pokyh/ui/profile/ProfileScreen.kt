@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.SettingsGroup
import dev.plattnericus.pokyh.ui.components.SettingsGroupGap
import dev.plattnericus.pokyh.ui.components.SettingsItem
import dev.plattnericus.pokyh.ui.components.SettingsLinkItem
import dev.plattnericus.pokyh.ui.components.SettingsRule
import dev.plattnericus.pokyh.ui.components.SettingsType
import dev.plattnericus.pokyh.ui.components.StatusPill
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.components.UntisAvatar
import dev.plattnericus.pokyh.ui.components.UntisImageAuth
import dev.plattnericus.pokyh.ui.components.avatarCacheKey
import dev.plattnericus.pokyh.ui.settings.LogoutItem
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.popIn
import dev.plattnericus.pokyh.ui.theme.pressHighlight

private const val WEBSITE_URL = "https://pokyh.com"

/**
 * Profil, as the web's (app/[lang]/(app)/profile): who you are (the picture, the name large, the
 * class, the school and the role under it), when you used POKYH (four figures and a GitHub-style
 * grid of days, the statistics one step further), then — like the end of the settings — the
 * account as rows under thin rules, the way to the settings, to the website and out.
 *
 * Beyond the web: the accounts saved on this phone, to switch between them, and the POKYH
 * account's connection — what only the app has.
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
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val accountsWithPassword by viewModel.accountsWithPassword.collectAsStateWithLifecycle()
    val defaultUsername by viewModel.defaultUsername.collectAsStateWithLifecycle()
    val switchingUsername by viewModel.switchingUsername.collectAsStateWithLifecycle()
    val refreshingUsername by viewModel.refreshingUsername.collectAsStateWithLifecycle()
    val switchError by viewModel.switchError.collectAsStateWithLifecycle()

    var accountToRemove by remember { mutableStateOf<SavedAccount?>(null) }
    var renameTarget by remember { mutableStateOf<SavedAccount?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = null,
                nav = TopBarNav.Back(onNavigateUp),
                actions = {
                    PokyhIconButton(icon = PokyhIcons.settings, contentDescription = t.settings, onClick = onOpenSettings, tinted = true)
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().appBackground().padding(padding),
            contentPadding = PaddingValues(start = PokyhSpacing.screenH, end = PokyhSpacing.screenH, bottom = 80.dp),
        ) {
            session?.let { s -> item(key = "header") { HeaderSection(s, Modifier.fadeIn()) } }

            if (session != null) {
                item(key = "activity") {
                    ActivitySection(viewModel = viewModel, onOpenStats = onOpenStats, onOpenSettings = onOpenSettings, modifier = Modifier.fadeIn(delayMillis = 30))
                }
            }

            item(key = "accounts_title") {
                Column(Modifier.padding(top = SettingsGroupGap).fadeIn(delayMillis = 60)) {
                    Text(t.accounts, style = SettingsType.groupTitle, color = PokyhTheme.colors.textPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text(I18n.s.account.accountsHint, style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 24.sp), color = PokyhTheme.colors.textSecondary)
                    Spacer(Modifier.height(12.dp))
                }
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
                    modifier = Modifier.fadeIn(delayMillis = 70),
                )
            }
            item(key = "add_account") {
                AddAccountRow(onClick = viewModel::addAccount, modifier = Modifier.fadeIn(delayMillis = 80))
            }

            // Like the end of the settings: the account as rows under thin rules, then the way to
            // the settings, to the website and out.
            session?.let { s ->
                item(key = "account") {
                    AccountGroup(
                        session = s,
                        backendStatus = backendStatus,
                        onOpenConnectionStatus = onConnectionStatus,
                        onOpenSettings = onOpenSettings,
                        onOpenWebsite = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL))) } },
                        onLogout = { viewModel.logoutCurrentAccount(onDone = onNavigateUp) },
                        modifier = Modifier.padding(top = SettingsGroupGap).fadeIn(delayMillis = 100),
                    )
                }
            }
        }
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

/**
 * Who you are, as the web's profile header: the picture (tap it to see it at full size), the name
 * large beside it, the class, the school and the role under the name.
 */
@Composable
private fun HeaderSection(session: UserSession, modifier: Modifier = Modifier) {
    val t = strings().profile
    val colors = PokyhTheme.colors
    var avatarOpen by remember { mutableStateOf(false) }
    val name = session.personName ?: session.username
    val meta = listOf(
        session.klasseName,
        session.schoolRef.name.ifEmpty { session.schoolRef.loginName },
        if (session.isParent) t.parent else t.student,
    ).filter { it.isNotBlank() }.joinToString(" · ")

    Row(
        modifier.fillMaxWidth().padding(top = 16.dp, bottom = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        UntisAvatar(
            rawImageUrl = session.imageUrl,
            name = name,
            auth = UntisImageAuth.of(session),
            cacheKey = avatarCacheKey(session.username),
            size = 88.dp,
            color = PokyhDecorative.periwinkle(colors.isDark).fill,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = t.openPicture, onClick = { avatarOpen = true }),
        )
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = PokyhType.body.copy(fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(meta, style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = colors.textSecondary)
        }
    }

    if (avatarOpen) {
        AvatarViewerDialog(session = session, onDismiss = { avatarOpen = false })
    }
}

/**
 * "Konto", as the end of the web's profile: what is known about the account, each in a row with
 * its value beside it (the IDs to copy), the POKYH account's connection, then the way to the
 * settings, to the website and out.
 */
@Composable
private fun AccountGroup(
    session: UserSession,
    backendStatus: BackendStatus,
    onOpenConnectionStatus: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWebsite: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings().profile
    val ta = strings().account
    data class Detail(val label: String, val value: String, val copy: Boolean = false, val mono: Boolean = false)
    val details = listOf(
        Detail(t.username, session.username, copy = true),
        Detail(t.studentId, session.studentId.takeIf { session.hasUntis && it > 0 }?.toString().orEmpty(), copy = true),
        Detail(t.klasse, session.klasseName),
        Detail(ta.schoolLabel, session.schoolRef.name.ifEmpty { session.schoolRef.loginName }),
        Detail(t.userId, session.stableUid.orEmpty(), copy = true, mono = true),
        Detail(t.classIdLabel, session.classId.orEmpty(), copy = true, mono = true),
    ).filter { it.value.isNotBlank() }

    SettingsGroup(title = t.account, modifier = modifier) {
        details.forEach { d ->
            SettingsItem(title = d.label, inline = true) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        d.value,
                        style = if (d.mono) PokyhType.body.copy(fontSize = 14.sp, fontFamily = FontFamily.Monospace) else PokyhType.body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold),
                        color = PokyhTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (d.copy) CopyButton(d.value, d.label)
                }
            }
        }
        val connected = backendStatus is BackendStatus.Ok
        val dark = PokyhTheme.colors.isDark
        SettingsLinkItem(
            title = t.pokyhAccount,
            onClick = onOpenConnectionStatus,
            trailing = { StatusPill(backendStatus.uiLabel(), if (connected) PokyhDecorative.sage(dark) else PokyhDecorative.butter(dark)) },
        )
        SettingsLinkItem(title = t.settings, hint = ta.settingsHint, onClick = onOpenSettings)
        SettingsLinkItem(title = ta.website, hint = ta.websiteHint, onClick = onOpenWebsite, external = true)
        LogoutItem(onLogout = onLogout, withIcon = true)
    }
}

/** Copies a value; the icon turns into a green tick for a moment, nothing else moves. */
@Composable
private fun CopyButton(value: String, label: String) {
    val clipboard = LocalClipboardManager.current
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(done) {
        if (done) {
            kotlinx.coroutines.delay(1500)
            done = false
        }
    }
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .pressHighlight(source, CircleShape)
            .clickable(interactionSource = source, indication = null, onClickLabel = "$label ${strings().profile.copy}") {
                clipboard.setText(AnnotatedString(value))
                done = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (done) PokyhIcons.check else PokyhIcons.copy,
            contentDescription = if (done) strings().profile.copied else null,
            tint = if (done) Brand.success else PokyhTheme.colors.textTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

// ── Accounts ────────────────────────────────────────────────────────────────

/**
 * An account saved on this phone, as a row under a rule like the rest of the page: its picture
 * (ringed while it is the one signed in), its name with its badges, who it is, and its menu.
 */
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
    val dark = colors.isDark
    val secondary = if (!account.nickname.isNullOrEmpty()) "${account.username} · ${account.displayName}" else account.displayName
    val source = remember { MutableInteractionSource() }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)

    Column(modifier.fillMaxWidth()) {
        SettingsRule()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(shape)
                .pressHighlight(source, shape)
                .clickable(interactionSource = source, indication = null, enabled = !isAnyBusy && !isActive, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).then(if (isActive) Modifier.background(Brand.accent, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                // Only the *active* account's picture can load live — the session headers belong to
                // the signed-in user. Other rows show the copy stored the last time they were active.
                UntisAvatar(
                    rawImageUrl = if (isActive) account.imageUrl else null,
                    name = account.title,
                    auth = activeAuth,
                    cacheKey = avatarCacheKey(account.username),
                    size = if (isActive) 38.dp else 44.dp,
                    color = PokyhDecorative.periwinkle(dark).fill,
                )
            }
            Column(Modifier.weight(1f)) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(account.title, style = SettingsType.rowTitle, color = colors.textPrimary)
                    if (isDefault) StatusPill(t.standard, PokyhDecorative.periwinkle(dark), icon = PokyhIcons.starFilled)
                    if (!hasPassword) StatusPill(t.needsPassword, PokyhDecorative.butter(dark))
                }
                Spacer(Modifier.height(2.dp))
                Text(secondary, style = SettingsType.rowHint, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                isSwitching || isRefreshing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.accent)
                isActive -> Icon(imageVector = PokyhIcons.check, contentDescription = t.activeAccount, tint = Brand.accent, modifier = Modifier.size(20.dp))
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
}

/** "Konto hinzufügen": the last row of the accounts, in the accent. */
@Composable
private fun AddAccountRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth()) {
        SettingsRule()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(shape)
                .pressHighlight(source, shape)
                .clickable(interactionSource = source, indication = null, onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).background(PokyhTheme.colors.accentTint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(PokyhIcons.addAccount, contentDescription = null, tint = PokyhTheme.colors.accentText, modifier = Modifier.size(20.dp))
            }
            Text(strings().profile.addAccount, style = SettingsType.rowTitle, color = PokyhTheme.colors.accentText, modifier = Modifier.weight(1f))
        }
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
