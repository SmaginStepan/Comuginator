package com.example.comuginator.storage

import android.content.Context

class FcmTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("fcm_store", Context.MODE_PRIVATE)

    var pendingToken: String?
        get() = prefs.getString("pending_token", null)
        set(value) {
            prefs.edit().putString("pending_token", value).apply()
        }

    fun savePendingToken(token: String) {
        pendingToken = token
    }

    fun clearPendingToken() {
        pendingToken = null
    }
}