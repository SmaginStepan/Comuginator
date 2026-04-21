package com.example.comuginator.ui.base

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.comuginator.service.ConnectionService
import com.example.comuginator.service.TelemetryScheduler
import com.example.comuginator.storage.SessionStore
import com.example.comuginator.ui.MainActivity
import retrofit2.HttpException

open class BaseActivity: AppCompatActivity() {
    private lateinit var store: SessionStore
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = SessionStore(this)
    }

    protected fun requireToken(): String {
        return store.token ?: error("No token in SessionStore")
    }

    protected fun ensureInitialized() {
        if (initialized) return
        if (!ensureNotificationsPermission()) return

        TelemetryScheduler.ensurePeriodic(this)
        initialized = true

        val existingToken = store.token
        if (!existingToken.isNullOrBlank()) {
            ConnectionService.start(this)
            onInitialized()
            return
        }
    }

    open fun onInitialized() {
        // if session and token is ready
    }

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d("MainActivity", "POST_NOTIFICATIONS granted=$granted")

            if (granted) ensureInitialized()
            else openNotifSettings(this)
        }

    private fun ensureNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true

        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return false
        }
        return true
    }

    protected fun handleUnauthorized(e: Exception): Boolean {
        if (e is HttpException && e.code() == 401) {
            val store = SessionStore(this)
            store.clear()

            startActivity(Intent(this, MainActivity::class.java))
            finish()

            return true
        }
        return false
    }

    private fun openNotifSettings(ctx: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}