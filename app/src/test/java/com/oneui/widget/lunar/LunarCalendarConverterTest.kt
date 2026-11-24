package com.oneui.widget.lunar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarCalendarConverterTest {

    @Test
    fun testLunarConversionBasic() {
        // Test convertSolarToLunar on a known date
        // 23/11/2026
        val lunar = LunarCalendarConverter.convertSolarToLunar(23, 11, 2026)
        assertNotNull(lunar)
        assertTrue(lunar.day in 1..30)
        assertTrue(lunar.month in 1..12)
        assertEquals(2026, lunar.year)
        assertTrue(lunar.pillText.isNotEmpty())
        assertTrue(lunar.dayMonthText.isNotEmpty())
        assertTrue(lunar.canChiText.isNotEmpty())
        assertTrue(lunar.dayCanChi.isNotEmpty())
        assertTrue(lunar.yearCanChi.isNotEmpty())
    }

    @Test
    fun testTodayLunarNotNull() {
        val today = LunarCalendarConverter.getTodayLunar()
        assertNotNull(today)
        assertTrue(today.day in 1..30)
        assertTrue(today.month in 1..12)
        assertTrue(today.pillText.contains("/"))
    }

    @Test
    fun testJulianDateCalculation() {
        // Test JD conversion
        val jd = LunarCalendarConverter.jdFromDate(1, 1, 2000)
        assertTrue(jd > 0)
        val (d, m, y) = LunarCalendarConverter.jdToDate(jd)
        assertEquals(1, d)
        assertEquals(1, m)
        assertEquals(2000, y)
    }

    @Test
    fun testCanChiArrays() {
        assertEquals(10, LunarCalendarConverter.CAN.size)
        assertEquals(12, LunarCalendarConverter.CHI.size)
        assertEquals("Giáp", LunarCalendarConverter.CAN[0])
        assertEquals("Tý", LunarCalendarConverter.CHI[0])
    }
}
