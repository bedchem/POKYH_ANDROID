package dev.plattnericus.pokyh.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.plattnericus.pokyh.data.model.MessageFolder
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import dev.plattnericus.pokyh.data.storage.SecureCredentialStore
import dev.plattnericus.pokyh.data.untis.UntisClient
import kotlinx.coroutines.flow.first

/**
 * `BackgroundRefresh` (Background.swift), ported to a WorkManager periodic job — the OS-scheduled
 * "best effort" refresh iOS runs via `BGAppRefreshTask` roughly every ~15 min even while the app
 * is closed. Deliberately messages-only, unlike the foreground `AppState.syncNotifications` sync
 * (which also schedules reminders / checks grades+lessons): those need the POKYH backend token a
 * live, already-authenticated [dev.plattnericus.pokyh.state.AppState] session carries, and this
 * worker intentionally does its OWN standalone silent WebUntis login rather than touching that
 * singleton — reaching into the live session from a background worker could clobber whatever the
 * user is actively doing in the foreground.
 */
@HiltWorker
class NotificationSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefsStore: PreferencesStore,
    private val credentialStore: SecureCredentialStore,
    private val untisClient: UntisClient,
    private val notifications: PokyhNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val username = prefsStore.lastActive.first()
            ?: prefsStore.defaultAccount.first()
            ?: prefsStore.accounts.first().firstOrNull()?.username
            ?: return Result.success()
        val password = credentialStore.getCredentials(username)?.second ?: return Result.success()

        return try {
            val session = untisClient.login(username, password)
            val inbox = untisClient.messages(session, MessageFolder.Inbox)
            notifications.checkNewMessages(inbox)
            Result.success()
        } catch (_: Exception) {
            // Best-effort, same spirit as iOS's `try?` around the background sync — a flaky/
            // offline network here should never retry-storm or surface an error anywhere.
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "pokyh_notification_sync"
    }
}
