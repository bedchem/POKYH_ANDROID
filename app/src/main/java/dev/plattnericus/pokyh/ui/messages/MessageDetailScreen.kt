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
import dev.plattnericus.pokyh.ui.components.LoadingStateView
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhRow
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

/**
 * A single message. The subject is the screen's title (in the header, not repeated in the body),
 * the sender is a compact identity row, and the body sits in a card — so a long message reads as
 * a document on the page rather than as loose text between dividers.
 */
@Composable
fun MessageDetailScreen(
    id: Int,
    onNavigateBack: () -> Unit,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val ui by viewModel.detail.collectAsStateWithLifecycle()
    LaunchedEffect(id) { viewModel.loadDetail(id) }
    val detail = ui.detail
    val context = LocalContext.current
    var noViewerFor by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = detail?.subject ?: "Nachricht",
                eyebrow = "Nachricht",
                nav = TopBarNav.Back(onNavigateBack),
            )
        },
    ) { innerPadding ->
        when {
            ui.loading -> LoadingStateView(Modifier.fillMaxSize().padding(innerPadding))
            ui.error != null -> ErrorStateView(
                message = ui.error!!,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onRetry = { viewModel.loadDetail(id) },
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

                PokyhCard(modifier = Modifier.fadeIn(delayMillis = 40)) {
                    SelectionContainer {
                        Text(
                            text = MessageFormat.plainText(detail.body),
                            style = PokyhType.body,
                            color = PokyhTheme.colors.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (detail.attachments.isNotEmpty() || ui.attachmentsLoading) {
                    PokyhSection(
                        title = "Anhänge",
                        modifier = Modifier.fadeIn(delayMillis = 80),
                    ) {
                        if (detail.attachments.isEmpty()) {
                            Text(
                                text = "Anhänge werden geladen …",
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
                            ?: noViewerFor?.let { "Keine App gefunden, die „$it“ öffnen kann." }
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
                        contentDescription = "Herunterladen",
                        tint = colors.accentText,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
    )
}
