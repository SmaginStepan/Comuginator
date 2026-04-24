package com.an0obis.comuginator.storage

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var openIncomingFullscreen: Boolean
        get() = prefs.getBoolean("open_incoming_fullscreen", false)
        set(value) {
            prefs.edit().putBoolean("open_incoming_fullscreen", value).apply()
        }
}