package com.example.comuginator.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.comuginator.api.ApiClient
import com.example.comuginator.api.FcmTokenRequest
import com.example.comuginator.storage.FcmTokenStore
import com.example.comuginator.storage.SessionStore

class FcmTokenSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val sessionStore = SessionStore(applicationContext)
            val token = sessionStore.token ?: return Result.success()

            val fcmTokenStore = FcmTokenStore(applicationContext)
            val pendingFcmToken = fcmTokenStore.pendingToken ?: return Result.success()

            ApiClient.api.updateFcmToken(
                auth = "Bearer $token",
                body = FcmTokenRequest(fcmToken = pendingFcmToken)
            )

            fcmTokenStore.clearPendingToken()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}