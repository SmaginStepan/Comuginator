package com.an0obis.comuginator.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("PowerConnectionReceiver", "action=${intent.action}")
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED ->
                TelemetryScheduler.enqueueImmediate(context, "power_connected")

            Intent.ACTION_POWER_DISCONNECTED ->
                TelemetryScheduler.enqueueImmediate(context, "power_disconnected")
        }
    }
}