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

/** Nachrichten — the WebUntis inbox, one grouped list per folder. */
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = hiltViewModel(),
    onMessageClick: (Int) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val ui by viewModel.list.collectAsStateWithLifecycle()
    val composeState by viewModel.compose.collectAsStateWithLifecycle()
    val isLoading = ui.loadingFolders.contains(ui.folder)

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = "Nachrichten",
                nav = TopBarNav.Back(onNavigateBack),
                actions = {
                    // Only the inbox has a read state, so the action only exists there — and it
                    // greys out once nothing is left to mark, rather than disappearing.
                    if (ui.folder == MessageFolder.Inbox) {
                        PokyhTextButton(
                            text = "Alle als gelesen",
                            icon = PokyhIcons.markAllRead,
                            onClick = viewModel::markAllRead,
                            enabled = ui.unreadIds.isNotEmpty() && !ui.markingAll,
                        )
                    }
                    PokyhIconButton(
                        icon = PokyhIcons.compose,
                        contentDescription = "Mitteilung verfassen",
                        onClick = viewModel::openCompose,
                        tint = Brand.onAccent,
                        containerColor = Brand.accent,
                    )
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
                    .padding(bottom = PokyhSpacing.lg),
            )
            Box(Modifier.fillMaxSize()) {
                when {
                    ui.firstLoad -> ListSkeleton()
                    ui.error != null && ui.messages.isEmpty() ->
                        ErrorStateView(message = ui.error!!, onRetry = viewModel::retry)
                    ui.messages.isEmpty() -> EmptyStateView(
                        icon = PokyhIcons.emptyFolder,
                        title = "Keine Nachrichten",
                        subtitle = "Dieser Ordner ist leer.",
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = PokyhSpacing.screenH,
                            end = PokyhSpacing.screenH,
                            bottom = PokyhSpacing.xxxl,
                        ),
                    ) {
                        item {
                            PokyhListCard(items = ui.messages, modifier = Modifier.fadeIn()) { msg ->
                                MessageRow(
                                    msg = msg,
                                    onClick = {
                                        viewModel.markRead(msg)
                                        onMessageClick(msg.id)
                                    },
                                )
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
private fun MessageRow(msg: MessagePreview, onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .pressHighlight(interactionSource, shape = null)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            InitialAvatar(name = msg.senderName, size = 42.dp)
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
                if (msg.hasAttachments) {
                    Icon(
                        imageVector = PokyhIcons.attachment,
                        contentDescription = "Anhang",
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
    private val germanWeekdays = mapOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So",
    )
    private val germanMonths = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )

    fun dateLabel(dateStr: String): String {
        val dt = parse(dateStr) ?: return ""
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val today = now.date
        if (dt.date == today) return "%02d:%02d".format(dt.hour, dt.minute)
        val yesterday = today.minus(DatePeriod(days = 1))
        if (dt.date == yesterday) return "Gestern"
        val days = today.toEpochDays() - dt.date.toEpochDays()
        if (days in 1..6) return germanWeekdays[dt.date.dayOfWeek] ?: ""
        return "%02d.%02d.".format(dt.date.dayOfMonth, dt.date.monthNumber)
    }

    fun fullDate(dateStr: String): String {
        val dt = parse(dateStr) ?: return dateStr
        return "${dt.date.dayOfMonth}. ${germanMonths[dt.date.monthNumber - 1]} ${dt.date.year} um %02d:%02d"
            .format(dt.hour, dt.minute)
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
