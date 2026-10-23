@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.profile

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn as animateFadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.insetSurface

/**
 * "Konto & Verbindung" — the general "what does my POKYH account see" diagnosis, reachable for
 * every signed-in user (not just ones with backend trouble).
 *
 * Built from the same hero-then-grouped-rows shape as [ProfileScreen], with the status hero
 * reusing the empty-state glyph-disc layout — so a screen that only ever appears when something
 * is confusing still looks like the rest of the app.
 *
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
        topBar = { PokyhTopBar(title = I18n.s.profile.connectionTitle, nav = TopBarNav.Back(onNavigateUp)) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().appBackground().padding(padding),
            contentPadding = PaddingValues(
                start = PokyhSpacing.screenH,
                end = PokyhSpacing.screenH,
                bottom = PokyhSpacing.xxxl,
            ),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
        ) {
            item(key = "hero") { HeroSection(backendStatus, Modifier.fadeIn()) }
            if (session != null) {
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

// ── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun HeroSection(status: BackendStatus, modifier: Modifier = Modifier) {
    val tint = statusTint(status)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(80.dp).background(tint.copy(alpha = 0.12f), PokyhShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(statusIcon(status), contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.size(PokyhSpacing.lg))
        Text(
            text = status.uiLabel(),
            style = PokyhType.title2,
            color = PokyhTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(
            text = statusExplanation(status),
            style = PokyhType.callout,
            color = PokyhTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun statusTint(status: BackendStatus): Color = when (status) {
    is BackendStatus.Ok -> Brand.success
    is BackendStatus.Failed -> Brand.danger
    else -> Brand.warning
}

private fun statusIcon(status: BackendStatus): ImageVector = when (status) {
    is BackendStatus.Ok -> PokyhIcons.verified
    is BackendStatus.NotStudent -> PokyhIcons.notAStudent
    is BackendStatus.NoClass -> PokyhIcons.noClass
    is BackendStatus.Failed -> PokyhIcons.serverUnreachable
    is BackendStatus.Unknown -> PokyhIcons.unknown
    is BackendStatus.Offline -> PokyhIcons.offline
}

private fun statusExplanation(status: BackendStatus): String = when (status) {
    is BackendStatus.Ok -> I18n.s.profile.heroOk
    is BackendStatus.NotStudent -> I18n.s.profile.heroNotStudent
    is BackendStatus.NoClass ->
        I18n.s.profile.heroNoClass
    is BackendStatus.Failed -> I18n.s.profile.heroFailed(status.message)
    is BackendStatus.Unknown -> I18n.s.profile.heroUnknown
    is BackendStatus.Offline -> I18n.s.profile.heroOffline
}

// ── Dein Konto ──────────────────────────────────────────────────────────────

@Composable
private fun AccountInfoSection(session: UserSession, modifier: Modifier = Modifier) {
    PokyhSection(title = I18n.s.profile.yourAccount, modifier = modifier) {
        PokyhCard(padding = 0.dp) {
            InfoRow(I18n.s.profile.name, session.personName ?: session.username)
            if (session.klasseName.isNotEmpty()) {
                PokyhRowSeparator()
                InfoRow(I18n.s.profile.klasse, session.klasseName)
            }
            PokyhRowSeparator()
            InfoRow(I18n.s.profile.school, I18n.s.profile.schoolName)
            PokyhRowSeparator()
            InfoRow(I18n.s.profile.role, roleLabel(session))
        }
    }
}

private fun roleLabel(session: UserSession): String = when {
    session.isParent -> I18n.s.profile.parent
    session.isStudent -> I18n.s.profile.student
    else -> I18n.s.profile.staff
}

@Composable
private fun InfoRow(label: String, value: String) {
    PokyhRow(
        title = value,
        subtitle = label,
        showChevron = false,
    )
}

// ── Technische Details / Erneut prüfen ──────────────────────────────────────

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
    PokyhSection(title = I18n.s.profile.diagnosis, modifier = modifier) {
        PokyhCard(padding = 0.dp) {
            PokyhRow(
                title = I18n.s.profile.checkAgain,
                titleColor = PokyhTheme.colors.accentText,
                leading = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Brand.accent,
                        )
                    } else {
                        Icon(
                            imageVector = PokyhIcons.refresh,
                            contentDescription = null,
                            tint = PokyhTheme.colors.accentText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                onClick = onReload,
                enabled = !loading,
                showChevron = false,
            )
            PokyhRowSeparator()
            PokyhRow(
                title = I18n.s.profile.technicalDetails,
                subtitle = I18n.s.profile.forSupport,
                leading = {
                    Icon(
                        imageVector = PokyhIcons.document,
                        contentDescription = null,
                        tint = PokyhTheme.colors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = onToggleExpanded,
                showChevron = false,
                trailing = {
                    Icon(
                        imageVector = PokyhIcons.chevronRight,
                        contentDescription = null,
                        tint = PokyhTheme.colors.textTertiary,
                        modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else 0f),
                    )
                },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = animateFadeIn(tween(PokyhMotion.durationFast)) + expandVertically(tween(PokyhMotion.durationStandard)),
                exit = fadeOut(tween(PokyhMotion.durationFast)) + shrinkVertically(tween(PokyhMotion.durationStandard)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PokyhSpacing.card)
                        .padding(bottom = PokyhSpacing.card),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    SelectionContainer {
                        Text(
                            text = diagnostics ?: I18n.s.common.loading,
                            style = PokyhType.caption2.copy(fontFamily = FontFamily.Monospace),
                            color = PokyhTheme.colors.textSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .insetSurface(PokyhShapes.sm)
                                .padding(PokyhSpacing.md),
                        )
                    }
                    if (diagnostics != null) {
                        PokyhTextButton(
                            text = I18n.s.profile.share,
                            icon = PokyhIcons.share,
                            onClick = { onShare(diagnostics) },
                        )
                    }
                }
            }
            PokyhRowSeparator()
            Row(
                modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                Icon(
                    imageVector = PokyhIcons.security,
                    contentDescription = null,
                    tint = PokyhTheme.colors.textTertiary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = I18n.s.profile.detailsNote,
                    style = PokyhType.caption2,
                    color = PokyhTheme.colors.textTertiary,
                )
            }
        }
    }
}
