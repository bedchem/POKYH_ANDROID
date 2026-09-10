package dev.plattnericus.pokyh.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.SavedAccount
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.ui.components.PokyhAvatar
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn

private const val PRIVACY_URL = "https://pokyh.com/datenschutz"
private const val TERMS_URL = "https://pokyh.com/nutzungsbedingungen"
private const val SUPPORT_EMAIL = "support@pokyh.com"

/**
 * ProfileView.swift, ported. A full-screen route (pushed from [ProfileToolbarAction] in every
 * tab's top bar) rather than iOS's sheet — same sections, same order.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text("Profil", style = PokyhType.headline, color = PokyhTheme.colors.textPrimary) },
                actions = { TextButton(onClick = onNavigateUp) { Text("Fertig") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PokyhTheme.colors.bg),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().appBackground().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                DarstellungSection(themeMode = themeMode, onSelect = viewModel::setThemeMode, modifier = Modifier.fadeIn(delayMillis = 60))
            }

            item(key = "konten_title") { SectionTitle("Konten") }
            items(accounts, key = { it.username }) { acc ->
                AccountRow(
                    account = acc,
                    isActive = acc.username == session?.username,
                    isDefault = acc.username == defaultUsername,
                    hasPassword = acc.username in accountsWithPassword,
                    isSwitching = acc.username == switchingUsername,
                    isRefreshing = acc.username == refreshingUsername,
                    isAnyBusy = switchingUsername != null,
                    onClick = { viewModel.switchAccount(acc.username) { onNavigateUp() } },
                    onToggleDefault = {
                        viewModel.setDefaultAccount(if (acc.username == defaultUsername) null else acc.username)
                    },
                    onRefresh = { viewModel.refreshAccount(acc.username) },
                    onRename = { renameText = acc.nickname.orEmpty(); renameTarget = acc },
                    onSignOut = { viewModel.signOutAccount(acc.username) },
                    onRemove = { accountToRemove = acc },
                    modifier = Modifier.fadeIn(delayMillis = 80),
                )
            }
            item(key = "add_account") {
                AddAccountRow(onClick = viewModel::addAccount, modifier = Modifier.fadeIn(delayMillis = 90))
            }

            item(key = "logout") {
                DestructiveActionCard(
                    label = "Abmelden",
                    icon = PokyhIcons.rectangle_portrait_and_arrow_right,
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fadeIn(delayMillis = 100),
                )
            }

            item(key = "clear_data") {
                Column(modifier = Modifier.fadeIn(delayMillis = 110), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DestructiveActionCard(
                        label = "Cache & Daten löschen",
                        icon = PokyhIcons.trash,
                        busy = clearing,
                        onClick = { confirmClearData = true },
                    )
                    Text(
                        "Löscht alle gespeicherten Konten, den Offline-Stundenplan, Noten-Cache und alle App-Daten von diesem Gerät.",
                        style = PokyhType.caption2,
                        color = PokyhTheme.colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            item(key = "about_title") { SectionTitle("Über & Rechtliches") }
            item(key = "about") {
                PokyhCard(modifier = Modifier.fadeIn(delayMillis = 120)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LinkRow("Datenschutzerklärung", PokyhIcons.hand_raised_fill) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
                        }
                        LinkRow("Nutzungsbedingungen", PokyhIcons.doc_text) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL)))
                        }
                        LinkRow("Support kontaktieren", PokyhIcons.envelope) {
                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")))
                        }
                        InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    }
                }
            }

            item(key = "footer") {
                Text(
                    "POKYH · Nicht offiziell mit der LBS Brixen oder WebUntis verbunden.",
                    style = PokyhType.caption2,
                    color = PokyhTheme.colors.textTertiary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).fadeIn(delayMillis = 130),
                )
            }
        }
    }

    if (confirmLogout && session != null) {
        LogoutConfirmDialog(
            username = session!!.username,
            onDismiss = { confirmLogout = false },
            onLogoutOnly = { confirmLogout = false; viewModel.logoutCurrentAccount(alsoRemoveFromDevice = false, onDone = onNavigateUp) },
            onLogoutAndRemove = { confirmLogout = false; viewModel.logoutCurrentAccount(alsoRemoveFromDevice = true, onDone = onNavigateUp) },
        )
    }

    accountToRemove?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            title = { Text("Konto entfernen?") },
            text = { Text("Die gespeicherten Anmeldedaten von ${acc.username} werden vom Gerät gelöscht.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeAccount(acc.username); accountToRemove = null }) {
                    Text("Konto entfernen", color = Brand.danger)
                }
            },
            dismissButton = { TextButton(onClick = { accountToRemove = null }) { Text("Abbrechen") } },
        )
    }

    if (confirmClearData) {
        AlertDialog(
            onDismissRequest = { confirmClearData = false },
            title = { Text("Cache & alle Daten löschen?") },
            text = {
                Text(
                    "Alle gespeicherten Konten, Anmeldedaten, der Offline-Stundenplan, der Noten-Cache und sämtliche " +
                        "App-Einstellungen werden entfernt. Du musst dich danach neu anmelden.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmClearData = false; viewModel.clearAllData(onDone = onNavigateUp) }) {
                    Text("Alles löschen", color = Brand.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClearData = false }) { Text("Abbrechen") } },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Konto umbenennen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Vergib einen eigenen Namen für ${target.username} (nur lokal sichtbar).", style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("Spitzname") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.renameAccount(target.username, renameText); renameTarget = null }) { Text("Speichern") }
            },
            dismissButton = {
                Row {
                    if (!target.nickname.isNullOrEmpty()) {
                        TextButton(onClick = { viewModel.renameAccount(target.username, null); renameTarget = null }) {
                            Text("Entfernen", color = Brand.danger)
                        }
                    }
                    TextButton(onClick = { renameTarget = null }) { Text("Abbrechen") }
                }
            },
        )
    }

    switchError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSwitchError,
            title = { Text("Fehler beim Kontowechsel") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissSwitchError) { Text("OK") } },
        )
    }
}

/** Small avatar/person-icon toolbar action other screens embed in their own `TopAppBar actions`
 * to open [ProfileScreen] — mirrors iOS's tab-bar-adjacent profile entry point. Reuses
 * [ProfileViewModel] purely to read the current session; every instance shares the same
 * [dev.plattnericus.pokyh.state.AppState] singleton underneath. */
@Composable
fun ProfileToolbarAction(onClick: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    IconButton(onClick = onClick) {
        val s = session
        if (s != null) {
            PokyhAvatar(url = s.imageUrl, name = s.personName ?: s.username, size = 28.dp)
        } else {
            Icon(PokyhIcons.person_crop_circle, contentDescription = "Profil", tint = PokyhTheme.colors.textSecondary, modifier = Modifier.size(28.dp))
        }
    }
}

// ── Sections ─────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection(session: UserSession, modifier: Modifier = Modifier) {
    PokyhCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PokyhAvatar(url = session.imageUrl, name = session.personName ?: session.username, size = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(session.personName ?: session.username, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                Text(
                    if (session.isParent) "Erziehungsberechtigt" else "Schüler/in",
                    style = PokyhType.caption,
                    color = PokyhTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun KontoSection(session: UserSession, backendStatus: BackendStatus, onOpenConnectionStatus: () -> Unit, modifier: Modifier = Modifier) {
    PokyhCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoRow("Benutzername", session.username)
            if (session.klasseName.isNotEmpty()) InfoRow("Klasse", session.klasseName)
            InfoRow("Schule", "LBS Brixen")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenConnectionStatus)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("POKYH-Konto", style = PokyhType.body, color = PokyhTheme.colors.textSecondary, modifier = Modifier.weight(1f))
                BackendStatusBadge(backendStatus)
                Spacer(Modifier.width(6.dp))
                Icon(PokyhIcons.chevron_right, contentDescription = null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun BackendStatusBadge(status: BackendStatus) {
    val connected = status is BackendStatus.Ok
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            if (connected) PokyhIcons.checkmark_circle_fill else PokyhIcons.exclamationmark_circle_fill,
            contentDescription = null,
            tint = if (connected) Brand.success else Brand.orange,
            modifier = Modifier.size(16.dp),
        )
        Text(
            status.uiLabel(),
            style = PokyhType.subheadline,
            color = if (connected) PokyhTheme.colors.textPrimary else PokyhTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun DarstellungSection(themeMode: PokyhThemeMode, onSelect: (PokyhThemeMode) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    PokyhCard(modifier = modifier) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PokyhIcons.paintbrush_fill, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Erscheinungsbild", style = PokyhType.body, color = PokyhTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                Icon(themeMode.icon(), contentDescription = null, tint = PokyhTheme.colors.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(themeMode.label(), style = PokyhType.subheadline, color = PokyhTheme.colors.textSecondary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PokyhThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label()) },
                        leadingIcon = { Icon(mode.icon(), contentDescription = null) },
                        onClick = { onSelect(mode); expanded = false },
                    )
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
    PokyhThemeMode.System -> PokyhIcons.circle_lefthalf_filled
    PokyhThemeMode.Light -> PokyhIcons.sun_max_fill
    PokyhThemeMode.Dark -> PokyhIcons.moon_fill
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = PokyhTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun AccountRow(
    account: SavedAccount,
    isActive: Boolean,
    isDefault: Boolean,
    hasPassword: Boolean,
    isSwitching: Boolean,
    isRefreshing: Boolean,
    isAnyBusy: Boolean,
    onClick: () -> Unit,
    onToggleDefault: () -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onSignOut: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = PokyhShapes.r14
    val bg = if (isActive) Brand.accent.copy(alpha = 0.10f) else PokyhTheme.colors.card
    val secondary = if (!account.nickname.isNullOrEmpty()) "${account.username} · ${account.displayName}" else account.displayName

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, shape)
            .border(0.5.dp, PokyhTheme.colors.border, shape)
            .clickable(enabled = !isAnyBusy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            if (isActive) {
                Box(
                    modifier = Modifier.size(42.dp)
                        .border(2.5.dp, Brand.accent, CircleShape),
                )
            }
            PokyhAvatar(url = account.imageUrl, name = account.title, size = 34.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    account.title,
                    style = if (isActive) PokyhType.body.copy(fontWeight = FontWeight.SemiBold) else PokyhType.body,
                    color = PokyhTheme.colors.textPrimary,
                )
                if (isDefault) MiniBadge("Standard", Brand.accent, PokyhIcons.star_fill)
                if (!hasPassword) MiniBadge("Passwort nötig", Brand.orange, null)
            }
            Text(secondary, style = PokyhType.caption2, color = PokyhTheme.colors.textSecondary)
        }
        when {
            isSwitching || isRefreshing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            isActive -> Icon(PokyhIcons.checkmark_circle_fill, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(22.dp))
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
private fun MiniBadge(text: String, color: Color, icon: ImageVector?) {
    Row(
        modifier = Modifier.background(color.copy(alpha = 0.2f), androidx.compose.foundation.shape.RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(7.dp))
        Text(text, style = PokyhType.caption2.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = color)
    }
}

private fun androidx.compose.ui.unit.TextUnit.sp() = this
private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

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
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(PokyhIcons.ellipsis_circle, contentDescription = "Mehr", tint = PokyhTheme.colors.textSecondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(if (isDefault) "Als Standard entfernen" else "Als Standard festlegen") },
                leadingIcon = { Icon(if (isDefault) PokyhIcons.star_outline else PokyhIcons.star_fill, contentDescription = null) },
                onClick = { expanded = false; onToggleDefault() },
            )
            DropdownMenuItem(
                text = { Text("Konto aktualisieren") },
                leadingIcon = { Icon(PokyhIcons.arrow_clockwise, contentDescription = null) },
                onClick = { expanded = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text("Umbenennen") },
                leadingIcon = { Icon(PokyhIcons.pencil, contentDescription = null) },
                onClick = { expanded = false; onRename() },
            )
            if (hasPassword) {
                DropdownMenuItem(
                    text = { Text("Abmelden") },
                    leadingIcon = { Icon(PokyhIcons.rectangle_portrait_and_arrow_right, contentDescription = null) },
                    onClick = { expanded = false; onSignOut() },
                )
            }
            DropdownMenuItem(
                text = { Text("Account entfernen", color = Brand.danger) },
                leadingIcon = { Icon(PokyhIcons.trash, contentDescription = null, tint = Brand.danger) },
                onClick = { expanded = false; onRemove() },
            )
        }
    }
}

@Composable
private fun AddAccountRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PokyhCard(modifier = modifier.clickable(onClick = onClick), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PokyhIcons.person_badge_plus, contentDescription = null, tint = Brand.accent)
            Text("Konto hinzufügen", style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun DestructiveActionCard(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false) {
    PokyhCard(modifier = modifier.clickable(enabled = !busy, onClick = onClick), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.danger)
            } else {
                Icon(icon, contentDescription = null, tint = Brand.danger)
            }
            Text(label, style = PokyhType.body, color = Brand.danger)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, style = PokyhType.body, color = PokyhTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
    }
}

@Composable
private fun LinkRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(18.dp))
        Text(label, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
    }
}

@Composable
private fun LogoutConfirmDialog(username: String, onDismiss: () -> Unit, onLogoutOnly: () -> Unit, onLogoutAndRemove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wirklich abmelden?") },
        text = { Text("Du kannst dich abmelden oder das gespeicherte Konto ganz vom Gerät entfernen.") },
        confirmButton = {
            TextButton(onClick = onLogoutOnly) { Text("Abmelden", color = Brand.danger) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLogoutAndRemove) { Text("Abmelden & löschen", color = Brand.danger) }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        },
    )
}
