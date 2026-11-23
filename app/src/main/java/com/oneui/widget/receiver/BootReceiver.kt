package com.oneui.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oneui.widget.ui.OneUIWidgetProvider
import com.oneui.widget.worker.WidgetUpdateWorker

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Lên lịch nhảy lịch lúc nửa đêm 00:00
            MidnightAlarmReceiver.scheduleMidnightAlarm(context)

            // Khởi động chu kỳ 30 phút/lần của WorkManager
            WidgetUpdateWorker.enqueuePeriodicWork(context)

            // Cập nhật lại giao diện widget
            OneUIWidgetProvider.updateAllWidgets(context)
        }
    }
}
