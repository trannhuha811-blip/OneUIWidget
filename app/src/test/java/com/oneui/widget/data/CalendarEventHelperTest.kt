package com.oneui.widget.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventHelperTest {

    @Test
    fun testExcludesKoreanHangulHoliday() {
        assertTrue(CalendarEventHelper.shouldExcludeEvent("소비자의날", "법정기념일"))
        assertTrue(CalendarEventHelper.shouldExcludeEvent("성탄절", "대한민국 공휴일"))
    }

    @Test
    fun testExcludesEnglishKoreanHolidayCalendar() {
        assertTrue(CalendarEventHelper.shouldExcludeEvent("Children's Day", "Holidays in South Korea"))
        assertTrue(CalendarEventHelper.shouldExcludeEvent("Liberation Day", "Korean Holidays"))
    }

    @Test
    fun testKeepsVietnameseHolidaysAndPersonalEvents() {
        assertFalse(CalendarEventHelper.shouldExcludeEvent("Ngày Văn hoá Việt Nam", "Ngày lễ ở Việt Nam"))
        assertFalse(CalendarEventHelper.shouldExcludeEvent("Lễ Giáng Sinh", "Ngày lễ Việt Nam"))
        assertFalse(CalendarEventHelper.shouldExcludeEvent("Họp team dự án", "Lịch cá nhân"))
    }
}
