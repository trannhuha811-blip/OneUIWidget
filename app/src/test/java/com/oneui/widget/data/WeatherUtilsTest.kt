package com.oneui.widget.data

import com.oneui.widget.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherUtilsTest {

    @Test
    fun testGreetingByHour() {
        assertEquals("Chào buổi sáng", WeatherUtils.getGreetingByHour(7))
        assertEquals("Chào buổi trưa", WeatherUtils.getGreetingByHour(11))
        assertEquals("Chào buổi trưa", WeatherUtils.getGreetingByHour(12))
        assertEquals("Chào buổi chiều", WeatherUtils.getGreetingByHour(14))
        assertEquals("Chào buổi tối", WeatherUtils.getGreetingByHour(19))
        assertEquals("Chào buổi tối", WeatherUtils.getGreetingByHour(23))
        assertEquals("Chào buổi tối", WeatherUtils.getGreetingByHour(3))
    }

    @Test
    fun testWeatherDescriptions() {
        assertEquals("Trời quang đãng", WeatherUtils.getWeatherDescription(0))
        assertEquals("Có mây rải rác", WeatherUtils.getWeatherDescription(2))
        assertEquals("Giông bão", WeatherUtils.getWeatherDescription(95))
        assertTrue(WeatherUtils.getWeatherDescription(999).isNotEmpty())
    }

    @Test
    fun testWeatherIcons() {
        assertEquals(R.drawable.ic_weather_sunny, WeatherUtils.getWeatherIcon(0))
        assertEquals(R.drawable.ic_weather_partly_cloudy, WeatherUtils.getWeatherIcon(2))
        assertEquals(R.drawable.ic_weather_thunder, WeatherUtils.getWeatherIcon(95))
        assertNotEquals(0, WeatherUtils.getWeatherIcon(999))
    }
}
