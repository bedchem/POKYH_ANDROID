package dev.plattnericus.pokyh.core.usage

import android.os.SystemClock
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.state.AppState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Nutzungsstatistik fürs Admin-Panel: wie lange POKYH im Vordergrund ist und auf welcher
 * Plattform. In der Datenschutzerklärung unter „Nutzungsstatistik" beschrieben.
 *
 *  - Nur angemeldet; ohne POKYH-Konto wird nichts gesendet.
 *  - Pro Besuch eine zufällige ID, nur im Speicher — keine Geräte-ID, nichts auf der Platte.
 *  - Sofort beim Öffnen bzw. bei der Anmeldung, dann alle 30 Sekunden im Vordergrund ein Ping
 *    mit den Vordergrund-Sekunden und den geöffneten Tabs seit dem letzten, und ein letzter
 *    beim Wechsel in den Hintergrund.
 *  - War die App länger als 30 Minuten im Hintergrund, beginnt ein neuer Besuch.
 *
 * Alle Zustände werden nur auf dem Main-Thread angefasst ([scope] läuft dort).
 */
@Singleton
class UsageReporter @Inject constructor(
    private val backendClient: BackendClient,
    private val appState: AppState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var visitId: String? = null
    private var visitUid: String? = null
    private var foregroundSince: Long? = null
    private var backgroundedAt: Long? = null
    private var pendingMs = 0L
    private var pendingScreens = 0
    private var ticker: Job? = null
    private var started = false

    /** Einmal pro Prozess; zählt ab dann jeden Tab-Wechsel als geöffneten Bildschirm. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            appState.selectedTab.drop(1).collect { pendingScreens += 1 }
        }
        // Eine Anmeldung (oder ein Kontowechsel) im Vordergrund beginnt den Besuch sofort,
        // nicht erst mit dem nächsten Ping — so erscheint sie gleich im Admin-Panel.
        scope.launch {
            combine(appState.phase, appState.session) { phase, session ->
                if (phase == AppState.Phase.Authed) session?.stableUid?.takeIf { it.isNotBlank() } else null
            }
                .distinctUntilChanged()
                .collect { uid -> if (uid != null && ticker != null && uid != visitUid) send() }
        }
    }

    /** Wired to `ON_START` (ProcessLifecycleOwner). */
    fun onForeground() {
        val now = SystemClock.elapsedRealtime()
        val since = backgroundedAt
        if (since != null && now - since > NEW_VISIT_AFTER_MS) visitId = null
        backgroundedAt = null
        if (foregroundSince == null) foregroundSince = now
        ticker?.cancel()
        ticker = scope.launch {
            send()
            while (isActive) {
                delay(PING_INTERVAL_MS)
                send()
            }
        }
    }

    /** Wired to `ON_STOP` (ProcessLifecycleOwner). */
    fun onBackground() {
        ticker?.cancel()
        ticker = null
        send(final = true)
        foregroundSince = null
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun send(final: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        foregroundSince?.let { since ->
            pendingMs += now - since
            foregroundSince = if (final) null else now
        }

        val session = appState.session.value
        val uid = session?.stableUid?.takeIf { it.isNotBlank() }
        val token = session?.apiToken
        if (appState.phase.value != AppState.Phase.Authed || uid == null || token == null) {
            visitId = null
            visitUid = null
            pendingMs = 0
            pendingScreens = 0
            return
        }
        if (visitId == null || visitUid != uid) {
            // Zeit unter einem anderen Konto (oder abgemeldet) gehört nicht zu diesem.
            if (visitId != null) {
                pendingMs = 0
                pendingScreens = 0
            }
            visitId = UUID.randomUUID().toString()
            visitUid = uid
        }
        val id = visitId ?: return

        val seconds = (pendingMs / 1000).toInt()
        pendingMs -= seconds * 1000L
        val screens = pendingScreens
        pendingScreens = 0

        // Eigener Job statt [ticker]: der letzte Ping beim Wechsel in den Hintergrund darf
        // nicht mit dem Ticker abgebrochen werden.
        scope.launch(Dispatchers.IO) {
            backendClient.usagePing(id, seconds, screens, token)
        }
    }

    private companion object {
        const val PING_INTERVAL_MS = 30_000L
        const val NEW_VISIT_AFTER_MS = 30 * 60_000L
    }
}
