package com.myhome.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.myhome.admin.DeviceControlManager
import com.myhome.net.TokenStorage
import com.myhome.net.dto.DeviceCommandDto
import com.myhome.repo.DeviceRepository
import com.myhome.storage.DeviceIdStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DeviceControlService : Service() {

    @Inject lateinit var deviceRepo: DeviceRepository
    @Inject lateinit var manager: DeviceControlManager
    @Inject lateinit var tokenStorage: TokenStorage
    @Inject lateinit var deviceIdStorage: DeviceIdStorage

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        pollJob?.cancel()
        pollJob = scope.launch { pollLoop() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val token = tokenStorage.getAccessToken()
            val deviceId = deviceIdStorage.get()
            if (token == null || deviceId == null) {
                delay(5000)
                continue
            }
            try {
                val commands = deviceRepo.pollCommands(deviceId, timeoutSec = 60)
                for (cmd in commands) {
                    executeCommand(cmd, deviceId)
                }
            } catch (e: Exception) {
                delay(5000)
            }
        }
    }

    private suspend fun executeCommand(cmd: DeviceCommandDto, deviceId: String) {
        val isOwner = manager.isDeviceOwner()
        val result = when (cmd.commandType) {
            "enable_block" -> if (isOwner) manager.setUninstallBlocked(true) else false
            "disable_block" -> if (isOwner) manager.setUninstallBlocked(false) else false
            else -> false
        }
        val error = if (!result) {
            if (!isOwner) "not_device_owner" else "execution_failed"
        } else null
        runCatching {
            deviceRepo.ackCommand(
                deviceId = deviceId,
                cmdId = cmd.id,
                success = result,
                error = error,
                isDeviceOwner = isOwner,
                isBlocked = manager.isUninstallBlocked(),
            )
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "设备管控",
                        NotificationManager.IMPORTANCE_LOW,
                    ),
                )
            }
        }
    }

    private fun startForegroundCompat() {
        val notif: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("我家 App 设备管控")
                .setContentText("正在监听家长指令")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("我家 App 设备管控")
                .setContentText("正在监听家长指令")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        const val CHANNEL_ID = "myhome_device_control"
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DeviceControlService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DeviceControlService::class.java))
        }
    }
}
