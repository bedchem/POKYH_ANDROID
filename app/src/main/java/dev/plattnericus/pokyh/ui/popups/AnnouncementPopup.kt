package dev.plattnericus.pokyh.ui.popups

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.ApiPopup
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhElevation
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.floatingSurface

/**
 * Hosts admin announcement popups for the signed-in app. Checks on every return to the
 * foreground (throttled in the ViewModel) and shows the queue one popup at a time.
 */
@Composable
fun AnnouncementPopupHost(viewModel: AnnouncementPopupViewModel = hiltViewModel()) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()

    LifecycleStartEffect(Unit) {
        viewModel.check()
        onStopOrDispose { }
    }

    val current = queue.firstOrNull() ?: return
    LaunchedEffect(current.id, current.revision) { viewModel.markShown(current) }

    AnnouncementPopupDialog(
        popup = current,
        remaining = queue.size - 1,
        onDismiss = viewModel::dismissCurrent,
    )
}

/**
 * Pastel, borderless card in the app's own language: a periwinkle icon tile, the title, the
 * backend-rendered content and one primary action. Tone separates the layers — no outlines.
 */
@Composable
private fun AnnouncementPopupDialog(popup: ApiPopup, remaining: Int, onDismiss: () -> Unit) {
    val colors = PokyhTheme.colors
    val tone = PokyhDecorative.periwinkle(colors.isDark)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = PokyhSpacing.xl)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .floatingSurface(shape = PokyhShapes.xxl, elevation = PokyhElevation.level4)
                .padding(PokyhSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
            ) {
                Box(
                    Modifier.size(44.dp).background(tone.fill, PokyhShapes.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Campaign, contentDescription = null, tint = tone.ink, modifier = Modifier.size(22.dp))
                }
                Text(popup.title, style = PokyhType.title2, color = colors.textPrimary)
            }

            PopupHtml(html = popup.html)

            PokyhPrimaryButton(
                text = if (remaining > 0) "Weiter ($remaining)" else "Verstanden",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The content in a WebView — Markdown/HTML with tables, colors and images is far more faithful
 * there than re-implemented in Compose. JavaScript stays off (the HTML is sanitised server-side
 * anyway); links open in the browser. The view sizes itself to its content and scrolls
 * internally once the card reaches its maximum height.
 */
@Composable
private fun ColumnScope.PopupHtml(html: String) {
    val colors = PokyhTheme.colors
    val document = remember(html, colors.isDark) { popupDocument(html, colors.isDark) }
    // Not keyed on the document: the WebViewClient below captures this state once.
    val contentHeight = remember { mutableIntStateOf(0) }

    AndroidView(
        modifier = Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .height(if (contentHeight.intValue > 0) contentHeight.intValue.dp else 120.dp),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        try {
                            view.context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(request.url.toString()))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (_: ActivityNotFoundException) { }
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        // contentHeight is in CSS px == dp. Images may still be decoding, so
                        // re-measure a few times as they arrive.
                        for (delay in longArrayOf(0, 250, 800, 2000)) {
                            view.postDelayed({
                                if (view.contentHeight > 0) contentHeight.intValue = view.contentHeight
                            }, delay)
                        }
                    }
                }
            }
        },
        update = { webView ->
            if (webView.tag != document) {
                webView.tag = document
                contentHeight.intValue = 0
                webView.loadDataWithBaseURL("https://api.pokyh.com/", document, "text/html", "utf-8", null)
            }
        },
    )
}

/** Mirrors the "Handy" preview in the admin panel (admin/src/components/popups/PopupPreview.tsx). */
private fun popupDocument(body: String, isDark: Boolean): String {
    val t = if (isDark) {
        mapOf(
            "text" to "#F2F2F4", "link" to "#BAB8F4", "accent" to "#6366F1", "nested" to "#232323",
            "cardAlt" to "#26262B", "sep" to "#232327", "peri" to "#24244C", "periInk" to "#BAB8F4",
            "butter" to "#473B16", "butterInk" to "#EBD79A", "sage" to "#23351E", "sageInk" to "#B4D0AD",
        )
    } else {
        mapOf(
            "text" to "#1C1A17", "link" to "#4F46E5", "accent" to "#6366F1", "nested" to "#ECECEB",
            "cardAlt" to "#EEEAE3", "sep" to "#F0ECE5", "peri" to "#DEDDFB", "periInk" to "#4A46A6",
            "butter" to "#F7E7AC", "butterInk" to "#85641A", "sage" to "#D5E4D0", "sageInk" to "#4B6E45",
        )
    }
    val css = """
        html,body{margin:0;padding:0;background:transparent}
        body{font-family:Roboto,system-ui,sans-serif;font-size:15px;line-height:1.55;color:${t["text"]};overflow-wrap:anywhere;-webkit-text-size-adjust:100%}
        body>:first-child{margin-top:0}body>:last-child{margin-bottom:0}
        h1{font-size:22px;line-height:1.25;font-weight:800;letter-spacing:-.02em;margin:18px 0 8px}
        h2{font-size:19px;line-height:1.3;font-weight:700;letter-spacing:-.01em;margin:16px 0 6px}
        h3{font-size:16px;font-weight:600;margin:14px 0 4px}
        p{margin:0 0 10px}
        a{color:${t["link"]};font-weight:600;text-decoration:none}
        img{max-width:100%;height:auto;border-radius:18px;display:block;margin:10px 0}
        blockquote{margin:10px 0;padding:10px 14px;border-radius:16px;background:${t["butter"]};color:${t["butterInk"]}}
        blockquote p{margin:0}
        code{background:${t["sage"]};color:${t["sageInk"]};padding:1px 6px;border-radius:7px;font-size:.88em}
        pre{background:${t["nested"]};padding:12px 14px;border-radius:16px;overflow-x:auto}
        pre code{background:none;color:inherit;padding:0}
        ul,ol{padding-left:22px;margin:0 0 10px}
        li{margin:3px 0}
        li:has(>input[type=checkbox]){list-style:none;margin-left:-20px}
        input[type=checkbox]{accent-color:${t["accent"]};margin:0 6px 0 0;vertical-align:-2px}
        hr{border:0;height:2px;border-radius:2px;background:${t["sep"]};margin:14px 0}
        table{border-collapse:separate;border-spacing:0;width:100%;border-radius:16px;overflow:hidden;margin:10px 0;font-size:14px}
        th{background:${t["peri"]};color:${t["periInk"]};font-weight:700;text-align:left;padding:8px 12px}
        td{background:${t["nested"]};padding:8px 12px}
        tr:nth-child(even) td{background:${t["cardAlt"]}}
        mark{color:#1C1A17;border-radius:5px;padding:0 4px}
        details{background:${t["nested"]};border-radius:16px;padding:10px 14px;margin:10px 0}
        summary{font-weight:600}
    """.trimIndent()
    return """<!doctype html><html><head><meta charset="utf-8">""" +
        """<meta name="viewport" content="width=device-width,initial-scale=1">""" +
        "<style>$css</style></head><body>$body</body></html>"
}
