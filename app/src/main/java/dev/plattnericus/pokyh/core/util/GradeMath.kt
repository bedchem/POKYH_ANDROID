package dev.plattnericus.pokyh.core.util

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/**
 * Noten-Mathematik — 1:1 portiert aus `GradeMath.swift` (dort wiederum aus `lib/grades.ts`).
 * Skala 1–10 (Südtirol), positiv ab 6.
 */
object GradeMath {

    /** The school's marks (core/util/GradeScale.kt): 1 to 10 in Italy, where 4 is the lowest mark given in practice. */
    private val scale get() = GradeScales.current
    private val highest get() = scale.max
    private val lowest get() = if (scale.kind == GradeScale.Kind.It10) 4.0 else scale.min

    fun round2(v: Double): Double = round(v * 100) / 100

    fun averageOf(values: List<Double>): Double =
        if (values.isEmpty()) 0.0 else round2(values.sum() / values.size)

    /** "8,5" / "8.5" -> 8.5, nur gültig im Bereich 1…10. */
    fun parseGradeInput(raw: String): Double? {
        val normalized = raw.replace(',', '.').trim()
        if (normalized.isEmpty()) return null
        val parsed = normalized.toDoubleOrNull() ?: return null
        if (parsed < scale.min || parsed > scale.max) return null
        return round2(parsed)
    }

    const val gradeStep: Double = 0.5

    fun roundToGradeStep(value: Double, up: Boolean): Double {
        val factor = 1 / gradeStep
        val scaled = value * factor
        val rounded = if (up) ceil(scaled - 1e-9) else floor(scaled + 1e-9)
        return round2(minOf(highest, maxOf(lowest, rounded / factor)))
    }

    enum class TargetStatus { REACHED, REACHABLE, IMPOSSIBLE }

    data class TargetResult(
        val target: Double,
        val status: TargetStatus,
        val count: Int,
        val needed: Double,
    )

    /** Zielnote-Rechner: wie viele Noten welchen Werts werden gebraucht? */
    fun target(targetInput: String, values: List<Double>): TargetResult? {
        val target = parseGradeInput(targetInput) ?: return null
        if (values.isEmpty()) return null
        val sum = values.sum()
        val n = values.size.toDouble()
        val currentAvg = sum / n

        if (abs(currentAvg - target) < 1e-6) {
            return TargetResult(target, TargetStatus.REACHED, 0, 0.0)
        }
        if (currentAvg < target) {
            for (k in 1..50) {
                val kk = k.toDouble()
                val perGrade = (target * (n + kk) - sum) / kk
                if (perGrade <= highest) {
                    return TargetResult(
                        target = target,
                        status = TargetStatus.REACHABLE,
                        count = k,
                        needed = roundToGradeStep(maxOf(lowest, perGrade), up = true),
                    )
                }
            }
            return TargetResult(target, TargetStatus.IMPOSSIBLE, 0, 0.0)
        } else {
            if (target <= lowest + 1e-6) return TargetResult(target, TargetStatus.IMPOSSIBLE, 0, 0.0)
            val kMin = ceil((sum - target * n) / (target - lowest) - 1e-9).toInt()
            if (kMin <= 0 || kMin > 50) return TargetResult(target, TargetStatus.IMPOSSIBLE, 0, 0.0)
            val kMinD = kMin.toDouble()
            val perGrade = (target * (n + kMinD) - sum) / kMinD
            return TargetResult(
                target = target,
                status = TargetStatus.REACHABLE,
                count = kMin,
                needed = roundToGradeStep(maxOf(lowest, perGrade), up = false),
            )
        }
    }
}
