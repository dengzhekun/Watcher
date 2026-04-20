package com.example.watcher.data.repository

import com.example.watcher.data.model.CommentaryEntry
import com.example.watcher.data.model.CommentaryEntryStatus
import com.example.watcher.data.model.LiveCommentaryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ObservationIngestionDebugState(
    val sessionId: String = "",
    val persistedSegmentCount: Int = 0,
    val fedSignalCount: Int = 0,
    val lastFedSignalSummary: String = "",
    val lastPersistedSegmentIndex: Int? = null,
    val pendingBatchSize: Int = 0
)

class DigitalLifeCardObservationIngestionService(
    private val blackboardManager: BlackboardManager
) {
    private val _debugState = MutableStateFlow(ObservationIngestionDebugState())
    val debugState: StateFlow<ObservationIngestionDebugState> = _debugState.asStateFlow()

    private var collectJob: Job? = null
    private val persistedSegments = mutableSetOf<String>()
    private val fedSegments = mutableSetOf<String>()
    private var activeSessionId: String = ""

    private val pendingBatch = mutableListOf<String>()
    private var batchFlushJob: Job? = null
    private var batchScope: CoroutineScope? = null
    private var batchFeedFn: (suspend (String) -> Unit)? = null

    companion object {
        private const val MAX_BATCH_SIZE = 4
        private const val MAX_BATCH_WAIT_MILLIS = 12_000L
    }

    fun start(
        scope: CoroutineScope,
        commentaryState: StateFlow<LiveCommentaryState>,
        shouldFeedToAgent: () -> Boolean,
        feedObservation: suspend (String) -> Unit
    ) {
        collectJob?.cancel()
        batchScope = scope
        batchFeedFn = feedObservation
        collectJob = scope.launch {
            commentaryState.collect { state ->
                onSessionChanged(state.sessionId)
                val completedEntries = state.entries.filter { it.status == CommentaryEntryStatus.Completed }
                for (entry in completedEntries) {
                    val segmentKey = segmentKeyOf(entry, state.sessionId)
                    if (segmentKey !in persistedSegments) {
                        blackboardManager.onCommentaryCompleted(entry)
                        persistedSegments.add(segmentKey)
                        _debugState.value = _debugState.value.copy(
                            sessionId = state.sessionId,
                            persistedSegmentCount = persistedSegments.size,
                            lastPersistedSegmentIndex = entry.segmentIndex
                        )
                    }
                    if (segmentKey !in fedSegments && shouldFeedToAgent()) {
                        val signal = extractDigitalLifeCardBehaviorSignal(entry.text)
                        fedSegments.add(segmentKey)
                        batchedFeed(scope, signal, feedObservation)
                    }
                }
            }
        }
    }

    private fun batchedFeed(
        scope: CoroutineScope,
        signal: String,
        feedObservation: suspend (String) -> Unit
    ) {
        synchronized(pendingBatch) {
            pendingBatch.add(signal)
            _debugState.value = _debugState.value.copy(pendingBatchSize = pendingBatch.size)
        }
        batchFlushJob?.cancel()
        val currentSize = synchronized(pendingBatch) { pendingBatch.size }
        if (currentSize >= MAX_BATCH_SIZE) {
            scope.launch { flushBatch(feedObservation) }
        } else {
            batchFlushJob = scope.launch {
                delay(MAX_BATCH_WAIT_MILLIS)
                flushBatch(feedObservation)
            }
        }
    }

    private suspend fun flushBatch(feedObservation: suspend (String) -> Unit) {
        val batch: List<String>
        synchronized(pendingBatch) {
            if (pendingBatch.isEmpty()) return
            batch = pendingBatch.toList()
            pendingBatch.clear()
            _debugState.value = _debugState.value.copy(pendingBatchSize = 0)
        }
        val combined = if (batch.size == 1) {
            batch[0]
        } else {
            buildString {
                appendLine("=== 批量观察 (${batch.size}条) ===")
                batch.forEachIndexed { i, s ->
                    appendLine("[观察 ${i + 1}]")
                    appendLine(s)
                    appendLine()
                }
                append("=== 请综合以上观察进行建模分析 ===")
            }
        }
        feedObservation(combined)
        _debugState.value = _debugState.value.copy(
            fedSignalCount = fedSegments.size,
            lastFedSignalSummary = combined.lineSequence().take(3).joinToString(" / ").take(180)
        )
    }

    suspend fun flushRemaining() {
        batchFlushJob?.cancel()
        val fn = batchFeedFn ?: return
        flushBatch(fn)
    }

    fun reset() {
        activeSessionId = ""
        persistedSegments.clear()
        fedSegments.clear()
        synchronized(pendingBatch) { pendingBatch.clear() }
        batchFlushJob?.cancel()
        batchFlushJob = null
        _debugState.value = ObservationIngestionDebugState()
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        batchFlushJob?.cancel()
        batchFlushJob = null
        batchScope = null
        batchFeedFn = null
    }

    private fun onSessionChanged(sessionId: String) {
        if (sessionId == activeSessionId) return
        activeSessionId = sessionId
        persistedSegments.clear()
        fedSegments.clear()
        synchronized(pendingBatch) { pendingBatch.clear() }
        batchFlushJob?.cancel()
        _debugState.value = ObservationIngestionDebugState(sessionId = sessionId)
    }

    private fun segmentKeyOf(entry: CommentaryEntry, fallbackSessionId: String): String {
        val sessionId = entry.sessionId.ifBlank { fallbackSessionId.ifBlank { "legacy" } }
        return "$sessionId:${entry.segmentIndex}"
    }
}
