package com.example.watcher.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.watcher.data.model.LiveSpeechState
import com.example.watcher.data.model.SpeechTranscriptEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class LiveSpeechRecognitionManager(
    private val context: Context,
    private val memoryManager: CommentaryMemoryManager
) {
    companion object {
        private const val TAG = "LiveSpeech"
        private const val MAX_TRANSCRIPTS = 30
        private const val SAMPLE_RATE = 16000
        private const val WS_BASE = "wss://office-api-ast-dx.iflyaisol.com/ast/communicate/v1"

        // iFlytek credentials
        private const val APP_ID = "1a62b808"
        private const val ACCESS_KEY_ID = "dad59fee22d468c1261fe0ee11cce64d"
        private const val ACCESS_KEY_SECRET = "NjU3YThhMGNkNDdhYzUyZmE0NThhODlj"

        // Audio: send 1280 bytes every 40ms (as per doc)
        private const val CHUNK_BYTES = 1280
        private const val SEND_INTERVAL_MS = 40L
    }

    private val _state = MutableStateFlow(LiveSpeechState())
    val state: StateFlow<LiveSpeechState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val idCounter = AtomicLong(0)

    /** Callback for event-driven triggers (e.g., to wake AI audiences) */
    var onSpeechResult: ((String) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    @Volatile private var audioLoopRunning = false // prevent duplicate audio threads

    fun start() {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
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

        isRecording = true
        _state.value = LiveSpeechState(isActive = true, isMicEnabled = true)
        audioRecord!!.startRecording()
        Thread { sendAudioLoop() }.start()
        connectWebSocket()
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        Log.d(TAG, "Stopping speech recognition")

        // Send end signal
        try {
            webSocket?.send("{\"end\": true}")
        } catch (_: Exception) {}
        webSocket?.close(1000, "stopped")
        webSocket = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        _state.value = LiveSpeechState()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    fun setMicEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isMicEnabled = enabled)
    }

    fun getFinalTranscripts(): List<Pair<Long, String>> =
        _state.value.transcripts.filter { it.isFinal }.map { it.timestamp to it.text }

    // ---- WebSocket connection ----

    private fun connectWebSocket() {
        val url = buildWsUrl()
        Log.d(TAG, "Connecting WebSocket...")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                _state.value = _state.value.copy(isListening = true, errorMessage = null)
                // Audio loop already running from start(), no need to restart
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _state.value = _state.value.copy(
                    isListening = false,
                    errorMessage = "连接失败: ${t.message}"
                )
                // Auto-reconnect after 2s
                if (isRecording) {
                    Thread.sleep(2000)
                    if (isRecording) connectWebSocket()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                _state.value = _state.value.copy(isListening = false)
                // Reconnect if still recording
                if (isRecording) {
                    Thread.sleep(1000)
                    if (isRecording) connectWebSocket()
                }
            }
        })
    }

    // ---- Audio capture & send ----

    private fun initAudioRecord() {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            channelConfig,
            audioFormat,
            maxOf(bufferSize * 2, CHUNK_BYTES * 4)
        )
        Log.d(TAG, "AudioRecord created")
    }

    private fun sendAudioLoop() {
        if (audioLoopRunning) {
            Log.w(TAG, "Audio loop already running, skipping")
            return
        }
        audioLoopRunning = true
        val buffer = ByteArray(CHUNK_BYTES)
        Log.d(TAG, "Audio send loop started")

        try {
            while (isRecording) {
                val ret = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (ret <= 0) {
                    Thread.sleep(SEND_INTERVAL_MS)
                    continue
                }

                if (_state.value.isMicEnabled) {
                    // WebSocket may be null during reconnection — just drop the data
                    try {
                        webSocket?.send(buffer.toByteString(0, ret))
                    } catch (_: Exception) {
                        // WebSocket closed, data dropped; reconnect will provide a new one
                    }
                }

                Thread.sleep(SEND_INTERVAL_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio send error", e)
        }
        audioLoopRunning = false
        Log.d(TAG, "Audio send loop ended")
    }

    // ---- Message handling ----

    private fun handleMessage(json: String) {
        try {
            val obj = JSONObject(json)
            val msgType = obj.optString("msg_type", obj.optString("action", ""))

            when (msgType) {
                "started" -> {
                    Log.d(TAG, "Session started")
                }
                "result" -> {
                    val resType = obj.optString("res_type", "")
                    if (resType == "asr") {
                        parseAsrResult(obj)
                    }
                }
                "error" -> {
                    val desc = obj.optString("desc", "未知错误")
                    Log.e(TAG, "Server error: $desc")
                    _state.value = _state.value.copy(errorMessage = desc)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse message error: ${e.message}, json=$json")
        }
    }

    private fun parseAsrResult(obj: JSONObject) {
        val data = obj.optJSONObject("data") ?: return
        val cn = data.optJSONObject("cn") ?: return
        val st = cn.optJSONObject("st") ?: return
        val type = st.optString("type", "1") // 0=final, 1=partial
        val rtArray = st.optJSONArray("rt") ?: return

        val sb = StringBuilder()
        for (i in 0 until rtArray.length()) {
            val rt = rtArray.optJSONObject(i) ?: continue
            val wsArray = rt.optJSONArray("ws") ?: continue
            for (j in 0 until wsArray.length()) {
                val ws = wsArray.optJSONObject(j) ?: continue
                val cwArray = ws.optJSONArray("cw") ?: continue
                for (k in 0 until cwArray.length()) {
                    val cw = cwArray.optJSONObject(k) ?: continue
                    val w = cw.optString("w", "")
                    val wp = cw.optString("wp", "n")
                    if (wp != "g") { // skip segment markers
                        sb.append(w)
                    }
                }
            }
        }

        val raw = sb.toString().trim()
        if (raw.isBlank()) return

        // Leading punctuation belongs to the previous segment's end
        val leadingPunc = raw.takeWhile { it in "，。？！、,.\u3002?!" }
        val text = raw.drop(leadingPunc.length)

        if (leadingPunc.isNotEmpty()) {
            appendToPreviousResult(leadingPunc)
        }
        if (text.isBlank()) return

        if (type == "0") {
            // Final result for this segment
            Log.d(TAG, "Final: '$text'")
            onResult(text)
        } else {
            // Partial result
            _state.value = _state.value.copy(isListening = true)
        }
    }

    // ---- URL & Signature ----

    private fun buildWsUrl(): String {
        val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        utcFormat.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val utc = utcFormat.format(Date())
        val uuid = UUID.randomUUID().toString()

        val params = sortedMapOf(
            "accessKeyId" to ACCESS_KEY_ID,
            "appId" to APP_ID,
            "audio_encode" to "pcm_s16le",
            "lang" to "autodialect",
            "samplerate" to SAMPLE_RATE.toString(),
            "utc" to utc,
            "uuid" to uuid,
            "eng_vad_mdn" to "1", // far-field
        )

        // Build baseString for signature
        val baseString = params.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }

        // HmacSHA1 signature
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(ACCESS_KEY_SECRET.toByteArray(), "HmacSHA1"))
        val signBytes = mac.doFinal(baseString.toByteArray())
        val signature = Base64.encodeToString(signBytes, Base64.NO_WRAP)

        // Build final URL
        val allParams = params.toMutableMap()
        allParams["signature"] = signature

        val queryString = allParams.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }

        val url = "$WS_BASE?$queryString"
        Log.d(TAG, "WS URL built, baseString length=${baseString.length}")
        return url
    }

    private fun urlEncode(s: String): String =
        URLEncoder.encode(s, "UTF-8")

    // ---- Result handling ----

    private fun appendToPreviousResult(punc: String) {
        val cur = _state.value
        val transcripts = cur.transcripts.toMutableList()
        if (transcripts.isNotEmpty()) {
            val prev = transcripts[0]
            transcripts[0] = prev.copy(text = prev.text + punc)
            _state.value = cur.copy(transcripts = transcripts)
        }
    }

    private fun onResult(text: String) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        val entry = SpeechTranscriptEntry(
            id = idCounter.incrementAndGet(), text = text,
            timestamp = now, displayTimestamp = timeFormat.format(Date(now)), isFinal = true
        )
        Log.d(TAG, "Recognized: $text")

        val cur = _state.value
        val transcripts = (listOf(entry) + cur.transcripts.filter { it.isFinal }).take(MAX_TRANSCRIPTS)
        _state.value = cur.copy(transcripts = transcripts, isListening = true, errorMessage = null)

        scope.launch { memoryManager.onNewSpeech(text) }
        onSpeechResult?.invoke(text)
    }
}
