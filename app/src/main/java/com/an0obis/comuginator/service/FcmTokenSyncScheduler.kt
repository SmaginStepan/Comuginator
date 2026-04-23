package com.an0obis.comuginator.service

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object FcmTokenSyncScheduler {
    private const val WORK_NAME = "fcm-token-sync"

    fun enqueueImmediate(context: Context, reason: String) {
        val request = OneTimeWorkRequestBuilder<FcmTokenSyncWorker>()
            .setInputData(workDataOf("reason" to reason))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}