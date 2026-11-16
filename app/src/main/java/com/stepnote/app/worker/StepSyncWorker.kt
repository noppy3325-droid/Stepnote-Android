package com.stepnote.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stepnote.app.StepnoteApplication
import com.stepnote.app.data.health.SourceFetchResult
import com.stepnote.app.widget.StepnoteWidget
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class StepSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? StepnoteApplication ?: return Result.failure()
        val repo = app.container.stepRepository
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // Sync yesterday and today so midnight rollover snapshots remain intact
        val yesterday = repo.syncDay(today.minusDays(1), zone, background = true)
        val current = repo.syncDay(today, zone, background = true)

        StepnoteWidget().updateAll(applicationContext)
        return if (runAttemptCount < 2 &&
            (yesterday is SourceFetchResult.Failure || current is SourceFetchResult.Failure)
        ) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "stepnote_periodic_sync"
        private const val ONE_TIME_WORK_NAME = "stepnote_immediate_sync"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSyncWorker>(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<StepSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

class BootRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                StepSyncWorker.schedulePeriodic(context)
                StepSyncWorker.enqueueImmediate(context)
            }
        }
    }
}
