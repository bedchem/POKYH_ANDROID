@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.classreg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.ClassregEvent
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.slideInTrailing

/** Klassenbuch — entries for the selected school year, newest `createDate` first, as one
 * grouped list. */
@Composable
fun ClassregEventsScreen(onNavigateBack: () -> Unit = {}, viewModel: ClassregViewModel = hiltViewModel()) {
    val year by viewModel.year.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var yearMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Klassenbuch",
                eyebrow = "Schuljahr $year/${(year + 1) % 100}",
                nav = TopBarNav.Back(onNavigateBack),
                actions = {
                    Box(modifier = Modifier.slideInTrailing()) {
                        PokyhTextButton(
                            text = "$year/${(year + 1) % 100}",
                            onClick = { yearMenuExpanded = true },
                            color = PokyhTheme.colors.textPrimary,
                        )
                        DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                            viewModel.availableYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y/${(y + 1) % 100}", style = PokyhType.body) },
                                    onClick = {
                                        yearMenuExpanded = false
                                        viewModel.selectYear(y)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                events.isEmpty() -> EmptyStateView(
                    icon = PokyhIcons.classRegister,
                    title = "Keine Einträge",
                    subtitle = "Im Klassenbuch sind für dieses Schuljahr keine Einträge vorhanden.",
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PokyhSpacing.screenH)
                        .padding(bottom = PokyhSpacing.xxxl),
                ) {
                    PokyhListCard(
                        items = events.sortedByDescending { it.createDate },
                        modifier = Modifier.fadeIn(),
                    ) { event ->
                        ClassregRow(event)
                    }
                }
            }
        }
    }
}

/** Category tint: only the two categories that actually mean "problem" get a status color; the
 * rest stay neutral, so a page of ordinary entries isn't a page of red and orange. */
private fun accentFor(event: ClassregEvent): Color {
    val l = event.categoryName.lowercase()
    return when {
        l.contains("täuschung") || l.contains("betrug") -> Brand.danger
        l.contains("vermerk") -> Brand.warning
        else -> Brand.accent
    }
}

@Composable
private fun ClassregRow(event: ClassregEvent) {
    val colors = PokyhTheme.colors
    val accent = accentFor(event)

    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = Fmt.dateShort(event.createDate),
            style = PokyhType.caption,
            color = colors.textTertiary,
            modifier = Modifier.width(52.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                if (event.subjectName.isNotEmpty()) {
                    Text(
                        text = event.subjectName,
                        style = PokyhType.headline,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                if (event.categoryName.isNotEmpty()) {
                    TagChip(text = event.categoryName, color = accent)
                }
            }
            val bodyText = event.text.ifEmpty { event.eventReasonName }
            if (bodyText.isNotEmpty()) {
                Text(bodyText, style = PokyhType.callout, color = colors.textSecondary)
            }
            if (event.creatorName.isNotEmpty()) {
                Text(event.creatorName, style = PokyhType.caption2, color = colors.textTertiary)
            }
        }
    }
}
