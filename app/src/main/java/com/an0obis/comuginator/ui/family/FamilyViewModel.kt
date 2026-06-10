package com.an0obis.comuginator.ui.family

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.an0obis.comuginator.R
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.api.CreateCommandRequest
import com.an0obis.comuginator.api.CreateInviteRequest
import com.an0obis.comuginator.api.JoinFamilyRequest
import com.an0obis.comuginator.storage.FamilyEntry
import com.an0obis.comuginator.api.FamilyMeResponse
import com.an0obis.comuginator.api.UpdateMyAvatarRequest
import com.an0obis.comuginator.api.UpdateNameRequest
import com.an0obis.comuginator.api.UserDto
import com.an0obis.comuginator.service.CommandSyncScheduler
import com.an0obis.comuginator.storage.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class InviteDisplay(
    val code: String,
    val role: String,
    val expiresAt: String,
    val inviteId: String
)

data class FamilyUiState(
    val familyResponse: FamilyMeResponse? = null,
    val optimisticVolumes: Map<String, Int> = emptyMap()
)

sealed class FamilyEvent {
    object NavigateToMain : FamilyEvent()
    object FamilySwitched : FamilyEvent()
    data class ShowToast(val message: String) : FamilyEvent()
}

class FamilyViewModel(application: Application) : AndroidViewModel(application) {

    val store = SessionStore(application)

    private val _uiState = MutableStateFlow(FamilyUiState())
    val uiState: StateFlow<FamilyUiState> = _uiState.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _inviteDisplay = MutableStateFlow<InviteDisplay?>(null)
    val inviteDisplay: StateFlow<InviteDisplay?> = _inviteDisplay.asStateFlow()

    private val _events = MutableSharedFlow<FamilyEvent>()
    val events: SharedFlow<FamilyEvent> = _events.asSharedFlow()

    var shownInviteId: String? = null
        private set

    private var refreshJob: Job? = null

    private fun str(resId: Int) = getApplication<Application>().getString(resId)
    private fun str(resId: Int, vararg args: Any?): String =
        getApplication<Application>().getString(resId, *args.map { it ?: "" }.toTypedArray())

    // ── Refresh loop ─────────────────────────────────────────────────────────

    fun startRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                loadFamily()
                delay(30.seconds)
            }
        }
    }

    fun stopRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    fun loadFamily() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.getMyFamily(store.authHeaderOrThrow())
                }
                store.role = response.me.role
                store.addOrUpdateFamily(
                    familyId = response.family.id,
                    userId = response.me.userId,
                    role = response.me.role,
                    name = response.family.name
                )
                _uiState.update { current ->
                    val newVolumes = current.optimisticVolumes.toMutableMap()
                    response.users.flatMap { it.devices }.forEach { device ->
                        if (newVolumes[device.deviceId] == device.state?.volumePercent) {
                            newVolumes.remove(device.deviceId)
                        }
                    }
                    current.copy(familyResponse = response, optimisticVolumes = newVolumes)
                }
                _statusText.value = str(R.string.family_last_updated, formatCurrentTime())
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.load_family_failed, e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun mapFamilyItems(users: List<UserDto>, optimisticVolumes: Map<String, Int>): List<FamilyListItem> {
        val out = mutableListOf<FamilyListItem>()
        for (user in users) {
            out += FamilyListItem.UserHeader(
                userId = user.id,
                userName = user.name ?: str(R.string.no_name),
                role = user.role,
                avatarImageUrl = user.avatarImageUrl
            )
            for (device in user.devices) {
                out += FamilyListItem.DeviceRow(
                    userId = user.id,
                    userRole = user.role,
                    deviceId = device.deviceId,
                    deviceName = device.name ?: device.deviceId,
                    batteryPercent = device.state?.batteryPercent,
                    isCharging = device.state?.isCharging,
                    lastSeenAt = device.lastSeenAt,
                    volumePercent = optimisticVolumes[device.deviceId] ?: device.state?.volumePercent
                )
            }
        }
        return out
    }

    // ── Invite ────────────────────────────────────────────────────────────────

    fun createInvite(role: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.creating_invite, role)
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.createInvite(
                        auth = store.authHeaderOrThrow(),
                        body = CreateInviteRequest(role = role, expiresInMinutes = 60)
                    )
                }
                shownInviteId = response.inviteId
                _inviteDisplay.value = InviteDisplay(
                    code = response.code,
                    role = role,
                    expiresAt = response.expiresAt,
                    inviteId = response.inviteId
                )
                _statusText.value = str(R.string.invite_created)
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.create_invite_failed, e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearInviteDisplay() {
        shownInviteId = null
        _inviteDisplay.value = null
    }

    fun onInviteUsed(inviteId: String?) {
        if (inviteId.isNullOrBlank()) return
        if (inviteId == shownInviteId) clearInviteDisplay()
    }

    // ── Volume ────────────────────────────────────────────────────────────────

    fun sendSetVolumeCommand(deviceId: String, volumePercent: Int) {
        val previousOptimistic = _uiState.value.optimisticVolumes[deviceId]
        _uiState.update { it.copy(optimisticVolumes = it.optimisticVolumes + (deviceId to volumePercent)) }

        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.sending_volume_command)
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.createCommand(
                        auth = store.authHeaderOrThrow(),
                        deviceId = deviceId,
                        body = CreateCommandRequest(
                            type = "set_volume",
                            payload = mapOf("volumePercent" to volumePercent)
                        )
                    )
                }
                _statusText.value = str(R.string.volume_command_sent, volumePercent)
            } catch (e: Exception) {
                _uiState.update { current ->
                    val newVolumes = current.optimisticVolumes.toMutableMap()
                    newVolumes.remove(deviceId)
                    if (previousOptimistic != null) newVolumes[deviceId] = previousOptimistic
                    current.copy(optimisticVolumes = newVolumes)
                }
                _statusText.value = str(R.string.failed_send_volume_command, e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────────

    fun updateFamilyName(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.updating_family_name)
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateMyFamily(
                        auth = store.authHeaderOrThrow(),
                        body = UpdateNameRequest(name = name)
                    )
                }
                _statusText.value = str(R.string.family_name_updated)
                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.update_family_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    fun updateUserName(userId: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.updating_user_name)
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateUserName(
                        auth = store.authHeaderOrThrow(),
                        userId = userId,
                        body = UpdateNameRequest(name)
                    )
                }
                _statusText.value = str(R.string.user_name_updated)
                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.update_user_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    fun updateDeviceName(deviceId: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.updating_device_name)
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateDeviceName(
                        auth = store.authHeaderOrThrow(),
                        deviceId = deviceId,
                        body = UpdateNameRequest(name)
                    )
                }
                _statusText.value = str(R.string.device_name_updated)
                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.update_device_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    fun updateUserAvatar(userId: String, avatarItemId: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.updating_avatar)
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.updateUserAvatar(
                        auth = store.authHeaderOrThrow(),
                        userId = userId,
                        body = UpdateMyAvatarRequest(avatarItemId = avatarItemId)
                    )
                }
                _statusText.value = str(R.string.avatar_updated)
                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.update_avatar_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    // ── Multi-family ──────────────────────────────────────────────────────────

    fun getFamilies(): List<FamilyEntry> = store.getFamilies()

    fun setActiveFamily(familyId: String) {
        store.setActiveFamily(familyId)
        _uiState.value = FamilyUiState()
        _inviteDisplay.value = null
        viewModelScope.launch { _events.emit(FamilyEvent.FamilySwitched) }
        loadFamily()
    }

    fun joinAnotherFamily(code: String) {
        val userName = store.userName ?: return
        val deviceName = store.deviceName ?: return
        val deviceId = store.deviceId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.joining_family)
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.api.joinFamily(
                        JoinFamilyRequest(
                            code = code.trim().uppercase(),
                            userName = userName,
                            deviceName = deviceName,
                            deviceId = deviceId
                        )
                    )
                }
                store.token = response.token
                store.addOrUpdateFamily(response.familyId, response.userId, response.role)
                store.setActiveFamily(response.familyId)
                _uiState.value = FamilyUiState()
                _inviteDisplay.value = null
                val successMsg = str(R.string.switch_family_success)
                _statusText.value = successMsg
                _events.emit(FamilyEvent.ShowToast(successMsg))
                _events.emit(FamilyEvent.FamilySwitched)
                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                val errorMsg = str(R.string.join_family_failed, e.message)
                _statusText.value = errorMsg
                _events.emit(FamilyEvent.ShowToast(errorMsg))
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.deleting_user)
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.deleteUser(auth = store.authHeaderOrThrow(), userId = userId)
                }
                _statusText.value = str(R.string.user_deleted)
                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.delete_user_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.deleting_device)
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.deleteDevice(auth = store.authHeaderOrThrow(), deviceId = deviceId)
                }
                _statusText.value = str(R.string.device_deleted)
                loadFamily()
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.delete_device_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    fun deleteFamily() {
        viewModelScope.launch {
            _isLoading.value = true
            _statusText.value = str(R.string.deleting_family)
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.api.deleteMyFamily(auth = store.authHeaderOrThrow())
                }
                store.clear()
                _events.emit(FamilyEvent.NavigateToMain)
            } catch (e: Exception) {
                if (handleUnauthorized(e)) return@launch
                _statusText.value = str(R.string.delete_family_failed, e.message)
                _isLoading.value = false
            }
        }
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun setStatus(text: String) {
        _statusText.value = text
    }

    fun sendHeartbeat() {
        CommandSyncScheduler.enqueueImmediate(getApplication(), "manual_test")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun handleUnauthorized(e: Exception): Boolean {
        if (e is HttpException && e.code() == 401) {
            val currentFamilyId = store.familyId
            if (currentFamilyId != null) {
                store.removeFamily(currentFamilyId)
                val remaining = store.getFamilies()
                if (remaining.isNotEmpty()) {
                    store.setActiveFamily(remaining.first().familyId)
                    _uiState.value = FamilyUiState()
                    _inviteDisplay.value = null
                    _events.emit(FamilyEvent.ShowToast(str(R.string.removed_from_family)))
                    _events.emit(FamilyEvent.FamilySwitched)
                    loadFamily()
                    return true
                }
            }
            store.clear()
            _events.emit(FamilyEvent.NavigateToMain)
            return true
        }
        return false
    }

    private fun formatCurrentTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
