@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.messages
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.smoothCorner
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.atTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** MessagesView.swift, ported. */
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = hiltViewModel(),
    onMessageClick: (Int) -> Unit,
) {
    val ui by viewModel.list.collectAsStateWithLifecycle()
    val isLoading = ui.loadingFolders.contains(ui.folder)

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Nachrichten", style = PokyhType.headline) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            FolderSegmentedControl(
                selected = ui.folder,
                onSelect = viewModel::selectFolder,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Box(Modifier.fillMaxSize()) {
                when {
                    ui.firstLoad -> ListSkeleton()
                    ui.error != null && ui.messages.isEmpty() -> ErrorStateView(message = ui.error!!, onRetry = viewModel::retry)
                    ui.messages.isEmpty() -> EmptyStateView(
                        icon = PokyhIcons.envelope,
                        title = "Keine Nachrichten",
                        subtitle = "Dieser Ordner ist leer.",
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        items(ui.messages, key = { it.id }) { msg ->
                            MessageRow(
                                msg = msg,
                                modifier = Modifier.fadeIn(),
                                onClick = {
                                    viewModel.markRead(msg)
                                    onMessageClick(msg.id)
                                },
                            )
                        }
                    }
                }

                if (isLoading && !ui.firstLoad) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(6.dp).size(20.dp),
                        color = Brand.accent,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

/** Port of the iOS `Picker(.segmented)` over [MessageFolder]. */
@Composable
private fun FolderSegmentedControl(
    selected: MessageFolder,
    onSelect: (MessageFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PokyhTheme.colors.cardAlt, PokyhShapes.r10)
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MessageFolder.entries.forEach { folder ->
            val isSelected = folder == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(folder) }
                    .background(if (isSelected) PokyhTheme.colors.surface else androidx.compose.ui.graphics.Color.Transparent, smoothCorner(8.dp))
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = folder.label,
                    style = PokyhType.subheadline.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (isSelected) PokyhTheme.colors.textPrimary else PokyhTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MessageRow(msg: MessagePreview, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.TopStart) {
            InitialAvatar(name = msg.senderName, size = 42.dp)
            if (!msg.isRead) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-2).dp, y = (-2).dp)
                        .size(11.dp)
                        .background(PokyhTheme.colors.surface, androidx.compose.foundation.shape.CircleShape)
                        .padding(2.dp)
                        .background(Brand.accent, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = msg.subject,
                    style = PokyhType.subheadline.copy(fontWeight = if (msg.isRead) FontWeight.Normal else FontWeight.Bold),
                    color = PokyhTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = MessageFormat.dateLabel(msg.sentDate),
                    style = PokyhType.caption2,
                    color = PokyhTheme.colors.textSecondary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val preview = msg.senderName + (if (msg.contentPreview.isEmpty()) "" else " · ${msg.contentPreview}")
                Text(
                    text = preview,
                    style = PokyhType.caption,
                    color = PokyhTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (msg.hasAttachments) {
                    Icon(
                        PokyhIcons.paperclip,
                        contentDescription = "Anhang",
                        tint = PokyhTheme.colors.textTertiary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** `MessageFormat` (MessagesView.swift), ported with kotlinx.datetime instead of Foundation
 * `Calendar`/`DateFormatter`. */
object MessageFormat {
    private val germanWeekdays = mapOf(
        DayOfWeek.MONDAY to "Mo", DayOfWeek.TUESDAY to "Di", DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do", DayOfWeek.FRIDAY to "Fr", DayOfWeek.SATURDAY to "Sa", DayOfWeek.SUNDAY to "So",
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
        return "${dt.date.dayOfMonth}. ${germanMonths[dt.date.monthNumber - 1]} ${dt.date.year} um %02d:%02d".format(dt.hour, dt.minute)
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
     * needs local wall-clock fields, same as iOS's calendar-day comparisons) plus a few common
     * backend timestamp shapes. No `Instant`/timezone conversion needed for that. */
    private fun parse(raw: String): LocalDateTime? {
        val normalized = Regex("(Z|[+-]\\d{2}:?\\d{2})$").replace(raw.trim(), "").replace(' ', 'T')
        runCatching { return LocalDateTime.parse(normalized) }
        runCatching { return LocalDate.parse(normalized).atTime(0, 0) }
        return null
    }
}
