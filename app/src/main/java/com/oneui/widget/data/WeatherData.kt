package com.oneui.widget.data

import com.oneui.widget.R

data class CurrentWeather(
    val temp: Int,
    val weatherCode: Int,
    val conditionDescription: String,
    val locationName: String,
    val sunrise: String,
    val sunset: String,
    val minTempToday: Int,
    val maxTempToday: Int
)

data class DailyForecast(
    val dayOfWeek: String, // e.g. "THỨ 3", "THỨ 4", "CN"
    val weatherCode: Int,
    val minTemp: Int,
    val maxTemp: Int
) {
    fun getWeatherIconRes(): Int = WeatherUtils.getWeatherIcon(weatherCode)
}

data class WidgetWeatherData(
    val current: CurrentWeather,
    val forecast6Days: List<DailyForecast>
)

object WeatherUtils {
    fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "Trời quang đãng"
            1 -> "Hầu như không mây"
            2 -> "Có mây rải rác"
            3 -> "Mây u ám"
            45, 48 -> "Có sương mù"
            51, 53, 55 -> "Mưa phùn nhẹ"
            56, 57 -> "Mưa phùn băng giá"
            61, 63 -> "Mưa rào vừa"
            65 -> "Mưa to diện rộng"
            66, 67 -> "Mưa đá lạnh"
            71, 73, 75 -> "Tuyết rơi"
            77 -> "Tuyết hạt"
            80, 81, 82 -> "Mưa rào thoáng qua"
            85, 86 -> "Mưa tuyết rào"
            95 -> "Giông bão"
            96, 99 -> "Giông kèm mưa đá"
            else -> "Trời nhiều mây"
        }
    }

    fun getWeatherIcon(code: Int): Int {
        return when (code) {
            0 -> R.drawable.ic_weather_sunny
            1, 2 -> R.drawable.ic_weather_partly_cloudy
            3 -> R.drawable.ic_weather_cloudy
            45, 48 -> R.drawable.ic_weather_fog
            51, 53, 55, 56, 57, 61, 63, 65, 80, 81, 82 -> R.drawable.ic_weather_rain
            71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
            95, 96, 99 -> R.drawable.ic_weather_thunder
            else -> R.drawable.ic_weather_partly_cloudy
        }
    }

    fun getGreetingByHour(hour: Int): String {
        return when (hour) {
            in 5..10 -> "Chào buổi sáng"
            in 11..12 -> "Chào buổi trưa"
            in 13..17 -> "Chào buổi chiều"
            else -> "Chào buổi tối"
        }
    }
}
