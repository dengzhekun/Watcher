package com.example.watcher.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.watcher.BuildConfig
import com.example.watcher.data.model.LiveSpeechState
import com.example.watcher.data.model.SpeechTranscriptEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal class VolcengineLiveSpeechRecognizer(
    private val context: Context,
    private val memoryManager: CommentaryMemoryManager
) : LiveSpeechRecognizer {
    companion object {
        private const val TAG = "LiveSpeech"
        private const val MAX_TRANSCRIPTS = 30
        private const val PARTIAL_ENTRY_ID = Long.MIN_VALUE
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16
        private const val CHUNK_DURATION_MS = 200L
        private const val IDLE_AUDIO_POLL_DELAY_MS = 40L
        private const val CHUNK_BYTES = ((SAMPLE_RATE * CHUNK_DURATION_MS) / 1000L * 2L).toInt()
    }

    private val _state = MutableStateFlow(LiveSpeechState())
    override val state: StateFlow<LiveSpeechState> = _state.asStateFlow()

    override var onSpeechResult: ((String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val asrConfigRepository = AsrConfigRepository(context)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val idCounter = AtomicLong(0)
    private val committedUtterances = LinkedHashSet<String>()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile private var isRecording = false
    @Volatile private var socketOpen = false
    @Volatile private var sessionConfirmed = false

    override fun start() {
        if (isRecording) {
            return
        }

        val credentials = resolveAsrCredentials()
        if (!credentials.isConfigured()) {
            _state.value = LiveSpeechState(
                isActive = true,
                errorMessage = "未配置火山流式 ASR 凭据。请先在 API 钱包的语音识别配置中填写，或在开发期 local.properties 中提供 VOLCENGINE_ASR_APP_KEY、VOLCENGINE_ASR_ACCESS_KEY、VOLCENGINE_ASR_RESOURCE_ID。"
            )
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = LiveSpeechState(isActive = true, errorMessage = "缺少麦克风权限")
            return
        }

        try {
            initAudioRecord()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord init failed", e)
            _state.value = LiveSpeechState(isActive = true, errorMessage = "录音初始化失败")
            return
        }

        synchronized(committedUtterances) {
            committedUtterances.clear()
        }
        socketOpen = false
        sessionConfirmed = false
        isRecording = true
        _state.value = LiveSpeechState(isActive = true, isMicEnabled = true)

        audioRecord?.startRecording()
        audioJob = scope.launch(Dispatchers.IO) {
            sendAudioLoop()
        }
        connectWebSocket(credentials)
    }

    override fun stop() {
        if (!isRecording && webSocket == null && audioRecord == null) {
            _state.value = LiveSpeechState()
            return
        }

        isRecording = false
        socketOpen = false
        sessionConfirmed = false
        reconnectJob?.cancel()
        reconnectJob = null
        audioJob?.cancel()
        audioJob = null

        try {
            webSocket?.send(
                VolcengineAsrWireProtocol.encodeAudioRequest(ByteArray(0), isLast = true).toByteString()
            )
        } catch (_: Exception) {
        }

        try {
            webSocket?.close(1000, "stopped")
        } catch (_: Exception) {
        }
        webSocket = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        _state.value = LiveSpeechState()
    }

    override fun release() {
        stop()
        scope.cancel()
    }

    override fun setMicEnabled(enabled: Boolean) {
        _state.update { it.copy(isMicEnabled = enabled) }
    }

    override fun getFinalTranscripts(): List<Pair<Long, String>> {
        return _state.value.transcripts
            .filter { it.isFinal }
            .map { it.timestamp to it.text }
    }

    private fun connectWebSocket(credentials: VolcengineAsrCredentials) {
        val connectId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url(VOLCENGINE_ASR_WS_URL)
            .header("X-Api-App-Key", credentials.appKey)
            .header("X-Api-Access-Key", credentials.accessKey)
            .header("X-Api-Resource-Id", credentials.resourceId)
            .header("X-Api-Connect-Id", connectId)
            .build()

        Log.d(TAG, "Connecting Volcengine ASR WebSocket, connectId=$connectId")

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val logId = response.header("X-Tt-Logid").orEmpty()
                Log.d(TAG, "Volcengine ASR connected, connectId=$connectId, logId=$logId")
                socketOpen = true
                sessionConfirmed = false
                _state.update { it.copy(isListening = false, errorMessage = null) }
                sendInitRequest(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleBinaryMessage(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.w(TAG, "Unexpected text frame from Volcengine ASR: ${text.take(160)}")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socketOpen = false
                sessionConfirmed = false
                val logId = response?.header("X-Tt-Logid").orEmpty()
                val mappedError = mapAsrNetworkError(t)
                Log.e(TAG, "Volcengine ASR failure, connectId=$connectId, logId=$logId: $mappedError", t)
                _state.update { it.copy(
                    isListening = false,
                    errorMessage = buildString {
                        append(mappedError)
                        if (logId.isNotBlank()) {
                            append("（logId=")
                            append(logId)
                            append("）")
                        }
                    }
                ) }
                if (isRecording) {
                    scheduleReconnect(credentials)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socketOpen = false
                sessionConfirmed = false
                Log.d(TAG, "Volcengine ASR closed, connectId=$connectId, code=$code, reason=$reason")
                _state.update { it.copy(isListening = false) }
                if (isRecording) {
                    scheduleReconnect(credentials)
                }
            }
        })
    }

    private fun scheduleReconnect(credentials: VolcengineAsrCredentials) {
        if (!isRecording) {
            return
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(1_500)
            if (isRecording) {
                connectWebSocket(credentials)
            }
        }
    }

    private fun initAudioRecord() {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)
        require(bufferSize > 0) { "Invalid AudioRecord buffer size: $bufferSize" }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            channelConfig,
            audioFormat,
            maxOf(bufferSize * 2, CHUNK_BYTES * 2)
        )
        require(audioRecord?.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord was not initialized." }
    }

    private suspend fun sendAudioLoop() {
        val buffer = ByteArray(CHUNK_BYTES)
        while (isRecording) {
            val bytesRead = try {
                audioRecord?.read(buffer, 0, buffer.size) ?: -1
            } catch (e: Exception) {
                Log.e(TAG, "Audio read failed", e)
                -1
            }

            if (bytesRead <= 0) {
                delay(IDLE_AUDIO_POLL_DELAY_MS)
                continue
            }

            if (_state.value.isMicEnabled && socketOpen) {
                try {
                    val frame = VolcengineAsrWireProtocol.encodeAudioRequest(
                        audioPayload = buffer.copyOf(bytesRead),
                        isLast = false
                    )
                    webSocket?.send(frame.toByteString())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send ASR audio frame: ${e.message}")
                }
            }
        }
    }

    private fun sendInitRequest(socket: WebSocket) {
        val frame = VolcengineAsrWireProtocol.encodeInitRequest(
            clientInfo = buildClientInfo(),
            sampleRate = SAMPLE_RATE,
            bitsPerSample = BITS_PER_SAMPLE,
            channelCount = CHANNEL_COUNT
        )
        val sent = socket.send(frame.toByteString())
        if (!sent) {
            socketOpen = false
            _state.update { it.copy(
                isListening = false,
                errorMessage = "火山语音初始化失败：请求未成功发送。"
            ) }
        }
    }

    private fun buildClientInfo(): VolcengineAsrWireProtocol.ClientInfo {
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

        return VolcengineAsrWireProtocol.ClientInfo(
            uid = context.packageName,
            deviceId = Build.MODEL ?: "android",
            platform = "Android ${Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT}",
            appVersion = appVersion
        )
    }

    private fun handleBinaryMessage(frameBytes: ByteArray) {
        val frame = try {
            VolcengineAsrWireProtocol.decode(frameBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode Volcengine ASR frame", e)
            _state.update { it.copy(errorMessage = "火山语音返回了无法解析的数据包") }
            return
        }

        when (frame.messageType) {
            VolcengineAsrWireProtocol.MESSAGE_TYPE_FULL_SERVER_RESPONSE -> {
                if (frame.payload.isEmpty()) {
                    return
                }
                parseServerResponse(frame)
            }

            VolcengineAsrWireProtocol.MESSAGE_TYPE_ERROR_RESPONSE -> {
                socketOpen = false
                sessionConfirmed = false
                val message = parseServerError(frame)
                _state.update { it.copy(
                    isListening = false,
                    errorMessage = message
                ) }
                if (isRecording) {
                    webSocket?.cancel()
                }
            }

            else -> Log.w(TAG, "Ignoring unsupported ASR message type: ${frame.messageType}")
        }
    }

    private fun parseServerResponse(frame: VolcengineAsrWireProtocol.DecodedFrame) {
        val payload = try {
            VolcengineAsrWireProtocol.parseResponsePayload(frame.payloadText)
        } catch (e: Exception) {
            Log.w(TAG, "Volcengine ASR payload is not valid JSON: ${frame.payloadText.take(160)}")
            return
        }

        if (!VolcengineAsrWireProtocol.isSuccessCode(payload.code)) {
            socketOpen = false
            sessionConfirmed = false
            val message = VolcengineAsrWireProtocol.extractResponseMessage(
                payload = payload,
                fallbackCode = payload.code
            )
            _state.update { it.copy(
                isListening = false,
                errorMessage = message
            ) }
            if (isRecording) {
                webSocket?.cancel()
            }
            return
        }

        sessionConfirmed = true

        val result = payload.result ?: run {
            _state.update { it.copy(isListening = true, errorMessage = null) }
            return
        }

        val utterances = result.optJSONArray("utterances")
        var latestPartialText: String? = null
        var committedAny = false

        if (utterances != null) {
            for (i in 0 until utterances.length()) {
                val utterance = utterances.optJSONObject(i) ?: continue
                val text = utterance.optString("text").trim()
                if (text.isBlank()) {
                    continue
                }
                val definite = utterance.optBoolean("definite", false)
                if (definite) {
                    val key = buildUtteranceKey(utterance, text)
                    if (rememberCommittedUtterance(key)) {
                        onFinalResult(text)
                        committedAny = true
                    }
                } else {
                    latestPartialText = text
                }
            }
        }

        val resultText = result.optString("text").trim()
        if (!committedAny && latestPartialText.isNullOrBlank() && frame.isLastPacket && resultText.isNotBlank()) {
            val fallbackKey = "final:${frame.sequence}:${resultText}"
            if (rememberCommittedUtterance(fallbackKey)) {
                onFinalResult(resultText)
                committedAny = true
            }
        }

        if (!latestPartialText.isNullOrBlank()) {
            updatePartialTranscript(latestPartialText)
        } else if (committedAny) {
            removePartialTranscript()
        } else if (resultText.isNotBlank()) {
            updatePartialTranscript(resultText)
        }

        _state.update { it.copy(isListening = true, errorMessage = null) }
    }

    private fun parseServerError(frame: VolcengineAsrWireProtocol.DecodedFrame): String {
        val rawText = frame.payloadText
        val parsedMessage = runCatching {
            val payload = VolcengineAsrWireProtocol.parseResponsePayload(rawText)
            VolcengineAsrWireProtocol.extractResponseMessage(payload, frame.errorCode)
        }.getOrDefault(rawText)
        val code = frame.errorCode ?: -1
        return "火山语音识别错误($code): ${parsedMessage.ifBlank { "未知错误" }}"
    }

    private fun buildUtteranceKey(utterance: JSONObject, text: String): String {
        val start = utterance.optInt("start_time", -1)
        val end = utterance.optInt("end_time", -1)
        return "$start:$end:$text"
    }

    private fun rememberCommittedUtterance(key: String): Boolean {
        synchronized(committedUtterances) {
            if (committedUtterances.contains(key)) {
                return false
            }
            committedUtterances += key
            if (committedUtterances.size > 128) {
                val iterator = committedUtterances.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            return true
        }
    }

    private fun updatePartialTranscript(text: String) {
        if (text.isBlank()) {
            removePartialTranscript()
            return
        }

        val now = System.currentTimeMillis()
        val partialEntry = SpeechTranscriptEntry(
            id = PARTIAL_ENTRY_ID,
            text = text,
            timestamp = now,
            displayTimestamp = timeFormat.format(Date(now)),
            isFinal = false
        )
        _state.update { current ->
            val finals = current.transcripts.filter { it.isFinal }.take(MAX_TRANSCRIPTS - 1)
            current.copy(
                transcripts = listOf(partialEntry) + finals,
                isListening = sessionConfirmed,
                errorMessage = null
            )
        }
    }

    private fun removePartialTranscript() {
        _state.update { current ->
            val finals = current.transcripts.filter { it.isFinal }.take(MAX_TRANSCRIPTS)
            if (finals.size == current.transcripts.size) current
            else current.copy(transcripts = finals)
        }
    }

    private fun onFinalResult(text: String) {
        if (text.isBlank()) {
            return
        }

        val now = System.currentTimeMillis()
        val entry = SpeechTranscriptEntry(
            id = idCounter.incrementAndGet(),
            text = text,
            timestamp = now,
            displayTimestamp = timeFormat.format(Date(now)),
            isFinal = true
        )
        _state.update { current ->
            val transcripts = (listOf(entry) + current.transcripts.filter { it.isFinal }).take(MAX_TRANSCRIPTS)
            current.copy(
                transcripts = transcripts,
                isListening = sessionConfirmed,
                errorMessage = null
            )
        }

        scope.launch {
            memoryManager.onNewSpeech(text)
        }
        onSpeechResult?.invoke(text)
    }

    private fun resolveAsrCredentials(): VolcengineAsrCredentials {
        return asrConfigRepository.resolveRuntimeCredentials(
            fallback = VolcengineAsrCredentials(
                appKey = BuildConfig.VOLCENGINE_ASR_APP_KEY,
                accessKey = BuildConfig.VOLCENGINE_ASR_ACCESS_KEY,
                resourceId = BuildConfig.VOLCENGINE_ASR_RESOURCE_ID
            )
        )
    }
}
