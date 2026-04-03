package com.example.watcher.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.watcher.data.model.CommentaryEntry
import com.example.watcher.data.model.CommentaryEntryStatus
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.remote.ArkResponseStreamEvent
import com.example.watcher.data.remote.ArkStreamingClient
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoVideoRequest
import com.example.watcher.data.remote.VideoContentItem
import com.example.watcher.data.remote.VideoMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class LiveCommentaryRepository(
    private val apiService: DoubaoApiService,
    private val streamingClient: ArkStreamingClient,
    private val recorder: MjpegVideoRecorder = MjpegVideoRecorder()
) {
    companion object {
        private const val TAG = "LiveCommentary"
        const val SEGMENT_DURATION = 4
        const val SAMPLING_FPS = 2
        const val MAX_ENTRIES = 50
        private const val MAX_PENDING_SEGMENTS = 4
        private const val HIGH_PRESSURE_PENDING_SEGMENTS = 3
        private const val HIGH_PRESSURE_ANALYZE_EVERY_NTH_SEGMENT = 2
        private const val FILE_POLL_ATTEMPTS = 30
        private const val FILE_POLL_INTERVAL_MS = 2_000L
    }

    private val apiKey = ArkConfig.apiKey
    private val videoModel = ArkConfig.videoAnalysisModel
    val memoryManager = CommentaryMemoryManager(apiService)
    val sceneMemoryManager = SceneMemoryManager(apiService)

    private val _state = MutableStateFlow(LiveCommentaryState())
    val commentaryState: StateFlow<LiveCommentaryState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var commentaryJob: Job? = null

    // Consumer tracking
    private val pendingCount = AtomicInteger(0)
    // Channel for Consumer A to receive completed commentary text
    private var builderChannel: Channel<Pair<Int, String>>? = null // (segmentIndex, commentaryText)

    fun startCommentary(
        outputRoot: File,
        latestFrameProvider: () -> Bitmap?
    ) {
        stopCommentary()
        _state.value = LiveCommentaryState(isActive = true)
        Log.d(TAG, "startCommentary called, outputRoot=$outputRoot")

        val commentaryDir = File(outputRoot, "commentary").also { it.mkdirs() }
        val segmentChannel = Channel<CommentarySegment>(MAX_PENDING_SEGMENTS)
        val segmentCounter = AtomicInteger(0)

        pendingCount.set(0)
        val bChannel = Channel<Pair<Int, String>>(Channel.UNLIMITED)
        builderChannel = bChannel

        commentaryJob = scope.launch {
            Log.d(TAG, "Commentary started: 3-consumer model (A=builder, B/C=commentators)")

            // Launch Consumer A (builder) 鈥?text-only, processes B/C output
            val consumerA = launch { runBuilderConsumer(bChannel) }

            // Launch Consumer B & C (commentators) 鈥?video analysis
            val consumerB = launch { runCommentatorConsumer("B", segmentChannel, bChannel) }
            val consumerC = launch { runCommentatorConsumer("C", segmentChannel, bChannel) }

            // Producer: continuous recording loop
            val recordingJob = launch {
                Log.d(TAG, "Recording producer started")
                try {
                    while (isActive) {
                        val segmentIndex = segmentCounter.incrementAndGet()
                        val wallClockStart = System.currentTimeMillis()
                        val displayTimestamp = formatTimestamp(wallClockStart)

                        Log.d(TAG, "Segment $segmentIndex: starting recording at $displayTimestamp")

                        addOrUpdateEntry(CommentaryEntry(
                            segmentIndex = segmentIndex,
                            wallClockStartTime = wallClockStart,
                            displayTimestamp = displayTimestamp,
                            text = "",
                            status = CommentaryEntryStatus.Recording
                        ))

                        val outputFile = File(commentaryDir, "segment_$segmentIndex.mp4")
                        try {
                            val effectiveSamplingFps = if (pendingCount.get() >= HIGH_PRESSURE_PENDING_SEGMENTS) {
                                1
                            } else {
                                SAMPLING_FPS
                            }
                            val result = recorder.recordSegment(
                                outputFile = outputFile,
                                durationSeconds = SEGMENT_DURATION,
                                samplingFps = effectiveSamplingFps,
                                frameProvider = latestFrameProvider
                            )
                            Log.d(
                                TAG,
                                "Segment $segmentIndex: recorded ${result.capturedFrameCount} frames at ${effectiveSamplingFps}fps"
                            )
                            if (shouldSkipSegmentForRealtime(segmentIndex)) {
                                result.file.delete()
                                updateEntryStatus(
                                    segmentIndex,
                                    CommentaryEntryStatus.Skipped,
                                    errorText = "已跳过（优先保证实时性）"
                                )
                                Log.w(
                                    TAG,
                                    "Segment $segmentIndex skipped before enqueue under high pressure"
                                )
                                updateRecordedCount(segmentIndex)
                                continue
                            }
                            enqueueSegmentWithBackpressure(segmentChannel, CommentarySegment(
                                segmentIndex = segmentIndex,
                                wallClockStartTime = wallClockStart,
                                displayTimestamp = displayTimestamp,
                                samplingFps = effectiveSamplingFps,
                                file = result.file
                            ))
                            updateRecordedCount(segmentIndex)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
            Log.w(TAG, "Segment $segmentIndex recording failed", e)
            updateEntryStatus(
                segmentIndex,
                CommentaryEntryStatus.Failed,
                errorText = "录制失败: ${e.message}"
            )
                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "Recording producer cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Recording producer crashed", e)
                } finally {
                    segmentChannel.close()
                    bChannel.close()
                }
            }

            recordingJob.join()
        }
    }

    fun stopCommentary() {
        commentaryJob?.cancel()
        commentaryJob = null
        // Keep scene memory and general memory for management UI viewing
        // Only clear the "active" flag; memory persists until explicit reset
        _state.value = _state.value.copy(
            isActive = false,
            scenePhase = sceneMemoryManager.phase.displayName,
            sceneMemory = sceneMemoryManager.sceneMemory,
            entityMemory = sceneMemoryManager.buildEntitySummary(),
            actionSummary = sceneMemoryManager.actionSummary,
            pendingAsks = sceneMemoryManager.getPendingRequests()
        )
    }

    /** Full reset 鈥?clears all memory (called by reset button) */
    fun fullReset() {
        stopCommentary()
        memoryManager.reset()
        sceneMemoryManager.reset()
        _state.value = LiveCommentaryState()
    }

    fun release() {
        stopCommentary()
        scope.cancel()
    }

    // --- Consumer B/C: Commentators (video analysis) ---

    private suspend fun runCommentatorConsumer(
        name: String,
        segmentChannel: Channel<CommentarySegment>,
        builderCh: Channel<Pair<Int, String>>
    ) {
        Log.d(TAG, "Consumer $name (commentator) started")
        try {
            for (segment in segmentChannel) {
                Log.d(TAG, "Consumer $name processing segment ${segment.segmentIndex}")
                val text = analyzeSegment(segment, name)
                pendingCount.decrementAndGet()

                // Feed commentary text to Consumer A
                if (text != null) {
                    builderCh.send(segment.segmentIndex to text)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // normal shutdown
        } finally {
            Log.d(TAG, "Consumer $name stopped")
        }
    }

    /** Returns commentary text or null on failure */
    private suspend fun analyzeSegment(segment: CommentarySegment, consumerName: String): String? {
        Log.d(TAG, "Segment ${segment.segmentIndex}: starting analysis ($consumerName)")
        try {
            updateEntryStatus(segment.segmentIndex, CommentaryEntryStatus.Uploading, consumerId = 0)
            val fileId = uploadVideoFile(segment.file, segment.samplingFps)

            updateEntryStatus(segment.segmentIndex, CommentaryEntryStatus.Processing, consumerId = 0)
            waitForFileReady(fileId)

            updateEntryStatus(segment.segmentIndex, CommentaryEntryStatus.Analyzing, consumerId = 0)
            val commentaryText = streamAnalyze(fileId, segment)
            Log.d(TAG, "Segment ${segment.segmentIndex}: done ($consumerName), ${commentaryText.length} chars")

            updateEntryText(
                segmentIndex = segment.segmentIndex,
                text = commentaryText,
                status = CommentaryEntryStatus.Completed,
                consumerId = 0
            )
            incrementAnalyzedCount()

            // Feed into general memory (for AI audiences)
            memoryManager.onNewCommentary(commentaryText)
            _state.value = _state.value.copy(
                memoryA = memoryManager.memoryA,
                latestMemoryB = memoryManager.latestMemoryB
            )

            return commentaryText
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Segment ${segment.segmentIndex} failed ($consumerName)", e)
            updateEntryStatus(
                segment.segmentIndex,
                CommentaryEntryStatus.Failed,
                errorText = "分析失败: ${e.message}",
                consumerId = 0
            )
            return null
        } finally {
            segment.file.delete()
        }
    }

    // --- Consumer A: Builder (text-only, builds scene memory) ---

    private suspend fun runBuilderConsumer(channel: Channel<Pair<Int, String>>) {
        Log.d(TAG, "Consumer A (builder) started")
        try {
            for ((segmentIndex, text) in channel) {
                if (!sceneMemoryManager.shouldProcess(segmentIndex)) {
                    Log.d(TAG, "Builder skipping segment $segmentIndex (phase=${sceneMemoryManager.phase})")
                    continue
                }
                Log.d(TAG, "Builder processing segment $segmentIndex [${sceneMemoryManager.phase.displayName}]")
                sceneMemoryManager.processCommentary(text, segmentIndex)

                // Update UI with scene memory state
                _state.value = _state.value.copy(
                    scenePhase = sceneMemoryManager.phase.displayName,
                    sceneMemory = sceneMemoryManager.sceneMemory,
                    entityMemory = sceneMemoryManager.buildEntitySummary(),
                    actionSummary = sceneMemoryManager.actionSummary,
                    pendingAsks = sceneMemoryManager.getPendingRequests()
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // normal shutdown
        } finally {
            Log.d(TAG, "Consumer A stopped")
        }
    }

    private suspend fun enqueueSegmentWithBackpressure(
        segmentChannel: Channel<CommentarySegment>,
        segment: CommentarySegment
    ) {
        while (true) {
            val sendResult = segmentChannel.trySend(segment)
            if (sendResult.isSuccess) {
                pendingCount.incrementAndGet()
                return
            }

            val dropped = segmentChannel.tryReceive().getOrNull()
            if (dropped != null) {
                pendingCount.decrementAndGet()
                dropped.file.delete()
                updateEntryStatus(
                    dropped.segmentIndex,
                    CommentaryEntryStatus.Skipped,
                    errorText = "已跳过（积压保护）"
                )
                Log.w(
                    TAG,
                    "Dropping queued segment ${dropped.segmentIndex} to keep live commentary fresh"
                )
                continue
            }

            delay(100L)
        }
    }

    private fun shouldSkipSegmentForRealtime(segmentIndex: Int): Boolean {
        val currentPending = pendingCount.get()
        return currentPending >= HIGH_PRESSURE_PENDING_SEGMENTS &&
            segmentIndex % HIGH_PRESSURE_ANALYZE_EVERY_NTH_SEGMENT == 0
    }

    private suspend fun uploadVideoFile(file: File, samplingFps: Int): String {
        val response = apiService.uploadFile(
            authorization = bearerToken(),
            purpose = "user_data".toRequestBody("text/plain".toMediaType()),
            preprocessConfigs = mapOf(
                "preprocess_configs[video][fps]" to samplingFps
                    .coerceIn(1, 5)
                    .toString()
                    .toRequestBody("text/plain".toMediaType())
            ),
            file = MultipartBody.Part.createFormData(
                name = "file",
                filename = file.name,
                body = file.asRequestBody("video/mp4".toMediaType())
            )
        )
        return response.resolvedId()
            ?: error("File upload succeeded but file_id was missing.")
    }

    private suspend fun waitForFileReady(fileId: String) {
        repeat(FILE_POLL_ATTEMPTS) { attempt ->
            val file = apiService.getFile(bearerToken(), fileId)
            val status = file.status?.lowercase()
            if (status == null || status in listOf("active", "processed", "ready", "succeeded")) {
                return
            }
            if (status == "failed") {
                error("Ark file preprocessing failed for $fileId")
            }
            delay(FILE_POLL_INTERVAL_MS)
            if (attempt == FILE_POLL_ATTEMPTS - 1) {
                error("Ark file preprocessing timed out for $fileId")
            }
        }
    }

    private suspend fun streamAnalyze(
        fileId: String,
        segment: CommentarySegment
    ): String {
        val request = DoubaoVideoRequest(
            model = videoModel,
            stream = true,
            input = listOf(
                VideoMessage(
                    role = "user",
                    content = listOf(
                        VideoContentItem(type = "input_video", fileId = fileId),
                        VideoContentItem(type = "input_text", text = buildCommentaryPrompt(segment))
                    )
                )
            )
        )

        updateEntryStatus(segment.segmentIndex, CommentaryEntryStatus.Streaming)

        return streamingClient.streamResponse(
            authorization = bearerToken(),
            requestPayload = request
        ) { event ->
            when (event) {
                is ArkResponseStreamEvent.OutputTextDelta ->
                    updateStreamingText(segment.segmentIndex, event.fullText)
                is ArkResponseStreamEvent.OutputTextDone ->
                    updateStreamingText(segment.segmentIndex, event.fullText)
                is ArkResponseStreamEvent.Completed -> { /* handled by return */ }
            }
        }
    }

    private fun buildCommentaryPrompt(segment: CommentarySegment): String = buildString {
        appendLine("你是一位直播实况解说员。观看这段视频片段，描述画面中正在发生的事情。")
        appendLine()

        val sceneContext = sceneMemoryManager.buildSceneContext()
        if (sceneContext.isNotBlank()) {
            appendLine(sceneContext)
        }

        val memA = memoryManager.memoryA
        val memB = memoryManager.latestMemoryB
        if (memA.isNotBlank() || memB.isNotBlank()) {
            appendLine("【长期记忆】")
            if (memA.isNotBlank()) appendLine("核心：$memA")
            if (memB.isNotBlank()) appendLine("近期：$memB")
            appendLine()
        }

        val asks = sceneMemoryManager.getPendingRequests()
        if (asks.isNotEmpty()) {
            appendLine("【建设者请求】（请在描述中重点补充以下信息）")
            asks.forEach { appendLine("- $it") }
            appendLine()
        }

        appendLine("规则：")
        appendLine("- 固定场景和已识别实体不要重复描述，专注于新发生的动作和变化。")
        appendLine("- 只描述画面中实际可见的内容，严禁推测或想象接下来会发生什么。")
        appendLine("- 如果发现新的人物或物体，详细描述其外观特征以便后续识别。")
        if (asks.isNotEmpty()) {
            appendLine("- 优先回答上方【建设者请求】中的问题。")
        }
        appendLine("- 使用现在时，简洁有力。")
        appendLine("- 如果场景静态无变化，尽可能详细描述场景中的物体、环境、布局、颜色等细节。")
        appendLine("- 最多 4-5 句话。")
        appendLine("- 使用简体中文。")
        appendLine("- 只返回解说文本，不要返回 JSON 或其他格式。")
        appendLine()
        appendLine("片段录制时间：${segment.displayTimestamp}")
    }

    // --- State management ---

    private fun addOrUpdateEntry(entry: CommentaryEntry) {
        _state.value = _state.value.let { current ->
            val updated = (listOf(entry) + current.entries)
                .distinctBy { it.segmentIndex }
                .sortedByDescending { it.segmentIndex }
                .take(MAX_ENTRIES)
            current.copy(entries = updated)
        }
    }

    private fun updateEntryStatus(
        segmentIndex: Int,
        status: CommentaryEntryStatus,
        errorText: String? = null,
        consumerId: Int = 0
    ) {
        _state.value = _state.value.let { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.segmentIndex == segmentIndex) {
                        entry.copy(
                            status = status,
                            text = errorText ?: entry.text,
                            consumerId = consumerId
                        )
                    } else entry
                }
            )
        }
    }

    private fun updateEntryText(
        segmentIndex: Int,
        text: String,
        status: CommentaryEntryStatus,
        consumerId: Int = 0
    ) {
        _state.value = _state.value.let { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.segmentIndex == segmentIndex) {
                        entry.copy(text = text, streamingText = "", status = status, consumerId = consumerId)
                    } else entry
                }
            )
        }
    }

    private fun updateStreamingText(segmentIndex: Int, streamingText: String) {
        _state.value = _state.value.let { current ->
            current.copy(
                entries = current.entries.map { entry ->
                    if (entry.segmentIndex == segmentIndex) {
                        entry.copy(
                            streamingText = streamingText,
                            status = CommentaryEntryStatus.Streaming
                        )
                    } else entry
                }
            )
        }
    }

    private fun updateRecordedCount(count: Int) {
        _state.value = _state.value.copy(recordedSegmentCount = count)
    }

    private fun incrementAnalyzedCount() {
        _state.value = _state.value.let { current ->
            current.copy(analyzedSegmentCount = current.analyzedSegmentCount + 1)
        }
    }

    // --- Helpers ---

    private fun bearerToken(): String = "Bearer $apiKey"

    private fun formatTimestamp(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

    private data class CommentarySegment(
        val segmentIndex: Int,
        val wallClockStartTime: Long,
        val displayTimestamp: String,
        val samplingFps: Int,
        val file: File
    )
}
