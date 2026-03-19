package com.example.comuginator.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.comuginator.storage.SessionStore

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            val store = SessionStore(context)
            if (!store.token.isNullOrBlank()) {
                ConnectionService.start(context)
            }
        }
    }
}