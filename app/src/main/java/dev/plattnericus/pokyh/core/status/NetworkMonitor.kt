package dev.plattnericus.pokyh.core.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** A server just answered — whatever the callbacks think, this phone is online. */
    fun reportReachable() {
        main.removeCallbacks(goOffline)
        if (!_online.value) _online.value = true
    }

    private fun update(caps: NetworkCapabilities?) {
        if (usable(caps)) {
            reportReachable()
        } else if (_online.value) {
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
    }
}
