package com.example.watcher.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import com.example.watcher.agentframework.service.AgentFrameworkService
import com.example.watcher.data.gateway.GatewayEvent
import com.example.watcher.data.gateway.GatewayServer
import com.example.watcher.data.gateway.GatewayServiceAnnouncer
import com.example.watcher.data.gateway.GatewayTaskManager
import com.example.watcher.data.gateway.GatewayTaskStatus
import com.example.watcher.data.model.BaselineSource
import com.example.watcher.data.model.IntentResult
import com.example.watcher.data.model.MonitorMode
import com.example.watcher.data.model.TargetTrigger
import com.example.watcher.data.model.VideoProcessTaskDraft
import com.example.watcher.data.model.VideoRunStatus
import com.example.watcher.data.repository.HistoryRepository
import com.example.watcher.data.repository.IntentRepository
import com.example.watcher.data.repository.LiveCommentaryRepository
import com.example.watcher.data.repository.LiveSpeechRecognitionManager
import com.example.watcher.data.repository.MonitorManager
import com.example.watcher.data.repository.VideoProcessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles all Gateway API server lifecycle, tool executor registration, and mDNS discovery.
 * Extracted from IntentViewModel.
 */
internal class GatewayDelegate(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val monitorManager: MonitorManager,
    private val intentRepository: IntentRepository,
    private val historyRepository: HistoryRepository,
    private val videoRepository: VideoProcessRepository,
    private val agentService: AgentFrameworkService,
    private val liveCommentaryRepository: LiveCommentaryRepository,
    private val liveSpeechManager: LiveSpeechRecognitionManager,
    private val streamUrlProvider: () -> String?,
    private val onStreamRelease: () -> Unit,
    private val onStreamReclaim: () -> Unit
) {
    private val taskManager = GatewayTaskManager()
    private val announcer = GatewayServiceAnnouncer(appContext)
    private var server: GatewayServer? = null
    private val prefs = appContext.getSharedPreferences("gateway_prefs", Context.MODE_PRIVATE)

    // Stream ownership: phone ↔ remote handoff protocol
    enum class StreamOwner { Phone, Remote }
    @Volatile private var streamOwner = StreamOwner.Phone
    @Volatile private var reclaimRequested = false

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    val apiKey: String get() = prefs.getString("api_key", null) ?: generateApiKey()
    val port: Int get() = prefs.getInt("port", GatewayServer.DEFAULT_PORT)

    fun toggle(enabled: Boolean) {
        if (enabled) start() else stop()
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return try {
            @Suppress("DEPRECATION")
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip != 0) "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
            else "0.0.0.0"
        } catch (_: Exception) { "0.0.0.0" }
    }

    fun release() {
        stop()
        taskManager.release()
    }

    // ── Server lifecycle ─────────────────────────────────────────

    private fun start() {
        if (server != null) return
        scope.launch(Dispatchers.IO) {
            registerExecutors()
            val ports = listOf(port, 8081, 8090, 9090)
            for (p in ports) {
                val s = GatewayServer(
                    port = p,
                    apiKey = apiKey,
                    localIpProvider = ::getLocalIpAddress,
                    frameProvider = { monitorManager.currentFrame.value },
                    taskManager = taskManager,
                    agentService = agentService,
                    commentaryStateProvider = {
                        val commentary = liveCommentaryRepository.commentaryState.value
                        val speech = liveSpeechManager.state.value
                        mapOf(
                            "isActive" to commentary.isActive,
                            "entries" to commentary.entries,
                            "recordedSegmentCount" to commentary.recordedSegmentCount,
                            "analyzedSegmentCount" to commentary.analyzedSegmentCount,
                            "memoryA" to commentary.memoryA,
                            "latestMemoryB" to commentary.latestMemoryB,
                            "scenePhase" to commentary.scenePhase,
                            "sceneMemory" to commentary.sceneMemory,
                            "entityMemory" to commentary.entityMemory,
                            "actionSummary" to commentary.actionSummary,
                            "pendingAsks" to commentary.pendingAsks,
                            "expertRequests" to commentary.expertRequests,
                            "speech" to mapOf(
                                "isActive" to speech.isActive,
                                "isListening" to speech.isListening,
                                "transcripts" to speech.transcripts
                            )
                        )
                    },
                    commentaryEntriesProvider = { since ->
                        val entries = liveCommentaryRepository.commentaryState.value.entries
                        if (since > 0) entries.filter { it.wallClockStartTime > since } else entries
                    },
                    onCommentaryAsk = { requests ->
                        liveCommentaryRepository.sceneMemoryManager.appendExpertRequests(requests)
                    },
                    streamStatusProvider = {
                        mapOf(
                            "owner" to streamOwner.name.lowercase(),
                            "reclaimRequested" to reclaimRequested,
                            "streamUrl" to streamUrlProvider()
                        )
                    },
                    onStreamHandoff = {
                        val url = streamUrlProvider()
                        if (!url.isNullOrBlank() && streamOwner == StreamOwner.Phone) {
                            onStreamRelease()
                            streamOwner = StreamOwner.Remote
                            reclaimRequested = false
                        }
                        url
                    },
                    onStreamReclaim = {
                        if (streamOwner == StreamOwner.Remote) {
                            reclaimRequested = true
                        } else {
                            // Already phone-owned, just reconnect
                            onStreamReclaim()
                        }
                    },
                    onStreamRelease = {
                        // Remote client confirms disconnect → phone reconnects
                        streamOwner = StreamOwner.Phone
                        reclaimRequested = false
                        onStreamReclaim()
                    }
                )
                try {
                    s.start()
                    server = s
                    announcer.register(p)
                    _running.value = true
                    android.util.Log.d("Gateway", "Started on port $p")
                    return@launch
                } catch (e: Exception) {
                    android.util.Log.w("Gateway", "Port $p unavailable: ${e.message}")
                    try { s.stop() } catch (_: Exception) {}
                }
            }
            android.util.Log.e("Gateway", "Failed to start on any port")
        }
    }

    private fun stop() {
        announcer.unregister()
        server?.stop()
        server = null
        _running.value = false
    }

    private fun generateApiKey(): String {
        val key = UUID.randomUUID().toString().replace("-", "").take(24)
        prefs.edit().putString("api_key", key).apply()
        return key
    }

    // ── Tool executors ───────────────────────────────────────────

    private fun registerExecutors() {
        // ── snapshot ──
        taskManager.registerExecutor("snapshot") { _, onEvent ->
            val bitmap = monitorManager.currentFrame.value
                ?: throw IllegalStateException("No frame available")
            onEvent(GatewayEvent("snapshot_captured", null))
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            mapOf("format" to "jpeg", "size" to out.size(), "message" to "Snapshot captured. Use GET /api/stream/snapshot for the image.")
        }

        // ── monitor ──
        taskManager.registerExecutor("monitor") { params, onEvent ->
            val objective = params["objective"] as? String
                ?: throw IllegalArgumentException("Missing required param: objective")
            val interval = (params["checkIntervalSeconds"] as? Number)?.toInt() ?: 30
            val trigger = params["triggerCondition"] as? String ?: ""

            onEvent(GatewayEvent("creating_task", mapOf("objective" to objective, "interval" to interval)))

            val task = IntentResult(
                title = objective.take(48),
                userInput = objective,
                userRequirement = objective,
                originalSceneDescription = "Gateway 远程创建的监控任务。$trigger".trim(),
                checkInterval = interval.coerceIn(2, 300),
                promptTemplate = buildMonitorPrompt(objective, trigger),
                monitorMode = MonitorMode.SceneBaseline,
                targetTrigger = TargetTrigger.OnAppear,
                baselineSource = BaselineSource.CapturedFrame
            ).normalized()

            val saved = intentRepository.saveTask(task)
            val taskId = saved.taskId ?: throw IllegalStateException("Failed to save monitor task")
            onEvent(GatewayEvent("task_saved", mapOf("taskId" to taskId)))

            monitorManager.stopMonitoring()
            val runId = historyRepository.startMonitorRun(saved)
            onEvent(GatewayEvent("monitoring_started", mapOf("runId" to runId, "checkInterval" to interval)))
            monitorManager.startMonitoring(saved, runId)

            // Register graceful cancel
            val gwTaskId = taskManager.listTasks().firstOrNull {
                it.status == GatewayTaskStatus.Running && it.tool == "monitor"
            }?.id
            if (gwTaskId != null) {
                taskManager.onTaskCancel(gwTaskId) { monitorManager.stopMonitoring() }
            }

            // Collect status updates until monitoring stops
            var lastCheckCount = 0
            while (true) {
                delay(2000)
                val status = monitorManager.monitorStatus.value
                if (!status.isRunning) break
                if (status.totalCheckCount > lastCheckCount) {
                    lastCheckCount = status.totalCheckCount
                    onEvent(GatewayEvent("check_result", mapOf(
                        "result" to status.lastResult.name,
                        "summary" to status.lastSummary,
                        "reason" to status.lastReason,
                        "confidence" to status.lastConfidence,
                        "totalChecks" to status.totalCheckCount,
                        "alerts" to status.alertCount,
                        "warnings" to status.warningCount,
                        "normals" to status.normalCount
                    )))
                }
            }

            val finalStatus = monitorManager.monitorStatus.value
            mapOf(
                "taskId" to taskId,
                "totalChecks" to finalStatus.totalCheckCount,
                "alerts" to finalStatus.alertCount,
                "warnings" to finalStatus.warningCount,
                "normals" to finalStatus.normalCount,
                "lastResult" to finalStatus.lastResult.name,
                "lastSummary" to finalStatus.lastSummary
            )
        }

        // ── video_analysis ──
        taskManager.registerExecutor("video_analysis") { params, onEvent ->
            val taskDesc = params["task"] as? String
                ?: throw IllegalArgumentException("Missing required param: task")
            val duration = (params["durationSeconds"] as? Number)?.toInt() ?: 60
            val segment = (params["segmentSeconds"] as? Number)?.toInt() ?: 10

            onEvent(GatewayEvent("creating_task", mapOf("task" to taskDesc, "duration" to duration, "segment" to segment)))

            val draft = VideoProcessTaskDraft(
                userRequirement = taskDesc,
                plannedDurationSeconds = duration.coerceIn(5, 600),
                plannedSegmentDurationSeconds = segment.coerceIn(2, 300),
                captureIntervalSeconds = segment.coerceIn(2, 300),
                plannedSamplingFps = 2,
                autoStartStreamingOutput = false,
                finalSummaryEnabled = true
            ).normalized()

            onEvent(GatewayEvent("recording_started", mapOf(
                "duration" to draft.plannedDurationSeconds,
                "segments" to draft.plannedSegmentCount,
                "segmentDuration" to draft.plannedSegmentDurationSeconds
            )))

            val stopFlag = AtomicBoolean(false)
            val gwTaskId = taskManager.listTasks().firstOrNull {
                it.status == GatewayTaskStatus.Running && it.tool == "video_analysis"
            }?.id
            if (gwTaskId != null) {
                taskManager.onTaskCancel(gwTaskId) { stopFlag.set(true) }
            }

            val result = videoRepository.executeTask(
                draft = draft,
                streamingOutputEnabled = false,
                latestFrameProvider = { monitorManager.currentFrame.value },
                outputRoot = appContext.filesDir,
                shouldStopRequested = { stopFlag.get() },
                onStatus = { update ->
                    val eventType = when (update.stage) {
                        VideoRunStatus.Recording -> "recording"
                        VideoRunStatus.Uploading -> "uploading"
                        VideoRunStatus.Preprocessing -> "preprocessing"
                        VideoRunStatus.Analyzing -> "analyzing"
                        VideoRunStatus.Summarizing -> "summarizing"
                        VideoRunStatus.Completed -> "completed"
                        VideoRunStatus.Failed -> "failed"
                        VideoRunStatus.Cancelled -> "cancelled"
                        else -> "progress"
                    }
                    onEvent(GatewayEvent(eventType, mapOf(
                        "message" to update.message,
                        "recordedSegments" to update.recordedSegmentCount,
                        "analyzedSegments" to update.analyzedSegmentCount,
                        "runId" to update.runId
                    )))
                }
            )

            mapOf(
                "runId" to result.run.id,
                "status" to result.run.status.name,
                "summary" to result.finalResult.summary,
                "conclusion" to result.finalResult.conclusion,
                "segmentCount" to result.run.segmentCount,
                "durationSeconds" to result.run.totalDurationSeconds,
                "timelineEvents" to result.finalResult.timelineEvents.map { event ->
                    mapOf(
                        "timestampSeconds" to event.timestampSeconds,
                        "title" to event.title,
                        "detail" to event.detail,
                        "confidence" to event.confidence
                    )
                }
            )
        }
    }

    private fun buildMonitorPrompt(objective: String, trigger: String): String = buildString {
        appendLine("你是一个视觉监控分析员。你需要分析当前画面，判断是否满足用户的监控条件。")
        appendLine()
        appendLine("监控目标：$objective")
        if (trigger.isNotBlank()) appendLine("触发条件：$trigger")
        appendLine()
        appendLine("请分析当前画面并与基线对比。返回 JSON：")
        appendLine("{\"status\": \"ALERT|WARNING|NORMAL\", \"summary\": \"一句话概括\", \"reason\": \"判断依据\", \"confidence\": 0.0-1.0}")
        appendLine()
        appendLine("- ALERT：检测到明确满足监控条件的情况，需要立即通知")
        appendLine("- WARNING：发现可疑变化，但尚未完全确认")
        appendLine("- NORMAL：未检测到异常")
    }
}
