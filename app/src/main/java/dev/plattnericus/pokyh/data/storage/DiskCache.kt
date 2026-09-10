package dev.plattnericus.pokyh.data.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistenter JSON-Cache auf der Platte — überlebt App-Neustarts und dient als Offline-Fallback,
 * wenn das Netz nicht erreichbar ist. Android-Pendant zu iOS' `DiskCache` (DiskCache.swift).
 *
 * Sicherheit: speichert ausschließlich fachliche Daten (Stundenplan/Noten), NIE Zugangsdaten —
 * Passwörter bleiben in [SecureCredentialStore]. `filesDir` ist app-sandboxed.
 *
 * Schlüssel-Konvention der Aufrufer (hier nicht hartkodiert, nur dokumentiert):
 * - `"tt-<studentId>-<yyyy-MM-dd>"` — Stundenplan eines Tages
 * - `"grades-<studentId>-<year>"` — Noten eines Schuljahrs
 * - `"session-<username>"` — zwischengespeicherte Session-Daten
 */
@Singleton
class DiskCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "offline").apply { mkdirs() }

    private fun fileFor(key: String): File {
        val safe = key.map { if (it.isLetterOrDigit()) it else '_' }.joinToString(separator = "")
        return File(dir, "$safe.json")
    }

    suspend fun <T> write(key: String, value: T, serializer: KSerializer<T>) = withContext(Dispatchers.IO) {
        try {
            val encoded = json.encodeToString(serializer, value)
            val target = fileFor(key)
            val tmp = File(dir, "${target.name}.tmp")
            tmp.writeText(encoded, Charsets.UTF_8)
            tmp.renameTo(target)
        } catch (_: Exception) {
            // Best-effort — ein fehlgeschlagenes Schreiben soll den Aufrufer nicht crashen.
        }
    }

    suspend fun <T> read(key: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString(serializer, file.readText(Charsets.UTF_8))
        } catch (_: SerializationException) {
            null
        } catch (_: IOException) {
            null
        }
    }
}
