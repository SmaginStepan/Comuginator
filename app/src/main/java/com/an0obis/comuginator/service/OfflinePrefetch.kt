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
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.storage.OfflineCache
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Builds the complete offline snapshot for the active family: the whole
 * library (items, sets, set contents), every level of the child-home tree, and
 * the images they show. Screens only cache what the user happened to visit;
 * this makes offline mode work for everything else too.
 */
object OfflinePrefetcher {

    private const val TAG = "OfflinePrefetch"
    private const val MAX_TREE_DEPTH = 8

    /** Returns true when everything was fetched; false if any part failed. */
    suspend fun run(context: Context): Boolean {
        val app = context.applicationContext
        val session = SessionStore(app)
        val auth = session.authHeader() ?: return true
        val familyId = session.familyId
        val cache = OfflineCache(app)
        val imageUrls = LinkedHashSet<String>()
        var ok = true

        try {
            val items = ApiClient.api.getLibraryItems(auth, source = null).items
            cache.saveLibraryItemsMerged(familyId, items)
            items.forEach { addImage(imageUrls, it.imageUrl) }

            val sets = ApiClient.api.getLibrarySets(auth).sets
            cache.saveLibrarySets(familyId, sets)
            for (set in sets) {
                addImage(imageUrls, set.cover?.imageUrl)
                try {
                    val details = ApiClient.api.getLibrarySet(auth, set.id).set
                    cache.saveSetDetails(familyId, details)
                    details.items.forEach { addImage(imageUrls, it.imageUrl) }
                } catch (e: Exception) {
                    Log.w(TAG, "set ${set.id} failed", e)
                    ok = false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "library failed", e)
            ok = false
        }

        // Child-home tree: MENU nodes are folders, so recurse into each one.
        val visited = HashSet<String?>()
        suspend fun visit(parentId: String?, depth: Int) {
            if (depth > MAX_TREE_DEPTH || !visited.add(parentId)) return
            val nodes = try {
                ApiClient.api.getChildHomeNodes(auth, parentId).items
            } catch (e: Exception) {
                Log.w(TAG, "child home level $parentId failed", e)
                ok = false
                return
            }
            // Keep show/hide changes made offline that haven't synced yet.
            cache.saveChildHomeNodes(familyId, parentId, cache.applyPendingVisibility(nodes))
            nodes.forEach { addImage(imageUrls, it.item?.imageUrl) }
            nodes.filter { it.type == "MENU" }.forEach { visit(it.id, depth + 1) }
        }
        visit(null, 0)

        warmImages(app, auth, imageUrls)

        Log.d(TAG, "done ok=$ok family=$familyId images=${imageUrls.size}")
        if (ok) SettingsStore(app).offlinePrefetchedAt = System.currentTimeMillis()
        return ok
    }

    private fun addImage(target: MutableSet<String>, url: String?) {
        if (!url.isNullOrBlank() && url.startsWith("http")) target.add(url)
    }

    /**
     * Loads every image once so Coil's disk cache holds it. The (tiny) decoded
     * bitmap is discarded; what matters is the cached network response.
     */
    private suspend fun warmImages(context: Context, auth: String, urls: Collection<String>) {
        val loader = Coil.imageLoader(context)
        urls.chunked(4).forEach { chunk ->
            coroutineScope {
                chunk.map { url ->
                    async {
                        try {
                            val request = ImageRequest.Builder(context)
                                .data(url)
                                .apply {
                                    // Never send our token to third-party hosts (ARASAAC).
                                    if (ApiClient.isProtectedImageUrl(url)) {
                                        addHeader("Authorization", auth)
                                    }
                                }
                                .size(96)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .build()
                            loader.execute(request)
                        } catch (e: Exception) {
                            Log.w(TAG, "image warm failed: $url", e)
                        }
                    }
                }.awaitAll()
            }
        }
    }
}

class OfflinePrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (SettingsStore(applicationContext).offlineMode) return Result.success()
        val ok = OfflinePrefetcher.run(applicationContext)
        return if (ok || runAttemptCount >= 2) Result.success() else Result.retry()
    }
}

object OfflinePrefetchScheduler {
    private const val WORK_NAME = "offline-prefetch"
    private const val MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L

    /**
     * Refreshes the offline snapshot when it is stale (or [force]d, e.g. after
     * reconnecting or switching family). Never restarts a run already in progress.
     */
    fun enqueueIfDue(context: Context, force: Boolean = false) {
        val settings = SettingsStore(context)
        if (settings.offlineMode) return
        if (!force && System.currentTimeMillis() - settings.offlinePrefetchedAt < MIN_INTERVAL_MS) return

        val request = OneTimeWorkRequestBuilder<OfflinePrefetchWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
