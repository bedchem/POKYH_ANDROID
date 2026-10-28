package dev.plattnericus.pokyh.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val appState: AppState,
    private val backendClient: BackendClient,
) : ViewModel() {
    data class UiState(
        val devices: List<BackendClient.DeviceSession>? = null,
        val logins: List<BackendClient.LoginEntry>? = null,
        val keptDays: Int = 0,
        val failed: Boolean = false,
        val busy: String? = null,
        val actionError: Boolean = false,
        val othersDone: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(failed = false) }
        viewModelScope.launch {
            runCatching { appState.withFreshApiToken { backendClient.deviceSessions(it) } }
                .onSuccess { list -> _ui.update { it.copy(devices = list) } }
                .onFailure { _ui.update { it.copy(failed = true) } }
            runCatching { appState.withFreshApiToken { backendClient.loginHistory(it) } }
                .onSuccess { h -> _ui.update { it.copy(logins = h.logins, keptDays = h.keptDays) } }
        }
    }

    fun signOut(device: BackendClient.DeviceSession) {
        _ui.update { it.copy(busy = device.id, actionError = false) }
        viewModelScope.launch {
            runCatching { appState.withFreshApiToken { backendClient.signOutDevice(device.id, it) } }
                .onSuccess { _ui.update { s -> s.copy(busy = null, devices = s.devices?.filterNot { it.id == device.id }) } }
                .onFailure { _ui.update { it.copy(busy = null, actionError = true) } }
        }
    }

    fun signOutOthers() {
        _ui.update { it.copy(busy = "others", actionError = false) }
        viewModelScope.launch {
            runCatching { appState.withFreshApiToken { backendClient.signOutOtherDevices(it) } }
                .onSuccess { _ui.update { s -> s.copy(busy = null, othersDone = true, devices = s.devices?.filter { it.current }) } }
                .onFailure { _ui.update { it.copy(busy = null, actionError = true) } }
        }
    }
}

/** "Geräte & Anmeldungen", as the web's settings/devices: where the account is signed in, and when. */
@Composable
fun DevicesScreen(onNavigateBack: () -> Unit, viewModel: DevicesViewModel = hiltViewModel()) {
    val t = strings().settings
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmOthers by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(10) }
    val current = ui.devices?.filter { it.current }.orEmpty()
    val others = ui.devices?.filterNot { it.current }.orEmpty()

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = { PokyhTopBar(title = t.devicesTitle, nav = TopBarNav.Back(onNavigateBack)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = PokyhSpacing.screenH, end = PokyhSpacing.screenH, bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
        ) {
            item(key = "lead") { Text(t.devicesLead, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary) }
            if (ui.failed) {
                item(key = "failed") {
                    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                        Text(t.devicesLoadError, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                        PokyhSecondaryButton(text = strings().common.retry, icon = PokyhIcons.refresh, onClick = viewModel::load)
                    }
                }
                return@LazyColumn
            }
            if (ui.actionError) item(key = "err") { Text(t.devicesFailed, style = PokyhType.footnote, color = Brand.danger) }
            item(key = "this") {
                PokyhSection(title = t.thisDevice, modifier = Modifier.fadeIn()) {
                    if (ui.devices == null) ListSkeleton() else PokyhCard(padding = 0.dp) {
                        current.forEachIndexed { i, d ->
                            if (i > 0) PokyhRowSeparator()
                            DeviceRow(d, busy = false, onSignOut = null)
                        }
                    }
                }
            }
            item(key = "others") {
                PokyhSection(
                    title = t.otherDevices,
                    trailing = if (others.size > 1) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (confirmOthers) PokyhTextButton(text = strings().common.cancel, color = PokyhTheme.colors.textSecondary, onClick = { confirmOthers = false })
                                PokyhTextButton(
                                    text = if (confirmOthers) t.signOutOthersConfirm else t.signOutOthers,
                                    color = Brand.danger,
                                    onClick = { if (confirmOthers) { confirmOthers = false; viewModel.signOutOthers() } else confirmOthers = true },
                                    enabled = ui.busy != "others",
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.fadeIn(30),
                ) {
                    when {
                        ui.devices == null -> ListSkeleton()
                        others.isEmpty() -> Text(if (ui.othersDone) t.signedOutOthers else t.noOthers, style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary)
                        else -> PokyhCard(padding = 0.dp) {
                            others.forEachIndexed { i, d ->
                                if (i > 0) PokyhRowSeparator()
                                DeviceRow(d, busy = ui.busy == d.id, onSignOut = { viewModel.signOut(d) })
                            }
                        }
                    }
                }
            }
            item(key = "history") {
                PokyhSection(title = t.history, modifier = Modifier.fadeIn(60)) {
                    val logins = ui.logins
                    when {
                        logins == null -> ListSkeleton()
                        logins.isEmpty() -> Text(t.historyEmpty, style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                            if (ui.keptDays > 0) Text(t.historyHint(ui.keptDays), style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary)
                            PokyhCard(padding = 0.dp) {
                                logins.take(shown).forEachIndexed { i, l ->
                                    if (i > 0) PokyhRowSeparator()
                                    LoginRow(l)
                                }
                            }
                            if (logins.size > shown) PokyhTextButton(text = t.showMore, onClick = { shown += 20 })
                        }
                    }
                }
            }
        }
    }
}

private fun deviceName(model: String, os: String, browser: String): String =
    listOf(model, browser, os).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(2).joinToString(" · ")
        .ifEmpty { I18n.s.settings.unknownDevice }

private fun placeOf(city: String?, region: String?, country: String): String =
    listOfNotNull(city?.takeIf { it.isNotBlank() }, region?.takeIf { it.isNotBlank() }, country.takeIf { it.isNotBlank() })
        .distinct().joinToString(", ").ifEmpty { I18n.s.settings.unknownPlace }

private fun whenText(iso: String): String = runCatching {
    val l = I18n.lang.value
    java.time.OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern(if (l == dev.plattnericus.pokyh.core.i18n.Lang.De) "d. MMM yyyy, HH:mm" else "d MMM yyyy, HH:mm", l.locale))
}.getOrDefault(iso.take(16).replace('T', ' '))

@Composable
private fun DeviceGlyph(client: String, platform: String) {
    val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.periwinkle(PokyhTheme.colors.isDark)
    Box(Modifier.size(40.dp).background(tone.fill, PokyhShapes.md), contentAlignment = Alignment.Center) {
        Icon(
            if (client == "app" || platform == "android" || platform == "ios") PokyhIcons.person else PokyhIcons.webSignIn,
            contentDescription = null,
            tint = tone.ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DeviceRow(d: BackendClient.DeviceSession, busy: Boolean, onSignOut: (() -> Unit)?) {
    val t = strings().settings
    val colors = PokyhTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceGlyph(d.client, d.platform)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(deviceName(d.model, d.os, d.browser), style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            Text(placeOf(d.city, d.region, d.country), style = PokyhType.footnote, color = colors.textSecondary)
            Text(t.lastActive(whenText(d.lastUsedAt)), style = PokyhType.caption, color = colors.textTertiary)
        }
        if (onSignOut != null) {
            PokyhTextButton(text = t.signOutShort, color = Brand.danger, onClick = onSignOut, enabled = !busy)
        }
    }
}

@Composable
private fun LoginRow(l: BackendClient.LoginEntry) {
    val colors = PokyhTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeviceGlyph(l.client, l.platform)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(deviceName(l.model, l.os, l.browser), style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            Text(placeOf(l.city, l.region, l.country), style = PokyhType.footnote, color = colors.textSecondary)
        }
        Text(whenText(l.at), style = PokyhType.caption, color = colors.textTertiary)
    }
}
