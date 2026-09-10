@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.messages
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.MessageAttachment
import dev.plattnericus.pokyh.ui.components.InitialAvatar
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground

/** MessageDetailScreen (MessagesView.swift), ported. */
@Composable
fun MessageDetailScreen(
    id: Int,
    onNavigateBack: () -> Unit,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val ui by viewModel.detail.collectAsStateWithLifecycle()

    LaunchedEffect(id) { viewModel.loadDetail(id) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            TopAppBar(
                title = { Text("Nachricht", style = PokyhType.headline) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(PokyhIcons.arrow_left, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PokyhTheme.colors.bg,
                    titleContentColor = PokyhTheme.colors.textPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                ui.loading -> CircularProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    color = Brand.accent,
                )
                ui.error != null -> Text(ui.error!!, style = PokyhType.body, color = PokyhTheme.colors.textSecondary)
                ui.detail != null -> {
                    val d = ui.detail!!
                    Text(d.subject, style = PokyhType.title2.copy(fontWeight = FontWeight.Bold), color = PokyhTheme.colors.textPrimary)

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(name = d.senderName, size = 44.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(d.senderName, style = PokyhType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = PokyhTheme.colors.textPrimary)
                            Text(MessageFormat.fullDate(d.sentDate), style = PokyhType.caption, color = PokyhTheme.colors.textSecondary)
                        }
                    }

                    HorizontalDivider(color = PokyhTheme.colors.separator)

                    SelectionContainer {
                        Text(
                            text = MessageFormat.plainText(d.body),
                            style = PokyhType.body,
                            color = PokyhTheme.colors.textPrimary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (d.attachments.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Anhänge",
                                style = PokyhType.caption.copy(fontWeight = FontWeight.Bold),
                                color = PokyhTheme.colors.textSecondary,
                            )
                            d.attachments.forEach { att -> AttachmentRow(att) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(attachment: MessageAttachment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PokyhTheme.colors.cardAlt, PokyhShapes.r10)
            .clickable {
                // TODO(attachments): implement download via WebUntis attachment endpoint
            }
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PokyhIcons.doc_fill, contentDescription = null, tint = PokyhTheme.colors.textSecondary)
        Text(attachment.name, style = PokyhType.subheadline, color = PokyhTheme.colors.textPrimary)
    }
}
