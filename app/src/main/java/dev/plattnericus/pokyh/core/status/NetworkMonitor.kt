package dev.plattnericus.pokyh.core.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the phone has a usable network, straight from the system.
 *
 * **[ServiceHealth] could not answer this, and that is what made the offline banner invisible.**
 * It only knows about servers the app has already tried, so on a cold start with no signal — the
 * exact case the banner exists for — it knew nothing, and the app had to fail two requests before
 * it would admit anything was wrong. On the Lock screen, where no request is sent at all, it
 * never would.
 *
 * The distinction also changes what the banner should say. "WebUntis nicht erreichbar" is wrong
 * when the phone is in flight mode; "keine Internetverbindung" is wrong when the Wi-Fi is fine
 * and the school's server is down. Only the system can tell those apart, and it can do it
 * instantly.
 *
 * **It is built to never call a working phone offline**, because that is the failure people
 * actually saw. Three things used to do it:
 *
 *  - *Requiring [NetworkCapabilities.NET_CAPABILITY_VALIDATED].* Plenty of working networks never
 *    get it — Private DNS or an ad-blocking DNS that blocks the connectivity check, some VPNs,
 *    ROMs with the check disabled. Only a network the system *knows* is a captive portal counts
 *    as unusable now; anything else that claims internet is trusted until a request says
 *    otherwise, and then [ServiceHealth] tells the server story instead.
 *  - *Reacting to every blip.* A Wi-Fi → mobile handover drops the default network for a moment
 *    before the next one arrives. Going offline now waits [OfflineDebounceMs]; coming back is
 *    immediate.
 *  - *Ignoring the servers.* A request that just got an answer is proof of a connection no
 *    matter what the callbacks last said — see [reportReachable].
 *
 * **Coming back from the background was the other half of it.** Android cuts a backgrounded app
 * off the network (Doze, battery saver, Data Saver, app standby) and says so through
 * `onBlockedStatusChanged`, which this took for "the phone is offline". On return the unblock
 * arrives a moment *after* the app is on screen, so a return could flash "Keine
 * Internetverbindung" then "Wieder online", and whatever loaded in that moment failed and was
 * blamed on the servers. So nothing goes offline while in the background, the real state is
 * re-read on return with [ResumeGraceMs] to settle, and failures in that window are not held
 * against a server — see [failureMeansServerDown].
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val goOffline = Runnable { _online.value = false }

    @Volatile private var foreground = true
    @Volatile private var foregroundedAt = SystemClock.elapsedRealtime()

    init {
        _online.value = usable(runCatching { manager?.getNetworkCapabilities(manager.activeNetwork) }.getOrNull())
            || manager == null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                update(runCatching { manager?.getNetworkCapabilities(network) }.getOrNull())
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update(caps)
            override fun onLost(network: Network) = update(null)
            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (blocked) update(null) else onAvailable(network)
            }
        }
        // Registration can throw on a device with the service missing or a restricted profile;
        // a monitor that cannot register should leave the app assuming it is online rather than
        // permanently accusing it of being offline.
        runCatching { manager?.registerDefaultNetworkCallback(callback, main) }
    }

    /** Wired to the app going to the background. Network blocks from here on are Android's
     * background restrictions, not the phone losing its connection. */
    fun onBackgrounded() {
        foreground = false
        main.removeCallbacks(goOffline)
    }

    /** Wired to the app coming back. Re-reads the real state instead of trusting callbacks that
     * may still be queued, and gives a blocked network [ResumeGraceMs] to be released. */
    fun onForegrounded() {
        foreground = true
        foregroundedAt = SystemClock.elapsedRealtime()
        main.removeCallbacks(goOffline)
        if (usableNow()) {
            reportReachable()
        } else if (_online.value) {
            main.postDelayed(goOffline, ResumeGraceMs)
        }
    }

    /** Whether the system says there is a usable network *right now*. `activeNetwork` is null
     * while this app is blocked, so a background restriction reads as not usable. */
    fun usableNow(): Boolean {
        if (manager == null) return true
        return usable(runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull())
    }

    /** Waits up to [timeoutMs] for [usableNow]; returns whether it got there. */
    suspend fun awaitUsable(timeoutMs: Long): Boolean {
        if (usableNow()) return true
        if (timeoutMs <= 0) return false
        return withTimeoutOrNull(timeoutMs) {
            while (!usableNow()) delay(PollMs)
            true
        } ?: false
    }

    /** Waits for the network only while the app is still settling after a return from the
     * background; otherwise returns at once, so a phone that really is offline is not held up. */
    suspend fun awaitSettled() {
        awaitUsable(ResumeGraceMs - (SystemClock.elapsedRealtime() - foregroundedAt))
    }

    /**
     * Whether a failed request says anything about the server. Not when the phone has no network,
     * not while the app is in the background (Android blocks it there), and not in the first
     * moments after coming back, while that block is still being lifted.
     */
    fun failureMeansServerDown(): Boolean =
        foreground &&
            SystemClock.elapsedRealtime() - foregroundedAt >= ResumeGraceMs &&
            usableNow()

    /** A server just answered — whatever the callbacks think, this phone is online. */
    fun reportReachable() {
        main.removeCallbacks(goOffline)
        if (!_online.value) _online.value = true
    }

    private fun update(caps: NetworkCapabilities?) {
        if (usable(caps)) {
            reportReachable()
        } else if (_online.value && foreground) {
            main.removeCallbacks(goOffline)
            main.postDelayed(goOffline, OfflineDebounceMs)
        }
    }

    private fun usable(caps: NetworkCapabilities?): Boolean =
        caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)

    private companion object {
        const val OfflineDebounceMs = 3_000L
        const val ResumeGraceMs = 5_000L
        const val PollMs = 250L
    }
}
