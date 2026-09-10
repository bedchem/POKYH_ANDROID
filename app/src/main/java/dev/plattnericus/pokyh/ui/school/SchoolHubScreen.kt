@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.school
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.setValue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.ui.components.IconTile
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.pressable

/** One row of the hub — either navigates to [route], or (when [route] is null, i.e. "Noten")
 * switches the selected bottom tab instead (Store.swift `app.selectedTab = .noten`). */
private data class HubItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val route: String?,
)

/** SchoolHubView.swift, ported. Wichtiges oben: Noten → Todos → Erinnerungen → Abwesenheiten →
 * Klassenbuch → Klasse. "Nachrichten" ist über den Briefumschlag-Button oben rechts erreichbar
 * (bereits Teil des globalen Toolbars, hier nicht dupliziert). */
@Composable
fun SchoolHubScreen(
    onNavigate: (String) -> Unit,
    viewModel: SchoolHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val items = remember(state.isParent) {
        buildList {
            add(HubItem("Noten", "Alle Fächer & Bewertungen", PokyhIcons.chart_bar_fill, Brand.accent, route = null))
            add(HubItem("Todos", "Persönliche Aufgabenliste", PokyhIcons.checklist, Brand.accentSoft, PokyhDestinations.TODOS))
            // Eltern-/Erziehungsberechtigtenkonten haben eigene Todos und sehen die Klasse, aber
            // KEINE Klassen-Erinnerungen.
            if (!state.isParent) {
                add(HubItem("Erinnerungen", "Hausaufgaben & Klassen-Erinnerungen", PokyhIcons.bell_fill, Brand.tint, PokyhDestinations.REMINDERS))
            }
            add(HubItem("Abwesenheiten", "Fehlstunden & Entschuldigungen", PokyhIcons.person_fill_xmark, Brand.orange, PokyhDestinations.ABSENCES))
            add(HubItem("Klassenbuch", "Klassenbuch-Einträge", PokyhIcons.book_closed_fill, Brand.orange, PokyhDestinations.CLASSREG_EVENTS))
            add(HubItem("Klasse", "Klassenmitglieder & Code", PokyhIcons.person_3_fill, Brand.tint, PokyhDestinations.CLASSROOM))
        }
    }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Schule", style = PokyhType.title1) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEachIndexed { idx, item ->
                val interactionSource = remember { MutableInteractionSource() }
                HubRow(
                    item = item,
                    interactionSource = interactionSource,
                    onClick = { if (item.route != null) onNavigate(item.route) else viewModel.selectGradesTab() },
                    modifier = Modifier.fadeIn(idx * 40).pressable(interactionSource),
                )
            }
        }
    }
}

@Composable
private fun HubRow(
    item: HubItem,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PokyhCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        padding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon = item.icon, color = item.accent, size = 46.dp, cornerRadius = 12.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)
                Text(item.subtitle, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
            }
            Icon(
                PokyhIcons.chevron_right,
                contentDescription = null,
                tint = PokyhTheme.colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
