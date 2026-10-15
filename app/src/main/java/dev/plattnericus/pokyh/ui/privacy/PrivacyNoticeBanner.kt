package dev.plattnericus.pokyh.ui.privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.state.PrivacyNoticePrompt
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import kotlinx.coroutines.launch

private const val PRIVACY_URL = "https://pokyh.com/legal?view=datenschutz"

/**
 * The privacy notice as a banner at the top, where the offline notice sits — PrivacyNotice.swift's
 * banner, the website's PrivacyGate: once after an account's first sign-in, and once as
 * "aktualisiert" when it changed, without signing in again. It informs and blocks nothing; it stays
 * until "Verstanden", which the backend records (see [AppState.checkPrivacyNotice]).
 */
@Composable
fun PrivacyNoticeHost(appState: AppState, modifier: Modifier = Modifier) {
    val notice by appState.privacyNotice.collectAsStateWithLifecycle()
    val session by appState.session.collectAsStateWithLifecycle()
    val current = notice?.takeIf { it.username == session?.username }
    // The last notice stays on screen while the banner slides away (a plain holder, not
    // state: it only remembers, it never triggers anything).
    val last = remember { arrayOfNulls<PrivacyNoticePrompt>(1) }
    if (current != null) last[0] = current
    AnimatedVisibility(
        visible = current != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        last[0]?.let { PrivacyNoticeBanner(it, onAcknowledge = { appState.acknowledgePrivacyNotice() }) }
    }
}

@Composable
private fun PrivacyNoticeBanner(notice: PrivacyNoticePrompt, onAcknowledge: suspend () -> Boolean) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var saving by remember(notice) { mutableStateOf(false) }
    var failed by remember(notice) { mutableStateOf(false) }

    val title = when (notice.kind) {
        PrivacyNoticePrompt.Kind.First -> "Datenschutzerklärung"
        PrivacyNoticePrompt.Kind.Updated -> "Datenschutzerklärung aktualisiert"
    }
    // Kept short; what the notice says is behind the link.
    val message = when (notice.kind) {
        PrivacyNoticePrompt.Kind.First -> "Wie POKYH mit deinen Daten umgeht, steht in der Datenschutzerklärung."
        PrivacyNoticePrompt.Kind.Updated -> "Wir haben unsere Datenschutzerklärung aktualisiert."
    }

    Column(
        modifier = Modifier
            .padding(horizontal = PokyhSpacing.md)
            .fillMaxWidth()
            .shadow(10.dp, PokyhShapes.xl)
            .background(PokyhTheme.colors.card, PokyhShapes.xl)
            .border(1.dp, PokyhTheme.colors.border, PokyhShapes.xl)
            .padding(PokyhSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Brand.accent.copy(alpha = 0.14f), PokyhShapes.sm),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(17.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
                Text(title, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                Text(message, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
                Text(
                    text = "Datenschutzerklärung lesen",
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.accentText,
                    modifier = Modifier.clickable { uriHandler.openUri(PRIVACY_URL) },
                )
                if (failed) {
                    Text("Das hat nicht geklappt. Bitte versuche es noch einmal.", style = PokyhType.footnote, color = Brand.danger)
                }
            }
        }
        PokyhPrimaryButton(
            text = "Verstanden",
            loading = saving,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    saving = true
                    failed = !onAcknowledge()
                    saving = false
                }
            },
        )
    }
}
