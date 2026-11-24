package com.oneui.widget.data

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.oneui.widget.lunar.LunarCalendarConverter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class WidgetEventItem(
    val title: String,
    val dateInfo: String,      // e.g. "25-12  All Day"
    val sourceInfo: String,    // e.g. "Ngày lễ Việt Nam"
    val timestamp: Long
) {
    val subtitle: String
        get() = "$dateInfo • $sourceInfo"
}

class CalendarEventHelper(private val context: Context) {

    fun getUpcomingEvents(limit: Int = 2): List<WidgetEventItem> {
        val result = mutableListOf<WidgetEventItem>()

        try {
            // 1. Lấy sự kiện từ thiết bị nếu có quyền READ_CALENDAR
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val deviceEvents = queryDeviceCalendar(limit)
                result.addAll(deviceEvents)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            // 2. Thêm ngày lễ Việt Nam (Dương lịch & Âm lịch) nếu danh sách chưa đủ số lượng
            val now = System.currentTimeMillis()
            val holidays = getVietnameseHolidays().filter { it.timestamp >= now }
                .sortedBy { it.timestamp }

            for (h in holidays) {
                if (result.size >= limit) break
                // Tránh trùng lặp tiêu đề
                if (result.none { it.title.equals(h.title, ignoreCase = true) }) {
                    result.add(h)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sắp xếp lại theo thời gian
        result.sortBy { it.timestamp }
        return result.take(limit)
    }

    private fun queryDeviceCalendar(limit: Int): List<WidgetEventItem> {
        val list = mutableListOf<WidgetEventItem>()
        val now = System.currentTimeMillis()
        val oneMonthLater = now + 45L * 24 * 60 * 60 * 1000 // 45 ngày tới

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, now)
        ContentUris.appendId(uriBuilder, oneMonthLater)

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME
        )

        val cursor: Cursor? = try {
            context.contentResolver.query(
                uriBuilder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )
        } catch (e: Exception) {
            null
        }

        cursor?.use {
            val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
            val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val calNameIdx = it.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

            val dateFormat = SimpleDateFormat("dd-MM", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            while (it.moveToNext() && list.size < limit) {
                val title = it.getString(titleIdx) ?: "Sự kiện"
                val begin = it.getLong(beginIdx)
                val isAllDay = it.getInt(allDayIdx) == 1
                val calName = it.getString(calNameIdx) ?: "Lịch cá nhân"

                val datePart = dateFormat.format(begin)
                val timePart = if (isAllDay) "All Day" else timeFormat.format(begin)
                val dateInfo = "$datePart  $timePart"
                val sourceInfo = if (calName.length > 18) calName.take(16) + "..." else calName

                list.add(WidgetEventItem(title, dateInfo, sourceInfo, begin))
            }
        }

        return list
    }

    /**
     * Danh sách ngày lễ truyền thống & quốc gia của Việt Nam (tự động tính thời gian theo năm hiện tại và tiếp theo)
     */
    private fun getVietnameseHolidays(): List<WidgetEventItem> {
        val list = mutableListOf<WidgetEventItem>()
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)

        for (year in currentYear..(currentYear + 1)) {
            // Ngày lễ Dương lịch cố định
            addSolarHoliday(list, year, 1, 1, "Tết Dương lịch")
            addSolarHoliday(list, year, 2, 14, "Lễ Tình nhân (Valentine)")
            addSolarHoliday(list, year, 3, 8, "Quốc tế Phụ nữ")
            addSolarHoliday(list, year, 4, 30, "Giải phóng miền Nam")
            addSolarHoliday(list, year, 5, 1, "Quốc tế Lao động")
            addSolarHoliday(list, year, 6, 1, "Quốc tế Thiếu nhi")
            addSolarHoliday(list, year, 9, 2, "Quốc khánh Việt Nam")
            addSolarHoliday(list, year, 10, 20, "Phụ nữ Việt Nam")
            addSolarHoliday(list, year, 11, 20, "Nhà giáo Việt Nam")
            addSolarHoliday(list, year, 12, 22, "Thành lập QĐND Việt Nam")
            addSolarHoliday(list, year, 12, 25, "Lễ Giáng Sinh")

            // Ngày lễ Âm lịch (tính toán chuyển đổi sang Dương lịch)
            addLunarHoliday(list, year, 1, 1, "Mùng 1 Tết Nguyên Đán")
            addLunarHoliday(list, year, 1, 2, "Mùng 2 Tết Nguyên Đán")
            addLunarHoliday(list, year, 1, 3, "Mùng 3 Tết Nguyên Đán")
            addLunarHoliday(list, year, 1, 15, "Tết Nguyên Tiêu")
            addLunarHoliday(list, year, 3, 10, "Giỗ Tổ Hùng Vương")
            addLunarHoliday(list, year, 4, 15, "Lễ Phật Đản")
            addLunarHoliday(list, year, 5, 5, "Tết Đoan Ngọ")
            addLunarHoliday(list, year, 7, 15, "Lễ Vu Lan")
            addLunarHoliday(list, year, 8, 15, "Tết Trung Thu")
            addLunarHoliday(list, year, 12, 23, "Ông Táo chầu trời")
        }

        return list
    }

    private fun addSolarHoliday(list: MutableList<WidgetEventItem>, year: Int, month: Int, day: Int, title: String) {
        val cal = Calendar.getInstance().apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dateStr = String.format(Locale.getDefault(), "%02d-%02d", day, month)
        list.add(
            WidgetEventItem(
                title = title,
                dateInfo = "$dateStr  All Day",
                sourceInfo = "Ngày lễ Việt Nam",
                timestamp = cal.timeInMillis
            )
        )
    }

    private fun addLunarHoliday(list: MutableList<WidgetEventItem>, lunarYear: Int, lunarMonth: Int, lunarDay: Int, title: String) {
        try {
            val approximateSolarMonth = when (lunarMonth) {
                1 -> 2
                12 -> 1
                else -> lunarMonth + 1
            }
            val cal = Calendar.getInstance().apply {
                set(lunarYear, (approximateSolarMonth - 2).coerceAtLeast(0), 1, 0, 0, 0)
            }

            for (i in 0..60) {
                val d = cal.get(Calendar.DAY_OF_MONTH)
                val m = cal.get(Calendar.MONTH) + 1
                val y = cal.get(Calendar.YEAR)

                val lunar = LunarCalendarConverter.convertSolarToLunar(d, m, y)
                if (lunar.month == lunarMonth && lunar.day == lunarDay && !lunar.isLeap) {
                    val dateStr = String.format(Locale.getDefault(), "%02d-%02d", d, m)
                    list.add(
                        WidgetEventItem(
                            title = title,
                            dateInfo = "$dateStr  All Day",
                            sourceInfo = "Lịch Âm ($lunarDay/$lunarMonth ÂL)",
                            timestamp = cal.timeInMillis
                        )
                    )
                    break
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
