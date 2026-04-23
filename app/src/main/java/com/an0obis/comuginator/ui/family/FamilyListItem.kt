package com.an0obis.comuginator.ui.family

sealed interface FamilyListItem {

    data class UserHeader(
        val userId: String,
        val userName: String,
        val role: String,
        val avatarImageUrl: String?
    ) : FamilyListItem

    data class DeviceRow(
        val userId: String,
        val userRole: String,
        val deviceId: String,
        val deviceName: String,
        val batteryPercent: Int?,
        val isCharging: Boolean?,
        val lastSeenAt: String?,
        val volumePercent: Int?
    ) : FamilyListItem
}