package com.example.watcher.data.model

data class CommentaryEntry(
    val sessionId: String = "",
    val segmentIndex: Int,
    val wallClockStartTime: Long,
    val displayTimestamp: String,
    val text: String,
    val status: CommentaryEntryStatus,
    val streamingText: String = "",
    val consumerId: Int = 0
)

enum class CommentaryEntryStatus {
    Recording,
    Uploading,
    Processing,
    Analyzing,
    Streaming,
    Completed,
    Skipped,
    Failed
}

data class LiveCommentaryState(
    val sessionId: String = "",
    val isActive: Boolean = false,
    val isDraining: Boolean = false,
    val entries: List<CommentaryEntry> = emptyList(),
    val recordedSegmentCount: Int = 0,
    val analyzedSegmentCount: Int = 0,
    val memoryA: String = "",
    val latestMemoryB: String = "",
    val scenePhase: String = "",
    val sceneMemory: String = "",
    val entityMemory: String = "",
    val actionSummary: String = "",
    val pendingAsks: List<String> = emptyList(),
    val expertRequests: List<String> = emptyList()
)
