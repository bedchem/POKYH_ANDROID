package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
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

/** Port of `CommentSection` — reused by reminder detail and dish detail. [currentUserId] decides
 * which rows show a delete button; [isAdmin] additionally allows deleting others' comments
 * (shown in orange, matching iOS). */
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

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary)

        comments.forEach { comment ->
            val canDelete = comment.authorId == currentUserId || isAdmin
            val deleteTint = if (comment.authorId != currentUserId && isAdmin) Brand.orange else PokyhTheme.colors.textTertiary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PokyhTheme.colors.cardAlt, PokyhShapes.r12)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InitialAvatar(name = comment.authorName, size = 32.dp, color = dev.plattnericus.pokyh.ui.theme.senderColor(comment.authorId))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(comment.authorName, style = PokyhType.caption.copy(fontWeight = FontWeight.SemiBold), color = PokyhTheme.colors.textPrimary)
                        Text(
                            timeAgoGerman(comment.createdAtEpochMs) + (if (comment.editedAtEpochMs != null) " · bearbeitet" else ""),
                            style = PokyhType.caption2,
                            color = PokyhTheme.colors.textTertiary,
                        )
                    }
                    Text(comment.body, style = PokyhType.subheadline, color = PokyhTheme.colors.textSecondary)
                }
                if (canDelete) {
                    IconButton(onClick = { onDelete(comment) }, modifier = Modifier.size(28.dp)) {
                        Icon(PokyhIcons.trash, contentDescription = "Löschen", tint = deleteTint, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PokyhTheme.colors.cardAlt, PokyhShapes.r12)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Kommentar schreiben…", style = PokyhType.subheadline) },
                textStyle = PokyhType.subheadline,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            IconButton(onClick = {
                val text = draft.trim()
                if (text.isNotEmpty()) { onAdd(text); draft = "" }
            }) {
                Icon(PokyhIcons.arrow_up_circle_fill, contentDescription = "Senden", tint = Brand.accent, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** "Gerade eben" / "vor N Min." / "vor N Std." / "vor N Tagen" (port of Web's `timeAgo()`, used
 * consistently by CommentSection on both iOS's ports and here). */
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
