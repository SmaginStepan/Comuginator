package com.an0obis.comuginator.service

import android.content.Context
import android.util.Log
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.storage.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Temporary parent elevation on a CHILD device.
 *
 * The elevation token is deliberately kept in memory only: if the process
 * dies, the device starts in child mode no matter what the server still
 * thinks (the server-side expiry is just a backstop). Elevation ends when the
 * app leaves the foreground (see the lifecycle callbacks in ComuginatorApp).
 */
object AdultMode {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var token: String? = null
        private set

    val active: Boolean
        get() = token != null

    fun begin(elevationToken: String) {
        token = elevationToken
    }

    /** Drops elevation locally and tells the server, best effort. */
    fun end(context: Context) {
        if (token == null) return
        token = null

        val auth = SessionStore(context).authHeader() ?: return
        scope.launch {
            try {
                ApiClient.api.endElevation(auth)
            } catch (e: Exception) {
                Log.w("AdultMode", "failed to end elevation on server", e)
                // The server-side expiry covers this case.
            }
        }
    }
}
