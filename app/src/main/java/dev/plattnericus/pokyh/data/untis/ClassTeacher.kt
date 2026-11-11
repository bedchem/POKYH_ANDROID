package dev.plattnericus.pokyh.data.untis

import dev.plattnericus.pokyh.data.json.JsonDyn

/**
 * Who gets an excuse: the class teacher alone, never the deputy. WebUntis' message recipients
 * (CLASS_TEACHERS) list both without saying which is which, named "Girtler Wolfram, Wolfram (Gi-Wo)".
 * The class timetable picker (`timetable/filter?resourceType=CLASS`) says it per class:
 * `classTeacher1` is the class teacher, `classTeacher2` the deputy. The short name in brackets ties
 * the two together (backend webuntis/classTeacher.ts, iOS ClassTeacherPick).
 */
object ClassTeacher {
    /** A teacher as the picker names them: "Gi-Wo", "Girtler Wolfram". */
    data class Named(val shortName: String, val longName: String)

    /** The own class's class teacher ([first]) and deputy ([second]). */
    data class OfClass(val first: Named?, val second: Named?)

    /**
     * The class teachers of the own class in the picker's answer: the class it preselects, or the
     * only one it offers. Null when it names no such class.
     */
    fun read(json: JsonDyn): OfClass? {
        val classes = json["classes"].array.filter { it.dictionary.isNotEmpty() }
        val preselected = json["preSelected"]["id"].int
        val own = classes.firstOrNull { preselected != null && it["class"]["id"].int == preselected }
            ?: classes.singleOrNull()
            ?: return null
        return OfClass(teacher(own["classTeacher1"]), teacher(own["classTeacher2"]))
    }

    /** Whether a recipient's display name ("Girtler Wolfram, Wolfram (Gi-Wo)") is this teacher. */
    fun names(name: String, teacher: Named): Boolean {
        val n = norm(name)
        if (teacher.shortName.isNotEmpty() && n.contains("(${norm(teacher.shortName)})")) return true
        if (teacher.longName.isEmpty()) return false
        val long = norm(teacher.longName)
        return n == long || n.startsWith("$long,") || n.startsWith("$long (")
    }

    /**
     * The class teacher among the class teachers the account may write to. Without a name to go
     * by, the one left once the deputy is out. Null when it can't be told: then nobody gets the
     * excuse, rather than the deputy.
     */
    fun <T> pick(candidates: List<T>, teachers: OfClass?, name: (T) -> String): T? {
        val second = teachers?.second
        fun isDeputy(candidate: T) = second != null && names(name(candidate), second)
        val first = teachers?.first
        if (first != null) {
            val hits = candidates.filter { names(name(it), first) && !isDeputy(it) }
            if (hits.size == 1) return hits[0]
        }
        return candidates.filterNot { isDeputy(it) }.singleOrNull()
    }

    private fun norm(s: String) = s.lowercase().trim().split(Regex("\\s+")).joinToString(" ")

    private fun teacher(node: JsonDyn): Named? {
        if (node.dictionary.isEmpty()) return null
        val short = node["shortName"].string?.trim().orEmpty()
        // "Girtler Wolfram (Gi-Wo)" without the short name, when there is no long name.
        val long = node["longName"].string?.trim().orEmpty()
            .ifEmpty { node["displayName"].string?.trim().orEmpty().replace(Regex("\\s*\\([^)]*\\)\\s*$"), "") }
        return if (short.isEmpty() && long.isEmpty()) null else Named(short, long)
    }
}
