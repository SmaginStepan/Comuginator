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
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.RawHttpException
import com.an0obis.comuginator.api.SendAacReplyRequest
import com.an0obis.comuginator.api.UpdateChildHomeNodeRequest
import com.an0obis.comuginator.storage.OfflineCache
import com.an0obis.comuginator.storage.PendingSelfMessage
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
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

    // Every trigger enqueues a fresh worker (REPLACE) which cancels the previous
    // one. The lock guarantees two passes never overlap, and the cancelled pass
    // only stops at a unit boundary (see NonCancellable below) — otherwise a
    // second pass could re-send something the first already delivered.
    override suspend fun doWork(): Result = syncMutex.withLock { runSync() }

    private suspend fun runSync(): Result {
        if (SettingsStore(applicationContext).offlineMode) {
            // User forced offline — don't sync until they turn it off.
            return Result.success()
        }

        val session = SessionStore(applicationContext)
        val auth = session.authHeader() ?: return Result.success()
        val familyId = session.familyId
        val cache = OfflineCache(applicationContext)
        var allOk = true

        // 1. Photos first: queued messages may reference them.
        for (photo in cache.getPendingPhotos()) {
            currentCoroutineContext().ensureActive()
            try {
                // The upload and recording that it happened must not be split.
                withContext(NonCancellable) {
                    val file = File(photo.filePath)
                    if (!file.exists()) {
                        cache.removePendingPhoto(photo.id)
                        return@withContext
                    }
                    val part = MultipartBody.Part.createFormData(
                        "file",
                        file.name,
                        file.asRequestBody((photo.mimeType ?: "image/jpeg").toMediaType())
                    )
                    val uploaded = ApiClient.api.uploadFamilyPhoto(
                        auth = auth,
                        file = part,
                        label = photo.label.toRequestBody("text/plain".toMediaType())
                    ).item

                    cache.putPhotoId(photo.id, uploaded)
                    cache.replaceLibraryItem(familyId, photo.id, uploaded)
                    cache.removePendingPhoto(photo.id)
                    file.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isPermanent(e)) {
                    Log.w("OfflineSyncWorker", "dropping rejected photo ${photo.id}", e)
                    cache.removePendingPhoto(photo.id)
                } else {
                    Log.w("OfflineSyncWorker", "photo upload failed", e)
                    allOk = false
                }
            }
        }

        // 2. Self-messages: create on the server once, then replay the reply
        //    that was given locally so the message doesn't reappear as unanswered.
        for (pending in cache.getPendingSelfMessages()) {
            currentCoroutineContext().ensureActive()
            try {
                var deferred = false

                withContext(NonCancellable) {
                    var current = pending

                    if (current.serverMessageId == null) {
                        val rewritten = rewriteLocalCards(current.requestJson, cache.getPhotoIdMap())
                        if (referencesLocalCards(rewritten)) {
                            // A photo it uses hasn't been uploaded yet — try again next run.
                            deferred = true
                            return@withContext
                        }
                        val messageId = withContext(Dispatchers.IO) {
                            ApiClient.sendAacMessageRaw(auth, rewritten)
                        }
                        current = current.copy(serverMessageId = messageId)
                        cache.updatePendingSelfMessage(current)
                    }

                    replayLocalReply(auth, cache, familyId, current)

                    cache.removePendingSelfMessage(pending.id)
                    cache.removeMessage(familyId, pending.id)
                }

                if (deferred) allOk = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isPermanent(e)) {
                    Log.w("OfflineSyncWorker", "dropping rejected self-message ${pending.id}", e)
                    cache.removePendingSelfMessage(pending.id)
                    cache.removeMessage(familyId, pending.id)
                } else {
                    Log.w("OfflineSyncWorker", "self-message upload failed", e)
                    allOk = false
                }
            }
        }

        // 3. Show/hide changes made in the child-home editor while offline.
        for (change in cache.getPendingVisibilityChanges()) {
            currentCoroutineContext().ensureActive()
            try {
                withContext(NonCancellable) {
                    ApiClient.api.updateChildHomeNode(
                        auth = auth,
                        nodeId = change.nodeId,
                        body = UpdateChildHomeNodeRequest(isVisible = change.isVisible)
                    )
                    cache.removePendingVisibilityChange(change.nodeId, change.createdAt)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isPermanent(e)) {
                    // e.g. the node was deleted meanwhile — retrying can't help.
                    Log.w("OfflineSyncWorker", "dropping rejected visibility change ${change.nodeId}", e)
                    cache.removePendingVisibilityChange(change.nodeId, change.createdAt)
                } else {
                    Log.w("OfflineSyncWorker", "visibility change failed", e)
                    allOk = false
                }
            }
        }

        // 4. Child-home taps made while offline.
        for (action in cache.getPendingNodeActions()) {
            currentCoroutineContext().ensureActive()
            try {
                withContext(NonCancellable) {
                    ApiClient.api.requestChildHomeAction(auth = auth, nodeId = action.nodeId)
                    cache.removePendingNodeAction(action.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is retrofit2.HttpException) {
                    // Server rejected it (e.g. node deleted meanwhile) —
                    // retrying won't help, drop it.
                    cache.removePendingNodeAction(action.id)
                } else {
                    Log.w("OfflineSyncWorker", "node action send failed", e)
                    allOk = false
                }
            }
        }

        return if (allOk) Result.success() else Result.retry()
    }

    private companion object {
        val syncMutex = Mutex()
    }

    /**
     * NORMAL messages take all reply cards in one call; SEQUENCE keeps only the
     * latest reply (each step replaces the previous), so only the last card is sent.
     */
    private suspend fun replayLocalReply(
        auth: String,
        cache: OfflineCache,
        familyId: String?,
        pending: PendingSelfMessage
    ) {
        val serverId = pending.serverMessageId ?: return
        val local = cache.findMessage(familyId, pending.id) ?: return
        val replyCards = local.reply?.reply.orEmpty()
        if (replyCards.isEmpty()) return

        val photoMap = cache.getPhotoIdMap()
        val cards = (if (local.mode == "SEQUENCE") listOf(replyCards.last()) else replyCards)
            .map { photoMap[it.id] ?: it }

        try {
            withContext(Dispatchers.IO) {
                ApiClient.replyToAacMessage(auth, serverId, SendAacReplyRequest(reply = cards))
            }
        } catch (e: Exception) {
            // e.g. 409 "already answered": the message itself is safely on the
            // server, so don't retry the reply forever.
            if (!isPermanent(e)) throw e
            Log.w("OfflineSyncWorker", "reply for $serverId rejected", e)
        }
    }

    /** Swaps cards that are offline-added photos for the real uploaded cards. */
    private fun rewriteLocalCards(json: String, photoMap: Map<String, AacCardDto>): String {
        if (photoMap.isEmpty()) return json
        val gson = Gson()
        val root = JsonParser.parseString(json).asJsonObject
        for (key in listOf("cards", "suggestedReplies")) {
            val array = root.getAsJsonArray(key) ?: continue
            for (i in 0 until array.size()) {
                val id = array[i].asJsonObject.get("id")?.asString ?: continue
                photoMap[id]?.let { array.set(i, gson.toJsonTree(it)) }
            }
        }
        return root.toString()
    }

    private fun referencesLocalCards(json: String): Boolean {
        val root = JsonParser.parseString(json).asJsonObject
        return listOf("cards", "suggestedReplies").any { key ->
            root.getAsJsonArray(key)?.any {
                it.asJsonObject.get("id")?.asString?.startsWith("local_") == true
            } == true
        }
    }

    /** Client errors won't succeed on retry (401/408/429 are transient). */
    private fun isPermanent(e: Exception): Boolean {
        val code = when (e) {
            is HttpException -> e.code()
            is RawHttpException -> e.code
            else -> return false
        }
        return code in 400..499 && code != 401 && code != 408 && code != 429
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
