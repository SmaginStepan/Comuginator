package com.an0obis.comuginator.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.an0obis.comuginator.R
import com.an0obis.comuginator.storage.SettingsStore
import com.an0obis.comuginator.ui.base.BaseActivity

class SettingsActivity : BaseActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var tvEnabled: TextView
    private lateinit var swOpenIncomingFullscreen: Switch
    private lateinit var btnFullscreenPermission: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsStore = SettingsStore(this)

        setContentView(R.layout.activity_settings)

        swOpenIncomingFullscreen = findViewById(R.id.swOpenIncomingFullscreen)
        btnFullscreenPermission = findViewById(R.id.btnFullscreenPermission)
        tvEnabled = findViewById(R.id.tvEnabled)

        swOpenIncomingFullscreen.isChecked = settingsStore.openIncomingFullscreen

        swOpenIncomingFullscreen.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.openIncomingFullscreen = isChecked
            updateEnabledState()
        }

        btnFullscreenPermission.setOnClickListener {
            openFullscreenPermissionSettings()
        }

        btnFullscreenPermission.isEnabled = Build.VERSION.SDK_INT >= 34

        updateEnabledState()
    }

    override fun onResume() {
        super.onResume()
        updateEnabledState()
    }

    private fun updateEnabledState() {
        var canUseFsi = true
        if (!settingsStore.openIncomingFullscreen) {
            canUseFsi = false
        }
        if (canUseFsi && Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            canUseFsi = nm.canUseFullScreenIntent()
        }
        if (canUseFsi) {
            tvEnabled.setText("Enabled")
        } else {
            tvEnabled.setText("Disabled")
        }
    }

    private fun openFullscreenPermissionSettings() {
        if (Build.VERSION.SDK_INT < 34) {
            Toast.makeText(
                this,
                "This setting is only needed on newer Android versions",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Could not open system settings",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}