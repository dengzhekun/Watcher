package com.example.watcher.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import com.example.watcher.data.local.AppDatabase
import com.example.watcher.data.model.AiAudienceLiveState
import com.example.watcher.data.model.CouncilConfig
import com.example.watcher.data.model.CouncilEntryDraft
import com.example.watcher.data.model.CouncilEntryUiState
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.data.model.CouncilUiState
import com.example.watcher.data.model.DanmakuItem
import com.example.watcher.data.model.GiftRankEntry
import com.example.watcher.data.model.InteractionMode
import com.example.watcher.data.model.LikeRankEntry
import com.example.watcher.data.model.LiveCommentaryState
import com.example.watcher.data.model.LiveSpeechState
import com.example.watcher.data.repository.AiAudienceManager
import com.example.watcher.data.repository.ArkConfig
import com.example.watcher.data.repository.LiveCommentaryRepository
import com.example.watcher.data.repository.LiveSpeechRecognitionManager
import com.example.watcher.data.repository.LlmWalletRepository
import com.example.watcher.data.repository.TemplateRepository
import com.example.watcher.data.repository.agent.AgentAudienceManager
import com.example.watcher.data.repository.council.CouncilEntryConfigGenerator
import com.example.watcher.data.repository.council.CouncilManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

internal class LiveInteractionController(
    private val scope: CoroutineScope,
    private val appContext: Context,
    private val database: AppDatabase,
    private val llmWalletRepository: LlmWalletRepository,
    private val templateRepository: TemplateRepository,
    private val liveCommentaryRepository: LiveCommentaryRepository,
    private val classicAudienceManager: AiAudienceManager,
    private val agentAudienceManager: AgentAudienceManager,
    private val councilManager: CouncilManager,
    private val liveSpeechManager: LiveSpeechRecognitionManager,
    private val councilEntryGenerator: CouncilEntryConfigGenerator,
    private val currentFrameProvider: () -> Bitmap?
) {
    private val _interactionMode = MutableStateFlow(InteractionMode.Off)
    private val _councilEntryUiState = MutableStateFlow(CouncilEntryUiState())

    val interactionMode: StateFlow<InteractionMode> = _interactionMode.asStateFlow()
    val councilEntryUiState: StateFlow<CouncilEntryUiState> = _councilEntryUiState.asStateFlow()
    val liveCommentaryState: StateFlow<LiveCommentaryState>
        get() = liveCommentaryRepository.commentaryState
    val liveSpeechState: StateFlow<LiveSpeechState>
        get() = liveSpeechManager.state
    val councilState: StateFlow<CouncilUiState>
        get() = councilManager.state

    val aiAudienceLiveState: StateFlow<AiAudienceLiveState> = combine(
        classicAudienceManager.liveState,
        agentAudienceManager.liveState
    ) { classic, agent ->
        mergeAudienceStates(classic, agent)
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiAudienceLiveState()
    )

    val danmakuFlow: SharedFlow<DanmakuItem> = merge(
        classicAudienceManager.danmakuFlow,
        agentAudienceManager.danmakuFlow
    ).shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        replay = 0
    )

    fun onSpeechResult(text: String) {
        when (_interactionMode.value) {
            InteractionMode.Live -> {
                classicAudienceManager.onSpeechEvent(text)
                agentAudienceManager.onSpeechEvent(text)
            }
            InteractionMode.Council -> councilManager.onSpeechEvent(text)
            InteractionMode.Off -> Unit
        }
    }

    fun startLiveCommentary() {
        android.util.Log.d(
            "LiveCommentary",
            "startLiveCommentary, currentFrame=${currentFrameProvider() != null}"
        )
        liveCommentaryRepository.startCommentary(
            outputRoot = appContext.filesDir,
            latestFrameProvider = currentFrameProvider,
            speechProvider = { liveSpeechManager.getFinalTranscripts() }
        )
    }

    fun stopLiveCommentary() {
        android.util.Log.d("LiveCommentary", "stopLiveCommentary")
        liveCommentaryRepository.stopCommentary()
        classicAudienceManager.stop()
        agentAudienceManager.stop()
        councilManager.stop()
        liveSpeechManager.stop()
        _interactionMode.value = InteractionMode.Off
    }

    fun startAiAudience() {
        if (_interactionMode.value == InteractionMode.Council) {
            councilManager.stop()
        }
        classicAudienceManager.start(
            frameProvider = currentFrameProvider,
            speechProvider = { liveSpeechManager.getFinalTranscripts() }
        )
        agentAudienceManager.start(
            frameProvider = currentFrameProvider,
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
            frameProvider = currentFrameProvider,
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
        scope.launch {
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
        scope.launch {
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
        scope.launch {
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
        scope.launch {
            liveCommentaryRepository.fullReset()
            database.aiAudienceMessageDao().deleteAll()
            liveSpeechManager.stop()
            liveSpeechManager.start()
            classicAudienceManager.fullReset()
            agentAudienceManager.fullReset()
            councilManager.stop()
            classicAudienceManager.start(
                frameProvider = currentFrameProvider,
                speechProvider = { liveSpeechManager.getFinalTranscripts() }
            )
            agentAudienceManager.start(
                frameProvider = currentFrameProvider,
                speechProvider = { liveSpeechManager.getFinalTranscripts() }
            )
            _interactionMode.value = InteractionMode.Live
            android.util.Log.d("LiveRoom", "All context reset")
        }
    }

    fun setLiveSpeechMicEnabled(enabled: Boolean) {
        liveSpeechManager.setMicEnabled(enabled)
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
}
