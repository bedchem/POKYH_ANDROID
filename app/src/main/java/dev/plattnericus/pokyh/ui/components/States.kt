package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.fadeIn

/** Port of `ErrorStateView`. */
@Composable
fun ErrorStateView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(30.dp).fadeIn(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(PokyhIcons.exclamationmark_triangle_fill, contentDescription = null, tint = Brand.orange, modifier = Modifier.size(38.dp))
        Text(message, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Brand.accent)) {
                Icon(PokyhIcons.arrow_clockwise, contentDescription = null, modifier = Modifier.size(18.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                Text("Erneut versuchen")
            }
        }
    }
}

/** Port of `EmptyStateView`. */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(40.dp).fadeIn(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, tint = Brand.accent.copy(alpha = 0.7f), modifier = Modifier.size(44.dp))
        Text(title, style = PokyhType.headline, color = PokyhTheme.colors.textSecondary)
        if (subtitle != null) {
            Text(subtitle, style = PokyhType.subheadline, color = PokyhTheme.colors.textTertiary, textAlign = TextAlign.Center)
        }
    }
}

/** Port of `BackendUnavailableView` — explains exactly why a POKYH-backend feature (Todos,
 * Erinnerungen, Klasse) isn't available for the current [status], instead of a generic message. */
@Composable
fun BackendUnavailableView(feature: String, status: BackendStatus, modifier: Modifier = Modifier) {
    val (icon, tint) = when (status) {
        is BackendStatus.NotStudent -> PokyhIcons.person_fill_xmark to Brand.orange
        is BackendStatus.NoClass -> PokyhIcons.person_2_slash to Brand.orange
        is BackendStatus.Failed -> PokyhIcons.wifi_exclamationmark to Brand.danger
        else -> PokyhIcons.lock to Brand.orange
    }
    val title = when (status) {
        is BackendStatus.NotStudent -> "Nur für Schülerkonten"
        is BackendStatus.NoClass -> "Keine Klasse gefunden"
        is BackendStatus.Failed -> "Verbindungsfehler"
        else -> "Nicht verfügbar"
    }
    val message = when (status) {
        is BackendStatus.NotStudent -> "$feature ist nur mit einem Schülerkonto verfügbar."
        is BackendStatus.NoClass -> "Für $feature wird deine WebUntis-Klasse benötigt — sie konnte für dein Konto nicht ermittelt werden."
        is BackendStatus.Failed -> "$feature konnte nicht geladen werden:\n${status.message}"
        else -> "$feature benötigt ein POKYH-Konto."
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp).fadeIn(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 360.dp),
        ) {
            Text(title, style = PokyhType.title3, color = PokyhTheme.colors.textPrimary, textAlign = TextAlign.Center)
            Text(message, style = PokyhType.subheadline, color = PokyhTheme.colors.textSecondary, textAlign = TextAlign.Center)
        }
        if (status is BackendStatus.NoClass) {
            Box(
                modifier = Modifier
                    .background(Brand.accent.copy(alpha = 0.12f), androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text("Profil → POKYH-Konto öffnen", style = PokyhType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = Brand.accent)
            }
        }
    }
}
