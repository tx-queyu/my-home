package com.myhome.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("is_device_owner") val isDeviceOwner: Boolean,
    @SerialName("is_blocked") val isBlocked: Boolean,
    @SerialName("last_seen") val lastSeen: String? = null,
    @SerialName("created_at") val createdAt: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    @SerialName("os_type") val osType: String = "android",
    @SerialName("os_version") val osVersion: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
)

@Serializable
data class DeviceRegisterRequest(
    val name: String,
    @SerialName("os_type") val osType: String = "android",
    @SerialName("os_version") val osVersion: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
)

@Serializable
data class DeviceCommandDto(
    val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("command_type") val commandType: String,
    val status: String,
    val error: String? = null,
    @SerialName("executed_at") val executedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class DeviceCommandCreateRequest(
    @SerialName("command_type") val commandType: String,
)

@Serializable
data class DeviceCommandAckRequest(
    val success: Boolean,
    val error: String? = null,
    @SerialName("is_device_owner") val isDeviceOwner: Boolean,
    @SerialName("is_blocked") val isBlocked: Boolean,
)
