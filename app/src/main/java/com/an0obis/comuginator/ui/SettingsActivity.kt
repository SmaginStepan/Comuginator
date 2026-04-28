package com.an0obis.comuginator.ui

import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.an0obis.comuginator.R
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import com.an0obis.comuginator.ui.base.BaseActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.LocaleListCompat

class SettingsActivity : BaseActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var tvEnabled: TextView
    private lateinit var swOpenIncomingFullscreen: SwitchCompat
    private lateinit var btnFullscreenPermission: Button
    private lateinit var btnLanguage: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsStore = SettingsStore(this)

        setContentView(R.layout.activity_settings)

        swOpenIncomingFullscreen = findViewById(R.id.swOpenIncomingFullscreen)
        btnFullscreenPermission = findViewById(R.id.btnFullscreenPermission)
        btnLanguage = findViewById(R.id.btnLanguage)
        tvEnabled = findViewById(R.id.tvEnabled)

        swOpenIncomingFullscreen.isChecked = settingsStore.openIncomingFullscreen

        btnLanguage.setOnClickListener {
            showLanguageDialog()
        }
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

    private fun showLanguageDialog() {
        val labels = arrayOf(
            getString(R.string.language_system),
            getString(R.string.language_english),
            getString(R.string.language_spanish),
            getString(R.string.language_russian)
        )

        val values = arrayOf("system", "en", "es", "ru")

        val current = SessionStore(this).appLanguage ?: "system"
        val checked = values.indexOf(current).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val lang = values[which]
                SessionStore(this).appLanguage = lang

                val locales = if (lang == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(lang)
                }

                AppCompatDelegate.setApplicationLocales(locales)

                dialog.dismiss()
                recreate()
            }
            .show()
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
            tvEnabled.setText(R.string.enabled)
        } else {
            tvEnabled.setText(R.string.disabled)
        }
    }

    private fun openFullscreenPermissionSettings() {
        if (Build.VERSION.SDK_INT < 34) {
            Toast.makeText(
                this,
                getText(R.string.system_setting_only_newer_android),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                getText(R.string.could_not_open_system_settings),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}