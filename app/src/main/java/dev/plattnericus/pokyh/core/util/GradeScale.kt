package dev.plattnericus.pokyh.core.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The grading scale of the account's school. A port of pokyh-backend's
 * src/webuntis/gradeScale.ts (the website gets it with the grades from the API); identical rules,
 * so web and app colour, count passes and draw the same way, from what WebUntis sends and nothing
 * else: the marks, the school year's last day, the school's postcode (from the school search).
 *
 * Italy marks 1 to 10 and 6 passes; Austria 1 to 5 and Germany 1 to 6, where 1 is best and 4
 * passes; Switzerland 1 to 6 with 6 best; German upper school 0 to 15 points. When the marks
 * don't settle it, [GradeScale.certain] is false and marks are shown without pass/fail colours.
 */
data class GradeScale(
    val kind: Kind,
    val min: Double,
    val max: Double,
    val higherIsBetter: Boolean,
    /** The worst mark that still passes. */
    val pass: Double,
    /** From here on a mark is excellent. */
    val excellent: Double,
    /** From here on a fail is critical. */
    val critical: Double,
    val certain: Boolean = true,
) {
    enum class Kind { It10, At5, De6, Ch6, De15 }

    private fun atLeast(a: Double, b: Double) = if (higherIsBetter) a >= b else a <= b

    fun passes(v: Double) = atLeast(v, pass)

    /** Passes with a little room: 6.4 and up in Italy, 3.6 and better in Germany. */
    fun passesWell(v: Double) = atLeast(v, if (higherIsBetter) pass + 0.4 else pass - 0.4)

    fun isExcellent(v: Double) = atLeast(v, excellent)

    /** A fail beyond rescue: 4 and below in Italy, 5.5 and worse in Germany. */
    fun isCritical(v: Double) = !passes(v) && atLeast(critical, v)

    /** How good the mark is, 0 (worst) to 1 (best). */
    fun goodness(v: Double): Double {
        val t = (v - min) / (max - min).takeIf { it != 0.0 }.let { it ?: 1.0 }
        return (if (higherIsBetter) t else 1 - t).coerceIn(0.0, 1.0)
    }

    fun clamp(v: Double) = v.coerceIn(min, max)

    /** The better of two marks. */
    fun better(a: Double, b: Double) = if (atLeast(a, b)) a else b

    /**
     * The mark as an Italian one: the pass mark is 6, the best mark 10, the worst 1 (a German 1
     * is a 10, its 4 a 6, its 6 a 1). The colour ramps, drawn for 1 to 10, then fit every scale.
     */
    fun italianEquivalent(v: Double): Double {
        val g = goodness(v)
        val gPass = goodness(pass)
        return if (passes(v)) 6 + 4 * ((g - gPass) / (1 - gPass).takeIf { it > 0 }.let { it ?: 1.0 })
        else 1 + 5 * (g / gPass.takeIf { it > 0 }.let { it ?: 1.0 })
    }
}

object GradeScales {
    private fun of(kind: GradeScale.Kind, certain: Boolean = true) = when (kind) {
        GradeScale.Kind.It10 -> GradeScale(kind, 1.0, 10.0, true, 6.0, 9.0, 4.0, certain)
        GradeScale.Kind.At5 -> GradeScale(kind, 1.0, 5.0, false, 4.0, 1.5, 5.0, certain)
        GradeScale.Kind.De6 -> GradeScale(kind, 1.0, 6.0, false, 4.0, 1.5, 5.5, certain)
        GradeScale.Kind.Ch6 -> GradeScale(kind, 1.0, 6.0, true, 4.0, 5.5, 3.0, certain)
        GradeScale.Kind.De15 -> GradeScale(kind, 0.0, 15.0, true, 5.0, 13.0, 3.0, certain)
    }

    val Italian: GradeScale = of(GradeScale.Kind.It10)

    /** The scale every screen colours by: the last one worked out from the account's grades. */
    var current: GradeScale by mutableStateOf(Italian)

    private val austrianWords = Regex("nicht\\s*genügend|nicht\\s*genuegend", RegexOption.IGNORE_CASE)
    private val germanWords = Regex("ungenügend|ungenuegend|mangelhaft|ausreichend|befriedigend", RegexOption.IGNORE_CASE)

    /** How many digits the school's postcode has: 4 (Austria, Switzerland), 5 (Germany, Italy) or null. */
    fun postcodeDigits(address: String?): Int? {
        val m = Regex("(?:^|[\\s,])(\\d{4,5})(?=[\\s,]|$)").find(address.orEmpty()) ?: return null
        return m.groupValues[1].length
    }

    /** An Italian school year, as WebUntis dates it, ends by 20 June (lbs-brixen: 11–16 June every year). */
    private fun italianYear(yearEnd: Int?): Boolean? {
        if (yearEnd == null || yearEnd < 19000101) return null
        val month = yearEnd / 100 % 100
        val day = yearEnd % 100
        if (month !in 1..12 || day < 1) return null
        return month < 6 || (month == 6 && day <= 20)
    }

    /**
     * The scale of [marks] (markDisplayValue to mark name), the backend's `inferGradeScale`:
     * a mark above 10 → points, above 6 → Italy; grade words → Austria or Germany; at a
     * four-digit school a 6 → Switzerland, else Austria (a guess); at a five-digit one a 1 or 2 →
     * Germany, else the school year decides (Italy when it ends by 20 June). Otherwise a guess.
     */
    fun infer(marks: List<Pair<Double, String>>, postcode: Int?, yearEnd: Int?): GradeScale {
        val values = marks.map { it.first }.filter { it.isFinite() && it > 0 }
        val names = marks.map { it.second.trim() }.filter { it.isNotEmpty() }.joinToString(" | ")
        val italian = italianYear(yearEnd)
        if (values.isEmpty()) {
            val kind = if (postcode == 4) GradeScale.Kind.At5 else if (italian == false) GradeScale.Kind.De6 else GradeScale.Kind.It10
            return of(kind, certain = false)
        }
        val max = values.max()
        val min = values.min()
        return when {
            max > 10 -> of(GradeScale.Kind.De15)
            max > 6 -> of(GradeScale.Kind.It10)
            austrianWords.containsMatchIn(names) -> of(GradeScale.Kind.At5)
            germanWords.containsMatchIn(names) -> of(GradeScale.Kind.De6)
            postcode == 4 -> if (max > 5) of(GradeScale.Kind.Ch6) else of(GradeScale.Kind.At5, certain = false)
            min <= 2.5 -> of(GradeScale.Kind.De6)
            italian != null -> of(if (italian) GradeScale.Kind.It10 else GradeScale.Kind.De6)
            else -> of(GradeScale.Kind.It10, certain = false)
        }
    }
}
