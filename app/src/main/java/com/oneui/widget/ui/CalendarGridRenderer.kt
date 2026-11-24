package com.oneui.widget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import java.util.Calendar

object CalendarGridRenderer {

    private const val TAG = "CalendarGrid"
    private const val RED_TODAY = 0xFFE53935.toInt()
    private const val RED_SUNDAY = 0xFFFF4D4D.toInt()
    private const val WHITE_MAIN = 0xFFFFFFFF.toInt()
    private const val WHITE_HEADER = 0xB3FFFFFF.toInt()
    private const val DIMMED_WHITE = 0x59FFFFFF.toInt() // 35-40% alpha
    private const val DIMMED_RED = 0x59FF4D4D.toInt()

    private val HEADERS = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

    /**
     * Vẽ lưới lịch tháng sắc nét trả về Bitmap để gán vào ImageView của RemoteViews.
     * Sử dụng kích thước cố định pixel (không phụ thuộc density) để tránh lỗi trong BroadcastReceiver context.
     * Giảm bitmap xuống 350x250 để tránh TransactionTooLargeException qua IPC.
     */
    fun renderCalendarBitmap(context: Context, widthPx: Int = 350, heightPx: Int = 250): Bitmap {
        // Dùng density cố định = 2.0 thay vì đọc từ context (tránh crash trong widget receiver context)
        val density = try {
            val d = context.resources.displayMetrics.density
            if (d > 0f) d else 2.0f
        } catch (e: Exception) {
            2.0f
        }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paint cho tiêu đề (T2..CN)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Paint cho ngày trong tháng
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        // Paint cho vòng tròn nổi bật ngày hôm nay (#E53935)
        val todayCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED_TODAY
            style = Paint.Style.FILL
        }

        // Paint chữ ngày hôm nay (In đậm, màu trắng)
        val todayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE_MAIN
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val colWidth = widthPx / 7f
        val topPadding = 8f * density
        val headerHeight = 18f * density
        val availableHeight = heightPx - topPadding - headerHeight
        val rowHeight = availableHeight / 6f

        // 1. Vẽ dòng tiêu đề thứ (T2 -> CN)
        val headerBaseline = topPadding + (headerPaint.textSize * 0.8f)
        for (i in 0 until 7) {
            val cx = colWidth * i + colWidth / 2f
            headerPaint.color = if (i == 6) RED_SUNDAY else WHITE_HEADER
            canvas.drawText(HEADERS[i], cx, headerBaseline, headerPaint)
        }

        // 2. Tính toán ngày trong tháng (bắt đầu tuần từ Thứ Hai)
        val todayCal = Calendar.getInstance()
        val currentDay = todayCal.get(Calendar.DAY_OF_MONTH)
        val currentMonth = todayCal.get(Calendar.MONTH)
        val currentYear = todayCal.get(Calendar.YEAR)

        val cal = Calendar.getInstance().apply {
            set(currentYear, currentMonth, 1)
        }

        // Thứ Hai là ngày đầu tuần. Trong Calendar: MONDAY=2, SUNDAY=1
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Offset: Thứ 2 = 0, Thứ 3 = 1, ..., CN = 6
        val offset = when (firstDayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        // Số ngày của tháng trước
        val prevCal = Calendar.getInstance().apply {
            set(currentYear, currentMonth, 1)
            add(Calendar.DAY_OF_MONTH, -1)
        }
        val daysInPrevMonth = prevCal.get(Calendar.DAY_OF_MONTH)
        val daysInCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val circleRadius = 11f * density

        // Duyệt qua 6 hàng x 7 cột = 42 ô
        for (row in 0 until 6) {
            val cy = topPadding + headerHeight + row * rowHeight + (rowHeight / 2f)
            // Tính baseline cho text căn giữa theo chiều dọc
            val textY = cy + (dayPaint.textSize / 3f)

            for (col in 0 until 7) {
                val index = row * 7 + col
                val cx = colWidth * col + colWidth / 2f
                val isSunday = (col == 6)

                when {
                    // Ngày của tháng trước
                    index < offset -> {
                        val dayVal = daysInPrevMonth - (offset - 1) + index
                        dayPaint.color = if (isSunday) DIMMED_RED else DIMMED_WHITE
                        canvas.drawText(dayVal.toString(), cx, textY, dayPaint)
                    }
                    // Ngày của tháng hiện tại
                    index < offset + daysInCurrentMonth -> {
                        val dayVal = index - offset + 1
                        if (dayVal == currentDay) {
                            // Vẽ vòng tròn đỏ rực (#E53935)
                            canvas.drawCircle(cx, cy, circleRadius, todayCirclePaint)
                            // Vẽ số ngày màu trắng đậm
                            canvas.drawText(dayVal.toString(), cx, textY, todayTextPaint)
                        } else {
                            dayPaint.color = if (isSunday) RED_SUNDAY else WHITE_MAIN
                            canvas.drawText(dayVal.toString(), cx, textY, dayPaint)
                        }
                    }
                    // Ngày của tháng sau
                    else -> {
                        val dayVal = index - (offset + daysInCurrentMonth) + 1
                        dayPaint.color = if (isSunday) DIMMED_RED else DIMMED_WHITE
                        canvas.drawText(dayVal.toString(), cx, textY, dayPaint)
                    }
                }
            }
        }

        return bitmap
    }
}
