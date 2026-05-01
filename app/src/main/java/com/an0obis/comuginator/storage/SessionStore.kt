package com.an0obis.comuginator.storage
import androidx.core.content.edit

import android.content.Context

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("comuginator_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit { putString("token", value) }

    var familyId: String?
        get() = prefs.getString("familyId", null)
        set(value) = prefs.edit { putString("familyId", value) }

    var userId: String?
        get() = prefs.getString("userId", null)
        set(value) = prefs.edit { putString("userId", value) }

    var deviceId: String?
        get() = prefs.getString("deviceId", null)
        set(value) = prefs.edit { putString("deviceId", value) }

    var userName: String?
        get() = prefs.getString("userName", null)
        set(value) = prefs.edit { putString("userName", value) }

    var deviceName: String?
        get() = prefs.getString("deviceName", null)
        set(value) = prefs.edit { putString("deviceName", value) }

    var lastUsedInviteId: String?
        get() = prefs.getString("last_used_invite_id", null)
        set(value) {
            prefs.edit {
                putString("last_used_invite_id", value)
            }
        }

    fun clearLastUsedInviteId() {
        prefs.edit {
            remove("last_used_invite_id")
        }
    }

    fun isConnected(): Boolean = !token.isNullOrBlank()

    fun authHeader(): String? = token?.let { "Bearer $it" }

    fun authHeaderOrThrow(): String {
        val token = token ?: error("No token in SessionStore")
        return "Bearer $token"
    }

    var appLanguage: String?
        get() = prefs.getString("appLanguage", null)
        set(value) = prefs.edit { putString("appLanguage", value) }

    var role: String?
        get() = prefs.getString("role", null)
        set(value) = prefs.edit { putString("role", value) }

    fun clear() {
        prefs.edit { clear() }
    }

}