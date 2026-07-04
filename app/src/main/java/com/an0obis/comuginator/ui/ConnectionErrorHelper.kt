package com.an0obis.comuginator.ui

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.an0obis.comuginator.service.OfflineAutoRecovery
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import java.io.IOException

/**
 * Routes persistent connectivity failures (after ApiClient's automatic
 * retries) to [ConnectionTroubleActivity]. Whatever the user picks there,
 * [onResolved] re-runs the failed load: "Try again" hits the network with a
 * fresh set of retries, "Enter offline mode" makes the reload read the cache.
 *
 * Must be created as a field (before onCreate finishes) because it registers
 * an activity-result launcher.
 */
class ConnectionErrorHelper(
    private val activity: ComponentActivity,
    private val onResolved: () -> Unit
) {
    private var showing = false

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        showing = false
        onResolved()
    }

    /** Shows the trouble screen (used by event-driven callers). */
    fun show() {
        if (showing) return
        if (SettingsStore(activity).offlineMode) return

        // Child devices don't get asked: they go offline automatically and a
        // background poller brings them back online when the server responds.
        if (SessionStore(activity).role == "CHILD") {
            OfflineAutoRecovery.engage(activity)
            onResolved()
            return
        }

        showing = true
        launcher.launch(Intent(activity, ConnectionTroubleActivity::class.java))
    }

    /** Returns true when the error was a connectivity problem and was routed. */
    fun handle(error: Throwable?): Boolean {
        if (error !is IOException) return false
        if (SettingsStore(activity).offlineMode) return false
        show()
        return true
    }
}
