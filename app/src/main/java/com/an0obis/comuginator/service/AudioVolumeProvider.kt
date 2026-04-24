package com.an0obis.comuginator.service

import android.content.Context

object AudioVolumeProvider {
    fun getCurrentVolumePercent(context: Context): Int {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

        val stream = android.media.AudioManager.STREAM_MUSIC
        val current = audioManager.getStreamVolume(stream)
        val max = audioManager.getStreamMaxVolume(stream)

        if (max <= 0) return 0
        return (current * 100) / max
    }
}
