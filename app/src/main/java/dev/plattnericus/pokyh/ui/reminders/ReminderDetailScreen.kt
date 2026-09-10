@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.reminders
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.ui.components.BackendUnavailableView
import dev.plattnericus.pokyh.ui.components.CommentSection
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn

/** ReminderDetailView.swift, ported. */
@Composable
fun ReminderDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReminderDetailViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val hasBackend = session?.apiToken != null

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Erinnerung", style = PokyhType.headline) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(PokyhIcons.arrow_left, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            val reminder = ui.reminder
            when {
                !hasBackend -> BackendUnavailableView(feature = "Erinnerungen", status = backendStatus)
                ui.loading && reminder == null -> ListSkeleton()
                ui.error != null && reminder == null -> ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                reminder == null -> ErrorStateView(message = "Erinnerung nicht gefunden.", onRetry = viewModel::refresh)
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    PokyhCard(radius = 16.dp, padding = 16.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(reminder.title, style = PokyhType.title2.bold(), color = PokyhTheme.colors.textPrimary)
                            parseRemindAt(reminder.remindAt)?.let { instant ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(PokyhIcons.calendar, contentDescription = null, tint = Brand.orange, modifier = Modifier.size(16.dp))
                                    Text(dueText(instant), style = PokyhType.subheadline, color = Brand.orange)
                                }
                            }
                            if (reminder.body.isNotEmpty()) {
                                Text(reminder.body, style = PokyhType.body, color = PokyhTheme.colors.textSecondary)
                            }
                            Text(
                                "von ${reminder.createdByName.ifEmpty { reminder.createdByUsername }}",
                                style = PokyhType.caption,
                                color = PokyhTheme.colors.textTertiary,
                            )
                        }
                    }

                    HorizontalDivider(color = PokyhTheme.colors.separator)

                    CommentSection(
                        title = "Kommentare",
                        comments = ui.comments,
                        currentUserId = ui.currentUserId,
                        isAdmin = ui.isAdmin,
                        onAdd = viewModel::addComment,
                        onDelete = viewModel::deleteComment,
                        modifier = Modifier.fadeIn(),
                    )
                }
            }
        }
    }
}
