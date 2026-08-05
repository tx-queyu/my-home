package com.myhome.admin

import android.content.Context
import android.os.Build
import com.myhome.BuildConfig
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.LocalServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ActivationResult(
    val ok: Boolean,
    val message: String,
    val detailCode: String? = null,
)

class AdbActivator(private val context: Context) {

    suspend fun pairAndActivate(host: String, port: Int, pairingCode: String): ActivationResult =
        withContext(Dispatchers.IO) {
            val manager = try {
                MyAdbConnectionManager.getInstance(context)
            } catch (t: Throwable) {
                return@withContext ActivationResult(
                    ok = false,
                    message = "初始化 ADB 失败：${t.message ?: t.javaClass.simpleName}",
                    detailCode = "adb_init_failed",
                )
            }
            manager.setApi(Build.VERSION.SDK_INT)
            manager.setTimeout(15, TimeUnit.SECONDS)

            val paired = try {
                manager.pair(host, port, pairingCode)
            } catch (t: Throwable) {
                return@withContext ActivationResult(
                    ok = false,
                    message = "配对失败：${t.message ?: t.javaClass.simpleName}。请确认配对码和端口未过期、设备与手机同 WiFi。",
                    detailCode = "adb_pair_failed",
                )
            }
            if (!paired) {
                return@withContext ActivationResult(
                    ok = false,
                    message = "配对失败，配对码或端口错误。请到平板「无线调试」重新生成配对码。",
                    detailCode = "adb_pair_rejected",
                )
            }

            val connected = try {
                manager.autoConnect(context, 5000) || manager.connect(host, 5555)
            } catch (t: AdbPairingRequiredException) {
                return@withContext ActivationResult(
                    ok = false,
                    message = "配对成功但未授权连接，请等待平板授权后重试。",
                    detailCode = "adb_unauthorized",
                )
            } catch (t: Throwable) {
                return@withContext ActivationResult(
                    ok = false,
                    message = "连接失败：${t.message ?: t.javaClass.simpleName}。",
                    detailCode = "adb_connect_failed",
                )
            }
            if (!connected) {
                return@withContext ActivationResult(
                    ok = false,
                    message = "配对成功但无法自动连接，请稍后重试。",
                    detailCode = "adb_connect_failed",
                )
            }

            val command = "dpm set-device-owner " +
                "${BuildConfig.APPLICATION_ID}/${MyDeviceAdminReceiver::class.java.name}"
            val output = try {
                runShellWithTimeout(manager, command)
            } catch (t: Throwable) {
                try { manager.close() } catch (_: Throwable) {}
                return@withContext ActivationResult(
                    ok = false,
                    message = "执行激活命令失败：${t.message ?: t.javaClass.simpleName}",
                    detailCode = "adb_shell_failed",
                )
            }
            try { manager.close() } catch (_: Throwable) {}

            val ok = output.contains("Success", ignoreCase = true) ||
                output.contains("Already set", ignoreCase = true)
            if (ok) {
                ActivationResult(ok = true, message = "激活成功，平板已是 Device Owner。")
            } else {
                ActivationResult(
                    ok = false,
                    message = "激活命令返回：${output.take(200).ifBlank { "(无输出)" }}\n" +
                        "常见原因：平板上还有未移除的 Google 账号、设备已设置过其它 Device Owner、或出厂未完成。",
                    detailCode = "dpm_set_failed",
                )
            }
        }

    private suspend fun runShellWithTimeout(
        manager: MyAdbConnectionManager,
        command: String,
    ): String = withTimeoutOrNull(15_000) {
        val stream = manager.openStream(LocalServices.SHELL)
        stream.use { s ->
            val output = StringBuilder()
            val input = BufferedReader(InputStreamReader(s.openInputStream()))
            s.openOutputStream().use { out ->
                out.write("$command\n".toByteArray())
                out.flush()
                out.write("exit\n".toByteArray())
                out.flush()
            }
            while (true) {
                val line = input.readLine() ?: break
                output.append(line).append('\n')
            }
            output.toString()
        }
    } ?: throw java.io.IOException("命令执行超时")
}
