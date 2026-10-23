package dev.plattnericus.pokyh.core.util

import dev.plattnericus.pokyh.core.i18n.I18n
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formatierungs-Helfer — 1:1 portiert aus `enum Fmt` (unterer Teil von Theme.swift,
 * ursprünglich aus `lib/grades.ts`). Reine String-/Int-Manipulation; nur das Dezimal- und das
 * Datumstrennzeichen folgen der Sprache der App ([I18n]).
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
        return s.replace('.', I18n.s.common.decimal)
    }

    /** yyyyMMdd-Int -> "dd.MM.yy" ("dd/MM/yy" auf Italienisch und Englisch). */
    fun dateShort(date: Int): String {
        val s = date.toString()
        if (s.length != 8) return s
        val sep = I18n.s.common.dateSep
        return "${s.substring(6, 8)}$sep${s.substring(4, 6)}$sep${s.substring(2, 4)}"
    }

    /** yyyyMMdd-Int -> "dd.MM.yyyy". */
    fun dateFull(date: Int): String {
        val s = date.toString()
        if (s.length != 8) return s
        val sep = I18n.s.common.dateSep
        return "${s.substring(6, 8)}$sep${s.substring(4, 6)}$sep${s.substring(0, 4)}"
    }

    /** yyyyMMdd-Int -> "21. Mai 2026" / "21 maggio 2026" / "21 May 2026". */
    fun dateLong(date: Int): String {
        val s = date.toString()
        if (s.length != 8) return s
        val month = s.substring(4, 6).toInt()
        if (month !in 1..12) return s
        return I18n.s.common.dateLong(s.substring(6, 8).toInt(), month, s.substring(0, 4).toInt())
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
