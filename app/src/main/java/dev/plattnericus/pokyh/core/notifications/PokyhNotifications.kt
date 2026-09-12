package dev.plattnericus.pokyh.core.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.R
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.core.util.toYyyyMMdd
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.data.model.MessagePreview
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import dev.plattnericus.pokyh.ui.reminders.parseRemindAt
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Android port of `NotificationManager` (Notifications.swift): one shared channel, opportunistic
 * "diff against seen IDs" checks for messages/grades/cancelled lessons (fired with no delay, same
 * as iOS's `trigger: nil`), and calendar-accurate reminder alarms. [AlarmManager] has no
 * `getPendingNotificationRequests` equivalent, so [PreferencesStore.scheduledReminderIds] is the
 * Android-side stand-in for "what's currently scheduled" that [scheduleReminders] diffs against.
 */
@Singleton
class PokyhNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsStore: PreferencesStore,
) {
    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "POKYH", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nachrichten, Noten, Stundenausfälle und Erinnerungen"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Posts a notification for each newly-arrived unread message and returns the **total** unread
     * count in the folder.
     *
     * Each notification carries that total as its badge number, so the launcher dot shows "how
     * many are waiting" rather than "how many arrived since the last sync" — and they're grouped
     * under one summary, so five new messages are one expandable stack instead of five separate
     * rows in the shade.
     *
     * The returned count is what drives the in-app badge (see
     * [dev.plattnericus.pokyh.state.AppState.unreadMessages]).
     */
    suspend fun checkNewMessages(messages: List<MessagePreview>): Int {
        val unread = messages.filter { !it.isRead }
        val seen = prefsStore.seenMessageIds.first()
        if (seen.isNotEmpty()) {
            val fresh = unread.filter { it.id !in seen }
            for (m in fresh) {
                val body = if (m.subject.isEmpty()) m.senderName else "${m.senderName}: ${m.subject}"
                notify(
                    id = idFor("msg", m.id),
                    title = "Neue Nachricht",
                    body = body,
                    group = MESSAGE_GROUP,
                    number = unread.size,
                )
            }
            // A summary only earns its place once there are several — one message plus a summary
            // is just the same message twice.
            if (fresh.size > 1) {
                notifySummary(
                    id = idFor("msg-summary", 0),
                    title = "${fresh.size} neue Nachrichten",
                    body = fresh.take(3).joinToString(" · ") { it.senderName },
                    group = MESSAGE_GROUP,
                    number = unread.size,
                )
            }
        }
        prefsStore.setSeenMessageIds(seen + messages.map { it.id })
        return unread.size
    }

    // ── Neue Noten erkennen → Hinweis ───────────────────────────────────────
    suspend fun checkNewGrades(subjects: List<SubjectGrades>) {
        val pairs = subjects.flatMap { s -> s.grades.filter { it.markDisplayValue > 0 }.map { s.subjectName to it } }
        val seen = prefsStore.seenGradeIds.first()
        if (seen.isNotEmpty()) {
            for ((subject, grade) in pairs) {
                if (grade.id !in seen) {
                    notify(idFor("grade", grade.id), "Neue Note", "$subject: ${Fmt.num(grade.markDisplayValue)}")
                }
            }
        }
        prefsStore.setSeenGradeIds(seen + pairs.map { it.second.id })
    }

    // ── Stundenausfälle erkennen → Hinweis ──────────────────────────────────
    suspend fun checkTimetableChanges(entries: List<TimetableEntry>) {
        val todayNum = todayLocalDate().toYyyyMMdd()
        val cancelled = entries.filter { it.isCancelled && it.date >= todayNum }
        fun key(e: TimetableEntry) = "${e.date}-${e.startTime}-${e.lessonId}"
        val seen = prefsStore.seenCancelledLessons.first()
        if (seen.isNotEmpty()) {
            for (e in cancelled) {
                val k = key(e)
                if (k !in seen) {
                    val body = "${e.subjectName} am ${Fmt.dateShort(e.date)} um ${Fmt.time(e.startTime)} entfällt."
                    notify(idFor("cancel", k.hashCode()), "Stunde fällt aus", body)
                }
            }
        }
        prefsStore.setSeenCancelledLessons(seen + cancelled.map(::key))
    }

    // ── Erinnerungen → geplante lokale Notifications (feuern auch geschlossen) ──
    suspend fun scheduleReminders(reminders: List<ApiReminder>) {
        val now = Instant.now()
        val upcoming = reminders.mapNotNull { r -> parseRemindAt(r.remindAt)?.let { r to it } }
            .filter { (_, at) -> at.isAfter(now) }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val previouslyScheduled = prefsStore.scheduledReminderIds.first()
        val currentIds = upcoming.map { (r, _) -> r.id }.toSet()
        for (staleId in previouslyScheduled - currentIds) cancelAlarm(alarmManager, staleId)

        val canExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        for ((reminder, at) in upcoming) {
            val pending = reminderPendingIntent(reminder.id, reminder.title, reminder.body)
            val triggerAt = at.toEpochMilli()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
        prefsStore.setScheduledReminderIds(currentIds)
    }

    private fun cancelAlarm(alarmManager: AlarmManager, reminderId: String) {
        val pending = reminderPendingIntent(reminderId, title = "", body = "")
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun reminderPendingIntent(reminderId: String, title: String, body: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
            putExtra(ReminderAlarmReceiver.EXTRA_BODY, body.ifEmpty { "Klassen-Erinnerung" })
            putExtra(ReminderAlarmReceiver.EXTRA_NOTIF_ID, idFor("reminder", reminderId.hashCode()))
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun idFor(prefix: String, key: Int): Int = "$prefix-$key".hashCode()

    private fun notify(
        id: Int,
        title: String,
        body: String,
        group: String? = null,
        number: Int = 0,
    ) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_ACCENT)
            .setContentTitle(title)
            .setContentText(body)
            // Long bodies get truncated to one line in the collapsed shade; BigTextStyle lets
            // the whole message show once expanded.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (group != null) setGroup(group)
                if (number > 0) {
                    setNumber(number)
                    setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                }
            }
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** The stack header for a group — shown instead of N individual rows when the shade collapses. */
    private fun notifySummary(id: Int, title: String, body: String, group: String, number: Int) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_ACCENT)
            .setContentTitle(title)
            .setContentText(body)
            .setGroup(group)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setNumber(number)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    companion object {
        const val CHANNEL_ID = "pokyh_general"

        /** Groups all message notifications into one collapsible stack. */
        private const val MESSAGE_GROUP = "pokyh_messages"

        /** [Brand.accent] as an ARGB int — the accent tint the system applies to the small icon
         * and the notification's accent line. Duplicated as a literal because this class is
         * framework-side and doesn't depend on the Compose theme. */
        private const val BRAND_ACCENT = 0xFF6366F1.toInt()
    }
}
