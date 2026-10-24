package dev.plattnericus.pokyh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

/**
 * The authenticator app's code for an account with two-factor sign-in
 * ([AppState.twoFactor]): asked for after the password, on any screen — the login, the lock
 * screen's unlock, or a relaunch, since such an account cannot sign itself in. Sending it signs
 * in again with the code ([AppState.submitTwoFactorCode]).
 */
@Composable
fun TwoFactorDialog(appState: AppState) {
    val request by appState.twoFactor.collectAsStateWithLifecycle()
    val current = request ?: return
    val s = I18n.s.login
    var code by rememberSaveable(current) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val digits = code.filter { it.isDigit() }
    val submit = { if (digits.length == 6) appState.submitTwoFactorCode(digits) }

    LaunchedEffect(current) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = { appState.cancelTwoFactor() },
        shape = PokyhShapes.xl,
        containerColor = PokyhTheme.colors.card,
        title = { Text(s.codeTitle, style = PokyhType.title3, color = PokyhTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                Text(s.codeHint, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                if (current.wrongCode) {
                    Text(s.codeWrong, style = PokyhType.callout, color = dev.plattnericus.pokyh.ui.theme.Brand.danger)
                }
                PokyhTextField(
                    value = code,
                    onValueChange = { next -> code = next.filter { it.isDigit() || it == ' ' }.take(7) },
                    placeholder = "123 456",
                    label = s.codeLabel,
                    focusRequester = focus,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
            }
        },
        confirmButton = {
            PokyhTextButton(text = s.codeConfirm, enabled = digits.length == 6, onClick = submit)
        },
        dismissButton = {
            PokyhTextButton(text = I18n.s.common.cancel, color = PokyhTheme.colors.textSecondary, onClick = { appState.cancelTwoFactor() })
        },
    )
}
