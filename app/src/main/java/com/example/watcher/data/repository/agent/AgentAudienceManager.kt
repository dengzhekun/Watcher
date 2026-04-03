package com.example.watcher.data.repository.agent

import android.graphics.Bitmap
import android.util.Log
import com.example.watcher.data.local.AiAudienceDao
import com.example.watcher.data.local.AiAudienceMessageDao
import com.example.watcher.data.local.LlmProviderDao
import com.example.watcher.data.model.AgentAudienceDebugSnapshot
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AiAudienceLiveState
import com.example.watcher.data.model.AiAudienceMessageEntity
import com.example.watcher.data.model.AudienceAction
import com.example.watcher.data.model.AudienceEngineType
import com.example.watcher.data.model.DanmakuItem
import com.example.watcher.data.model.GiftEvent
import com.example.watcher.data.model.GiftRankEntry
import com.example.watcher.data.model.GiftType
import com.example.watcher.data.model.LikeRankEntry
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.model.MemorySnapshot
import com.example.watcher.data.remote.ChatMessage
import com.example.watcher.data.remote.OpenAiCompatibleProvider
import com.example.watcher.data.repository.BitmapEncoding
import com.example.watcher.data.repository.CommentaryMemoryManager
import com.example.watcher.data.repository.SceneMemoryManager
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentAudienceManager(
    private val providerDao: LlmProviderDao,
    private val audienceDao: AiAudienceDao,
    private val messageDao: AiAudienceMessageDao,
    private val memoryManager: CommentaryMemoryManager,
    private val sceneMemoryManager: SceneMemoryManager
) {
    private val audienceType = AudienceEngineType.Agent

    companion object {
        private const val TAG = "AgentAudience"
        private const val MAX_LIVE_MESSAGES = 80
        private const val MAX_AI_CHAT_HISTORY = 10
        private const val INITIAL_BUDGET = 100
        private const val LIKE_COOLDOWN_MS = 60_000L
    }

    private val _liveState = MutableStateFlow(AiAudienceLiveState())
    val liveState: StateFlow<AiAudienceLiveState> = _liveState.asStateFlow()

    private val _danmakuFlow = MutableSharedFlow<DanmakuItem>(extraBufferCapacity = 16)
    val danmakuFlow: SharedFlow<DanmakuItem> = _danmakuFlow.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val heartbeatJobs = mutableMapOf<Long, Job>()
    private val resetChannels = mutableMapOf<Long, Channel<String>>()
    private val busyAudiences = mutableSetOf<Long>()
    private val providerMutexes = mutableMapOf<String, Mutex>()
    private val runtimeStates = mutableMapOf<Long, AgentRuntimeState>()
    private val debugSnapshots = mutableMapOf<Long, AgentAudienceDebugSnapshot>()
    private val stateMutex = Mutex()
    private val gson = Gson()

    private val wallets = mutableMapOf<Long, Int>()
    private val lastLikeTime = mutableMapOf<Long, Long>()
    private val likeCounts = mutableMapOf<String, Int>()
    private val giftSpending = mutableMapOf<String, Int>()
    private val recentGifts = mutableListOf<GiftEvent>()
    private val lastPostContent = mutableMapOf<Long, Pair<Long, String>>()
    private val lastResponse = mutableMapOf<Long, Pair<Long, String>>()
    private val lastMentionCheckTime = mutableMapOf<Long, Long>()
    private val promptBuilder = AgentAudiencePromptBuilder(memoryManager, sceneMemoryManager)
    private val responseParser = AgentAudienceResponseParser()

    private var lastLikerName: String? = null
    private var observeJob: Job? = null
    private var speechListenJob: Job? = null
    private var latestFrameProvider: (() -> Bitmap?)? = null
    private var speechTranscriptProvider: (() -> List<Pair<Long, String>>)? = null

    private val speechEvent = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun onSpeechEvent(text: String) {
        speechEvent.tryEmit(text)
    }

    fun start(
        frameProvider: () -> Bitmap?,
        speechProvider: (() -> List<Pair<Long, String>>)? = null
    ) {
        latestFrameProvider = frameProvider
        speechTranscriptProvider = speechProvider
        observeJob?.cancel()
        observeJob = scope.launch {
            audienceDao.observeAll().collect { allAudiences ->
                val enabled = allAudiences.filter { it.enabled && it.audienceType == audienceType }
                val providers = providerDao.getAll()
                syncHeartbeats(enabled, providers)
            }
        }
        speechListenJob?.cancel()
        speechListenJob = scope.launch {
            speechEvent.collect { text -> triggerBySpeech(text) }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
        speechListenJob?.cancel()
        speechListenJob = null
        heartbeatJobs.values.forEach { it.cancel() }
        heartbeatJobs.clear()
        resetChannels.values.forEach { it.close() }
        resetChannels.clear()
        providerMutexes.clear()
        latestFrameProvider = null
        speechTranscriptProvider = null
        lastMentionCheckTime.clear()
        lastLikeTime.clear()
        busyAudiences.clear()
        runtimeStates.clear()
        debugSnapshots.clear()
        _liveState.value = AiAudienceLiveState()
    }

    fun fullReset() {
        stop()
        wallets.clear()
        likeCounts.clear()
        giftSpending.clear()
        recentGifts.clear()
        lastLikerName = null
        lastPostContent.clear()
        lastResponse.clear()
        scope.launch {
            audienceDao.clearAgentStatesByType(audienceType.name)
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    fun compressMemories() {
        val stateSnapshots = runtimeStates.mapValues { (_, state) -> state.toSnapshot() }
        scope.launch {
            val allMessages = messageDao.getRecent(200)
            if (allMessages.isEmpty() && stateSnapshots.isEmpty()) return@launch

            val audiences = audienceDao.getEnabled().filter { it.audienceType == audienceType }
            val providers = providerDao.getAll()
            val memoryA = memoryManager.memoryA
            val memoryB = memoryManager.latestMemoryB

            for (audience in audiences) {
                val provider = providers.find { it.id == audience.providerId } ?: continue
                val llm = OpenAiCompatibleProvider(
                    id = provider.id,
                    displayName = provider.name,
                    endpoint = provider.endpoint,
                    apiKey = provider.apiKey,
                    modelName = provider.modelName
                )
                val snapshot = stateSnapshots[audience.id]
                val prompt = buildString {
                    appendLine("你是「${audience.name}」，以下是你的人设：")
                    appendLine(audience.persona)
                    appendLine()
                    if (audience.personalMemory.isNotBlank()) {
                        appendLine("你之前的长期记忆：")
                        appendLine(audience.personalMemory)
                        appendLine()
                    }
                    if (memoryA.isNotBlank()) appendLine("本场直播核心内容：$memoryA")
                    if (memoryB.isNotBlank()) appendLine("最近摘要：$memoryB")
                    if (snapshot != null) {
                        appendLine("你本场结束时的内部状态：")
                        appendLine("- 情绪：${snapshot.emotion}（${snapshot.emotionIntensity}/100）")
                        appendLine("- 当前目标：${snapshot.currentGoal}")
                        appendLine("- 最近关注：${snapshot.focusTarget}")
                        appendLine("- 社交画像：${snapshot.socialProfile.archetype} / ${snapshot.socialProfile.speakingStyle} / ${snapshot.socialProfile.spendingStyle}")
                        appendLine("- 连续沉默轮数：${snapshot.silenceStreak}")
                        if (snapshot.workingMemory.isNotEmpty()) {
                            appendLine("- 短期记忆：${snapshot.workingMemory.joinToString("；")}")
                        }
                        if (snapshot.episodicMemory.isNotEmpty()) {
                            appendLine("- 主观经历：${snapshot.episodicMemory.joinToString("；")}")
                        }
                        if (snapshot.socialInbox.isNotEmpty()) {
                            appendLine("- 待处理社交事件：${snapshot.socialInbox.joinToString("；") { it.toPromptString() }}")
                        }
                    }
                    appendLine()
                    appendLine("本场全部弹幕（从旧到新）：")
                    allMessages.reversed().forEach { msg ->
                        val who = if (msg.audienceId == audience.id) "你" else msg.audienceName
                        appendLine("- $who: ${msg.content}")
                    }
                    appendLine()
                    appendLine("请用第一人称，更新你的长期记忆。")
                    appendLine("保留最重要的：你对主播的印象、和其他观众的关系、你最在意的事件、你的感受变化。")
                    appendLine("300字以内。只返回记忆文本，不要加标题或格式。")
                }

                try {
                    val memory = llm.chat(
                        systemPrompt = "你是一个角色长期记忆整理助手。",
                        messages = listOf(ChatMessage(role = "user", content = prompt))
                    ).trim()
                    if (memory.isNotBlank() && memory.length < 1000) {
                        audienceDao.updateMemory(audience.id, memory)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Compress memory failed for '${audience.name}'", e)
                }
            }

            messageDao.deleteAll()
        }
    }

    fun getWallet(audienceId: Long): Int = wallets[audienceId] ?: INITIAL_BUDGET

    fun setInitialBudget(audienceId: Long, budget: Int) {
        wallets[audienceId] = budget
    }

    fun getLastPost(audienceId: Long): String? = lastPostContent[audienceId]?.second

    fun getLastResponse(audienceId: Long): String? = lastResponse[audienceId]?.second

    fun getRuntimeDebugSnapshot(audienceId: Long): AgentAudienceDebugSnapshot? = debugSnapshots[audienceId]

    fun getRuntimeDebugSnapshot(audience: AiAudienceEntity): AgentAudienceDebugSnapshot? {
        debugSnapshots[audience.id]?.let { return it }
        val restored = parseRuntimeState(audience.agentStateJson)?.toDebugSnapshot() ?: return null
        debugSnapshots[audience.id] = restored
        return restored
    }

    fun getMemorySnapshot(): MemorySnapshot = MemorySnapshot(
        memoryA = memoryManager.memoryA,
        memoryB = memoryManager.latestMemoryB,
        recentVisual = memoryManager.recentVisual.toList(),
        rawBufferSize = memoryManager.rawSinceLastB.size
    )

    private fun syncHeartbeats(
        enabledAudiences: List<AiAudienceEntity>,
        providers: List<LlmProviderEntity>
    ) {
        val enabledIds = enabledAudiences.map { it.id }.toSet()
        val runningIds = heartbeatJobs.keys.toSet()
        for (id in runningIds - enabledIds) {
            heartbeatJobs.remove(id)?.cancel()
            resetChannels.remove(id)?.close()
            lastMentionCheckTime.remove(id)
            runtimeStates.remove(id)
            debugSnapshots.remove(id)
        }
        for (audience in enabledAudiences) {
            if (audience.id in runningIds) continue
            val provider = providers.find { it.id == audience.providerId } ?: continue
            launchHeartbeat(audience, provider, enabledAudiences)
        }
        _liveState.value = _liveState.value.copy(activeAudienceCount = enabledAudiences.size)
    }

    private fun launchHeartbeat(
        audience: AiAudienceEntity,
        provider: LlmProviderEntity,
        allAudiences: List<AiAudienceEntity>
    ) {
        heartbeatJobs[audience.id]?.cancel()
        resetChannels.remove(audience.id)?.close()
        val llm = OpenAiCompatibleProvider(
            id = provider.id,
            displayName = provider.name,
            endpoint = provider.endpoint,
            apiKey = provider.apiKey,
            modelName = provider.modelName
        )
        val otherNames = allAudiences.filter { it.id != audience.id }.map { it.name }
        val mutex = providerMutexes.getOrPut(provider.id) { Mutex() }
        val resetCh = Channel<String>(Channel.CONFLATED)
        resetChannels[audience.id] = resetCh
        wallets.putIfAbsent(audience.id, INITIAL_BUDGET)

        heartbeatJobs[audience.id] = scope.launch {
            val baseIntervalMs = (audience.heartbeatIntervalSeconds * 1000L).coerceAtLeast(5000L)
            val initialDelay = (audience.id * 3000L) % baseIntervalMs + (1000L..4000L).random()
            var lastTickTime = 0L
            delay(initialDelay)

            while (isActive) {
                val waitMs = (baseIntervalMs + (-2000L..2000L).random()).coerceAtLeast(3000L)
                val triggerType = select<String> {
                    resetCh.onReceive { it }
                    onTimeout(waitMs) { "heartbeat" }
                }
                if (System.currentTimeMillis() - lastTickTime < 5000L) continue

                try {
                    busyAudiences.add(audience.id)
                    lastTickTime = System.currentTimeMillis()
                    val result = mutex.withLock { tick(audience, llm, otherNames, triggerType) }
                    busyAudiences.remove(audience.id)
                    if (result != null) {
                        result.mentionedId?.let { triggerAudience(it, "mention:${audience.name}") }
                        if (result.isHighlight) triggerAllAudiences(audience.id, "highlight")
                    }
                } catch (e: CancellationException) {
                    busyAudiences.remove(audience.id)
                    throw e
                } catch (e: Exception) {
                    busyAudiences.remove(audience.id)
                    Log.e(TAG, "Tick failed for '${audience.name}'", e)
                    delay(5000L)
                }
            }
        }
    }

    private suspend fun triggerBySpeech(text: String) {
        val alreadyTriggered = mutableSetOf<Long>()
        val allAudiences = audienceDao.getEnabled()
        for (audience in allAudiences) {
            if (text.contains(audience.name)) {
                val ch = resetChannels[audience.id]
                if (ch != null && audience.id !in busyAudiences) {
                    ch.trySend("speech_named")
                    alreadyTriggered.add(audience.id)
                }
            }
        }
        triggerRandomAudiences("speech", alreadyTriggered)
    }

    private fun triggerRandomAudiences(triggerType: String, exclude: Set<Long>) {
        val available = resetChannels.entries.filter { it.key !in busyAudiences && it.key !in exclude }
        if (available.isEmpty()) return
        val count = if (available.size == 1) 1 else (1..2).random()
        available.shuffled().take(count).forEach { (_, ch) -> ch.trySend(triggerType) }
    }

    private fun triggerAllAudiences(except: Long, triggerType: String) {
        resetChannels.entries
            .filter { it.key != except && it.key !in busyAudiences }
            .forEach { (_, ch) -> ch.trySend(triggerType) }
    }

    private fun triggerAudience(audienceId: Long, triggerType: String) {
        resetChannels[audienceId]?.trySend(triggerType)
    }

    private suspend fun tick(
        audience: AiAudienceEntity,
        llm: OpenAiCompatibleProvider,
        otherNames: List<String>,
        triggerType: String
    ): TickResult? {
        val now = System.currentTimeMillis()
        val recentMessages = messageDao.getRecent(MAX_AI_CHAT_HISTORY).reversed()
        val mentions = loadPendingMentions(audience.id)
        val recentSpeech = speechTranscriptProvider?.invoke()
            ?.filter { it.first >= now - 2 * 60 * 1000 }
            ?.take(8)
            .orEmpty()

        val state = stateMutex.withLock {
            val runtime = runtimeStates.getOrPut(audience.id) { createRuntimeState(audience) }
            runtime.ensureStreamerRelation()
            runtime.rememberWorkingMemory(promptBuilder.triggerMemoryLine(triggerType))
            hydrateSocialContext(
                state = runtime,
                audience = audience,
                triggerType = triggerType,
                recentMessages = recentMessages,
                mentions = mentions,
                recentSpeech = recentSpeech
            )
            runtime
        }
        val isFirstEntry = !state.hasEntered
        val systemPrompt = promptBuilder.buildSystemPrompt(audience)
        val messages = promptBuilder.buildMessages(
            audience = audience,
            otherNames = otherNames,
            state = state,
            triggerType = triggerType,
            recentMessages = recentMessages,
            mentions = mentions,
            recentSpeech = recentSpeech,
            now = now,
            isFirstEntry = isFirstEntry,
            budget = wallets[audience.id] ?: INITIAL_BUDGET
        )
        val imageUri = if (audience.includeFrame) {
            latestFrameProvider?.invoke()?.let { BitmapEncoding.toDataUri(it) }
        } else {
            null
        }

        lastPostContent[audience.id] = System.currentTimeMillis() to buildDebugPrompt(systemPrompt, messages, imageUri != null)
        val rawResponse = llm.chat(systemPrompt = systemPrompt, messages = messages, imageDataUri = imageUri).trim()
        lastResponse[audience.id] = System.currentTimeMillis() to rawResponse

        val decision = responseParser.parseDecision(rawResponse, state)
        applyDecisionState(audience, state, decision, triggerType)

        var action = parseAction(audience.id, audience.name, decision.action)
        val content = decision.content
            ?.trim()
            ?.takeIf {
                decision.speak &&
                    it.isNotBlank() &&
                    !it.contains(AgentAudienceResponseParser.SILENCE_TOKEN, ignoreCase = true)
            }
        if (action is AudienceAction.Gift && action.gift == GiftType.HIGHLIGHT && content.isNullOrBlank()) {
            action = AudienceAction.None
        }
        if (content == null && action == AudienceAction.None) {
            persistRuntimeState(audience.id)
            return null
        }

        val mentionMatch = content?.let { Regex("@(\\S+)").find(it) }
        val target = mentionMatch?.groupValues?.getOrNull(1)?.let { name ->
            audienceDao.getEnabled().find { it.name.equals(name, ignoreCase = true) }
        }

        val displayContent = content ?: when (action) {
            is AudienceAction.Like -> "❤️"
            is AudienceAction.Gift -> "${action.gift.emoji} ${action.gift.displayName}"
            else -> return null
        }
        val message = AiAudienceMessageEntity(
            audienceId = audience.id,
            audienceName = audience.name,
            content = displayContent,
            mentionedAudienceId = target?.id,
            mentionedAudienceName = target?.name,
            triggerType = triggerType,
            timestamp = System.currentTimeMillis()
        )
        val msgId = messageDao.insert(message)

        _danmakuFlow.emit(
            DanmakuItem(
                id = msgId,
                audienceName = audience.name,
                content = displayContent,
                timestamp = message.timestamp,
                action = action
            )
        )
        stateMutex.withLock {
            applyPostActionSocialEffects(state, audience.name, content, action, target?.name)
            runtimeStates[audience.id] = state
            debugSnapshots[audience.id] = state.toDebugSnapshot()
        }
        persistRuntimeState(audience.id)
        refreshLiveState()
        return TickResult(target?.id, action is AudienceAction.Gift && action.gift == GiftType.HIGHLIGHT)
    }

    private suspend fun loadPendingMentions(audienceId: Long): List<AiAudienceMessageEntity> {
        val sinceTime = lastMentionCheckTime.getOrDefault(audienceId, 0L)
        val mentions = messageDao.getPendingMentions(audienceId, sinceTime)
        if (mentions.isNotEmpty()) {
            lastMentionCheckTime[audienceId] = mentions.maxOf { it.timestamp }
        }
        return mentions
    }

    private fun buildDebugPrompt(systemPrompt: String, messages: List<ChatMessage>, hasImage: Boolean): String = buildString {
        appendLine("=== System ===")
        appendLine(systemPrompt)
        appendLine("=== Messages (${messages.size}) ===")
        messages.forEachIndexed { index, message ->
            appendLine("[${index + 1}|${message.role}] ${message.content}")
        }
        if (hasImage) appendLine("[image attached]")
    }

    private suspend fun applyDecisionState(
        audience: AiAudienceEntity,
        state: AgentRuntimeState,
        decision: AgentDecision,
        triggerType: String
    ) {
        stateMutex.withLock {
            state.emotion = decision.emotion
            state.emotionIntensity = decision.emotionIntensity
            state.currentGoal = decision.goal
            state.focusTarget = decision.focus
            decision.relationUpdates.take(2).forEach { update ->
                val target = update.target?.takeIf { it.isNotBlank() } ?: return@forEach
                val relation = state.relations.getOrPut(target) { AgentRelationState() }
                relation.affinity = (relation.affinity + update.affinityDelta).coerceIn(-5, 5)
                relation.familiarity = (relation.familiarity + update.familiarityDelta).coerceIn(0, 10)
                relation.tension = (relation.tension + update.tensionDelta).coerceIn(0, 10)
                if (!update.note.isNullOrBlank()) relation.note = update.note.take(40)
            }
            val actionSummary = when {
                !decision.content.isNullOrBlank() -> "发言：${decision.content.take(24)}"
                decision.action != "none" -> "动作：${decision.action}"
                else -> "这轮选择沉默"
            }
            state.lastActionSummary = actionSummary
            if (!decision.content.isNullOrBlank() || decision.action != "none") {
                state.hasEntered = true
                state.silenceStreak = 0
            } else {
                state.silenceStreak = (state.silenceStreak + 1).coerceAtMost(20)
            }
            if (decision.memory.isNotBlank()) state.rememberEpisode(decision.memory)
            when {
                !decision.content.isNullOrBlank() -> state.rememberWorkingMemory("我刚刚说了：${decision.content.take(40)}")
                decision.action != "none" -> state.rememberWorkingMemory("我刚刚执行了动作：${decision.action}")
                else -> state.rememberWorkingMemory("这轮因 ${promptBuilder.triggerDescription(triggerType)} 触发，但我选择沉默")
            }
            if (triggerType == "speech_named") {
                val streamer = state.relations.getOrPut("主播") { AgentRelationState() }
                streamer.familiarity = (streamer.familiarity + 1).coerceIn(0, 10)
                streamer.affinity = (streamer.affinity + 1).coerceIn(-5, 5)
                streamer.note = "主播刚刚点到了我"
            }
            runtimeStates[audience.id] = state
            debugSnapshots[audience.id] = state.toDebugSnapshot()
        }
    }

    private fun createRuntimeState(audience: AiAudienceEntity): AgentRuntimeState {
        val restored = parseRuntimeState(audience.agentStateJson)
        return (restored ?: AgentRuntimeState(socialProfile = deriveSocialProfile(audience))).also {
            it.ensureStreamerRelation()
            debugSnapshots[audience.id] = it.toDebugSnapshot()
        }
    }

    private fun parseRuntimeState(rawJson: String): AgentRuntimeState? {
        if (rawJson.isBlank()) return null
        return runCatching {
            gson.fromJson(rawJson, PersistedAgentState::class.java)?.toRuntimeState()
        }.recoverCatching {
            gson.fromJson(rawJson, PersistedAgentRuntimeState::class.java)?.toRuntimeState()
        }.onFailure {
            Log.w(TAG, "Failed to restore agent runtime state", it)
        }.getOrNull()
    }

    private suspend fun persistRuntimeState(audienceId: Long) {
        val stateJson = stateMutex.withLock {
            runtimeStates[audienceId]?.let { gson.toJson(it.toPersistedState()) }
        } ?: return
        runCatching {
            audienceDao.updateAgentState(audienceId, stateJson)
        }.onFailure {
            Log.w(TAG, "Failed to persist agent runtime state for $audienceId", it)
        }
    }

    private fun AgentRuntimeState.toDebugSnapshot(): AgentAudienceDebugSnapshot {
        val relationLines = relations.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, AgentRelationState>> { it.value.familiarity }
                    .thenByDescending { it.value.affinity - it.value.tension }
            )
            .take(MAX_RELATIONS_IN_PROMPT)
            .map { (target, relation) -> "$target: ${relation.toPromptString()}" }
        return AgentAudienceDebugSnapshot(
            emotion = emotion,
            emotionIntensity = emotionIntensity,
            currentGoal = currentGoal,
            focusTarget = focusTarget,
            lastActionSummary = lastActionSummary,
            silenceStreak = silenceStreak,
            hasEntered = hasEntered,
            socialArchetype = socialProfile.archetype,
            speakingStyle = socialProfile.speakingStyle,
            spendingStyle = socialProfile.spendingStyle,
            socialDrive = socialProfile.socialDrive,
            recentInteractionTargets = recentInteractionTargets.toList(),
            socialInbox = socialInbox.map { it.toPromptString() }.toList(),
            relationSummaries = relationLines
        )
    }

    private fun deriveSocialProfile(audience: AiAudienceEntity): AgentSocialProfile {
        val persona = audience.persona
        val archetype = when {
            audience.socialArchetype.isNotBlank() -> audience.socialArchetype
            "乐子" in persona || "整活" in persona || "搞笑" in persona -> "乐子人"
            "理性" in persona || "分析" in persona || "冷静" in persona -> "理中客"
            "热情" in persona || "活泼" in persona || "外向" in persona -> "气氛组"
            "毒舌" in persona || "吐槽" in persona || "阴阳" in persona -> "吐槽役"
            "守护" in persona || "支持" in persona || "陪伴" in persona -> "守护型观众"
            else -> "普通观众"
        }
        val speakingStyle = when {
            audience.speakingStyle.isNotBlank() -> audience.speakingStyle
            "高冷" in persona || "冷淡" in persona -> "少量发言，偏克制"
            "话痨" in persona || "健谈" in persona || "活泼" in persona -> "爱接话，口语感强"
            "阴阳" in persona || "毒舌" in persona -> "会吐槽，略带刺"
            "温柔" in persona || "治愈" in persona -> "温和、安抚感强"
            else -> "口语化、简短"
        }
        val spendingStyle = when {
            audience.spendingStyle.isNotBlank() -> audience.spendingStyle
            "富" in persona || "土豪" in persona || "打赏" in persona -> "愿意为情绪价值花钱"
            "抠" in persona || "节省" in persona || "理性" in persona -> "很谨慎，轻易不送大礼"
            "支持" in persona || "守护" in persona -> "会为了支持主播适度消费"
            else -> "谨慎消费"
        }
        val socialDrive = when {
            audience.socialDrive.isNotBlank() -> audience.socialDrive
            "存在感" in persona || "关注" in persona -> "想被看见，喜欢被主播注意"
            "交朋友" in persona || "互动" in persona -> "喜欢和其他观众建立关系"
            "整活" in persona || "乐子" in persona -> "想制造节目效果"
            "陪伴" in persona || "支持" in persona -> "重视陪伴和支持感"
            else -> "先观察，再挑值得互动的对象"
        }
        return AgentSocialProfile(
            archetype = archetype,
            speakingStyle = speakingStyle,
            spendingStyle = spendingStyle,
            socialDrive = socialDrive
        )
    }

    private fun hydrateSocialContext(
        state: AgentRuntimeState,
        audience: AiAudienceEntity,
        triggerType: String,
        recentMessages: List<AiAudienceMessageEntity>,
        mentions: List<AiAudienceMessageEntity>,
        recentSpeech: List<Pair<Long, String>>
    ) {
        when {
            triggerType == "speech_named" -> {
                state.focusTarget = "主播"
                state.currentGoal = "接住主播刚点到我的话题"
                state.pushSocialEvent("主播", "刚刚点到了我的名字", "点名")
            }
            triggerType == "speech" && recentSpeech.isNotEmpty() -> {
                state.focusTarget = "主播"
                state.currentGoal = "判断是否要接主播的话"
                state.pushSocialEvent("主播", recentSpeech.first().second.take(60), "发言")
            }
            triggerType == "highlight" -> {
                state.currentGoal = "对高曝光内容做出反应"
                state.pushSocialEvent("全场", "出现了一条醒目留言", "高光")
            }
        }

        mentions.takeLast(2).forEach { mention ->
            val relation = state.relations.getOrPut(mention.audienceName) { AgentRelationState() }
            relation.familiarity = (relation.familiarity + 1).coerceIn(0, 10)
            state.focusTarget = mention.audienceName
            state.currentGoal = "决定要不要回应 ${mention.audienceName}"
            state.noteInteractionTarget(mention.audienceName)
            state.pushSocialEvent(mention.audienceName, mention.content.take(60), "@提及")
        }

        recentMessages.takeLast(4)
            .filter { it.audienceId != audience.id }
            .forEach { msg ->
                val relation = state.relations.getOrPut(msg.audienceName) { AgentRelationState() }
                relation.familiarity = (relation.familiarity + 1).coerceIn(0, 10)
                if (msg.content.contains(audience.name)) {
                    relation.affinity = (relation.affinity + 1).coerceIn(-5, 5)
                    state.pushSocialEvent(msg.audienceName, msg.content.take(60), "被提到")
                }
            }
    }

    private fun applyPostActionSocialEffects(
        state: AgentRuntimeState,
        audienceName: String,
        content: String?,
        action: AudienceAction,
        targetName: String?
    ) {
        if (!targetName.isNullOrBlank()) {
            val relation = state.relations.getOrPut(targetName) { AgentRelationState() }
            relation.familiarity = (relation.familiarity + 1).coerceIn(0, 10)
            relation.affinity = (relation.affinity + 1).coerceIn(-5, 5)
            relation.note = "我最近主动和 Ta 互动过"
            state.noteInteractionTarget(targetName)
        }
        if (!content.isNullOrBlank()) {
            if (content.contains("@主播")) {
                state.noteInteractionTarget("主播")
            }
            if (content.contains("主播")) {
                val streamer = state.relations.getOrPut("主播") { AgentRelationState() }
                streamer.familiarity = (streamer.familiarity + 1).coerceIn(0, 10)
            }
        }
        when (action) {
            is AudienceAction.Gift -> {
                val streamer = state.relations.getOrPut("主播") { AgentRelationState() }
                streamer.affinity = (streamer.affinity + if (action.gift.cost >= 30) 2 else 1).coerceIn(-5, 5)
                state.currentGoal = "观察这次送礼有没有带来反馈"
                state.pushSocialEvent(audienceName, "我刚送出了 ${action.gift.displayName}", "送礼")
            }
            is AudienceAction.Like -> {
                state.currentGoal = "轻量表达支持后继续观察"
            }
            else -> Unit
        }
    }

    private fun parseAction(audienceId: Long, audienceName: String, rawAction: String): AudienceAction {
        val action = rawAction.lowercase()
        return when {
            action == "like" || action.contains("点赞") -> {
                val now = System.currentTimeMillis()
                val last = lastLikeTime[audienceId] ?: 0L
                if (now - last >= LIKE_COOLDOWN_MS) {
                    lastLikeTime[audienceId] = now
                    likeCounts[audienceName] = (likeCounts[audienceName] ?: 0) + 1
                    lastLikerName = audienceName
                    AudienceAction.Like
                } else {
                    AudienceAction.None
                }
            }

            action.startsWith("gift:") || action.contains("送礼") || action.contains("gift") -> {
                val gift = GiftType.fromName(rawAction)
                if (gift == null) {
                    AudienceAction.None
                } else {
                    val budget = wallets[audienceId] ?: 0
                    if (budget < gift.cost) {
                        AudienceAction.None
                    } else {
                        wallets[audienceId] = budget - gift.cost
                        giftSpending[audienceName] = (giftSpending[audienceName] ?: 0) + gift.cost
                        recentGifts.add(GiftEvent(audienceName, gift, System.currentTimeMillis()))
                        AudienceAction.Gift(gift)
                    }
                }
            }

            else -> AudienceAction.None
        }
    }

    private suspend fun refreshLiveState() {
        val messages = messageDao.getRecent(MAX_LIVE_MESSAGES)
        while (recentGifts.size > 10) recentGifts.removeAt(0)
        _liveState.value = _liveState.value.copy(
            messages = messages,
            likeBoard = likeCounts.entries.sortedByDescending { it.value }.take(3).map { LikeRankEntry(it.key, it.value) },
            lastLiker = lastLikerName,
            giftBoard = giftSpending.entries.sortedByDescending { it.value }.take(3).map { GiftRankEntry(it.key, it.value) },
            recentGifts = recentGifts.takeLast(3)
        )
    }

    private data class TickResult(
        val mentionedId: Long?,
        val isHighlight: Boolean
    )
}
