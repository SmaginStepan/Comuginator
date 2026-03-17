package com.example.comuginator.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

data class BatterySnapshot(
    val batteryPercent: Int?,
    val isCharging: Boolean?
)

object DeviceInfoProvider {

    fun getBatterySnapshot(context: Context): BatterySnapshot {
        val intent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        if (intent == null) {
            return BatterySnapshot(
                batteryPercent = null,
                isCharging = null
            )
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

        val batteryPercent =
            if (level >= 0 && scale > 0) ((level * 100) / scale) else null

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatterySnapshot(
            batteryPercent = batteryPercent,
            isCharging = isCharging
        )
    }

    fun getPlatform(): String = "android"

    fun getModel(): String = Build.MODEL ?: "unknown"

    fun getOsVersion(): String = Build.VERSION.RELEASE ?: "unknown"

    fun getAppVersion(context: Context): String {
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        return pkg.versionName ?: "1.0"
    }
}