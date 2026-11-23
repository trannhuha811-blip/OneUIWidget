package com.oneui.widget.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class WeatherRepository(private val context: Context) {

    private val prefs = WidgetPreferences(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getWeatherData(): WidgetWeatherData = withContext(Dispatchers.IO) {
        val (lat, lon, locationName) = determineCoordinatesAndName()

        try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,apparent_temperature,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                    "&timezone=Asia%2FHo_Chi_Minh"

            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrEmpty()) {
                    prefs.cachedWeatherJson = jsonStr
                    prefs.lastUpdateTime = System.currentTimeMillis()
                    return@withContext parseWeatherData(jsonStr, locationName)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Đọc từ bộ nhớ đệm nếu có
        val cached = prefs.cachedWeatherJson
        if (!cached.isNullOrEmpty()) {
            try {
                return@withContext parseWeatherData(cached, locationName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Trả về dữ liệu mặc định chuẩn như ảnh mẫu nếu hoàn toàn không có mạng & cache
        return@withContext getFallbackWeatherData(locationName)
    }

    private suspend fun determineCoordinatesAndName(): Triple<Double, Double, String> {
        if (!prefs.useAutoLocation) {
            return Triple(prefs.fixedLatitude, prefs.fixedLongitude, prefs.fixedCityName)
        }

        val location = getLastKnownLocation()
        if (location != null) {
            val geocodedName = getSubLocalityName(location.latitude, location.longitude)
            val finalName = geocodedName ?: prefs.fixedCityName
            return Triple(location.latitude, location.longitude, finalName)
        }

        return Triple(prefs.fixedLatitude, prefs.fixedLongitude, prefs.fixedCityName)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) }
                .addOnFailureListener { cont.resume(null) }
        } catch (e: Exception) {
            cont.resume(null)
        }
    }

    private fun getSubLocalityName(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                // Ưu tiên: Phường/Xã (subLocality), sau đó đến Quận/Huyện (subAdminArea/locality)
                val subLoc = addr.subLocality
                val locality = addr.locality ?: addr.subAdminArea
                when {
                    !subLoc.isNullOrBlank() -> {
                        if (subLoc.startsWith("Phường", true) || subLoc.startsWith("P.", true) ||
                            subLoc.startsWith("Xã", true) || subLoc.startsWith("X.", true)
                        ) {
                            subLoc
                        } else {
                            "P. $subLoc"
                        }
                    }
                    !locality.isNullOrBlank() -> locality
                    else -> addr.adminArea
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseWeatherData(jsonStr: String, locationName: String): WidgetWeatherData {
        val root = JSONObject(jsonStr)
        val currentJson = root.getJSONObject("current")
        val dailyJson = root.getJSONObject("daily")

        val currentTemp = Math.round(currentJson.getDouble("temperature_2m")).toInt()
        val weatherCode = currentJson.getInt("weather_code")
        val conditionDesc = WeatherUtils.getWeatherDescription(weatherCode)

        val timeArray = dailyJson.getJSONArray("time")
        val weatherCodeArray = dailyJson.getJSONArray("weather_code")
        val maxTempArray = dailyJson.getJSONArray("temperature_2m_max")
        val minTempArray = dailyJson.getJSONArray("temperature_2m_min")
        val sunriseArray = dailyJson.getJSONArray("sunrise")
        val sunsetArray = dailyJson.getJSONArray("sunset")

        // Hôm nay (index 0)
        val minTempToday = Math.round(minTempArray.getDouble(0)).toInt()
        val maxTempToday = Math.round(maxTempArray.getDouble(0)).toInt()

        val sunriseStr = formatSunTime(sunriseArray.optString(0, ""))
        val sunsetStr = formatSunTime(sunsetArray.optString(0, ""))

        val currentWeather = CurrentWeather(
            temp = currentTemp,
            weatherCode = weatherCode,
            conditionDescription = conditionDesc,
            locationName = locationName,
            sunrise = sunriseStr,
            sunset = sunsetStr,
            minTempToday = minTempToday,
            maxTempToday = maxTempToday
        )

        // 6 ngày tiếp theo (index 1..6)
        val forecastList = mutableListOf<DailyForecast>()
        val count = Math.min(timeArray.length(), 7)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (i in 1 until count) {
            val dateStr = timeArray.getString(i)
            val code = weatherCodeArray.getInt(i)
            val minT = Math.round(minTempArray.getDouble(i)).toInt()
            val maxT = Math.round(maxTempArray.getDouble(i)).toInt()

            val dayOfWeekStr = try {
                val date = dateFormat.parse(dateStr)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    when (cal.get(Calendar.DAY_OF_WEEK)) {
                        Calendar.MONDAY -> "THỨ 2"
                        Calendar.TUESDAY -> "THỨ 3"
                        Calendar.WEDNESDAY -> "THỨ 4"
                        Calendar.THURSDAY -> "THỨ 5"
                        Calendar.FRIDAY -> "THỨ 6"
                        Calendar.SATURDAY -> "THỨ 7"
                        Calendar.SUNDAY -> "CN"
                        else -> "CN"
                    }
                } else "THỨ ${(i + 1)}"
            } catch (e: Exception) {
                "THỨ ${(i + 1)}"
            }

            forecastList.add(
                DailyForecast(
                    dayOfWeek = dayOfWeekStr,
                    weatherCode = code,
                    minTemp = minT,
                    maxTemp = maxT
                )
            )
        }

        return WidgetWeatherData(currentWeather, forecastList)
    }

    private fun formatSunTime(isoTime: String): String {
        return try {
            if (isoTime.contains("T")) {
                val timePart = isoTime.substringAfter("T")
                val parts = timePart.split(":")
                "${parts[0]}:${parts[1]}"
            } else {
                isoTime
            }
        } catch (e: Exception) {
            "06:00"
        }
    }

    private fun getFallbackWeatherData(locationName: String): WidgetWeatherData {
        val current = CurrentWeather(
            temp = 28,
            weatherCode = 2,
            conditionDescription = "Có mây rải rác",
            locationName = locationName,
            sunrise = "05:52",
            sunset = "17:34",
            minTempToday = 27,
            maxTempToday = 32
        )

        val days = listOf("THỨ 3", "THỨ 4", "THỨ 5", "THỨ 6", "THỨ 7", "CN")
        val forecast = days.mapIndexed { index, day ->
            DailyForecast(
                dayOfWeek = day,
                weatherCode = if (index % 2 == 0) 2 else 61,
                minTemp = 25 + (index % 3),
                maxTemp = 32 + (index % 2)
            )
        }

        return WidgetWeatherData(current, forecast)
    }
}
