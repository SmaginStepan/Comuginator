package com.example.comuginator.service

import com.example.comuginator.storage.FcmTokenStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class ComuginatorFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        FcmTokenStore(this).savePendingToken(token)
        FcmTokenSyncScheduler.enqueueImmediate(this, "new_token")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"] ?: return
        when (type) {
            "sync_commands" -> {
                CommandSyncScheduler.enqueueImmediate(this, "fcm_push")
            }
        }
    }
}