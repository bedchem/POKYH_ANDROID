package dev.plattnericus.pokyh.core.status

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** The two servers POKYH depends on. A screen is usually served by exactly one of them. */
enum class PokyhService(val label: String) {
    UNTIS("WebUntis"),
    BACKEND("POKYH-Server"),
}

/**
 * What the app currently knows about one service.
 *
 * [reachable] is deliberately nullable: before the first request of a session there is nothing
 * to claim, and "unknown" must not render as "offline" — a banner that says WebUntis is down
 * while the app has simply not asked it anything yet is worse than no banner.
 */
data class ServiceState(
    val reachable: Boolean? = null,
    /** When this service last answered. 0 if it never has in this process. */
    val lastOkAt: Long = 0L,
    /** Why it is considered down — short, already user-facing German. */
    val message: String? = null,
)

/**
 * Which servers are answering, kept in one place so the UI can say *which* one is down instead
 * of showing every screen the same "keine Verbindung".
 *
 * Fed by [ServiceHealthInterceptor] from the one shared OkHttp client, so every call to either
 * host updates it without a single call site having to remember to report. That is the whole
 * reason it hangs off the interceptor rather than off the clients' methods: there are dozens of
 * those, and any one of them forgetting would make the banner lie.
 *
 * **Down needs [FailuresBeforeDown] consecutive failures; up needs one success.** A single
 * timeout on a phone changing cells is not an outage, and a banner that flickers on every such
 * blip teaches people to ignore it. Recovery is immediate in the other direction — the moment a
 * server answers, it is up, and there is no reason to make the user wait to be told so.
 */
@Singleton
class ServiceHealth @Inject constructor() {

    private val _states = MutableStateFlow(PokyhService.entries.associateWith { ServiceState() })
    val states: StateFlow<Map<PokyhService, ServiceState>> = _states.asStateFlow()

    private val failures = mutableMapOf<PokyhService, Int>()

    fun reportOk(service: PokyhService) {
        synchronized(failures) { failures[service] = 0 }
        _states.update { current ->
            val previous = current[service] ?: ServiceState()
            current + (service to previous.copy(reachable = true, lastOkAt = System.currentTimeMillis(), message = null))
        }
    }

    fun reportDown(service: PokyhService, message: String?) {
        val count = synchronized(failures) {
            val next = (failures[service] ?: 0) + 1
            failures[service] = next
            next
        }
        if (count < FailuresBeforeDown) return
        markDown(service, message)
    }

    /**
     * Mark a service down immediately, skipping the debounce.
     *
     * For the one case where waiting for a second failure would be dishonest: the login has
     * already given up on this server and put the user into a cached session, so the app is
     * *acting* on the outage. Making the banner that explains why wait for another request —
     * which, offline, may never be sent — is how the user ends up in offline mode with nothing
     * on screen saying so.
     */
    fun markDown(service: PokyhService, message: String?, unlessOkSince: Long = Long.MAX_VALUE) {
        _states.update { current ->
            val previous = current[service] ?: ServiceState()
            // It answered after the moment the caller is asking about — the outage being reported
            // is already disproven, and marking it down would leave a stale banner up.
            if (previous.reachable == true && previous.lastOkAt >= unlessOkSince) return@update current
            current + (service to previous.copy(reachable = false, message = message))
        }
    }

    /** Back to "unknown" for every service currently marked down; the next request decides. */
    fun forgetOutages() {
        synchronized(failures) { failures.clear() }
        _states.update { current ->
            current.mapValues { (_, state) -> if (state.reachable == false) state.copy(reachable = null, message = null) else state }
        }
    }

    /** Forget everything — on sign-out, so the next account starts without the last one's outage. */
    fun reset() {
        synchronized(failures) { failures.clear() }
        _states.value = PokyhService.entries.associateWith { ServiceState() }
    }

    private companion object {
        const val FailuresBeforeDown = 2
    }
}

/** The services currently known to be down, in enum order. Unknown is not down — see [ServiceState]. */
fun Map<PokyhService, ServiceState>.unreachable(): List<PokyhService> =
    PokyhService.entries.filter { this[it]?.reachable == false }
