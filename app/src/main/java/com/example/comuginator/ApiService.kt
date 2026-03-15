package com.example.comuginator

import retrofit2.http.*

data class RegisterRequest(
    val deviceId: String,
    val name: String
)

data class RegisterResponse(
    val deviceId: String,
    val token: String
)

data class BatteryRequest(
    val batteryPercent: Int,
    val isCharging: Boolean
)

data class Command(
    val id: String,
    val type: String,
    val payload: Map<String, Any>
)

data class CommandsResponse(
    val items: List<Command>
)

interface ApiService {

    @POST("/v1/devices/register")
    suspend fun register(
        @Body body: RegisterRequest
    ): RegisterResponse

    @POST("/v1/battery")
    suspend fun battery(
        @Header("Authorization") token: String,
        @Body body: BatteryRequest
    )

    @GET("/v1/commands/pending")
    suspend fun commands(
        @Header("Authorization") token: String
    ): CommandsResponse
}