package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.fadeIn

/**
 * Every "there is nothing here" screen, built from one layout so they're interchangeable: a
 * tinted glyph disc, a title, a line of explanation, and at most one action.
 *
 * The disc is the empty state's whole visual identity — it reuses [IconTile]'s "semantic color
 * at low alpha" idea at hero size, so an empty list looks like the same product as the rows
 * that would have been in it. No decorative illustration: an empty state's job is to explain,
 * and a flourish that only appears when something is missing reads as an apology.
 */
@Composable
private fun StateLayout(
    icon: ImageVector,
    tint: Color,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PokyhSpacing.xxxl)
            .fadeIn(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(tint.copy(alpha = 0.12f), PokyhShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.size(PokyhSpacing.xl))
        Text(
            text = title,
            style = PokyhType.title2,
            color = PokyhTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.size(PokyhSpacing.sm))
            Text(
                text = message,
                style = PokyhType.callout,
                color = PokyhTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.size(PokyhSpacing.xxl))
            action()
        }
    }
}

/** Nothing to show, and that's fine. */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    StateLayout(
        icon = icon,
        tint = Brand.accent,
        title = title,
        message = subtitle,
        modifier = modifier,
        action = action,
    )
}

/** Something went wrong and retrying is the answer. */
@Composable
fun ErrorStateView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    StateLayout(
        icon = PokyhIcons.warningSign,
        tint = Brand.warning,
        title = "Da ist etwas schiefgelaufen",
        message = message,
        modifier = modifier,
        action = onRetry?.let {
            {
                PokyhSecondaryButton(
                    text = "Erneut versuchen",
                    onClick = it,
                    icon = PokyhIcons.refresh,
                )
            }
        },
    )
}

/** First load, with no cached content to show underneath. */
@Composable
fun LoadingStateView(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Brand.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        if (label != null) {
            Spacer(Modifier.size(PokyhSpacing.lg))
            Text(label, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
        }
    }
}

/**
 * Why a POKYH-backend feature (Todos, Erinnerungen, Klasse) isn't available for the current
 * [status] — specific per status rather than one generic message, because each of these has a
 * different fix and telling the user which one they're in is the whole point.
 */
@Composable
fun BackendUnavailableView(feature: String, status: BackendStatus, modifier: Modifier = Modifier) {
    val (icon, tint) = when (status) {
        is BackendStatus.NotStudent -> PokyhIcons.notAStudent to Brand.warning
        is BackendStatus.NoClass -> PokyhIcons.noClass to Brand.warning
        is BackendStatus.Failed -> PokyhIcons.serverUnreachable to Brand.danger
        else -> PokyhIcons.locked to Brand.warning
    }
    val title = when (status) {
        is BackendStatus.NotStudent -> "Nur für Schülerkonten"
        is BackendStatus.NoClass -> "Keine Klasse gefunden"
        is BackendStatus.Failed -> "Verbindungsfehler"
        else -> "Nicht verfügbar"
    }
    val message = when (status) {
        is BackendStatus.NotStudent -> "$feature ist nur mit einem Schülerkonto verfügbar."
        is BackendStatus.NoClass ->
            "Für $feature wird deine WebUntis-Klasse benötigt — sie konnte für dein Konto nicht ermittelt werden."
        is BackendStatus.Failed -> "$feature konnte nicht geladen werden:\n${status.message}"
        else -> "$feature benötigt ein POKYH-Konto."
    }

    StateLayout(
        icon = icon,
        tint = tint,
        title = title,
        message = message,
        modifier = modifier,
        action = if (status is BackendStatus.NoClass) {
            {
                Box(
                    modifier = Modifier
                        .background(PokyhTheme.colors.accentTint, PokyhShapes.pill)
                        .padding(horizontal = PokyhSpacing.lg, vertical = PokyhSpacing.sm),
                ) {
                    Text(
                        text = "Profil → POKYH-Konto öffnen",
                        style = PokyhType.caption,
                        color = PokyhTheme.colors.accentText,
                    )
                }
            }
        } else {
            null
        },
    )
}

/**
 * A slim inline notice inside a scrolling page — for a fact worth stating in place rather than
 * a state that replaces the screen ("Heute kein Unterricht"). A tinted [IconTile] plus one line,
 * on the standard row surface, so it sits in the same rhythm as the rows above and below it.
 */
@Composable
fun PokyhInlineNotice(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Brand.accent,
) {
    PokyhTileRow(modifier = modifier) {
        IconTile(icon = icon, color = tint, size = 38.dp)
        Text(
            text = text,
            style = PokyhType.callout,
            color = PokyhTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
