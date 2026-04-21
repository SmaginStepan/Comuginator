package com.example.comuginator.service

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object TelemetryScheduler {

    private const val PERIODIC_TELEMETRY_WORK_NAME = "periodic-telemetry"
    private const val IMMEDIATE_TELEMETRY_WORK_NAME = "immediate-telemetry"

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<TelemetryWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_TELEMETRY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun enqueueImmediate(context: Context, reason: String) {
        val request = OneTimeWorkRequestBuilder<TelemetryWorker>()
            .setInputData(workDataOf("reason" to reason))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_TELEMETRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}