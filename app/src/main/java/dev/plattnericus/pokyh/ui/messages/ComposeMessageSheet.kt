@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.messages

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.data.model.MessageRecipient
import dev.plattnericus.pokyh.data.model.OutgoingAttachment
import dev.plattnericus.pokyh.ui.components.InitialAvatar
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.accentSurface
import dev.plattnericus.pokyh.ui.theme.insetSurface
import dev.plattnericus.pokyh.ui.theme.nestedSurface
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import kotlinx.coroutines.launch

/** Files bigger than this aren't read into memory — WebUntis' own per-file cap is lower still. */
private const val MaxAttachmentBytes = 20L * 1024 * 1024

/**
 * "Mitteilung an Lehrkraft" — the web frontend's compose sheet, as a bottom sheet.
 *
 * Recipients live on a second *view* inside the same sheet rather than in a sheet on top of a
 * sheet: picking a teacher is a step of writing the message, not a separate surface, and
 * stacking two scrims would dim the app twice.
 */
@Composable
fun ComposeMessageSheet(
    state: MessagesViewModel.ComposeUiState,
    viewModel: MessagesViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var pickingRecipients by remember { mutableStateOf(false) }

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // Sent / saved shows its confirmation for a beat before the sheet leaves.
    LaunchedEffect(state.sent, state.draftSaved) {
        if (state.sent || state.draftSaved) {
            kotlinx.coroutines.delay(900)
            dismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        containerColor = PokyhTheme.colors.card,
        shape = PokyhShapes.topXxl,
    ) {
        if (pickingRecipients) {
            RecipientPicker(
                recipients = state.recipients,
                selected = state.selected,
                failed = state.recipientsFailed,
                onToggle = viewModel::toggleRecipient,
                onDone = { pickingRecipients = false },
            )
        } else {
            ComposeForm(
                state = state,
                viewModel = viewModel,
                onPickRecipients = { pickingRecipients = true },
                onClose = ::dismiss,
            )
        }
    }
}

@Composable
private fun ComposeForm(
    state: MessagesViewModel.ComposeUiState,
    viewModel: MessagesViewModel,
    onPickRecipients: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val read = uris.mapNotNull { readAttachment(context, it) }
        if (read.size < uris.size) viewModel.fileError("Mindestens eine Datei konnte nicht gelesen werden.")
        viewModel.addFiles(read)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PokyhIconButton(
                icon = PokyhIcons.close,
                contentDescription = "Schließen",
                onClick = onClose,
                tinted = true,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Text(
                text = "Mitteilung an Lehrkraft",
                style = PokyhType.headline,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(horizontal = PokyhSpacing.md),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp).padding(PokyhSpacing.sm),
                    color = Brand.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                PokyhIconButton(
                    icon = if (state.sent) PokyhIcons.check else PokyhIcons.sendMessage,
                    contentDescription = "Senden",
                    onClick = viewModel::send,
                    tint = Brand.onAccent,
                    containerColor = if (state.sent) Brand.success else Brand.accent,
                    enabled = !state.busy,
                    size = 36.dp,
                    iconSize = 17.dp,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap)) {
            SheetField(
                label = "An",
                value = when {
                    state.selected.isEmpty() && state.recipientsFailed -> "Nicht verfügbar"
                    state.selected.isEmpty() -> "Wählen"
                    state.selected.size == 1 -> state.selected.first().name
                    else -> "${state.selected.size} Empfänger"
                },
                placeholder = state.selected.isEmpty(),
                onClick = onPickRecipients,
            )

            PokyhTextField(
                value = state.subject,
                onValueChange = viewModel::setSubject,
                placeholder = "Betreff",
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            PokyhTextField(
                value = state.content,
                onValueChange = viewModel::setContent,
                placeholder = "Text hier eingeben",
                singleLine = false,
                minLines = 6,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.files.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap)) {
                state.files.forEachIndexed { index, file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .nestedSurface(PokyhShapes.lg)
                            .padding(PokyhSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileGlyph(file.name)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = PokyhType.subheadline,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatFileSize(file.bytes.size),
                                style = PokyhType.caption,
                                color = colors.textTertiary,
                            )
                        }
                        PokyhIconButton(
                            icon = PokyhIcons.close,
                            contentDescription = "${file.name} entfernen",
                            onClick = { viewModel.removeFile(index) },
                            enabled = !state.busy,
                            tint = colors.textSecondary,
                            size = 32.dp,
                            iconSize = 15.dp,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap)) {
            PokyhSecondaryButton(
                text = "Anhang hinzufügen",
                icon = PokyhIcons.attachment,
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !state.busy,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PokyhSecondaryButton(
                text = if (state.draftSaved) "Entwurf gespeichert" else "Als Entwurf speichern",
                icon = if (state.draftSaved) PokyhIcons.check else PokyhIcons.compose,
                onClick = viewModel::saveDraft,
                loading = state.savingDraft,
                enabled = !state.busy,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.recipientsFailed) {
            Text(
                text = "Empfänger konnten gerade nicht geladen werden.",
                style = PokyhType.footnote,
                color = Brand.warning,
            )
        }
        state.error?.let { message -> SheetBanner(text = message, color = Brand.danger) }
        if (state.sent) SheetBanner(text = "Nachricht gesendet.", color = Brand.success)
    }
}

/** The recipient list, grouped exactly as the API groups it: class teachers first, then the rest. */
@Composable
private fun RecipientPicker(
    recipients: List<MessageRecipient>,
    selected: List<MessageRecipient>,
    failed: Boolean,
    onToggle: (MessageRecipient) -> Unit,
    onDone: () -> Unit,
) {
    val colors = PokyhTheme.colors
    var query by remember { mutableStateOf("") }
    val matching = recipients.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val selectedKeys = selected.map { "${it.type}:${it.id}" }.toSet()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PokyhIconButton(
                icon = PokyhIcons.back,
                contentDescription = "Zurück",
                onClick = onDone,
                tinted = true,
                size = 36.dp,
                iconSize = 18.dp,
            )
            Text(
                text = "Empfänger",
                style = PokyhType.headline,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f).padding(horizontal = PokyhSpacing.md),
            )
            PokyhTextButton(
                text = if (selected.isEmpty()) "Fertig" else "Fertig (${selected.size})",
                onClick = onDone,
            )
        }

        PokyhTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Lehrkraft suchen…",
            leadingIcon = PokyhIcons.search,
            modifier = Modifier.fillMaxWidth(),
        )

        if (recipients.isEmpty()) {
            Text(
                text = if (failed) {
                    "Empfänger konnten gerade nicht geladen werden. Bitte versuche es später erneut."
                } else {
                    "Empfänger werden geladen …"
                },
                style = PokyhType.footnote,
                color = colors.textTertiary,
                modifier = Modifier.fillMaxWidth().padding(vertical = PokyhSpacing.xl),
            )
        } else {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
            ) {
                RecipientGroup(
                    title = "Klassenlehrkraft",
                    items = matching.filter { it.isClassTeacher },
                    selectedKeys = selectedKeys,
                    onToggle = onToggle,
                )
                RecipientGroup(
                    title = "Andere",
                    items = matching.filterNot { it.isClassTeacher },
                    selectedKeys = selectedKeys,
                    onToggle = onToggle,
                )
            }
        }
    }
}

@Composable
private fun RecipientGroup(
    title: String,
    items: List<MessageRecipient>,
    selectedKeys: Set<String>,
    onToggle: (MessageRecipient) -> Unit,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.rowGap)) {
        PokyhLabel(title)
        items.forEach { recipient ->
            val isSelected = "${recipient.type}:${recipient.id}" in selectedKeys
            val interactionSource = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isSelected) Modifier.accentSurface(PokyhShapes.lg) else Modifier)
                    .pressHighlight(interactionSource, PokyhShapes.lg)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onToggle(recipient) },
                    )
                    .padding(horizontal = PokyhSpacing.md, vertical = PokyhSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialAvatar(name = recipient.name, size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = recipient.name,
                        style = if (isSelected) PokyhType.body.semibold() else PokyhType.body,
                        color = PokyhTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    recipient.role?.let {
                        Text(
                            text = it,
                            style = PokyhType.caption,
                            color = PokyhTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        imageVector = PokyhIcons.ok,
                        contentDescription = "Ausgewählt",
                        tint = Brand.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** A tappable "label → value ›" row on the sheet's own surface (no card inside a sheet). */
@Composable
private fun SheetField(label: String, value: String, placeholder: Boolean, onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    Box(Modifier.fillMaxWidth().insetSurface(PokyhShapes.lg)) {
        PokyhRow(
            title = label,
            trailing = {
                Text(
                    text = value,
                    style = PokyhType.subheadline,
                    color = if (placeholder) colors.textTertiary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            onClick = onClick,
        )
    }
}

@Composable
private fun SheetBanner(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = PokyhType.footnote,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .nestedSurface(PokyhShapes.lg)
            .padding(PokyhSpacing.md),
    )
}

@Composable
internal fun FileGlyph(name: String, size: androidx.compose.ui.unit.Dp = 38.dp) {
    Box(
        modifier = Modifier.size(size).accentSurface(PokyhShapes.md),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = fileIcon(name),
            contentDescription = null,
            tint = PokyhTheme.colors.accentText,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/** PDFs and images get their own glyph — the two attachment kinds a school actually sends. */
internal fun fileIcon(name: String): ImageVector = when (name.substringAfterLast('.', "").lowercase()) {
    "pdf" -> PokyhIcons.filePdf
    "png", "jpg", "jpeg", "gif", "webp", "heic", "bmp" -> PokyhIcons.fileImage
    "doc", "docx", "odt", "rtf", "txt", "csv", "xls", "xlsx", "ppt", "pptx" -> PokyhIcons.document
    else -> PokyhIcons.file
}

internal fun formatFileSize(bytes: Int): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes + 1023) / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/** Reads a picked document into memory, with its display name — null if it can't be read. */
private fun readAttachment(context: Context, uri: Uri): OutgoingAttachment? = runCatching {
    val resolver = context.contentResolver
    var name = "Anhang"
    var size = -1L
    resolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { name = cursor.getString(it) }
            cursor.getColumnIndex(OpenableColumns.SIZE)
                .takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { size = cursor.getLong(it) }
        }
    }
    if (size > MaxAttachmentBytes) return null
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > MaxAttachmentBytes) return null
    OutgoingAttachment(
        name = name,
        mimeType = resolver.getType(uri) ?: "application/octet-stream",
        bytes = bytes,
    )
}.getOrNull()
