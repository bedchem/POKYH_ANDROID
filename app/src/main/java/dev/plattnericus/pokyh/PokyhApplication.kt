package dev.plattnericus.pokyh

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.plattnericus.pokyh.core.notifications.NotificationSyncWorker
import dev.plattnericus.pokyh.core.notifications.PokyhNotifications
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Hilt entry point (POKYHApp.swift). Also owns the two pieces of app-wide (not per-Activity)
 * background setup iOS does in `init()`/`AppDelegate`: creating the notification channel up
 * front — so [dev.plattnericus.pokyh.core.notifications.ReminderAlarmReceiver] can post into it
 * even from a cold process an alarm just woke — and registering [NotificationSyncWorker]'s
 * periodic run (`BackgroundRefresh.register()` + an initial `.schedule()`, folded into one
 * `enqueueUniquePeriodicWork` since WorkManager — unlike `BGTaskScheduler` — reschedules itself).
 * See [dev.plattnericus.pokyh.MainActivity] for the UI-process wiring (splash, edge-to-edge,
 * app-lifecycle → [dev.plattnericus.pokyh.state.AppState] auto-lock).
 */
@HiltAndroidApp
class PokyhApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var pokyhNotifications: PokyhNotifications

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        pokyhNotifications.ensureChannel()

        // The manifest's WorkManagerInitializer removal (see AndroidManifest.xml) means nothing
        // else initializes WorkManager — do it here, explicitly, with the Hilt-aware config, so
        // NotificationSyncWorker's @AssistedInject constructor actually gets resolved.
        WorkManager.initialize(this, workManagerConfiguration)

        val request = PeriodicWorkRequestBuilder<NotificationSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NotificationSyncWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
