package com.example.watcher.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.watcher.watcherApplication
import com.example.watcher.data.local.AppDatabase
import com.example.watcher.data.model.BlackboardDay
import com.example.watcher.data.model.BlackboardEntry
import com.example.watcher.data.model.BlackboardObservationItem
import com.example.watcher.data.model.BehaviorClaim
import com.example.watcher.data.model.BehaviorReasoningLog
import com.example.watcher.data.model.CommentaryEntryStatus
import com.example.watcher.data.model.CommentaryPromptProfile
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.MatchBreakdown
import com.example.watcher.data.model.ObservationGoal
import com.example.watcher.data.model.SceneProfile
import com.example.watcher.data.model.SceneProbeSnapshot
import com.example.watcher.data.model.VideoStreamSettings
import com.example.watcher.data.remote.ArkStreamingClient
import com.example.watcher.data.remote.RetrofitClient
import com.example.watcher.data.repository.BlackboardManager
import com.example.watcher.data.repository.DigitalLifeCardObservationIngestionService
import com.example.watcher.data.repository.DigitalLifeCardPortraitModelService
import com.example.watcher.data.repository.DigitalLifeCardSessionCoordinator
import com.example.watcher.data.repository.LiveCommentaryRepository
import com.example.watcher.data.repository.PlaceClusterManager
import com.example.watcher.data.repository.PortraitCuratorActivityEntry
import com.example.watcher.data.repository.PortraitCuratorAgent
import com.example.watcher.data.repository.PortraitCuratorMemoryDebugState
import com.example.watcher.data.repository.PortraitCuratorStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DigitalLifeCardViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "DigitalLifeCardVM"
    }

    private val appContext = getApplication<Application>()
    private val database = AppDatabase.getDatabase(application)
    private val behaviorModelDao = database.behaviorModelDao()
    private val sceneProfileDao = database.sceneProfileDao()
    private val llmWalletRepository =
        appContext.watcherApplication().agentFrameworkContainer.llmWalletRepository

    private val liveCommentaryRepository = LiveCommentaryRepository(
        apiService = RetrofitClient.doubaoApiService,
        streamingClient = ArkStreamingClient(),
        llmWalletRepository = llmWalletRepository,
        promptProfile = CommentaryPromptProfile.digitalLifeCard()
    )

    private val blackboardManager = BlackboardManager(
        blackboardDao = database.blackboardDao()
    )

    private val sceneProfileRepository = com.example.watcher.data.repository.SceneProfileRepository(
        database = database,
        sceneProfileDao = sceneProfileDao,
        behaviorModelDao = behaviorModelDao,
        apiService = RetrofitClient.doubaoApiService,
        llmWalletRepository = llmWalletRepository
    )

    private val behaviorClaimConsolidator = com.example.watcher.data.repository.BehaviorClaimConsolidator(
        database = database,
        apiService = RetrofitClient.doubaoApiService,
        llmWalletRepository = llmWalletRepository
    )

    private val portraitCuratorAgent = PortraitCuratorAgent(
        service = appContext.watcherApplication().agentFrameworkContainer.service,
        blackboardDao = database.blackboardDao(),
        behaviorModelDao = database.behaviorModelDao(),
        behaviorClaimConsolidator = behaviorClaimConsolidator,
        sceneProfileDao = database.sceneProfileDao(),
        sceneMemoryManager = liveCommentaryRepository.sceneMemoryManager,
        currentMatchBreakdownProvider = { _lastMatchBreakdown.value }
    )
    private val portraitModelService = DigitalLifeCardPortraitModelService(
        blackboardDao = database.blackboardDao(),
        behaviorModelDao = behaviorModelDao,
        sceneProfileDao = sceneProfileDao,
        sceneProfileRepository = sceneProfileRepository,
        behaviorClaimConsolidator = behaviorClaimConsolidator
    )
    private val observationIngestionService = DigitalLifeCardObservationIngestionService(
        blackboardManager = blackboardManager
    )
    private val sessionCoordinator = DigitalLifeCardSessionCoordinator(
        liveCommentaryRepository = liveCommentaryRepository,
        blackboardManager = blackboardManager,
        placeClusterManager = PlaceClusterManager(appContext),
        sceneProfileRepository = sceneProfileRepository,
        portraitModelService = portraitModelService,
        portraitCuratorAgent = portraitCuratorAgent
    )

    // --- Video frame source ---
    private val _currentFrame = MutableStateFlow<Bitmap?>(null)

    // --- Stream control ---
    private val _isStreamPlaying = MutableStateFlow(false)
    val isStreamPlaying: StateFlow<Boolean> = _isStreamPlaying.asStateFlow()

    private val _streamReconnectToken = MutableStateFlow(0)
    val streamReconnectToken: StateFlow<Int> = _streamReconnectToken.asStateFlow()

    val videoStreamSettings: Flow<VideoStreamSettings?> =
        database.videoStreamSettingsDao().getSettings()

    // --- Commentary state ---
    val commentaryState: StateFlow<LiveCommentaryState>
        get() = liveCommentaryRepository.commentaryState

    // --- Blackboard & Behavior Model ---
    val blackboardDays: Flow<List<BlackboardDay>> = database.blackboardDao().observeAllDays()
    val behaviorClaims: Flow<List<BehaviorClaim>> = behaviorModelDao.observeAllClaims()
    val allObservationGoals: Flow<List<ObservationGoal>> = behaviorModelDao.observeAllGoals()
    val allBehaviorReasoningLogs: Flow<List<BehaviorReasoningLog>> =
        behaviorModelDao.observeAllReasoningLogs()
    val allSceneProfiles: Flow<List<SceneProfile>> = sceneProfileDao.observeAll()

    private val _selectedDayEntries = MutableStateFlow<List<BlackboardEntry>>(emptyList())
    val selectedDayEntries: StateFlow<List<BlackboardEntry>> = _selectedDayEntries.asStateFlow()

    private val _currentSceneId = MutableStateFlow<String?>(null)
    val currentSceneId: StateFlow<String?> = _currentSceneId.asStateFlow()

    private val _currentSceneLabel = MutableStateFlow("未识别场景")
    val currentSceneLabel: StateFlow<String> = _currentSceneLabel.asStateFlow()

    private val _lastMatchedSceneId = MutableStateFlow<String?>(null)
    val lastMatchedSceneId: StateFlow<String?> = _lastMatchedSceneId.asStateFlow()

    private val _lastMatchBreakdown = MutableStateFlow<MatchBreakdown?>(null)
    val lastMatchBreakdown: StateFlow<MatchBreakdown?> = _lastMatchBreakdown.asStateFlow()

    private var lastSceneProbe: SceneProbeSnapshot? = null

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val currentSceneClaims: Flow<List<BehaviorClaim>> =
        currentSceneId.flatMapLatest { sceneId ->
            if (sceneId == null) {
                flowOf(emptyList())
            } else {
                behaviorModelDao.observeClaimsByScene(sceneId)
            }
        }

    val universalClaims: Flow<List<BehaviorClaim>> = behaviorModelDao.observeUniversalClaims()

    val observationGoals: Flow<List<ObservationGoal>> =
        currentSceneId.flatMapLatest { sceneId ->
            if (sceneId == null) {
                flowOf(emptyList())
            } else {
                behaviorModelDao.observeGoalsByScene(sceneId)
            }
        }

    val behaviorReasoningLogs: Flow<List<BehaviorReasoningLog>> =
        currentSceneId.flatMapLatest { sceneId ->
            if (sceneId == null) {
                flowOf(emptyList())
            } else {
                behaviorModelDao.observeReasoningByScene(sceneId)
            }
        }
    val agentActivityLog: StateFlow<List<PortraitCuratorActivityEntry>>
        get() = portraitCuratorAgent.activityLog
    val agentMemoryDebugState: StateFlow<PortraitCuratorMemoryDebugState>
        get() = portraitCuratorAgent.memoryDebugState

    private val _observationControlState = MutableStateFlow(ObservationControlState())
    val observationControlState: StateFlow<ObservationControlState> = _observationControlState.asStateFlow()
    private val _claimConsolidationUiState = MutableStateFlow(ClaimConsolidationUiState())
    val claimConsolidationUiState: StateFlow<ClaimConsolidationUiState> = _claimConsolidationUiState.asStateFlow()

    private val _blackboardDebugState = MutableStateFlow(BlackboardDebugUiState())
    val blackboardDebugState: StateFlow<BlackboardDebugUiState> = _blackboardDebugState.asStateFlow()

    // --- Agent status ---
    private val _agentStatus = MutableStateFlow<PortraitCuratorStatus>(PortraitCuratorStatus.Idle)
    val agentStatus: StateFlow<PortraitCuratorStatus> = _agentStatus.asStateFlow()
    private var statusPollingJob: Job? = null
    private var pendingConsolidationSceneId: String? = null

    private var latestObservationItems: List<BlackboardObservationItem> = emptyList()

    init {
        observationIngestionService.start(
            scope = viewModelScope,
            commentaryState = commentaryState,
            shouldFeedToAgent = { portraitCuratorAgent.isRunning },
            feedObservation = portraitCuratorAgent::feedObservation
        )

        viewModelScope.launch {
            database.blackboardDao().observeObservationItemsByDate(today()).collect { items ->
                latestObservationItems = items
                refreshBlackboardDebugState()
            }
        }

        viewModelScope.launch {
            observationIngestionService.debugState.collect {
                refreshBlackboardDebugState()
            }
        }

        viewModelScope.launch {
            commentaryState.collect {
                if (
                    !it.isActive &&
                    !it.isDraining &&
                    _observationControlState.value.phase == ObservationControlPhase.Running
                ) {
                    _observationControlState.value = ObservationControlState()
                }
                refreshBlackboardDebugState()
            }
        }
    }

    fun updateVideoFrame(bitmap: Bitmap?) {
        _currentFrame.value = bitmap
    }

    fun setStreamPlaying(playing: Boolean) {
        _isStreamPlaying.value = playing
    }

    fun reconnectStream() {
        _isStreamPlaying.value = true
        _streamReconnectToken.value++
    }

    fun saveVideoStreamSettings(settings: VideoStreamSettings) {
        viewModelScope.launch {
            database.videoStreamSettingsDao().insert(settings.normalized())
        }
    }

    // --- Commentary + Agent controls ---

    fun startCommentary() {
        if (
            _observationControlState.value.isBusy ||
            commentaryState.value.isActive ||
            commentaryState.value.isDraining ||
            portraitCuratorAgent.isRunning
        ) {
            return
        }
        _observationControlState.value = ObservationControlState(
            phase = ObservationControlPhase.StartingAgent,
            message = "正在启动 Agent…"
        )
        viewModelScope.launch {
            try {
                stopStatusPolling()
                observationIngestionService.reset()
                pendingConsolidationSceneId = null
                _lastMatchedSceneId.value = null
                _lastMatchBreakdown.value = null
                val result = sessionCoordinator.startObservation(
                    initialFrame = _currentFrame.value,
                    latestFrameProvider = { _currentFrame.value },
                    outputRoot = appContext.filesDir
                )
                lastSceneProbe = result.lastSceneProbe
                _lastMatchedSceneId.value = result.lastMatchedSceneId
                _lastMatchBreakdown.value = result.lastMatchBreakdown
                bindCurrentScene(result.sceneProfile)
                portraitCuratorAgent.refreshMemoryDebug()
                _agentStatus.value = portraitCuratorAgent.getStatus()
                startStatusPolling()

                _observationControlState.value = ObservationControlState(
                    phase = ObservationControlPhase.Running,
                    message = "观察流运行中"
                )
            } catch (error: Exception) {
                stopStatusPolling()
                if (portraitCuratorAgent.isRunning) {
                    portraitCuratorAgent.stop()
                    startStatusPolling()
                }
                _observationControlState.value = ObservationControlState(
                    phase = ObservationControlPhase.Error,
                    message = error.message ?: "启动失败"
                )
            }
        }
    }

    fun stopCommentary() {
        val phase = _observationControlState.value.phase
        if (
            phase == ObservationControlPhase.StoppingObservation ||
            phase == ObservationControlPhase.AwaitingDrain ||
            phase == ObservationControlPhase.Consolidating ||
            phase == ObservationControlPhase.StoppingAgent
        ) {
            return
        }
        _observationControlState.value = ObservationControlState(
            phase = ObservationControlPhase.StoppingObservation,
            message = "正在停止观察输入…"
        )
        Log.d(TAG, "stopCommentary requested")
        viewModelScope.launch {
            try {
                pendingConsolidationSceneId = _currentSceneId.value
                _observationControlState.value = ObservationControlState(
                    phase = ObservationControlPhase.AwaitingDrain,
                    message = "正在等待剩余片段处理完成…"
                )
                val result = withContext(Dispatchers.IO) {
                    sessionCoordinator.stopObservation(
                        currentSceneId = _currentSceneId.value,
                        currentSceneLabel = _currentSceneLabel.value,
                        lastSceneProbe = lastSceneProbe,
                        date = today(),
                        flushIngestion = { observationIngestionService.flushRemaining() }
                    )
                }
                result.updatedSceneProfile?.let(::bindCurrentScene)
                pendingConsolidationSceneId = result.finalSceneId
                if (result.enteredConsolidation) {
                    _agentStatus.value = portraitCuratorAgent.getStatus()
                    startStatusPolling()
                    _observationControlState.value = ObservationControlState(
                        phase = ObservationControlPhase.Consolidating,
                        message = "观察输入已停止，Agent 正在归一收敛…"
                    )
                    Log.d(TAG, "stopCommentary entered consolidation phase")
                } else {
                    Log.d(TAG, "stopCommentary found no running agent, finalize directly")
                    finalizeAgentConsolidation()
                }
            } catch (error: Exception) {
                Log.e(TAG, "stopCommentary pipeline failed", error)
                _observationControlState.value = ObservationControlState(
                    phase = ObservationControlPhase.Error,
                    message = error.message ?: "停止观察失败"
                )
            }
            refreshBlackboardDebugState()
        }
    }

    fun stopAgent() {
        val phase = _observationControlState.value.phase
        if (phase == ObservationControlPhase.StoppingAgent) return
        _observationControlState.value = ObservationControlState(
            phase = ObservationControlPhase.StoppingAgent,
            message = "正在停止 Agent…"
        )
        viewModelScope.launch {
            try {
                Log.d(TAG, "stopAgent requested, running=${portraitCuratorAgent.isRunning}")
                if (portraitCuratorAgent.isRunning) {
                    sessionCoordinator.requestAgentStop()
                    _agentStatus.value = portraitCuratorAgent.getStatus()
                    startStatusPolling()
                } else {
                    finalizeAgentConsolidation()
                }
            } catch (error: Exception) {
                Log.e(TAG, "stopAgent failed", error)
                _observationControlState.value = ObservationControlState(
                    phase = ObservationControlPhase.Error,
                    message = error.message ?: "停止 Agent 失败"
                )
            }
            refreshBlackboardDebugState()
        }
    }

    fun resetCommentary() {
        if (_observationControlState.value.isBusy || portraitCuratorAgent.isRunning) return
        stopStatusPolling()
        liveCommentaryRepository.fullReset()
        observationIngestionService.reset()
        lastSceneProbe = null
        pendingConsolidationSceneId = null
        _lastMatchedSceneId.value = null
        _lastMatchBreakdown.value = null
        _agentStatus.value = PortraitCuratorStatus.Idle
        _observationControlState.value = ObservationControlState()
        refreshBlackboardDebugState()
    }

    fun regeneratePortrait(sceneId: String) {
        viewModelScope.launch {
            if (sceneId.isBlank()) return@launch
            if (commentaryState.value.isActive || commentaryState.value.isDraining || _observationControlState.value.isBusy) {
                return@launch
            }
            stopStatusPolling()
            sessionCoordinator.regenerateSceneModel(
                sceneId = sceneId,
                fallbackSceneLabel = _currentSceneLabel.value,
                completedEntries = commentaryState.value.entries.filter { it.status == CommentaryEntryStatus.Completed }
            )
            _agentStatus.value = portraitCuratorAgent.getStatus()
            startStatusPolling()
            refreshBlackboardDebugState()
        }
    }

    fun renameScene(sceneId: String, newLabel: String) {
        viewModelScope.launch {
            portraitModelService.renameScene(sceneId, newLabel)
            if (_currentSceneId.value == sceneId) {
                val updated = portraitModelService.getSceneById(sceneId)
                _currentSceneLabel.value = updated?.let(portraitModelService::displaySceneLabel) ?: _currentSceneLabel.value
                portraitCuratorAgent.currentSceneLabel = _currentSceneLabel.value
                refreshBlackboardDebugState()
            }
        }
    }

    fun mergeScenes(sourceSceneId: String, targetSceneId: String) {
        if (sourceSceneId == targetSceneId) return
        viewModelScope.launch {
            val merged = portraitModelService.mergeScenes(sourceSceneId, targetSceneId) ?: return@launch
            if (_currentSceneId.value == sourceSceneId) {
                bindCurrentScene(merged)
            }
            if (_lastMatchedSceneId.value == sourceSceneId) {
                _lastMatchedSceneId.value = null
                _lastMatchBreakdown.value = null
            }
            refreshBlackboardDebugState()
        }
    }

    fun runClaimConsolidation(sceneId: String) {
        if (sceneId.isBlank() || _claimConsolidationUiState.value.isRunning) return
        if (commentaryState.value.isActive || commentaryState.value.isDraining || portraitCuratorAgent.isRunning) {
            _claimConsolidationUiState.value = ClaimConsolidationUiState(
                sceneId = sceneId,
                isRunning = false,
                errorMessage = "请先停止观察流并等待 Agent 空闲后再手动归一",
                updatedAt = System.currentTimeMillis()
            )
            return
        }
        viewModelScope.launch {
            _claimConsolidationUiState.value = ClaimConsolidationUiState(
                sceneId = sceneId,
                isRunning = true,
                updatedAt = System.currentTimeMillis()
            )
            try {
                val execution = withContext(Dispatchers.IO) {
                    portraitModelService.runManualClaimConsolidation(
                        sceneId = sceneId,
                        currentSceneId = _currentSceneId.value,
                        currentSceneLabel = _currentSceneLabel.value
                    )
                }
                _claimConsolidationUiState.value = ClaimConsolidationUiState(
                    sceneId = sceneId,
                    isRunning = false,
                    mergedCount = execution.result.mergedCount,
                    reason = execution.result.reason,
                    summaries = execution.result.summaries,
                    updatedAt = System.currentTimeMillis()
                )
                Log.d(
                    TAG,
                    "manual claim consolidation finished, sceneId=$sceneId merged=${execution.result.mergedCount} reason=${execution.result.reason}"
                )
            } catch (error: Exception) {
                Log.e(TAG, "manual claim consolidation failed, sceneId=$sceneId", error)
                _claimConsolidationUiState.value = ClaimConsolidationUiState(
                    sceneId = sceneId,
                    isRunning = false,
                    errorMessage = error.message ?: "归一失败",
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    fun loadBlackboardEntries(date: String) {
        viewModelScope.launch {
            _selectedDayEntries.value = database.blackboardDao().getEntriesByDate(date)
        }
    }

    private fun startStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val status = portraitCuratorAgent.getStatus()
                    _agentStatus.value = status
                    portraitCuratorAgent.refreshMemoryDebug()
                    if (status is PortraitCuratorStatus.Running && status.isTerminal) {
                        Log.d(
                            TAG,
                            "status polling detected terminal agent state=${status.lifecycleState}, stopReason=${status.stopReason}"
                        )
                        val cleared = portraitCuratorAgent.clearTerminalRuntime()
                        if (cleared) {
                            _agentStatus.value = PortraitCuratorStatus.Idle
                            withContext(Dispatchers.IO) {
                                finalizeAgentConsolidation()
                            }
                            break
                        }
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "status polling failed", error)
                    _observationControlState.value = ObservationControlState(
                        phase = ObservationControlPhase.Error,
                        message = error.message ?: "Agent 状态轮询失败"
                    )
                    break
                }
                delay(3_000L)
            }
            statusPollingJob = null
        }
    }

    private fun stopStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = null
    }

    private suspend fun finalizeAgentConsolidation() {
        Log.d(TAG, "finalizeAgentConsolidation start, sceneId=$pendingConsolidationSceneId")
        val shouldResetControlState = pendingConsolidationSceneId != null ||
            _observationControlState.value.phase == ObservationControlPhase.StoppingObservation ||
            _observationControlState.value.phase == ObservationControlPhase.AwaitingDrain ||
            _observationControlState.value.phase == ObservationControlPhase.Consolidating ||
            _observationControlState.value.phase == ObservationControlPhase.StoppingAgent
        pendingConsolidationSceneId?.let { portraitModelService.finalizeSceneSession(it) }
        pendingConsolidationSceneId = null
        _agentStatus.value = portraitCuratorAgent.getStatus()
        portraitCuratorAgent.refreshMemoryDebug()
        if (shouldResetControlState) {
            _observationControlState.value = ObservationControlState(
                message = "观察已结束，Agent 收尾完成"
            )
        }
        refreshBlackboardDebugState()
        Log.d(TAG, "finalizeAgentConsolidation end")
    }

    override fun onCleared() {
        super.onCleared()
        observationIngestionService.stop()
        liveCommentaryRepository.release()
        stopStatusPolling()
    }

    private fun refreshBlackboardDebugState() {
        val state = commentaryState.value
        val sharedFields = buildList {
            if (state.sessionId.isNotBlank()) {
                add(DebugField("sessionId", state.sessionId))
            }
            _currentSceneId.value?.let { add(DebugField("currentSceneId", it)) }
            add(DebugField("currentSceneLabel", _currentSceneLabel.value))
            add(DebugField("sceneMemory", state.sceneMemory))
            add(DebugField("entityMemory", state.entityMemory))
            add(DebugField("actionSummary", state.actionSummary))
            if (state.isDraining) {
                add(DebugField("drainState", "commentary pipeline draining"))
            }
            add(DebugField("memoryA", state.memoryA))
            add(DebugField("latestMemoryB", state.latestMemoryB))
            if (state.pendingAsks.isNotEmpty()) {
                add(DebugField("pendingAsks", state.pendingAsks.joinToString("\n")))
            }
            if (state.expertRequests.isNotEmpty()) {
                add(DebugField("expertRequests", state.expertRequests.joinToString("\n")))
            }
        }.filter { it.content.isNotBlank() }

        val categoryCounts = latestObservationItems
            .groupingBy { it.category }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { BlackboardCategoryCount(it.key, it.value) }

        val recentObservations = latestObservationItems
            .takeLast(12)
            .reversed()
            .map {
                BlackboardDebugObservation(
                    category = it.category,
                    dimensionHint = it.dimensionHint,
                    content = it.content,
                    segmentIndex = it.segmentIndex
                )
            }
        val ingestionState = observationIngestionService.debugState.value

        _blackboardDebugState.value = BlackboardDebugUiState(
            sessionId = state.sessionId,
            sharedKeys = sharedFields.map { it.key },
            sharedFields = sharedFields,
            categoryCounts = categoryCounts,
            recentObservations = recentObservations,
            observationItemCount = latestObservationItems.size,
            persistedSegmentCount = ingestionState.persistedSegmentCount,
            fedSignalCount = ingestionState.fedSignalCount,
            lastFedSignalSummary = ingestionState.lastFedSignalSummary,
            lastPersistedSegmentIndex = ingestionState.lastPersistedSegmentIndex
        )
    }

    private fun bindCurrentScene(profile: SceneProfile) {
        _currentSceneId.value = profile.sceneId
        _currentSceneLabel.value = portraitModelService.displaySceneLabel(profile)
        portraitCuratorAgent.currentSceneId = profile.sceneId
        portraitCuratorAgent.currentSceneLabel = _currentSceneLabel.value
        refreshBlackboardDebugState()
    }
}
