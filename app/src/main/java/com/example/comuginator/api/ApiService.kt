package com.example.comuginator.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
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
        @Query("q") query: String
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


}