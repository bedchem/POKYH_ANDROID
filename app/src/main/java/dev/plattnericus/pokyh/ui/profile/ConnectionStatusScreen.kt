@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn

/**
 * ConnectionStatusView.swift, ported — a full-screen route (pushed from [ProfileScreen]'s
 * "POKYH-Konto" row) rather than iOS's sheet. Accessible for EVERY signed-in user (not just
 * ones with backend trouble) — it's the general "what does my POKYH account see" diagnosis.
 * Reuses [ProfileViewModel] for the session/status it already surfaces plus the diagnostics
 * plumbing that lives there (see that file's header comment for why).
 */
@Composable
fun ConnectionStatusScreen(
    onNavigateUp: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val loadingDiagnostics by viewModel.loadingDiagnostics.collectAsStateWithLifecycle()
    var detailsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadDiagnosticsIfNeeded() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Konto & Verbindung", style = PokyhType.headline, color = PokyhTheme.colors.textPrimary) },
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
            item(key = "hero") { HeroSection(backendStatus, Modifier.fadeIn()) }

            if (session != null) {
                item(key = "konto_title") { SectionLabel("Dein Konto") }
                item(key = "konto") { AccountInfoSection(session!!, Modifier.fadeIn(delayMillis = 40)) }
            }

            item(key = "details") {
                DetailsSection(
                    loading = loadingDiagnostics,
                    diagnostics = diagnostics,
                    expanded = detailsExpanded,
                    onToggleExpanded = { detailsExpanded = !detailsExpanded },
                    onReload = viewModel::reloadDiagnostics,
                    onShare = { text ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                    modifier = Modifier.fadeIn(delayMillis = 80),
                )
            }
        }
    }
}

// ── Hero ─────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(status: BackendStatus, modifier: Modifier = Modifier) {
    val tint = statusTint(status)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(76.dp).background(tint.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(statusIcon(status), contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Text(status.uiLabel(), style = PokyhType.title3, color = PokyhTheme.colors.textPrimary)
        Text(
            statusExplanation(status),
            style = PokyhType.subheadline,
            color = PokyhTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun statusTint(status: BackendStatus): Color = when (status) {
    is BackendStatus.Ok -> Brand.success
    is BackendStatus.Failed -> Brand.danger
    else -> Brand.orange
}

private fun statusIcon(status: BackendStatus): ImageVector = when (status) {
    is BackendStatus.Ok -> PokyhIcons.checkmark_seal_fill
    is BackendStatus.NotStudent -> PokyhIcons.person_fill_xmark
    is BackendStatus.NoClass -> PokyhIcons.person_2_slash
    is BackendStatus.Failed -> PokyhIcons.wifi_exclamationmark
    is BackendStatus.Unknown -> PokyhIcons.questionmark_circle
}

private fun statusExplanation(status: BackendStatus): String = when (status) {
    is BackendStatus.Ok -> "Dein POKYH-Konto ist aktiv. Todos, Erinnerungen und Klasse stehen zur Verfügung."
    is BackendStatus.NotStudent -> "POKYH-Funktionen sind nur mit einem Schülerkonto verfügbar."
    is BackendStatus.NoClass -> "Deine WebUntis-Klasse konnte nicht ermittelt werden — dadurch sind Todos & Erinnerungen gesperrt. Tippe auf Erneut prüfen; hilft das nicht, sende die technischen Details an den Support."
    is BackendStatus.Failed -> "Verbindung zum POKYH-Server fehlgeschlagen: ${status.message}"
    is BackendStatus.Unknown -> "Status noch nicht ermittelt."
}

// ── Dein Konto ───────────────────────────────────────────────────────────

@Composable
private fun AccountInfoSection(session: UserSession, modifier: Modifier = Modifier) {
    PokyhCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            InfoRow("Name", session.personName ?: session.username)
            if (session.klasseName.isNotEmpty()) InfoRow("Klasse", session.klasseName)
            InfoRow("Schule", "LBS Brixen")
            InfoRow("Rolle", roleLabel(session))
        }
    }
}

private fun roleLabel(session: UserSession): String = when {
    session.isParent -> "Erziehungsberechtigt"
    session.isStudent -> "Schüler/in"
    else -> "Lehrkraft/Verwaltung"
}

// ── Technische Details / Erneut prüfen ──────────────────────────────────

@Composable
private fun DetailsSection(
    loading: Boolean,
    diagnostics: String?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onReload: () -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhCard(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !loading, onClick = onReload)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Brand.accent)
                } else {
                    Icon(PokyhIcons.arrow_clockwise, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(18.dp))
                }
                Text("Erneut prüfen", style = PokyhType.body, color = Brand.accent)
            }

            HorizontalDivider(color = PokyhTheme.colors.separator, thickness = 0.5.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Technische Details (für Support)",
                    style = PokyhType.body,
                    color = PokyhTheme.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    PokyhIcons.chevron_right,
                    contentDescription = null,
                    tint = PokyhTheme.colors.textTertiary,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = if (expanded) 90f else 0f },
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SelectionContainer {
                        Text(
                            diagnostics ?: "Wird geladen…",
                            style = PokyhType.caption2.copy(fontFamily = FontFamily.Monospace),
                            color = PokyhTheme.colors.textSecondary,
                        )
                    }
                    if (diagnostics != null) {
                        Row(
                            modifier = Modifier.clickable { onShare(diagnostics) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(PokyhIcons.square_and_arrow_up, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(14.dp))
                            Text("Teilen", style = PokyhType.caption, color = Brand.accent)
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(PokyhIcons.lock_shield, contentDescription = null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(12.dp))
                Text(
                    "Enthält nur deine WebUntis-Daten – keine Passwörter.",
                    style = PokyhType.caption2,
                    color = PokyhTheme.colors.textTertiary,
                )
            }
        }
    }
}

// ── Bausteine ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold),
        color = PokyhTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, style = PokyhType.body, color = PokyhTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
    }
}
