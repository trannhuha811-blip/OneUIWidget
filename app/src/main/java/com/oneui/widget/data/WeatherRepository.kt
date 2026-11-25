package com.oneui.widget.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class WeatherRepository(private val context: Context) {

    private val prefs = WidgetPreferences(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Lấy dữ liệu nhanh từ bộ nhớ cache hoặc mặc định (chạy ngay lập tức, không chờ mạng)
     */
    fun getCachedOrDefaultWeather(): WidgetWeatherData {
        val displayLocation = prefs.cachedLocationName ?: prefs.fixedCityName
        val cached = prefs.cachedWeatherJson
        if (!cached.isNullOrEmpty()) {
            try {
                return parseWeatherData(cached, displayLocation)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return getFallbackWeatherData(displayLocation)
    }

    /**
     * Lấy dữ liệu thời tiết mới nhất qua mạng (có timeout an toàn chống treo)
     */
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
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!prefs.useAutoLocation || (!hasFine && !hasCoarse)) {
            return Triple(prefs.fixedLatitude, prefs.fixedLongitude, prefs.fixedCityName)
        }

        // Giới hạn thời gian chờ vị trí tối đa 4 giây (ưu tiên lastLocation, fallback getCurrentLocation)
        val location = withTimeoutOrNull(4000L) {
            getBestLocation(hasFine)
        }

        if (location != null) {
            val resolvedName = resolveLocationName(location.latitude, location.longitude)
            if (!resolvedName.isNullOrBlank()) {
                prefs.cachedLocationName = resolvedName
            }
            val finalName = resolvedName ?: prefs.cachedLocationName ?: prefs.fixedCityName
            return Triple(location.latitude, location.longitude, finalName)
        }

        val fallbackName = prefs.cachedLocationName ?: prefs.fixedCityName
        return Triple(prefs.fixedLatitude, prefs.fixedLongitude, fallbackName)
    }

    @SuppressLint("MissingPermission")
    private suspend fun getBestLocation(hasFine: Boolean): Location? = suspendCancellableCoroutine { cont ->
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation
                .addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) {
                        if (cont.isActive) cont.resume(lastLoc)
                    } else {
                        val priority = if (hasFine) {
                            Priority.PRIORITY_HIGH_ACCURACY
                        } else {
                            Priority.PRIORITY_BALANCED_POWER_ACCURACY
                        }
                        fusedClient.getCurrentLocation(priority, null)
                            .addOnSuccessListener { currLoc ->
                                if (cont.isActive) cont.resume(currLoc)
                            }
                            .addOnFailureListener {
                                if (cont.isActive) cont.resume(null)
                            }
                    }
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(null)
                }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /**
     * Ưu tiên Nominatim (OpenStreetMap) vì đã cập nhật đầy đủ địa giới Phường/Xã mới tại Việt Nam
     * sau sáp nhập (bỏ cấp huyện). Nếu mạng lỗi mới fallback về Android Geocoder.
     */
    private fun resolveLocationName(lat: Double, lon: Double): String? {
        getNominatimWardOrCommune(lat, lon)?.let { return it }
        return getGeocoderWardOrCommune(lat, lon)
    }

    private fun getNominatimWardOrCommune(lat: Double, lon: Double): String? {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                    "&lat=$lat&lon=$lon&accept-language=vi&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OneUIWidget-Android/1.0")
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val addrObj = root.optJSONObject("address") ?: return null
            val addrMap = mutableMapOf<String, String>()
            val keys = addrObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val valStr = addrObj.optString(key, "")
                if (valStr.isNotBlank()) {
                    addrMap[key] = valStr
                }
            }
            LocationNameFormatter.fromNominatimAddress(addrMap)
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getGeocoderWardOrCommune(lat: Double, lon: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            val addresses = geocoder.getFromLocation(lat, lon, 3)
            if (!addresses.isNullOrEmpty()) {
                for (addr in addresses) {
                    val candidate = LocationNameFormatter.fromGeocoderFields(
                        subLocality = addr.subLocality,
                        locality = addr.locality,
                        subAdminArea = addr.subAdminArea,
                        adminArea = addr.adminArea,
                        addressLine = if (addr.maxAddressLineIndex >= 0) addr.getAddressLine(0) else null
                    )
                    if (!candidate.isNullOrBlank()) {
                        return candidate
                    }
                }
            }
            null
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
