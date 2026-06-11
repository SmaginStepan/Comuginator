package com.an0obis.comuginator.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import com.an0obis.comuginator.R
import com.an0obis.comuginator.storage.SettingsStore
import com.an0obis.comuginator.ui.messaging.IncomingMessageActivity

object NotificationHelper {

    private const val CHANNEL_ID_BASE = "comuginator_messages"
    private const val CHANNEL_NAME = "Messages"

    private fun currentChannelId(store: SettingsStore): String {
        val version = store.notificationChannelVersion
        return if (version == 0) CHANNEL_ID_BASE else "${CHANNEL_ID_BASE}_v$version"
    }

    fun showNewMessageNotification(
        context: Context,
        messageId: String?,
        commandId: String?,
        senderName: String? = null,
        senderAvatar: Bitmap? = null
    ) {
        if (!NotificationPolicy.isEnabled(context)) {
            Log.d("NotificationHelper", "suppressed: notifications disabled in settings")
            return
        }
        val channelId = ensureChannel(context)

        val intent = Intent(context, IncomingMessageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(IncomingMessageActivity.EXTRA_MESSAGE_ID, messageId)
            putExtra(IncomingMessageActivity.EXTRA_COMMAND_ID, commandId)
            putExtra(IncomingMessageActivity.EXTRA_MODE, IncomingMessageActivity.MODE_MESSAGE)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_comuginator)
            .setContentTitle(senderName ?: context.getString(R.string.new_message))
            .setContentText(context.getString(R.string.tap_to_open))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (senderAvatar != null) {
            builder.setLargeIcon(senderAvatar)
        }

        applyLegacySound(context, builder)

        val notification = builder.build()
        val notificationId =  notificationIdForMessage(messageId)

        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId, notification)
        } catch (_: SecurityException) {
            Log.d("NotificationHelper", "can't show notification")
        }
    }

    fun showNewReplyNotification(
        context: Context,
        messageId: String?,
        commandId: String?
    ) {
        if (!NotificationPolicy.isEnabled(context)) {
            Log.d("NotificationHelper", "suppressed: notifications disabled in settings")
            return
        }
        val channelId = ensureChannel(context)

        val intent = Intent(context, IncomingMessageActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(IncomingMessageActivity.EXTRA_MESSAGE_ID, messageId)
            putExtra(IncomingMessageActivity.EXTRA_COMMAND_ID, commandId)
            putExtra(IncomingMessageActivity.EXTRA_MODE, IncomingMessageActivity.MODE_REPLY)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val settings = SettingsStore(context)

        val builder  = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_comuginator)
            .setContentTitle(context.getString(R.string.new_reply))
            .setContentText(context.getString(R.string.tap_to_open))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (settings.openIncomingFullscreen) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                //.setFullScreenIntent(pendingIntent, true)
        }

        applyLegacySound(context, builder)

        val notification = builder.build()
        val notificationId = notificationIdForMessage(messageId)

        try {
            NotificationManagerCompat.from(context)
                .notify(notificationId, notification)
        } catch (_: SecurityException) {
            Log.d("NotificationHelper", "can't show notification")
        }
    }

    /** Creates (or reuses) the channel for the current sound and returns its id. */
    private fun ensureChannel(context: Context): String {
        val store = SettingsStore(context)
        val channelId = currentChannelId(store)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            when (val sound = store.notificationSound) {
                null -> Unit // system default
                "" -> channel.setSound(null, null) // silent
                else -> channel.setSound(
                    sound.toUri(),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
            }
            manager.createNotificationChannel(channel)
        }

        // Channels are immutable, so sound changes create a new version —
        // drop the stale ones.
        manager.notificationChannels
            .filter { it.id.startsWith(CHANNEL_ID_BASE) && it.id != channelId }
            .forEach { manager.deleteNotificationChannel(it.id) }

        return channelId
    }

    /** Pre-O devices have no channels; the sound goes on the notification itself. */
    private fun applyLegacySound(context: Context, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return
        when (val sound = SettingsStore(context).notificationSound) {
            null -> Unit
            "" -> builder.setSound(null)
            else -> builder.setSound(sound.toUri())
        }
    }

    fun notificationIdForMessage(messageId: String?): Int {
        return messageId.hashCode()
    }
}