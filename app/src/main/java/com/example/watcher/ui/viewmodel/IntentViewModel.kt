package com.example.watcher.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.watcher.R
import com.example.watcher.watcherApplication
import com.example.watcher.data.local.AppDatabase
import com.example.watcher.data.model.BaselineSource
import com.example.watcher.data.model.DeviceProvisionUiState
import com.example.watcher.data.model.HistoryRecordDetail
import com.example.watcher.data.model.HistoryRecordType
import com.example.watcher.data.model.HistoryRecordSelection
import com.example.watcher.data.model.IntentResult
import com.example.watcher.data.model.MonitorLogEntry
import com.example.watcher.data.model.MonitorStatus
import com.example.watcher.data.model.MonitorTask
import com.example.watcher.data.model.DeviceRuntimeInfo
import com.example.watcher.data.model.DiscoveredStreamDevice
import com.example.watcher.data.model.DiscoveredStreamDeviceKind
import com.example.watcher.data.model.StreamScanUiState
import com.example.watcher.data.model.TimelineEventEntity
import com.example.watcher.data.model.VideoProcessTask
import com.example.watcher.data.model.VideoProcessTaskDraft
import com.example.watcher.data.model.VideoProcessingStatus
import com.example.watcher.data.model.VideoRunStatus
import com.example.watcher.data.model.VideoStreamSettings
import com.example.watcher.data.model.MonitorTaskTemplates
import com.example.watcher.data.model.MonitorTemplateEntity
import com.example.watcher.data.model.VideoTaskTemplates
import com.example.watcher.data.model.VideoTemplateEntity
import com.example.watcher.data.model.toMonitorTaskTemplate
import com.example.watcher.data.model.toVideoTaskTemplate
import com.example.watcher.data.remote.RetrofitClient
import com.example.watcher.data.repository.AndroidAlertNotifier
import com.example.watcher.data.repository.ArkConfig
import com.example.watcher.data.repository.BitmapEncoding
import com.example.watcher.data.repository.CouncilExpertRepository
import com.example.watcher.data.repository.DeviceProvisionCoordinator
import com.example.watcher.data.repository.HistoryRepository
import com.example.watcher.data.repository.IntentRepository
import com.example.watcher.data.repository.LanStreamScanner
import com.example.watcher.data.repository.MonitorManager
import com.example.watcher.data.repository.SnapshotStore
import com.example.watcher.data.repository.StreamDeviceCoordinator
import com.example.watcher.data.repository.VideoExecutionStatusUpdate
import com.example.watcher.data.repository.TemplateRepository
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AgentAudienceDebugSnapshot
import com.example.watcher.data.model.AudienceEngineType
import com.example.watcher.data.model.CouncilConfig
import com.example.watcher.data.model.CouncilEntryDraft
import com.example.watcher.data.model.CouncilEntryUiState
import com.example.watcher.data.model.CouncilExpertEntity
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.data.model.CouncilUiState
import com.example.watcher.data.model.InteractionMode
import com.example.watcher.data.model.MemorySnapshot
import com.example.watcher.data.model.AiAudienceLiveState
import com.example.watcher.data.model.GiftRankEntry
import com.example.watcher.data.model.LikeRankEntry
import com.example.watcher.data.model.DanmakuItem
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.LiveSpeechState
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.repository.AiAudienceManager
import com.example.watcher.data.repository.LiveSpeechRecognitionManager
import com.example.watcher.data.repository.TemplateShareManager
import com.example.watcher.data.remote.ArkStreamingClient
import com.example.watcher.data.repository.LiveCommentaryRepository
import com.example.watcher.data.repository.VideoProcessRepository
import com.example.watcher.data.repository.agent.AgentAudienceManager
import com.example.watcher.data.repository.council.CouncilEntryConfigGenerator
// Gateway imports moved to GatewayDelegate
import com.example.watcher.data.repository.council.CouncilManager
import com.example.watcher.data.remote.OpenAiCompatibleProvider
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class IntentViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val database = AppDatabase.getDatabase(application)
    private val llmWalletRepository = appContext.watcherApplication().agentFrameworkContainer.llmWalletRepository
    private val repository = IntentRepository(
        apiService = RetrofitClient.doubaoApiService,
        taskDao = database.monitorTaskDao(),
        llmWalletRepository = llmWalletRepository
    )
    val liveCommentaryRepository = LiveCommentaryRepository(
        apiService = RetrofitClient.doubaoApiService,
        streamingClient = ArkStreamingClient(),
        llmWalletRepository = llmWalletRepository
    )
    private val classicAudienceManager = AiAudienceManager(
        llmWalletRepository = llmWalletRepository,
        audienceDao = database.aiAudienceDao(),
        messageDao = database.aiAudienceMessageDao(),
        memoryManager = liveCommentaryRepository.memoryManager,
        sceneMemoryManager = liveCommentaryRepository.sceneMemoryManager
    )
    private val agentAudienceManager = AgentAudienceManager(
        llmWalletRepository = llmWalletRepository,
        audienceDao = database.aiAudienceDao(),
        messageDao = database.aiAudienceMessageDao(),
        memoryManager = liveCommentaryRepository.memoryManager,
        sceneMemoryManager = liveCommentaryRepository.sceneMemoryManager
    )
    private val councilManager = CouncilManager(
        llmWalletRepository = llmWalletRepository,
        councilExpertDao = database.councilExpertDao(),
        knowledgeDao = database.councilKnowledgeDao(),
        messageDao = database.aiAudienceMessageDao(),
        memoryManager = liveCommentaryRepository.memoryManager,
        sceneMemoryManager = liveCommentaryRepository.sceneMemoryManager
    )
    private val audienceTypeCache = MutableStateFlow<Map<Long, AudienceEngineType>>(emptyMap())
    private val _interactionMode = MutableStateFlow(InteractionMode.Off)
    val liveSpeechManager = LiveSpeechRecognitionManager(
        context = appContext,
        memoryManager = liveCommentaryRepository.memoryManager
    ).also {
        it.onSpeechResult = { text ->
            when (_interactionMode.value) {
                InteractionMode.Live -> {
                    classicAudienceManager.onSpeechEvent(text)
                    agentAudienceManager.onSpeechEvent(text)
                }
                InteractionMode.Council -> councilManager.onSpeechEvent(text)
                InteractionMode.Off -> Unit
            }
        }
    }
    private val videoRepository = VideoProcessRepository(
        apiService = RetrofitClient.doubaoApiService,
        taskDao = database.videoProcessTaskDao(),
        runDao = database.videoProcessRunDao(),
        segmentRunDao = database.videoSegmentRunDao(),
        timelineEventDao = database.timelineEventDao(),
        llmWalletRepository = llmWalletRepository
    )
    private val historyRepository = HistoryRepository(
        monitorRunDao = database.monitorRunDao(),
        monitorEventDao = database.monitorEventDao(),
        monitorMediaDao = database.monitorMediaDao(),
        videoRunDao = database.videoProcessRunDao(),
        videoSegmentRunDao = database.videoSegmentRunDao(),
        timelineEventDao = database.timelineEventDao()
    )
    private val migrationPreferences = appContext.getSharedPreferences(
        "watcher_migrations",
        Context.MODE_PRIVATE
    )
    private val alertNotifier = AndroidAlertNotifier(appContext)
    private val snapshotStore = SnapshotStore(appContext)
    private val lanStreamScanner = LanStreamScanner(appContext)
    private val templateRepository = TemplateRepository(database.templateDao())
    private val councilExpertRepository = CouncilExpertRepository(database.councilExpertDao())
    private val councilEntryGenerator = CouncilEntryConfigGenerator()

    init {
        viewModelScope.launch {
            database.aiAudienceDao().observeAll().collect { audiences ->
                audienceTypeCache.value = audiences.associate { it.id to it.audienceType }
            }
        }
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    private val _currentIntentResult = MutableStateFlow<IntentResult?>(null)
    private val _pendingBaselineImagePath = MutableStateFlow<String?>(null)
    private val _pendingBaselineBase64 = MutableStateFlow<String?>(null)
    private val _councilEntryUiState = MutableStateFlow(CouncilEntryUiState())
    private val _isStreamPlaying = MutableStateFlow(true)
    private val _streamReconnectToken = MutableStateFlow(0)
    // Device state delegated to DeviceDelegate
    private val _videoPlanUiState = MutableStateFlow<VideoPlanUiState>(VideoPlanUiState.Idle)
    private val _currentVideoTask = MutableStateFlow<VideoProcessTaskDraft?>(null)
    private val _videoProcessingStatus = MutableStateFlow(VideoProcessingStatus())
    private val _selectedVideoRunId = MutableStateFlow<Long?>(null)
    private val _selectedVideoRunEvents = MutableStateFlow<List<TimelineEventEntity>>(emptyList())
    // History state delegated to HistoryDelegate

    private var lastPersistedCheckTime = 0L
    // streamScanJob and deviceProvisionJob moved to DeviceDelegate
    private var videoProcessingJob: Job? = null
    private var videoStopRequested = AtomicBoolean(false)

    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val interactionMode: StateFlow<InteractionMode> = _interactionMode.asStateFlow()
    val currentIntentResult: StateFlow<IntentResult?> = _currentIntentResult.asStateFlow()
    val pendingBaselineImagePath: StateFlow<String?> = _pendingBaselineImagePath.asStateFlow()
    val pendingBaselineBase64: StateFlow<String?> = _pendingBaselineBase64.asStateFlow()
    val isStreamPlaying: StateFlow<Boolean> = _isStreamPlaying.asStateFlow()
    val streamReconnectToken: StateFlow<Int> = _streamReconnectToken.asStateFlow()
    val streamScanUiState: StateFlow<StreamScanUiState> get() = deviceDelegate.streamScanUiState
    val deviceProvisionUiState: StateFlow<DeviceProvisionUiState> get() = deviceDelegate.deviceProvisionUiState
    val settingsNotice: StateFlow<String?> get() = deviceDelegate.settingsNotice
    val videoPlanUiState: StateFlow<VideoPlanUiState> = _videoPlanUiState.asStateFlow()
    val currentVideoTask: StateFlow<VideoProcessTaskDraft?> = _currentVideoTask.asStateFlow()
    val videoProcessingStatus: StateFlow<VideoProcessingStatus> = _videoProcessingStatus.asStateFlow()
    val selectedVideoRunId: StateFlow<Long?> = _selectedVideoRunId.asStateFlow()
    val selectedVideoRunEvents: StateFlow<List<TimelineEventEntity>> = _selectedVideoRunEvents.asStateFlow()
    val selectedHistoryRecord: StateFlow<HistoryRecordSelection?> get() = historyDelegate.selectedHistoryRecord
    val selectedHistoryDetail: StateFlow<HistoryRecordDetail?> get() = historyDelegate.selectedHistoryDetail

    val tasksFlow = repository.observeTasks()
    val videoTasksFlow = videoRepository.observeTasks()
    val recentVideoRunsFlow = videoRepository.observeRecentRuns()
    val historyRecordsFlow = historyRepository.observeHistoryRecords()
    val storageSummaryFlow = historyRepository.observeStorageSummary()
    val videoStreamSettings = database.videoStreamSettingsDao().getSettings()
    val monitorTemplatesFlow = templateRepository.monitorTemplates
    val videoTemplatesFlow = templateRepository.videoTemplates
    val councilTemplatesFlow = templateRepository.councilTemplates
    val councilExpertsFlow = councilExpertRepository.experts

    val monitorManager = MonitorManager(
        apiService = RetrofitClient.doubaoApiService,
        alertNotifier = alertNotifier,
        historyRepository = historyRepository,
        snapshotStore = snapshotStore,
        llmWalletRepository = llmWalletRepository
    )
    private val streamDeviceCoordinator = StreamDeviceCoordinator(monitorManager)
    private val deviceDelegate = DeviceDelegate(
        scope = viewModelScope,
        settingsDao = database.videoStreamSettingsDao(),
        lanStreamScanner = lanStreamScanner,
        streamDeviceCoordinator = streamDeviceCoordinator,
        migrationPreferences = migrationPreferences,
        onReconnectStream = ::reconnectStream
    )
    private val templateDelegate = TemplateDelegate(
        scope = viewModelScope,
        templateRepository = templateRepository,
        templateDao = database.templateDao(),
        councilExpertRepository = councilExpertRepository
    )
    private val historyDelegate = HistoryDelegate(
        scope = viewModelScope,
        historyRepository = historyRepository,
        videoRepository = videoRepository,
        selectedVideoRunId = _selectedVideoRunId,
        selectedVideoRunEvents = _selectedVideoRunEvents
    )
    // ── Gateway delegation ────────────────────────────────────
    private val gatewayDelegate = GatewayDelegate(
        scope = viewModelScope,
        appContext = appContext,
        monitorManager = monitorManager,
        intentRepository = repository,
        historyRepository = historyRepository,
        videoRepository = videoRepository,
        agentService = appContext.watcherApplication().agentFrameworkContainer.service,
        liveCommentaryRepository = liveCommentaryRepository,
        liveSpeechManager = liveSpeechManager,
        streamUrlProvider = { monitorManager.currentStreamUrl },
        onStreamRelease = { _isStreamPlaying.value = false },
        onStreamReclaim = { reconnectStream() }
    )
    val gatewayRunning: StateFlow<Boolean> get() = gatewayDelegate.running
    val gatewayApiKey: String get() = gatewayDelegate.apiKey
    val gatewayPort: Int get() = gatewayDelegate.port
    fun toggleGateway(enabled: Boolean) = gatewayDelegate.toggle(enabled)
    fun getLocalIpAddress(): String = gatewayDelegate.getLocalIpAddress()

    val monitorStatus: StateFlow<MonitorStatus> get() = monitorManager.monitorStatus
    val monitorLogs: StateFlow<List<MonitorLogEntry>> get() = monitorManager.monitorLogs

    init {
        initializeVideoSettings()
        observeVideoSettings()
        observeMonitorStatusPersistence()
        historyDelegate.startObserving()
    }

    fun analyzeIntent(userInput: String) {
        if (userInput.isBlank()) {
            _uiState.value = UiState.Error(appContext.getString(R.string.error_empty_request))
            return
        }

        stopMonitoringIfRunning()

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val selectedBaselineBase64 = _pendingBaselineBase64.value
            val selectedBaselinePath = _pendingBaselineImagePath.value
            val baselineBitmap = selectedBaselineBase64?.let(::decodeBase64Bitmap)
            val fallbackFrame = monitorManager.currentFrame.value
            val effectiveBitmap = baselineBitmap ?: fallbackFrame
            val effectiveSource = if (selectedBaselineBase64 != null) {
                BaselineSource.UploadedImage
            } else {
                BaselineSource.CapturedFrame
            }
            repository.analyzeIntent(
                userInput = userInput,
                frame = effectiveBitmap,
                baselineSource = effectiveSource,
                baselineImagePath = if (effectiveSource == BaselineSource.UploadedImage) {
                    selectedBaselinePath
                } else {
                    null
                }
            )
                .onSuccess(::showMonitorIntentResult)
                .onFailure { error ->
                    _uiState.value = UiState.Error(
                        error.message ?: appContext.getString(R.string.error_analyze_request_failed)
                    )
                }
        }
    }

    fun analyzeVideoIntent(userInput: String) {
        if (userInput.isBlank()) {
            _videoPlanUiState.value = VideoPlanUiState.Error(appContext.getString(R.string.error_empty_request))
            return
        }

        viewModelScope.launch {
            showVideoPlanningInProgress()
            videoRepository.planVideoTask(userInput, monitorManager.currentFrame.value)
                .onSuccess { plan ->
                    val draft = plan.toDraft(userInput)
                    showVideoDraftReady(
                        draft = draft,
                        message = "Plan generated. Review it or tweak it before recording.",
                    )
                }
                .onFailure { error ->
                    _videoPlanUiState.value = VideoPlanUiState.Error(error.message ?: "视频规划失败")
                    _videoProcessingStatus.value = VideoProcessingStatus(
                        stage = VideoRunStatus.Failed,
                        message = "视频规划失败",
                        errorMessage = error.message,
                        isBusy = false
                    )
                }
        }
    }

    fun loadTask(task: MonitorTask) {
        stopMonitoringIfRunning()
        showMonitorIntentResult(IntentResult.fromTask(task))
    }

    fun loadVideoTask(task: VideoProcessTask) {
        showVideoDraftReady(
            draft = VideoProcessTaskDraft.fromEntity(task),
            message = "Video task loaded. You can refine it or start recording now.",
        )
    }

    fun saveCurrentTask(result: IntentResult) {
        viewModelScope.launch {
            val shouldRestartMonitoring = shouldRestartMonitoring(result.taskId)
            runCatching { repository.saveTask(result) }
                .onSuccess { saved ->
                    showMonitorIntentResult(saved)
                    if (shouldRestartMonitoring) {
                        restartMonitoring(saved)
                    }
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(
                        error.message ?: appContext.getString(R.string.error_save_task_failed)
                    )
                }
        }
    }

    fun saveVideoTask(draft: VideoProcessTaskDraft) {
        viewModelScope.launch {
            runCatching { videoRepository.saveTask(draft) }
                .onSuccess { saved ->
                    showVideoDraftReady(
                        draft = saved,
                        message = "Video task saved.",
                    )
                }
                .onFailure { error ->
                    _videoPlanUiState.value = VideoPlanUiState.Error(error.message ?: "视频任务保存失败")
                }
        }
    }

    fun refreshBaselineFromCurrentFrame() {
        val frame = monitorManager.currentFrame.value ?: run {
            _uiState.value = UiState.Error(
                appContext.getString(R.string.error_refresh_baseline_no_stream)
            )
            return
        }
        val currentResult = _currentIntentResult.value ?: run {
            _uiState.value = UiState.Error(
                appContext.getString(R.string.error_refresh_baseline_no_task)
            )
            return
        }

        viewModelScope.launch {
            val shouldRestartMonitoring = shouldRestartMonitoring(currentResult.taskId)
            runCatching {
                val reParsed = repository.analyzeIntent(
                    userInput = currentResult.userInput,
                    frame = frame,
                    baselineSource = BaselineSource.CapturedFrame,
                    baselineImagePath = null,
                    persist = false
                )
                    .getOrThrow()
                    .copy(taskId = currentResult.taskId, createdAt = currentResult.createdAt)
                _pendingBaselineImagePath.value = null
                _pendingBaselineBase64.value = null
                repository.saveTask(reParsed)
            }.onSuccess { updated ->
                showMonitorIntentResult(updated)
                if (shouldRestartMonitoring) {
                    restartMonitoring(updated)
                }
            }.onFailure { error ->
                _uiState.value = UiState.Error(
                    error.message ?: appContext.getString(R.string.error_refresh_baseline_failed)
                )
            }
        }
    }

    fun setBaselineFromPickedImage(uri: Uri) {
        viewModelScope.launch {
            android.util.Log.d("ImageUpload", "setBaselineFromPickedImage called, uri=$uri")
            val currentResult = _currentIntentResult.value
            val shouldRestartMonitoring = shouldRestartMonitoring(currentResult?.taskId)
            runCatching {
                android.util.Log.d("ImageUpload", "Importing image...")
                val importedPath = importBaselineImage(uri)
                    ?: error("Unable to import the selected image.")
                android.util.Log.d("ImageUpload", "Imported to: $importedPath")
                val bitmap = decodeBitmap(uri)
                    ?: error("Unable to read the selected image.")
                android.util.Log.d("ImageUpload", "Decoded bitmap: ${bitmap.width}x${bitmap.height}")
                _pendingBaselineImagePath.value = importedPath
                _pendingBaselineBase64.value = BitmapEncoding.toBase64(bitmap)
                if (currentResult == null) {
                    return@runCatching null
                }
                val reParsed = repository.analyzeIntent(
                    userInput = currentResult.userInput,
                    frame = bitmap,
                    baselineSource = BaselineSource.UploadedImage,
                    baselineImagePath = importedPath,
                    persist = false
                )
                    .getOrThrow()
                    .copy(taskId = currentResult.taskId, createdAt = currentResult.createdAt)
                return@runCatching repository.saveTask(reParsed)
            }.onSuccess { updated ->
                updated?.let {
                    showMonitorIntentResult(it)
                    if (shouldRestartMonitoring) {
                        restartMonitoring(it)
                    }
                }
            }.onFailure { error ->
                android.util.Log.e("ImageUpload", "Failed: ${error.message}", error)
                _uiState.value = UiState.Error(error.message ?: "导入基准图片失败")
            }
        }
    }

    fun applyMonitorTemplate(templateId: String) {
        viewModelScope.launch {
            val entity = templateRepository.getMonitorTemplate(templateId)
            if (entity != null) {
                showMonitorIntentResult(entity.toMonitorTaskTemplate().toIntentResult())
            } else {
                val fallback = MonitorTaskTemplates.findById(templateId) ?: return@launch
                showMonitorIntentResult(fallback.toIntentResult())
            }
        }
    }

    fun applyVideoTemplate(templateId: String) {
        viewModelScope.launch {
            val entity = templateRepository.getVideoTemplate(templateId)
            if (entity != null) {
                showVideoDraftReady(
                    draft = entity.toVideoTaskTemplate().toDraft(),
                    message = "Template loaded. You can refine it before execution.",
                )
            } else {
                val fallback = VideoTaskTemplates.findById(templateId) ?: return@launch
                showVideoDraftReady(
                    draft = fallback.toDraft(),
                    message = "Template loaded. You can refine it before execution.",
                )
            }
        }
    }

    // ── Template delegation ─────────────────────────────────────
    fun updateMonitorTemplate(entity: MonitorTemplateEntity) = templateDelegate.updateMonitorTemplate(entity)
    fun updateVideoTemplate(entity: VideoTemplateEntity) = templateDelegate.updateVideoTemplate(entity)
    fun updateCouncilTemplate(entity: CouncilTemplateEntity) = templateDelegate.updateCouncilTemplate(entity)
    fun resetMonitorTemplate(templateId: String) = templateDelegate.resetMonitorTemplate(templateId)
    fun resetVideoTemplate(templateId: String) = templateDelegate.resetVideoTemplate(templateId)
    fun resetCouncilTemplate(templateId: String) = templateDelegate.resetCouncilTemplate(templateId)

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            if (_currentIntentResult.value?.taskId == id) {
                monitorManager.stopMonitoring()
                _currentIntentResult.value = null
                _pendingBaselineImagePath.value = null
                _pendingBaselineBase64.value = null
                _uiState.value = UiState.Idle
            }
            repository.deleteTask(id)
        }
    }

    fun deleteVideoTask(id: Long) {
        viewModelScope.launch {
            if (_currentVideoTask.value?.taskId == id) {
                clearSelectedVideoTaskState()
            }
            videoRepository.deleteTask(id)
        }
    }

    fun setStreamPlaying(playing: Boolean) {
        _isStreamPlaying.value = playing
    }

    fun reconnectStream() {
        _isStreamPlaying.value = true
        _streamReconnectToken.value = _streamReconnectToken.value + 1
    }

    fun updateVideoFrame(bitmap: android.graphics.Bitmap?) {
        monitorManager.setCurrentFrame(bitmap)
    }

    fun startMonitoring(task: IntentResult) {
        viewModelScope.launch {
            startMonitoringInternal(task)
        }
    }

    fun pauseMonitoring() {
        monitorManager.pauseMonitoring()
    }

    fun resumeMonitoring() {
        monitorManager.resumeMonitoring()
    }

    fun stopMonitoring() {
        monitorManager.stopMonitoring()
    }

    fun startVideoProcessing(
        task: VideoProcessTaskDraft? = _currentVideoTask.value,
        streamingOutputEnabled: Boolean = false
    ) = launchVideoProcessing(task = task, streamingOutputEnabled = streamingOutputEnabled)

    fun stopVideoProcessing() = requestStopVideoProcessing()

    fun selectVideoRun(runId: Long?) {
        _selectedVideoRunId.value = runId
    }

    fun launchVideoProcessing(
        task: VideoProcessTaskDraft? = _currentVideoTask.value,
        streamingOutputEnabled: Boolean = false
    ) {
        val draft = task?.normalized() ?: run {
            _videoPlanUiState.value = VideoPlanUiState.Error("Create or load a video task first.")
            return
        }

        _currentVideoTask.value = draft
        resetVideoProcessingJob()
        _selectedVideoRunId.value = null
        val effectiveStreamingEnabled = streamingOutputEnabled || draft.autoStartStreamingOutput

        var launchedJob: Job? = null
        launchedJob = viewModelScope.launch {
            var activeRunId: Long? = null
            try {
                showVideoExecutionStarting(draft, effectiveStreamingEnabled)
                val result = videoRepository.executeTask(
                    draft = draft,
                    streamingOutputEnabled = effectiveStreamingEnabled,
                    latestFrameProvider = { monitorManager.currentFrame.value },
                    outputRoot = appContext.filesDir,
                    shouldStopRequested = { videoStopRequested.get() },
                    onStatus = { update ->
                        activeRunId = update.runId
                        _selectedVideoRunId.value = update.runId
                        applyVideoStatusUpdate(draft, update)
                    }
                )
                _selectedVideoRunId.value = result.run.id
            } catch (_: CancellationException) {
                handleVideoProcessingCancellation(
                    runId = activeRunId,
                    draft = draft,
                    streamingEnabled = effectiveStreamingEnabled
                )
            } catch (error: Exception) {
                handleVideoProcessingFailure(
                    runId = activeRunId,
                    draft = draft,
                    streamingEnabled = effectiveStreamingEnabled,
                    error = error
                )
            } finally {
                clearVideoProcessingJobIfMatches(launchedJob)
            }
        }
        videoProcessingJob = launchedJob
    }

    fun requestStopVideoProcessing() {
        if (videoProcessingJob?.isActive != true) {
            return
        }
        videoStopRequested.set(true)
        _videoProcessingStatus.value = _videoProcessingStatus.value.copy(
            message = "Stopping new captures and summarizing recorded segments.",
            errorMessage = null,
            stopRequested = true,
            isBusy = true
        )
    }

    // ── History delegation ──────────────────────────────────────
    fun selectHistoryRecord(selection: HistoryRecordSelection?) = historyDelegate.selectHistoryRecord(selection)
    fun deleteHistoryRecord(selection: HistoryRecordSelection) = historyDelegate.deleteHistoryRecord(selection)

    // ── Device delegation ───────────────────────────────────────
    fun saveVideoStreamSettings(settings: VideoStreamSettings) = deviceDelegate.saveVideoStreamSettings(settings)
    fun refreshDeviceProvisionInfo() = deviceDelegate.refreshDeviceProvisionInfo()
    fun scanProvisioningWifi() = deviceDelegate.scanProvisioningWifi()
    fun submitProvisioningWifi(ssid: String, password: String) = deviceDelegate.submitProvisioningWifi(ssid, password)
    fun clearProvisionedWifi() = deviceDelegate.clearProvisionedWifi()
    fun scanVideoStreamDevices() = deviceDelegate.scanVideoStreamDevices()
    fun clearStreamDeviceScan() = deviceDelegate.clearStreamDeviceScan()
    fun clearDeviceProvisionState() = deviceDelegate.clearDeviceProvisionState()
    fun consumeSettingsNotice() = deviceDelegate.consumeSettingsNotice()

    fun saveSnapshot(bitmap: android.graphics.Bitmap): String? {
        return snapshotStore.save(bitmap)?.also(monitorManager::attachSnapshot)
    }

    // --- Live commentary ---

    val liveCommentaryState: StateFlow<LiveCommentaryState>
        get() = liveCommentaryRepository.commentaryState

    fun startLiveCommentary() {
        android.util.Log.d("LiveCommentary", "ViewModel.startLiveCommentary, currentFrame=${monitorManager.currentFrame.value != null}")
        liveCommentaryRepository.startCommentary(
            outputRoot = appContext.filesDir,
            latestFrameProvider = { monitorManager.currentFrame.value },
            speechProvider = { liveSpeechManager.getFinalTranscripts() }
        )
    }

    fun stopLiveCommentary() {
        android.util.Log.d("LiveCommentary", "ViewModel.stopLiveCommentary")
        liveCommentaryRepository.stopCommentary()
        classicAudienceManager.stop()
        agentAudienceManager.stop()
        councilManager.stop()
        liveSpeechManager.stop()
        _interactionMode.value = InteractionMode.Off
    }

    // --- AI Audience ---

    val aiAudienceLiveState: StateFlow<AiAudienceLiveState> = combine(
        classicAudienceManager.liveState,
        agentAudienceManager.liveState
    ) { classic, agent ->
        mergeAudienceStates(classic, agent)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiAudienceLiveState()
    )

    val danmakuFlow: SharedFlow<DanmakuItem> = merge(
        classicAudienceManager.danmakuFlow,
        agentAudienceManager.danmakuFlow
    ).shareIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        replay = 0
    )

    fun startAiAudience() {
        if (_interactionMode.value == InteractionMode.Council) {
            councilManager.stop()
        }
        classicAudienceManager.start(
            frameProvider = { monitorManager.currentFrame.value },
            speechProvider = { liveSpeechManager.getFinalTranscripts() }
        )
        agentAudienceManager.start(
            frameProvider = { monitorManager.currentFrame.value },
            speechProvider = { liveSpeechManager.getFinalTranscripts() }
        )
        _interactionMode.value = InteractionMode.Live
    }

    fun stopAiAudience() {
        classicAudienceManager.compressMemories()
        agentAudienceManager.compressMemories()
        classicAudienceManager.stop()
        agentAudienceManager.stop()
        if (_interactionMode.value == InteractionMode.Live) {
            _interactionMode.value = InteractionMode.Off
        }
    }

    // --- Live Speech ---

    val liveSpeechState: StateFlow<LiveSpeechState>
        get() = liveSpeechManager.state

    val councilState: StateFlow<CouncilUiState>
        get() = councilManager.state

    val councilEntryUiState: StateFlow<CouncilEntryUiState>
        get() = _councilEntryUiState.asStateFlow()

    fun startLiveSpeech() {
        liveSpeechManager.start()
    }

    fun stopLiveSpeech() {
        liveSpeechManager.stop()
    }

    fun startCouncilMode(config: CouncilConfig) {
        stopAiAudience()
        _interactionMode.value = InteractionMode.Council
        startLiveCommentary()
        startLiveSpeech()
        councilManager.start(
            frameProvider = { monitorManager.currentFrame.value },
            speechProvider = { liveSpeechManager.getFinalTranscripts() },
            initialConfig = config
        )
    }

    fun updateCouncilConfig(config: CouncilConfig) {
        councilManager.updateConfig(config)
    }

    fun triggerCouncilAnalysis(reason: String = "manual") {
        councilManager.triggerAnalysis(reason)
    }

    fun stopCouncilMode() {
        councilManager.stop()
        liveSpeechManager.stop()
        liveCommentaryRepository.stopCommentary()
        _interactionMode.value = InteractionMode.Off
    }

    fun updateCouncilEntryDraft(draft: CouncilEntryDraft) {
        _councilEntryUiState.value = _councilEntryUiState.value.copy(
            draft = draft,
            errorMessage = null,
            statusMessage = null
        )
    }

    fun applyCouncilEntryTemplate(templateId: String) {
        viewModelScope.launch {
            val template = templateRepository.getCouncilTemplate(templateId) ?: return@launch
            val generated = councilEntryGenerator.fromTemplate(template)
            _councilEntryUiState.value = CouncilEntryUiState(
                draft = CouncilEntryDraft(
                    scene = generated.config.sceneType.name,
                    userNeed = generated.config.objective,
                    concern = generated.config.focus,
                    sourceTemplateId = templateId
                ),
                generated = generated,
                statusMessage = "模板已加载到智囊团简报中。"
            )
        }
    }

    fun resetCouncilEntryDraft() {
        _councilEntryUiState.value = CouncilEntryUiState()
    }

    fun generateCouncilEntryConfig(draft: CouncilEntryDraft) {
        viewModelScope.launch {
            if (!draft.canGenerate()) {
                _councilEntryUiState.value = _councilEntryUiState.value.copy(
                    errorMessage = "请至少填写一项场景信息后再生成。",
                    statusMessage = null
                )
                return@launch
            }

            _councilEntryUiState.value = _councilEntryUiState.value.copy(
                draft = draft,
                isGenerating = true,
                errorMessage = null,
                statusMessage = "正在生成智囊团配置…"
            )

            val templates = templateRepository.councilTemplatesFirstSnapshot()
            val experts = database.councilExpertDao().getAll()
            try {
                val provider = llmWalletRepository.resolveOpenAiProvider(ArkConfig.intentModel)
                val generated = councilEntryGenerator.generate(
                    provider = provider,
                    draft = draft,
                    templates = templates,
                    availableExperts = experts
                )
                _councilEntryUiState.value = _councilEntryUiState.value.copy(
                    isGenerating = false,
                    generated = generated,
                    errorMessage = null,
                    statusMessage = "智囊团配置已就绪。"
                )
            } catch (e: Exception) {
                _councilEntryUiState.value = _councilEntryUiState.value.copy(
                    isGenerating = false,
                    errorMessage = "生成失败：${e.message ?: "未知错误"}",
                    statusMessage = null
                )
            }
        }
    }

    fun saveGeneratedCouncilTemplate(label: String) {
        val generated = _councilEntryUiState.value.generated ?: return
        val finalLabel = label.trim().ifBlank { generated.title }
        viewModelScope.launch {
            templateRepository.updateCouncilTemplate(
                CouncilTemplateEntity(
                    templateId = "generated_${UUID.randomUUID().toString().take(8)}",
                    label = finalLabel,
                    description = generated.summary,
                    sceneType = generated.config.sceneType.name,
                    objective = generated.config.objective,
                    focus = generated.config.focus,
                    speakerRole = generated.config.speakerRole,
                    targetRole = generated.config.targetRole,
                    background = generated.config.background,
                    isDefault = false
                )
            )
            _councilEntryUiState.value = _councilEntryUiState.value.copy(
                statusMessage = "Council template saved.",
                errorMessage = null
            )
        }
    }

    fun resetLiveRoom() {
        viewModelScope.launch {
            // Clear all memory (general + scene)
            liveCommentaryRepository.fullReset()
            // Clear AI audience messages
            database.aiAudienceMessageDao().deleteAll()
            // Clear speech transcripts (stop + restart picks up clean state)
            liveSpeechManager.stop()
            liveSpeechManager.start()
            // Full reset AI audience (clears all debug/session data too)
            classicAudienceManager.fullReset()
            agentAudienceManager.fullReset()
            councilManager.stop()
            classicAudienceManager.start(
                frameProvider = { monitorManager.currentFrame.value },
                speechProvider = { liveSpeechManager.getFinalTranscripts() }
            )
            agentAudienceManager.start(
                frameProvider = { monitorManager.currentFrame.value },
                speechProvider = { liveSpeechManager.getFinalTranscripts() }
            )
            _interactionMode.value = InteractionMode.Live
            android.util.Log.d("LiveRoom", "All context reset")
        }
    }

    fun setLiveSpeechMicEnabled(enabled: Boolean) {
        liveSpeechManager.setMicEnabled(enabled)
    }

    val llmProvidersFlow get() = llmWalletRepository.observeProviders()
    val aiAudiencesFlow get() = database.aiAudienceDao().observeAll()

    fun saveProvider(provider: LlmProviderEntity) {
        viewModelScope.launch { llmWalletRepository.upsertProvider(provider, makeDefault = true) }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch { llmWalletRepository.deleteProvider(id) }
    }

    fun saveAudience(audience: AiAudienceEntity) {
        viewModelScope.launch { database.aiAudienceDao().upsert(audience) }
    }

    fun saveCouncilExpert(expert: CouncilExpertEntity) {
        viewModelScope.launch { councilExpertRepository.updateExpert(expert) }
    }

    fun addCouncilExpert() {
        viewModelScope.launch { councilExpertRepository.createExpert() }
    }

    fun duplicateCouncilExpert(expert: CouncilExpertEntity) {
        viewModelScope.launch { councilExpertRepository.duplicateExpert(expert) }
    }

    fun resetCouncilExpert(expertId: String) {
        viewModelScope.launch { councilExpertRepository.resetExpert(expertId) }
    }

    fun deleteCouncilExpert(expertId: String) {
        viewModelScope.launch { councilExpertRepository.deleteExpert(expertId) }
    }

    fun restoreMissingCouncilExperts() {
        viewModelScope.launch { councilExpertRepository.restoreMissingSystemPresets() }
    }

    fun deleteAudience(id: Long) {
        viewModelScope.launch { database.aiAudienceDao().deleteById(id) }
    }

    // --- Template Share/Import ---

    fun deleteMonitorTemplate(templateId: String) = templateDelegate.deleteMonitorTemplate(templateId)
    fun deleteVideoTemplate(templateId: String) = templateDelegate.deleteVideoTemplate(templateId)
    fun deleteCouncilTemplate(templateId: String) = templateDelegate.deleteCouncilTemplate(templateId)
    fun exportMonitorTemplate(template: MonitorTemplateEntity): String = templateDelegate.exportMonitorTemplate(template)
    fun exportVideoTemplate(template: VideoTemplateEntity): String = templateDelegate.exportVideoTemplate(template)
    fun exportCouncilTemplate(template: CouncilTemplateEntity): String = templateDelegate.exportCouncilTemplate(template)
    fun exportCouncilExpertTemplate(expert: CouncilExpertEntity): String = templateDelegate.exportCouncilExpertTemplate(expert)
    fun importTemplate(text: String, onResult: (String) -> Unit) = templateDelegate.importTemplate(text, onResult)

    // --- Management / Debug ---
    fun getAudienceLastPost(audienceId: Long): String? =
        audienceManagerFor(audienceId).getLastPost(audienceId)

    fun getAudienceLastResponse(audienceId: Long): String? =
        audienceManagerFor(audienceId).getLastResponse(audienceId)

    fun getAudienceWallet(audienceId: Long): Int =
        audienceManagerFor(audienceId).getWallet(audienceId)

    fun setAudienceWallet(audienceId: Long, budget: Int) =
        audienceManagerFor(audienceId).setInitialBudget(audienceId, budget)

    fun getAgentAudienceDebugSnapshot(audience: AiAudienceEntity): AgentAudienceDebugSnapshot? =
        if (audience.audienceType == AudienceEngineType.Agent) {
            agentAudienceManager.getRuntimeDebugSnapshot(audience)
        } else {
            null
        }

    fun getMemorySnapshot(): MemorySnapshot {
        val classic = classicAudienceManager.getMemorySnapshot()
        val agent = agentAudienceManager.getMemorySnapshot()
        return MemorySnapshot(
            memoryA = if (classic.memoryA.length >= agent.memoryA.length) classic.memoryA else agent.memoryA,
            memoryB = if (classic.memoryB.length >= agent.memoryB.length) classic.memoryB else agent.memoryB,
            recentVisual = if (classic.recentVisual.size >= agent.recentVisual.size) classic.recentVisual else agent.recentVisual,
            rawBufferSize = maxOf(classic.rawBufferSize, agent.rawBufferSize)
        )
    }

    fun getCouncilExpertLastPrompt(expertId: String): String? =
        councilManager.getLastPrompt(expertId)

    fun getCouncilExpertLastResponse(expertId: String): String? =
        councilManager.getLastResponse(expertId)

    fun getCouncilExpertSessionMemory(expertId: String): List<String> =
        councilManager.getSessionMemory(expertId)

    suspend fun getExpertKnowledge(expertId: String): List<com.example.watcher.data.model.CouncilKnowledgeEntity> {
        val dao = database.councilKnowledgeDao()
        // Expert's own calibration + any user_profile entries + session facts attributed to this expert
        val calibration = dao.getExpertCalibration(expertId, limit = 10)
        val userProfile = dao.getUserProfile(limit = 5)
        val bySource = dao.getBySource(expertId, limit = 5)
        return (calibration + userProfile + bySource)
            .distinctBy { it.id }
            .sortedByDescending { it.relevance }
            .take(20)
    }

    fun deleteKnowledgeEntry(id: Long) {
        viewModelScope.launch {
            database.councilKnowledgeDao().deleteById(id)
        }
    }

    override fun onCleared() {
        super.onCleared()
        gatewayDelegate.release()
        deviceDelegate.release()
        videoStopRequested.set(true)
        videoProcessingJob?.cancel()
        monitorManager.release()
        streamDeviceCoordinator.release()
        liveCommentaryRepository.release()
        classicAudienceManager.release()
        agentAudienceManager.release()
        councilManager.release()
        liveSpeechManager.release()
    }

    private fun audienceManagerFor(audienceId: Long): AudienceManagerHandle =
        when (audienceTypeCache.value[audienceId]) {
            AudienceEngineType.Agent -> AgentHandle
            else -> ClassicHandle
        }

    private fun mergeAudienceStates(
        classic: AiAudienceLiveState,
        agent: AiAudienceLiveState
    ): AiAudienceLiveState {
        val likeTotals = mutableMapOf<String, Int>()
        (classic.likeBoard + agent.likeBoard).forEach { entry ->
            likeTotals[entry.audienceName] = (likeTotals[entry.audienceName] ?: 0) + entry.count
        }
        val giftTotals = mutableMapOf<String, Int>()
        (classic.giftBoard + agent.giftBoard).forEach { entry ->
            giftTotals[entry.audienceName] = (giftTotals[entry.audienceName] ?: 0) + entry.totalSpent
        }
        return AiAudienceLiveState(
            messages = (classic.messages + agent.messages)
                .distinctBy { it.id }
                .sortedByDescending { it.timestamp }
                .take(80),
            activeAudienceCount = classic.activeAudienceCount + agent.activeAudienceCount,
            likeBoard = likeTotals.entries.sortedByDescending { it.value }
                .take(3)
                .map { LikeRankEntry(it.key, it.value) },
            lastLiker = agent.lastLiker ?: classic.lastLiker,
            giftBoard = giftTotals.entries.sortedByDescending { it.value }
                .take(3)
                .map { GiftRankEntry(it.key, it.value) },
            recentGifts = (classic.recentGifts + agent.recentGifts)
                .sortedByDescending { it.timestamp }
                .take(3)
        )
    }

    private interface AudienceManagerHandle {
        fun getLastPost(audienceId: Long): String?
        fun getLastResponse(audienceId: Long): String?
        fun getWallet(audienceId: Long): Int
        fun setInitialBudget(audienceId: Long, budget: Int)
    }

    private val ClassicHandle = object : AudienceManagerHandle {
        override fun getLastPost(audienceId: Long): String? = classicAudienceManager.getLastPost(audienceId)
        override fun getLastResponse(audienceId: Long): String? = classicAudienceManager.getLastResponse(audienceId)
        override fun getWallet(audienceId: Long): Int = classicAudienceManager.getWallet(audienceId)
        override fun setInitialBudget(audienceId: Long, budget: Int) =
            classicAudienceManager.setInitialBudget(audienceId, budget)
    }

    private val AgentHandle = object : AudienceManagerHandle {
        override fun getLastPost(audienceId: Long): String? = agentAudienceManager.getLastPost(audienceId)
        override fun getLastResponse(audienceId: Long): String? = agentAudienceManager.getLastResponse(audienceId)
        override fun getWallet(audienceId: Long): Int = agentAudienceManager.getWallet(audienceId)
        override fun setInitialBudget(audienceId: Long, budget: Int) =
            agentAudienceManager.setInitialBudget(audienceId, budget)
    }

    private fun initializeVideoSettings() = deviceDelegate.initializeVideoSettings()

    private fun observeVideoSettings() {
        viewModelScope.launch {
            videoStreamSettings.collect { settings ->
                settings?.let { deviceDelegate.applySettings(it) }
            }
        }
    }

    private fun observeMonitorStatusPersistence() {
        viewModelScope.launch {
            monitorManager.monitorStatus.collect { status ->
                val taskId = _currentIntentResult.value?.taskId
                if (taskId != null && status.lastCheckTime > lastPersistedCheckTime) {
                    lastPersistedCheckTime = status.lastCheckTime
                    repository.updateTaskOutcome(
                        taskId = taskId,
                        lastStatus = status.lastResult.name,
                        lastSummary = status.lastSummary.ifBlank { status.lastReason }
                    )
                }
            }
        }
    }

    // observeSelectedVideoRunEvents and observeSelectedHistoryDetail moved to HistoryDelegate

    private fun showMonitorIntentResult(result: IntentResult) {
        _currentIntentResult.value = result
        if (result.baselineSource == BaselineSource.UploadedImage) {
            _pendingBaselineImagePath.value = result.baselineImagePath
            _pendingBaselineBase64.value = result.baseFrameBase64
        } else {
            _pendingBaselineImagePath.value = null
            _pendingBaselineBase64.value = null
        }
        _uiState.value = UiState.Success(result)
    }

    private fun showVideoPlanningInProgress() {
        _videoPlanUiState.value = VideoPlanUiState.Loading
        _selectedVideoRunId.value = null
        _videoProcessingStatus.value = VideoProcessingStatus(
            stage = VideoRunStatus.Planning,
            message = "正在生成视频任务规划",
            isBusy = true
        )
    }

    private fun showVideoDraftReady(draft: VideoProcessTaskDraft, message: String) {
        _currentVideoTask.value = draft
        _videoPlanUiState.value = VideoPlanUiState.Success(draft)
        showAwaitingVideoConfirmation(task = draft, message = message)
    }

    private fun showVideoExecutionStarting(
        draft: VideoProcessTaskDraft,
        streamingEnabled: Boolean
    ) {
        _videoProcessingStatus.value = VideoProcessingStatus(
            stage = VideoRunStatus.Recording,
            activeTask = draft,
            activeRunId = null,
            templateLabel = draft.templateLabel,
            currentSegmentIndex = 0,
            segmentCount = draft.plannedSegmentCount,
            segmentDurationSeconds = draft.plannedSegmentDurationSeconds,
            captureIntervalSeconds = draft.captureIntervalSeconds,
            message = "Preparing to record.",
            streamingEnabled = streamingEnabled,
            streamingBuffer = "",
            isRecordingActive = true,
            isAnalysisActive = false,
            recordingSegmentIndex = 1,
            remainingDurationSeconds = draft.plannedDurationSeconds,
            isBusy = true
        )
    }

    private fun handleVideoProcessingCancellation(
        runId: Long?,
        draft: VideoProcessTaskDraft,
        streamingEnabled: Boolean
    ) {
        runId?.let {
            _selectedVideoRunId.value = it
            viewModelScope.launch {
                videoRepository.markRunCancelled(
                    runId = it,
                    segmentIndex = _videoProcessingStatus.value.currentSegmentIndex,
                    segmentCount = draft.plannedSegmentCount,
                    streamingEnabled = streamingEnabled,
                    onStatus = { update -> applyVideoStatusUpdate(draft, update) }
                )
            }
        }
        _videoProcessingStatus.value = _videoProcessingStatus.value.copy(
            stage = VideoRunStatus.Cancelled,
            message = "Current video processing task was cancelled.",
            isStreamingActive = false,
            isRecordingActive = false,
            isAnalysisActive = false,
            activeStreamingSegmentIndex = 0,
            isBusy = false
        )
    }

    private fun handleVideoProcessingFailure(
        runId: Long?,
        draft: VideoProcessTaskDraft,
        streamingEnabled: Boolean,
        error: Exception
    ) {
        runId?.let {
            _selectedVideoRunId.value = it
            viewModelScope.launch {
                videoRepository.markRunFailed(
                    runId = it,
                    segmentIndex = _videoProcessingStatus.value.currentSegmentIndex,
                    segmentCount = draft.plannedSegmentCount,
                    streamingEnabled = streamingEnabled,
                    error = error,
                    onStatus = { update -> applyVideoStatusUpdate(draft, update) }
                )
            }
        }
        _videoProcessingStatus.value = _videoProcessingStatus.value.copy(
            stage = VideoRunStatus.Failed,
            message = error.message ?: "执行失败",
            errorMessage = error.message ?: "执行失败",
            isStreamingActive = false,
            isRecordingActive = false,
            isAnalysisActive = false,
            activeStreamingSegmentIndex = 0,
            isBusy = false
        )
    }

    private fun clearVideoProcessingJobIfMatches(job: Job?) {
        if (videoProcessingJob == job) {
            videoProcessingJob = null
        }
    }

    private fun resetVideoProcessingJob() {
        videoStopRequested.set(true)
        videoProcessingJob?.cancel()
        videoStopRequested = AtomicBoolean(false)
    }

    private fun clearSelectedVideoTaskState() {
        _currentVideoTask.value = null
        _videoPlanUiState.value = VideoPlanUiState.Idle
        _selectedVideoRunId.value = null
        _videoProcessingStatus.value = VideoProcessingStatus()
    }

    private fun stopMonitoringIfRunning() {
        if (monitorStatus.value.isRunning) {
            monitorManager.stopMonitoring()
        }
    }

    private fun shouldRestartMonitoring(taskId: Long?): Boolean {
        return monitorStatus.value.isRunning && _currentIntentResult.value?.taskId == taskId
    }

    private suspend fun restartMonitoring(task: IntentResult) {
        lastPersistedCheckTime = 0L
        startMonitoringInternal(task)
    }

    private suspend fun startMonitoringInternal(task: IntentResult) {
        val normalized = task.normalized()
        _currentIntentResult.value = normalized
        lastPersistedCheckTime = 0L
        normalized.taskId?.let {
            repository.touchTask(it, null, null)
        }
        val runId = historyRepository.startMonitorRun(normalized)
        monitorManager.startMonitoring(normalized, runId)
    }

    private fun applyVideoStatusUpdate(task: VideoProcessTaskDraft, update: VideoExecutionStatusUpdate) {
        _selectedVideoRunId.value = update.runId
        val previousStatus = _videoProcessingStatus.value
        _videoProcessingStatus.value = _videoProcessingStatus.value.copy(
            stage = update.stage,
            activeTask = task,
            activeRunId = update.runId,
            templateLabel = update.templateLabel ?: task.templateLabel,
            currentSegmentIndex = update.segmentIndex,
            segmentCount = update.segmentCount,
            segmentDurationSeconds = update.segmentDurationSeconds,
            captureIntervalSeconds = update.captureIntervalSeconds,
            message = update.message,
            finalSummary = update.finalSummary.ifBlank { previousStatus.finalSummary },
            finalConclusion = update.finalConclusion.ifBlank { previousStatus.finalConclusion },
            timelineEvents = update.timelineEvents.takeIf(List<*>::isNotEmpty) ?: previousStatus.timelineEvents,
            streamingBuffer = when {
                update.streamingBuffer != null -> update.streamingBuffer
                update.streamingEnabled -> previousStatus.streamingBuffer
                else -> ""
            },
            streamingEnabled = update.streamingEnabled,
            isStreamingActive = update.isStreamingActive,
            isRecordingActive = update.isRecordingActive,
            isAnalysisActive = update.isAnalysisActive,
            recordingSegmentIndex = update.recordingSegmentIndex,
            activeStreamingSegmentIndex = update.activeStreamingSegmentIndex,
            recordedSegmentCount = update.recordedSegmentCount,
            analyzedSegmentCount = update.analyzedSegmentCount,
            pendingSegmentCount = update.pendingSegmentCount,
            recordedDurationSeconds = update.recordedDurationSeconds,
            remainingDurationSeconds = update.remainingDurationSeconds,
            nextCaptureInSeconds = update.nextCaptureInSeconds,
            stopRequested = update.stopRequested,
            segmentFeedbacks = update.segmentFeedbacks.takeIf(List<*>::isNotEmpty)
                ?: previousStatus.segmentFeedbacks,
            errorMessage = update.errorMessage,
            isBusy = update.stage !in TERMINAL_VIDEO_STAGES
        )
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return appContext.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
    }

    private fun decodeBase64Bitmap(base64: String): Bitmap? {
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun importBaselineImage(uri: Uri): String? {
        val contentResolver = appContext.contentResolver
        val extension = resolveImageExtension(contentResolver, uri)
        return contentResolver.openInputStream(uri)?.use { inputStream ->
            snapshotStore.importImage(
                inputStream = inputStream,
                directory = "MonitorBaselines",
                prefix = "BASELINE_IMPORT",
                extension = extension
            )
        }
    }

    private fun resolveImageExtension(contentResolver: ContentResolver, uri: Uri): String {
        val mimeType = contentResolver.getType(uri)
        val fromMimeType = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val displayName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
        val fromName = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
        return fromName ?: fromMimeType
    }

    private fun showAwaitingVideoConfirmation(task: VideoProcessTaskDraft, message: String) {
        _selectedVideoRunId.value = null
        _videoProcessingStatus.value = _videoProcessingStatus.value.copy(
            stage = VideoRunStatus.AwaitingConfirmation,
            activeTask = task,
            activeRunId = null,
            templateLabel = task.templateLabel,
            currentSegmentIndex = 0,
            segmentCount = task.plannedSegmentCount,
            segmentDurationSeconds = task.plannedSegmentDurationSeconds,
            captureIntervalSeconds = task.captureIntervalSeconds,
            finalSummary = "",
            finalConclusion = "",
            timelineEvents = emptyList(),
            streamingBuffer = "",
            streamingEnabled = task.autoStartStreamingOutput,
            isStreamingActive = false,
            isRecordingActive = false,
            isAnalysisActive = false,
            recordingSegmentIndex = 0,
            activeStreamingSegmentIndex = 0,
            recordedSegmentCount = 0,
            analyzedSegmentCount = 0,
            pendingSegmentCount = 0,
            recordedDurationSeconds = 0,
            remainingDurationSeconds = task.plannedDurationSeconds,
            nextCaptureInSeconds = 0,
            stopRequested = false,
            segmentFeedbacks = emptyList(),
            message = message,
            errorMessage = null,
            isBusy = false
        )
    }

    // Device provisioning/scan/migration methods moved to DeviceDelegate

    private companion object {
        val TERMINAL_VIDEO_STAGES = setOf(
            VideoRunStatus.Completed,
            VideoRunStatus.Failed,
            VideoRunStatus.Cancelled
        )
    }
}

