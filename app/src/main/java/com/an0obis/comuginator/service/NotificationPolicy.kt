package com.an0obis.comuginator.service

import android.content.Context
import com.an0obis.comuginator.storage.NotificationRule
import com.an0obis.comuginator.storage.SettingsStore
import java.util.Calendar

/**
 * Decides whether notifications are currently enabled on this device.
 *
 * Evaluated lazily ("last event wins") instead of flipping a flag with
 * alarms: the most recent of the manual toggle and every rule's latest
 * firing time determines the state. No alarms or boot receivers needed,
 * and a transition can never be missed.
 */
object NotificationPolicy {

    fun isEnabled(context: Context): Boolean {
        val store = SettingsStore(context)
        val now = Calendar.getInstance()

        var winnerAt = store.notificationsToggledAt
        var winnerEnabled = store.notificationsEnabledManually

        for (rule in store.getNotificationRules()) {
            val firedAt = lastFiredAt(rule, now) ?: continue
            if (firedAt > winnerAt) {
                winnerAt = firedAt
                winnerEnabled = rule.enable
            }
        }
        return winnerEnabled
    }

    /** The most recent moment this rule fired, or null if it never has. */
    private fun lastFiredAt(rule: NotificationRule, now: Calendar): Long? {
        val parts = rule.time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null

        if (rule.date != null) {
            val dateParts = rule.date.take(10).split("-")
            if (dateParts.size != 3) return null
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, dateParts[0].toIntOrNull() ?: return null)
                set(Calendar.MONTH, (dateParts[1].toIntOrNull() ?: return null) - 1)
                set(Calendar.DAY_OF_MONTH, dateParts[2].toIntOrNull() ?: return null)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis.takeIf { it <= now.timeInMillis }
        }

        if (rule.weekdays.isEmpty()) return null

        // Weekly rule: walk back up to a week to find the latest firing.
        for (daysBack in 0..7) {
            val cal = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -daysBack)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis > now.timeInMillis) continue
            // Calendar: Sun=1..Sat=7 → app convention: Mon=1..Sun=7
            val weekdayIso = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
            if (weekdayIso in rule.weekdays) return cal.timeInMillis
        }
        return null
    }
}
