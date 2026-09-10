@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.classreg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.slideInTrailing

/** Port of `ClassregEventsView` (ClassregEventsView.swift) — Klassenbuch-Einträge des gewählten
 * Schuljahres, absteigend nach `createDate` sortiert. */
@Composable
fun ClassregEventsScreen(viewModel: ClassregViewModel = hiltViewModel()) {
    val year by viewModel.year.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var yearMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Klassenbuch", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
                actions = {
                    Box {
                        Text(
                            text = "$year/${(year + 1) % 100}",
                            style = PokyhType.subheadline.semibold(),
                            color = PokyhTheme.colors.textPrimary,
                            modifier = Modifier
                                .slideInTrailing()
                                .clickable { yearMenuExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                            viewModel.availableYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y/${(y + 1) % 100}") },
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
                    icon = PokyhIcons.book_closed_fill,
                    title = "Keine Einträge",
                    subtitle = "Im Klassenbuch sind keine Einträge vorhanden.",
                )
                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    events.sortedByDescending { it.createDate }.forEach { ClassregRow(event = it) }
                }
            }
        }
    }
}

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
    val accent = accentFor(event)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokyhTheme.colors.card, PokyhShapes.r14)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(58.dp)) {
            Text(Fmt.dateShort(event.createDate), style = PokyhType.caption2.bold(), color = PokyhTheme.colors.textPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (event.subjectName.isNotEmpty()) {
                    Text(
                        text = event.subjectName,
                        style = PokyhType.subheadline.bold(),
                        color = PokyhTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                if (event.categoryName.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(accent.copy(alpha = 0.15f), RoundedCornerShape(50)),
                    ) {
                        Text(
                            text = event.categoryName,
                            style = PokyhType.caption2.bold(),
                            color = accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            val bodyText = event.text.ifEmpty { event.eventReasonName }
            if (bodyText.isNotEmpty()) {
                Text(bodyText, style = PokyhType.subheadline, color = PokyhTheme.colors.textPrimary)
            }
            if (event.creatorName.isNotEmpty()) {
                Text(event.creatorName, style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
            }
        }
    }
}
