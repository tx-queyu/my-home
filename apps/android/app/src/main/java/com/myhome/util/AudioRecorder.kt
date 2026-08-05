package com.myhome.util

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * 16kHz 16bit mono PCM 录音器 —— 给朗读评测（讯飞 ISE）用。
 *
 * 调用流程：
 *   val recorder = AudioRecorder()
 *   if (recorder.start()) {
 *       delay(2000)  // 或等用户停止
 *       val pcm = recorder.stop()
 *       // pcm 上传到后端 /api/courses/{id}/words/{word_id}/assess
 *   }
 *
 * 权限：需 android.permission.RECORD_AUDIO，由调用方先确认已授予。
 *
 * 失败兜底：
 *   - AudioRecord 初始化失败（权限拒绝/硬件不可用）→ start() 返回 false
 *   - stop() 保证 audioRecord.release() 调用，不漏资源
 */
class AudioRecorder {
    private var audioRecord: AudioRecord? = null
    private val recorded = ByteArrayOutputStream()
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    val isRecording: Boolean
        get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING

    /** 启动录音。成功 true；权限拒绝/初始化失败 false。 */
    fun start(): Boolean {
        if (isRecording) return false
        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) return false
        val bufferSize = minBuf.coerceAtLeast(3200)
        try {
            @Suppress("MissingPermission")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize,
            )
        } catch (e: SecurityException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release()
            audioRecord = null
            return false
        }
        synchronized(recorded) { recorded.reset() }
        audioRecord?.startRecording()
        recordJob = scope.launch {
            val buf = ShortArray(1024)
            while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: -1
                if (n > 0) {
                    synchronized(recorded) {
                        for (i in 0 until n) {
                            val v = buf[i].toInt()
                            recorded.write(v and 0xFF)
                            recorded.write((v ushr 8) and 0xFF)
                        }
                    }
                }
            }
        }
        return true
    }

    /** 停止录音 + 返回累积 PCM bytes（16kHz 16bit mono）。 */
    fun stop(): ByteArray {
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
            // 未在 RECORDING 状态调 stop，忽略
        }
        audioRecord?.release()
        audioRecord = null
        return synchronized(recorded) { recorded.toByteArray() }
    }

    /** 释放资源（ disposable 时调用）。 */
    fun destroy() {
        stop()
    }
}
