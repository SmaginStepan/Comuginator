package com.an0obis.comuginator.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.storage.OfflineCache
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * Uploads everything that was created while offline: queued photos to the
 * library and queued self-messages. Runs with a connected-network constraint;
 * retries until the queues are empty.
 */
class OfflineSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (SettingsStore(applicationContext).offlineMode) {
            // User forced offline — don't sync until they turn it off.
            return Result.success()
        }

        val auth = SessionStore(applicationContext).authHeader() ?: return Result.success()
        val cache = OfflineCache(applicationContext)
        var allOk = true

        for (photo in cache.getPendingPhotos()) {
            try {
                val file = File(photo.filePath)
                if (!file.exists()) {
                    cache.removePendingPhoto(photo.id)
                    continue
                }
                val part = MultipartBody.Part.createFormData(
                    "file",
                    file.name,
                    file.asRequestBody("image/jpeg".toMediaType())
                )
                ApiClient.api.uploadFamilyPhoto(
                    auth = auth,
                    file = part,
                    label = photo.label.toRequestBody("text/plain".toMediaType())
                )
                cache.removePendingPhoto(photo.id)
                file.delete()
            } catch (e: Exception) {
                Log.w("OfflineSyncWorker", "photo upload failed", e)
                allOk = false
            }
        }

        for (message in cache.getPendingSelfMessages()) {
            try {
                ApiClient.sendAacMessageRaw(auth, message.requestJson)
                cache.removePendingSelfMessage(message.id)
            } catch (e: Exception) {
                Log.w("OfflineSyncWorker", "self-message upload failed", e)
                allOk = false
            }
        }

        return if (allOk) Result.success() else Result.retry()
    }
}

object OfflineSyncScheduler {
    private const val WORK_NAME = "offline-sync"

    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
