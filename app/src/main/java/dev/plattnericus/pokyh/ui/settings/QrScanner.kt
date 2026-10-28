package dev.plattnericus.pokyh.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.WebButton
import dev.plattnericus.pokyh.ui.components.WebButtonVariant
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhType

/**
 * The QR scanner, in the app: the camera full screen, dimmed around a square where the code goes,
 * the prompt under it and a way out. ZXing reads the picture on the phone itself, so it works the
 * same with or without Google's services (Huawei and Honor phones have none) — Google's own
 * scanner opened a screen of its own and, in the release build, took the app down instead.
 * Asks for the camera the first time; refused, it says so and leads to the settings.
 *
 * [onResult] gets the text of the first QR code read, or null when closed without one.
 */
@Composable
internal fun QrScannerDialog(onResult: (String?) -> Unit) {
    val context = LocalContext.current
    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var allowed by remember { mutableStateOf(granted()) }
    var refused by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        allowed = ok
        refused = !ok
    }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }

    Dialog(
        onDismissRequest = { onResult(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (allowed) {
                CameraReader(onCode = { onResult(it) })
                Viewfinder()
            }
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PokyhIconButton(
                        icon = PokyhIcons.close,
                        contentDescription = strings().common.close,
                        onClick = { onResult(null) },
                        tint = Color.White,
                    )
                    Text(
                        strings().settings.webSignIn,
                        style = PokyhType.headline,
                        color = Color.White,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val ta = strings().account
                    Text(
                        if (refused && !allowed) ta.cameraNeeded else ta.scanPrompt,
                        style = PokyhType.body.copy(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                    if (refused && !allowed) {
                        WebButton(
                            ta.openSettings,
                            variant = WebButtonVariant.Primary,
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The camera picture, reading QR codes until the first one; it stops while the app is in the background. */
@Composable
private fun CameraReader(onCode: (String) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var done by remember { mutableStateOf(false) }
    val view = remember { mutableStateOf<BarcodeView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            BarcodeView(ctx).apply {
                decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                cameraSettings = CameraSettings().apply {
                    isAutoFocusEnabled = true
                    isContinuousFocusEnabled = true
                }
                decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        val text = result.text ?: return
                        if (done) return
                        done = true
                        pause()
                        onCode(text)
                    }
                })
                view.value = this
                resume()
            }
        },
    )
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!done) view.value?.resume()
                Lifecycle.Event.ON_PAUSE -> view.value?.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            view.value?.pause()
        }
    }
}

/** The picture dimmed around a square in the middle, its corners marked, where the code goes. */
@Composable
private fun Viewfinder() {
    Canvas(Modifier.fillMaxSize()) {
        val side = minOf(size.width, size.height) * 0.68f
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2 - size.height * 0.04f
        val radius = 28.dp.toPx()
        val hole = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(left, top, left + side, top + side, CornerRadius(radius)))
        }
        drawPath(hole, Color.Black.copy(alpha = 0.55f))
        // The four corners, as a scanner frame: arcs with short legs.
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        val leg = side * 0.16f
        val corners = listOf(
            Offset(left, top) to 180f,
            Offset(left + side - 2 * radius, top) to 270f,
            Offset(left + side - 2 * radius, top + side - 2 * radius) to 0f,
            Offset(left, top + side - 2 * radius) to 90f,
        )
        for ((origin, start) in corners) {
            drawArc(Color.White, startAngle = start, sweepAngle = 90f, useCenter = false, topLeft = origin, size = Size(2 * radius, 2 * radius), style = stroke)
        }
        val w = stroke.width
        fun line(a: Offset, b: Offset) = drawLine(Color.White, a, b, strokeWidth = w, cap = StrokeCap.Round)
        line(Offset(left + radius, top), Offset(left + radius + leg, top))
        line(Offset(left, top + radius), Offset(left, top + radius + leg))
        line(Offset(left + side - radius, top), Offset(left + side - radius - leg, top))
        line(Offset(left + side, top + radius), Offset(left + side, top + radius + leg))
        line(Offset(left + side - radius, top + side), Offset(left + side - radius - leg, top + side))
        line(Offset(left + side, top + side - radius), Offset(left + side, top + side - radius - leg))
        line(Offset(left + radius, top + side), Offset(left + radius + leg, top + side))
        line(Offset(left, top + side - radius), Offset(left, top + side - radius - leg))
    }
}
