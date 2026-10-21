@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.login

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
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.accentSurface
import kotlinx.coroutines.launch

/**
 * "Passwort vergessen", as on the web's sign-in page: username and the e-mail address stored in
 * WebUntis; WebUntis then sends a link to set a new password. POKYH never sees a password here.
 */
@Composable
fun PasswordResetSheet(viewModel: LoginViewModel, initialUsername: String, onDismiss: () -> Unit) {
    val state by viewModel.reset.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf(initialUsername.trim()) }
    var email by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    LaunchedEffect(Unit) { viewModel.resetResetState() }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        containerColor = PokyhTheme.colors.card,
        shape = PokyhShapes.topXxl,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PokyhIconButton(
                    icon = PokyhIcons.close,
                    contentDescription = "Schließen",
                    onClick = ::dismiss,
                    tinted = true,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Text(
                    "Passwort vergessen",
                    style = PokyhType.headline,
                    color = PokyhTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f).padding(horizontal = PokyhSpacing.md),
                )
            }
            if (state.sent) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    Box(Modifier.size(64.dp).accentSurface(PokyhShapes.xl), contentAlignment = Alignment.Center) {
                        Icon(PokyhIcons.check, null, tint = Brand.success, modifier = Modifier.size(30.dp))
                    }
                    Text("E-Mail ist unterwegs.", style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                    Text(
                        "WebUntis schickt dir einen Link, mit dem du ein neues Passwort setzt. Schau auch im Spam-Ordner nach.",
                        style = PokyhType.footnote,
                        color = PokyhTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    PokyhPrimaryButton(text = "Fertig", onClick = ::dismiss, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Text(
                    "Gib deinen WebUntis-Benutzernamen und die E-Mail-Adresse ein, die in WebUntis hinterlegt ist. " +
                        "WebUntis schickt dir dann einen Link für ein neues Passwort.",
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.textSecondary,
                )
                PokyhTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Benutzername",
                    leadingIcon = PokyhIcons.person,
                    enabled = !state.sending,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth(),
                )
                PokyhTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "E-Mail-Adresse",
                    leadingIcon = PokyhIcons.messages,
                    enabled = !state.sending,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                        keyboardType = KeyboardType.Email,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { Text(it, style = PokyhType.footnote, color = Brand.danger) }
                PokyhPrimaryButton(
                    text = "Link anfordern",
                    icon = PokyhIcons.sendMessage,
                    loading = state.sending,
                    enabled = username.isNotBlank() && email.isNotBlank(),
                    onClick = { viewModel.requestPasswordReset(username, email) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
