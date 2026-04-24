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
                Log.d("CommandSyncWorker", "command ${command.type}")
                when (command.type) {
                    "set_volume" -> {
                        handleSetVolumeCommand(command)
                    }
                    "aac_message_available" -> {
                        handleNewMessageCommand(command)
                    }
                    "aac_reply_available" -> {
                        handleNewReplyCommand(command)
                    }
                    else -> {

                    }
                }

                ApiClient.api.ackCommand(
                    commandId = command.id,
                    auth = "Bearer $token"
                )
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("CommandSyncWorker", "failed", e)
            Result.retry()
        }
    }

    private fun handleSetVolumeCommand(cmd: CommandDto) {
        val raw = cmd.payload["volumePercent"] ?: return
        val volumePercent = when (raw) {
            is Double -> raw.toInt()
            is Int -> raw
            else -> return
        }.coerceIn(0, 100)

        val audioManager =
            applicationContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val stream = android.media.AudioManager.STREAM_MUSIC
        val maxVolume = audioManager.getStreamMaxVolume(stream)
        val targetVolume = (maxVolume * volumePercent) / 100
        audioManager.setStreamVolume(stream, targetVolume, 0)
    }

    private fun handleNewMessageCommand(command: CommandDto) {
        NotificationHelper.showNewMessageNotification(
            context = applicationContext,
            messageId = command.payload["messageId"] as? String,
            commandId = command.id
        )
    }

    private fun handleNewReplyCommand(command: CommandDto) {
        NotificationHelper.showNewReplyNotification(
            context = applicationContext,
            messageId = command.payload["messageId"] as? String,
            commandId = command.id
        )
    }
}