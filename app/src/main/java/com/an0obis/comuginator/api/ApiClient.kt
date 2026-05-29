package com.an0obis.comuginator.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson

object ApiClient {
    const val BASE_URL = "https://comuginator.com/"
    private val gson = Gson()
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun loadBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null

                val bytes = response.body.bytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.e("ApiClient", "failed", e)
            null
        }
    }

    fun getAacMessageWithAuthHeader(authHeader: String, messageId: String): AacMessageDetailsDto {
        val request = Request.Builder()
            .url("${BASE_URL}v1/messages/aac/$messageId")
            .header("Authorization", authHeader)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("getAacMessage failed: ${response.code}")
            }

            val body = response.body.string()
            return gson.fromJson(body, AacMessageDetailsDto::class.java)
        }
    }
    fun replyToAacMessage(
        authHeader: String,
        messageId: String,
        requestBody: SendAacReplyRequest
    ): SendAacReplyResponse {
        val json = gson.toJson(requestBody)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("${BASE_URL}v1/messages/aac/$messageId/reply")
            .header("Authorization", authHeader)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("replyToAacMessage failed: ${response.code}")
            }

            val raw = response.body.string()
            return gson.fromJson(raw, SendAacReplyResponse::class.java)
        }
    }
}