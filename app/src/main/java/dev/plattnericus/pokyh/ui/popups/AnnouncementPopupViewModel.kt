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

/** The popups due right now, shown as one stack, and which of them is on screen. */
data class PopupStack(val popups: List<ApiPopup> = emptyList(), val index: Int = 0) {
    val current: ApiPopup? get() = popups.getOrNull(index)
    val isLast: Boolean get() = index >= popups.lastIndex
}

/**
 * Admin announcement popups (backend `/popups/active`), shown as one stack: "Weiter" steps
 * through it, the last one says "Fertig". Closing (X, back, tap outside) closes the whole stack
 * and counts every popup in it as seen, so the ones skipped by closing don't return on the next
 * start or sign-in.
 *
 * The backend only filters by time window; "is it due on this device" is decided here, with the
 * same rule as the website (`pokyh-frontend/lib/popup-schedule.ts`): the window is split into
 * `showCount` equal slots and a popup is shown at most once per slot — "7× in 7 Tagen" = at most
 * once a day. Missed slots are not caught up and nothing shows after the window ends.
 */
@HiltViewModel
class AnnouncementPopupViewModel @Inject constructor(
    private val backendClient: BackendClient,
    private val prefsStore: PreferencesStore,
) : ViewModel() {

    private val _stack = MutableStateFlow(PopupStack())
    val stack: StateFlow<PopupStack> = _stack.asStateFlow()

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
            val due = live.filter { isDue(it, seen, System.currentTimeMillis()) }
            if (due.isEmpty()) return@launch
            _stack.update { s ->
                val known = s.popups.map { keyOf(it) }.toSet()
                s.copy(popups = s.popups + due.filter { keyOf(it) !in known })
            }
        }
    }

    /** Counts as seen the moment it is on screen, not when it is dismissed. */
    fun markShown(popup: ApiPopup) = markSeen(listOf(popup))

    fun goTo(index: Int) {
        _stack.update { s -> if (index in s.popups.indices) s.copy(index = index) else s }
    }

    fun next() {
        if (_stack.value.isLast) close() else goTo(_stack.value.index + 1)
    }

    fun back() = goTo(_stack.value.index - 1)

    fun close() {
        val popups = _stack.value.popups
        _stack.value = PopupStack()
        markSeen(popups)
    }

    private fun markSeen(popups: List<ApiPopup>) {
        if (popups.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            prefsStore.updatePopupsSeen { seen -> withSeen(seen, popups, now) }
        }
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

        /**
         * Spends the current slot of every popup that is still due. One already recorded for this
         * slot is left alone, so marking the one on screen again when the stack closes never
         * costs it a second showing.
         */
        fun withSeen(seen: Map<String, PopupSeenEntry>, popups: List<ApiPopup>, now: Long): Map<String, PopupSeenEntry> {
            val next = seen.filterValues { now - it.at < PRUNE_AFTER_MS }.toMutableMap()
            for (p in popups) {
                if (!isDue(p, next, now)) continue
                val slot = currentSlot(p, now) ?: continue
                next[keyOf(p)] = PopupSeenEntry(slot = slot, count = (next[keyOf(p)]?.count ?: 0) + 1, at = now)
            }
            return next
        }
    }
}
