package dev.plattnericus.pokyh.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Stable
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.SecurityRow
import dev.plattnericus.pokyh.ui.components.SettingsItem
import dev.plattnericus.pokyh.ui.components.SettingsRule
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.components.StatusPill
import dev.plattnericus.pokyh.ui.components.WebButton
import dev.plattnericus.pokyh.ui.components.WebButtonVariant
import dev.plattnericus.pokyh.ui.components.WebSwitch
import dev.plattnericus.pokyh.ui.pixel.PixelIconName
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import kotlinx.coroutines.CoroutineScope
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import kotlinx.coroutines.launch

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
internal interface UntisAccountEntryPoint {
    fun untisClient(): UntisClient
}

/** The WebUntis account's own settings on the settings page: loading, could not be read, or there. */
internal sealed interface UntisAccountLoad {
    data object Loading : UntisAccountLoad
    data object Failed : UntisAccountLoad
    data class Ready(val account: UntisClient.UntisAccount) : UntisAccountLoad
}

/**
 * The WebUntis account as the web's settings read it (fetchUntisAccount): the e-mail address,
 * forwarding, the language in WebUntis, the two-factor sign-in and whether the school lets the
 * password be changed here. The rows of Konto & Sicherheit read it and save through it.
 */
@Stable
internal class UntisAccountHolder(private val appState: AppState, val untis: UntisClient, private val scope: CoroutineScope) {
    var load by mutableStateOf<UntisAccountLoad>(UntisAccountLoad.Loading)
        private set
    /** Which setting is being saved ("forward", "language"), so the others wait. */
    var saving by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val account: UntisClient.UntisAccount? get() = (load as? UntisAccountLoad.Ready)?.account

    fun reload() {
        load = UntisAccountLoad.Loading
        scope.launch {
            val s = appState.session.value ?: run { load = UntisAccountLoad.Failed; return@launch }
            load = runCatching { untis.untisAccount(s) }.fold({ UntisAccountLoad.Ready(it) }, { UntisAccountLoad.Failed })
        }
    }

    fun update(change: (UntisClient.UntisAccount) -> UntisClient.UntisAccount) {
        account?.let { load = UntisAccountLoad.Ready(change(it)) }
    }

    /** Saves one setting; on a failure the row goes back and the reason shows under the group. */
    fun save(key: String, optimistic: (UntisClient.UntisAccount) -> UntisClient.UntisAccount, call: suspend (UserSession) -> Unit) {
        val before = account ?: return
        saving = key
        error = null
        update(optimistic)
        scope.launch {
            val s = appState.session.value
            val ok = s != null && runCatching { call(s) }
                .onFailure { e -> error = e.message?.takeIf { it.isNotBlank() && it != "session_expired" } ?: I18n.s.settings.saveError }
                .isSuccess
            if (!ok) load = UntisAccountLoad.Ready(before)
            saving = null
        }
    }
}

@Composable
internal fun rememberUntisAccount(appState: AppState, enabled: Boolean): UntisAccountHolder {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember {
        val untis = dagger.hilt.android.EntryPointAccessors.fromApplication(context.applicationContext, UntisAccountEntryPoint::class.java).untisClient()
        UntisAccountHolder(appState, untis, scope)
    }
    LaunchedEffect(enabled) { if (enabled) holder.reload() }
    return holder
}

/**
 * The e-mail address, the first row of Konto & Sicherheit: a placeholder while it loads, a way
 * to try again when it could not, else the address (or "Fehlt", outlined) and the way to change it.
 */
@Composable
internal fun UntisEmailRow(holder: UntisAccountHolder, appState: AppState) {
    val t = strings().account
    val saveError = strings().settings.saveError
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    when (val l = holder.load) {
        UntisAccountLoad.Loading -> Column(Modifier.fillMaxWidth()) {
            SettingsRule()
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SkeletonBlock(height = 48.dp, width = 48.dp, shape = RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonBlock(height = 16.dp, width = 128.dp)
                    SkeletonBlock(height = 14.dp, modifier = Modifier.fillMaxWidth(0.6f))
                }
            }
        }
        UntisAccountLoad.Failed -> SecurityRow(
            icon = PixelIconName.Mail,
            title = t.email,
            hint = t.accountError,
            actions = { WebButton(t.retry, onClick = holder::reload, variant = WebButtonVariant.Quiet) },
        )
        is UntisAccountLoad.Ready -> {
            val mail = l.account.email
            val missing = PokyhDecorative.blush(PokyhTheme.colors.isDark)
            SecurityRow(
                icon = PixelIconName.Mail,
                title = t.email,
                value = mail.ifEmpty { null },
                hint = if (mail.isNotEmpty()) t.emailCardHint else t.emailNone,
                highlight = mail.isEmpty(),
                status = if (mail.isEmpty()) { { StatusPill(t.missing, missing) } } else null,
                actions = {
                    WebButton(
                        if (mail.isNotEmpty()) t.emailChange else t.emailAdd,
                        onClick = { editing = true },
                        variant = if (mail.isNotEmpty()) WebButtonVariant.Tone else WebButtonVariant.Primary,
                        icon = PokyhIcons.edit,
                    )
                },
            )
        }
    }
    val a = holder.account
    if (editing && a != null) {
        EmailSheet(
            current = a.email,
            onDismiss = { editing = false },
            onSave = { mail, done ->
                scope.launch {
                    val s = appState.session.value ?: return@launch
                    runCatching { holder.untis.saveUntisAccount(s, email = mail) }
                        .onSuccess { holder.update { it.copyWith(email = mail.trim()) }; done(null) }
                        .onFailure { done(it.message ?: saveError) }
                }
            },
        )
    }
}

/** The WebUntis password: dots, and changing it (where the school lets it) or resetting it. */
@Composable
internal fun UntisPasswordRow(holder: UntisAccountHolder, onChange: () -> Unit, onForgot: () -> Unit) {
    val t = strings().account
    val changeAllowed = holder.account?.passwordChangeAllowed ?: true
    SecurityRow(
        icon = PixelIconName.Key,
        title = t.passwordTitle,
        value = "••••••••",
        hint = t.passwordCardHint,
        actions = {
            if (changeAllowed) WebButton(t.passwordChange, onClick = onChange, variant = WebButtonVariant.Tone, icon = PokyhIcons.edit)
            WebButton(t.passwordForgot, onClick = onForgot, variant = WebButtonVariant.Quiet)
        },
    )
}

/** Two-factor sign-in, where WebUntis offers it: on (a green shield) or off (gold, outlined, "Empfohlen"). */
@Composable
internal fun UntisTwoFactorRow(holder: UntisAccountHolder, appState: AppState) {
    val t = strings().account
    val ts = strings().settings
    val a = holder.account ?: return
    if (!a.twoFactorAvailable && !a.twoFactorEnabled) return
    var mode by remember { mutableStateOf<String?>(null) } // "setup" | "disable"
    val dark = PokyhTheme.colors.isDark
    val actions: (@Composable RowScope.() -> Unit)? = when {
        !a.twoFactorEnabled -> { { WebButton(ts.acctTwoFactorSetup, onClick = { mode = "setup" }, icon = PokyhIcons.security) } }
        !a.twoFactorRequired -> { { WebButton(ts.tfDisableShort, onClick = { mode = "disable" }, variant = WebButtonVariant.Quiet) } }
        else -> null
    }
    SecurityRow(
        icon = if (a.twoFactorEnabled) PixelIconName.ShieldOn else PixelIconName.ShieldOff,
        title = ts.acctTwoFactor,
        value = if (a.twoFactorEnabled) null else t.stateOff,
        status = {
            if (a.twoFactorEnabled) StatusPill(t.stateOn, PokyhDecorative.sage(dark), icon = PokyhIcons.check)
            else StatusPill(t.recommended, PokyhDecorative.butter(dark))
        },
        hint = when {
            !a.twoFactorEnabled -> ts.acctTwoFactorCardOff
            a.twoFactorRequired -> ts.acctTwoFactorRequired
            else -> ts.acctTwoFactorCardOn
        },
        highlight = !a.twoFactorEnabled,
        actions = actions,
    )
    mode?.let { m ->
        TwoFactorSheet(
            disable = m == "disable",
            appState = appState,
            untis = holder.untis,
            onDismiss = { mode = null },
            onChanged = { on -> holder.update { it.copyWith(twoFactor = on) } },
        )
    }
}

/** Below the security rows: what failed to save, messages by e-mail too, and WebUntis' own language. */
@Composable
internal fun UntisAccountItems(holder: UntisAccountHolder) {
    val t = strings().settings
    holder.error?.let {
        Text(it, style = PokyhType.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = PokyhTheme.colors.dangerText, modifier = Modifier.padding(vertical = 12.dp))
    }
    val a = holder.account ?: return
    SettingsItem(
        title = t.acctForward,
        hint = if (a.email.isNotEmpty()) t.acctForwardHint else t.acctForwardNeedsEmail,
        control = {
            WebSwitch(
                checked = a.forwardToEmail,
                enabled = a.email.isNotEmpty() && holder.saving == null,
                onCheckedChange = { on -> holder.save("forward", { it.copyWith(forward = on) }) { s -> holder.untis.saveUntisAccount(s, forwardToEmail = on) } },
            )
        },
    )
    if (a.languages.isNotEmpty()) {
        var open by remember { mutableStateOf(false) }
        SettingsItem(
            title = t.untisLanguage,
            hint = t.untisLanguageHint,
            control = {
                Box {
                    WebButton(
                        a.languages.firstOrNull { it.first == a.languageCode }?.second ?: a.languageCode,
                        onClick = { open = true },
                        variant = WebButtonVariant.Quiet,
                        trailingIcon = PokyhIcons.expandMenu,
                        enabled = holder.saving == null,
                    )
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        a.languages.forEach { (key, name) ->
                            DropdownMenuItem(
                                text = { Text(name, style = PokyhType.body) },
                                trailingIcon = {
                                    if (key == a.languageCode) Icon(PokyhIcons.check, contentDescription = null, tint = PokyhTheme.colors.accentText, modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    open = false
                                    holder.save("language", { it.copyWith(language = key) }) { s -> holder.untis.saveUntisAccount(s, languageCode = key) }
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}

private fun UntisClient.UntisAccount.copyWith(email: String = this.email, forward: Boolean = forwardToEmail, twoFactor: Boolean = twoFactorEnabled, language: String = languageCode) =
    UntisClient.UntisAccount(email, forward, passwordChangeAllowed, twoFactorAvailable, twoFactor, twoFactorRequired, language, languages)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailSheet(current: String, onDismiss: () -> Unit, onSave: (String, (String?) -> Unit) -> Unit) {
    val t = strings().settings
    var mail by remember { mutableStateOf(current) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PokyhTheme.colors.card, shape = PokyhShapes.topXxl) {
        Column(Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.screenH).padding(bottom = PokyhSpacing.xxl), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg)) {
            Text(t.acctEmail, style = PokyhType.title2, color = PokyhTheme.colors.textPrimary)
            Text(t.acctEmailLead, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
            PokyhTextField(
                value = mail,
                onValueChange = { mail = it; message = null; saved = false },
                label = t.acctEmail,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let { Text(it, style = PokyhType.footnote, color = Brand.danger) }
            if (saved) Text(t.acctEmailSaved, style = PokyhType.footnote, color = Brand.success)
            PokyhPrimaryButton(
                text = if (busy) t.acctEmailSaving else t.acctEmailSave,
                loading = busy,
                enabled = !busy && mail.trim() != current,
                onClick = {
                    val m = mail.trim()
                    if (m.isNotEmpty() && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(m)) {
                        message = t.acctEmailInvalid
                        return@PokyhPrimaryButton
                    }
                    busy = true
                    onSave(m) { err -> busy = false; message = err; saved = err == null }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Two-factor sign-in, as WebUntis' own wizard (web components/account/TwoFactorSheet): a short
 * explanation, then the new secret — to open in the authenticator app on this phone, as a key to
 * type, or as a QR code for another device — then the app's first code turns it on. The secret
 * lives only in this sheet while it is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoFactorSheet(disable: Boolean, appState: AppState, untis: UntisClient, onDismiss: () -> Unit, onChanged: (Boolean) -> Unit) {
    val t = strings().settings
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(if (disable) "disable" else "intro") }
    var setup by remember { mutableStateOf<UntisClient.TwoFactorSetup?>(null) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PokyhTheme.colors.card, shape = PokyhShapes.topXxl) {
        Column(Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.screenH).padding(bottom = PokyhSpacing.xxl), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg)) {
            Text(if (step == "disable") t.tfDisableTitle else if (step == "done") t.tfDoneTitle else t.tfTitle, style = PokyhType.title2, color = PokyhTheme.colors.textPrimary)
            when (step) {
                "intro" -> {
                    Text(t.tfIntro, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                    Text(t.tfApps, style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary)
                    Text(t.tfRenewNote, style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary)
                    PokyhPrimaryButton(text = t.tfStart, loading = busy, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val s = appState.session.value ?: return@launch
                            runCatching { untis.startTwoFactorSetup(s) }
                                .onSuccess { setup = it; step = "scan" }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    })
                }
                "scan" -> {
                    val sec = setup ?: return@Column
                    // On this phone the QR can't be scanned: the app opens the link itself.
                    if (sec.link.isNotEmpty()) {
                        PokyhSecondaryButton(text = t.tfOpenApp, icon = PokyhIcons.security, modifier = Modifier.fillMaxWidth(), onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sec.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                .onFailure { error = t.tfNoApp }
                        })
                        val qr = remember(sec.link) { qrBitmap(sec.link) }
                        if (qr != null) {
                            Text(t.tfScan, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Image(qr.asImageBitmap(), contentDescription = null, modifier = Modifier.size(180.dp).background(androidx.compose.ui.graphics.Color.White, PokyhShapes.md).padding(8.dp))
                            }
                        }
                    }
                    Text(t.tfManual, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                    val grouped = sec.secret.trimEnd('=').chunked(4).joinToString(" ")
                    Column(Modifier.fillMaxWidth().background(PokyhTheme.colors.nested, PokyhShapes.lg).padding(PokyhSpacing.card)) {
                        Text(grouped, style = PokyhType.callout.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold), color = PokyhTheme.colors.textPrimary)
                        PokyhTextButton(text = if (copied) t.tfCopied else t.tfCopy, onClick = { clipboard.setText(AnnotatedString(sec.secret)); copied = true })
                    }
                    PokyhTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter { it.isDigit() }.take(6); error = null },
                        label = t.tfCode,
                        placeholder = "123456",
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(t.tfCodeHint, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
                    PokyhPrimaryButton(text = if (busy) t.tfEnabling else t.tfEnable, loading = busy, enabled = !busy && code.length == 6, modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true
                        scope.launch {
                            val s = appState.session.value ?: return@launch
                            runCatching { untis.enableTwoFactor(s, sec.secret, code) }
                                .onSuccess { setup = null; step = "done"; onChanged(true) }
                                .onFailure { code = ""; error = it.message ?: t.tfCodeFormat }
                            busy = false
                        }
                    })
                }
                "done" -> {
                    Text(t.tfDone, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                    Text(t.tfDoneNote, style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold), color = Brand.warning)
                    PokyhPrimaryButton(text = strings().common.close, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    Text(t.tfDisableText, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                    PokyhPrimaryButton(text = if (busy) t.tfDisabling else t.tfDisableShort, loading = busy, enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                        busy = true
                        scope.launch {
                            val s = appState.session.value ?: return@launch
                            runCatching { untis.disableTwoFactor(s) }
                                .onSuccess { onChanged(false); onDismiss() }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    })
                    PokyhTextButton(text = strings().common.cancel, color = PokyhTheme.colors.textSecondary, onClick = onDismiss)
                }
            }
            error?.let { Text(it, style = PokyhType.footnote, color = Brand.danger) }
        }
    }
}

/** The otpauth link as a QR code (zxing, already in the app for the web sign-in scanner). */
private fun qrBitmap(text: String): android.graphics.Bitmap? = runCatching {
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 360, 360)
    val bmp = android.graphics.Bitmap.createBitmap(matrix.width, matrix.height, android.graphics.Bitmap.Config.RGB_565)
    for (x in 0 until matrix.width) for (y in 0 until matrix.height) bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    bmp
}.getOrNull()
