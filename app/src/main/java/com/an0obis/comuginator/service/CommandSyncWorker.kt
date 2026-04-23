package com.an0obis.comuginator.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.CommandDto
import com.an0obis.comuginator.storage.SessionStore

class CommandSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val sessionStore = SessionStore(applicationContext)
            val token = sessionStore.token ?: return Result.success()

            val response = ApiClient.api.getPendingCommands(
                auth = "Bearer $token"
            )

            val items = response.items ?: emptyList()

            for (command in items) {
                when (command.type) {
                    "aac_message_available" -> {
                        handleNewMessageCommand(command)
                    }

                    "aac_reply_available" -> {
                        handleNewReplyCommand(command)
                    }
                    else -> {
                        // unknown command type for now
                        Log.d("CommandSyncWorker", "unknown command type ${command.type}")
                    }
                }

                ApiClient.api.ackCommand(
                    commandId = command.id,
                    auth = "Bearer $token"
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun handleNewMessageCommand(command: CommandDto) {
        NotificationHelper.showNewMessageNotification(
            context = applicationContext
        )
    }

    private fun handleNewReplyCommand(command: CommandDto) {
       NotificationHelper.showNewReplyNotification(
            context = applicationContext
        )
    }
}