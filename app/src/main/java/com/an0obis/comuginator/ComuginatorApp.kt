package com.an0obis.comuginator

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.storage.SessionStore
import okhttp3.OkHttpClient

class ComuginatorApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Background workers and raw OkHttp helpers need the active family
        // context even when no activity has been created yet.
        ApiClient.familyIdProvider = { SessionStore(this).familyId }
    }

    // Global Coil loader: image requests must carry X-Family-Id, otherwise the
    // server resolves the device's oldest family and protected images 404.
    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val familyId = SessionStore(this).familyId
                val request = if (!familyId.isNullOrBlank()) {
                    chain.request().newBuilder()
                        .header("X-Family-Id", familyId)
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
    }
}
