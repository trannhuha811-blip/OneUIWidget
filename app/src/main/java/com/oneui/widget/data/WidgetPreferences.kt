package com.oneui.widget.data

import android.content.Context
import android.content.SharedPreferences

class WidgetPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("oneui_widget_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_OPACITY = "widget_bg_opacity"
        private const val KEY_USE_AUTO_LOCATION = "use_auto_location"
        private const val KEY_FIXED_LAT = "fixed_latitude"
        private const val KEY_FIXED_LON = "fixed_longitude"
        private const val KEY_FIXED_CITY = "fixed_city_name"
        private const val KEY_CACHED_WEATHER_JSON = "cached_weather_json"
        private const val KEY_CACHED_LOCATION_NAME = "cached_location_name"
        private const val KEY_LAST_UPDATE_TIME = "last_update_timestamp"
    }

    // Độ mờ nền: 0% (trong suốt hoàn toàn) đến 50%
    var backgroundOpacity: Int
        get() = prefs.getInt(KEY_OPACITY, 20) // default 20%
        set(value) = prefs.edit().putInt(KEY_OPACITY, value.coerceIn(0, 50)).apply()

    var useAutoLocation: Boolean
        get() = prefs.getBoolean(KEY_USE_AUTO_LOCATION, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_AUTO_LOCATION, value).apply()

    var fixedLatitude: Double
        get() = prefs.getString(KEY_FIXED_LAT, "10.8031")?.toDoubleOrNull() ?: 10.8031
        set(value) = prefs.edit().putString(KEY_FIXED_LAT, value.toString()).apply()

    var fixedLongitude: Double
        get() = prefs.getString(KEY_FIXED_LON, "106.7180")?.toDoubleOrNull() ?: 106.7180
        set(value) = prefs.edit().putString(KEY_FIXED_LON, value.toString()).apply()

    var fixedCityName: String
        get() = prefs.getString(KEY_FIXED_CITY, "P. Thạnh Mỹ Tây") ?: "P. Thạnh Mỹ Tây"
        set(value) = prefs.edit().putString(KEY_FIXED_CITY, value).apply()

    var cachedWeatherJson: String?
        get() = prefs.getString(KEY_CACHED_WEATHER_JSON, null)
        set(value) = prefs.edit().putString(KEY_CACHED_WEATHER_JSON, value).apply()

    var cachedLocationName: String?
        get() = prefs.getString(KEY_CACHED_LOCATION_NAME, null)
        set(value) = prefs.edit().putString(KEY_CACHED_LOCATION_NAME, value).apply()

    var lastUpdateTime: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_TIME, value).apply()
}
