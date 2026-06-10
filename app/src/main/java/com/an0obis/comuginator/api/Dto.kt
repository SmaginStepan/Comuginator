package com.an0obis.comuginator.api

data class CreateFamilyRequest(
    val userName: String,
    val deviceName: String,
    val deviceId: String,
    val familyName: String? = null,
    val timezone: String? = null
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
    val deviceId: String,
    val timezone: String? = null
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
    val inviteId: String,
    val code: String,
    val expiresAt: String
)

data class MyFamiliesResponse(
    val ok: Boolean,
    val families: List<MyFamilyDto>
)

data class MyFamilyDto(
    val familyId: String,
    val familyName: String?,
    val userId: String,
    val userName: String?,
    val role: String
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
    val mode: String = "NORMAL",
    val cards: List<AacCardDto>,
    val suggestedReplies: List<SuggestedReplyItem>,
    val requiredReplyCount: Int = 1
)

data class SendAacMessageResponse(
    val ok: Boolean,
    val messageId: String
)

data class AacSuggestedReplyDto(
    val type: String? = null,

    val id: String? = null,
    val label: String? = null,
    val imageUrl: String? = null,
    val source: String? = null,
    val sourceRef: String? = null,
    val storageKey: String? = null,

    val seconds: Int? = null
) {
    fun isWait(): Boolean = type == "WAIT"

    fun toCardDto(): AacCardDto {
        if (type == "WAIT") {
            val waitSeconds = seconds ?: 60

            val label = if (waitSeconds < 60) {
                "⏱ ${waitSeconds}s"
            } else {
                val minutes = waitSeconds / 60
                val restSeconds = waitSeconds % 60
                "⏱ %d:%02d".format(minutes, restSeconds)
            }

            return AacCardDto(
                id = "WAIT_$waitSeconds",
                label = label,
                imageUrl = "",
                source = "WAIT",
                sourceRef = waitSeconds.toString()
            )
        }

        return AacCardDto(
            id = id.orEmpty(),
            label = label.orEmpty(),
            imageUrl = imageUrl.orEmpty(),
            source = source.orEmpty(),
            sourceRef = sourceRef
        )
    }
}

data class WaitStepDto(
    val type: String = "WAIT",
    val seconds: Int
) : SuggestedReplyItem

data class AacCardDto(
    val id: String,
    val label: String,
    val imageUrl: String?,
    val source: String? = null,
    val sourceRef: String? = null,
): SuggestedReplyItem

data class AacUserDto(
    val id: String,
    val name: String,
    val role: String,
    val avatarItemId: String?,
    val avatarImageUrl: String?
)
data class AacReplyShortDto(
    val id: String,
    val reply: List<AacCardDto>,
    val createdAt: String
)

data class AacMessageDetailsDto(
    val id: String,
    val fromUser: AacUserDto,
    val toUser: AacUserDto,
    val message: List<AacCardDto>,
    val suggestedReplies: List<AacSuggestedReplyDto>,
    val mode: String = "NORMAL",
    val reply: AacReplyShortDto?,
    val requiredReplyCount: Int = 1,
    val createdAt: String,
    val answeredAt: String?
)

data class SendAacReplyRequest(
    val reply: List<AacCardDto>
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
    val suggestedReplies: List<AacSuggestedReplyDto>,
    val reply: AacReplyShortDto?,
    val createdAt: String,
    val mode: String = "NORMAL",
    val requiredReplyCount: Int = 1,
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

data class UpdateFamilyRequest(
    val name: String? = null,
    val timezone: String? = null
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

data class RenameLibraryItemRequest(
    val label: String
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

data class FcmTokenRequest(
    val fcmToken: String
)

data class ChildHomeNodeItemDto(
    val id: String,
    val label: String,
    val source: String?,
    val sourceRef: String?,
    val imageUrl: String?,
    val isVisible: Boolean = true
)

data class ChildHomeTargetDto(
    val id: String,
    val nodeId: String,
    val userId: String
)

data class ChildHomeNodeDto(
    val id: String,
    val familyId: String,
    val itemId: String,
    val parentId: String?,
    val type: String,
    val sortOrder: Int,
    val targetMode: String,
    val blinkEnabled: Boolean,
    val blinkSeconds: Int,
    val labelOverride: String?,
    val isVisible: Boolean = true,
    val item: ChildHomeNodeItemDto?,
    val targets: List<ChildHomeTargetDto> = emptyList()
)

data class ChildHomeNodesResponse(
    val ok: Boolean,
    val items: List<ChildHomeNodeDto>
)

data class ChildHomeActionRequestResponse(
    val ok: Boolean,
    val sentCount: Int,
    val blinkEnabled: Boolean,
    val blinkSeconds: Int
)

data class CreateChildHomeNodeRequest(
    val itemId: String,
    val parentId: String?,
    val type: String,
    val sortOrder: Int? = null,
    val targetMode: String = "ALL_PARENTS",
    val targetUserIds: List<String>? = null,
    val blinkEnabled: Boolean = true,
    val blinkSeconds: Int = 60
)

data class UpdateChildHomeNodeRequest(
    val itemId: String? = null,
    val parentId: String? = null,
    val type: String? = null,
    val sortOrder: Int? = null,
    val targetMode: String? = null,
    val targetUserIds: List<String>? = null,
    val blinkEnabled: Boolean? = null,
    val blinkSeconds: Int? = null,
    val labelOverride: String? = null,
    val isVisible: Boolean? = null
)

data class ChildHomeNodeResponse(
    val ok: Boolean,
    val item: ChildHomeNodeDto
)

data class MoveLibrarySetItemsRequest(
    val itemIds: List<String>
)

data class MoveLibrarySetsRequest(
    val setIds: List<String>
)

data class ScheduleItemDto(
    val id: String,
    val familyId: String,

    val name: String?,

    val mode: String, // WEEKDAY | DATE

    val weekdays: List<Int>,
    val date: String?,
    val time: String,

    val sortOrder: Int,

    val cards: List<AacCardDto>,

    val forceShowChildHomeNodeIds: List<String>,
    val forceHideChildHomeNodeIds: List<String>
)

data class ScheduleItemsResponse(
    val ok: Boolean,
    val items: List<ScheduleItemDto>
)

data class ScheduleItemResponse(
    val ok: Boolean,
    val item: ScheduleItemDto
)

data class UpdateScheduleItemRequest(
    val mode: String? = null,
    val name: String? = null,
    val weekdays: List<Int>? = null,
    val date: String? = null,
    val time: String? = null,
    val cards: List<AacCardDto>? = null,
    val isEnabled: Boolean? = null,
    val forceShowChildHomeNodeIds: List<String>? = null,
    val forceHideChildHomeNodeIds: List<String>? = null
)

data class CreateScheduleItemRequest(
    val mode: String,
    val name: String? = null,
    val weekdays: List<Int> = emptyList(),
    val date: String? = null,
    val time: String,
    val cards: List<AacCardDto>,
    val sortOrder: Int? = null,
    val isEnabled: Boolean = true,
    val forceShowChildHomeNodeIds: List<String> = emptyList(),
    val forceHideChildHomeNodeIds: List<String> = emptyList()
)