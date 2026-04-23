package com.example.comuginator.service

import android.util.Log
import com.example.comuginator.storage.FcmTokenStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ComuginatorFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "onNewToken: $token")

        FcmTokenStore(applicationContext).savePendingToken(token)
        FcmTokenSyncScheduler.enqueueImmediate(applicationContext, "on_new_token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d("FCM", "message data=${message.data}")

        when (message.data["type"]) {
            "sync_commands" -> {
                CommandSyncScheduler.enqueueImmediate(
                    applicationContext,
                    message.data["reason"] ?: "fcm_push"
                )
            }
        }
    }
}