package com.example.watcher.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val ASR_CONFIG_PREFS = "asr_config_prefs"
private const val KEY_ASR_TEST_STATUS = "asr_test_status"
private const val KEY_ASR_TEST_TIME = "asr_test_time"
private const val KEY_ASR_TEST_MESSAGE = "asr_test_message"
internal const val VOLCENGINE_ASR_WS_URL = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_async"
internal const val DEFAULT_DOUBAO_STREAMING_ASR_RESOURCE_ID = "volc.bigasr.sauc.duration"

enum class AsrConfigSource {
    Wallet,
    LegacyRuntime,
    BuildConfigFallback,
    Missing
}

enum class AsrConnectivityStatus {
    Untested,
    Verified,
    Failed
}

data class AsrConnectivitySnapshot(
    val status: AsrConnectivityStatus = AsrConnectivityStatus.Untested,
    val lastTestedAt: Long? = null,
    val message: String? = null
)

internal data class ResolvedAsrConfig(
    val credentials: VolcengineAsrCredentials = VolcengineAsrCredentials(),
    val source: AsrConfigSource = AsrConfigSource.Missing,
    val connectivity: AsrConnectivitySnapshot = AsrConnectivitySnapshot()
)

internal fun resolveEditorAsrConfig(
    stored: VolcengineAsrCredentials,
    legacy: VolcengineAsrCredentials,
    fallback: VolcengineAsrCredentials,
    connectivity: AsrConnectivitySnapshot = AsrConnectivitySnapshot()
): ResolvedAsrConfig {
    if (stored.isConfigured()) {
        return ResolvedAsrConfig(
            credentials = stored,
            source = AsrConfigSource.Wallet,
            connectivity = connectivity
        )
    }

    if (legacy.appKey.isNotBlank() || legacy.accessKey.isNotBlank()) {
        return ResolvedAsrConfig(
            credentials = legacy.merge(fallback),
            source = AsrConfigSource.LegacyRuntime,
            connectivity = connectivity
        )
    }

    if (fallback.isConfigured()) {
        return ResolvedAsrConfig(
            credentials = fallback,
            source = AsrConfigSource.BuildConfigFallback,
            connectivity = connectivity
        )
    }

    return ResolvedAsrConfig(connectivity = connectivity)
}

internal fun resolveRuntimeAsrCredentials(
    stored: VolcengineAsrCredentials,
    fallback: VolcengineAsrCredentials
): VolcengineAsrCredentials {
    return when {
        stored.isConfigured() -> stored
        fallback.isConfigured() -> fallback
        else -> stored.merge(fallback)
    }
}

internal fun buildValidationHandshakeFrames(
    clientInfo: VolcengineAsrWireProtocol.ClientInfo,
    sampleRate: Int,
    bitsPerSample: Int,
    channelCount: Int,
    audioChunkBytes: Int
): List<ByteArray> {
    return listOf(
        VolcengineAsrWireProtocol.encodeInitRequest(
            clientInfo = clientInfo,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            channelCount = channelCount
        ),
        VolcengineAsrWireProtocol.encodeAudioRequest(
            audioPayload = ByteArray(audioChunkBytes),
            isLast = false
        ),
        VolcengineAsrWireProtocol.encodeAudioRequest(
            audioPayload = ByteArray(0),
            isLast = true
        )
    )
}

internal class AsrConfigRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(ASR_CONFIG_PREFS, Context.MODE_PRIVATE)
    private val secretStore = AppRuntimeSecretStore(appContext)

    fun resolveConfig(
        fallback: VolcengineAsrCredentials = VolcengineAsrCredentials()
    ): ResolvedAsrConfig {
        return resolveEditorAsrConfig(
            stored = secretStore.getStoredVolcengineAsrCredentials(),
            legacy = secretStore.getLegacySpeechCredentials(),
            fallback = fallback,
            connectivity = getConnectivitySnapshot()
        )
    }

    fun resolveRuntimeCredentials(
        fallback: VolcengineAsrCredentials = VolcengineAsrCredentials()
    ): VolcengineAsrCredentials {
        return resolveRuntimeAsrCredentials(
            stored = secretStore.getStoredVolcengineAsrCredentials(),
            fallback = fallback
        )
    }

    fun saveCredentials(credentials: VolcengineAsrCredentials) {
        secretStore.putVolcengineAsrCredentials(credentials)
    }

    fun clearCredentials() {
        secretStore.clearVolcengineAsrCredentials()
    }

    fun getConnectivitySnapshot(): AsrConnectivitySnapshot {
        val statusValue = prefs.getString(KEY_ASR_TEST_STATUS, null)
        val status = statusValue?.let {
            runCatching { AsrConnectivityStatus.valueOf(it) }.getOrNull()
        } ?: AsrConnectivityStatus.Untested
        val testedAt = prefs.getLong(KEY_ASR_TEST_TIME, 0L).takeIf { it > 0L }
        val message = prefs.getString(KEY_ASR_TEST_MESSAGE, null)?.takeIf { it.isNotBlank() }
        return AsrConnectivitySnapshot(
            status = status,
            lastTestedAt = testedAt,
            message = message
        )
    }

    fun setConnectivitySnapshot(
        status: AsrConnectivityStatus,
        message: String?,
        testedAt: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putString(KEY_ASR_TEST_STATUS, status.name)
            .putLong(KEY_ASR_TEST_TIME, testedAt)
            .putString(KEY_ASR_TEST_MESSAGE, message.orEmpty())
            .apply()
    }

    fun clearConnectivitySnapshot() {
        prefs.edit()
            .remove(KEY_ASR_TEST_STATUS)
            .remove(KEY_ASR_TEST_TIME)
            .remove(KEY_ASR_TEST_MESSAGE)
            .apply()
    }

    suspend fun testCredentials(credentials: VolcengineAsrCredentials): String {
        require(credentials.isConfigured()) {
            "请先填写完整的 App Key、Access Key 和 Resource ID。"
        }

        return withTimeout(10_000L) {
            suspendCancellableCoroutine { continuation ->
                val client = OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build()

                var socket: WebSocket? = null
                var completed = false
                var logId: String? = null

                fun finish(block: () -> Unit) {
                    if (completed) return
                    completed = true
                    try {
                        block()
                    } finally {
                        client.dispatcher.executorService.shutdown()
                        client.connectionPool.evictAll()
                    }
                }

                val connectId = UUID.randomUUID().toString()
                val request = Request.Builder()
                    .url(VOLCENGINE_ASR_WS_URL)
                    .header("X-Api-App-Key", credentials.appKey)
                    .header("X-Api-Access-Key", credentials.accessKey)
                    .header("X-Api-Resource-Id", credentials.resourceId)
                    .header("X-Api-Connect-Id", connectId)
                    .build()

                socket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        logId = response.header("X-Tt-Logid")
                        if (!sendValidationHandshake(webSocket)) {
                            finish {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IllegalStateException("火山语音验证失败：初始化或首个音频包未成功发送。")
                                    )
                                }
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                        val frame = runCatching {
                            VolcengineAsrWireProtocol.decode(bytes.toByteArray())
                        }.getOrElse { error ->
                            finish {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(
                                        IllegalStateException("火山语音返回了无法解析的数据包", error)
                                    )
                                }
                            }
                            return
                        }

                        when (frame.messageType) {
                            VolcengineAsrWireProtocol.MESSAGE_TYPE_FULL_SERVER_RESPONSE -> {
                                if (frame.payload.isEmpty()) {
                                    return
                                }
                                val parsed = runCatching {
                                    VolcengineAsrWireProtocol.parseResponsePayload(frame.payloadText)
                                }.getOrElse { error ->
                                    finish {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                IllegalStateException(
                                                    "火山语音返回了无法解析的初始化响应",
                                                    error
                                                )
                                            )
                                        }
                                    }
                                    return
                                }

                                if (!VolcengineAsrWireProtocol.isSuccessCode(parsed.code)) {
                                    finish {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                IllegalStateException(
                                                    VolcengineAsrWireProtocol.extractResponseMessage(
                                                        payload = parsed,
                                                        fallbackCode = parsed.code
                                                    )
                                                )
                                            )
                                        }
                                    }
                                    return
                                }

                                finish {
                                    webSocket.close(1000, "wallet_test_complete")
                                    if (continuation.isActive) {
                                        continuation.resume(
                                            if (logId.isNullOrBlank()) {
                                                "初始化成功，已接受测试音频。"
                                            } else {
                                                "初始化成功，已接受测试音频，logId=$logId"
                                            }
                                        )
                                    }
                                }
                            }

                            VolcengineAsrWireProtocol.MESSAGE_TYPE_ERROR_RESPONSE -> {
                                finish {
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(
                                            IllegalStateException(parseErrorFrameMessage(frame))
                                        )
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        val mapped = mapAsrNetworkError(t)
                        val detail = buildString {
                            append(mapped)
                            response?.header("X-Tt-Logid")?.takeIf { it.isNotBlank() }?.let {
                                append("，logId=")
                                append(it)
                            }
                        }
                        finish {
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException(detail, t))
                            }
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        finish {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    IllegalStateException(
                                        "火山语音验证连接已关闭（code=$code, reason=${reason.ifBlank { "unknown" }}），未收到有效响应。"
                                    )
                                )
                            }
                        }
                    }
                })

                continuation.invokeOnCancellation {
                    socket?.cancel()
                    client.dispatcher.executorService.shutdown()
                    client.connectionPool.evictAll()
                }
            }
        }
    }

    private fun buildClientInfo(): VolcengineAsrWireProtocol.ClientInfo {
        val appVersion = packageVersionName()
        return VolcengineAsrWireProtocol.ClientInfo(
            uid = appContext.packageName,
            deviceId = Build.MODEL ?: "android",
            platform = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}",
            appVersion = appVersion
        )
    }

    private fun packageVersionName(): String {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            }
        }.getOrNull()?.versionName.orEmpty()
    }

    private fun parseErrorFrameMessage(frame: VolcengineAsrWireProtocol.DecodedFrame): String {
        val parsed = runCatching {
            VolcengineAsrWireProtocol.parseResponsePayload(frame.payloadText)
        }.getOrNull()
        return if (parsed != null) {
            "火山语音识别错误(${frame.errorCode ?: -1}): ${
                VolcengineAsrWireProtocol.extractResponseMessage(parsed, frame.errorCode)
            }"
        } else {
            "火山语音识别错误(${frame.errorCode ?: -1}): ${frame.payloadText.ifBlank { "未知错误" }}"
        }
    }

    private fun sendValidationHandshake(socket: WebSocket): Boolean {
        return buildValidationHandshakeFrames(
            clientInfo = buildClientInfo(),
            sampleRate = TEST_SAMPLE_RATE,
            bitsPerSample = TEST_BITS_PER_SAMPLE,
            channelCount = TEST_CHANNEL_COUNT,
            audioChunkBytes = TEST_AUDIO_CHUNK_BYTES
        )
            .all { frame -> socket.send(frame.toByteString()) }
    }

    private companion object {
        const val TEST_SAMPLE_RATE = 16000
        const val TEST_BITS_PER_SAMPLE = 16
        const val TEST_CHANNEL_COUNT = 1
        const val TEST_AUDIO_CHUNK_DURATION_MS = 200L
        const val TEST_AUDIO_CHUNK_BYTES =
            ((TEST_SAMPLE_RATE * TEST_AUDIO_CHUNK_DURATION_MS) / 1000L * 2L).toInt()
    }
}
