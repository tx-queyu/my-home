package com.myhome

import android.os.Build
import com.myhome.repo.AuthRepository
import com.myhome.repo.DeviceRepository
import com.myhome.storage.DeviceIdStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRegistrar @Inject constructor(
    private val authRepo: AuthRepository,
    private val deviceRepo: DeviceRepository,
    private val deviceIdStorage: DeviceIdStorage,
) {
    val authFlow: Flow<Boolean?> = authRepo.tokenFlow
        .map { it != null }
        .distinctUntilChanged()
        .onEach { hasToken ->
            if (hasToken) ensureRegistered()
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun ensureRegistered() {
        if (deviceIdStorage.get() != null) return
        scope.launch {
            runCatching { deviceRepo.register(Build.MODEL) }
                .onSuccess { device -> deviceIdStorage.save(device.id) }
        }
    }
}
