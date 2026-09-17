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

/**
 * Which of a lesson's three fields WebUntis reports as *changed* for this occurrence — the
 * lesson still happens, but not as timetabled (a different room, a stand-in teacher, a swapped
 * subject).
 *
 * This is a different thing from [SlotKind.REPLACEMENT], which is the harder case: a lesson
 * cancelled outright and a *separate* lesson put in its place. Here there is one lesson, and
 * WebUntis hands us its `original*` fields alongside the current ones. Untis Mobile highlights
 * exactly the field that differs (the room chip on a room change), and so does the week grid.
 */
data class SlotChanges(
    val subjectChanged: Boolean,
    val teacherChanged: Boolean,
    val roomChanged: Boolean,
) {
    val any: Boolean get() = subjectChanged || teacherChanged || roomChanged

    companion object {
        val none = SlotChanges(subjectChanged = false, teacherChanged = false, roomChanged = false)
    }
}

/**
 * [SlotChanges] for one entry.
 *
 * Two signals, and both are needed. WebUntis flags the *new* value in place
 * (`position3[].current.status == "ADDED"`, surfaced as [TimetableEntry.addedRooms] and friends)
 * far more often than it fills in an `original*` field — in a real week of this school's data,
 * every room change came through that way and `removed` was null throughout. Reading only
 * `original*`, which is what this did, meant no room change was ever highlighted.
 *
 * An `original*` field is only a change when it is present, non-blank AND actually different:
 * WebUntis fills these in for plenty of entries that did not change (it echoes the current value
 * back), and treating those as changes would light up half the week.
 */
fun TimetableEntry.changes(): SlotChanges {
    fun changed(original: String?, current: String): Boolean =
        !original.isNullOrBlank() && original.trim() != current.trim()
    // A cancelled lesson isn't "changed" — it's gone, and the strikethrough says so. Marking it
    // as changed as well would put a second, contradictory highlight on the same cell.
    if (isCancelled) return SlotChanges.none
    return SlotChanges(
        subjectChanged = addedSubjects.isNotEmpty() || changed(originalSubject, subjectName),
        teacherChanged = addedTeachers.isNotEmpty() || changed(originalTeacher, teacherName),
        roomChanged = addedRooms.isNotEmpty() || changed(originalRoom, roomName),
    )
}

/**
 * Splits a comma-joined field ([TimetableEntry.roomName] and friends) into its parts, each
 * tagged with whether WebUntis marked *that part* as new.
 *
 * A lesson moved into a second room comes back as `"Inf VI, a+2/05"` with only `Inf VI` added,
 * and highlighting the whole string would claim both rooms changed. The grid draws the parts and
 * gives a background to the new ones only.
 */
fun splitChanged(joined: String, added: List<String>): List<Pair<String, Boolean>> =
    joined.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        .map { part -> part to added.any { it.trim() == part } }

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

    /**
     * Variant taking pre-computed slots (avoids a double `buildSlots` call).
     *
     * A day of nothing but substitutions is **not** a special day: it is drawn lesson by lesson,
     * each as the 75/25 pair of the lesson that happens and the one it replaced. The whole-day
     * "Tag durchgehend ersetzt" card named no subject, teacher or room, so it said less than the
     * cells it covered. [DayKind.ALL_REPLACEMENT] is therefore never produced any more.
     */
    @Suppress("UNUSED_PARAMETER")
    fun dayKind(dayEntries: List<TimetableEntry>, slots: List<MergedSlot>, hasOtherDayEntries: Boolean, index: Int): DayKind {
        if (index == 5 && dayEntries.isEmpty()) return DayKind.WEEKEND
        return baseDayKind(dayEntries, hasOtherDayEntries)
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

/**
 * At iOS's 0.82 a standard 50-minute period is 41dp, and 41dp is *exactly* the subject, the
 * teacher and the room with nothing to spare — so the grid rounded down to two lines and the room
 * was the line that fell off, on most cells, most of the week. 0.95 gives the period 48dp, which
 * holds all three comfortably at any font scale; the week is ~70dp taller overall, in a view that
 * already scrolls.
 */
const val PX_PER_MINUTE: Float = 0.95f
const val GRID_GUTTER_DP: Int = 42

/** One fixed timetable period: number + start/end in minutes-since-midnight. */
data class TimetablePeriod(val number: Int, val startMinute: Int, val endMinute: Int)

/**
 * The 10-period grid this school runs, 07:50–16:45, exactly as iOS (`TimetableView.periods`).
 *
 * **This is a default, not a fact about every timetable.** WebUntis gives lesson times but not
 * the bell schedule on the route this app calls, so the axis uses these for its period numbers
 * and draws its rules from the lessons' own start/end times as well — and `WeekGrid` drops the
 * numbers entirely on a timetable whose lessons don't line up with them. A school with a
 * different schedule therefore gets a correct grid with clock times, never mislabelled periods.
 */
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
