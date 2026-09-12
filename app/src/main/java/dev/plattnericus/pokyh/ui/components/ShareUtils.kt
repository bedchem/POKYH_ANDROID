package dev.plattnericus.pokyh.ui.components

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Port of iOS `ShareSheet` (`UIActivityViewController`) for exporting the week's timetable /
 * exams as `.ics` — Android's equivalent is a `FileProvider` URI + `ACTION_SEND` chooser.
 * Writes into `filesDir/shared/`, matching `file_paths.xml`'s `files-path name="shared_files"`.
 */
fun shareTextFile(context: Context, fileName: String, mimeType: String, content: String) {
    val dir = File(context.filesDir, "shared").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(content)

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** Convenience for the two ICS exports TimetableScreen offers (week / exams). */
fun shareIcs(context: Context, fileName: String, icsContent: String) =
    shareTextFile(context, fileName, "text/calendar", icsContent)

/**
 * Hands a downloaded file to the system: written into `cacheDir/shared/` (matching
 * `file_paths.xml`'s `cache-path name="shared_cache"`), then opened with whatever app handles
 * its type — a PDF viewer, the gallery, a document app.
 *
 * The share sheet is the fallback rather than the default, because "open" is what a tapped
 * attachment should do; from the viewer (or from the sheet, when nothing can open it) the file
 * can still be saved to Files/Drive. Returns false when neither is possible.
 */
fun openOrShareFile(context: Context, fileName: String, mimeType: String, bytes: ByteArray): Boolean {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, sanitizeFileName(fileName))
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (view.resolveActivity(context.packageManager) != null) {
        context.startActivity(view)
        return true
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { context.startActivity(Intent.createChooser(send, fileName)) }.isSuccess
}

/** Keeps a server-supplied name usable as a file name (the web route does the same). */
private fun sanitizeFileName(name: String): String {
    val cleaned = name.map { c -> if (c.isLetterOrDigit() || c in ".-_ ") c else '_' }
        .joinToString("")
        .replace("..", "_")
        .trim()
    return cleaned.ifEmpty { "anhang" }
}
