package com.an0obis.comuginator.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("/v1/families/create")
    suspend fun createFamily(
        @Body body: CreateFamilyRequest
    ): CreateFamilyResponse

    @POST("/v1/families/join")
    suspend fun joinFamily(
        @Body body: JoinFamilyRequest
    ): JoinFamilyResponse

    @POST("/v1/invites")
    suspend fun createInvite(
        @Header("Authorization") auth: String,
        @Body body: CreateInviteRequest
    ): CreateInviteResponse

    @GET("/v1/families/me")
    suspend fun getMyFamily(
        @Header("Authorization") auth: String
    ): FamilyMeResponse

    @POST("/v1/devices/heartbeat")
    suspend fun heartbeat(
        @Header("Authorization") auth: String,
        @Body body: HeartbeatRequest
    ): HeartbeatResponse

    @POST("/v1/devices/{deviceId}/commands")
    suspend fun createCommand(
        @Header("Authorization") auth: String,
        @Path("deviceId") deviceId: String,
        @Body body: CreateCommandRequest
    ): CreateCommandResponse

    @GET("/v1/commands/pending")
    suspend fun getPendingCommands(
        @Header("Authorization") auth: String
    ): PendingCommandsResponse

    @POST("/v1/commands/{id}/ack")
    suspend fun ackCommand(
        @Header("Authorization") auth: String,
        @Path("id") commandId: String
    ): AckCommandResponse

    @GET("/v1/arasaac/search")
    suspend fun searchArasaac(
        @Header("Authorization") auth: String,
        @Query("q") query: String,
        @Query("lang") lang: String
    ): ArasaacSearchResponse

    @POST("/v1/messages/aac")
    suspend fun sendAacMessage(
        @Header("Authorization") auth: String,
        @Body body: SendAacMessageRequest
    ): SendAacMessageResponse

    @GET("/v1/messages/aac")
    suspend fun getAacMessages(
        @Header("Authorization") auth: String,
        @Query("scope") scope: String = "all",
        @Query("fromUserId") fromUserId: String? = null,
        @Query("toUserId") toUserId: String? = null
    ): AacMessagesResponse

    @Multipart
    @POST("/v1/library/items/upload")
    suspend fun uploadFamilyPhoto(
        @Header("Authorization") auth: String,
        @Part file: MultipartBody.Part,
        @Part("label") label: RequestBody
    ): UploadFamilyPhotoResponse

    @Suppress("unused")
    @GET("/v1/library/items")
    suspend fun getFamilyPhotos(
        @Header("Authorization") auth: String,
        @Query("source") source: String = "FAMILY_PHOTO"
    ): FamilyPhotoListResponse

    @PATCH("/v1/families/me")
    suspend fun updateMyFamily(
        @Header("Authorization") auth: String,
        @Body body: UpdateNameRequest
    ): UpdateFamilyResponse

    @PATCH("/v1/users/{userId}")
    suspend fun updateUserName(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String,
        @Body body: UpdateNameRequest
    ): UpdateUserResponse

    @PATCH("/v1/devices/{deviceId}/name")
    suspend fun updateDeviceName(
        @Header("Authorization") auth: String,
        @Path("deviceId") deviceId: String,
        @Body body: UpdateNameRequest
    ): UpdateDeviceResponse

    @DELETE("v1/devices/{deviceId}")
    suspend fun deleteDevice(
        @Header("Authorization") auth: String,
        @Path("deviceId") deviceId: String
    ): OkResponse

    @GET("/v1/library/sets")
    suspend fun getLibrarySets(
        @Header("Authorization") auth: String
    ): LibrarySetsResponse

    @GET("/v1/library/sets/{setId}")
    suspend fun getLibrarySet(
        @Header("Authorization") auth: String,
        @Path("setId") setId: String
    ): LibrarySetResponse

    @POST("/v1/library/sets")
    suspend fun createLibrarySet(
        @Header("Authorization") auth: String,
        @Body body: CreateLibrarySetRequest
    ): LibrarySetResponse

    @PATCH("/v1/library/sets/{setId}")
    suspend fun updateLibrarySet(
        @Header("Authorization") auth: String,
        @Path("setId") setId: String,
        @Body body: UpdateLibrarySetRequest
    ): LibrarySetResponse

    @DELETE("/v1/library/sets/{setId}")
    suspend fun deleteLibrarySet(
        @Header("Authorization") auth: String,
        @Path("setId") setId: String
    ): OkResponse

    @GET("/v1/library/items")
    suspend fun getLibraryItems(
        @Header("Authorization") auth: String,
        @Query("source") source: String? = null
    ): LibraryItemsResponse

    @DELETE("/v1/library/items/{itemId}")
    suspend fun deleteLibraryItem(
        @Header("Authorization") auth: String,
        @Path("itemId") itemId: String
    ): OkResponse

    @PATCH("/v1/library/items/{itemId}")
    suspend fun renameLibraryItem(
        @Header("Authorization") auth: String,
        @Path("itemId") itemId: String,
        @Body body: RenameLibraryItemRequest
    ): LibraryItemResponse

    @POST("/v1/library/sets/{setId}/items")
    suspend fun addItemsToSet(
        @Header("Authorization") auth: String,
        @Path("setId") setId: String,
        @Body body: AddItemsToSetRequest
    ): LibrarySetResponse

    @Suppress("unused")
    @DELETE("/v1/library/sets/{setId}/items/{itemId}")
    suspend fun removeItemFromSet(
        @Header("Authorization") auth: String,
        @Path("setId") setId: String,
        @Path("itemId") itemId: String
    ): LibrarySetResponse

    @Suppress("unused")
    @POST("/v1/library/sets/{setId}/move-items")
    suspend fun moveItemsToAnotherSet(
        @Header("Authorization") auth: String,
        @Path("setId") setId: String,
        @Body body: MoveItemsToSetRequest
    ): OkResponse

    @POST("/v1/library/items/arasaac")
    suspend fun createArasaacLibraryItem(
        @Header("Authorization") auth: String,
        @Body body: CreateArasaacLibraryItemRequest
    ): LibraryItemResponse

    @Suppress("unused")
    @PATCH("/v1/users/me/avatar")
    suspend fun updateMyAvatar(
        @Header("Authorization") auth: String,
        @Body body: UpdateMyAvatarRequest
    ): UpdateMyAvatarResponse

    @PATCH("/v1/users/{userId}/avatar")
    suspend fun updateUserAvatar(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String,
        @Body body: UpdateMyAvatarRequest
    ): UpdateMyAvatarResponse

    @POST("/v1/devices/fcm-token")
    suspend fun updateFcmToken(
        @Header("Authorization") auth: String,
        @Body body: FcmTokenRequest
    ): OkResponse

    @GET("/v1/child-home/nodes")
    suspend fun getChildHomeNodes(
        @Header("Authorization") auth: String,
        @Query("parentId") parentId: String? = null
    ): ChildHomeNodesResponse

    @POST("/v1/child-home/actions/{nodeId}/request")
    suspend fun requestChildHomeAction(
        @Header("Authorization") auth: String,
        @Path("nodeId") nodeId: String
    ): ChildHomeActionRequestResponse

    @POST("/v1/child-home/nodes")
    suspend fun createChildHomeNode(
        @Header("Authorization") auth: String,
        @Body body: CreateChildHomeNodeRequest
    ): ChildHomeNodeResponse

    @PATCH("/v1/child-home/nodes/{nodeId}")
    suspend fun updateChildHomeNode(
        @Header("Authorization") auth: String,
        @Path("nodeId") nodeId: String,
        @Body body: UpdateChildHomeNodeRequest
    ): ChildHomeNodeResponse

    @DELETE("/v1/child-home/nodes/{nodeId}")
    suspend fun deleteChildHomeNode(
        @Header("Authorization") auth: String,
        @Path("nodeId") nodeId: String
    ): OkResponse

    @POST("/v1/library/sets/{setId}/move-items")
    suspend fun moveLibrarySetItems(
        @Header("Authorization") auth: String,
        @Path("setId") setId: String,
        @Body body: MoveLibrarySetItemsRequest
    )

    @POST("/v1/library/sets/move")
    suspend fun moveLibrarySets(
        @Header("Authorization") auth: String,
        @Body body: MoveLibrarySetsRequest
    )

    @DELETE("/v1/users/{userId}")
    suspend fun deleteUser(
        @Header("Authorization") auth: String,
        @Path("userId") userId: String
    ): OkResponse

    @DELETE("/v1/families/me")
    suspend fun deleteMyFamily(
        @Header("Authorization") auth: String
    ): OkResponse

    @GET("/v1/schedule/items")
    suspend fun getScheduleItems(
        @Header("Authorization") auth: String
    ): ScheduleItemsResponse

    @DELETE("/v1/schedule/items/{id}")
    suspend fun deleteScheduleItem(
        @Header("Authorization") auth: String,
        @Path("id") id: String
    ): OkResponse

    @POST("/v1/schedule/items")
    suspend fun createScheduleItem(
        @Header("Authorization") auth: String,
        @Body body: CreateScheduleItemRequest
    ): ScheduleItemResponse

    @PATCH("/v1/schedule/items/{id}")
    suspend fun updateScheduleItem(
        @Header("Authorization") auth: String,
        @Path("id") id: String,
        @Body body: UpdateScheduleItemRequest
    ): ScheduleItemResponse
}