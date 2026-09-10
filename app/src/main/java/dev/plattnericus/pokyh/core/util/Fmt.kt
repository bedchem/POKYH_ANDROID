package dev.plattnericus.pokyh.core.util

import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formatierungs-Helfer — 1:1 portiert aus `enum Fmt` (unterer Teil von Theme.swift,
 * ursprünglich aus `lib/grades.ts`). Reine String-/Int-Manipulation, keine
 * locale-abhängigen Formatter — genau wie im Swift-Original.
 */
object Fmt {

    /** Rundet auf `digits` Nachkommastellen, entfernt Nullen am Ende, "." -> "," (z. B. 8.50 -> "8,5"). */
    fun num(value: Double, digits: Int = 2): String {
        val factor = Math.pow(10.0, digits.toDouble())
        val rounded = (value * factor).roundToLong() / factor
        var s = String.format(Locale.ROOT, "%.${digits}f", rounded)
        if (s.contains('.')) {
            s = s.trimEnd('0')
            if (s.endsWith('.')) s = s.dropLast(1)
        }
        return s.replace('.', ',')
    }

    /** yyyyMMdd-Int -> "dd.MM.yy". */
    fun dateShort(date: Int): String {
        val s = date.toString()
        if (s.length != 8) return s
        return "${s.substring(6, 8)}.${s.substring(4, 6)}.${s.substring(2, 4)}"
    }

    /** yyyyMMdd-Int -> "dd.MM.yyyy". */
    fun dateFull(date: Int): String {
        val s = date.toString()
        if (s.length != 8) return s
        return "${s.substring(6, 8)}.${s.substring(4, 6)}.${s.substring(0, 4)}"
    }

    /** HHmm-Int -> "HH:mm". */
    fun time(t: Int): String {
        val s = t.toString().padStart(4, '0')
        return "${s.substring(0, 2)}:${s.substring(2, 4)}"
    }

    /** HHmm-Int -> Minuten seit Mitternacht. */
    fun minutes(t: Int): Int {
        val s = t.toString().padStart(4, '0')
        val h = s.substring(0, 2).toIntOrNull() ?: 0
        val m = s.substring(2, 4).toIntOrNull() ?: 0
        return h * 60 + m
    }
}
