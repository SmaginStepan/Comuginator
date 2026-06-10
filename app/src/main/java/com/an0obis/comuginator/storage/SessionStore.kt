package com.an0obis.comuginator.storage
import androidx.core.content.edit

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class FamilyEntry(
    val familyId: String,
    val userId: String,
    val role: String,
    val name: String? = null
)

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("comuginator_session", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit { putString("token", value) }

    var familyId: String?
        get() = prefs.getString("familyId", null)
        set(value) = prefs.edit { putString("familyId", value) }

    var userId: String?
        get() = prefs.getString("userId", null)
        set(value) = prefs.edit { putString("userId", value) }

    var deviceId: String?
        get() = prefs.getString("deviceId", null)
        set(value) = prefs.edit { putString("deviceId", value) }

    var userName: String?
        get() = prefs.getString("userName", null)
        set(value) = prefs.edit { putString("userName", value) }

    var deviceName: String?
        get() = prefs.getString("deviceName", null)
        set(value) = prefs.edit { putString("deviceName", value) }

    var lastUsedInviteId: String?
        get() = prefs.getString("last_used_invite_id", null)
        set(value) {
            prefs.edit {
                putString("last_used_invite_id", value)
            }
        }

    fun clearLastUsedInviteId() {
        prefs.edit {
            remove("last_used_invite_id")
        }
    }

    fun isConnected(): Boolean = !token.isNullOrBlank()

    fun authHeader(): String? = token?.let { "Bearer $it" }

    fun authHeaderOrThrow(): String {
        val token = token ?: error("No token in SessionStore")
        return "Bearer $token"
    }

    var appLanguage: String?
        get() = prefs.getString("appLanguage", null)
        set(value) = prefs.edit { putString("appLanguage", value) }

    var role: String?
        get() = prefs.getString("role", null)
        set(value) = prefs.edit { putString("role", value) }

    fun getFamilies(): List<FamilyEntry> {
        val json = prefs.getString("families_v2", null)
        if (json.isNullOrBlank()) {
            // Migrate from single-family storage
            val id = familyId ?: return emptyList()
            val uid = userId ?: return emptyList()
            val r = role ?: return emptyList()
            val entry = FamilyEntry(id, uid, r)
            saveFamilies(listOf(entry))
            return listOf(entry)
        }
        return try {
            val type = object : TypeToken<List<FamilyEntry>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveFamilies(list: List<FamilyEntry>) {
        prefs.edit { putString("families_v2", Gson().toJson(list)) }
    }

    /** Replace the local family list with the server's truth (GET /v1/me/families). */
    fun syncFamilies(entries: List<FamilyEntry>) {
        saveFamilies(entries)
    }

    /** The timezone this device last reported to the server for the family. */
    fun timezoneSynced(familyId: String): String? =
        prefs.getString("tz_synced_$familyId", null)

    fun setTimezoneSynced(familyId: String, timezone: String) {
        prefs.edit { putString("tz_synced_$familyId", timezone) }
    }

    fun removeFamily(familyId: String) {
        val list = getFamilies().toMutableList()
        list.removeAll { it.familyId == familyId }
        saveFamilies(list)
    }

    fun addOrUpdateFamily(familyId: String, userId: String, role: String, name: String? = null) {
        val list = getFamilies().toMutableList()
        val existing = list.find { it.familyId == familyId }
        list.removeAll { it.familyId == familyId }
        list.add(FamilyEntry(familyId, userId, role, name ?: existing?.name))
        saveFamilies(list)
    }

    fun setActiveFamily(familyId: String) {
        val entry = getFamilies().find { it.familyId == familyId } ?: return
        this.familyId = entry.familyId
        this.userId = entry.userId
        this.role = entry.role
    }

    fun clear() {
        prefs.edit { clear() }
    }

}