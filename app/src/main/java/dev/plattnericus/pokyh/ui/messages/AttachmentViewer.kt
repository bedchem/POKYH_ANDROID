package dev.plattnericus.pokyh.ui.messages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Whether the viewer below can show this file itself (the web's in-app viewer: PDFs and images). */
internal fun canViewInApp(file: UntisClient.DownloadedAttachment): Boolean =
    file.mimeType.startsWith("image/") || file.mimeType == "application/pdf" ||
        file.name.endsWith(".pdf", ignoreCase = true) ||
        listOf(".png", ".jpg", ".jpeg", ".gif", ".webp").any { file.name.endsWith(it, ignoreCase = true) }

/**
 * An attachment shown in the app, as the web does for PDFs and pictures: pages one under the
 * other (pinch to zoom), with "open in another app" for saving or printing.
 */
@Composable
internal fun AttachmentViewer(file: UntisClient.DownloadedAttachment, onOpenExternally: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pdf = file.mimeType == "application/pdf" || file.name.endsWith(".pdf", ignoreCase = true)
    val pages by produceState<List<Bitmap>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (pdf) {
                    val tmp = java.io.File(context.cacheDir, "viewer.pdf").apply { writeBytes(file.bytes) }
                    ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            (0 until minOf(renderer.pageCount, 60)).map { i ->
                                renderer.openPage(i).use { page ->
                                    val width = 1400
                                    val bmp = Bitmap.createBitmap(width, (width.toFloat() * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(android.graphics.Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bmp
                                }
                            }
                        }
                    }
                } else {
                    listOfNotNull(BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size))
                }
            }.getOrDefault(emptyList())
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(PokyhTheme.colors.bg)) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = PokyhSpacing.sm, vertical = PokyhSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                PokyhIconButton(icon = PokyhIcons.close, contentDescription = strings().common.close, onClick = onDismiss)
                Text(file.name, style = PokyhType.headline, color = PokyhTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                PokyhTextButton(text = strings().messages.openExternally, onClick = onOpenExternally)
            }
            val list = pages
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = PokyhTheme.colors.textTertiary)
                }
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PokyhTextButton(text = strings().messages.openExternally, onClick = onOpenExternally)
                }
                else -> LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                offsetX = if (scale == 1f) 0f else (offsetX + pan.x).coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2)
                            }
                        }
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX),
                    contentPadding = PaddingValues(PokyhSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    items(list) { bmp ->
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height).background(Color.White),
                        )
                    }
                }
            }
        }
    }
}
