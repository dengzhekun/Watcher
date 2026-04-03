package com.example.watcher.ui.viewmodel

import com.example.watcher.data.model.IntentResult
import com.example.watcher.data.model.VideoProcessTaskDraft

sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    data class Success(val result: IntentResult) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class VideoPlanUiState {
    data object Idle : VideoPlanUiState()
    data object Loading : VideoPlanUiState()
    data class Success(val task: VideoProcessTaskDraft) : VideoPlanUiState()
    data class Error(val message: String) : VideoPlanUiState()
}
