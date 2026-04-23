package com.an0obis.comuginator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.CommandDto
import com.an0obis.comuginator.api.HeartbeatRequest
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import retrofit2.HttpException
import android.util.Log

class ConnectionService : Service() {

    companion object {
        private const val CHANNEL_ID = "comuginator_connection"
        private const val CHANNEL_NAME = "Comuginator connection"
        private const val NOTIFICATION_ID = 1001
        private const val HEARTBEAT_INTERVAL_MS = 30_000L

        fun start(context: Context): Boolean {
            val intent = Intent(context, ConnectionService::class.java)
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                Log.w("ConnectionService", "Failed to start service", e)
                false
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sessionStore: SessionStore

    @Volatile
    private var loopStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = sessionStore.token
        if (token.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Connection active")
            )
        } catch (e: Exception) {
            Log.w("ConnectionService", "Failed to enter foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!loopStarted) {
            loopStarted = true
            startHeartbeatLoop()
        }

        return START_STICKY
    }
    private fun getCurrentVolumePercent(): Int {
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val stream = android.media.AudioManager.STREAM_MUSIC
        val current = audioManager.getStreamVolume(stream)
        val max = audioManager.getStreamMaxVolume(stream)
        if (max <= 0) return 0
        return (current * 100) / max
    }

    private fun handleSetVolumeCommand(cmd: CommandDto) {
        val raw = cmd.payload["volumePercent"] ?: return
        val volumePercent = when (raw) {
            is Double -> raw.toInt()
            is Int -> raw
            else -> return
        }.coerceIn(0, 100)

        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val stream = android.media.AudioManager.STREAM_MUSIC
        val maxVolume = audioManager.getStreamMaxVolume(stream)
        val targetVolume = (maxVolume * volumePercent) / 100

        audioManager.setStreamVolume(stream, targetVolume, 0)
    }

    private suspend fun fetchAndProcessCommands() {
        val token = sessionStore.token ?: return

        val response = ApiClient.api.getPendingCommands("Bearer $token")

        for (cmd in response.items) {
            try {
                when (cmd.type) {
                    "set_volume" -> {
                        handleSetVolumeCommand(cmd)
                        ApiClient.api.ackCommand("Bearer $token", cmd.id)
                    }
                    "aac_message_available",
                    "aac_reply_available" -> {
                        // не трогаем, это для UI
                    }
                }
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return
                // пока просто не ack-аем, тогда команда останется pending
            }
        }
    }

    private fun handleUnauthorized(e: Exception): Boolean {
        if (e is HttpException && e.code() == 401) {
            sessionStore.clear()
            updateNotification("Session expired")
            stopSelf()
            return true
        }
        return false
    }

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    sendHeartbeatOnce()
                    fetchAndProcessCommands()
                    updateNotification("Last heartbeat OK: ${nowLocalTime()}")
                } catch (e: Exception) {
                    if (handleUnauthorized(e)) {
                        break
                    }
                    updateNotification("Heartbeat error: ${e.message ?: "unknown"}")
                }

                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }
    private suspend fun sendHeartbeatOnce() {
        val token = sessionStore.token ?: return
        val battery = DeviceInfoProvider.getBatterySnapshot(this)
        val volumePercent = getCurrentVolumePercent()

        ApiClient.api.heartbeat(
            auth = "Bearer $token",
            body = HeartbeatRequest(
                batteryPercent = battery.batteryPercent,
                volumePercent = volumePercent,
                isCharging = battery.isCharging,
                reportedAt = DeviceInfoProvider.getReportedAtIsoUtc(),
                platform = DeviceInfoProvider.getPlatform(),
                model = DeviceInfoProvider.getModel(),
                osVersion = DeviceInfoProvider.getOsVersion(),
                appVersion = DeviceInfoProvider.getAppVersion(this)
            )
        )
    }

    private fun buildNotification(text: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Comuginator")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Comuginator connection alive"
            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }

    private fun nowLocalTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }

    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    override fun onDestroy() {
        loopStarted = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}