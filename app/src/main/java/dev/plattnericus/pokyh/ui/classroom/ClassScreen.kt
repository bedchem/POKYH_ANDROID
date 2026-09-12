@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.classroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.ApiClassMember
import dev.plattnericus.pokyh.ui.components.BackendUnavailableView
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhAvatar
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.accentSurface
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn

/**
 * Klasse — class info (name + join code) over the member list. Loaded from the POKYH backend
 * (no WebUntis equivalent).
 *
 * The class card is the screen's hero: the name at [PokyhType.statMedium] with the join code as
 * an accent-tinted monospace capsule under it, because the code is the one thing anyone opens
 * this screen to read out loud.
 */
@Composable
fun ClassScreen(onNavigateBack: () -> Unit = {}, viewModel: ClassViewModel = hiltViewModel()) {
    val klass by viewModel.klass.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val hasBackend = viewModel.hasBackend

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = { PokyhTopBar(title = "Klasse", nav = TopBarNav.Back(onNavigateBack)) },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend -> BackendUnavailableView(feature = "Die Klassenansicht", status = backendStatus)
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                klass != null -> {
                    val c = klass!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = PokyhSpacing.screenH)
                            .padding(bottom = PokyhSpacing.xxxl),
                        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
                    ) {
                        ClassHeaderCard(name = c.name, code = c.code, modifier = Modifier.fadeIn())
                        PokyhSection(
                            title = "Mitglieder",
                            trailing = { PokyhLabel("${c.members.size}") },
                            modifier = Modifier.fadeIn(delayMillis = 40),
                        ) {
                            PokyhListCard(items = c.members) { member -> MemberRow(member) }
                        }
                    }
                }
                else -> EmptyStateView(
                    icon = PokyhIcons.classMembers,
                    title = "Keine Klasse",
                    subtitle = "Du bist noch keiner Klasse beigetreten.",
                )
            }
        }
    }
}

@Composable
private fun ClassHeaderCard(name: String, code: String, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    PokyhCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = PokyhIcons.classMembers,
                contentDescription = null,
                tint = Brand.accent,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.size(PokyhSpacing.md))
            Text(name, style = PokyhType.statMedium, color = colors.textPrimary)
            Spacer(Modifier.size(PokyhSpacing.md))
            PokyhLabel("Beitrittscode")
            Spacer(Modifier.size(PokyhSpacing.sm))
            Text(
                text = code,
                style = PokyhType.title3.copy(fontFamily = FontFamily.Monospace),
                color = colors.accentText,
                modifier = Modifier
                    .accentSurface(PokyhShapes.pill)
                    .padding(horizontal = PokyhSpacing.lg, vertical = PokyhSpacing.sm),
            )
        }
    }
}

@Composable
private fun MemberRow(member: ApiClassMember) {
    PokyhRow(
        title = member.username,
        showChevron = false,
        leading = { PokyhAvatar(url = null, name = member.username, size = 38.dp) },
    )
}
