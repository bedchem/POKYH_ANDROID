package dev.plattnericus.pokyh.data.untis

import dev.plattnericus.pokyh.data.model.TimetableEntry
import java.util.UUID

// ── Slot-/Tag-Logik — 1:1 Port von `Timetable.buildSlots`/`dayKind` (TimetableView.swift) ──
// Pure functions only: no network, no Compose/UI dependency, so both a ViewModel and any
// later home-screen widget can reuse them.

enum class SlotKind { NORMAL, CANCELLED, REPLACEMENT, EXAM, EVENT }
enum class DayKind { NORMAL, HOLIDAY, ALL_CANCELLED, ALL_REPLACEMENT, FULL_DAY_EVENT, WEEKEND }

data class MergedSlot(
    val id: String = UUID.randomUUID().toString(),
    val display: TimetableEntry,
    val replacement: TimetableEntry? = null,
    val kind: SlotKind,
)

/** HHmm int → minutes since midnight (`Fmt.minutes`). */
private fun minutesOfTime(t: Int): Int {
    val s = t.toString().padStart(4, '0')
    return (s.substring(0, 2).toIntOrNull() ?: 0) * 60 + (s.substring(2, 4).toIntOrNull() ?: 0)
}

object TimetableSlots {

    /** Groups one day's entries by start time, merging cancelled+active pairs into display slots. */
    fun buildSlots(dayEntries: List<TimetableEntry>): List<MergedSlot> {
        val groups = mutableMapOf<Int, MutableList<TimetableEntry>>()
        for (e in dayEntries) groups.getOrPut(e.startTime) { mutableListOf() }.add(e)

        val slots = mutableListOf<MergedSlot>()
        for (groupStart in groups.keys.sorted()) {
            val group = groups.getValue(groupStart)
            val cancelled = group.filter { it.isCancelled }
            var active = group.filter { !it.isCancelled }

            val spanningCancelled = if (cancelled.isEmpty()) {
                dayEntries.filter {
                    it.isCancelled && minutesOfTime(it.startTime) < minutesOfTime(groupStart) &&
                        minutesOfTime(it.endTime) > minutesOfTime(groupStart)
                }
            } else emptyList()
            val effectiveCancelled = if (cancelled.isEmpty()) spanningCancelled else cancelled

            if (active.isEmpty() && effectiveCancelled.isNotEmpty()) {
                val spanning = dayEntries.filter {
                    !it.isCancelled && minutesOfTime(it.startTime) < minutesOfTime(groupStart) &&
                        minutesOfTime(it.endTime) > minutesOfTime(groupStart)
                }
                if (spanning.isNotEmpty()) active = spanning
            }

            if (active.isEmpty()) {
                effectiveCancelled.firstOrNull()?.let {
                    slots.add(MergedSlot(display = it, replacement = null, kind = SlotKind.CANCELLED))
                }
                continue
            }
            if (effectiveCancelled.isNotEmpty()) {
                slots.add(MergedSlot(display = effectiveCancelled[0], replacement = active[0], kind = SlotKind.REPLACEMENT))
                continue
            }

            val display = active.firstOrNull { it.isExam }
                ?: active.firstOrNull { it.isAdditional }
                ?: active.firstOrNull { it.subjectName.isEmpty() && !it.note.isNullOrEmpty() }
                ?: active.firstOrNull { it.isSubstitution }
                ?: active[0]

            val kind = when {
                display.isExam -> SlotKind.EXAM
                display.isAdditional -> SlotKind.NORMAL
                display.subjectName.isEmpty() && !display.note.isNullOrEmpty() -> SlotKind.EVENT
                display.isSubstitution -> SlotKind.REPLACEMENT
                else -> SlotKind.NORMAL
            }
            slots.add(MergedSlot(display = display, replacement = null, kind = kind))
        }

        // Drop cancelled slots that are visually covered by an active slot.
        val activeSlots = slots.filter { !it.display.isCancelled }
        return slots.filter { slot ->
            if (!slot.display.isCancelled) return@filter true
            val sm = minutesOfTime(slot.display.startTime)
            val em = minutesOfTime(slot.display.endTime)
            activeSlots.none { a -> minutesOfTime(a.display.startTime) <= sm && minutesOfTime(a.display.endTime) >= em }
        }
    }

    fun dayKind(dayEntries: List<TimetableEntry>, hasOtherDayEntries: Boolean, index: Int): DayKind =
        dayKind(dayEntries, buildSlots(dayEntries), hasOtherDayEntries, index)

    /** Variant taking pre-computed slots (avoids a double `buildSlots` call). */
    fun dayKind(dayEntries: List<TimetableEntry>, slots: List<MergedSlot>, hasOtherDayEntries: Boolean, index: Int): DayKind {
        if (index == 5 && dayEntries.isEmpty()) return DayKind.WEEKEND
        val base = baseDayKind(dayEntries, hasOtherDayEntries)
        if (base == DayKind.NORMAL && slots.isNotEmpty() && slots.all { it.kind == SlotKind.REPLACEMENT }) {
            val first = slots[0].replacement
            val same = slots.all {
                it.replacement?.subjectName == first?.subjectName &&
                    it.replacement?.note == first?.note &&
                    it.replacement?.teacherName == first?.teacherName
            }
            if (same) return DayKind.ALL_REPLACEMENT
        }
        return base
    }

    private fun baseDayKind(dayEntries: List<TimetableEntry>, hasOtherDayEntries: Boolean): DayKind {
        if (dayEntries.isEmpty()) return if (hasOtherDayEntries) DayKind.HOLIDAY else DayKind.NORMAL
        if (dayEntries.size == 1 && !dayEntries[0].isCancelled && dayEntries[0].subjectName.isEmpty() && !dayEntries[0].note.isNullOrEmpty()) {
            return DayKind.FULL_DAY_EVENT
        }
        if (dayEntries.all { it.isCancelled }) return DayKind.ALL_CANCELLED
        return DayKind.NORMAL
    }
}

// ── Grid geometry constants (TimetableView.swift `pxPerMin` / `gutter` / `periods`) ────────
// Density-independent: screens multiply PX_PER_MINUTE by 1.dp and GRID_GUTTER_DP by 1.dp.

const val PX_PER_MINUTE: Float = 0.82f
const val GRID_GUTTER_DP: Int = 42

/** One fixed timetable period: number + start/end in minutes-since-midnight. */
data class TimetablePeriod(val number: Int, val startMinute: Int, val endMinute: Int)

/** Fixed 10-period grid, 07:50–16:45, exactly as iOS (`TimetableView.periods`). */
val TIMETABLE_PERIODS: List<TimetablePeriod> = listOf(
    TimetablePeriod(1, 470, 520),
    TimetablePeriod(2, 520, 570),
    TimetablePeriod(3, 570, 620),
    TimetablePeriod(4, 635, 685),
    TimetablePeriod(5, 685, 735),
    TimetablePeriod(6, 735, 785),
    TimetablePeriod(7, 795, 845),
    TimetablePeriod(8, 845, 895),
    TimetablePeriod(9, 905, 955),
    TimetablePeriod(10, 955, 1005),
)
