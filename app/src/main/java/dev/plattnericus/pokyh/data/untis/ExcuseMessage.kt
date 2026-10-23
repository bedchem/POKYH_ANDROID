package dev.plattnericus.pokyh.data.untis

import dev.plattnericus.pokyh.core.i18n.Lang
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import java.time.LocalDate

/**
 * Excusing a past absence. WebUntis lets students of age and guardians report an absence ahead
 * ("Abwesenheit melden"), but refuses the edit that would add a reason to one that is already
 * over. So the excuse goes to the class teachers as a MessageCenter message, written here —
 * a port of pokyh-backend's `src/webuntis/excuseMessage.ts`, word for word, so the web and the
 * app write the same messages and find each other's again.
 *
 * The subject names the absence in a fixed form (a prefix, the dates, the times), and that is
 * how the absences page finds the message again in the account's "Gesendet" folder: nothing
 * about it is stored anywhere else.
 */
object ExcuseMessage {

    /** A time span as YYYYMMDDHHMM numbers (202512170750). */
    data class Span(val start: Long, val end: Long)

    private class Wording(
        val prefix: String,
        val sep: String,
        val weekdays: List<String>, // Sunday first, as JavaScript's getDay()
        val hello: String,
        val one: (String) -> String,
        val many: String,
        val oneChild: (String, String) -> String,
        val manyChild: (String) -> String,
        val onDay: (String, String?, String?) -> String,
        val at: (String, String) -> String,
        val range: (String, String) -> String,
        val reason: String,
        val note: String,
        val bye: String,
    )

    private val TEXT: Map<Lang, Wording> = mapOf(
        Lang.De to Wording(
            prefix = "Entschuldigung",
            sep = ".",
            weekdays = listOf("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"),
            hello = "Guten Tag,",
            one = { w -> "hiermit entschuldige ich meine Abwesenheit $w." },
            many = "hiermit entschuldige ich meine folgenden Abwesenheiten:",
            oneChild = { c, w -> "hiermit entschuldige ich die Abwesenheit meines Kindes $c $w." },
            manyChild = { c -> "hiermit entschuldige ich die folgenden Abwesenheiten meines Kindes $c:" },
            onDay = { d, f, t -> if (f != null && t != null) "am $d, von $f bis $t" else "am $d (ganztägig)" },
            at = { d, t -> "$d, $t" },
            range = { f, t -> "vom $f bis $t" },
            reason = "Grund",
            note = "Bemerkung",
            bye = "Mit freundlichen Grüßen",
        ),
        Lang.It to Wording(
            prefix = "Giustificazione",
            sep = "/",
            weekdays = listOf("domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato"),
            hello = "Buongiorno,",
            one = { w -> "con la presente giustifico la mia assenza $w." },
            many = "con la presente giustifico le mie seguenti assenze:",
            oneChild = { c, w -> "con la presente giustifico l’assenza di mio/a figlio/a $c $w." },
            manyChild = { c -> "con la presente giustifico le seguenti assenze di mio/a figlio/a $c:" },
            onDay = { d, f, t -> if (f != null && t != null) "di $d dalle $f alle $t" else "di $d (tutto il giorno)" },
            at = { d, t -> "$d ore $t" },
            range = { f, t -> "dal $f al $t" },
            reason = "Motivo",
            note = "Nota",
            bye = "Cordiali saluti",
        ),
        Lang.En to Wording(
            prefix = "Absence excuse",
            sep = "/",
            weekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"),
            hello = "Hello,",
            one = { w -> "I am writing to excuse my absence $w." },
            many = "I am writing to excuse my following absences:",
            oneChild = { c, w -> "I am writing to excuse the absence of my child $c $w." },
            manyChild = { c -> "I am writing to excuse the following absences of my child $c:" },
            onDay = { d, f, t -> if (f != null && t != null) "on $d, from $f to $t" else "on $d (all day)" },
            at = { d, t -> "$d, $t" },
            range = { f, t -> "from $f to $t" },
            reason = "Reason",
            note = "Note",
            bye = "Kind regards",
        ),
    )

    /** HHMM (750) or minutes of the day (470), as the classic API mixes them → minutes of the day. */
    private fun minutesOf(t: Int): Int {
        if (t <= 0) return 0
        if (t > 2359 || t % 100 > 59) return t
        return (t / 100) * 60 + t % 100
    }

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    private fun clock(t: Int): String {
        val m = minutesOf(t)
        return "${pad(m / 60)}:${pad(m % 60)}"
    }

    private fun date(d: Int, lang: Lang): String {
        val sep = TEXT.getValue(lang).sep
        return "${pad(d % 100)}$sep${pad((d / 100) % 100)}$sep${d / 10000}"
    }

    /** "Mittwoch, 17.12.2025" / "mercoledì 17/12/2025". */
    private fun day(d: Int, lang: Lang): String {
        val dow = runCatching { LocalDate.of(d / 10000, (d / 100) % 100, d % 100).dayOfWeek.value % 7 }.getOrDefault(0)
        val weekday = TEXT.getValue(lang).weekdays[dow]
        return if (lang == Lang.It) "$weekday ${date(d, lang)}" else "$weekday, ${date(d, lang)}"
    }

    private fun hasTimes(a: AbsenceEntry) = a.startTime > 0 || a.endTime > 0

    /** Start and end of an absence as YYYYMMDDHHMM. */
    fun span(a: AbsenceEntry): Span {
        val s = minutesOf(a.startTime)
        val e = if (hasTimes(a)) minutesOf(a.endTime) else 23 * 60 + 59
        return Span(
            start = a.startDate.toLong() * 10_000 + (s / 60) * 100 + s % 60,
            end = a.endDate.toLong() * 10_000 + (e / 60) * 100 + e % 60,
        )
    }

    /** The span all the absences cover together. */
    fun spanOf(absences: List<AbsenceEntry>): Span {
        val spans = absences.map(::span)
        return Span(spans.minOf { it.start }, spans.maxOf { it.end })
    }

    /** One absence in words, for the body. */
    private fun whenText(a: AbsenceEntry, lang: Lang): String {
        val t = TEXT.getValue(lang)
        if (a.startDate == a.endDate) {
            return if (hasTimes(a)) t.onDay(day(a.startDate, lang), clock(a.startTime), clock(a.endTime))
            else t.onDay(day(a.startDate, lang), null, null)
        }
        return if (hasTimes(a)) {
            t.range(t.at(day(a.startDate, lang), clock(a.startTime)), t.at(day(a.endDate, lang), clock(a.endTime)))
        } else {
            t.range(day(a.startDate, lang), day(a.endDate, lang))
        }
    }

    /** The span in the subject, what [parseSubject] reads back: "17.12.2025, 07:50-13:05" … */
    private fun subjectSpan(first: AbsenceEntry, last: AbsenceEntry, lang: Lang): String {
        val times = hasTimes(first) || hasTimes(last)
        if (first.startDate == last.endDate) {
            return if (times) "${date(first.startDate, lang)}, ${clock(first.startTime)}-${clock(last.endTime)}" else date(first.startDate, lang)
        }
        return if (times) {
            "${date(first.startDate, lang)}, ${clock(first.startTime)} - ${date(last.endDate, lang)}, ${clock(last.endTime)}"
        } else {
            "${date(first.startDate, lang)} - ${date(last.endDate, lang)}"
        }
    }

    /** Subject and plain-text body of the excuse. */
    fun compose(
        absences: List<AbsenceEntry>,
        lang: Lang,
        reasonName: String?,
        note: String?,
        childName: String?,
        signature: String?,
    ): Pair<String, String> {
        val t = TEXT.getValue(lang)
        val sorted = absences.sortedBy { span(it).start }
        val first = sorted.first()
        val last = sorted.maxBy { span(it).end }
        val subject = "${t.prefix}: ${subjectSpan(first, last, lang)}"

        val child = childName?.trim()?.takeIf { it.isNotEmpty() }
        val lines = mutableListOf(t.hello, "")
        if (sorted.size == 1) {
            val w = whenText(first, lang)
            lines.add(if (child != null) t.oneChild(child, w) else t.one(w))
        } else {
            lines.add(if (child != null) t.manyChild(child) else t.many)
            for (a in sorted) lines.add("- ${whenText(a, lang)}")
        }
        val reason = reasonName?.trim()?.takeIf { it.isNotEmpty() }
        val n = note?.trim()?.takeIf { it.isNotEmpty() }
        if (reason != null || n != null) {
            lines.add("")
            if (reason != null) lines.add("${t.reason}: $reason")
            if (n != null) lines.add("${t.note}: $n")
        }
        lines.add("")
        lines.add(t.bye)
        signature?.trim()?.takeIf { it.isNotEmpty() }?.let { lines.add(it) }
        return subject to lines.joinToString("\n")
    }

    private val PREFIXES = TEXT.values.map { "${it.prefix.lowercase()}:" }
    private val DATE = Regex("(\\d{1,2})[./](\\d{1,2})[./](\\d{4})")
    private val TIME = Regex("(?<![\\d.:/])(\\d{1,2}):(\\d{2})(?![\\d.:/])")

    /** The span an excuse subject names, in any of the languages; null for any other subject. */
    fun parseSubject(subject: String?): Span? {
        val s = subject.orEmpty().trim()
        val lower = s.lowercase()
        if (PREFIXES.none { lower.startsWith(it) }) return null
        val dates = DATE.findAll(s).map { m ->
            m.groupValues[3].toLong() * 10_000 + m.groupValues[2].toLong() * 100 + m.groupValues[1].toLong()
        }.toList()
        if (dates.isEmpty()) return null
        val times = TIME.findAll(s).map { m -> m.groupValues[1].toInt() * 100 + m.groupValues[2].toInt() }
            .filter { it / 100 < 24 && it % 100 < 60 }.toList()
        val startTime = if (times.size >= 2) times.first() else 0
        val endTime = if (times.size >= 2) times.last() else 2359
        val start = dates.first() * 10_000 + startTime
        val end = dates.last() * 10_000 + endTime
        return if (end > start) Span(start, end) else null
    }

    private val ESCAPES = mapOf('&' to "&amp;", '<' to "&lt;", '>' to "&gt;", '"' to "&quot;", '\'' to "&#39;")

    /** Plain text → the HTML WebUntis stores a message as (paragraphs, line breaks). */
    fun plainTextToHtml(text: String): String {
        val escaped = text.replace(Regex("\r\n?"), "\n").map { ESCAPES[it] ?: it.toString() }.joinToString("")
        return escaped.split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("") { "<p>${it.replace("\n", "<br>")}</p>" }
    }
}
