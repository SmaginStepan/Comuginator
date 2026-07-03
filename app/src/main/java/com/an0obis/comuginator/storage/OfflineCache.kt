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
    val createdAt: Long
)

/** A message to self composed offline; the raw request JSON is replayed later. */
data class PendingSelfMessage(
    val id: String,
    val requestJson: String,
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

    // ── Pending self-messages ───────────────────────────────────────────────

    fun getPendingSelfMessages(): List<PendingSelfMessage> =
        load("pending_self_messages", object : TypeToken<List<PendingSelfMessage>>() {}.type)
            ?: emptyList()

    fun addPendingSelfMessage(message: PendingSelfMessage) =
        save("pending_self_messages", getPendingSelfMessages() + message)

    fun removePendingSelfMessage(id: String) =
        save("pending_self_messages", getPendingSelfMessages().filterNot { it.id == id })
}
