package com.example.watcher.data.repository

import android.util.Log
import com.example.watcher.data.model.CommentaryPromptProfile
import com.example.watcher.data.model.EntityStatus
import com.example.watcher.data.model.SceneProfile
import com.example.watcher.data.model.SceneEntity
import com.example.watcher.data.remote.ContentItem
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoRequest
import com.example.watcher.data.remote.Message
import com.example.watcher.data.remote.extractOutputText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Three-layer scene memory with structured entity management.
 *
 * Layer 1 - sceneMemory:  Static environment description
 * Layer 2 - entities:     Structured SceneEntity objects (CRUD by Consumer A)
 * Layer 3 - actionSummary: Rolling dynamic events summary
 *
 * Consumer A operates via structured tags:
 *   [SCENE] / [NEW] / [ATTR] / [NOTE] / [FIX] / [STATUS] / [ACTION] / [ASK]
 */
class SceneMemoryManager(
    private val apiService: DoubaoApiService,
    private val llmWalletRepository: LlmWalletRepository,
    private val promptProfile: CommentaryPromptProfile = CommentaryPromptProfile.liveRoom()
) {
    companion object {
        private const val TAG = "SceneMemory"
        /** Each set of expert requests is injected into at most this many consumer prompts. */
        private const val MAX_EXPERT_REQUEST_SERVES = 4
    }

    private val mutex = Mutex()

    // Layer 1: Static scene
    var sceneMemory: String = ""
        private set

    // Layer 2: Structured entities
    private val _entities = mutableMapOf<String, SceneEntity>()
    val entities: Map<String, SceneEntity> get() = _entities.toMap()

    // Layer 3: Dynamic action summary
    var actionSummary: String = ""
        private set

    // ASK requests from Consumer A (builder)
    private val _pendingRequests = mutableListOf<String>()
    // Observation requests from council experts (visual-only, auto-expire)
    private val _expertRequests = mutableListOf<String>()
    private var expertRequestsSetAt = 0L
    private var expertRequestsServedCount = 0
    private val _actionBuffer = mutableListOf<String>()

    // Phase tracking
    enum class Phase(val displayName: String) {
        BOOTSTRAP("冷启动"),
        BUILDING("补全中"),
        STEADY("稳态")
    }

    var phase: Phase = Phase.BOOTSTRAP
        private set
    private var processedCount = 0
    private var currentSegmentIndex = 0
    var currentSceneProfileId: String? = null
        private set
    private var recalledSceneLabel: String = ""
    private var recalledSceneProbeSummary: String = ""
    private var recalledMatchedAnchors: List<String> = emptyList()

    fun shouldProcess(segmentIndex: Int): Boolean {
        return when (phase) {
            Phase.BOOTSTRAP -> true
            Phase.BUILDING -> segmentIndex % 2 == 0
            Phase.STEADY -> segmentIndex % 5 == 0
        }
    }

    fun preloadSceneProfile(
        profile: SceneProfile,
        probeSummary: String,
        matchedAnchors: List<String> = emptyList()
    ) {
        sceneMemory = profile.summary
        actionSummary = "候选场景已召回：${profile.label}"
        currentSceneProfileId = profile.sceneId
        recalledSceneLabel = profile.label
        recalledSceneProbeSummary = probeSummary
        recalledMatchedAnchors = matchedAnchors
        phase = Phase.BUILDING
        processedCount = processedCount.coerceAtLeast(6)
        _pendingRequests.clear()
        _pendingRequests += buildSceneVerificationAsks(profile, matchedAnchors)
    }

    suspend fun processCommentary(commentaryText: String, segmentIndex: Int = 0) = mutex.withLock {
        processedCount++
        currentSegmentIndex = segmentIndex
        _actionBuffer.add(commentaryText)

        val prompt = buildBuilderPrompt(commentaryText)
        val result = callLlm(prompt) ?: return@withLock

        parseBuilderOutput(result)
        updatePhase()

        Log.d(TAG, "Processed #$processedCount [${phase.displayName}]: scene=${sceneMemory.length}c, entities=${_entities.size}, asks=${_pendingRequests.size}")
    }

    fun getPendingRequests(): List<String> = _pendingRequests.toList()

    /** Read-only snapshot for UI display — does not affect expiry counter. */
    fun getExpertRequests(): List<String> = _expertRequests.toList()

    /**
     * Returns current expert requests for prompt injection. Each call increments
     * the served counter. After [MAX_EXPERT_REQUEST_SERVES] consumer segments,
     * requests auto-expire (consumers have had enough chances to respond).
     */
    @Synchronized
    fun consumeExpertRequests(): List<String> {
        if (_expertRequests.isEmpty()) return emptyList()
        expertRequestsServedCount++
        if (expertRequestsServedCount > MAX_EXPERT_REQUEST_SERVES) {
            _expertRequests.clear()
            return emptyList()
        }
        return _expertRequests.toList()
    }

    /**
     * Replace all expert observation requests. Called by CouncilManager after
     * each Gathering phase — empty list clears previous requests.
     */
    @Synchronized
    fun setExpertRequests(requests: List<String>) {
        _expertRequests.clear()
        _expertRequests.addAll(requests.take(8))
        expertRequestsSetAt = System.currentTimeMillis()
        expertRequestsServedCount = 0
    }

    /**
     * Append additional observation requests (e.g. from discussion phase).
     * Deduplicates and caps at 8 total.
     */
    @Synchronized
    fun appendExpertRequests(requests: List<String>) {
        val existing = _expertRequests.toSet()
        val newOnes = requests.filter { it !in existing }
        _expertRequests.addAll(newOnes)
        while (_expertRequests.size > 8) _expertRequests.removeAt(0)
        // Reset serve counter so the appended requests also get served
        expertRequestsServedCount = 0
    }


    /** Build full scene context for B/C commentary prompts */
    fun buildSceneContext(): String = buildString {
        if (currentSceneProfileId != null) {
            appendLine("【候选场景档案】（优先验证是否仍为该场景）")
            appendLine("标签：$recalledSceneLabel")
            if (sceneMemory.isNotBlank()) appendLine("摘要：$sceneMemory")
            if (recalledMatchedAnchors.isNotEmpty()) appendLine("命中锚点：${recalledMatchedAnchors.joinToString("、")}")
            if (recalledSceneProbeSummary.isNotBlank()) appendLine("当前帧粗识别：$recalledSceneProbeSummary")
            appendLine()
        }
        if (sceneMemory.isNotBlank()) {
            appendLine("【固定场景】（不需要再描述）")
            appendLine(sceneMemory)
            appendLine()
        }
        val activeEntities = _entities.values.filter { it.status == EntityStatus.ACTIVE }
        if (activeEntities.isNotEmpty()) {
            appendLine("【已识别实体】（不需要重新介绍，只描述新动作）")
            activeEntities.forEach { appendLine("- ${it.toPromptString()}") }
            appendLine()
        }
        if (actionSummary.isNotBlank()) {
            appendLine("【近期动态摘要】")
            appendLine(actionSummary)
            appendLine()
        }
        if (_actionBuffer.isNotEmpty()) {
            appendLine("【最近几段解说】")
            _actionBuffer.takeLast(3).forEach { appendLine("- $it") }
        }
    }

    /**
     * Build a low-contamination context block for B/C observers.
     *
     * Only stable scene/entity context is included. Dynamic summaries and
     * recent commentary are intentionally excluded to avoid carrying over
     * old actions as if they were still visible in the current segment.
     */
    fun buildObservationContext(): String = buildString {
        if (currentSceneProfileId != null) {
            appendLine("【候选场景档案】（仅用于确认是否仍是该场景，不代表当前动作仍在继续）")
            appendLine("标签：$recalledSceneLabel")
            if (sceneMemory.isNotBlank()) appendLine("摘要：$sceneMemory")
            if (recalledMatchedAnchors.isNotEmpty()) appendLine("命中锚点：${recalledMatchedAnchors.joinToString("、")}")
            if (recalledSceneProbeSummary.isNotBlank()) appendLine("当前帧粗识别：$recalledSceneProbeSummary")
            appendLine()
        }
        if (sceneMemory.isNotBlank()) {
            appendLine("【固定场景】（稳定背景，仅用于减少重复描述）")
            appendLine(sceneMemory)
            appendLine()
        }
        val activeEntities = _entities.values.filter { it.status == EntityStatus.ACTIVE }
        if (activeEntities.isNotEmpty()) {
            appendLine("【已识别实体】（稳定身份信息，不代表其当前动作仍在继续）")
            activeEntities.forEach { appendLine("- ${it.toPromptString()}") }
            appendLine()
        }
    }

    /** Build entity memory as readable text (for UI display and AI audience context) */
    fun buildEntitySummary(): String {
        if (_entities.isEmpty()) return ""
        return _entities.values.joinToString("\n") { it.toPromptString() }
    }

    fun reset() {
        sceneMemory = ""
        _entities.clear()
        actionSummary = ""
        _pendingRequests.clear()
        _expertRequests.clear()
        _actionBuffer.clear()
        processedCount = 0
        phase = Phase.BOOTSTRAP
        currentSceneProfileId = null
        recalledSceneLabel = ""
        recalledSceneProbeSummary = ""
        recalledMatchedAnchors = emptyList()
    }

    // --- Builder prompt ---

    private fun buildBuilderPrompt(latestText: String): String = buildString {
        appendLine(promptProfile.builderRole)
        appendLine()

        // Current state
        if (sceneMemory.isNotBlank()) appendLine("当前固定场景：$sceneMemory")
        if (_entities.isNotEmpty()) {
            appendLine("当前已识别实体（${_entities.size}个）：")
            _entities.values.forEach { appendLine("  ${it.toPromptString()}") }
        }
        if (actionSummary.isNotBlank()) appendLine("当前动态摘要：$actionSummary")
        appendLine()

        appendLine("最近的画面描述：")
        _actionBuffer.takeLast(3).forEach { appendLine("- $it") }
        appendLine()
        appendLine("最新一段：$latestText")
        appendLine()

        when (phase) {
            Phase.BOOTSTRAP -> appendLine(promptProfile.builderBootstrapHint)
            Phase.BUILDING -> appendLine(promptProfile.builderBuildingHint)
            Phase.STEADY -> appendLine(promptProfile.builderSteadyHint)
        }

        appendLine()
        appendLine("用以下标签操作（每行一个，只输出需要的操作）：")
        appendLine()
        appendLine("[SCENE] 完整固定场景描述（150字内，无变化则不输出）")
        appendLine()
        appendLine("[NEW] id | 名称 | 类型 | 初始描述")
        appendLine("  创建新实体。id 用英文下划线命名(如 player_red_5)，类型如 person/object/team/animal")
        appendLine("  例：[NEW] player_red_5 | 红队5号 | person | 高个短发，核心控球手")
        appendLine()
        appendLine("[ATTR] id | 属性名 | 属性值")
        appendLine("  给已有实体添加或更新一个属性")
        appendLine("  例：[ATTR] player_red_5 | 服装 | 红色球衣，白色短裤")
        appendLine("  例：[ATTR] player_red_5 | 编号 | 5")
        appendLine()
        appendLine("[NOTE] id | 备注内容")
        appendLine("  给实体添加观察备注（推测、行为模式等）")
        appendLine("  例：[NOTE] player_red_5 | 似乎是队长，经常组织进攻")
        appendLine()
        appendLine("[FIX] id | 属性名 | 修正值")
        appendLine("  校对修正已有属性（发现之前记录有误时使用）")
        appendLine("  例：[FIX] player_red_5 | 编号 | 15（之前误认为5号）")
        appendLine()
        appendLine("[STATUS] id | active/left/uncertain")
        appendLine("  更新实体状态（离场、重新出现等）")
        appendLine("  例：[STATUS] player_blue_7 | left")
        appendLine()
        appendLine("[ACTION] 近期动态压缩摘要（100字内）")
        appendLine("[ASK] 需要解说员下次重点观察的问题")
        appendLine()
        appendLine("只输出标签行。")
    }

    // --- Parse output ---

    private fun parseBuilderOutput(output: String) {
        val newRequests = mutableListOf<String>()

        for (line in output.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[SCENE]") -> {
                    val v = trimmed.removePrefix("[SCENE]").trim()
                    if (v.isNotBlank()) {
                        sceneMemory = v
                        Log.d(TAG, "Scene: ${v.take(50)}...")
                    }
                }

                trimmed.startsWith("[NEW]") -> {
                    parseNewEntity(trimmed.removePrefix("[NEW]").trim())
                }

                trimmed.startsWith("[ATTR]") -> {
                    parseAttr(trimmed.removePrefix("[ATTR]").trim(), isFix = false)
                }

                trimmed.startsWith("[FIX]") -> {
                    parseAttr(trimmed.removePrefix("[FIX]").trim(), isFix = true)
                }

                trimmed.startsWith("[NOTE]") -> {
                    parseNote(trimmed.removePrefix("[NOTE]").trim())
                }

                trimmed.startsWith("[STATUS]") -> {
                    parseStatus(trimmed.removePrefix("[STATUS]").trim())
                }

                trimmed.startsWith("[ACTION]") -> {
                    val v = trimmed.removePrefix("[ACTION]").trim()
                    if (v.isNotBlank()) actionSummary = v
                }

                trimmed.startsWith("[ASK]") -> {
                    val v = trimmed.removePrefix("[ASK]").trim()
                    if (v.isNotBlank()) newRequests.add(v)
                }
            }
        }

        val mergedRequests = (_pendingRequests + newRequests)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
        _pendingRequests.clear()
        _pendingRequests.addAll(mergedRequests)
        while (_actionBuffer.size > 10) _actionBuffer.removeAt(0)
    }

    private fun parseNewEntity(raw: String) {
        val parts = raw.split("|").map { it.trim() }
        if (parts.size < 3) return

        val id = parts[0]
        val name = parts[1]
        val type = parts.getOrElse(2) { "unknown" }
        val desc = parts.getOrElse(3) { "" }

        if (_entities.containsKey(id)) {
            Log.d(TAG, "Entity $id already exists, adding note instead")
            if (desc.isNotBlank()) _entities[id]?.notes?.add(desc)
            return
        }

        val entity = SceneEntity(
            id = id, name = name, type = type,
            attributes = mutableMapOf(),
            notes = if (desc.isNotBlank()) mutableListOf(desc) else mutableListOf(),
            firstSeenSegment = currentSegmentIndex,
            lastSeenSegment = currentSegmentIndex
        )
        _entities[id] = entity
        Log.d(TAG, "Entity created: ${entity.toPromptString()}")
    }

    private fun parseAttr(raw: String, isFix: Boolean) {
        val parts = raw.split("|").map { it.trim() }
        if (parts.size < 3) return

        val id = parts[0]
        val key = parts[1]
        val value = parts[2]
        val entity = _entities[id] ?: return

        if (isFix) Log.d(TAG, "Entity $id FIX: $key = $value (was: ${entity.attributes[key]})")
        else Log.d(TAG, "Entity $id ATTR: $key = $value")

        entity.attributes[key] = value
        entity.lastSeenSegment = currentSegmentIndex
    }

    private fun parseNote(raw: String) {
        val parts = raw.split("|", limit = 2).map { it.trim() }
        if (parts.size < 2) return

        val entity = _entities[parts[0]] ?: return
        entity.notes.add(parts[1])
        entity.lastSeenSegment = currentSegmentIndex
        if (entity.notes.size > 5) entity.notes.removeAt(0)
        Log.d(TAG, "Entity ${parts[0]} NOTE: ${parts[1]}")
    }

    private fun parseStatus(raw: String) {
        val parts = raw.split("|").map { it.trim() }
        if (parts.size < 2) return

        val entity = _entities[parts[0]] ?: return
        entity.status = when (parts[1].lowercase()) {
            "left" -> EntityStatus.LEFT
            "uncertain" -> EntityStatus.UNCERTAIN
            else -> EntityStatus.ACTIVE
        }
        entity.lastSeenSegment = currentSegmentIndex
        Log.d(TAG, "Entity ${parts[0]} STATUS: ${entity.status.label}")
    }

    // --- Phase ---

    private fun updatePhase() {
        phase = when {
            sceneMemory.isBlank() -> Phase.BOOTSTRAP
            _entities.size < 2 -> Phase.BOOTSTRAP
            processedCount < 10 -> Phase.BUILDING
            else -> Phase.STEADY
        }
    }

    private fun buildSceneVerificationAsks(
        profile: SceneProfile,
        matchedAnchors: List<String>
    ): List<String> {
        val asks = mutableListOf<String>()
        asks += "优先确认当前画面是否仍是「${profile.label}」这个固定场景。"
        if (matchedAnchors.isNotEmpty()) {
            asks += "请验证这些锚点是否仍然存在：${matchedAnchors.joinToString("、")}。"
        } else if (profile.anchorObjects.isNotBlank()) {
            asks += "请验证这些锚点是否仍然存在：${profile.anchorObjects}。"
        }
        asks += "如果与已知场景不一致，请只描述新增、缺失或位置变化最明显的物品。"
        return asks.take(3)
    }

    // --- LLM ---

    private suspend fun callLlm(prompt: String): String? {
        return try {
            val llmConfig = llmWalletRepository.resolveArkResponsesConfig(ArkConfig.intentModel)
            val response = apiService.analyzeIntent(
                authorization = llmConfig.bearerToken(),
                request = DoubaoRequest(
                    model = llmConfig.modelName,
                    input = listOf(
                        Message(role = "user", content = listOf(ContentItem(type = "input_text", text = prompt)))
                    )
                )
            )
            response.extractOutputText()?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "LLM call failed: ${e.message}")
            null
        }
    }
}
