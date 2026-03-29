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
    val role: String,
    val expiresInMinutes: Int = 60
)

data class CreateInviteResponse(
    val code: String,
    val expiresAt: String
)

data class FamilyMeResponse(
    val family: FamilyDto,
    val me: MeDto,
    val users: List<UserDto>
)

data class FamilyDto(
    val id: String,
    val name: String?
)

data class MeDto(
    val userId: String,
    val deviceId: String,
    val role: String
)

data class UserDto(
    val id: String,
    val familyId: String,
    val role: String,
    val name: String?,
    val devices: List<DeviceDto>
)

data class DeviceDto(
    val id: String,
    val deviceId: String,
    val name: String?,
    val createdAt: String,
    val lastSeenAt: String?,
    val platform: String?,
    val model: String?,
    val osVersion: String?,
    val appVersion: String?,
    val state: DeviceStateDto?
)

data class DeviceStateDto(
    val deviceId: String,
    val volumePercent: Int?,
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val reportedAt: String?
)

data class HeartbeatRequest(
    val batteryPercent: Int?,
    val volumePercent: Int?,
    val isCharging: Boolean?,
    val reportedAt: String?,
    val platform: String?,
    val model: String?,
    val osVersion: String?,
    val appVersion: String?
)

data class HeartbeatResponse(
    val ok: Boolean,
    val now: String
)

data class CreateCommandRequest(
    val type: String,
    val payload: Map<String, @JvmSuppressWildcards Any>
)

data class CreateCommandResponse(
    val ok: Boolean,
    val commandId: String
)

data class PendingCommandsResponse(
    val items: List<CommandDto>
)

data class CommandDto(
    val id: String,
    val deviceId: String,
    val type: String,
    val payload: Map<String, @JvmSuppressWildcards Any>,
    val status: String,
    val createdAt: String
)

data class AckCommandResponse(
    val ok: Boolean
)

data class ArasaacSearchResponse(
    val items: List<ArasaacCardDto>
)

data class ArasaacCardDto(
    val id: String,
    val label: String,
    val imageUrl: String
)

data class SendAacMessageRequest(
    val targetUserId: String,
    val cards: List<SendAacCardDto>,
    val suggestedReplies: List<SendAacCardDto> = emptyList()
)

data class SendAacCardDto(
    val id: String,
    val label: String,
    val imageUrl: String
)

data class SendAacMessageResponse(
    val ok: Boolean,
    val messageId: String
)