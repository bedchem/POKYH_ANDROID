@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.MessageFolder
import dev.plattnericus.pokyh.data.model.MessagePreview
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.InitialAvatar
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhSegmentedControl
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.atTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.setValue

/** Nachrichten — the WebUntis inbox, one grouped list per folder. */
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = hiltViewModel(),
    /** The message and whether it can be answered (only what came into the inbox). */
    onMessageClick: (Int, Boolean) -> Unit,
    onNavigateBack: () -> Unit = {},
    /** Opened from Home's "Nachricht schreiben": the compose sheet straight away. */
    openCompose: Boolean = false,
) {
    val ui by viewModel.list.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { if (openCompose) viewModel.openCompose() }
    val composeState by viewModel.compose.collectAsStateWithLifecycle()
    val isLoading = ui.loadingFolders.contains(ui.folder)
    var confirmDelete by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = ui.selecting) { viewModel.clearSelection() }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = if (ui.selecting) strings().messages.selectedCount(ui.selected.size) else strings().nav.messages,
                nav = if (ui.selecting) TopBarNav.Back(viewModel::clearSelection) else TopBarNav.Back(onNavigateBack),
                actions = if (ui.selecting) {
                    {
                        PokyhTextButton(text = strings().messages.selectAll, onClick = viewModel::selectAll)
                        PokyhIconButton(
                            icon = PokyhIcons.delete,
                            contentDescription = strings().messages.delete,
                            onClick = { confirmDelete = true },
                            tint = Brand.danger,
                        )
                    }
                } else {
                    {
                    // Only the inbox has a read state, so the action only exists there — and it
                    // greys out once nothing is left to mark, rather than disappearing.
                    if (ui.folder == MessageFolder.Inbox) {
                        PokyhTextButton(
                            text = strings().messages.markAllRead,
                            icon = PokyhIcons.markAllRead,
                            onClick = viewModel::markAllRead,
                            enabled = ui.unreadIds.isNotEmpty() && !ui.markingAll,
                        )
                    }
                    PokyhIconButton(
                        icon = PokyhIcons.compose,
                        contentDescription = strings().messages.compose,
                        onClick = viewModel::openCompose,
                        tint = Brand.onAccent,
                        containerColor = Brand.accent,
                    )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            PokyhSegmentedControl(
                options = MessageFolder.entries,
                selected = ui.folder,
                onSelect = viewModel::selectFolder,
                label = { it.label },
                modifier = Modifier
                    .padding(horizontal = PokyhSpacing.screenH)
                    .padding(bottom = PokyhSpacing.md),
            )
            if (ui.folder != MessageFolder.Drafts) {
                dev.plattnericus.pokyh.ui.components.PokyhTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = strings().messages.search,
                    leadingIcon = PokyhIcons.search,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PokyhSpacing.screenH)
                        .padding(bottom = PokyhSpacing.md),
                )
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    ui.firstLoad -> ListSkeleton()
                    ui.error != null && ui.messages.isEmpty() ->
                        ErrorStateView(message = ui.error!!, onRetry = viewModel::retry)
                    ui.messages.isEmpty() && ui.query.isNotBlank() -> EmptyStateView(
                        sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Search,
                        title = if (ui.searching) strings().common.loading else strings().messages.noMatches,
                    )
                    ui.messages.isEmpty() -> EmptyStateView(
                        sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Mail,
                        title = strings().messages.emptyTitle,
                        subtitle = strings().messages.emptyFolder,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = PokyhSpacing.screenH,
                            end = PokyhSpacing.screenH,
                            bottom = PokyhSpacing.xxxl,
                        ),
                    ) {
                        val groups = ui.messages.groupBy { MessageFormat.group(it.sentDate) }.toSortedMap()
                        groups.forEach { (group, list) ->
                            item(key = "g$group") {
                                dev.plattnericus.pokyh.ui.components.PokyhLabel(
                                    when (group) {
                                        0 -> I18n.s.messages.groupToday
                                        1 -> I18n.s.messages.groupWeek
                                        else -> I18n.s.messages.groupOlder
                                    },
                                    Modifier.padding(top = if (group == groups.firstKey()) 0.dp else PokyhSpacing.lg, bottom = PokyhSpacing.sm),
                                )
                            }
                            item(key = "l$group") {
                                PokyhListCard(items = list, modifier = Modifier.fadeIn()) { msg ->
                                    MessageRow(
                                        msg = msg,
                                        selected = msg.id in ui.selected,
                                        onLongClick = { viewModel.toggleSelected(msg.id) },
                                        onClick = {
                                            when {
                                                ui.selecting -> viewModel.toggleSelected(msg.id)
                                                // A draft opens in the form again, to go on with it.
                                                ui.folder == MessageFolder.Drafts -> viewModel.openDraft(msg.id)
                                                else -> {
                                                    viewModel.markRead(msg)
                                                    onMessageClick(msg.id, ui.folder == MessageFolder.Inbox)
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (isLoading && !ui.firstLoad) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(PokyhSpacing.sm)
                            .size(20.dp),
                        color = Brand.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = PokyhTheme.colors.card,
            title = { Text(strings().messages.deleteConfirm(ui.selected.size), style = PokyhType.headline, color = PokyhTheme.colors.textPrimary) },
            confirmButton = {
                PokyhTextButton(text = strings().messages.delete, color = Brand.danger, onClick = { confirmDelete = false; viewModel.deleteSelected() })
            },
            dismissButton = {
                PokyhTextButton(text = strings().common.cancel, color = PokyhTheme.colors.textSecondary, onClick = { confirmDelete = false })
            },
        )
    }

    if (composeState.open) {
        ComposeMessageSheet(
            state = composeState,
            viewModel = viewModel,
            onDismiss = viewModel::closeCompose,
        )
    }
}

/**
 * An inbox row. Unread is carried by the subject's weight plus an accent dot on the avatar —
 * not by a tinted row background, which would put a second surface color inside the card for
 * every unread message.
 */
@Composable
private fun MessageRow(msg: MessagePreview, onClick: () -> Unit, selected: Boolean = false, onLongClick: (() -> Unit)? = null) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .then(if (selected) Modifier.background(colors.accentTint) else Modifier)
            .pressHighlight(interactionSource, shape = null)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (selected) {
                Box(Modifier.size(42.dp).background(Brand.accent, PokyhShapes.pill), contentAlignment = Alignment.Center) {
                    Icon(PokyhIcons.check, contentDescription = null, tint = Brand.onAccent, modifier = Modifier.size(20.dp))
                }
            } else InitialAvatar(name = msg.senderName, size = 42.dp)
            if (!msg.isRead) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(12.dp)
                        .background(colors.card, PokyhShapes.pill)
                        .padding(2.dp)
                        .background(Brand.accent, PokyhShapes.pill),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = msg.subject,
                    style = if (msg.isRead) PokyhType.body else PokyhType.body.semibold(),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(PokyhSpacing.sm))
                Text(
                    text = MessageFormat.dateLabel(msg.sentDate),
                    style = PokyhType.caption2,
                    color = colors.textTertiary,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
            ) {
                val preview = msg.senderName +
                    (if (msg.contentPreview.isEmpty()) "" else " · ${msg.contentPreview}")
                Text(
                    text = preview,
                    style = PokyhType.footnote,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (msg.needsConfirmation) {
                    val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.butter(colors.isDark)
                    Text(strings().messages.needsConfirm, style = PokyhType.caption2.semibold(), color = tone.ink, modifier = Modifier.background(tone.fill, PokyhShapes.pill).padding(horizontal = 8.dp, vertical = 1.dp))
                }
                val total = msg.readTotal ?: 0
                if (total > 0) {
                    Text(strings().messages.readCount(msg.readConfirmed ?: 0, total), style = PokyhType.caption2.semibold(), color = colors.textSecondary, modifier = Modifier.background(colors.nested, PokyhShapes.pill).padding(horizontal = 8.dp, vertical = 1.dp))
                }
                if (msg.hasAttachments) {
                    Icon(
                        imageVector = PokyhIcons.attachment,
                        contentDescription = strings().messages.attachment,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/** `MessageFormat`, ported with kotlinx.datetime instead of Foundation `Calendar`/`DateFormatter`. */
object MessageFormat {

    fun dateLabel(dateStr: String): String {
        val dt = parse(dateStr) ?: return ""
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val today = now.date
        if (dt.date == today) return "%02d:%02d".format(dt.hour, dt.minute)
        val yesterday = today.minus(DatePeriod(days = 1))
        if (dt.date == yesterday) return I18n.s.common.yesterday
        val days = today.toEpochDays() - dt.date.toEpochDays()
        if (days in 1..6) return I18n.s.common.weekdaysShort[dt.date.dayOfWeek.ordinal]
        return "%02d.%02d.".format(dt.date.dayOfMonth, dt.date.monthNumber)
    }

    /** Today (0), this week (1) or older (2), for the list's sections. */
    fun group(dateStr: String): Int {
        val dt = parse(dateStr) ?: return 2
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (dt.date == today) return 0
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        return if (dt.date >= monday) 1 else 2
    }

    fun fullDate(dateStr: String): String {
        val dt = parse(dateStr) ?: return dateStr
        val c = I18n.s.common
        return I18n.s.messages.at(c.dateLong(dt.date.dayOfMonth, dt.date.monthNumber, dt.date.year), "%02d:%02d".format(dt.hour, dt.minute))
    }

    /** Entfernt HTML-Tags grob (entspricht sanitize -> plaintext). */
    fun plainText(raw: String): String {
        if (raw.isEmpty()) return ""
        var s = raw
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .replace("</p>", "\n")
        s = Regex("<[^>]+>").replace(s, "")
        s = s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        return s.trim()
    }

    /** Accepts ISO-8601 (with or without a trailing `Z`/offset, which is stripped — display only
     * needs local wall-clock fields, same as the calendar-day comparisons above) plus a few
     * common backend timestamp shapes. No `Instant`/timezone conversion needed for that. */
    private fun parse(raw: String): LocalDateTime? {
        val normalized = Regex("(Z|[+-]\\d{2}:?\\d{2})$").replace(raw.trim(), "").replace(' ', 'T')
        runCatching { return LocalDateTime.parse(normalized) }
        runCatching { return LocalDate.parse(normalized).atTime(0, 0) }
        return null
    }
}
