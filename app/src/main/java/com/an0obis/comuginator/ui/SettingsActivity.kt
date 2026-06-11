package com.an0obis.comuginator.ui

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.an0obis.comuginator.R
import com.an0obis.comuginator.service.NotificationPolicy
import com.an0obis.comuginator.storage.NotificationRule
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import com.an0obis.comuginator.ui.base.BaseActivity
import com.an0obis.comuginator.util.TimeFormat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.LocaleListCompat
import java.util.Calendar
import java.util.UUID

class SettingsActivity : BaseActivity() {

    private lateinit var settingsStore: SettingsStore
    private lateinit var tvEnabled: TextView
    private lateinit var swOpenIncomingFullscreen: SwitchCompat
    private lateinit var btnFullscreenPermission: Button
    private lateinit var btnLanguage: Button
    private lateinit var swNotifications: SwitchCompat
    private lateinit var llNotificationRules: LinearLayout
    private lateinit var btnAddRule: Button
    private lateinit var tvRulesCounter: TextView
    private lateinit var btnNotificationSound: Button

    private val soundPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = IntentCompat.getParcelableExtra(
                    result.data ?: return@registerForActivityResult,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
                // null pick = "Silent" in the system picker
                settingsStore.setNotificationSound(uri?.toString() ?: "")
                updateSoundButton()
            }
        }

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

        swNotifications = findViewById(R.id.swNotifications)
        llNotificationRules = findViewById(R.id.llNotificationRules)
        btnAddRule = findViewById(R.id.btnAddRule)
        tvRulesCounter = findViewById(R.id.tvRulesCounter)
        btnAddRule.setOnClickListener { showAddRuleDialog() }

        btnNotificationSound = findViewById(R.id.btnNotificationSound)
        btnNotificationSound.setOnClickListener { openSoundPicker() }
        updateSoundButton()

        updateEnabledState()
        renderNotificationState()
    }

    override fun onResume() {
        super.onResume()
        updateEnabledState()
        renderNotificationState()
    }

    // ── Notification sound ────────────────────────────────────────────────────

    private fun openSoundPicker() {
        val currentSound = settingsStore.notificationSound
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.notification_sound))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                Settings.System.DEFAULT_NOTIFICATION_URI
            )
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                when (currentSound) {
                    null -> Settings.System.DEFAULT_NOTIFICATION_URI
                    "" -> null
                    else -> currentSound.toUri()
                }
            )
        }
        try {
            soundPickerLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getText(R.string.could_not_open_system_settings), Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun updateSoundButton() {
        val soundName = when (val sound = settingsStore.notificationSound) {
            null -> getString(R.string.sound_default)
            "" -> getString(R.string.sound_silent)
            else -> try {
                RingtoneManager.getRingtone(this, sound.toUri())?.getTitle(this)
                    ?: getString(R.string.sound_default)
            } catch (_: Exception) {
                getString(R.string.sound_default)
            }
        }
        btnNotificationSound.text =
            getString(R.string.notification_sound_with_value, soundName)
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    private fun renderNotificationState() {
        swNotifications.setOnCheckedChangeListener(null)
        swNotifications.isChecked = NotificationPolicy.isEnabled(this)
        swNotifications.setOnCheckedChangeListener { _, isChecked ->
            settingsStore.setNotificationsEnabledManually(isChecked)
        }
        renderNotificationRules()
    }

    private fun renderNotificationRules() {
        llNotificationRules.removeAllViews()
        val rules = settingsStore.getNotificationRules()
        rules.forEach { rule ->
            val row = layoutInflater.inflate(
                R.layout.item_notification_rule, llNotificationRules, false
            )
            row.findViewById<TextView>(R.id.tvRuleText).text = describeRule(rule)
            row.findViewById<Button>(R.id.btnDeleteRule).setOnClickListener {
                settingsStore.removeNotificationRule(rule.id)
                renderNotificationState()
            }
            llNotificationRules.addView(row)
        }
        tvRulesCounter.text =
            resources.getQuantityString(R.plurals.items_count, rules.size, rules.size)
    }

    private fun describeRule(rule: NotificationRule): String {
        val action = getString(if (rule.enable) R.string.enabled else R.string.disabled)
        val whenPart = rule.date?.let { TimeFormat.date(it) }
            ?: rule.weekdays.joinToString(", ") { weekdayName(it) }
        return "$action · ${rule.time} · $whenPart"
    }

    private fun weekdayName(day: Int): String = when (day) {
        1 -> getString(R.string.weekday_mon)
        2 -> getString(R.string.weekday_tue)
        3 -> getString(R.string.weekday_wed)
        4 -> getString(R.string.weekday_thu)
        5 -> getString(R.string.weekday_fri)
        6 -> getString(R.string.weekday_sat)
        7 -> getString(R.string.weekday_sun)
        else -> "?"
    }

    private fun showAddRuleDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_notification_rule, null)
        val rbEnable = view.findViewById<RadioButton>(R.id.rbRuleEnable)
        val rbDate = view.findViewById<RadioButton>(R.id.rbRuleDate)
        val rgMode = view.findViewById<RadioGroup>(R.id.rgRuleMode)
        val btnPickDate = view.findViewById<Button>(R.id.btnRulePickDate)
        val llWeekdays = view.findViewById<LinearLayout>(R.id.llRuleWeekdays)
        val btnPickTime = view.findViewById<Button>(R.id.btnRulePickTime)
        val cbDays = listOf<CheckBox>(
            view.findViewById(R.id.cbRuleMon), view.findViewById(R.id.cbRuleTue),
            view.findViewById(R.id.cbRuleWed), view.findViewById(R.id.cbRuleThu),
            view.findViewById(R.id.cbRuleFri), view.findViewById(R.id.cbRuleSat),
            view.findViewById(R.id.cbRuleSun)
        )

        val cal = Calendar.getInstance()
        var selectedDate = "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        var selectedTime = "%02d:%02d".format(
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)
        )

        btnPickDate.text = TimeFormat.date(selectedDate)
        btnPickTime.text = getString(R.string.time_with_value, selectedTime)

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            val isDate = checkedId == R.id.rbRuleDate
            btnPickDate.visibility = if (isDate) View.VISIBLE else View.GONE
            llWeekdays.visibility = if (isDate) View.GONE else View.VISIBLE
        }

        btnPickDate.setOnClickListener {
            val parts = selectedDate.split("-")
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
                    btnPickDate.text = TimeFormat.date(selectedDate)
                },
                parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR),
                (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1,
                parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnPickTime.setOnClickListener {
            val parts = selectedTime.split(":")
            TimePickerDialog(
                this,
                { _, h, m ->
                    selectedTime = "%02d:%02d".format(h, m)
                    btnPickTime.text = getString(R.string.time_with_value, selectedTime)
                },
                parts.getOrNull(0)?.toIntOrNull() ?: 0,
                parts.getOrNull(1)?.toIntOrNull() ?: 0,
                true
            ).show()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_rule)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val isDate = rbDate.isChecked
                val weekdays = if (isDate) emptyList() else
                    cbDays.mapIndexedNotNull { i, cb -> if (cb.isChecked) i + 1 else null }

                if (!isDate && weekdays.isEmpty()) {
                    Toast.makeText(this, getString(R.string.day_of_week), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                settingsStore.addNotificationRule(
                    NotificationRule(
                        id = UUID.randomUUID().toString(),
                        enable = rbEnable.isChecked,
                        time = selectedTime,
                        weekdays = weekdays,
                        date = if (isDate) selectedDate else null
                    )
                )
                renderNotificationState()
            }
            .show()
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