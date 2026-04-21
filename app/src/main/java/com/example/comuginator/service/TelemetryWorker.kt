package com.example.comuginator.service

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.HeartbeatRequest
import com.example.comuginator.storage.SessionStore
import android.util.Log

class TelemetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun doWork(): Result {
        return try {
            val reason = inputData.getString("reason") ?: "periodic"
            Log.d("TelemetryWorker", "sending heartbeat, reason=$reason")

            val sessionStore = SessionStore(applicationContext)
            val token = sessionStore.token ?: return Result.success()

            val snapshot = BatteryTelemetryReader.read(applicationContext)

            ApiClient.api.heartbeat(
                auth = "Bearer $token",
                HeartbeatRequest(
                    batteryPercent = snapshot.batteryPercent,
                    volumePercent = null,
                    isCharging = snapshot.isCharging,
                    reportedAt = java.time.Instant.now().toString(),
                    platform = "android",
                    model = Build.MODEL ?: "unknown",
                    osVersion = Build.VERSION.RELEASE ?: "unknown",
                    appVersion = getAppVersion()
                )
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun getAppVersion(): String {
        return try {
            val pInfo = applicationContext.packageManager
                .getPackageInfo(applicationContext.packageName, 0)

            pInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}