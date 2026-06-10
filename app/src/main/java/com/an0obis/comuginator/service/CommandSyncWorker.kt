package com.an0obis.comuginator.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.CommandDto
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.widget.ComuginatorWidgetProvider


const val ACTION_INVITE_USED = "com.an0obis.comuginator.INVITE_USED"
const val ACTION_CHILD_HOME_SCHEDULE_APPLIED =
    "com.an0obis.comuginator.CHILD_HOME_SCHEDULE_APPLIED"
const val EXTRA_INVITE_ID = "inviteId"
class CommandSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val sessionStore = SessionStore(applicationContext)

            val response = ApiClient.api.getPendingCommands(
                auth = sessionStore.authHeader() ?: return Result.failure()
            )

            val items = response.items

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
                    "child_home_schedule_applied" -> {
                        applicationContext.sendBroadcast(
                            Intent(ACTION_CHILD_HOME_SCHEDULE_APPLIED)
                                .setPackage(applicationContext.packageName)
                        )
                    }
                    "invite_used" -> {
                        val inviteId = command.payload["inviteId"] as? String

                        if (!inviteId.isNullOrBlank()) {
                            sessionStore.lastUsedInviteId = inviteId

                            applicationContext.sendBroadcast(
                                Intent(ACTION_INVITE_USED)
                                    .setPackage(applicationContext.packageName)
                                    .putExtra(EXTRA_INVITE_ID, inviteId)
                            )
                        }
                    }
                    else -> {

                    }
                }

                ApiClient.api.ackCommand(
                    commandId = command.id,
                    auth = sessionStore.authHeader()?: return Result.failure()
                )
            }

            if (items.isNotEmpty()) {
                ComuginatorWidgetProvider.requestUpdate(applicationContext)
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
        val messageId = command.payload["messageId"] as? String

        var senderName: String? = null
        var senderAvatar = null as android.graphics.Bitmap?

        try {
            if (messageId != null) {
                val sessionStore = SessionStore(applicationContext)
                val authHeader = sessionStore.authHeader() ?: return

                val message = try {
                    ApiClient.getAacMessageWithAuthHeader(
                        authHeader = authHeader,
                        messageId = messageId
                    )
                } catch (e: Exception) {
                    // The message may belong to another family this device is in —
                    // probe them for the notification details (no context switch).
                    sessionStore.getFamilies()
                        .filter { it.familyId != sessionStore.familyId }
                        .firstNotNullOfOrNull { family ->
                            try {
                                ApiClient.getAacMessageWithAuthHeader(
                                    authHeader = authHeader,
                                    messageId = messageId,
                                    familyId = family.familyId
                                )
                            } catch (_: Exception) {
                                null
                            }
                        } ?: throw e
                }

                senderName = message.fromUser.name
                senderAvatar = ApiClient.loadBitmap(message.fromUser.avatarImageUrl, authHeader)
            }
        } catch (e: Exception) {
            Log.w("CommandSyncWorker", "failed to load notification details", e)
        }

        NotificationHelper.showNewMessageNotification(
            context = applicationContext,
            messageId = messageId,
            commandId = command.id,
            senderName = senderName,
            senderAvatar = senderAvatar
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