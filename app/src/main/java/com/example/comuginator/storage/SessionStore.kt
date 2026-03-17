package com.example.comuginator.storage

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("comuginator_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var familyId: String?
        get() = prefs.getString("familyId", null)
        set(value) = prefs.edit().putString("familyId", value).apply()

    var userId: String?
        get() = prefs.getString("userId", null)
        set(value) = prefs.edit().putString("userId", value).apply()

    var deviceId: String?
        get() = prefs.getString("deviceId", null)
        set(value) = prefs.edit().putString("deviceId", value).apply()

    var userName: String?
        get() = prefs.getString("userName", null)
        set(value) = prefs.edit().putString("userName", value).apply()

    var deviceName: String?
        get() = prefs.getString("deviceName", null)
        set(value) = prefs.edit().putString("deviceName", value).apply()

    fun isConnected(): Boolean = !token.isNullOrBlank()

    fun authHeader(): String? = token?.let { "Bearer $it" }

    fun clear() {
        prefs.edit().clear().apply()
    }
}