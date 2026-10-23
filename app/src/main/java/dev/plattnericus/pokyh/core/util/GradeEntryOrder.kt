package dev.plattnericus.pokyh.core.util

import android.content.Context
import dev.plattnericus.pokyh.data.model.GradeEntry
import dev.plattnericus.pokyh.data.model.SubjectGrades
import org.json.JSONObject

/**
 * The order grades were *entered* in WebUntis — what "Kürzlich hinzugefügt" (Noten), "Zuletzt
 * eingetragen" (Home) and the widget show: not the exam date, the moment the grade appeared.
 * Maths and Italian entered on the exam day and German a week later list German first, though its
 * exam was the earliest.
 *
 * WebUntis' grade ids don't follow that order everywhere, so the order comes from, in turn:
 *  1. the time WebUntis says the grade was entered or last changed ([GradeEntry.enteredAt]), when
 *     its answer carries one;
 *  2. the moment this device first saw the grade — every load of the grades and the background
 *     sync note new ids here, so a grade entered a week late shows up as new a week late;
 *  3. the id, then the exam date, for grades that were all there the first time the app looked.
 */
object GradeEntryOrder {
    private const val PREFS = "pokyh_grade_order"
    private const val KEY = "first_seen"
    /** How many ids are remembered (the newest); a school year has a few hundred grades. */
    private const val LIMIT = 3000

    private var prefs: android.content.SharedPreferences? = null
    private var firstSeen: MutableMap<Long, Long>? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    @Synchronized
    private fun map(): MutableMap<Long, Long> {
        firstSeen?.let { return it }
        val out = HashMap<Long, Long>()
        runCatching {
            val json = JSONObject(prefs?.getString(KEY, null) ?: "{}")
            for (k in json.keys()) k.toLongOrNull()?.let { out[it] = json.getLong(k) }
        }
        firstSeen = out
        return out
    }

    /**
     * Notes the grades not seen before. On the very first look everything is "already there"
     * (time 0), so only grades that appear afterwards count as newly entered.
     */
    @Synchronized
    fun observe(subjects: List<SubjectGrades>) {
        val ids = subjects.flatMap { s -> s.grades.map { it.id } }.filter { it != 0L }
        if (ids.isEmpty()) return
        val m = map()
        val first = m.isEmpty()
        val now = System.currentTimeMillis()
        var changed = false
        for (id in ids) {
            if (!m.containsKey(id)) {
                m[id] = if (first) 0L else now
                changed = true
            }
        }
        if (!changed) return
        if (m.size > LIMIT) {
            val keep = m.entries.sortedByDescending { it.value }.take(LIMIT).associate { it.key to it.value }
            m.clear()
            m.putAll(keep)
        }
        val json = JSONObject()
        for ((k, v) in m) json.put(k.toString(), v)
        prefs?.edit()?.putString(KEY, json.toString())?.apply()
    }

    /** When the grade was entered, as far as can be told; 0 when it was there from the start. */
    fun enteredAt(g: GradeEntry): Long = g.enteredAt ?: map()[g.id] ?: 0L

    /** Newest entered first (see the class comment). */
    fun <T> comparator(grade: (T) -> GradeEntry): Comparator<T> =
        compareByDescending<T> { enteredAt(grade(it)) }
            .thenByDescending { grade(it).id }
            .thenByDescending { grade(it).date }
}
