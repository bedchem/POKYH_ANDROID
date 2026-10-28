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
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.PokyhRow
import dev.plattnericus.pokyh.ui.profile.PokyhDialog
import dev.plattnericus.pokyh.ui.theme.Brand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the website's QR code holds (backend services/qrLogin.ts `qrText`): the plain text, or —
 * with QR_LINK_BASE set — a link to `/l/<code>` on a POKYH host, which the phone's own camera
 * opens in the app ([WebSignInLink]).
 */
private const val QR_PREFIX = "POKYH-LOGIN:"
private val QR_CODE = Regex("^[A-Za-z0-9_-]{24,64}$")
private val QR_LINK = Regex("^https://([a-z0-9-]+\\.)*pokyh\\.com/l/([A-Za-z0-9_-]+)/?([?#].*)?$", RegexOption.IGNORE_CASE)

/** The sign-in code of a scanned text or opened link; `null` for anything that is not POKYH's. */
internal fun webSignInCodeOf(scanned: String): String? {
    val text = scanned.trim()
    val code = when {
        // As written in the QR code, or lowercased by a camera app that opened it as a link.
        text.startsWith(QR_PREFIX, ignoreCase = true) -> text.substring(QR_PREFIX.length)
        else -> QR_LINK.matchEntire(text)?.groupValues?.get(2) ?: return null
    }
    return code.takeIf { QR_CODE.matches(it) }
}

/**
 * A sign-in link the phone's own camera opened the app with: MainActivity hands it here, and the
 * signed-in shell asks to confirm it once it is on screen — straight away when the app was open,
 * after the lock screen (or signing in) when it wasn't.
 */
object WebSignInLink {
    private val _pending = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** The code waiting for the signed-in app. */
    val pending: kotlinx.coroutines.flow.StateFlow<String?> = _pending

    /** Takes a sign-in link out of the intent the activity was opened with, if it carries one. */
    fun offer(intent: android.content.Intent?) {
        if (intent?.action != android.content.Intent.ACTION_VIEW) return
        val code = intent.dataString?.let(::webSignInCodeOf) ?: return
        // Only once: a recreated activity keeps its intent.
        intent.data = null
        _pending.value = code
    }

    /** The shell has taken [code] over. */
    fun consumed(code: String) {
        _pending.compareAndSet(code, null)
    }
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
 * ([AppState.approveWebSignIn]). The scanner is the app's own camera screen ([QrScannerDialog],
 * ZXing): the same on every phone, with or without Google's services.
 *
 * Started from the settings ([WebSignInRow]) and from the account menu in every tab's header;
 * [WebSignInDialogs] shows the scanner and what follows the scan.
 */
class WebSignInFlow internal constructor(
    private val appState: AppState,
    private val scope: CoroutineScope,
) {
    /** What the scanner read (null: closed without a code). */
    private fun onScanned(text: String?) {
        if (text == null) return
        val code = webSignInCodeOf(text)
        if (code == null) step = Step.Failed(I18n.s.settings.webSignInNotPokyh, scanAgain = true) else lookUp(code)
    }

    private var step by mutableStateOf<Step>(Step.Idle)

    /** The camera is open. */
    private var scanning by mutableStateOf(false)

    val working: Boolean get() = step == Step.Working

    private fun readable(e: Throwable): String = when {
        e is AppError && e.isNetwork -> I18n.s.common.noConnection
        e is AppError && e.isSessionExpired -> I18n.s.common.sessionExpired
        else -> e.message?.takeIf { it.isNotBlank() } ?: I18n.s.common.unknownError
    }

    private fun lookUp(code: String) {
        step = Step.Working
        scope.launch {
            step = try {
                val info = appState.webSignInInfo(code)
                if (info == null) Step.Failed(I18n.s.settings.webSignInGone, scanAgain = true) else Step.Confirm(code, info)
            } catch (e: Throwable) {
                Step.Failed(readable(e), scanAgain = false)
            }
        }
    }

    /** A code that came in another way (a link the phone's camera opened): straight to the confirmation. */
    fun open(code: String) {
        if (working) return
        scanning = false
        lookUp(code)
    }

    /** Opens the camera; closing it without a code leaves everything as it was. */
    fun scan() {
        if (working) return
        step = Step.Idle
        scanning = true
    }

    private fun approve(code: String) {
        step = Step.Working
        scope.launch {
            step = try {
                if (appState.approveWebSignIn(code)) Step.Done else Step.Failed(I18n.s.settings.webSignInGone, scanAgain = true)
            } catch (e: Throwable) {
                Step.Failed(readable(e), scanAgain = false)
            }
        }
    }

    @Composable
    internal fun Dialogs() {
        val t = strings().settings
        if (scanning) {
            QrScannerDialog(onResult = { text ->
                scanning = false
                onScanned(text)
            })
        }
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
}

/** A [WebSignInFlow] for this screen. Show [WebSignInDialogs] with it. */
@Composable
fun rememberWebSignIn(appState: AppState): WebSignInFlow {
    val scope = rememberCoroutineScope()
    return remember(appState, scope) { WebSignInFlow(appState, scope) }
}

/** The confirmation, the result and the failures of a [WebSignInFlow]. */
@Composable
fun WebSignInDialogs(flow: WebSignInFlow) = flow.Dialogs()

/** The settings' row: scans on a tap, a spinner while it looks the code up or signs in. */
@Composable
fun WebSignInRow(appState: AppState, leading: @Composable () -> Unit) {
    val t = strings().settings
    val flow = rememberWebSignIn(appState)
    PokyhRow(
        title = t.webSignIn,
        subtitle = t.webSignInHint,
        leading = leading,
        trailing = if (flow.working) {
            { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Brand.accent) }
        } else null,
        onClick = { flow.scan() },
        enabled = !flow.working,
    )
    WebSignInDialogs(flow)
}
