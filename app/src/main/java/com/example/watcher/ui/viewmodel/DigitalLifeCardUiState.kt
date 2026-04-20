package com.example.watcher.ui.viewmodel

enum class ObservationControlPhase {
    Idle,
    StartingAgent,
    StartingObservation,
    Running,
    StoppingObservation,
    AwaitingDrain,
    Consolidating,
    StoppingAgent,
    Error
}

data class ObservationControlState(
    val phase: ObservationControlPhase = ObservationControlPhase.Idle,
    val message: String = "未启动"
) {
    val isBusy: Boolean
        get() = phase == ObservationControlPhase.StartingAgent ||
            phase == ObservationControlPhase.StartingObservation ||
            phase == ObservationControlPhase.StoppingObservation ||
            phase == ObservationControlPhase.AwaitingDrain ||
            phase == ObservationControlPhase.Consolidating ||
            phase == ObservationControlPhase.StoppingAgent
}

data class ClaimConsolidationUiState(
    val sceneId: String? = null,
    val isRunning: Boolean = false,
    val mergedCount: Int = 0,
    val reason: String = "",
    val summaries: List<String> = emptyList(),
    val errorMessage: String? = null,
    val updatedAt: Long? = null
)

data class BlackboardDebugUiState(
    val sessionId: String = "",
    val sharedKeys: List<String> = emptyList(),
    val sharedFields: List<DebugField> = emptyList(),
    val categoryCounts: List<BlackboardCategoryCount> = emptyList(),
    val recentObservations: List<BlackboardDebugObservation> = emptyList(),
    val observationItemCount: Int = 0,
    val persistedSegmentCount: Int = 0,
    val fedSignalCount: Int = 0,
    val lastFedSignalSummary: String = "",
    val lastPersistedSegmentIndex: Int? = null
)

data class DebugField(
    val key: String,
    val content: String
)

data class BlackboardCategoryCount(
    val category: String,
    val count: Int
)

data class BlackboardDebugObservation(
    val category: String,
    val dimensionHint: String,
    val content: String,
    val segmentIndex: Int
)
