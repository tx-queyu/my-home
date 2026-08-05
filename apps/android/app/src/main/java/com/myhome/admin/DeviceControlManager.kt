package com.myhome.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceControlManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val appPackage: String = ctx.packageName
    private val adminComponent: ComponentName =
        ComponentName(ctx, MyDeviceAdminReceiver::class.java)
    private val dpm: DevicePolicyManager =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(appPackage)

    fun isUninstallBlocked(): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            dpm.isUninstallBlocked(adminComponent, appPackage)
        } catch (_: SecurityException) {
            false
        }
    }

    fun setUninstallBlocked(blocked: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            dpm.setUninstallBlocked(adminComponent, appPackage, blocked)
            true
        } catch (_: SecurityException) {
            Thread.sleep(200)
            try {
                dpm.setUninstallBlocked(adminComponent, appPackage, blocked)
                true
            } catch (_: SecurityException) {
                false
            }
        }
    }

    fun clearDeviceOwner(): Boolean {
        if (!isDeviceOwner()) return true
        return try {
            dpm.clearDeviceOwnerApp(appPackage)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
