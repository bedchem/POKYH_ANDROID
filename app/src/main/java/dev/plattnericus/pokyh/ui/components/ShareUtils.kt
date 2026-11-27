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
    val type = mimeTypeOf(fileName, mimeType)

    // The system's "Open with" list of the apps for this type (PDF viewers for a PDF). It used to
    // ask resolveActivity first, which since Android 11 sees no other app without a <queries>
    // entry, so every file fell through to the share sheet instead of the viewers.
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, type)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val opened = runCatching {
        context.startActivity(Intent.createChooser(view, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (opened) return true
    val send = Intent(Intent.ACTION_SEND).apply {
        this.type = type
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { context.startActivity(Intent.createChooser(send, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}

/**
 * Saves a file into the phone's Downloads (Android 10 and up: through MediaStore, no permission
 * needed; before: the app's own Downloads folder). Returns the name it was saved under, or null.
 */
fun saveToDownloads(context: Context, fileName: String, mimeType: String, bytes: ByteArray): String? = runCatching {
    val name = sanitizeFileName(fileName)
    val type = mimeTypeOf(fileName, mimeType)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, type)
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching null
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return@runCatching null
        values.clear()
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        notifyDownloaded(context, name, uri, type)
    } else {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: return@runCatching null
        File(dir.apply { mkdirs() }, name).writeBytes(bytes)
    }
    name
}.getOrNull()

/** The file's type: the server's, unless it is a generic one, then the one its name says (a ".pdf" is a PDF). */
private fun mimeTypeOf(fileName: String, given: String): String {
    val generic = given.isBlank() || given == "application/octet-stream" || given == "binary/octet-stream"
    if (!generic) return given
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: given.ifBlank { "application/octet-stream" }
}

/** Keeps a server-supplied name usable as a file name (the web route does the same). */
private fun sanitizeFileName(name: String): String {
    val cleaned = name.map { c -> if (c.isLetterOrDigit() || c in ".-_ ") c else '_' }
        .joinToString("")
        .replace("..", "_")
        .trim()
    return cleaned.ifEmpty { "anhang" }
}

/**
 * The system's share sheet for a downloaded file (messengers, mail, Drive, …), from the file
 * viewer's ⋮ menu. Returns false when nothing could take it.
 */
fun shareFile(context: Context, fileName: String, mimeType: String, bytes: ByteArray): Boolean = runCatching {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, sanitizeFileName(fileName))
    file.writeBytes(bytes)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeTypeOf(fileName, mimeType)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/**
 * Prints a PDF through Android's print dialog (a printer, or "Save as PDF"): the file is handed
 * over as it is. Returns false when the system has no print service to offer.
 */
fun printPdf(context: Context, fileName: String, bytes: ByteArray): Boolean = runCatching {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
    val adapter = object : android.print.PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: android.print.PrintAttributes?,
            newAttributes: android.print.PrintAttributes?,
            cancellationSignal: android.os.CancellationSignal?,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = android.print.PrintDocumentInfo.Builder(sanitizeFileName(fileName))
                .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?,
            destination: android.os.ParcelFileDescriptor,
            cancellationSignal: android.os.CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            runCatching {
                java.io.FileOutputStream(destination.fileDescriptor).use { it.write(bytes) }
            }.onSuccess {
                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            }.onFailure {
                callback.onWriteFailed(it.message)
            }
        }
    }
    manager.print(fileName, adapter, null)
}.isSuccess

/**
 * A POKYH notification once a file is in Downloads: its name, and a tap opens it in an app for
 * its type. Android announces only the downloads its own download service makes; POKYH writes the
 * file itself, so it says so itself. Nothing when notifications are off.
 */
private fun notifyDownloaded(context: Context, name: String, uri: android.net.Uri, type: String) {
    if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return
    runCatching {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        // The app's one channel (PokyhNotifications.CHANNEL_ID), in case nothing created it yet.
        if (manager.getNotificationChannel(dev.plattnericus.pokyh.core.notifications.PokyhNotifications.CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    dev.plattnericus.pokyh.core.notifications.PokyhNotifications.CHANNEL_ID,
                    "POKYH",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val id = "download-$name".hashCode()
        val open = android.app.PendingIntent.getActivity(
            context,
            id,
            Intent.createChooser(view, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val t = dev.plattnericus.pokyh.core.i18n.I18n.s.messages
        val notification = androidx.core.app.NotificationCompat.Builder(context, dev.plattnericus.pokyh.core.notifications.PokyhNotifications.CHANNEL_ID)
            .setSmallIcon(dev.plattnericus.pokyh.R.drawable.ic_notification)
            .setContentTitle(t.downloadedTitle)
            .setContentText("$name · ${t.downloadedTap}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        androidx.core.app.NotificationManagerCompat.from(context).notify(id, notification)
    }
}
