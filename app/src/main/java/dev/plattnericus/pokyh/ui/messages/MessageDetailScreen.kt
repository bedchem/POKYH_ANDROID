@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.MessageAttachment
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.InitialAvatar
import dev.plattnericus.pokyh.ui.components.ArticleSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhSection
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.components.openOrShareFile
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.accentSurface
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import androidx.compose.ui.text.withLink
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton

/**
 * A single message. The subject is the screen's title (in the header, not repeated in the body),
 * the sender is a compact identity row, and the body sits in a card — so a long message reads as
 * a document on the page rather than as loose text between dividers.
 */
@Composable
fun MessageDetailScreen(
    id: Int,
    onNavigateBack: () -> Unit,
    /** Opened from the inbox: "Antworten" is offered (WebUntis still decides in its reply form). */
    canReply: Boolean = false,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val ui by viewModel.detail.collectAsStateWithLifecycle()
    var linkWarning by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val composeState by viewModel.compose.collectAsStateWithLifecycle()
    // Not opened from the inbox: a sent message (its read status, whether it can be taken back).
    LaunchedEffect(id) { viewModel.loadDetail(id, outgoing = !canReply) }
    var viewing by remember { mutableStateOf<dev.plattnericus.pokyh.data.untis.UntisClient.DownloadedAttachment?>(null) }
    var revokeArmed by remember { mutableStateOf(false) }
    val detail = ui.detail
    val context = LocalContext.current
    var noViewerFor by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = detail?.subject ?: strings().messages.message,
                eyebrow = strings().messages.message,
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
    ) { innerPadding ->
        when {
            ui.loading -> ArticleSkeleton(Modifier.fillMaxSize().padding(innerPadding))
            ui.error != null -> ErrorStateView(
                message = ui.error!!,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onRetry = { viewModel.loadDetail(id, outgoing = !canReply) },
            )
            detail != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = PokyhSpacing.screenH)
                    .padding(bottom = PokyhSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().fadeIn(),
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InitialAvatar(name = detail.senderName, size = 44.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
                        Text(
                            text = detail.senderName,
                            style = PokyhType.headline,
                            color = PokyhTheme.colors.textPrimary,
                        )
                        Text(
                            text = MessageFormat.fullDate(detail.sentDate),
                            style = PokyhType.footnote,
                            color = PokyhTheme.colors.textSecondary,
                        )
                    }
                }

                // Taken back: the recipients no longer see it.
                if (detail.isRevoked) {
                    val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(PokyhTheme.colors.isDark)
                    Text(
                        strings().messages.revokedNotice,
                        style = PokyhType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = tone.ink,
                        modifier = Modifier.fillMaxWidth().background(tone.fill, dev.plattnericus.pokyh.ui.theme.PokyhShapes.lg).padding(PokyhSpacing.card),
                    )
                }
                // A read confirmation the sender asked for (inbox).
                detail.confirmation?.let { rc ->
                    val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.butter(PokyhTheme.colors.isDark)
                    Column(
                        Modifier.fillMaxWidth().background(tone.fill, dev.plattnericus.pokyh.ui.theme.PokyhShapes.lg).padding(PokyhSpacing.card),
                        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
                    ) {
                        val when_ = rc.confirmedAt?.let { MessageFormat.fullDate(it) }
                        if (rc.canConfirm) {
                            Text(strings().messages.confirmTitle, style = PokyhType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = tone.ink)
                            Text(strings().messages.confirmText(detail.senderName), style = PokyhType.footnote, color = tone.ink)
                            PokyhPrimaryButton(text = strings().messages.confirmAction, loading = ui.confirming, enabled = !ui.confirming, onClick = viewModel::confirmRead)
                            if (ui.confirmFailed) Text(strings().messages.confirmFailed, style = PokyhType.footnote, color = Brand.danger)
                        } else if (when_ != null) {
                            Text(
                                rc.confirmedBy?.let { strings().messages.confirmDoneBy(it, when_) } ?: strings().messages.confirmDone(when_),
                                style = PokyhType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                                color = tone.ink,
                            )
                        }
                    }
                }
                // How many recipients confirmed reading (sent), with who.
                val total = detail.readTotal ?: 0
                if (detail.outgoing && total > 0) {
                    Row(
                        Modifier.fillMaxWidth().background(PokyhTheme.colors.card, dev.plattnericus.pokyh.ui.theme.PokyhShapes.lg).padding(PokyhSpacing.card),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(strings().messages.readStatus(detail.readConfirmed ?: 0, total), style = PokyhType.callout, color = PokyhTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        PokyhTextButton(text = strings().messages.readStatusOpen, onClick = viewModel::openReadStatus)
                    }
                }

                PokyhCard(modifier = Modifier.fadeIn(delayMillis = 40)) {
                    SelectionContainer {
                        LinkedBody(detail.body, onLink = { linkWarning = it })
                    }
                }

                if (canReply) {
                    PokyhSecondaryButton(
                        text = strings().messages.reply,
                        icon = PokyhIcons.sendMessage,
                        onClick = { viewModel.openReply(detail.id) },
                        modifier = Modifier.fillMaxWidth().fadeIn(delayMillis = 60),
                    )
                }

                // Delete, after asking, as on the web.
                dev.plattnericus.pokyh.ui.components.PokyhTextButton(
                    text = strings().messages.delete,
                    icon = PokyhIcons.delete,
                    color = Brand.danger,
                    onClick = { confirmDelete = true },
                )
                if (ui.deleteFailed) Text(strings().messages.deleteFailed, style = PokyhType.footnote, color = Brand.danger)

                // A sent message can be taken back where WebUntis allows it (asks once more first).
                if (detail.canRevoke) {
                    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
                        dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton(
                            text = if (revokeArmed) strings().messages.revokeConfirm else strings().messages.revoke,
                            icon = PokyhIcons.close,
                            loading = ui.revoking,
                            enabled = !ui.revoking,
                            onClick = { if (revokeArmed) { revokeArmed = false; viewModel.revoke() } else revokeArmed = true },
                        )
                        Text(strings().messages.revokeHint, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
                        if (ui.revokeFailed) Text(strings().messages.revokeFailed, style = PokyhType.footnote, color = Brand.danger)
                    }
                }

                // Said to have files, but none came back: try again (web "tap to reload").
                if (detail.hasAttachments && detail.attachments.isEmpty() && !ui.attachmentsLoading) {
                    PokyhSection(title = strings().messages.attachments) {
                        Text(
                            strings().messages.attachmentsError + " · " + strings().messages.tapToReload,
                            style = PokyhType.callout,
                            color = PokyhTheme.colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().background(PokyhTheme.colors.card, dev.plattnericus.pokyh.ui.theme.PokyhShapes.lg)
                                .clickable(onClick = viewModel::reloadAttachments).padding(PokyhSpacing.card),
                        )
                    }
                }

                if (detail.attachments.isNotEmpty() || ui.attachmentsLoading) {
                    PokyhSection(
                        title = strings().messages.attachments,
                        modifier = Modifier.fadeIn(delayMillis = 80),
                    ) {
                        if (detail.attachments.isEmpty()) {
                            Text(
                                text = strings().messages.attachmentsLoading,
                                style = PokyhType.footnote,
                                color = PokyhTheme.colors.textTertiary,
                            )
                        } else {
                            PokyhListCard(items = detail.attachments) { attachment ->
                                AttachmentRow(
                                    attachment = attachment,
                                    downloading = ui.downloadingKey == viewModel.attachmentKey(attachment),
                                    enabled = ui.downloadingKey == null,
                                    onClick = {
                                        noViewerFor = null
                                        viewModel.downloadAttachment(attachment) { file ->
                                            // PDFs and pictures open in the app, as on the web.
                                            if (canViewInApp(file)) {
                                                viewing = file
                                                return@downloadAttachment
                                            }
                                            val opened = openOrShareFile(
                                                context = context,
                                                fileName = file.name,
                                                mimeType = file.mimeType,
                                                bytes = file.bytes,
                                            )
                                            if (!opened) noViewerFor = file.name
                                        }
                                    },
                                )
                            }
                        }
                        val problem = ui.downloadError
                            ?: noViewerFor?.let { strings().messages.noAppFor(it) }
                        if (problem != null) {
                            Text(
                                text = problem,
                                style = PokyhType.footnote,
                                color = Brand.danger,
                                modifier = Modifier.padding(top = PokyhSpacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }

    linkWarning?.let { url ->
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { linkWarning = null },
            containerColor = PokyhTheme.colors.card,
            icon = { Icon(PokyhIcons.warningSign, contentDescription = null, tint = Brand.warning) },
            title = { Text(strings().messages.externalLink, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                    Text(strings().messages.leavingApp, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
                    Text(url, style = PokyhType.footnote, color = PokyhTheme.colors.textPrimary)
                }
            },
            confirmButton = {
                PokyhTextButton(text = strings().messages.openLink, onClick = { linkWarning = null; runCatching { uriHandler.openUri(url) } })
            },
            dismissButton = {
                PokyhTextButton(text = strings().common.cancel, color = PokyhTheme.colors.textSecondary, onClick = { linkWarning = null })
            },
        )
    }

    viewing?.let { file ->
        AttachmentViewer(
            file = file,
            onOpenExternally = {
                if (!openOrShareFile(context = context, fileName = file.name, mimeType = file.mimeType, bytes = file.bytes)) noViewerFor = file.name
            },
            onDismiss = { viewing = null },
        )
    }

    ui.readStatus?.let { list ->
        ReadStatusSheet(list, loading = ui.readStatusLoading, failed = ui.readStatusFailed, onDismiss = viewModel::closeReadStatus)
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = PokyhTheme.colors.card,
            title = { Text(strings().messages.deleteConfirm(1), style = PokyhType.headline, color = PokyhTheme.colors.textPrimary) },
            confirmButton = {
                PokyhTextButton(text = strings().messages.delete, color = Brand.danger, onClick = {
                    confirmDelete = false
                    ui.detail?.let { d -> viewModel.deleteMessage(d.id, onNavigateBack) }
                })
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
 * An attachment as a downloadable row: file-kind glyph, name, size, and a download affordance —
 * the old row was a plain, un-tappable line, so a PDF in a message had no way out of the app.
 * Tapping fetches the bytes and hands them to whatever app opens that type.
 */
@Composable
private fun AttachmentRow(
    attachment: MessageAttachment,
    downloading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = PokyhTheme.colors
    PokyhRow(
        title = attachment.name,
        subtitle = formatFileSize(attachment.size).ifEmpty { null },
        showChevron = false,
        enabled = enabled || downloading,
        onClick = onClick,
        leading = { FileGlyph(attachment.name) },
        trailing = {
            if (downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Brand.accent,
                    strokeWidth = 2.dp,
                )
            } else {
                Box(
                    modifier = Modifier.size(32.dp).accentSurface(PokyhShapes.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PokyhIcons.download,
                        contentDescription = strings().messages.download,
                        tint = colors.accentText,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
    )
}

private val UrlRegex = Regex("(https?://[^\\s<>\"{}|\\\\^`\\[\\]]+)|(www\\.[^\\s<>\"]+)")

/** The message text with its links tappable (web: linkified, opened after a warning). */
@Composable
private fun LinkedBody(raw: String, onLink: (String) -> Unit) {
    val accent = PokyhTheme.colors.accentText
    val text = androidx.compose.runtime.remember(raw) {
        // An HTML link keeps its address: "text (address)" unless the text is the address.
        val withHrefs = Regex("<a\\s[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .replace(raw) { m -> val href = m.groupValues[1]; val label = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim(); if (label.isEmpty() || label == href) href else "$label ($href)" }
        MessageFormat.plainText(withHrefs)
    }
    val annotated = androidx.compose.runtime.remember(text, accent) {
        androidx.compose.ui.text.buildAnnotatedString {
            var last = 0
            UrlRegex.findAll(text).forEach { m ->
                val url = m.value.trimEnd('.', ',', ';', ':', ')', '!', '?')
                val end = m.range.first + url.length
                append(text.substring(last, m.range.first))
                val target = if (url.startsWith("www.")) "https://$url" else url
                withLink(
                    androidx.compose.ui.text.LinkAnnotation.Clickable(
                        tag = target,
                        styles = androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = accent, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)),
                        linkInteractionListener = { onLink(target) },
                    ),
                ) { append(url) }
                last = end
            }
            append(text.substring(last))
        }
    }
    Text(text = annotated, style = PokyhType.body, color = PokyhTheme.colors.textPrimary, modifier = Modifier.fillMaxWidth())
}

/** Who of the recipients confirmed reading (a class: per person). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReadStatusSheet(list: List<dev.plattnericus.pokyh.data.model.ReadStatusRecipient>, loading: Boolean, failed: Boolean, onDismiss: () -> Unit) {
    val t = strings().messages
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PokyhTheme.colors.card,
        shape = dev.plattnericus.pokyh.ui.theme.PokyhShapes.topXxl,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.screenH).padding(bottom = PokyhSpacing.xxl), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Text(t.readStatusTitle, style = PokyhType.title2, color = PokyhTheme.colors.textPrimary)
            when {
                loading -> androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = PokyhTheme.colors.textTertiary)
                failed -> Text(t.readStatusFailed, style = PokyhType.callout, color = Brand.danger)
                else -> androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(list.flatMap { r -> if (r.people.size > 1) r.people.map { (n, at) -> Triple(n, r.className.ifEmpty { r.name }, at) } else listOf(Triple(r.name, r.className, r.confirmedAt)) }) { (name, group, at) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText)) {
                            InitialAvatar(name = name, size = 34.dp)
                            Column(Modifier.weight(1f)) {
                                Text(name, style = PokyhType.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = PokyhTheme.colors.textPrimary)
                                if (group.isNotBlank() && group != name) Text(group, style = PokyhType.caption, color = PokyhTheme.colors.textTertiary)
                            }
                            Text(
                                at?.let { t.readConfirmedOn(MessageFormat.fullDate(it)) } ?: t.readPending,
                                style = PokyhType.caption,
                                color = if (at != null) Brand.success else PokyhTheme.colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}
