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
 * The popups due right now, shown as one stack, which of them is on screen, and which have been
 * on screen for the minimum viewing time ([viewed], by "id:revision").
 */
data class PopupStack(
    val popups: List<ApiPopup> = emptyList(),
    val index: Int = 0,
    val viewed: Set<String> = emptySet(),
) {
    val current: ApiPopup? get() = popups.getOrNull(index)
    val isLast: Boolean get() = index >= popups.lastIndex

    /** The current popup hasn't been on screen long enough yet: no closing, no skipping ahead. */
    val locked: Boolean get() = current?.let { popupKey(it) !in viewed } ?: false

    /** Back is always allowed; forward only to viewed popups, or to the next one once unlocked. */
    fun canReach(i: Int): Boolean {
        if (i !in popups.indices || i == index) return false
        return i < index || popupKey(popups[i]) in viewed || (i == index + 1 && !locked)
    }
}

internal fun popupKey(p: ApiPopup) = "${p.id}:${p.revision}"

/**
 * Admin announcement popups (backend `/popups/active`), shown as one stack: "Weiter" steps
 * through it, the last one says "Fertig". Every popup has to be on screen for a few seconds
 * first (the countdown in [AnnouncementPopupDialog]); until then nothing closes or skips it.
 * A popup counts as seen once it has been on screen that long — never just because it was
 * composed, so one that flashed past during a sign-in check comes back. Closing (X, back, tap
 * outside) closes the whole stack and counts every popup in it as seen, so the ones skipped by
 * closing don't return on the next start or sign-in.
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

    /** The popup has been on screen for the minimum viewing time: unlock it and count it as seen. */
    fun markViewed(popup: ApiPopup) {
        _stack.update { s -> s.copy(viewed = s.viewed + popupKey(popup)) }
        markSeen(listOf(popup))
    }

    fun goTo(index: Int) {
        _stack.update { s -> if (s.canReach(index)) s.copy(index = index) else s }
    }

    fun next() {
        val s = _stack.value
        if (s.locked) return
        if (s.isLast) close() else goTo(s.index + 1)
    }

    fun back() = goTo(_stack.value.index - 1)

    fun close() {
        val s = _stack.value
        if (s.locked) return
        _stack.value = PopupStack()
        markSeen(s.popups)
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

        fun keyOf(p: ApiPopup) = popupKey(p)

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
