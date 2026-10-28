package dev.plattnericus.pokyh.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.model.School
import dev.plattnericus.pokyh.data.untis.AppKey
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

/** Microsoft's four squares, the mark of every "sign in with Microsoft" button. */
@Composable
internal fun MicrosoftMark(size: Int = 18) {
    val cell = (size / 2 - 1).dp
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(cell).background(Color(0xFFF25022)))
            Box(Modifier.size(cell).background(Color(0xFF7FBA00)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(cell).background(Color(0xFF00A4EF)))
            Box(Modifier.size(cell).background(Color(0xFFFFB900)))
        }
    }
}

/** "Mit Office 365 anmelden", first, as on the school's own WebUntis page. */
@Composable
internal fun OfficeButton(onClick: () -> Unit, showOr: Boolean) {
    val t = strings().login
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
        Row(
            Modifier.fillMaxWidth().clip(PokyhShapes.lg).background(PokyhTheme.colors.card).clickable(onClick = onClick).padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicrosoftMark()
            Text(t.office, style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = PokyhTheme.colors.textPrimary)
        }
        if (showOr) Text(t.or, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/**
 * Office 365 schools: WebUntis signs their people in through Microsoft, which only WebUntis
 * itself can complete. POKYH signs in with the app key WebUntis shows each account instead: the
 * steps to it, with links into the school's own WebUntis, then user and key (web OfficeSignIn).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfficeSignInSheet(school: School, initialUser: String, busy: Boolean, error: String?, onSubmit: (user: String, key: String) -> Unit, onDismiss: () -> Unit) {
    val t = strings().login
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var user by remember { mutableStateOf(initialUser) }
    var key by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val base = "https://${school.server}/WebUntis/?school=${school.query}"
    // WebUntis' table copied whole (or the QR code's link) fills both fields.
    fun takePaste(text: String): Boolean {
        val (u, k) = AppKey.parse(text)
        if (k == null) return false
        key = k
        if (u != null) user = u
        return true
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PokyhTheme.colors.card, shape = PokyhShapes.topXxl) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = PokyhSpacing.screenH).padding(bottom = PokyhSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MicrosoftMark(22)
                Text(t.office, style = PokyhType.title2, color = PokyhTheme.colors.textPrimary)
            }
            Text(t.officeLead, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
            Step(1, t.officeStep1) { PokyhTextButton(text = t.officeOpenLogin + " ↗", onClick = { uri.openUri("$base#/basic/login") }) }
            Step(2, t.officeStep2) {
                PokyhTextButton(text = t.officeOpenProfile + " ↗", onClick = { uri.openUri("$base#/basic/profile") })
                Text(t.officeAdmin, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
            }
            Step(3, t.officeStep3) {
                PokyhTextButton(text = t.officePaste, onClick = {
                    clipboard.getText()?.text?.let { if (!takePaste(it)) problem = t.errKeyInvalid }
                })
            }
            PokyhTextField(
                value = user,
                onValueChange = { v -> if (v.length > 30 && takePaste(v)) Unit else user = v.take(100) },
                label = t.officeUser,
                leadingIcon = PokyhIcons.person,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth(),
            )
            PokyhTextField(
                value = key,
                onValueChange = { v -> if (!takePaste(v)) key = v.take(200) },
                label = t.officeKey,
                leadingIcon = PokyhIcons.password,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(t.officeKeyHint, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
            (problem ?: error)?.let { Text(it, style = PokyhType.footnote, color = Brand.danger) }
            PokyhPrimaryButton(
                text = if (busy) t.signingIn else t.signIn,
                loading = busy,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    problem = null
                    if (user.isBlank() || key.isBlank()) { problem = t.errKeyMissing; return@PokyhPrimaryButton }
                    val k = AppKey.normalise(key)
                    if (k.isEmpty()) { problem = t.errKeyInvalid; return@PokyhPrimaryButton }
                    onSubmit(user.trim(), k)
                },
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String, extra: @Composable () -> Unit = {}) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).background(PokyhTheme.colors.nested, PokyhShapes.pill), contentAlignment = Alignment.Center) {
            Text("$n", style = PokyhType.caption.copy(fontWeight = FontWeight.Bold), color = PokyhTheme.colors.textSecondary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, style = PokyhType.callout, color = PokyhTheme.colors.textPrimary)
            extra()
        }
    }
}
