package dev.plattnericus.pokyh.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases as pokyh-backend tests/gradeScale.test.mjs: both platforms decide alike. */
class GradeScaleTest {

    private fun marks(vararg m: Pair<String, Double>) = m.map { it.second to it.first }
    private fun values(vararg v: Double) = v.map { it to it.toString() }

    @Test
    fun italianSchoolFromTheMarksOfLbsBrixen() {
        val brixen = marks("4/5" to 4.5, "5+" to 5.25, "5/6" to 5.5, "6" to 6.0, "6/7" to 6.5, "7+" to 7.25, "7/8" to 7.5, "8" to 8.0, "8/9" to 8.5, "10-" to 9.75)
        val s = GradeScales.infer(brixen, GradeScales.postcodeDigits("39042, Brixen, Fischzuchtweg 18"), null)
        assertEquals(GradeScale.Kind.It10, s.kind)
        assertTrue(s.certain)
        assertTrue(s.passes(6.0))
        assertFalse(s.passes(5.5))
    }

    @Test
    fun fivesAndSixesTheSchoolYearDecides() {
        val fivesAndSixes = marks("5/6" to 5.5, "6" to 6.0, "5+" to 5.25)
        // lbs-brixen's school year 2026/27 ends on 11 June 2027.
        val italian = GradeScales.infer(fivesAndSixes, 5, 20270611)
        assertEquals(GradeScale.Kind.It10, italian.kind)
        assertTrue(italian.certain)
        assertEquals(GradeScale.Kind.De6, GradeScales.infer(values(3.0, 4.0, 3.5), 5, 20270731).kind)
        assertFalse(GradeScales.infer(fivesAndSixes, 5, null).certain)
    }

    @Test
    fun germanAustrianSwissAndPoints() {
        val german = GradeScales.infer(marks("2-" to 2.3, "1" to 1.0, "4" to 4.0), 5, null)
        assertEquals(GradeScale.Kind.De6, german.kind)
        assertTrue(german.passes(4.0))
        assertFalse(german.passes(5.0))
        assertEquals(1.0, german.better(1.0, 2.0), 0.0)
        assertEquals(GradeScale.Kind.At5, GradeScales.infer(marks("Nicht genügend" to 5.0, "Gut" to 2.0), null, null).kind)
        assertEquals(GradeScale.Kind.Ch6, GradeScales.infer(values(5.5, 6.0, 4.5), 4, null).kind)
        assertEquals(GradeScale.Kind.De15, GradeScales.infer(values(11.0, 13.0, 7.0), null, null).kind)
    }

    @Test
    fun italianEquivalentPutsThePassMarkAtSix() {
        val german = GradeScales.infer(marks("1" to 1.0, "2" to 2.0), 5, null)
        assertEquals(10.0, german.italianEquivalent(1.0), 1e-9)
        assertEquals(6.0, german.italianEquivalent(4.0), 1e-9)
        assertEquals(1.0, german.italianEquivalent(6.0), 1e-9)
    }

    @Test
    fun postcodeDigits() {
        assertEquals(5, GradeScales.postcodeDigits("39042, Brixen, Fischzuchtweg 18"))
        assertEquals(4, GradeScales.postcodeDigits("1010 Wien, Stubenbastei 6"))
        assertEquals(null, GradeScales.postcodeDigits("Musterstraße 12"))
    }
}
