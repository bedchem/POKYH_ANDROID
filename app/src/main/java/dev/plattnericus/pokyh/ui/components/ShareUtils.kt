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
