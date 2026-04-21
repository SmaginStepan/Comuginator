package com.example.comuginator.service

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

object CommandSyncScheduler {
    private const val IMMEDIATE_COMMAND_SYNC_WORK_NAME = "immediate-command-sync"

    fun enqueueImmediate(context: Context, reason: String) {
        val request = OneTimeWorkRequestBuilder<CommandSyncWorker>()
            .setInputData(workDataOf("reason" to reason))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_COMMAND_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}