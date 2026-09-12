package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.senderColor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * UI-only comment shape — deliberately decoupled from any backend DTO (reminder comments and
 * dish comments use different network models but render identically). Callers map their
 * `ApiComment` list to this before passing it in.
 */
data class CommentUiItem(
    val id: String,
    val authorId: String,
    val authorName: String,
    val body: String,
    val createdAtEpochMs: Long,
    val editedAtEpochMs: Long? = null,
)

/**
 * The comment thread, shared by reminder detail and dish detail.
 *
 * Comments are a grouped [PokyhListCard], not one card per comment — a thread is one object, and
 * per-comment cards made a five-comment thread look like five unrelated things. The composer
 * sits under it as the section's own input.
 *
 * [currentUserId] decides which rows show a delete button; [isAdmin] additionally allows
 * deleting others' comments, shown in [Brand.warning] so it's visibly a moderation action rather
 * than "delete my own".
 */
@Composable
fun CommentSection(
    title: String,
    comments: List<CommentUiItem>,
    currentUserId: String,
    modifier: Modifier = Modifier,
    isAdmin: Boolean = false,
    onAdd: (String) -> Unit = {},
    onDelete: (CommentUiItem) -> Unit = {},
) {
    var draft by remember { mutableStateOf("") }
    val colors = PokyhTheme.colors

    PokyhSection(
        modifier = modifier,
        title = title,
        trailing = if (comments.isEmpty()) null else {
            { PokyhLabel("${comments.size}") }
        },
        contentSpacing = PokyhSpacing.md,
    ) {
        if (comments.isEmpty()) {
            Text(
                text = "Noch keine Kommentare.",
                style = PokyhType.footnote,
                color = colors.textTertiary,
            )
        } else {
            PokyhListCard(items = comments) { comment ->
                CommentRow(
                    comment = comment,
                    canDelete = comment.authorId == currentUserId || isAdmin,
                    isModeration = comment.authorId != currentUserId && isAdmin,
                    onDelete = { onDelete(comment) },
                )
            }
        }

        PokyhComposerField(
            value = draft,
            onValueChange = { draft = it },
            onSubmit = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    onAdd(text)
                    draft = ""
                }
            },
            submitIcon = PokyhIcons.send,
            submitDescription = "Senden",
            placeholder = "Kommentar schreiben…",
        )
    }
}

@Composable
private fun CommentRow(
    comment: CommentUiItem,
    canDelete: Boolean,
    isModeration: Boolean,
    onDelete: () -> Unit,
) {
    val colors = PokyhTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        verticalAlignment = Alignment.Top,
    ) {
        InitialAvatar(
            name = comment.authorName,
            size = 34.dp,
            color = senderColor(comment.authorId),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                Text(
                    text = comment.authorName,
                    style = PokyhType.caption,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = timeAgoGerman(comment.createdAtEpochMs) +
                        (if (comment.editedAtEpochMs != null) " · bearbeitet" else ""),
                    style = PokyhType.caption2,
                    color = colors.textTertiary,
                )
            }
            Text(comment.body, style = PokyhType.callout, color = colors.textSecondary)
        }
        if (canDelete) {
            PokyhIconButton(
                icon = PokyhIcons.delete,
                contentDescription = "Löschen",
                onClick = onDelete,
                tint = if (isModeration) Brand.warning else colors.textTertiary,
                size = 32.dp,
                iconSize = 16.dp,
            )
        }
    }
}

/** "Gerade eben" / "vor N Min." / "vor N Std." / "vor N Tagen" (port of the web's `timeAgo()`). */
private fun timeAgoGerman(epochMs: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = (now - epochMs).milliseconds
    val minutes = diff.inWholeMinutes
    val hours = diff.inWholeHours
    val days = diff.inWholeDays
    return when {
        minutes < 1 -> "Gerade eben"
        minutes < 60 -> "vor $minutes Min."
        hours < 24 -> "vor $hours Std."
        else -> "vor $days ${if (days == 1L) "Tag" else "Tagen"}"
    }
}
