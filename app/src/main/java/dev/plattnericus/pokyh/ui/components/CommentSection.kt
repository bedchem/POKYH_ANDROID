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
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch

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
    /** The stars its author gave the dish, as the backend matched them (dish comments only). */
    val stars: Int? = null,
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
    /** Posts a comment and says whether it worked: the draft is only cleared once it did. */
    onAdd: (body: String, done: (Boolean) -> Unit) -> Unit = { _, done -> done(false) },
    onDelete: (CommentUiItem) -> Unit = {},
    /** Saves an edit of the account's own comment; without it, no edit button. */
    onEdit: ((CommentUiItem, String, (Boolean) -> Unit) -> Unit)? = null,
    /** Stars per account (stableUid → 1–5), shown beside the author's name — under a dish. */
    ratings: Map<String, Double> = emptyMap(),
    /** The thread is still loading: a spinner, not "no comments". */
    loading: Boolean = false,
    /** Commenting needs the POKYH server; without it the thread is read-only. */
    canWrite: Boolean = true,
    /** Where the thread is, for a report ("Wo: …"). */
    reportWhere: String = "https://pokyh.com",
) {
    val c = strings().common
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val colors = PokyhTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    PokyhSection(
        modifier = modifier,
        title = title,
        trailing = if (comments.isEmpty()) null else {
            { PokyhLabel("${comments.size}") }
        },
        contentSpacing = PokyhSpacing.md,
    ) {
        when {
            loading && comments.isEmpty() -> Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.textTertiary)
            }
            comments.isEmpty() -> Text(
                text = if (canWrite && currentUserId.isNotEmpty()) "${c.noComments} ${c.noCommentsHint}" else c.noComments,
                style = PokyhType.footnote,
                color = colors.textTertiary,
            )
            else -> PokyhListCard(items = comments) { comment ->
                val mine = comment.authorId == currentUserId && currentUserId.isNotEmpty()
                CommentRow(
                    comment = comment,
                    // The live vote under the same account first; else what the backend matched.
                    stars = ratings[comment.authorId]?.toInt() ?: comment.stars,
                    mine = mine,
                    canDelete = mine || isAdmin,
                    isModeration = !mine && isAdmin,
                    editing = editingId == comment.id,
                    canEdit = mine && onEdit != null && canWrite,
                    canReport = !mine && currentUserId.isNotEmpty(),
                    onStartEdit = { editingId = comment.id },
                    onCancelEdit = { editingId = null },
                    onSaveEdit = { body, done ->
                        onEdit?.invoke(comment, body) { ok ->
                            if (ok) editingId = null
                            done(ok)
                        }
                    },
                    onReport = { scope.launch { reportComment(context, comment, reportWhere) } },
                    onDelete = { onDelete(comment) },
                )
            }
        }

        if (canWrite) {
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
                PokyhComposerField(
                    value = draft,
                    // The backend takes up to 2000 characters, as on the web.
                    onValueChange = { draft = it.take(COMMENT_MAX); failed = false },
                    onSubmit = {
                        val text = draft.trim()
                        if (text.isNotEmpty() && !sending) {
                            sending = true
                            failed = false
                            onAdd(text) { ok ->
                                sending = false
                                if (ok) draft = "" else failed = true
                            }
                        }
                    },
                    submitIcon = PokyhIcons.send,
                    submitDescription = c.send,
                    placeholder = c.writeComment,
                )
                if (failed || sending || draft.length > COMMENT_MAX - 200) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            failed -> Text(c.commentFailed, style = PokyhType.caption, color = Brand.danger, modifier = Modifier.weight(1f))
                            sending -> Text(c.sending, style = PokyhType.caption, color = colors.textTertiary, modifier = Modifier.weight(1f))
                            else -> Box(Modifier.weight(1f))
                        }
                        if (draft.length > COMMENT_MAX - 200) {
                            Text("${draft.length}/$COMMENT_MAX", style = PokyhType.caption, color = if (draft.length >= COMMENT_MAX) Brand.danger else colors.textTertiary)
                        }
                    }
                }
            }
        }
    }
}

private const val COMMENT_MAX = 2000

/**
 * A report (Art. 16 Digital Services Act), as on the web: the mail program opens with the notice
 * prepared, to POKYH's contact point; without the address (offline), the imprint instead.
 */
private suspend fun reportComment(context: android.content.Context, comment: CommentUiItem, where: String) {
    val t = I18n.s.common
    val address = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val text = java.net.URL("https://pokyh.com/api/legal/contact").readText()
            org.json.JSONObject(text).optString("email").takeIf { Regex("^[^\\s@]+@[^\\s@]+$").matches(it) }
        }.getOrNull()
    }
    val date = java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
        .withLocale(I18n.lang.value.locale)
        .format(java.time.Instant.ofEpochMilli(comment.createdAtEpochMs).atZone(java.time.ZoneId.systemDefault()))
    val body = t.reportBody(where, comment.authorName, date, comment.id, if (comment.body.length > 400) comment.body.take(400) + "…" else comment.body)
    val intent = if (address != null) {
        android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).apply {
            putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(address))
            putExtra(android.content.Intent.EXTRA_SUBJECT, t.reportSubject)
            putExtra(android.content.Intent.EXTRA_TEXT, body)
        }
    } else {
        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://pokyh.com/legal/impressum"))
    }
    runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun CommentRow(
    comment: CommentUiItem,
    stars: Int?,
    mine: Boolean,
    canDelete: Boolean,
    isModeration: Boolean,
    editing: Boolean,
    canEdit: Boolean,
    canReport: Boolean,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: (String, (Boolean) -> Unit) -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val c = strings().common
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
                if (mine) {
                    val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.periwinkle(colors.isDark)
                    Text(
                        c.you,
                        style = PokyhType.caption2,
                        color = tone.ink,
                        modifier = Modifier.background(tone.fill, dev.plattnericus.pokyh.ui.theme.PokyhShapes.pill).padding(horizontal = 8.dp, vertical = 1.dp),
                    )
                }
                if (stars != null && stars > 0) CommentStars(stars)
                Text(
                    text = timeAgo(comment.createdAtEpochMs) +
                        (if (comment.editedAtEpochMs != null) " · " + c.edited else ""),
                    style = PokyhType.caption2,
                    color = colors.textTertiary,
                )
            }
            if (editing) {
                var body by remember(comment.id) { mutableStateOf(comment.body) }
                var busy by remember { mutableStateOf(false) }
                PokyhTextField(
                    value = body,
                    onValueChange = { body = it.take(COMMENT_MAX) },
                    singleLine = false,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                    PokyhTextButton(text = c.cancel, onClick = onCancelEdit)
                    PokyhTextButton(
                        text = c.save,
                        onClick = {
                            val text = body.trim()
                            if (text.isNotEmpty() && !busy) {
                                busy = true
                                onSaveEdit(text) { busy = false }
                            }
                        },
                    )
                }
            } else {
                Text(comment.body, style = PokyhType.callout, color = colors.textSecondary)
            }
        }
        if (!editing) Row {
            if (canEdit) {
                PokyhIconButton(icon = PokyhIcons.edit, contentDescription = c.editComment, onClick = onStartEdit, tint = colors.textTertiary, size = 32.dp, iconSize = 15.dp)
            }
            if (canReport) {
                PokyhIconButton(icon = PokyhIcons.report, contentDescription = c.reportComment, onClick = onReport, tint = colors.textTertiary, size = 32.dp, iconSize = 15.dp)
            }
            if (canDelete) {
                PokyhIconButton(
                    icon = PokyhIcons.delete,
                    contentDescription = c.delete,
                    onClick = onDelete,
                    tint = if (isModeration) Brand.warning else colors.textTertiary,
                    size = 32.dp,
                    iconSize = 16.dp,
                )
            }
        }
    }
}

/** The stars an author gave the dish, beside their name (the web's comment `Stars`). */
@Composable
private fun CommentStars(value: Int) {
    val colors = PokyhTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = I18n.s.common.ratedStars(value) },
    ) {
        for (s in 1..5) {
            androidx.compose.material3.Icon(
                imageVector = if (s <= value) PokyhIcons.starFilled else PokyhIcons.starEmpty,
                contentDescription = null,
                tint = if (s <= value) Brand.star else colors.textTertiary.copy(alpha = 0.4f),
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** "Gerade eben" / "vor N Min." / "vor N Std." / "vor N Tagen" (port of the web's `timeAgo()`). */
private fun timeAgo(epochMs: Long): String {
    val now = Clock.System.now().toEpochMilliseconds()
    val diff = (now - epochMs).milliseconds
    val c = I18n.s.common
    return when {
        diff.inWholeMinutes < 1 -> c.justNow
        diff.inWholeMinutes < 60 -> c.minutesAgo(diff.inWholeMinutes)
        diff.inWholeHours < 24 -> c.hoursAgo(diff.inWholeHours)
        else -> c.daysAgo(diff.inWholeDays)
    }
}
