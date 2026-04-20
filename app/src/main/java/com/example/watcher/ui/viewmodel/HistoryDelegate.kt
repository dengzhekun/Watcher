package com.example.watcher.ui.viewmodel

import com.example.watcher.data.model.HistoryRecordDetail
import com.example.watcher.data.model.HistoryRecordSelection
import com.example.watcher.data.model.HistoryRecordType
import com.example.watcher.data.model.TimelineEventEntity
import com.example.watcher.data.repository.HistoryRepository
import com.example.watcher.data.repository.VideoProcessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Handles history record selection, deletion, and detail/event observation.
 * Extracted from IntentViewModel.
 */
internal class HistoryDelegate(
    private val scope: CoroutineScope,
    private val historyRepository: HistoryRepository,
    private val videoRepository: VideoProcessRepository,
    private val selectedVideoRunId: MutableStateFlow<Long?>,
    private val selectedVideoRunEvents: MutableStateFlow<List<TimelineEventEntity>>
) {
    private val _selectedRecord = MutableStateFlow<HistoryRecordSelection?>(null)
    private val _selectedDetail = MutableStateFlow<HistoryRecordDetail?>(null)

    val selectedHistoryRecord: StateFlow<HistoryRecordSelection?> = _selectedRecord.asStateFlow()
    val selectedHistoryDetail: StateFlow<HistoryRecordDetail?> = _selectedDetail.asStateFlow()

    fun startObserving() {
        observeSelectedHistoryDetail()
        observeSelectedVideoRunEvents()
    }

    fun selectHistoryRecord(selection: HistoryRecordSelection?) {
        _selectedRecord.value = selection
    }

    fun deleteHistoryRecord(selection: HistoryRecordSelection) {
        scope.launch {
            val detail = _selectedDetail.value
            if (detail?.selection == selection && !detail.canDelete) return@launch

            historyRepository.deleteHistoryRecord(selection)
            if (_selectedRecord.value == selection) {
                _selectedRecord.value = null
                _selectedDetail.value = null
            }
            if (selection.type == HistoryRecordType.VideoAnalysis &&
                selectedVideoRunId.value == selection.recordId
            ) {
                selectedVideoRunId.value = null
                selectedVideoRunEvents.value = emptyList()
            }
        }
    }

    private fun observeSelectedVideoRunEvents() {
        scope.launch {
            selectedVideoRunId
                .flatMapLatest { runId ->
                    if (runId == null) flowOf(emptyList())
                    else videoRepository.observeTimelineForRun(runId)
                }
                .collect { events -> selectedVideoRunEvents.value = events }
        }
    }

    private fun observeSelectedHistoryDetail() {
        scope.launch {
            _selectedRecord
                .flatMapLatest { selection ->
                    if (selection == null) flowOf(null)
                    else historyRepository.observeHistoryDetail(selection)
                }
                .collect { detail -> _selectedDetail.value = detail }
        }
    }
}
