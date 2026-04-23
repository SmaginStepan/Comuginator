package com.an0obis.comuginator.service

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.HeartbeatRequest
import com.an0obis.comuginator.storage.SessionStore
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TelemetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

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
                    reportedAt = nowIsoString(),
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

    private fun nowIsoString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
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