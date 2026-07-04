package com.an0obis.comuginator.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

const val ACTION_BACK_ONLINE = "com.an0obis.comuginator.BACK_ONLINE"

/**
 * Child devices never see the connection-trouble screen: on a persistent
 * connectivity failure they switch to offline mode automatically, then this
 * poller pings the server once a minute and leaves offline mode by itself as
 * soon as a request succeeds ([ACTION_BACK_ONLINE] tells the UI to reload).
 */
object OfflineAutoRecovery {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Enter offline mode automatically and start watching for recovery. */
    fun engage(context: Context) {
        SettingsStore(context).offlineMode = true
        startPolling(context.applicationContext)
    }

    /** Idempotent; also used at app start when a child device is still offline. */
    fun startPolling(context: Context) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext

        job = scope.launch {
            while (true) {
                delay(60.seconds)

                val settings = SettingsStore(appContext)
                if (!settings.offlineMode) break
                val auth = SessionStore(appContext).authHeader() ?: break

                try {
                    ApiClient.api.getMyFamilies(auth)
                    settings.offlineMode = false
                    OfflineSyncScheduler.enqueue(appContext)
                    appContext.sendBroadcast(
                        Intent(ACTION_BACK_ONLINE).setPackage(appContext.packageName)
                    )
                    Log.d("OfflineAutoRecovery", "connection restored, back online")
                    break
                } catch (_: Exception) {
                    // still unreachable — keep polling
                }
            }
        }
    }
}
