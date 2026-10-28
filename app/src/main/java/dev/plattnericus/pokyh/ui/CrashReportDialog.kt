package dev.plattnericus.pokyh.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.plattnericus.pokyh.core.diagnostics.CrashLog
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.ui.profile.PokyhDialog

/**
 * After a crash, once: what happened and a way to send the report ([CrashLog]) — through the
 * phone's share sheet, so it goes wherever the person chooses (a chat, an e-mail). Shared or
 * closed, it is gone; the next crash brings a new one.
 */
@Composable
internal fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    val text = report ?: return
    val t = strings().account
    PokyhDialog(
        title = t.crashTitle,
        body = t.crashBody,
        onDismiss = {
            CrashLog.clear(context)
            report = null
        },
        confirmLabel = t.crashShare,
        onConfirm = {
            CrashLog.clear(context)
            report = null
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, t.crashTitle)
                .putExtra(Intent.EXTRA_TEXT, text)
            runCatching { context.startActivity(Intent.createChooser(send, t.crashShare).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        },
        dismissLabel = strings().common.close,
    )
}
