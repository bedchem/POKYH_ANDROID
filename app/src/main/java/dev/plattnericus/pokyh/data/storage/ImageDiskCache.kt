package dev.plattnericus.pokyh.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lokaler Bild-Cache für Avatare (Bytes only) — Android-Pendant zu iOS' `ImageCache`
 * (ImageCache.swift), reduziert auf die reine Platten-Ebene.
 *
 * Existiert NUR für Profilbilder: die müssen offline sofort ohne Netzwerk-Roundtrip rendern
 * können. Gerichts-/Fach-Bilder laufen stattdessen über Coils eigenen Cache. Es werden
 * ausschließlich Bild-Bytes gespeichert — niemals Zugangsdaten/Tokens.
 */
@Singleton
class ImageDiskCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, "images").apply { mkdirs() }

    /** Stabiler, kollisionsarmer Dateiname je URL (SHA-256, hex). */
    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString(separator = "") { "%02x".format(it) }
        return File(dir, "$name.img")
    }

    suspend fun get(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(url)
        if (!file.exists()) return@withContext null
        try {
            file.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun put(url: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val target = fileFor(url)
            val tmp = File(dir, "${target.name}.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(target)
        } catch (_: Exception) {
            // Best-effort.
        }
    }
}
