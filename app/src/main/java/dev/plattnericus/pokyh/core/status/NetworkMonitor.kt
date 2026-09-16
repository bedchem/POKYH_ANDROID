package dev.plattnericus.pokyh.core.status

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
 * [NET_CAPABILITY_VALIDATED] rather than merely connected: a captive-portal Wi-Fi — a hotel, a
 * school guest network with an unaccepted login page — is "connected" and carries no traffic, and
 * that is a very common way for this app to appear broken.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _online.value = currentlyOnline() }
            override fun onLost(network: Network) { _online.value = currentlyOnline() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = currentlyOnline()
            }
        }
        // Registration can throw on a device with the service missing or a restricted profile;
        // a monitor that cannot register should leave the app assuming it is online rather than
        // permanently accusing it of being offline.
        runCatching { manager?.registerNetworkCallback(request, callback) }
    }

    private fun currentlyOnline(): Boolean {
        val caps = runCatching { manager?.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
            ?: return manager == null
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
