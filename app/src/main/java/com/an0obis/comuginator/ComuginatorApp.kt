package com.an0obis.comuginator

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.an0obis.comuginator.api.ApiClient
import com.an0obis.comuginator.service.AdultMode
import com.an0obis.comuginator.service.OfflineAutoRecovery
import com.an0obis.comuginator.storage.SessionStore
import com.an0obis.comuginator.storage.SettingsStore
import okhttp3.OkHttpClient

class ComuginatorApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Background workers and raw OkHttp helpers need the active family
        // context even when no activity has been created yet.
        ApiClient.familyIdProvider = { SessionStore(this).familyId }
        ApiClient.elevationTokenProvider = { AdultMode.token }

        // Child devices recover from offline mode automatically.
        if (SessionStore(this).role == "CHILD" && SettingsStore(this).offlineMode) {
            OfflineAutoRecovery.startPolling(this)
        }

        registerActivityLifecycleCallbacks(foregroundTracker)
    }

    // Adult mode on a child device lasts only while the app is in the
    // foreground: when the last activity stops, elevation ends.
    private val foregroundTracker = object : ActivityLifecycleCallbacks {
        private var startedCount = 0

        override fun onActivityStarted(activity: Activity) {
            startedCount++
        }

        override fun onActivityStopped(activity: Activity) {
            startedCount--
            // A rotation stops and re-creates the activity — not a background trip.
            if (startedCount <= 0 && !activity.isChangingConfigurations && AdultMode.active) {
                AdultMode.end(applicationContext)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
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
