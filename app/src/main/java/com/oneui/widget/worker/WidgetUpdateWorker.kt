package com.oneui.widget.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.oneui.widget.ui.OneUIWidgetProvider
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            OneUIWidgetProvider.updateAllWidgets(appContext)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val WORK_NAME_PERIODIC = "OneUIWidgetPeriodicUpdate"
        private const val WORK_NAME_IMMEDIATE = "OneUIWidgetImmediateUpdate"

        /**
         * Lên lịch cập nhật nền chu kỳ 30 phút/lần theo đúng yêu cầu
         */
        fun enqueuePeriodicWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                30, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }

        /**
         * Cập nhật ngay lập tức (dùng khi bấm nút làm mới)
         */
        fun enqueueImmediateWork(context: Context) {
            val immediateRequest = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .build()

            WorkManager.getInstance(context).enqueue(immediateRequest)
        }
    }
}
