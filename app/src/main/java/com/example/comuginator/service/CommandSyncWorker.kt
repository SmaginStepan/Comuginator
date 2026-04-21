package com.example.comuginator.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.CommandDto
import com.example.comuginator.storage.SessionStore

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
                        Log.d("CommandSyncWorker", "$command.type")
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
//        NotificationHelper.showNewMessageNotification(
//            context = applicationContext,
//            title = "New message",
//            text = "Tap to open"
//        )
    }

    private fun handleNewReplyCommand(command: CommandDto) {
//        NotificationHelper.showNewReplyNotification(
//            context = applicationContext,
//            title = "New reply",
//            text = "Tap to open"
//        )
    }
}