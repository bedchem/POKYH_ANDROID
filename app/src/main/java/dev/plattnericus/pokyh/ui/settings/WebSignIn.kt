package dev.plattnericus.pokyh.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.profile.PokyhDialog
import dev.plattnericus.pokyh.ui.theme.Brand
import kotlinx.coroutines.launch

/** What the website's QR code holds (backend services/qrLogin.ts `qrText`). */
private const val QR_PREFIX = "POKYH-LOGIN:"
private val QR_CODE = Regex("^[A-Za-z0-9_-]{24,64}$")

/** The sign-in code of a scanned text; `null` for anything that is not POKYH's. */
internal fun webSignInCodeOf(scanned: String): String? {
    val text = scanned.trim()
    if (!text.startsWith(QR_PREFIX)) return null
    return text.removePrefix(QR_PREFIX).takeIf { QR_CODE.matches(it) }
}

private sealed interface Step {
    data object Idle : Step
    data object Working : Step
    data class Confirm(val code: String, val info: BackendClient.QrLoginInfo) : Step
    data object Done : Step
    data class Failed(val message: String, val scanAgain: Boolean) : Step
}

/**
 * "Am Computer anmelden": scans the QR code pokyh.com shows ("Mit der POKYH-App anmelden"), says
 * which computer asks and where it is, and once confirmed signs it in as this account
 * ([AppState.approveWebSignIn]). The scanner is Google's (Play services): its camera screen is the
 * system's, so the app itself needs no camera permission.
 */
@Composable
fun WebSignInRow(appState: AppState, leading: @Composable () -> Unit) {
    val t = strings().settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<Step>(Step.Idle) }

    fun readable(e: Throwable): String = when {
        e is AppError && e.isNetwork -> I18n.s.common.noConnection
        e is AppError && e.isSessionExpired -> I18n.s.common.sessionExpired
        else -> e.message?.takeIf { it.isNotBlank() } ?: I18n.s.common.unknownError
    }

    fun lookUp(code: String) {
        step = Step.Working
        scope.launch {
            step = try {
                val info = appState.webSignInInfo(code)
                if (info == null) Step.Failed(t.webSignInGone, scanAgain = true) else Step.Confirm(code, info)
            } catch (e: Throwable) {
                Step.Failed(readable(e), scanAgain = false)
            }
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                val code = webSignInCodeOf(barcode.rawValue.orEmpty())
                if (code == null) step = Step.Failed(t.webSignInNotPokyh, scanAgain = true) else lookUp(code)
            }
            // Without Play services, or while the scanner is still being downloaded.
            .addOnFailureListener { step = Step.Failed(t.webSignInNoScanner, scanAgain = false) }
        // Cancelled: the settings stay as they were.
    }

    fun approve(code: String) {
        step = Step.Working
        scope.launch {
            step = try {
                if (appState.approveWebSignIn(code)) Step.Done else Step.Failed(t.webSignInGone, scanAgain = true)
            } catch (e: Throwable) {
                Step.Failed(readable(e), scanAgain = false)
            }
        }
    }

    val working = step == Step.Working
    PokyhRow(
        title = t.webSignIn,
        subtitle = t.webSignInHint,
        leading = leading,
        trailing = if (working) {
            { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.accent) }
        } else null,
        onClick = { scan() },
        enabled = !working,
    )

    when (val s = step) {
        is Step.Confirm -> {
            val where = listOfNotNull(s.info.place.takeIf { it.isNotBlank() }, s.info.ip?.takeIf { it.isNotBlank() }).joinToString(" · ")
            PokyhDialog(
                title = t.webSignInConfirmTitle,
                body = buildString {
                    append(s.info.label.ifBlank { t.webSignInUnknownDevice })
                    if (where.isNotEmpty()) append("\n").append(where)
                    append("\n\n").append(t.webSignInConfirmHint)
                },
                onDismiss = { step = Step.Idle },
                confirmLabel = t.webSignInConfirm,
                onConfirm = { approve(s.code) },
            )
        }
        Step.Done -> PokyhDialog(
            title = t.webSignInDoneTitle,
            body = t.webSignInDoneBody,
            onDismiss = { step = Step.Idle },
            confirmLabel = I18n.s.common.ok,
            onConfirm = { step = Step.Idle },
            dismissLabel = null,
        )
        is Step.Failed -> PokyhDialog(
            title = t.webSignIn,
            body = s.message,
            onDismiss = { step = Step.Idle },
            confirmLabel = if (s.scanAgain) t.webSignInScanAgain else I18n.s.common.ok,
            onConfirm = {
                step = Step.Idle
                if (s.scanAgain) scan()
            },
            dismissLabel = if (s.scanAgain) I18n.s.common.cancel else null,
        )
        else -> Unit
    }
}
