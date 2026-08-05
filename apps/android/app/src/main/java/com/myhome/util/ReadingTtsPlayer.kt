package com.myhome.util

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.myhome.net.ApiService
import com.myhome.net.dto.TtsRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 朗读练习 TTS 播放器：Edge TTS 优先（高质量英式英语），失败回退 Android 系统 TTS。
 *
 * - speak() 立即返回，在 onDone 回调里通知调用方（与原 TextToSpeech.QUEUE_FLUSH 等价语义）
 * - 任何错误（网络失败 / 解码失败 / 系统 TTS 初始化失败）都调 onDone 推进状态机，绝不卡住
 * - 单例：内部 MediaPlayer / Job 每次 speak() 重建，跨屏幕共享安全
 */
@Singleton
class ReadingTtsPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiService,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var fetchJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var systemTts: TextToSpeech? = null
    private var systemTtsReady = false
    private var pendingUtteranceId: String? = null
    private var pendingCallback: (() -> Unit)? = null

    /** 触发播放。多次调用会取消上一次。 */
    fun speak(text: String, onDone: () -> Unit) {
        cancelCurrent()
        if (text.isBlank()) {
            onDone()
            return
        }
        fetchJob = scope.launch {
            try {
                val body = api.synthTts(TtsRequest(text = text))
                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    throw IllegalStateException("empty mp3")
                }
                withContext(Dispatchers.Main) { playMp3(bytes, onDone) }
            } catch (e: Exception) {
                Log.w(TAG, "Edge TTS 失败，回退系统 TTS: ${e.message}")
                withContext(Dispatchers.Main) { speakSystem(text, onDone) }
            }
        }
    }

    private fun playMp3(bytes: ByteArray, onDone: () -> Unit) {
        val tempFile = File(context.cacheDir, "tts_${System.currentTimeMillis()}.mp3")
        try {
            tempFile.writeBytes(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "写 mp3 临时文件失败: ${e.message}")
            tempFile.delete()
            onDone()
            return
        }
        val player = MediaPlayer()
        try {
            player.setDataSource(tempFile.path)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                it.release()
                tempFile.delete()
                if (mediaPlayer === it) mediaPlayer = null
                onDone()
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                mp.release()
                tempFile.delete()
                if (mediaPlayer === mp) mediaPlayer = null
                onDone()
                true
            }
            mediaPlayer = player
            player.prepare()
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer 初始化失败: ${e.message}")
            player.release()
            tempFile.delete()
            mediaPlayer = null
            onDone()
        }
    }

    private fun speakSystem(text: String, onDone: () -> Unit) {
        if (systemTts == null && !systemTtsReady) {
            systemTts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    systemTtsReady = true
                    doSpeakSystem(text, onDone)
                } else {
                    Log.e(TAG, "系统 TTS 初始化失败 status=$status")
                    onDone()
                }
            }
        } else {
            doSpeakSystem(text, onDone)
        }
    }

    private fun doSpeakSystem(text: String, onDone: () -> Unit) {
        val tts = systemTts ?: run { onDone(); return }
        val locale = detectLocale(text)
        tts.language = locale
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId == pendingUtteranceId) {
                    pendingUtteranceId = null
                    val cb = pendingCallback
                    pendingCallback = null
                    cb?.invoke()
                }
            }
            @Deprecated("deprecated in API")
            override fun onError(utteranceId: String?) { onDone(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { onDone(utteranceId) }
        })
        val utteranceId = "tts_${System.currentTimeMillis()}"
        pendingUtteranceId = utteranceId
        pendingCallback = onDone
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "系统 TTS speak 失败 result=$result")
            pendingUtteranceId = null
            pendingCallback = null
            onDone()
        }
    }

    private fun detectLocale(text: String): Locale {
        val hasCjk = text.any { it.code in 0x4E00..0x9FFF }
        return if (hasCjk) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
    }

    private fun cancelCurrent() {
        fetchJob?.cancel()
        fetchJob = null
        mediaPlayer?.release()
        mediaPlayer = null
        try { systemTts?.stop() } catch (_: Throwable) {}
        pendingUtteranceId = null
        pendingCallback = null
    }

    /** 取消当前播放（不 shutdown 系统 TTS，留给下次复用）。 */
    fun stop() = cancelCurrent()

    /** 释放所有资源。调用后实例不可再用。 */
    fun release() {
        cancelCurrent()
        try { systemTts?.shutdown() } catch (_: Throwable) {}
        systemTts = null
        systemTtsReady = false
    }

    companion object {
        private const val TAG = "ReadingTtsPlayer"
    }
}
