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
    val avatarItemId: String?,
    val avatarImageUrl: String?,
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
    val items: List<AacCardDto>
)

data class SendAacMessageRequest(
    val targetUserId: String,
    val cards: List<AacCardDto>,
    val suggestedReplies: List<AacCardDto> = emptyList()
)

data class SendAacMessageResponse(
    val ok: Boolean,
    val messageId: String
)

data class AacCardDto(
    val id: String,
    val label: String,
    val imageUrl: String?,
    val source: String? = null,
    val sourceRef: String? = null,
)

data class AacUserDto(
    val id: String,
    val name: String,
    val role: String,
    val avatarItemId: String?,
    val avatarUrl: String?
)
data class AacReplyShortDto(
    val id: String,
    val reply: AacCardDto,
    val createdAt: String
)

data class AacMessageDetailsDto(
    val id: String,
    val fromUser: AacUserDto,
    val toUser: AacUserDto,
    val message: List<AacCardDto>,
    val suggestedReplies: List<AacCardDto>,
    val reply: AacReplyShortDto?,
    val createdAt: String,
    val answeredAt: String?
)

data class SendAacReplyRequest(
    val reply: AacCardDto
)

data class SendAacReplyResponse(
    val ok: Boolean,
    val replyId: String
)

data class AacMessageListItemDto(
    val id: String,
    val familyId: String,
    val fromUserId: String,
    val toUserId: String,
    val fromUser: AacUserDto?,
    val toUser: AacUserDto?,
    val message: List<AacCardDto>,
    val suggestedReplies: List<AacCardDto>,
    val reply: AacReplyShortDto?,
    val createdAt: String,
    val answeredAt: String?
)

data class AacMessagesResponse(
    val ok: Boolean,
    val items: List<AacMessageListItemDto>
)

data class UploadFamilyPhotoResponse(
    val ok: Boolean,
    val item: AacCardDto
)

data class FamilyPhotoListResponse(
    val ok: Boolean,
    val items: List<AacCardDto>
)

data class UpdateNameRequest(
    val name: String
)

data class UpdateFamilyResponse(
    val ok: Boolean,
    val family: FamilyNameDto
)

data class UpdateUserResponse(
    val ok: Boolean,
    val user: UserNameDto
)

data class UpdateDeviceResponse(
    val ok: Boolean,
    val device: DeviceNameDto
)

data class FamilyNameDto(
    val id: String,
    val name: String
)

data class UserNameDto(
    val id: String,
    val name: String,
    val role: String
)

data class DeviceNameDto(
    val deviceId: String,
    val name: String
)

data class LibrarySetDto(
    val id: String,
    val name: String,
    val cover: AacCardDto?,
    val itemsCount: Int
)

data class LibrarySetDetailsDto(
    val id: String,
    val name: String,
    val cover: AacCardDto?,
    val items: List<AacCardDto>
)

data class LibrarySetsResponse(
    val ok: Boolean,
    val sets: List<LibrarySetDto>
)

data class LibrarySetResponse(
    val ok: Boolean,
    val set: LibrarySetDetailsDto
)

data class CreateLibrarySetRequest(
    val name: String,
    val coverItemId: String? = null,
    val itemIds: List<String> = emptyList()
)

data class UpdateLibrarySetRequest(
    val name: String? = null,
    val coverItemId: String? = null
)

data class AddItemsToSetRequest(
    val itemIds: List<String>
)

data class MoveItemsToSetRequest(
    val itemIds: List<String>,
    val targetSetId: String
)

data class CreateArasaacLibraryItemRequest(
    val label: String,
    val sourceRef: String
)

data class LibraryItemsResponse(
    val ok: Boolean,
    val items: List<AacCardDto>
)

data class OkResponse(
    val ok: Boolean
)

data class LibraryItemResponse(
    val ok: Boolean,
    val item: AacCardDto
)

data class UpdateMyAvatarRequest(
    val avatarItemId: String?
)

data class UpdateMyAvatarResponse(
    val ok: Boolean,
    val user: UserDto
)