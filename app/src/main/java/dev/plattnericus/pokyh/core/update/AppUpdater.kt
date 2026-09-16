package dev.plattnericus.pokyh.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * In-app updates from GitHub releases ([BuildConfig.UPDATE_REPO]).
 *
 * A release counts when its tag (`v2.0.1`) is newer than [BuildConfig.VERSION_NAME] **and** it
 * has an `.apk` attached. A release created before its APK is uploaded is simply not an update
 * yet, so there is no window where users get offered a broken one.
 *
 * The APK is downloaded into `cacheDir/updates/`, checked (same package, higher versionCode) and
 * handed to the system installer, which asks the user to confirm. Android only accepts it when it
 * is signed with the same key as the installed app — see the signing config in build.gradle.kts.
 *
 * App-scoped rather than a ViewModel so a running download survives the dialog, the Profile
 * screen and configuration changes.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val prefsStore: PreferencesStore,
) {
    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
    )

    /** What the update dialog shows. [Idle] means no dialog. */
    sealed interface State {
        data object Idle : State
        data class Available(val release: Release) : State
        /** [progress] is 0..1, or null while the size is unknown. */
        data class Downloading(val release: Release, val progress: Float?) : State
        /** Downloaded, but the user still has to allow POKYH to install apps. */
        data class NeedsPermission(val release: Release, val apk: File) : State
        data class Failed(val release: Release, val message: String) : State
    }

    /** The Profile screen's "Nach Updates suchen" row. */
    enum class ManualCheck { Idle, Checking, UpToDate, Failed }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _manualCheck = MutableStateFlow(ManualCheck.Idle)
    val manualCheck: StateFlow<ManualCheck> = _manualCheck.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val json = Json { ignoreUnknownKeys = true }

    // Downloads are tens of MB on school Wi-Fi: no overall deadline, only a stall timeout.
    private val http = okHttpClient.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var launchChecked = false
    private var downloadJob: Job? = null

    /**
     * Once per process, on app start. Stays quiet about everything — offline, rate limits, no
     * release — and respects "Morgen". Debug builds are a different package (`.debug`) that a
     * release APK can't update, so they skip it.
     */
    fun checkOnLaunch() {
        if (launchChecked || BuildConfig.DEBUG) return
        launchChecked = true
        scope.launch {
            if (prefsStore.updateSnoozedUntil.first() > System.currentTimeMillis()) return@launch
            val release = runCatching { fetchNewerRelease() }.getOrNull() ?: return@launch
            if (_state.value == State.Idle) _state.value = State.Available(release)
        }
    }

    /** "Nach Updates suchen" — ignores "Morgen" and reports its outcome. */
    fun checkNow() {
        if (_manualCheck.value == ManualCheck.Checking) return
        if (_state.value != State.Idle) return // a dialog for this is already on screen
        scope.launch {
            _manualCheck.value = ManualCheck.Checking
            _manualCheck.value = try {
                val release = fetchNewerRelease()
                if (release != null) {
                    _state.value = State.Available(release)
                    ManualCheck.Idle
                } else {
                    ManualCheck.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ManualCheck.Failed
            }
        }
    }

    /** Forgets the last "Aktuell"/"Fehlgeschlagen" so it doesn't linger into the next visit. */
    fun clearManualCheckResult() {
        if (_manualCheck.value != ManualCheck.Checking) _manualCheck.value = ManualCheck.Idle
    }

    /** "Morgen": close the dialog; the launch check stays quiet until midnight. */
    fun snooze() {
        cancelDownload()
        _state.value = State.Idle
        scope.launch {
            val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            prefsStore.setUpdateSnoozedUntil(midnight)
        }
    }

    fun cancel() {
        cancelDownload()
        _state.value = State.Idle
    }

    fun startUpdate() {
        val release = when (val s = _state.value) {
            is State.Available -> s.release
            is State.Failed -> s.release
            else -> return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _state.value = State.Downloading(release, null)
            try {
                val apk = download(release)
                verify(apk)
                install(release, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed(release, e.toUserMessage())
            }
        }
    }

    /** Opens the system page where the user allows POKYH to install apps. */
    fun openInstallPermissionSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** Called when the app returns to the foreground — picks up the permission granted meanwhile. */
    fun onResume() {
        val s = _state.value as? State.NeedsPermission ?: return
        if (context.packageManager.canRequestPackageInstalls()) install(s.release, s.apk)
    }

    // ── GitHub ──────────────────────────────────────────────────────────────

    private suspend fun fetchNewerRelease(): Release? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { response ->
            // 404 = no published release yet — that's "up to date", not an error.
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("GitHub antwortet mit ${response.code}")
            val gh = json.decodeFromString(GhRelease.serializer(), response.body.string())
            val version = gh.tagName.trim().removePrefix("v").removePrefix("V")
            if (gh.draft || gh.prerelease || !isNewer(version, BuildConfig.VERSION_NAME)) return@withContext null
            val apk = gh.assets
                .filter { it.name.endsWith(".apk", ignoreCase = true) }
                .maxByOrNull { it.updatedAt }
                ?: return@withContext null
            Release(version = version, notes = gh.body.orEmpty().trim(), apkUrl = apk.downloadUrl, apkSize = apk.size)
        }
    }

    private suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Only ever one update on disk.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "POKYH-${release.version}.apk")
        val part = File(dir, target.name + ".part")

        http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download fehlgeschlagen (${response.code})")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    var lastReported = -1
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (total != null) {
                            val percent = (received * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                _state.value = State.Downloading(release, (received.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
        if (!part.renameTo(target)) throw IOException("Update konnte nicht gespeichert werden")
        target
    }

    /** Refuses anything the installer would reject anyway, with a message that says why. */
    private fun verify(apk: File) {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.path, 0)
        } ?: throw UpdateException("Die heruntergeladene Datei ist beschädigt.")
        if (info.packageName != context.packageName) {
            throw UpdateException("Dieses Update ist für eine andere App-Variante (${info.packageName}).")
        }
        if (PackageInfoCompat.getLongVersionCode(info) <= BuildConfig.VERSION_CODE) {
            throw UpdateException("Das Update ist nicht neuer als die installierte Version.")
        }
    }

    private fun install(release: Release, apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = State.NeedsPermission(release, apk)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            // The system installer takes it from here; installing replaces this process.
            _state.value = State.Idle
        } catch (e: Exception) {
            _state.value = State.Failed(release, "Installation konnte nicht gestartet werden.")
        }
    }

    private fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    private class UpdateException(message: String) : Exception(message)

    private fun Exception.toUserMessage(): String = when (this) {
        is UpdateException -> message.orEmpty()
        is java.net.UnknownHostException -> "Keine Internetverbindung."
        is java.net.SocketTimeoutException -> "Zeitüberschreitung beim Download."
        is IOException -> message?.takeIf { it.isNotBlank() } ?: "Download fehlgeschlagen."
        else -> "Unbekannter Fehler."
    }

    @Serializable
    private data class GhRelease(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<GhAsset> = emptyList(),
    )

    @Serializable
    private data class GhAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long = 0,
        @SerialName("updated_at") val updatedAt: String = "",
    )

    companion object {
        /** Numeric, part by part: 2.0.10 > 2.0.9, and 2.1 == 2.1.0. */
        fun isNewer(remote: String, local: String): Boolean {
            val r = Regex("\\d+").findAll(remote).map { it.value.toInt() }.toList()
            val l = Regex("\\d+").findAll(local).map { it.value.toInt() }.toList()
            if (r.isEmpty() || l.isEmpty()) return false
            for (i in 0 until maxOf(r.size, l.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = l.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
