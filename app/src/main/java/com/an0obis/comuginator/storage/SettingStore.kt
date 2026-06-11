package com.an0obis.comuginator.storage

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A scheduled notifications on/off switch. Fires at [time] either on the
 * given [weekdays] (1=Mon..7=Sun, repeats weekly) or once on [date].
 */
data class NotificationRule(
    val id: String,
    val enable: Boolean,
    val time: String,                       // "HH:mm"
    val weekdays: List<Int> = emptyList(),
    val date: String? = null                // "yyyy-MM-dd"
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var openIncomingFullscreen: Boolean
        get() = prefs.getBoolean("open_incoming_fullscreen", false)
        set(value) {
            prefs.edit { putBoolean("open_incoming_fullscreen", value) }
        }

    // ── Notifications on/off ────────────────────────────────────────────────
    // The manual toggle is an event with a timestamp so scheduled rules and
    // the manual switch can compete on "last event wins".

    val notificationsEnabledManually: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)

    val notificationsToggledAt: Long
        get() = prefs.getLong("notifications_toggled_at", 0L)

    fun setNotificationsEnabledManually(enabled: Boolean) {
        prefs.edit {
            putBoolean("notifications_enabled", enabled)
            putLong("notifications_toggled_at", System.currentTimeMillis())
        }
    }

    // ── Notification sound ──────────────────────────────────────────────────
    // null = system default, "" = silent, otherwise a ringtone URI.
    // Channels are immutable after creation (API 26+), so every sound change
    // bumps the version, which becomes part of the channel id.

    val notificationSound: String?
        get() = prefs.getString("notification_sound", null)

    val notificationChannelVersion: Int
        get() = prefs.getInt("notification_channel_version", 0)

    fun setNotificationSound(uri: String?) {
        prefs.edit {
            if (uri == null) remove("notification_sound") else putString("notification_sound", uri)
            putInt("notification_channel_version", notificationChannelVersion + 1)
        }
    }

    fun getNotificationRules(): List<NotificationRule> {
        val json = prefs.getString("notification_rules", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NotificationRule>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addNotificationRule(rule: NotificationRule) {
        saveNotificationRules(getNotificationRules() + rule)
    }

    fun removeNotificationRule(id: String) {
        saveNotificationRules(getNotificationRules().filterNot { it.id == id })
    }

    private fun saveNotificationRules(rules: List<NotificationRule>) {
        prefs.edit { putString("notification_rules", Gson().toJson(rules)) }
    }
}
