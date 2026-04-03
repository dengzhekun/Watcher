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
import com.example.watcher.data.repository.BitmapEncoding
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
import java.util.concurrent.atomic.AtomicBoolean

class IntentViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>()
    private val database = AppDatabase.getDatabase(application)
    private val repository = IntentRepository(
        apiService = RetrofitClient.doubaoApiService,
        taskDao = database.monitorTaskDao()
    )
    val liveCommentaryRepository = LiveCommentaryRepository(
        apiService = RetrofitClient.doubaoApiService,
        streamingClient = ArkStreamingClient()
    )
    private val classicAudienceManager = AiAudienceManager(
        providerDao = database.llmProviderDao(),
        audienceDao = database.aiAudienceDao(),
        messageDao = database.aiAudienceMessageDao(),
        memoryManager = liveCommentaryRepository.memoryManager,
        sceneMemoryManager = liveCommentaryRepository.sceneMemoryManager
    )
    private val agentAudienceManager = AgentAudienceManager(
        providerDao = database.llmProviderDao(),
        audienceDao = database.aiAudienceDao(),
        messageDao = database.aiAudienceMessageDao(),
        memoryManager = liveCommentaryRepository.memoryManager,
        sceneMemoryManager = liveCommentaryRepository.sceneMemoryManager
    )
    private val audienceTypeCache = MutableStateFlow<Map<Long, AudienceEngineType>>(emptyMap())
    val liveSpeechManager = LiveSpeechRecognitionManager(
        context = appContext,
        memoryManager = liveCommentaryRepository.memoryManager
    ).also {
        it.onSpeechResult = { text ->
            classicAudienceManager.onSpeechEvent(text)
            agentAudienceManager.onSpeechEvent(text)
        }
    }
    private val videoRepository = VideoProcessRepository(
        apiService = RetrofitClient.doubaoApiService,
        taskDao = database.videoProcessTaskDao(),
        runDao = database.videoProcessRunDao(),
        segmentRunDao = database.videoSegmentRunDao(),
        timelineEventDao = database.timelineEventDao()
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
        MIGRATION_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val alertNotifier = AndroidAlertNotifier(appContext)
    private val snapshotStore = SnapshotStore(appContext)
    private val lanStreamScanner = LanStreamScanner(appContext)
    private val templateRepository = TemplateRepository(database.templateDao())

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
    private val _isStreamPlaying = MutableStateFlow(true)
    private val _streamReconnectToken = MutableStateFlow(0)
    private val _streamScanUiState = MutableStateFlow(StreamScanUiState())
    private val _deviceProvisionUiState = MutableStateFlow(DeviceProvisionUiState())
    private val _settingsNotice = MutableStateFlow<String?>(null)
    private val _videoPlanUiState = MutableStateFlow<VideoPlanUiState>(VideoPlanUiState.Idle)
    private val _currentVideoTask = MutableStateFlow<VideoProcessTaskDraft?>(null)
    private val _videoProcessingStatus = MutableStateFlow(VideoProcessingStatus())
    private val _selectedVideoRunId = MutableStateFlow<Long?>(null)
    private val _selectedVideoRunEvents = MutableStateFlow<List<TimelineEventEntity>>(emptyList())
    private val _selectedHistoryRecord = MutableStateFlow<HistoryRecordSelection?>(null)
    private val _selectedHistoryDetail = MutableStateFlow<HistoryRecordDetail?>(null)

    private var lastPersistedCheckTime = 0L
    private var streamScanJob: Job? = null
    private var deviceProvisionJob: Job? = null
    private var videoProcessingJob: Job? = null
    private var videoStopRequested = AtomicBoolean(false)

    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val currentIntentResult: StateFlow<IntentResult?> = _currentIntentResult.asStateFlow()
    val pendingBaselineImagePath: StateFlow<String?> = _pendingBaselineImagePath.asStateFlow()
    val pendingBaselineBase64: StateFlow<String?> = _pendingBaselineBase64.asStateFlow()
    val isStreamPlaying: StateFlow<Boolean> = _isStreamPlaying.asStateFlow()
    val streamReconnectToken: StateFlow<Int> = _streamReconnectToken.asStateFlow()
    val streamScanUiState: StateFlow<StreamScanUiState> = _streamScanUiState.asStateFlow()
    val deviceProvisionUiState: StateFlow<DeviceProvisionUiState> = _deviceProvisionUiState.asStateFlow()
    val settingsNotice: StateFlow<String?> = _settingsNotice.asStateFlow()
    val videoPlanUiState: StateFlow<VideoPlanUiState> = _videoPlanUiState.asStateFlow()
    val currentVideoTask: StateFlow<VideoProcessTaskDraft?> = _currentVideoTask.asStateFlow()
    val videoProcessingStatus: StateFlow<VideoProcessingStatus> = _videoProcessingStatus.asStateFlow()
    val selectedVideoRunId: StateFlow<Long?> = _selectedVideoRunId.asStateFlow()
    val selectedVideoRunEvents: StateFlow<List<TimelineEventEntity>> = _selectedVideoRunEvents.asStateFlow()
    val selectedHistoryRecord: StateFlow<HistoryRecordSelection?> = _selectedHistoryRecord.asStateFlow()
    val selectedHistoryDetail: StateFlow<HistoryRecordDetail?> = _selectedHistoryDetail.asStateFlow()

    val tasksFlow = repository.observeTasks()
    val videoTasksFlow = videoRepository.observeTasks()
    val recentVideoRunsFlow = videoRepository.observeRecentRuns()
    val historyRecordsFlow = historyRepository.observeHistoryRecords()
    val storageSummaryFlow = historyRepository.observeStorageSummary()
    val videoStreamSettings = database.videoStreamSettingsDao().getSettings()
    val monitorTemplatesFlow = templateRepository.monitorTemplates
    val videoTemplatesFlow = templateRepository.videoTemplates

    val monitorManager = MonitorManager(
        apiService = RetrofitClient.doubaoApiService,
        alertNotifier = alertNotifier,
        historyRepository = historyRepository,
        snapshotStore = snapshotStore
    )
    private val streamDeviceCoordinator = StreamDeviceCoordinator(monitorManager)
    val monitorStatus: StateFlow<MonitorStatus> get() = monitorManager.monitorStatus
    val monitorLogs: StateFlow<List<MonitorLogEntry>> get() = monitorManager.monitorLogs

    init {
        initializeVideoSettings()
        observeVideoSettings()
        observeMonitorStatusPersistence()
        observeSelectedVideoRunEvents()
        observeSelectedHistoryDetail()
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

    fun updateMonitorTemplate(entity: MonitorTemplateEntity) {
        viewModelScope.launch { templateRepository.updateMonitorTemplate(entity) }
    }

    fun updateVideoTemplate(entity: VideoTemplateEntity) {
        viewModelScope.launch { templateRepository.updateVideoTemplate(entity) }
    }

    fun resetMonitorTemplate(templateId: String) {
        viewModelScope.launch { templateRepository.resetMonitorTemplate(templateId) }
    }

    fun resetVideoTemplate(templateId: String) {
        viewModelScope.launch { templateRepository.resetVideoTemplate(templateId) }
    }

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

    fun selectHistoryRecord(selection: HistoryRecordSelection?) {
        _selectedHistoryRecord.value = selection
    }

    fun deleteHistoryRecord(selection: HistoryRecordSelection) {
        viewModelScope.launch {
            val detail = _selectedHistoryDetail.value
            if (detail?.selection == selection && !detail.canDelete) {
                return@launch
            }

            historyRepository.deleteHistoryRecord(selection)
            if (_selectedHistoryRecord.value == selection) {
                _selectedHistoryRecord.value = null
                _selectedHistoryDetail.value = null
            }
            if (selection.type == HistoryRecordType.VideoAnalysis &&
                _selectedVideoRunId.value == selection.recordId
            ) {
                _selectedVideoRunId.value = null
                _selectedVideoRunEvents.value = emptyList()
            }
        }
    }

    fun saveVideoStreamSettings(settings: VideoStreamSettings) {
        viewModelScope.launch {
            val normalized = settings.normalized()
            if (normalized.deviceProfile == VideoStreamSettings.DEVICE_PROFILE_MJPEG_ONLY) {
                _settingsNotice.value = "Generic MJPEG mode skips light and camera control commands."
            }
            database.videoStreamSettingsDao().insert(normalized)
            reconnectStream()
        }
    }

    fun refreshDeviceProvisionInfo() {
        launchDeviceProvisionAction(
            loadingState = { current ->
                current.copy(
                    isLoadingInfo = true,
                    isWaitingForReconnect = false,
                    isFindingProvisionedDevice = false,
                    errorMessage = null,
                    statusMessage = "Loading device status..."
                )
            }
        ) { coordinator ->
            val info = coordinator.fetchDeviceInfo()
            val currentSettings = database.videoStreamSettingsDao().getSettingsSync() ?: VideoStreamSettings()
            val updatedSettings = currentSettings.copy(
                ipAddress = info.ip.ifBlank { currentSettings.ipAddress },
                preferredWifiSsid = info.staSsid.ifBlank { currentSettings.preferredWifiSsid }
            ).normalized()
            database.videoStreamSettingsDao().insert(updatedSettings)
            _deviceProvisionUiState.value = _deviceProvisionUiState.value.copy(
                isLoadingInfo = false,
                deviceInfo = info,
                statusMessage = if (info.isApMode) {
                    "Device is in hotspot provisioning mode. Submit target Wi-Fi first."
                } else {
                    "Device is on ${info.staSsid.ifBlank { "external Wi-Fi" }} at ${info.ip}."
                },
                errorMessage = null
            )
        }
    }

    fun scanProvisioningWifi() {
        launchDeviceProvisionAction(
            loadingState = { current ->
                current.copy(
                    isScanningWifi = true,
                    isWaitingForReconnect = false,
                    isFindingProvisionedDevice = false,
                    errorMessage = null,
                    statusMessage = "Scanning nearby Wi-Fi networks..."
                )
            }
        ) { coordinator ->
            val networks = coordinator.scanWifiNetworks()
            _deviceProvisionUiState.value = _deviceProvisionUiState.value.copy(
                isScanningWifi = false,
                wifiNetworks = networks,
                statusMessage = if (networks.isEmpty()) {
                    "No Wi-Fi networks were found by the device."
                } else {
                    "Found ${networks.size} Wi-Fi networks. Tap one to fill it in."
                },
                errorMessage = null
            )
        }
    }

    fun submitProvisioningWifi(ssid: String, password: String) {
        launchDeviceProvisionAction(
            loadingState = { current ->
                current.copy(
                    isSubmittingWifi = true,
                    isWaitingForReconnect = false,
                    isFindingProvisionedDevice = false,
                    errorMessage = null,
                    statusMessage = "Saving Wi-Fi settings to the device..."
                )
            }
        ) { coordinator ->
            val expectedDeviceId = _deviceProvisionUiState.value.deviceInfo?.deviceId
            val notice = coordinator.saveWifiConfig(ssid = ssid, password = password)
            _deviceProvisionUiState.value = _deviceProvisionUiState.value.copy(
                isSubmittingWifi = false,
                isWaitingForReconnect = true,
                statusMessage = notice,
                errorMessage = null
            )
            _settingsNotice.value = notice
            awaitProvisionedDeviceReconnect(
                expectedDeviceId = expectedDeviceId,
                targetWifiSsid = ssid.trim()
            )
        }
    }

    fun clearProvisionedWifi() {
        launchDeviceProvisionAction(
            loadingState = { current ->
                current.copy(
                    isClearingWifi = true,
                    isWaitingForReconnect = false,
                    isFindingProvisionedDevice = false,
                    errorMessage = null,
                    statusMessage = "Clearing saved Wi-Fi from the device..."
                )
            }
        ) { coordinator ->
            val notice = coordinator.clearWifiConfig()
            val currentSettings = database.videoStreamSettingsDao().getSettingsSync() ?: VideoStreamSettings()
            database.videoStreamSettingsDao().insert(
                currentSettings.copy(
                    ipAddress = VideoStreamSettings.DEFAULT_DEVICE_IP,
                    preferredWifiSsid = ""
                ).normalized()
            )
            _deviceProvisionUiState.value = DeviceProvisionUiState(
                statusMessage = notice
            )
            _settingsNotice.value = notice
        }
    }

    fun scanVideoStreamDevices() {
        streamScanJob?.cancel()
        streamScanJob = viewModelScope.launch {
            val currentSettings = database.videoStreamSettingsDao().getSettingsSync() ?: VideoStreamSettings()
            val discoveredDevices = linkedMapOf<String, DiscoveredStreamDevice>()
            _streamScanUiState.value = StreamScanUiState(
                isScanning = true,
                statusMessage = "Scanning the current LAN for video devices..."
            )

            try {
                val summary = lanStreamScanner.scan(preferredPort = currentSettings.port) { device ->
                    discoveredDevices[device.host] = device
                    _streamScanUiState.value = StreamScanUiState(
                        isScanning = true,
                        devices = sortDiscoveredDevices(discoveredDevices.values.toList()),
                        statusMessage = "Scanning... ${discoveredDevices.size} candidates found."
                    )
                }

                val devices = sortDiscoveredDevices(discoveredDevices.values.toList())
                _streamScanUiState.value = StreamScanUiState(
                    isScanning = false,
                    devices = devices,
                    statusMessage = if (devices.isEmpty()) {
                        "No video stream was found on ${summary.subnetLabel}. You can still enter an address manually."
                    } else {
                        "Found ${devices.size} candidates on ${summary.subnetLabel}. Pick one and save settings."
                    }
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _streamScanUiState.value = StreamScanUiState(
                    isScanning = false,
                    errorMessage = error.message ?: "Failed to scan for devices. Try again later."
                )
            }
        }
    }

    fun clearStreamDeviceScan() {
        streamScanJob?.cancel()
        streamScanJob = null
        _streamScanUiState.value = StreamScanUiState()
    }

    fun clearDeviceProvisionState() {
        deviceProvisionJob?.cancel()
        deviceProvisionJob = null
        _deviceProvisionUiState.value = DeviceProvisionUiState()
    }

    fun consumeSettingsNotice() {
        _settingsNotice.value = null
    }

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
            latestFrameProvider = { monitorManager.currentFrame.value }
        )
    }

    fun stopLiveCommentary() {
        android.util.Log.d("LiveCommentary", "ViewModel.stopLiveCommentary")
        liveCommentaryRepository.stopCommentary()
        classicAudienceManager.stop()
        agentAudienceManager.stop()
        liveSpeechManager.stop()
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
        classicAudienceManager.start(
            frameProvider = { monitorManager.currentFrame.value },
            speechProvider = { liveSpeechManager.getFinalTranscripts() }
        )
        agentAudienceManager.start(
            frameProvider = { monitorManager.currentFrame.value },
            speechProvider = { liveSpeechManager.getFinalTranscripts() }
        )
    }

    fun stopAiAudience() {
        classicAudienceManager.compressMemories()
        agentAudienceManager.compressMemories()
        classicAudienceManager.stop()
        agentAudienceManager.stop()
    }

    // --- Live Speech ---

    val liveSpeechState: StateFlow<LiveSpeechState>
        get() = liveSpeechManager.state

    fun startLiveSpeech() {
        liveSpeechManager.start()
    }

    fun stopLiveSpeech() {
        liveSpeechManager.stop()
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
            classicAudienceManager.start(
                frameProvider = { monitorManager.currentFrame.value },
                speechProvider = { liveSpeechManager.getFinalTranscripts() }
            )
            agentAudienceManager.start(
                frameProvider = { monitorManager.currentFrame.value },
                speechProvider = { liveSpeechManager.getFinalTranscripts() }
            )
            android.util.Log.d("LiveRoom", "All context reset")
        }
    }

    fun setLiveSpeechMicEnabled(enabled: Boolean) {
        liveSpeechManager.setMicEnabled(enabled)
    }

    val llmProvidersFlow get() = database.llmProviderDao().observeAll()
    val aiAudiencesFlow get() = database.aiAudienceDao().observeAll()

    fun saveProvider(provider: LlmProviderEntity) {
        viewModelScope.launch { database.llmProviderDao().upsert(provider) }
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch { database.llmProviderDao().deleteById(id) }
    }

    fun saveAudience(audience: AiAudienceEntity) {
        viewModelScope.launch { database.aiAudienceDao().upsert(audience) }
    }

    fun deleteAudience(id: Long) {
        viewModelScope.launch { database.aiAudienceDao().deleteById(id) }
    }

    // --- Template Share/Import ---

    fun deleteMonitorTemplate(templateId: String) {
        viewModelScope.launch { database.templateDao().deleteMonitorTemplate(templateId) }
    }

    fun deleteVideoTemplate(templateId: String) {
        viewModelScope.launch { database.templateDao().deleteVideoTemplate(templateId) }
    }

    fun exportMonitorTemplate(template: MonitorTemplateEntity): String =
        TemplateShareManager.exportMonitorTemplate(template)

    fun exportVideoTemplate(template: VideoTemplateEntity): String =
        TemplateShareManager.exportVideoTemplate(template)

    fun importTemplate(text: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            TemplateShareManager.importTemplate(text)
                .onSuccess { result ->
                    when {
                        result.monitorTemplate != null -> {
                            database.templateDao().upsertMonitor(result.monitorTemplate)
                            onResult("监控模板「${result.label}」导入成功")
                        }
                        result.videoTemplate != null -> {
                            database.templateDao().upsertVideo(result.videoTemplate)
                            onResult("视频分析模板「${result.label}」导入成功")
                        }
                    }
                }
                .onFailure { e ->
                    onResult("导入失败: ${e.message}")
                }
        }
    }

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

    override fun onCleared() {
        super.onCleared()
        deviceProvisionJob?.cancel()
        streamScanJob?.cancel()
        videoStopRequested.set(true)
        videoProcessingJob?.cancel()
        monitorManager.release()
        streamDeviceCoordinator.release()
        liveCommentaryRepository.release()
        classicAudienceManager.release()
        agentAudienceManager.release()
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

    private fun initializeVideoSettings() {
        viewModelScope.launch {
            val settingsDao = database.videoStreamSettingsDao()
            val currentSettings = settingsDao.getSettingsSync()
            if (currentSettings == null) {
                settingsDao.insert(VideoStreamSettings())
            } else {
                migrateChangeDetectionDefaultsIfNeeded(currentSettings)
                migrateResolutionDefaultsIfNeeded(currentSettings)
            }
        }
    }

    private fun observeVideoSettings() {
        viewModelScope.launch {
            videoStreamSettings.collect { settings ->
                settings?.let { applySettings(it) }
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

    private fun observeSelectedVideoRunEvents() {
        viewModelScope.launch {
            _selectedVideoRunId
                .flatMapLatest { runId ->
                    if (runId == null) {
                        flowOf(emptyList())
                    } else {
                        videoRepository.observeTimelineForRun(runId)
                    }
                }
                .collect { events ->
                    _selectedVideoRunEvents.value = events
                }
        }
    }

    private fun observeSelectedHistoryDetail() {
        viewModelScope.launch {
            _selectedHistoryRecord
                .flatMapLatest { selection ->
                    if (selection == null) {
                        flowOf(null)
                    } else {
                        historyRepository.observeHistoryDetail(selection)
                    }
                }
                .collect { detail ->
                    _selectedHistoryDetail.value = detail
                }
        }
    }

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

    private suspend fun applySettings(settings: VideoStreamSettings) {
        runCatching { streamDeviceCoordinator.applySettings(settings) }
            .onSuccess { outcome ->
                outcome.persistedSettings?.let { database.videoStreamSettingsDao().insert(it) }
                if (!outcome.notice.isNullOrBlank()) {
                    _settingsNotice.value = outcome.notice
                }
            }
            .onFailure { error ->
                if (!isDeviceProvisionFlowBusy()) {
                    _settingsNotice.value = error.message ?: "Device connection failed."
                }
            }
    }

    private fun isDeviceProvisionFlowBusy(): Boolean {
        val state = _deviceProvisionUiState.value
        return state.isLoadingInfo ||
            state.isScanningWifi ||
            state.isSubmittingWifi ||
            state.isClearingWifi ||
            state.isWaitingForReconnect ||
            state.isFindingProvisionedDevice
    }

    private fun launchDeviceProvisionAction(
        loadingState: (DeviceProvisionUiState) -> DeviceProvisionUiState,
        action: suspend (DeviceProvisionCoordinator) -> Unit
    ) {
        deviceProvisionJob?.cancel()
        deviceProvisionJob = viewModelScope.launch {
            _deviceProvisionUiState.value = loadingState(_deviceProvisionUiState.value)
            try {
                action(createDeviceProvisionCoordinator())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _deviceProvisionUiState.value = _deviceProvisionUiState.value.copy(
                    isLoadingInfo = false,
                    isScanningWifi = false,
                    isSubmittingWifi = false,
                    isClearingWifi = false,
                    isWaitingForReconnect = false,
                    isFindingProvisionedDevice = false,
                    errorMessage = error.message ?: "Device provisioning failed.",
                    statusMessage = null
                )
            }
        }
    }

    private suspend fun awaitProvisionedDeviceReconnect(
        expectedDeviceId: String?,
        targetWifiSsid: String
    ) {
        delay(PROVISIONING_RESTART_GRACE_PERIOD_MS)
        _deviceProvisionUiState.value = _deviceProvisionUiState.value.copy(
            isWaitingForReconnect = false,
            isFindingProvisionedDevice = true,
            statusMessage = "The device is restarting. Switch the phone back to the same LAN and the app will find its new IP automatically.",
            errorMessage = null
        )

        val discoveredDevice = withTimeoutOrNull(PROVISIONING_REDISCOVERY_TIMEOUT_MS) {
            while (true) {
                val currentSettings = database.videoStreamSettingsDao().getSettingsSync() ?: VideoStreamSettings()
                val knownMdnsUrl = _deviceProvisionUiState.value.deviceInfo?.mdnsUrl
                val found = lanStreamScanner.rediscoverProvisionedDevice(
                    settings = currentSettings,
                    expectedDeviceId = expectedDeviceId,
                    knownMdnsUrl = knownMdnsUrl
                )
                if (found != null) {
                    return@withTimeoutOrNull found
                }

                _deviceProvisionUiState.value = _deviceProvisionUiState.value.copy(
                    statusMessage = "Waiting for the phone to return to the target LAN, then searching for the device's new IP..."
                )
                delay(PROVISIONING_REDISCOVERY_RETRY_MS)
            }
        } as DiscoveredStreamDevice?

        if (discoveredDevice == null) {
            _deviceProvisionUiState.value = _deviceProvisionUiState.value.copy(
                isFindingProvisionedDevice = false,
                errorMessage = "Wi-Fi was sent to the device, but its new IP was not found yet. Reconnect the phone to the target LAN and try reading device status again.",
                statusMessage = null
            )
            return
        }

        val updatedSettings = persistProvisionedDevice(discoveredDevice, targetWifiSsid)
        reconnectStream()

        val refreshedInfo = runCatching {
            DeviceProvisionCoordinator(updatedSettings.baseUrl).fetchDeviceInfo()
        }.getOrElse {
            discoveredDevice.toRuntimeInfo(targetWifiSsid)
        }

        _deviceProvisionUiState.value = _deviceProvisionUiState.value.copy(
            isFindingProvisionedDevice = false,
            deviceInfo = refreshedInfo,
            statusMessage = "Device connected to \"$targetWifiSsid\". New IP: ${refreshedInfo.ip}. Reconnecting automatically now.",
            errorMessage = null
        )
        _settingsNotice.value = "Device discovered at ${refreshedInfo.ip}. Stream reconnecting automatically."
    }

    private suspend fun persistProvisionedDevice(
        device: DiscoveredStreamDevice,
        targetWifiSsid: String
    ): VideoStreamSettings {
        val currentSettings = database.videoStreamSettingsDao().getSettingsSync() ?: VideoStreamSettings()
        val updatedSettings = currentSettings.copy(
            ipAddress = device.host,
            port = device.preferredPort,
            deviceProfile = VideoStreamSettings.DEVICE_PROFILE_ESP32,
            preferredWifiSsid = targetWifiSsid
        ).normalized()
        database.videoStreamSettingsDao().insert(updatedSettings)
        return updatedSettings
    }

    private suspend fun createDeviceProvisionCoordinator(): DeviceProvisionCoordinator {
        val settings = database.videoStreamSettingsDao().getSettingsSync() ?: VideoStreamSettings()
        return DeviceProvisionCoordinator(settings.normalized().baseUrl)
    }

    private suspend fun migrateChangeDetectionDefaultsIfNeeded(currentSettings: VideoStreamSettings) {
        if (migrationPreferences.getBoolean(KEY_CHANGE_DETECTION_DEFAULTS_MIGRATED, false)) {
            return
        }

        val migratedSettings = currentSettings.copy(
            changeDetectionEnabled = VideoStreamSettings.DEFAULT_CHANGE_DETECTION_ENABLED,
            changeThresholdPercent = VideoStreamSettings.DEFAULT_CHANGE_THRESHOLD_PERCENT
        )

        if (migratedSettings != currentSettings) {
            database.videoStreamSettingsDao().insert(migratedSettings)
        }

        migrationPreferences.edit()
            .putBoolean(KEY_CHANGE_DETECTION_DEFAULTS_MIGRATED, true)
            .apply()
    }

    private suspend fun migrateResolutionDefaultsIfNeeded(currentSettings: VideoStreamSettings) {
        if (migrationPreferences.getBoolean(KEY_RESOLUTION_DEFAULTS_MIGRATED, false)) {
            return
        }

        val normalized = currentSettings.normalized()
        if (normalized.resolution == VideoStreamSettings.FALLBACK_RESOLUTION) {
            database.videoStreamSettingsDao().insert(
                normalized.copy(resolution = VideoStreamSettings.DEFAULT_RESOLUTION)
            )
        }

        migrationPreferences.edit()
            .putBoolean(KEY_RESOLUTION_DEFAULTS_MIGRATED, true)
            .apply()
    }

    private fun sortDiscoveredDevices(
        devices: List<DiscoveredStreamDevice>
    ): List<DiscoveredStreamDevice> {
        return devices.sortedWith(
            compareBy<DiscoveredStreamDevice>({ it.kind.name }, { it.host })
        )
    }

    private companion object {
        val TERMINAL_VIDEO_STAGES = setOf(
            VideoRunStatus.Completed,
            VideoRunStatus.Failed,
            VideoRunStatus.Cancelled
        )
    }
}

private fun DiscoveredStreamDevice.toRuntimeInfo(targetWifiSsid: String): DeviceRuntimeInfo {
    return DeviceRuntimeInfo(
        deviceId = deviceId,
        mode = "sta",
        staSsid = targetWifiSsid,
        ip = host,
        httpPort = preferredPort,
        streamPort = streamPort,
        mdnsUrl = mdnsUrl,
        mdnsActive = mdnsUrl.isNotBlank(),
        streamUrl = streamUrl
    )
}

private const val MIGRATION_PREFERENCES_NAME = "watcher_migrations"
private const val KEY_CHANGE_DETECTION_DEFAULTS_MIGRATED = "change_detection_defaults_v1"
private const val KEY_RESOLUTION_DEFAULTS_MIGRATED = "resolution_defaults_v1"
private const val PROVISIONING_RESTART_GRACE_PERIOD_MS = 2_500L
private const val PROVISIONING_REDISCOVERY_TIMEOUT_MS = 90_000L
private const val PROVISIONING_REDISCOVERY_RETRY_MS = 3_000L
