package com.example.comuginator.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

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
}