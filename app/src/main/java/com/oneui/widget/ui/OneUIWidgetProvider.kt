package com.oneui.widget.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.RemoteViews
import android.widget.Toast
import com.oneui.widget.MainActivity
import com.oneui.widget.R
import com.oneui.widget.data.CalendarEventHelper
import com.oneui.widget.data.DailyForecast
import com.oneui.widget.data.WeatherRepository
import com.oneui.widget.data.WeatherUtils
import com.oneui.widget.data.WidgetPreferences
import com.oneui.widget.lunar.LunarCalendarConverter
import com.oneui.widget.receiver.MidnightAlarmReceiver
import com.oneui.widget.worker.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class OneUIWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        updateAllWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAllWidgets(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightAlarmReceiver.scheduleMidnightAlarm(context)
        WidgetUpdateWorker.enqueuePeriodicWork(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            Toast.makeText(context, "Đang làm mới dữ liệu widget...", Toast.LENGTH_SHORT).show()
            WidgetUpdateWorker.enqueueImmediateWork(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.oneui.widget.ACTION_REFRESH_WIDGET"
        private const val REQUEST_CODE_REFRESH = 2001
        private const val REQUEST_CODE_CALENDAR = 2002
        private const val REQUEST_CODE_CONFIG = 2003

        fun updateAllWidgets(context: Context) {
            CoroutineScope(Dispatchers.Main).launch {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, OneUIWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isEmpty()) return@launch

                val weatherRepo = WeatherRepository(context)
                val weatherData = weatherRepo.getWeatherData()
                val eventHelper = CalendarEventHelper(context)
                val upcomingEvents = eventHelper.getUpcomingEvents(2)
                val prefs = WidgetPreferences(context)

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_large)
                    buildWidgetViews(context, views, weatherData, upcomingEvents, prefs)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        private fun buildWidgetViews(
            context: Context,
            views: RemoteViews,
            weatherData: com.oneui.widget.data.WidgetWeatherData,
            upcomingEvents: List<com.oneui.widget.data.WidgetEventItem>,
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
            // Lời chào tự động theo giờ hệ thống
            views.setTextViewText(R.id.tv_greeting, WeatherUtils.getGreetingByHour(hour))

            // Nhiệt độ chính cỡ lớn, thanh mảnh
            views.setTextViewText(R.id.tv_main_temp, "${weatherData.current.temp}°")

            // Mô tả trạng thái tiếng Việt & tên địa điểm
            views.setTextViewText(R.id.tv_weather_desc, weatherData.current.conditionDescription)
            views.setTextViewText(R.id.tv_location_name, weatherData.current.locationName)

            // 2 Pill Badge viền trắng mảnh: Bình minh / Hoàng hôn
            views.setTextViewText(R.id.tv_sunrise_info, "${weatherData.current.minTempToday}°C")
            views.setTextViewText(R.id.tv_sunset_info, "${weatherData.current.maxTempToday}°C")

            // ==========================================
            // KHU VỰC 2: GÓC TRÊN BÊN PHẢI – LỊCH DƯƠNG & LỊCH ÂM VIỆT NAM
            // ==========================================
            // Tháng Dương lịch in hoa: "THÁNG 11"
            views.setTextViewText(R.id.tv_solar_month, "THÁNG $month")

            // Số ngày cực lớn: "23"
            views.setTextViewText(R.id.tv_solar_day, dayOfMonth.toString())

            // Thứ tiếng Việt
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

            // Khung bo tròn nền trắng đặc, chữ đen đậm: Ngày/Tháng Âm lịch + Can Chi
            val lunarDate = LunarCalendarConverter.getTodayLunar()
            views.setTextViewText(R.id.tv_lunar_badge, lunarDate.pillText)

            // ==========================================
            // KHU VỰC 3: GIỮA BÊN TRÁI – SỰ KIỆN & NGÀY LỄ VIỆT NAM
            // ==========================================
            if (upcomingEvents.isNotEmpty()) {
                val ev1 = upcomingEvents[0]
                views.setTextViewText(R.id.tv_event1_title, ev1.title)
                views.setTextViewText(R.id.tv_event1_subtitle, ev1.subtitle)
            } else {
                views.setTextViewText(R.id.tv_event1_title, "Lễ Giáng sinh (Noel)")
                views.setTextViewText(R.id.tv_event1_subtitle, "25-12 All Day • Ngày lễ Việt Nam")
            }

            if (upcomingEvents.size > 1) {
                val ev2 = upcomingEvents[1]
                views.setTextViewText(R.id.tv_event2_title, ev2.title)
                views.setTextViewText(R.id.tv_event2_subtitle, ev2.subtitle)
            } else {
                views.setTextViewText(R.id.tv_event2_title, "Tết Dương lịch")
                views.setTextViewText(R.id.tv_event2_subtitle, "01-01 All Day • Ngày lễ Việt Nam")
            }

            // ==========================================
            // KHU VỰC 4: GIỮA BÊN PHẢI – LƯỚI LỊCH THÁNG (CALENDAR GRID)
            // ==========================================
            val calendarBitmap = CalendarGridRenderer.renderCalendarBitmap(context, 440, 310)
            views.setImageViewBitmap(R.id.img_calendar_grid, calendarBitmap)

            // ==========================================
            // KHU VỰC 5: HÀNG DƯỚI CÙNG – DỰ BÁO 6 NGÀY TỚI + HÔM NAY
            // ==========================================
            // Cột trái ngoài cùng: icon lớn + nhiệt độ hiện tại + min/max
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
            // ĐỘ MỜ NỀN TRONG SUỐT THEO TÙY CHỈNH
            // ==========================================
            val opacity = prefs.backgroundOpacity // 0% - 50%
            val alpha = (opacity * 255) / 100
            val bgColor = Color.argb(alpha, 18, 19, 22)
            views.setInt(R.id.widget_root, "setBackgroundColor", bgColor)

            // ==========================================
            // SỰ KIỆN CHẠM (CLICK LISTENERS)
            // ==========================================
            // Nút làm mới dữ liệu
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
}
