package com.oneui.widget.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Calendar

object CalendarGridRenderer {

    private const val RED_TODAY = 0xFFE53935.toInt()
    private const val RED_SUNDAY = 0xFFFF4D4D.toInt()
    private const val WHITE_MAIN = 0xFFFFFFFF.toInt()
    private const val WHITE_HEADER = 0xB3FFFFFF.toInt()
    private const val DIMMED_WHITE = 0x4DFFFFFF.toInt() // 30% alpha for other month
    private const val DIMMED_RED = 0x4DFF4D4D.toInt()

    private val HEADERS = arrayOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

    /**
     * Vẽ số ngày lớn với hiệu ứng Gradient chuyển màu từ trắng sáng ở đỉnh xuống mờ dần ở chân số
     * Khớp 100% với ảnh mẫu thiết kế gốc của One UI.
     */
    fun renderDayNumberBitmap(day: Int, widthPx: Int = 220, heightPx: Int = 160): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 148f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)

            // Gradient: Trắng tinh ở trên -> chuyển mờ dần trong suốt 35% ở chân số
            shader = LinearGradient(
                0f, 10f, 0f, heightPx.toFloat(),
                intArrayOf(
                    0xFFFFFFFF.toInt(),
                    0xEEFFFFFF.toInt(),
                    0x99FFFFFF.toInt(),
                    0x40FFFFFF.toInt()
                ),
                floatArrayOf(0f, 0.40f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        val text = day.toString()
        // Căn giữa theo chiều dọc
        val textY = heightPx / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, widthPx / 2f, textY, paint)

        return bitmap
    }

    /**
     * Vẽ lưới lịch tháng sắc nét với tỷ lệ pixel cố định chuẩn,
     * đảm bảo khoảng cách giữa các số đều, thoáng, không bao giờ bị dính hay đè lên nhau.
     */
    fun renderCalendarBitmap(context: Context, widthPx: Int = 420, heightPx: Int = 300): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paint cho tiêu đề (T2..CN)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        // Paint cho ngày thường
        val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 21f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        // Paint cho vòng tròn đỏ ngày hôm nay
        val todayCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED_TODAY
            style = Paint.Style.FILL
        }

        // Paint chữ ngày hôm nay (In đậm, màu trắng)
        val todayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE_MAIN
            textSize = 21f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

        val colWidth = widthPx / 7f
        val topPadding = 12f
        val headerHeight = 28f
        val availableHeight = heightPx - topPadding - headerHeight
        val rowHeight = availableHeight / 6f
        val circleRadius = 17f

        // 1. Vẽ dòng tiêu đề thứ (T2 -> CN)
        val headerBaseline = topPadding + (headerPaint.textSize * 0.85f)
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

        // Thứ Hai là ngày đầu tuần. MONDAY=2, SUNDAY=1
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
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

        // 3. Duyệt qua 6 hàng x 7 cột = 42 ô
        for (row in 0 until 6) {
            val cy = topPadding + headerHeight + row * rowHeight + (rowHeight / 2f)
            val textY = cy + (dayPaint.textSize / 3f) - 1f

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
