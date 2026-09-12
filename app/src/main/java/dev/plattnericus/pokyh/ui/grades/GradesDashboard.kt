package dev.plattnericus.pokyh.ui.grades

import dev.plattnericus.pokyh.data.model.GradeEntry
import dev.plattnericus.pokyh.data.model.SubjectGrades
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.datetime.LocalDate

/**
 * Everything the Noten dashboard displays, computed once from the subject list.
 *
 * A deliberate 1:1 port of the web frontend's `dashboard` memo in `app/grades/page.tsx`
 * (github.com/bedchem/pokyh-frontend) — same inputs, same formulas, same rounding, same
 * geometry constants for the sparkline and the donut. The numbers on this screen have to agree
 * with the web app's for the same account, so this is the one place in the app where "port it
 * exactly" beats "write it the Kotlin way": the arithmetic is copied, not re-derived.
 *
 * Unlike the web version, this is computed even when there are no grades at all — the screen
 * shows its cards with zeroed values rather than replacing them with an empty state.
 */
internal data class GradesDashboard(
    val allCount: Int,
    val subjectCount: Int,
    val latestGradeDate: Int,
    val overallAvg: Double,
    val bestGrade: Double,
    val pos: Int,
    val neg: Int,
    val passRate: Double,
    /** Index 0 = grade 1 … index 9 = grade 10. */
    val distribution: List<Int>,
    val mode: Int,
    val median: Double,
    val sigma: Double,
    /** Change vs. the average excluding the last ~month. Positive = improving. */
    val delta: Double,
    val previousMonthAvg: Double,
    val spark: SparkPath,
    val sparkPoints: List<SparkPoint>,
    val recent: List<RecentGradeItem>,
    val donutSegments: List<DonutSegment>,
) {
    val hasGrades: Boolean get() = allCount > 0
}

/** The sparkline in the viewBox the web uses (200x60), so the shape matches exactly. */
internal data class SparkPath(val points: List<Pair<Float, Float>>)

internal data class SparkPoint(val x: Float, val y: Float, val avg: Double, val label: String)

internal data class RecentGradeItem(val id: Int, val subject: String, val numeric: Double, val date: Int)

/**
 * One donut arc, pre-computed in the web's 110x110 viewBox: centre (55,55), radius 29, stroke
 * 16, and a 0.035rad gap between segments. [tickStart]/[tickEnd]/[labelPos] are the leader line
 * and number that sit outside the ring.
 */
internal data class DonutSegment(
    val grade: Int,
    val count: Int,
    val startAngle: Float,
    val sweep: Float,
    val tickStart: Pair<Float, Float>,
    val tickEnd: Pair<Float, Float>,
    val labelPos: Pair<Float, Float>,
)

internal object DonutGeometry {
    const val VIEWPORT = 110f
    const val CX = 55f
    const val CY = 55f
    const val RADIUS = 29f
    const val STROKE = 16f

    /** Segments narrower than this don't get a leader line and label — they'd collide. */
    const val LABEL_MIN_SWEEP = 0.18f
}

internal object SparkGeometry {
    const val WIDTH = 200f
    const val HEIGHT = 60f
}

private val MonthLabels = listOf(
    "Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez",
)

/** `monthKey` — the `YYYYMM` prefix of a WebUntis `YYYYMMDD` date. */
private fun monthKey(date: Int): String = date.toString().take(6)

internal fun buildGradesDashboard(subjects: List<SubjectGrades>, today: LocalDate): GradesDashboard {
    data class Flat(val entry: GradeEntry, val subjectName: String)

    val allGrades = subjects
        .flatMap { s -> s.grades.map { Flat(it, s.subjectName) } }
        .sortedBy { it.entry.date }

    val values = allGrades.map { it.entry.markDisplayValue }.filter { it > 0 }
    val overallAvg = if (values.isEmpty()) 0.0 else values.sum() / values.size
    val bestGrade = values.maxOrNull() ?: 0.0
    val latestGradeDate = allGrades.lastOrNull()?.entry?.date ?: 0

    val pos = values.count { it >= 6 }
    val neg = values.count { it < 6 }
    val passRate = if (values.isEmpty()) 0.0 else pos.toDouble() / values.size * 100

    val distribution = MutableList(10) { 0 }
    values.forEach { v ->
        val idx = v.roundToInt().coerceIn(1, 10) - 1
        distribution[idx] += 1
    }
    val modeIdx = distribution.indexOf(distribution.max())

    val sortedVals = values.sorted()
    val median = when {
        sortedVals.isEmpty() -> 0.0
        sortedVals.size % 2 == 1 -> sortedVals[(sortedVals.size - 1) / 2]
        else -> (sortedVals[sortedVals.size / 2 - 1] + sortedVals[sortedVals.size / 2]) / 2
    }

    val sigma = if (values.isEmpty()) {
        0.0
    } else {
        sqrt(values.sumOf { (it - overallAvg) * (it - overallAvg) } / values.size)
    }

    // Vormonat: the overall average over everything OLDER than roughly a month, so the delta
    // reads "how has my average moved recently".
    val oneMonthAgoKey = run {
        val month = today.monthNumber
        val year = if (month == 1) today.year - 1 else today.year
        val prevMonth = if (month == 1) 12 else month - 1
        year * 10000 + prevMonth * 100 + today.dayOfMonth
    }
    val prevMonthVals = allGrades
        .filter { it.entry.date < oneMonthAgoKey }
        .map { it.entry.markDisplayValue }
        .filter { it > 0 }
    val previousMonthAvg = if (prevMonthVals.isEmpty()) overallAvg else prevMonthVals.sum() / prevMonthVals.size
    val delta = overallAvg - previousMonthAvg

    // Cumulative average per month: the running average of everything up to and including that
    // month, which is what makes the sparkline a trend rather than a noisy per-month series.
    val monthKeys = allGrades.map { monthKey(it.entry.date) }.distinct().sorted()
    val cumulativeAvgs = monthKeys.indices.map { i ->
        val upto = monthKeys.take(i + 1).toSet()
        val vals = allGrades
            .filter { monthKey(it.entry.date) in upto }
            .map { it.entry.markDisplayValue }
            .filter { it > 0 }
        if (vals.isEmpty()) 0.0 else vals.sum() / vals.size
    }

    // Y is scaled to the data's own range (padded), not to 1..10 — otherwise a spread of 6.8 to
    // 7.1 renders as a flat line.
    val validAvgs = cumulativeAvgs.filter { it > 0 }
    val minA = validAvgs.minOrNull() ?: 1.0
    val maxA = validAvgs.maxOrNull() ?: 10.0
    val rangePad = max(0.4, (maxA - minA) * 0.3)
    val dataMin = max(1.0, minA - rangePad)
    val dataMax = min(10.0, maxA + rangePad)
    fun toSparkY(v: Double): Float =
        if (dataMax == dataMin) 30f else (54 - ((v - dataMin) / (dataMax - dataMin)) * 48).toFloat()

    val sparkXs = cumulativeAvgs.indices.map { i ->
        if (cumulativeAvgs.size <= 1) 100f else (i.toFloat() / (cumulativeAvgs.size - 1)) * SparkGeometry.WIDTH
    }

    val sparkPathPoints: List<Pair<Float, Float>> = when {
        cumulativeAvgs.isEmpty() -> emptyList()
        cumulativeAvgs.size == 1 -> {
            val y = if (cumulativeAvgs[0] > 0) toSparkY(cumulativeAvgs[0]) else 30f
            listOf(0f to y, SparkGeometry.WIDTH to y)
        }
        else -> cumulativeAvgs.mapIndexed { i, v ->
            sparkXs[i] to (if (v > 0) toSparkY(v) else 58f)
        }
    }

    val sparkPoints = monthKeys.mapIndexed { i, key ->
        val avg = cumulativeAvgs[i]
        SparkPoint(
            x = sparkXs[i],
            y = if (avg > 0) toSparkY(avg) else 58f,
            avg = avg,
            label = "${MonthLabels[key.substring(4, 6).toInt() - 1]} ${key.substring(2, 4)}",
        )
    }

    val recent = allGrades
        .filter { it.entry.markDisplayValue > 0 }
        .sortedByDescending { it.entry.id }
        .take(3)
        .map { RecentGradeItem(it.entry.id, it.subjectName, it.entry.markDisplayValue, it.entry.date) }

    val donutSegments = buildDonutSegments(distribution, values.size)

    return GradesDashboard(
        allCount = allGrades.size,
        subjectCount = subjects.size,
        latestGradeDate = latestGradeDate,
        overallAvg = overallAvg,
        bestGrade = bestGrade,
        pos = pos,
        neg = neg,
        passRate = passRate,
        distribution = distribution,
        mode = modeIdx + 1,
        median = median,
        sigma = sigma,
        delta = delta,
        previousMonthAvg = previousMonthAvg,
        spark = SparkPath(sparkPathPoints),
        sparkPoints = sparkPoints,
        recent = recent,
        donutSegments = donutSegments,
    )
}

/** Walks grades 10 → 1 so the ring reads best-first clockwise from the top, like the web. */
private fun buildDonutSegments(distribution: List<Int>, total: Int): List<DonutSegment> {
    if (total == 0) return emptyList()
    val gap = 0.035f
    val outerEdge = DonutGeometry.RADIUS + DonutGeometry.STROKE / 2 + 1
    val tickEndR = DonutGeometry.RADIUS + DonutGeometry.STROKE / 2 + 7
    val labelR = DonutGeometry.RADIUS + DonutGeometry.STROKE / 2 + 14

    val segments = mutableListOf<DonutSegment>()
    var startAngle = (-PI / 2).toFloat()
    for (i in 9 downTo 0) {
        val count = distribution[i]
        if (count == 0) continue
        val grade = i + 1
        val sweep = max(0.01f, (count.toFloat() / total) * (PI * 2).toFloat() - gap)
        val midAngle = startAngle + sweep / 2
        segments += DonutSegment(
            grade = grade,
            count = count,
            startAngle = startAngle,
            sweep = sweep,
            tickStart = polar(outerEdge, midAngle),
            tickEnd = polar(tickEndR, midAngle),
            labelPos = polar(labelR, midAngle),
        )
        startAngle += sweep + gap
    }
    return segments
}

private fun polar(radius: Float, angle: Float): Pair<Float, Float> =
    (DonutGeometry.CX + radius * cos(angle)) to (DonutGeometry.CY + radius * sin(angle))

/**
 * The four-tier grade band the web uses for coloring a value (`gradeClass` in `lib/grades.ts`):
 * ≥9 excellent, ≥6 positive, >4 negative, else critical. Distinct from the continuous
 * [dev.plattnericus.pokyh.ui.theme.gradeColor] ramp, which is for a single grade's own tint.
 */
enum class GradeBand { Excellent, Positive, Negative, Critical }

fun gradeBand(value: Double): GradeBand = when {
    value >= 9 -> GradeBand.Excellent
    value >= 6 -> GradeBand.Positive
    value > 4 -> GradeBand.Negative
    else -> GradeBand.Critical
}

/** `fmtUntisDateLong` — "21. Mai 2026" for a `YYYYMMDD` int. */
internal fun formatUntisDateLong(date: Int): String {
    val s = date.toString()
    if (s.length != 8) return s
    val months = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )
    val month = s.substring(4, 6).toInt()
    if (month !in 1..12) return s
    return "${s.substring(6, 8).toInt()}. ${months[month - 1]} ${s.substring(0, 4)}"
}

/** Absolute delta, formatted for the trend pill. */
internal fun absDelta(delta: Double): Double = abs(delta)
