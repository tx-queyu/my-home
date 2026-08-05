package com.myhome.repo

import com.myhome.admin.OsDetector
import com.myhome.net.ApiService
import com.myhome.net.dto.DeviceCommandAckRequest
import com.myhome.net.dto.DeviceCommandCreateRequest
import com.myhome.net.dto.DeviceCommandDto
import com.myhome.net.dto.DeviceDto
import com.myhome.net.dto.DeviceRegisterRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val api: ApiService,
) {
    suspend fun register(name: String): DeviceDto {
        val req = DeviceRegisterRequest(
            name = name,
            osType = OsDetector.osType(),
            osVersion = OsDetector.osVersion(),
            manufacturer = OsDetector.manufacturer(),
            model = OsDetector.model(),
        )
        return api.registerDevice(req)
    }

    suspend fun list(): List<DeviceDto> = api.listDevices()

    suspend fun get(id: String): DeviceDto = api.getDevice(id)

    suspend fun issueCommand(deviceId: String, commandType: String): DeviceCommandDto =
        api.issueDeviceCommand(deviceId, DeviceCommandCreateRequest(commandType))

    suspend fun pollCommands(deviceId: String, timeoutSec: Int = 60): List<DeviceCommandDto> =
        api.pollDeviceCommands(deviceId, timeoutSec)

    suspend fun ackCommand(
        deviceId: String,
        cmdId: String,
        success: Boolean,
        error: String?,
        isDeviceOwner: Boolean,
        isBlocked: Boolean,
    ): DeviceCommandDto = api.ackDeviceCommand(
        cmdId,
        deviceId,
        DeviceCommandAckRequest(
            success = success,
            error = error,
            isDeviceOwner = isDeviceOwner,
            isBlocked = isBlocked,
        ),
    )
}
