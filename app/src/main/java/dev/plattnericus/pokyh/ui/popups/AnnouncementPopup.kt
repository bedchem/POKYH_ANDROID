package dev.plattnericus.pokyh.ui.popups

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.data.model.ApiPopup
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhPrimaryButton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhElevation
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.floatingSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Hosts admin announcement popups for the signed-in app. Checks on every return to the
 * foreground (throttled in the ViewModel) and shows everything due as one stack — but only
 * once the app has been signed in and on screen for [SETTLE_MS], so a popup never flashes past
 * while the sign-in check or a redirect is still settling.
 */
@Composable
fun AnnouncementPopupHost(viewModel: AnnouncementPopupViewModel = hiltViewModel()) {
    val stack by viewModel.stack.collectAsStateWithLifecycle()
    var settled by remember { mutableStateOf(false) }

    LifecycleStartEffect(Unit) {
        viewModel.check()
        onStopOrDispose { }
    }
    LaunchedEffect(Unit) {
        delay(SETTLE_MS)
        settled = true
    }

    if (!settled || stack.current == null) return

    AnnouncementPopupDialog(
        stack = stack,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onSelect = viewModel::goTo,
        onViewed = viewModel::markViewed,
        onClose = viewModel::close,
    )
}

private const val SETTLE_MS = 1500L

/** Every popup stays up at least this long before it can be closed or skipped. */
private const val MIN_VIEW_MS = 3000L

/** How far each waiting popup peeks out above the card. */
private val LayerPeek = 9.dp

/** Beyond this the dots stop being countable at a glance — "3 / 9" instead. */
private const val MAX_DOTS = 7

/**
 * Pastel, borderless card in the app's own language: a periwinkle icon tile, the title, the
 * backend-rendered content and a footer — "Zurück" on the left, one dot per popup in the
 * middle, "Weiter" (or "Fertig" on the last one) on the right. The popups still waiting peek
 * out above the card. Tone separates the layers — no outlines.
 *
 * Until a popup has been on screen for [MIN_VIEW_MS] a countdown ring sits where the X goes and
 * nothing closes or skips it — not X, back, tapping outside, "Weiter"/"Fertig" or a later dot.
 * Only time the app is actually in the foreground counts.
 */
@Composable
private fun AnnouncementPopupDialog(
    stack: PopupStack,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit,
    onViewed: (ApiPopup) -> Unit,
    onClose: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val tone = PokyhDecorative.periwinkle(colors.isDark)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    val waiting = stack.popups.size - 1 - stack.index
    val current = stack.current ?: return
    val locked = stack.locked
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var elapsed by remember(popupKey(current)) { mutableLongStateOf(0L) }
    LaunchedEffect(popupKey(current), locked) {
        if (!locked) return@LaunchedEffect
        var last = -1L
        while (elapsed < MIN_VIEW_MS) {
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                last = -1L
            }
            withFrameMillis { now ->
                // Capped so a stalled frame can't jump the countdown ahead.
                if (last >= 0) elapsed = (elapsed + (now - last).coerceAtMost(100L)).coerceAtMost(MIN_VIEW_MS)
                last = now
            }
        }
        onViewed(current)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !locked,
            dismissOnClickOutside = !locked,
        ),
    ) {
        Box(Modifier.padding(horizontal = PokyhSpacing.xl).fillMaxWidth()) {
            StackLayer(visible = waiting >= 2, inset = PokyhSpacing.huge, top = 0.dp, alpha = 0.55f)
            StackLayer(visible = waiting >= 1, inset = PokyhSpacing.xl, top = LayerPeek, alpha = 1f)

            Column(
                modifier = Modifier
                    .padding(top = LayerPeek * 2)
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
                    AnimatedContent(
                        targetState = stack.index,
                        transitionSpec = { stepTransition() },
                        modifier = Modifier.weight(1f),
                        label = "popupTitle",
                    ) { i ->
                        Text(stack.popups.getOrNull(i)?.title.orEmpty(), style = PokyhType.title2, color = colors.textPrimary)
                    }
                    Crossfade(targetState = locked, animationSpec = tween(PokyhMotion.durationFast), label = "popupClose") { isLocked ->
                        if (isLocked) {
                            CountdownRing(elapsedMs = elapsed, totalMs = MIN_VIEW_MS)
                        } else {
                            PokyhIconButton(
                                icon = PokyhIcons.close,
                                contentDescription = "Schließen",
                                onClick = onClose,
                                tint = colors.textSecondary,
                                tinted = true,
                                size = 36.dp,
                                iconSize = 18.dp,
                            )
                        }
                    }
                }

                AnimatedContent(
                    targetState = stack.index,
                    transitionSpec = { stepTransition() },
                    modifier = Modifier.weight(1f, fill = false),
                    label = "popupBody",
                ) { i ->
                    stack.popups.getOrNull(i)?.let { PopupHtml(html = it.html) }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Equal weights on both sides keep the dots exactly centred.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (stack.index > 0) {
                            PokyhTextButton(text = "Zurück", onClick = onBack, icon = PokyhIcons.chevronLeft, color = colors.textSecondary)
                        }
                    }
                    when {
                        stack.popups.size > MAX_DOTS -> Text(
                            "${stack.index + 1} / ${stack.popups.size}",
                            style = PokyhType.footnote,
                            color = colors.textSecondary,
                        )
                        stack.popups.size > 1 -> PopupDots(
                            count = stack.popups.size,
                            current = stack.index,
                            canSelect = stack::canReach,
                            onSelect = onSelect,
                        )
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        PokyhPrimaryButton(
                            text = if (stack.isLast) "Fertig" else "Weiter",
                            onClick = onNext,
                            enabled = !locked,
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

/** Next slides in from the right, back from the left; the outgoing popup just fades. */
private fun AnimatedContentTransitionScope<Int>.stepTransition(): ContentTransform {
    val direction = if (targetState > initialState) 1 else -1
    return (fadeIn(tween(PokyhMotion.durationStandard)) +
        slideInHorizontally(tween(PokyhMotion.durationStandard)) { width -> direction * width / 14 }) togetherWith
        fadeOut(tween(PokyhMotion.durationFast))
}

/** One of the waiting popups behind the card: narrower, in the card's quieter tone. */
@Composable
private fun BoxScope.StackLayer(visible: Boolean, inset: Dp, top: Dp, alpha: Float) {
    val shown by animateFloatAsState(
        targetValue = if (visible) alpha else 0f,
        animationSpec = tween(PokyhMotion.durationStandard),
        label = "stackLayer",
    )
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = top)
            .padding(horizontal = inset)
            .fillMaxWidth()
            .height(PokyhSpacing.huge)
            .alpha(shown)
            .background(PokyhTheme.colors.cardAlt, PokyhShapes.xl),
    )
}

/**
 * Where the X goes while a popup is locked: the seconds left inside a ring that drains with the
 * remaining time.
 */
@Composable
private fun CountdownRing(elapsedMs: Long, totalMs: Long) {
    val colors = PokyhTheme.colors
    val seconds = ((totalMs - elapsedMs + 999) / 1000).toInt().coerceAtLeast(1)
    val remaining = 1f - elapsedMs.toFloat() / totalMs
    val track = colors.textTertiary.copy(alpha = 0.25f)
    Box(
        Modifier
            .size(36.dp)
            .background(colors.cardAlt, CircleShape)
            .semantics { contentDescription = "Schließen in $seconds Sekunden möglich" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize().padding(3.dp)) {
            val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(Brand.accent, startAngle = -90f, sweepAngle = 360f * remaining, useCenter = false, style = stroke)
        }
        Text("$seconds", style = PokyhType.caption, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    }
}

/**
 * One dot per popup; the current one stretches into an accent pill. Tapping a dot jumps there
 * when [canSelect] allows it (not past a popup that is still counting down).
 */
@Composable
private fun PopupDots(count: Int, current: Int, canSelect: (Int) -> Boolean, onSelect: (Int) -> Unit) {
    val colors = PokyhTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(
                targetValue = if (active) 22.dp else 7.dp,
                animationSpec = tween(PokyhMotion.durationStandard),
                label = "popupDotWidth",
            )
            val color by animateColorAsState(
                targetValue = if (active) Brand.accent else colors.textTertiary,
                animationSpec = tween(PokyhMotion.durationStandard),
                label = "popupDotColor",
            )
            Box(
                Modifier
                    .height(PokyhSpacing.xxxl)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = active || canSelect(i),
                        role = Role.Tab,
                        onClick = { onSelect(i) },
                    )
                    .semantics {
                        contentDescription = "Mitteilung ${i + 1} von $count"
                        selected = active
                    }
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = width, height = 7.dp).background(color, PokyhShapes.pill))
            }
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
private fun PopupHtml(html: String, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val document = remember(html, colors.isDark) { popupDocument(html, colors.isDark) }
    // Not keyed on the document: the WebViewClient below captures this state once.
    val contentHeight = remember { mutableIntStateOf(0) }

    AndroidView(
        modifier = modifier
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
