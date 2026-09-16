package dev.plattnericus.pokyh.core.update

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import dev.plattnericus.pokyh.core.notifications.PokyhNotifications
import java.io.File

/**
 * 2.0.0 installs as an update over the old Flutter app (same applicationId), so that app's files
 * are still in the data directory. None of it can be read here — the Flutter app stored sessions
 * and settings in its own formats — so it is removed on first start, along with its notification
 * channels, which would otherwise keep showing up in the system settings.
 *
 * Idempotent: once the marker files are gone this does nothing.
 */
object LegacyFlutterCleanup {

    fun runIfNeeded(context: Context) {
        val dataDir = context.applicationInfo.dataDir?.let(::File) ?: return
        val sharedPrefs = File(dataDir, "shared_prefs")
        val flutterDir = File(dataDir, "app_flutter")
        val flutterPrefs = File(sharedPrefs, "FlutterSharedPreferences.xml")
        if (!flutterDir.exists() && !flutterPrefs.exists()) return

        runCatching {
            flutterDir.deleteRecursively()
            listOf(
                "FlutterSharedPreferences.xml",
                "FlutterSecureStorage.xml",
                "scheduled_notifications.xml",
                "flutter_local_notifications_plugin.xml",
            ).forEach { File(sharedPrefs, it).delete() }
            File(dataDir, "databases").listFiles { f -> f.name.startsWith("libCachedImageData") }?.forEach { it.delete() }
            File(context.cacheDir, "libCachedImageData").deleteRecursively()
        }.onFailure { Log.w(TAG, "Removing Flutter files failed", it) }

        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.notificationChannels
                .filter { it.id != PokyhNotifications.CHANNEL_ID && it.id != NotificationChannelDefault }
                .forEach { nm.deleteNotificationChannel(it.id) }
        }.onFailure { Log.w(TAG, "Removing Flutter notification channels failed", it) }
    }

    private const val TAG = "LegacyFlutterCleanup"
    private const val NotificationChannelDefault = "miscellaneous"
}
