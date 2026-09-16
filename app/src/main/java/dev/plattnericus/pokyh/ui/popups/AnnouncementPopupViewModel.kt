package dev.plattnericus.pokyh.ui.popups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.ApiPopup
import dev.plattnericus.pokyh.data.storage.PopupSeenEntry
import dev.plattnericus.pokyh.data.storage.PreferencesStore
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Admin announcement popups (backend `/popups/active`), queued one at a time.
 *
 * The backend only filters by time window; "is it due on this device" is decided here, with the
 * same rule as the website (`pokyh-frontend/lib/popups.ts`): the window is split into
 * `showCount` equal slots and a popup is shown at most once per slot — "7× in 7 Tagen" = at most
 * once a day. Missed slots are not caught up and nothing shows after the window ends.
 */
@HiltViewModel
class AnnouncementPopupViewModel @Inject constructor(
    private val backendClient: BackendClient,
    private val prefsStore: PreferencesStore,
) : ViewModel() {

    private val _queue = MutableStateFlow<List<ApiPopup>>(emptyList())
    val queue: StateFlow<List<ApiPopup>> = _queue.asStateFlow()

    private var lastCheck = 0L

    /** Called whenever the app comes to the foreground; throttled so tab hopping is free. */
    fun check() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < RECHECK_MS) return
        lastCheck = now
        viewModelScope.launch {
            // Popups are optional — a network or parse failure never surfaces.
            val live = runCatching { backendClient.activePopups() }.getOrNull() ?: return@launch
            val seen = prefsStore.popupsSeen.first()
            val due = live.filter { isDue(it, seen, now) }
            if (due.isEmpty()) return@launch
            _queue.update { current ->
                val known = current.map { keyOf(it) }.toSet()
                current + due.filter { keyOf(it) !in known }
            }
        }
    }

    /** Counts as seen the moment it is on screen, not when it is dismissed. */
    fun markShown(popup: ApiPopup) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val slot = currentSlot(popup, now) ?: return@launch
            val seen = prefsStore.popupsSeen.first()
            if (!isDue(popup, seen, now)) return@launch
            val previous = seen[keyOf(popup)]
            val updated = seen
                .filterValues { now - it.at < PRUNE_AFTER_MS }
                .plus(keyOf(popup) to PopupSeenEntry(slot = slot, count = (previous?.count ?: 0) + 1, at = now))
            prefsStore.setPopupsSeen(updated)
        }
    }

    fun dismissCurrent() {
        _queue.update { it.drop(1) }
    }

    private companion object {
        const val RECHECK_MS = 10 * 60 * 1000L
        const val PRUNE_AFTER_MS = 365L * 24 * 60 * 60 * 1000

        fun keyOf(p: ApiPopup) = "${p.id}:${p.revision}"

        fun parse(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

        fun currentSlot(p: ApiPopup, now: Long): Int? {
            val end = parse(p.endsAt)
            if (end != null && end <= now) return null
            val start = parse(p.startsAt)
            val slotMs = p.slotMs
            if (slotMs == null || slotMs <= 0 || start == null) return 0
            if (now < start) return null
            return ((now - start) / slotMs).toInt().coerceAtMost(p.showCount - 1)
        }

        fun isDue(p: ApiPopup, seen: Map<String, PopupSeenEntry>, now: Long): Boolean {
            val slot = currentSlot(p, now) ?: return false
            val entry = seen[keyOf(p)] ?: return true
            return entry.count < p.showCount && slot > entry.slot
        }
    }
}
