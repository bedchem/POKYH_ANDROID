package dev.plattnericus.pokyh.core.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import dev.plattnericus.pokyh.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * What went wrong when POKYH last crashed, kept on the phone so the next start can offer to send
 * it ([dev.plattnericus.pokyh.ui.CrashReportDialog]). Phones we can't plug in — Honor's, Huawei's —
 * crashed without a trace; this is the trace. Only the error, the app version and the phone's
 * make, model and Android version: no account, no school data. Nothing leaves the phone unless
 * the person shares it.
 *
 * The release build is shrunk, so the stack shows short names; the build's mapping file
 * (kept per release) turns them back (`retrace`).
 */
object CrashLog {
    private const val FILE = "last_crash.txt"
    private const val TAG = "POKYH"

    /** Writes an uncaught error down before the process dies, then lets Android end it as before. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { File(app.filesDir, FILE).writeText(report(thread.name, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * For background work (syncing notifications, widgets, alarms, the update check): an error
     * there is logged and the work given up, instead of taking the whole app down — on some
     * phones (Honor, Huawei) the system's own services throw where others don't.
     */
    val background: CoroutineExceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e(TAG, "Background work failed", error)
    }

    /** The report of the last crash, if there was one since it was last dismissed. */
    fun read(context: Context): String? = runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun report(thread: String, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("POKYH ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${java.time.OffsetDateTime.now()} · thread $thread")
            appendLine()
            // The shareable text has room for the error and its causes, not for every frame.
            append(trace.take(12_000))
        }
    }
}
