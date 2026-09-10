package dev.plattnericus.pokyh.core.notifications

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.plattnericus.pokyh.R

/**
 * Fires one reminder notification when its [android.app.AlarmManager] alarm
 * ([PokyhNotifications.scheduleReminders]) goes off. Deliberately dependency-free (no Hilt) —
 * the alarm can wake a fully cold process, and building the notification straight from the
 * Intent's extras needs nothing else. [dev.plattnericus.pokyh.PokyhApplication.onCreate] always
 * runs first in that case too, so [PokyhNotifications.CHANNEL_ID] is guaranteed to already exist.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, PokyhNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
