package com.oneui.widget.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.oneui.widget.MainActivity
import com.oneui.widget.R
import com.oneui.widget.data.CalendarEventHelper
import com.oneui.widget.data.DailyForecast
import com.oneui.widget.data.WeatherRepository
import com.oneui.widget.data.WeatherUtils
import com.oneui.widget.data.WidgetEventItem
import com.oneui.widget.data.WidgetPreferences
import com.oneui.widget.data.WidgetWeatherData
import com.oneui.widget.lunar.LunarCalendarConverter
import com.oneui.widget.receiver.MidnightAlarmReceiver
import com.oneui.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class OneUIWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "OneUIWidget"
        const val ACTION_REFRESH_WIDGET = "com.oneui.widget.ACTION_REFRESH_WIDGET"
        private const val REQUEST_CODE_REFRESH = 2001
        private const val REQUEST_CODE_CALENDAR = 2002
        private const val REQUEST_CODE_CONFIG = 2003

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val componentName = ComponentName(context, OneUIWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds == null || appWidgetIds.isEmpty()) return

                val weatherRepo = WeatherRepository(context)
                val upcomingEvents = safeGetEvents(context)
                val prefs = WidgetPreferences(context)

                // BƯỚC 1: Hiển thị ngay lập tức dữ liệu Offline/Cache
                val immediateWeather = weatherRepo.getCachedOrDefaultWeather()
                for (appWidgetId in appWidgetIds) {
                    safeUpdateWidget(context, appWidgetManager, appWidgetId, immediateWeather, upcomingEvents, prefs)
                }

                // BƯỚC 2: Gọi mạng ngầm cập nhật thời tiết mới nhất
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val freshWeather = weatherRepo.getWeatherData()
                        for (appWidgetId in appWidgetIds) {
                            safeUpdateWidget(context, appWidgetManager, appWidgetId, freshWeather, upcomingEvents, prefs)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi cập nhật mạng ngầm", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi updateAllWidgets", e)
            }
        }

        fun safeGetEvents(context: Context): List<WidgetEventItem> {
            return try {
                CalendarEventHelper(context).getUpcomingEvents(2)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi đọc lịch sự kiện", e)
                emptyList()
            }
        }

        fun safeUpdateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            weatherData: WidgetWeatherData,
            upcomingEvents: List<WidgetEventItem>,
            prefs: WidgetPreferences
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_large)
                buildWidgetViews(context, views, weatherData, upcomingEvents, prefs)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi cập nhật widget $appWidgetId, dùng fallback", e)
                try {
                    // Fallback: hiển thị layout gốc không dữ liệu (tốt hơn crash hoàn toàn)
                    val fallbackViews = RemoteViews(context.packageName, R.layout.widget_large)
                    fallbackViews.setTextViewText(R.id.tv_greeting, WeatherUtils.getGreetingByHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)))
                    fallbackViews.setTextViewText(R.id.tv_main_temp, "--°")
                    fallbackViews.setTextViewText(R.id.tv_weather_desc, "Đang tải...")
                    appWidgetManager.updateAppWidget(appWidgetId, fallbackViews)
                } catch (e2: Exception) {
                    Log.e(TAG, "Lỗi ngay cả fallback widget", e2)
                }
            }
        }

        fun buildWidgetViews(
            context: Context,
            views: RemoteViews,
            weatherData: WidgetWeatherData,
            upcomingEvents: List<WidgetEventItem>,
            prefs: WidgetPreferences
        ) {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val month = cal.get(Calendar.MONTH) + 1
            val dayOfWeekInt = cal.get(Calendar.DAY_OF_WEEK)

            // ==========================================
            // KHU VỰC 1: GÓC TRÊN BÊN TRÁI – THỜI TIẾT HIỆN TẠI
            // ==========================================
            views.setTextViewText(R.id.tv_greeting, WeatherUtils.getGreetingByHour(hour))
            views.setTextViewText(R.id.tv_main_temp, "${weatherData.current.temp}°")
            views.setTextViewText(R.id.tv_weather_desc, weatherData.current.conditionDescription)
            views.setTextViewText(R.id.tv_location_name, weatherData.current.locationName)
            views.setTextViewText(R.id.tv_sunrise_info, "${weatherData.current.minTempToday}°C")
            views.setTextViewText(R.id.tv_sunset_info, "${weatherData.current.maxTempToday}°C")

            // ==========================================
            // KHU VỰC 2: GÓC TRÊN BÊN PHẢI – LỊCH DƯƠNG & LỊCH ÂM VIỆT NAM
            // ==========================================
            views.setTextViewText(R.id.tv_solar_month, "THÁNG $month")
            try {
                val dayBitmap = CalendarGridRenderer.renderDayNumberBitmap(dayOfMonth)
                views.setImageViewBitmap(R.id.img_solar_day, dayBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi vẽ số ngày", e)
            }

            val dayOfWeekStr = when (dayOfWeekInt) {
                Calendar.MONDAY -> "Thứ Hai"
                Calendar.TUESDAY -> "Thứ Ba"
                Calendar.WEDNESDAY -> "Thứ Tư"
                Calendar.THURSDAY -> "Thứ Năm"
                Calendar.FRIDAY -> "Thứ Sáu"
                Calendar.SATURDAY -> "Thứ Bảy"
                Calendar.SUNDAY -> "Chủ Nhật"
                else -> "Thứ Hai"
            }
            views.setTextViewText(R.id.tv_day_of_week, dayOfWeekStr)

            // Khung bo tròn viền trắng + badge trắng Can Chi khớp 100% bản mẫu
            try {
                val lunarDate = LunarCalendarConverter.getTodayLunar()
                views.setTextViewText(R.id.tv_lunar_day_month, lunarDate.dayMonthText)
                views.setTextViewText(R.id.tv_lunar_canchi, lunarDate.canChiText)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi tính lịch âm", e)
                views.setTextViewText(R.id.tv_lunar_day_month, "$dayOfMonth/$month")
                views.setTextViewText(R.id.tv_lunar_canchi, "Âm lịch")
            }

            // ==========================================
            // KHU VỰC 3: GIỮA BÊN TRÁI – SỰ KIỆN & NGÀY LỄ VIỆT NAM (DẠNG 3 DÒNG)
            // ==========================================
            if (upcomingEvents.isNotEmpty()) {
                val ev1 = upcomingEvents[0]
                views.setTextViewText(R.id.tv_event1_title, ev1.title)
                views.setTextViewText(R.id.tv_event1_time, ev1.dateInfo)
                views.setTextViewText(R.id.tv_event1_source, ev1.sourceInfo)
            } else {
                views.setTextViewText(R.id.tv_event1_title, "Lễ Giáng Sinh")
                views.setTextViewText(R.id.tv_event1_time, "25-12  All Day")
                views.setTextViewText(R.id.tv_event1_source, "Ngày lễ Việt Nam")
            }

            if (upcomingEvents.size > 1) {
                val ev2 = upcomingEvents[1]
                views.setTextViewText(R.id.tv_event2_title, ev2.title)
                views.setTextViewText(R.id.tv_event2_time, ev2.dateInfo)
                views.setTextViewText(R.id.tv_event2_source, ev2.sourceInfo)
            } else {
                views.setTextViewText(R.id.tv_event2_title, "Tết Dương lịch")
                views.setTextViewText(R.id.tv_event2_time, "01-01  All Day")
                views.setTextViewText(R.id.tv_event2_source, "Ngày lễ Việt Nam")
            }

            // ==========================================
            // KHU VỰC 4: GIỮA BÊN PHẢI – LƯỚI LỊCH THÁNG (CALENDAR GRID CÂN ĐỐI)
            // ==========================================
            try {
                val calendarBitmap = CalendarGridRenderer.renderCalendarBitmap(context, 420, 300)
                views.setImageViewBitmap(R.id.img_calendar_grid, calendarBitmap)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi vẽ lịch tháng", e)
            }

            // ==========================================
            // KHU VỰC 5: HÀNG DƯỚI CÙNG – DỰ BÁO 6 NGÀY TỚI + HÔM NAY
            // ==========================================
            views.setImageViewResource(
                R.id.iv_bottom_today_icon,
                WeatherUtils.getWeatherIcon(weatherData.current.weatherCode)
            )
            views.setTextViewText(R.id.tv_bottom_today_temp, "${weatherData.current.temp}°C")
            views.setTextViewText(
                R.id.tv_bottom_today_minmax,
                "${weatherData.current.minTempToday}°  ${weatherData.current.maxTempToday}°"
            )

            // 6 cột bên phải
            val forecast = weatherData.forecast6Days
            bindDailyForecastColumn(views, 1, forecast.getOrNull(0))
            bindDailyForecastColumn(views, 2, forecast.getOrNull(1))
            bindDailyForecastColumn(views, 3, forecast.getOrNull(2))
            bindDailyForecastColumn(views, 4, forecast.getOrNull(3))
            bindDailyForecastColumn(views, 5, forecast.getOrNull(4))
            bindDailyForecastColumn(views, 6, forecast.getOrNull(5))

            // ==========================================
            // SỰ KIỆN CHẠM (CLICK LISTENERS)
            // ==========================================
            try {
                val refreshIntent = Intent(context, OneUIWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH_WIDGET
                }
                val refreshPendingIntent = PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE_REFRESH,
                    refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_refresh_widget, refreshPendingIntent)

                // Bấm vào khu vực lịch -> mở ứng dụng Lịch của máy
                val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val calendarPendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_CALENDAR,
                    calendarIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.area_calendar_header, calendarPendingIntent)
                views.setOnClickPendingIntent(R.id.img_calendar_grid, calendarPendingIntent)

                // Bấm vào khu vực thời tiết -> mở MainActivity của app để cấu hình
                val configIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val configPendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_CONFIG,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.area_weather_current, configPendingIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi thiết lập click listener", e)
            }
        }

        private fun bindDailyForecastColumn(views: RemoteViews, colIndex: Int, day: DailyForecast?) {
            val (dayId, iconId, minId, maxId) = when (colIndex) {
                1 -> listOf(R.id.tv_f1_day, R.id.iv_f1_icon, R.id.tv_f1_min, R.id.tv_f1_max)
                2 -> listOf(R.id.tv_f2_day, R.id.iv_f2_icon, R.id.tv_f2_min, R.id.tv_f2_max)
                3 -> listOf(R.id.tv_f3_day, R.id.iv_f3_icon, R.id.tv_f3_min, R.id.tv_f3_max)
                4 -> listOf(R.id.tv_f4_day, R.id.iv_f4_icon, R.id.tv_f4_min, R.id.tv_f4_max)
                5 -> listOf(R.id.tv_f5_day, R.id.iv_f5_icon, R.id.tv_f5_min, R.id.tv_f5_max)
                6 -> listOf(R.id.tv_f6_day, R.id.iv_f6_icon, R.id.tv_f6_min, R.id.tv_f6_max)
                else -> return
            }

            if (day != null) {
                views.setTextViewText(dayId, day.dayOfWeek)
                views.setImageViewResource(iconId, day.getWeatherIconRes())
                views.setTextViewText(minId, "↓ ${day.minTemp}°")
                views.setTextViewText(maxId, "↑ ${day.maxTemp}°")
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        try {
            val weatherRepo = WeatherRepository(context)
            val initialWeather = weatherRepo.getCachedOrDefaultWeather()
            val upcomingEvents = safeGetEvents(context)
            val prefs = WidgetPreferences(context)

            for (widgetId in appWidgetIds) {
                safeUpdateWidget(context, appWidgetManager, widgetId, initialWeather, upcomingEvents, prefs)
            }

            // Kích hoạt cập nhật dữ liệu mới qua mạng ngầm
            updateAllWidgets(context)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi onUpdate", e)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        try {
            updateAllWidgets(context)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi onAppWidgetOptionsChanged", e)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        try {
            MidnightAlarmReceiver.scheduleMidnightAlarm(context)
            WidgetUpdateWorker.enqueuePeriodicWork(context)
            updateAllWidgets(context)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi onEnabled", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        try {
            if (intent.action == ACTION_REFRESH_WIDGET) {
                Toast.makeText(context, "Đang làm mới dữ liệu widget...", Toast.LENGTH_SHORT).show()
                WidgetUpdateWorker.enqueueImmediateWork(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi onReceive", e)
        }
    }
}
