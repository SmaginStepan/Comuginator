package com.an0obis.comuginator.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.an0obis.comuginator.api.AacMessageDetailsDto
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

    /**
     * Loads a message for notification purposes. The message may belong to
     * another family this device is in, so the other memberships are probed
     * (no context switch from a background worker). Null if it can't be loaded.
     */
    private fun fetchMessage(messageId: String, authHeader: String): AacMessageDetailsDto? {
        val sessionStore = SessionStore(applicationContext)
        return try {
            ApiClient.getAacMessageWithAuthHeader(authHeader = authHeader, messageId = messageId)
        } catch (e: Exception) {
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
                }
        }
    }

    // A message a user sent to themselves (e.g. "Show message") must never
    // produce a notification — neither the message itself nor its reply.
    private fun isSelfMessage(message: AacMessageDetailsDto): Boolean =
        message.fromUser.id == message.toUser.id

    private fun handleNewMessageCommand(command: CommandDto) {
        val messageId = command.payload["messageId"] as? String

        var senderName: String? = null
        var senderAvatar = null as android.graphics.Bitmap?

        try {
            if (messageId != null) {
                val authHeader = SessionStore(applicationContext).authHeader() ?: return
                val message = fetchMessage(messageId, authHeader)

                if (message != null) {
                    if (isSelfMessage(message)) {
                        Log.d("CommandSyncWorker", "no notification: message $messageId is to self")
                        return
                    }
                    senderName = message.fromUser.name
                    senderAvatar = ApiClient.loadBitmap(message.fromUser.avatarImageUrl, authHeader)
                }
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
        val messageId = command.payload["messageId"] as? String

        if (messageId != null) {
            try {
                val authHeader = SessionStore(applicationContext).authHeader()
                val message = authHeader?.let { fetchMessage(messageId, it) }
                if (message != null && isSelfMessage(message)) {
                    Log.d("CommandSyncWorker", "no notification: reply to self message $messageId")
                    return
                }
            } catch (e: Exception) {
                Log.w("CommandSyncWorker", "failed to check reply message", e)
            }
        }

        NotificationHelper.showNewReplyNotification(
            context = applicationContext,
            messageId = messageId,
            commandId = command.id
        )
    }
}