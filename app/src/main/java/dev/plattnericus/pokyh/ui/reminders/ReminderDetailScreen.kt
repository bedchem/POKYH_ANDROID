@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.ui.components.BackendUnavailableView
import dev.plattnericus.pokyh.ui.components.CommentSection
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.StatusLabel
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn

/** A single class reminder: the reminder itself as a card, then its comment thread. */
@Composable
fun ReminderDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReminderDetailViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val backendStatus by viewModel.backendStatus.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val hasBackend = session?.apiToken != null
    val reminder = ui.reminder

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = reminder?.title ?: "Erinnerung",
                eyebrow = "Erinnerung",
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend -> BackendUnavailableView(feature = "Erinnerungen", status = backendStatus)
                ui.loading && reminder == null -> ListSkeleton()
                ui.error != null && reminder == null ->
                    ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                reminder == null ->
                    ErrorStateView(message = "Erinnerung nicht gefunden.", onRetry = viewModel::refresh)
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PokyhSpacing.screenH)
                        .padding(bottom = PokyhSpacing.xxxl),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
                ) {
                    PokyhCard(modifier = Modifier.fadeIn()) {
                        parseRemindAt(reminder.remindAt)?.let { instant ->
                            StatusLabel(
                                text = dueText(instant),
                                color = Brand.orange,
                                icon = PokyhIcons.timetable,
                            )
                            Spacer(Modifier.size(PokyhSpacing.md))
                        }
                        if (reminder.body.isNotEmpty()) {
                            Text(
                                text = reminder.body,
                                style = PokyhType.body,
                                color = PokyhTheme.colors.textPrimary,
                            )
                            Spacer(Modifier.size(PokyhSpacing.lg))
                        }
                        PokyhLabel("von ${reminder.createdByName.ifEmpty { reminder.createdByUsername }}")
                    }
                    CommentSection(
                        title = "Kommentare",
                        comments = ui.comments,
                        currentUserId = ui.currentUserId,
                        isAdmin = ui.isAdmin,
                        onAdd = viewModel::addComment,
                        onDelete = viewModel::deleteComment,
                        modifier = Modifier.fadeIn(delayMillis = 40),
                    )
                }
            }
        }
    }
}
