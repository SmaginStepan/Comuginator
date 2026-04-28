package com.an0obis.comuginator.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.FcmTokenRequest
import com.an0obis.comuginator.storage.FcmTokenStore
import com.an0obis.comuginator.storage.SessionStore

class FcmTokenSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val sessionStore = SessionStore(applicationContext)

            val fcmTokenStore = FcmTokenStore(applicationContext)
            val pendingFcmToken = fcmTokenStore.pendingToken ?: return Result.success()

            ApiClient.api.updateFcmToken(
                auth = sessionStore.authHeader()?: return Result.failure(),
                body = FcmTokenRequest(fcmToken = pendingFcmToken)
            )

            fcmTokenStore.clearPendingToken()
            Result.success()
        } catch (e: Exception) {
            Log.e("FcmTokenSyncWorker", "doWork", e)
            Result.retry()
        }
    }
}