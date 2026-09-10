@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.classroom
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.appBackground

/** Port of `ClassView` (ClassView.swift) — Klasseninfo (Name/Code) + Mitgliederliste, geladen
 * über den POKYH-Backend (kein WebUntis-Äquivalent). */
@Composable
fun ClassScreen(viewModel: ClassViewModel = hiltViewModel()) {
    val klass by viewModel.klass.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val hasBackend = viewModel.hasBackend

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Klasse", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend -> BackendUnavailableView(feature = "Die Klassenansicht", status = backendStatus)
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                klass != null -> {
                    val c = klass!!
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PokyhTheme.colors.card, PokyhShapes.r18)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = PokyhIcons.person_3_fill,
                                contentDescription = null,
                                tint = Brand.accent,
                                modifier = Modifier.size(34.dp),
                            )
                            Text(c.name, style = PokyhType.title2.bold(), color = PokyhTheme.colors.textPrimary)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Code:", style = PokyhType.subheadline, color = PokyhTheme.colors.textSecondary)
                                Box(
                                    modifier = Modifier
                                        .background(Brand.accent.copy(alpha = 0.12f), RoundedCornerShape(50)),
                                ) {
                                    Text(
                                        text = c.code,
                                        style = PokyhType.body.copy(fontFamily = FontFamily.Monospace).bold(),
                                        color = PokyhTheme.colors.textPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${c.members.size} Mitglieder", style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                            c.members.forEach { m -> MemberRow(member = m) }
                        }
                    }
                }
                else -> EmptyStateView(
                    icon = PokyhIcons.person_3_fill,
                    title = "Keine Klasse",
                    subtitle = "Du bist noch keiner Klasse beigetreten.",
                )
            }
        }
    }
}

@Composable
private fun MemberRow(member: ApiClassMember) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokyhTheme.colors.cardAlt, PokyhShapes.r12)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PokyhAvatar(url = null, name = member.username, size = 36.dp)
        Text(member.username, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
        Spacer(Modifier.weight(1f))
    }
}
