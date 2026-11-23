package com.oneui.widget.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.oneui.widget.ui.OneUIWidgetProvider
import java.util.Calendar

class MidnightAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Cập nhật lại toàn bộ widget ngay lúc 00:00:01 để đổi ngày, đổi thứ, nhảy lịch âm và lưới lịch
        OneUIWidgetProvider.updateAllWidgets(context)

        // Đặt báo thức cho 00:00:01 ngày tiếp theo
        scheduleMidnightAlarm(context)
    }

    companion object {
        const val ACTION_MIDNIGHT_TICK = "com.oneui.widget.ACTION_MIDNIGHT_TICK"
        private const val REQUEST_CODE_MIDNIGHT = 1001

        fun scheduleMidnightAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, MidnightAlarmReceiver::class.java).apply {
                action = ACTION_MIDNIGHT_TICK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_MIDNIGHT,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Tính mốc 00:00:01 ngày mai
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        cal.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                // Trên Android 12+ nếu chưa cấp quyền SCHEDULE_EXACT_ALARM, dùng setAndAllowWhileIdle
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    cal.timeInMillis,
                    pendingIntent
                )
            }
        }
    }
}
