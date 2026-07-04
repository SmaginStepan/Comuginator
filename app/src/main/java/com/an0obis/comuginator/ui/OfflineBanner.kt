package com.an0obis.comuginator.ui

import android.app.Activity
import android.view.View
import android.widget.Button
import com.an0obis.comuginator.R
import com.an0obis.comuginator.service.OfflineSyncScheduler
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore

/**
 * The "Offline mode · Reconnect" strip at the bottom of the main screens
 * (include layout/view_offline_banner in the activity layout). Reconnect
 * turns offline mode off, kicks the pending-uploads sync, and re-runs the
 * screen's load — which goes through the usual retry + connection-trouble
 * flow if the server is still unreachable.
 */
object OfflineBanner {

    fun setup(activity: Activity, onReconnect: () -> Unit) {
        val banner = activity.findViewById<View>(R.id.offlineBanner) ?: return
        banner.findViewById<Button>(R.id.btnReconnect).setOnClickListener {
            SettingsStore(activity).offlineMode = false
            OfflineSyncScheduler.enqueue(activity.applicationContext)
            refresh(activity)
            onReconnect()
        }
        refresh(activity)
    }

    fun refresh(activity: Activity) {
        val banner = activity.findViewById<View>(R.id.offlineBanner) ?: return
        banner.visibility =
            if (SettingsStore(activity).offlineMode) View.VISIBLE else View.GONE

        // Child devices recover automatically (OfflineAutoRecovery): the
        // banner is informational only, no manual reconnect.
        banner.findViewById<Button>(R.id.btnReconnect).visibility =
            if (SessionStore(activity).role == "CHILD") View.GONE else View.VISIBLE
    }
}
