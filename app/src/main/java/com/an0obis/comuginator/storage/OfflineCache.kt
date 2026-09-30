package com.an0obis.comuginator.storage

import android.content.Context
import com.an0obis.comuginator.api.AacCardDto
import com.an0obis.comuginator.api.AacMessageListItemDto
import com.an0obis.comuginator.api.ChildHomeNodeDto
import com.an0obis.comuginator.api.FamilyMeResponse
import com.an0obis.comuginator.api.LibrarySetDetailsDto
import com.an0obis.comuginator.api.LibrarySetDto
import com.an0obis.comuginator.api.ScheduleItemDto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type

/** A photo added while offline, waiting to be uploaded to the library. */
data class PendingPhoto(
    val id: String,
    val filePath: String,
    val label: String,
    val createdAt: Long,
    val mimeType: String? = null
)

/**
 * A message to self composed offline; the raw request JSON is replayed later.
 * [serverMessageId] is set once the message exists on the server, so a retry
 * of a failed follow-up step (uploading the local reply) never creates it twice.
 */
data class PendingSelfMessage(
    val id: String,
    val requestJson: String,
    val createdAt: Long,
    val serverMessageId: String? = null
)

/** A show/hide change to a child-home node made offline; sent later. */
data class PendingVisibilityChange(
    val nodeId: String,
    val isVisible: Boolean,
    val createdAt: Long
)

/** A child-home node tap made offline; the parent request is sent later. */
data class PendingNodeAction(
    val id: String,
    val nodeId: String,
    val createdAt: Long
)

/**
 * File-based JSON snapshots of server data so the app can work without a
 * connection. Snapshots are written on every successful load ("write-through")
 * and read back when the network is unavailable or offline mode is forced.
 * Keys are scoped by familyId so multi-family devices don't mix data.
 */
class OfflineCache(context: Context) {

    private val gson = Gson()
    private val dir = File(context.filesDir, "offline").apply { mkdirs() }

    /** Where offline-added photos live until they are uploaded. */
    val photosDir = File(context.filesDir, "offline_photos").apply { mkdirs() }

    private fun file(key: String) = File(dir, "$key.json")

    private fun <T> save(key: String, value: T) {
        try {
            file(key).writeText(gson.toJson(value))
        } catch (_: Exception) {
            // cache write failures must never break the caller
        }
    }

    private fun <T> load(key: String, type: Type): T? = try {
        val f = file(key)
        if (f.exists()) gson.fromJson(f.readText(), type) else null
    } catch (_: Exception) {
        null
    }

    // ── Snapshots ───────────────────────────────────────────────────────────

    fun saveLibrarySets(familyId: String?, sets: List<LibrarySetDto>) =
        save("sets_$familyId", sets)

    fun loadLibrarySets(familyId: String?): List<LibrarySetDto>? =
        load("sets_$familyId", object : TypeToken<List<LibrarySetDto>>() {}.type)

    fun saveSetDetails(familyId: String?, set: LibrarySetDetailsDto) =
        save("set_${familyId}_${set.id}", set)

    fun loadSetDetails(familyId: String?, setId: String): LibrarySetDetailsDto? =
        load("set_${familyId}_$setId", LibrarySetDetailsDto::class.java)

    fun saveLibraryItems(familyId: String?, items: List<AacCardDto>) =
        save("items_$familyId", items)

    fun loadLibraryItems(familyId: String?): List<AacCardDto>? =
        load("items_$familyId", object : TypeToken<List<AacCardDto>>() {}.type)

    /** Photos added offline, shaped as library cards (file:// image, "local_" id). */
    fun pendingPhotoCards(): List<AacCardDto> =
        getPendingPhotos().map {
            AacCardDto(
                id = it.id,
                label = it.label,
                imageUrl = android.net.Uri.fromFile(File(it.filePath)).toString(),
                source = "FAMILY_PHOTO"
            )
        }

    /**
     * Stores a fresh server library snapshot while keeping photos that were
     * added offline and haven't been uploaded yet at the top of the list.
     */
    fun saveLibraryItemsMerged(familyId: String?, serverItems: List<AacCardDto>) {
        val serverIds = serverItems.map { it.id }.toSet()
        val local = pendingPhotoCards().filter { it.id !in serverIds }
        saveLibraryItems(familyId, local + serverItems)
    }

    /** After an offline photo is uploaded, swap its local card for the real one. */
    fun replaceLibraryItem(familyId: String?, localId: String, real: AacCardDto) {
        val items = loadLibraryItems(familyId) ?: return
        saveLibraryItems(familyId, items.map { if (it.id == localId) real else it })
    }

    fun saveChildHomeNodes(familyId: String?, parentId: String?, nodes: List<ChildHomeNodeDto>) =
        save("childhome_${familyId}_${parentId ?: "root"}", nodes)

    fun loadChildHomeNodes(familyId: String?, parentId: String?): List<ChildHomeNodeDto>? =
        load(
            "childhome_${familyId}_${parentId ?: "root"}",
            object : TypeToken<List<ChildHomeNodeDto>>() {}.type
        )

    fun saveScheduleItems(familyId: String?, items: List<ScheduleItemDto>) =
        save("schedule_$familyId", items)

    fun loadScheduleItems(familyId: String?): List<ScheduleItemDto>? =
        load("schedule_$familyId", object : TypeToken<List<ScheduleItemDto>>() {}.type)

    fun saveMessages(familyId: String?, messages: List<AacMessageListItemDto>) =
        save("messages_$familyId", messages)

    fun findMessage(familyId: String?, id: String): AacMessageListItemDto? =
        loadMessages(familyId)?.firstOrNull { it.id == id }

    fun removeMessage(familyId: String?, id: String) {
        val messages = loadMessages(familyId) ?: return
        saveMessages(familyId, messages.filterNot { it.id == id })
    }

    fun loadMessages(familyId: String?): List<AacMessageListItemDto>? =
        load("messages_$familyId", object : TypeToken<List<AacMessageListItemDto>>() {}.type)

    fun saveFamily(familyId: String?, response: FamilyMeResponse) =
        save("family_$familyId", response)

    fun loadFamily(familyId: String?): FamilyMeResponse? =
        load("family_$familyId", FamilyMeResponse::class.java)

    // ── Pending photo uploads ───────────────────────────────────────────────

    fun getPendingPhotos(): List<PendingPhoto> =
        load("pending_photos", object : TypeToken<List<PendingPhoto>>() {}.type) ?: emptyList()

    fun addPendingPhoto(photo: PendingPhoto) =
        save("pending_photos", getPendingPhotos() + photo)

    fun removePendingPhoto(id: String) =
        save("pending_photos", getPendingPhotos().filterNot { it.id == id })

    /**
     * Local photo id → the real library card it became after upload. Persisted
     * because a queued message may only be replayed on a later sync run than
     * the one that uploaded its photo.
     */
    fun getPhotoIdMap(): Map<String, AacCardDto> =
        load("photo_id_map", object : TypeToken<Map<String, AacCardDto>>() {}.type) ?: emptyMap()

    fun putPhotoId(localId: String, real: AacCardDto) =
        save("photo_id_map", getPhotoIdMap() + (localId to real))

    // ── Pending self-messages ───────────────────────────────────────────────

    fun getPendingSelfMessages(): List<PendingSelfMessage> =
        load("pending_self_messages", object : TypeToken<List<PendingSelfMessage>>() {}.type)
            ?: emptyList()

    fun addPendingSelfMessage(message: PendingSelfMessage) =
        save("pending_self_messages", getPendingSelfMessages() + message)

    fun updatePendingSelfMessage(updated: PendingSelfMessage) =
        save(
            "pending_self_messages",
            getPendingSelfMessages().map { if (it.id == updated.id) updated else it }
        )

    fun removePendingSelfMessage(id: String) =
        save("pending_self_messages", getPendingSelfMessages().filterNot { it.id == id })

    // ── Pending child-home visibility changes ──────────────────────────────

    fun getPendingVisibilityChanges(): List<PendingVisibilityChange> =
        load("pending_visibility", object : TypeToken<List<PendingVisibilityChange>>() {}.type)
            ?: emptyList()

    /** Only the latest change per node matters, so a new one replaces the old. */
    fun queueVisibilityChange(change: PendingVisibilityChange) =
        save(
            "pending_visibility",
            getPendingVisibilityChanges().filterNot { it.nodeId == change.nodeId } + change
        )

    /**
     * Removes a change once it was sent. Matching on [createdAt] keeps a newer
     * toggle of the same node (made while this one was being sent) queued.
     */
    fun removePendingVisibilityChange(nodeId: String, createdAt: Long) =
        save(
            "pending_visibility",
            getPendingVisibilityChanges().filterNot {
                it.nodeId == nodeId && it.createdAt == createdAt
            }
        )

    /**
     * Overlays not-yet-synced visibility changes on nodes freshly read from the
     * server (or cache), so a reload never flashes the old state back.
     */
    fun applyPendingVisibility(nodes: List<ChildHomeNodeDto>): List<ChildHomeNodeDto> {
        val pending = getPendingVisibilityChanges().associate { it.nodeId to it.isVisible }
        if (pending.isEmpty()) return nodes
        return nodes.map { node -> pending[node.id]?.let { node.copy(isVisible = it) } ?: node }
    }

    fun setCachedNodeVisibility(familyId: String?, parentId: String?, nodeId: String, visible: Boolean) {
        val nodes = loadChildHomeNodes(familyId, parentId) ?: return
        saveChildHomeNodes(
            familyId,
            parentId,
            nodes.map { if (it.id == nodeId) it.copy(isVisible = visible) else it }
        )
    }

    // ── Pending child-home action requests ─────────────────────────────────

    fun getPendingNodeActions(): List<PendingNodeAction> =
        load("pending_node_actions", object : TypeToken<List<PendingNodeAction>>() {}.type)
            ?: emptyList()

    fun addPendingNodeAction(action: PendingNodeAction) =
        save("pending_node_actions", getPendingNodeActions() + action)

    fun removePendingNodeAction(id: String) =
        save("pending_node_actions", getPendingNodeActions().filterNot { it.id == id })
}
