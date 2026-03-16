package com.example.comuginator.api

data class CreateFamilyRequest(
    val userName: String,
    val deviceName: String,
    val deviceId: String,
    val familyName: String? = null
)

data class CreateFamilyResponse(
    val familyId: String,
    val userId: String,
    val deviceId: String,
    val token: String,
    val role: String
)

data class JoinFamilyRequest(
    val code: String,
    val userName: String,
    val deviceName: String,
    val deviceId: String
)

data class JoinFamilyResponse(
    val familyId: String,
    val userId: String,
    val deviceId: String,
    val token: String,
    val role: String,
    val userCreated: Boolean
)

data class CreateInviteRequest(
    val expiresInMinutes: Int = 60
)

data class CreateInviteResponse(
    val code: String,
    val expiresAt: String
)

data class DeviceStateDto(
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val reportedAt: String?
)

data class DeviceDto(
    val id: String,
    val deviceId: String,
    val userId: String,
    val name: String?,
    val platform: String?,
    val model: String?,
    val osVersion: String?,
    val appVersion: String?,
    val createdAt: String,
    val updatedAt: String,
    val lastSeenAt: String?,
    val state: DeviceStateDto?
)

data class UserDto(
    val id: String,
    val familyId: String,
    val role: String,
    val name: String?,
    val avatarUrl: String?,
    val createdAt: String,
    val updatedAt: String,
    val devices: List<DeviceDto>
)

data class FamilyInfoDto(
    val id: String,
    val name: String?
)

data class MeDto(
    val userId: String,
    val deviceId: String,
    val role: String
)

data class FamilyMeResponse(
    val family: FamilyInfoDto,
    val me: MeDto,
    val users: List<UserDto>
)

data class HeartbeatRequest(
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val reportedAt: String? = null,
    val platform: String? = null,
    val model: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null
)

data class HeartbeatResponse(
    val ok: Boolean,
    val now: String
)