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
import dev.plattnericus.pokyh.core.i18n.strings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.setValue

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
    var editing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    if (editing && reminder != null) {
        AddReminderSheet(
            submitting = saving,
            error = saveError,
            editing = reminder,
            onDismiss = { editing = false },
            onAdd = { title, body, remindAt -> viewModel.editReminder(title, body, remindAt) { editing = false } },
        )
    }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = reminder?.title ?: strings().tasks.reminder,
                eyebrow = strings().tasks.reminder,
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                !hasBackend -> BackendUnavailableView(feature = strings().tasks.remindersFeature, status = backendStatus)
                ui.loading && reminder == null -> ListSkeleton()
                ui.error != null && reminder == null ->
                    ErrorStateView(message = ui.error!!, onRetry = viewModel::refresh)
                reminder == null ->
                    ErrorStateView(message = strings().tasks.reminderNotFound, onRetry = viewModel::refresh)
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PokyhSpacing.screenH)
                        .padding(bottom = PokyhSpacing.xxxl),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
                ) {
                    val at = parseRemindAt(reminder.remindAt)
                    val overdue = at != null && at.isBefore(java.time.Instant.now())
                    val mine = (session?.stableUid != null && reminder.createdBy == session?.stableUid) ||
                        reminder.createdByUsername.equals(session?.username, ignoreCase = true)
                    // "in 3 Tagen · Mittwoch, 29. Oktober, 08:00", as the web's sheet.
                    if (at != null) {
                        val l = dev.plattnericus.pokyh.core.i18n.I18n.lang.value
                        val local = at.atZone(java.time.ZoneId.systemDefault())
                        Text(
                            timeUntil(at) + " · " + strings().tasks.dateTime(
                                java.time.format.DateTimeFormatter.ofPattern(strings().tasks.dayLong, l.locale).format(local).replaceFirstChar { it.titlecase(l.locale) },
                                java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(local),
                            ),
                            style = PokyhType.callout,
                            color = PokyhTheme.colors.textSecondary,
                            modifier = Modifier.fadeIn(),
                        )
                    }
                    val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(PokyhTheme.colors.isDark)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .fadeIn()
                            .background(if (overdue) tone.fill else PokyhTheme.colors.card, dev.plattnericus.pokyh.ui.theme.PokyhShapes.xl)
                            .padding(PokyhSpacing.card),
                    ) {
                        if (reminder.body.isNotEmpty()) {
                            Text(
                                text = reminder.body,
                                style = PokyhType.body,
                                color = if (overdue) tone.ink else PokyhTheme.colors.textPrimary,
                            )
                            Spacer(Modifier.size(PokyhSpacing.sm))
                        }
                        Text(
                            if (mine) strings().common.you else strings().tasks.by(reminder.createdByUsername.ifEmpty { reminder.createdByName }),
                            style = PokyhType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                            color = (if (overdue) tone.ink else PokyhTheme.colors.textSecondary).copy(alpha = 0.75f),
                        )
                    }
                    if (mine || ui.isAdmin) {
                        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                            dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton(
                                text = strings().tasks.edit,
                                icon = PokyhIcons.edit,
                                onClick = { editing = true },
                            )
                            dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton(
                                text = if (ui.isAdmin && !mine) strings().tasks.deleteAsAdmin else strings().tasks.deleteItem,
                                icon = PokyhIcons.delete,
                                onClick = { viewModel.deleteReminder(onNavigateBack) },
                            )
                        }
                    }
                    CommentSection(
                        title = strings().common.comments,
                        comments = ui.comments,
                        currentUserId = ui.currentUserId,
                        isAdmin = ui.isAdmin,
                        onAdd = viewModel::addComment,
                        onDelete = viewModel::deleteComment,
                        onEdit = viewModel::editComment,
                        reportWhere = "https://pokyh.com/reminders",
                        modifier = Modifier.fadeIn(delayMillis = 40),
                    )
                }
            }
        }
    }
}
