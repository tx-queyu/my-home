package com.myhome.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhome.BuildConfig
import com.myhome.net.dto.VersionInfoDto
import com.myhome.repo.ApkDownloader
import com.myhome.repo.VersionRepository
import com.myhome.repo.isVersionNewer
import com.myhome.util.friendlyError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateState {
    data object Checking : UpdateState
    data class Error(val message: String) : UpdateState
    data class UpdateAvailable(
        val info: VersionInfoDto,
        val downloadProgress: Float? = null,
        val downloadError: String? = null,
    ) : UpdateState
    data object UpToDate : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val versionRepo: VersionRepository,
    private val apkDownloader: ApkDownloader,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Checking)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            runCatching { versionRepo.fetchVersionInfo() }
                .onSuccess { info ->
                    val newer = isVersionNewer(info.version, BuildConfig.VERSION_NAME)
                    _state.value = if (newer) UpdateState.UpdateAvailable(info) else UpdateState.UpToDate
                }
                .onFailure { e ->
                    _state.value = UpdateState.Error(friendlyError(e))
                }
        }
    }

    fun startUpdateDownload() {
        val current = _state.value
        val info = (current as? UpdateState.UpdateAvailable)?.info ?: return
        val url = versionRepo.apkDownloadUrl(info.apkUrl)
        val id = apkDownloader.startDownload(url, info.version)
        _state.update {
            (it as UpdateState.UpdateAvailable).copy(downloadProgress = 0f, downloadError = null)
        }
        if (id <= 0) {
            _state.update {
                (it as UpdateState.UpdateAvailable).copy(downloadProgress = null)
            }
            return
        }
        viewModelScope.launch {
            var elapsed = 0L
            while (elapsed < 600_000L) {
                kotlinx.coroutines.delay(500L)
                elapsed += 500L
                val p = apkDownloader.queryProgress(id)
                if (p == null) {
                    _state.update {
                        (it as UpdateState.UpdateAvailable).copy(
                            downloadProgress = null,
                            downloadError = "下载状态查询失败，请重试",
                        )
                    }
                    return@launch
                }
                if (p < 0f) {
                    _state.update {
                        (it as UpdateState.UpdateAvailable).copy(
                            downloadProgress = null,
                            downloadError = "下载失败，请重试",
                        )
                    }
                    return@launch
                }
                _state.update {
                    (it as UpdateState.UpdateAvailable).copy(downloadProgress = p)
                }
                if (p >= 1f) {
                    _state.update {
                        (it as UpdateState.UpdateAvailable).copy(downloadProgress = null)
                    }
                    return@launch
                }
            }
            _state.update {
                (it as UpdateState.UpdateAvailable).copy(downloadProgress = null)
            }
        }
    }
}
