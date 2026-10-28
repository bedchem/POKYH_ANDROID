package dev.plattnericus.pokyh.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.plattnericus.pokyh.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where a tapped notification leads. Every notification opens [MainActivity] with the route of
 * its screen as an extra (a new grade its subject, a message the message, a reminder the
 * reminder, a cancelled lesson the timetable); the activity hands the route here, and the
 * signed-in shell goes there as soon as it is on screen — after the lock screen when the app was
 * locked, or after starting when it wasn't running at all.
 */
object NotificationRoute {
    const val EXTRA_ROUTE = "pokyh_route"

    private val _pending = MutableStateFlow<String?>(null)

    /** The screen a tapped notification asked for, until the shell has gone there. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Takes the route out of the intent the activity was started (or brought back) with. */
    fun offer(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_ROUTE)?.takeIf { it.isNotBlank() } ?: return
        // Only once: the activity keeps its intent, and a recreation must not open it again.
        intent.removeExtra(EXTRA_ROUTE)
        _pending.value = route
    }

    /** The shell went to [route]. */
    fun consumed(route: String) {
        _pending.compareAndSet(route, null)
    }

    /** What a notification does when tapped: opens the app, at [route] when there is one. */
    fun contentIntent(context: Context, requestCode: Int, route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // The running app comes forward (onNewIntent) instead of a second one starting.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) putExtra(EXTRA_ROUTE, route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
