package com.example.watcher.data.repository

import com.example.watcher.agentframework.autonomy.AutonomousAgentEvent
import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.core.AgentMemoryScope
import com.example.watcher.agentframework.core.AgentMessageRole
import com.example.watcher.agentframework.core.AgentRunConfig
import com.example.watcher.agentframework.integration.readAppAgentBrainProfileConfig
import com.example.watcher.agentframework.integration.withAppAgentBrainProfileConfig
import com.example.watcher.agentframework.service.AgentBrainCatalog
import com.example.watcher.agentframework.service.AgentBrainConnectionTester
import com.example.watcher.agentframework.service.AgentInvocationInput
import com.example.watcher.agentframework.service.AgentInvocationRequest
import com.example.watcher.agentframework.service.AgentKnowledgeSeed
import com.example.watcher.agentframework.service.AgentMemorySeed
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.remote.ChatMessage
import com.example.watcher.data.remote.OpenAiCompatibleProvider
import com.example.watcher.agentframework.knowledge.AgentKnowledgeEntry
import com.example.watcher.agentframework.memory.AgentMemoryEntry
import com.example.watcher.agentframework.service.AgentFrameworkService
import com.example.watcher.agentframework.service.AgentRegistration
import com.example.watcher.agentframework.service.AutonomousAgentRuntimeRecord
import com.example.watcher.agentframework.service.RegisteredAgentProfile
import com.example.watcher.data.repository.ArkConfig
import com.example.watcher.ui.viewmodel.AgentActivityItemUiModel
import com.example.watcher.ui.viewmodel.AgentDetailUiModel
import com.example.watcher.ui.viewmodel.AgentEditorDraft
import com.example.watcher.ui.viewmodel.AgentListItemUiModel
import com.example.watcher.ui.viewmodel.AgentRunUiModel
import com.example.watcher.ui.viewmodel.SavedBrainUiModel
import com.example.watcher.ui.viewmodel.AgentTextRecordUiModel
import com.example.watcher.ui.viewmodel.toSavedBrainUiModel
import com.example.watcher.ui.viewmodel.toAppAgentBrainProfileConfig

class AgentConfigRepository(
    private val service: AgentFrameworkService,
    private val brainCatalog: AgentBrainCatalog,
    private val brainConnectionTester: AgentBrainConnectionTester,
    private val llmWalletRepository: LlmWalletRepository
) {
    suspend fun listAgentItems(): List<AgentListItemUiModel> {
        val profiles = service.listAgents()
        val providersById = llmWalletRepository.listProviders().associateBy { it.id }
        val latestRuntimeByAgent = linkedMapOf<String, AutonomousAgentRuntimeRecord>()
        service.listAutonomousRuntimes().forEach { runtime ->
            if (latestRuntimeByAgent.containsKey(runtime.agentId)) return@forEach
            latestRuntimeByAgent[runtime.agentId] = runtime
        }
        return profiles.map { profile ->
            val runtime = latestRuntimeByAgent[profile.agentId]
            val brainConfig = profile.readAppAgentBrainProfileConfig()
            AgentListItemUiModel(
                agentId = profile.agentId,
                name = profile.definition.name,
                description = profile.definition.description,
                tags = profile.tags.toList().sorted(),
                updatedAt = profile.updatedAt,
                brainSummary = when {
                    brainConfig.providerId.isNotBlank() -> {
                        providersById[brainConfig.providerId]?.name ?: brainConfig.displayName.ifBlank { brainConfig.providerId }
                    }
                    brainConfig.hasCustomConnection() -> {
                    brainConfig.displayName.ifBlank {
                        brainConfig.modelName.ifBlank { brainConfig.endpoint }
                    }
                    }
                    else -> "Default brain"
                },
                usesCustomBrain = brainConfig.providerId.isNotBlank() || brainConfig.hasCustomConnection(),
                latestRuntimeState = runtime?.lifecycleState?.name,
                latestRuntimeAt = runtime?.updatedAt
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun listSavedBrains(): List<SavedBrainUiModel> {
        return llmWalletRepository.listProviders().map { it.toSavedBrainUiModel() }
    }

    suspend fun loadAgentDetail(agentId: String): AgentDetailUiModel? {
        val profile = service.getAgentProfile(agentId) ?: return null
        val runs = service.listAutonomousRuntimes(agentId).take(10)
        val latestRuntimeId = runs.firstOrNull()?.runtimeId
        val events = latestRuntimeId?.let { service.getAutonomousRuntimeEvents(it).takeLast(50) }.orEmpty()
        val workingMemory = latestRuntimeId?.let {
            service.readRuntimeMemory(it, AgentMemoryScope.Working, limit = 30)
        }.orEmpty()
        val episodicMemory = latestRuntimeId?.let {
            service.readRuntimeMemory(it, AgentMemoryScope.Episodic, limit = 30)
        }.orEmpty()
        return AgentDetailUiModel(
            profile = profile,
            knowledge = service.readAgentKnowledge(agentId, limit = 50).map { it.toUiModel("Knowledge") },
            workingMemory = workingMemory.map { it.toUiModel("Working Memory") },
            episodicMemory = episodicMemory.map { it.toUiModel("Episodic Memory") },
            runs = runs.map { it.toUiModel() },
            activities = events.mapIndexed { index, event -> event.toUiModel(index) },
            latestRuntimeId = latestRuntimeId
        )
    }

    suspend fun migrateLegacyBrainSecrets(): Boolean {
        var migratedAny = false
        service.listAgents().forEach { profile ->
            val config = profile.readAppAgentBrainProfileConfig()
            if (
                config.providerId.isNotBlank() ||
                config.apiKey.isBlank() ||
                config.endpoint.isBlank()
            ) {
                return@forEach
            }

            val provider = config.toProviderEntity()
            llmWalletRepository.upsertProvider(provider, makeDefault = false)
            service.registerAgent(
                AgentRegistration(
                    definition = profile.definition,
                    brainFactoryId = profile.brainFactoryId,
                    config = profile.config,
                    tags = profile.tags,
                    metadata = profile.metadata.withAppAgentBrainProfileConfig(
                        com.example.watcher.agentframework.integration.AppAgentBrainProfileConfig(
                            providerId = provider.id,
                            displayName = provider.name,
                            modelName = provider.modelName
                        )
                    )
                )
            )
            migratedAny = true
        }
        return migratedAny
    }

    suspend fun saveAgent(
        draft: AgentEditorDraft,
        existingProfile: RegisteredAgentProfile? = null
    ): RegisteredAgentProfile {
        val registration = buildAgentRegistration(draft, existingProfile)
        service.registerAgent(registration)
        val normalizedId = registration.definition.agentId
        if (existingProfile != null && existingProfile.agentId != normalizedId) {
            service.unregisterAgent(existingProfile.agentId)
        }
        return service.getAgentProfile(normalizedId)
            ?: error("Agent profile was not persisted: $normalizedId")
    }

    suspend fun deleteAgent(agentId: String) {
        service.unregisterAgent(agentId)
    }

    suspend fun deleteKnowledgeEntry(agentId: String, entryId: String) {
        val deleted = service.deleteAgentKnowledgeEntry(agentId, entryId)
        require(deleted) { "Knowledge entry not found: $entryId" }
    }

    suspend fun testBrainConnection(
        draft: AgentEditorDraft,
        existingProfile: RegisteredAgentProfile? = null
    ): String {
        val resolvedBrainFactoryId = existingProfile?.brainFactoryId ?: brainCatalog.defaultFactoryId
        require(brainCatalog.contains(resolvedBrainFactoryId)) {
            "Unsupported brain factory: $resolvedBrainFactoryId"
        }
        val metadata = (existingProfile?.metadata ?: emptyMap())
            .withAppAgentBrainProfileConfig(
                config = resolveBrainConfigForDraft(draft),
                includeApiKey = true
            )
        return brainConnectionTester.testConnection(
            factoryId = resolvedBrainFactoryId,
            metadata = metadata
        )
    }

    suspend fun testAgent(
        draft: AgentEditorDraft,
        existingProfile: RegisteredAgentProfile? = null,
        prompt: String = "你好，你是谁"
    ): String {
        require(draft.systemInstruction.trim().isNotBlank()) { "System instruction is required before testing the agent." }
        val provider = resolveProviderForDraft(draft)
        return provider.chat(
            systemPrompt = draft.systemInstruction.trim(),
            messages = listOf(ChatMessage(role = "user", content = prompt))
        ).trim()
    }

    suspend fun testContext(
        draft: AgentEditorDraft,
        existingProfile: RegisteredAgentProfile? = null
    ): String {
        val probeSuffix = System.currentTimeMillis().toString().takeLast(6)
        val knowledgeProbe = "TEST_KNOWLEDGE_$probeSuffix"
        val workingProbe = "TEST_WORKING_$probeSuffix"
        val episodicProbe = "TEST_EPISODIC_$probeSuffix"
        val updateProbe = "TEST_UPDATE_$probeSuffix"
        val tempAgentId = buildContextTestAgentId(draft.agentId.ifBlank { existingProfile?.agentId ?: "draft" })
        val registration = buildAgentRegistration(
            draft = draft,
            existingProfile = existingProfile,
            forcedAgentId = tempAgentId,
            allowExistingId = true
        )

        var runtimeId: String? = null
        var cleanupVerified = false
        try {
            service.registerAgent(registration)
            val createdProfile = service.getAgentProfile(tempAgentId)
            val profileCreated = createdProfile != null
            val invocation = service.invoke(
                AgentInvocationRequest(
                    agentId = tempAgentId,
                    inputs = listOf(
                        AgentInvocationInput(
                            role = AgentMessageRole.User,
                            content = buildContextTestPrompt(
                                knowledgeProbe = knowledgeProbe,
                                workingProbe = workingProbe,
                                episodicProbe = episodicProbe
                            )
                        )
                    ),
                    preloadMemory = listOf(
                        AgentMemorySeed(
                            scope = AgentMemoryScope.Working,
                            content = "Working probe: $workingProbe",
                            tags = setOf("context-test", "working")
                        ),
                        AgentMemorySeed(
                            scope = AgentMemoryScope.Episodic,
                            content = "Episodic probe: $episodicProbe",
                            tags = setOf("context-test", "episodic")
                        )
                    ),
                    preloadKnowledge = listOf(
                        AgentKnowledgeSeed(
                            content = "Knowledge probe: $knowledgeProbe",
                            tags = setOf("context-test", "knowledge"),
                            metadata = mapOf("source" to "agent_config_test")
                        )
                    ),
                    awaitCompletion = true
                )
            )
            runtimeId = invocation.invocationId
            val resolvedInvocation = service.getInvocation(invocation.invocationId) ?: invocation
            val invocationRecordCreated = service.getInvocation(invocation.invocationId) != null
            val runtimeRecordCreated = service.getAutonomousRuntime(invocation.invocationId) != null
            val response = resolvedInvocation.outputs.lastOrNull()
                ?: resolvedInvocation.finalSnapshot?.lastReply
                ?: ""
            val runtimeMemory = service.readRuntimeMemory(invocation.invocationId, limit = 20)
            val storedKnowledge = service.readAgentKnowledge(tempAgentId, limit = 20)

            val knowledgeStored = storedKnowledge.any { it.content.contains(knowledgeProbe) }
            val workingStored = runtimeMemory.any {
                it.scope == AgentMemoryScope.Working && it.content.contains(workingProbe)
            }
            val episodicStored = runtimeMemory.any {
                it.scope == AgentMemoryScope.Episodic && it.content.contains(episodicProbe)
            }
            val knowledgeSeen = response.contains(knowledgeProbe)
            val workingSeen = response.contains(workingProbe)
            val episodicSeen = response.contains(episodicProbe)

            val updatedRegistration = registration.copy(
                definition = registration.definition.copy(
                    systemInstruction = registration.definition.systemInstruction.trim() +
                        "\nContext CRUD update marker: $updateProbe"
                )
            )
            service.registerAgent(updatedRegistration)
            val updatedProfile = service.getAgentProfile(tempAgentId)
            val updateStored = updatedProfile?.definition?.systemInstruction?.contains(updateProbe) == true

            val cleanup = cleanupContextTestArtifacts(
                agentId = tempAgentId,
                runtimeId = invocation.invocationId
            )
            cleanupVerified = true

            val passed = profileCreated &&
                invocationRecordCreated &&
                runtimeRecordCreated &&
                knowledgeStored &&
                workingStored &&
                episodicStored &&
                (knowledgeSeen || workingSeen || episodicSeen) &&
                updateStored &&
                cleanup.passed

            return buildString {
                appendLine("Context CRUD test result: ${if (passed) "PASS" else "CHECK"}")
                appendLine("Create agent profile: ${if (profileCreated) "OK" else "MISS"}")
                appendLine("Create invocation record: ${if (invocationRecordCreated) "OK" else "MISS"}")
                appendLine("Create runtime record: ${if (runtimeRecordCreated) "OK" else "MISS"}")
                appendLine("Knowledge store read: ${if (knowledgeStored) "OK" else "MISS"}")
                appendLine("Working memory read: ${if (workingStored) "OK" else "MISS"}")
                appendLine("Episodic memory read: ${if (episodicStored) "OK" else "MISS"}")
                appendLine("Agent saw knowledge: ${if (knowledgeSeen) "YES" else "NO"}")
                appendLine("Agent saw working memory: ${if (workingSeen) "YES" else "NO"}")
                appendLine("Agent saw episodic memory: ${if (episodicSeen) "YES" else "NO"}")
                appendLine("Update agent profile: ${if (updateStored) "OK" else "MISS"}")
                appendLine("Cleanup knowledge: ${if (cleanup.knowledgeCleared) "OK" else "MISS"}")
                appendLine("Cleanup runtime memory: ${if (cleanup.runtimeMemoryCleared) "OK" else "MISS"}")
                appendLine("Cleanup structured memory: ${if (cleanup.structuredMemoryCleared) "OK" else "MISS"}")
                appendLine("Cleanup invocation record: ${if (cleanup.invocationDeleted) "OK" else "MISS"}")
                appendLine("Cleanup runtime record: ${if (cleanup.runtimeDeleted) "OK" else "MISS"}")
                appendLine("Cleanup agent profile: ${if (cleanup.profileDeleted) "OK" else "MISS"}")
                appendLine()
                appendLine("Probes:")
                appendLine("Knowledge=$knowledgeProbe")
                appendLine("Working=$workingProbe")
                appendLine("Episodic=$episodicProbe")
                appendLine("Update=$updateProbe")
                appendLine()
                appendLine("Agent reply:")
                append(response.ifBlank { "(empty)" })
            }.trim()
        } finally {
            if (!cleanupVerified) {
                runtimeId?.let { id ->
                    runCatching { service.clearRuntimeMemory(id) }
                    runCatching { service.clearStructuredMemory(id) }
                    runCatching { service.deleteInvocationRecord(id) }
                    runCatching { service.deleteAutonomousRuntimeRecord(id) }
                }
                runCatching { service.clearAgentKnowledge(tempAgentId) }
                runCatching { service.unregisterAgent(tempAgentId) }
            }
        }
    }

    suspend fun saveBrainProfile(draft: AgentEditorDraft): SavedBrainUiModel {
        val config = resolveBrainConfigForDraft(draft)
        require(config.endpoint.isNotBlank()) { "Brain endpoint is required before saving a brain." }
        require(config.apiKey.isNotBlank()) { "Brain API key is required before saving a brain." }
        val provider = config.toProviderEntity()
        llmWalletRepository.upsertProvider(provider, makeDefault = false)
        return provider.toSavedBrainUiModel()
    }

    suspend fun getSavedBrain(brainId: String): SavedBrainUiModel? {
        return llmWalletRepository.getProviderById(brainId)?.toSavedBrainUiModel()
    }

    companion object {
        private val AGENT_ID_PATTERN = Regex("[A-Za-z0-9._-]+")

        private fun parseTags(tagsText: String): Set<String> {
            return tagsText.split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }

        private fun parsePositiveInt(value: String, fallback: Int, fieldName: String): Int {
            val parsed = value.trim().ifBlank { fallback.toString() }.toIntOrNull()
            require(parsed != null && parsed > 0) { "$fieldName must be a positive integer." }
            return parsed
        }

        private fun parsePositiveLong(value: String, fallback: Long, fieldName: String): Long {
            val parsed = value.trim().ifBlank { fallback.toString() }.toLongOrNull()
            require(parsed != null && parsed > 0L) { "$fieldName must be a positive integer." }
            return parsed
        }
    }

    private suspend fun resolveBrainConfigForDraft(draft: AgentEditorDraft) =
        if (draft.selectedBrainId.isNotBlank()) {
            val savedProvider = llmWalletRepository.getProviderById(draft.selectedBrainId)
            require(savedProvider != null) { "Saved brain not found: ${draft.selectedBrainId}" }
            draft.copy(
                brainEndpoint = savedProvider.endpoint,
                brainApiKey = savedProvider.apiKey,
                brainModelName = savedProvider.modelName,
                brainDisplayName = savedProvider.name
            ).toAppAgentBrainProfileConfig()
        } else {
            draft.toAppAgentBrainProfileConfig()
        }

    private suspend fun materializeBrainConfigForDraft(
        draft: AgentEditorDraft
    ): com.example.watcher.agentframework.integration.AppAgentBrainProfileConfig {
        if (draft.selectedBrainId.isNotBlank()) {
            val savedProvider = llmWalletRepository.getProviderById(draft.selectedBrainId)
            require(savedProvider != null) { "Saved brain not found: ${draft.selectedBrainId}" }
            return com.example.watcher.agentframework.integration.AppAgentBrainProfileConfig(
                providerId = savedProvider.id,
                displayName = savedProvider.name,
                modelName = savedProvider.modelName
            )
        }

        val config = draft.toAppAgentBrainProfileConfig()
        if (!config.hasCustomConnection()) {
            return config
        }

        config.validate()
        val provider = config.toProviderEntity()
        llmWalletRepository.upsertProvider(provider, makeDefault = false)
        return com.example.watcher.agentframework.integration.AppAgentBrainProfileConfig(
            providerId = provider.id,
            displayName = provider.name,
            modelName = provider.modelName
        )
    }

    private suspend fun resolveProviderForDraft(draft: AgentEditorDraft): OpenAiCompatibleProvider {
        if (draft.selectedBrainId.isNotBlank()) {
            val savedProvider = llmWalletRepository.getProviderById(draft.selectedBrainId)
            require(savedProvider != null) { "Saved brain not found: ${draft.selectedBrainId}" }
            return savedProvider.toOpenAiProvider()
        }
        val config = draft.toAppAgentBrainProfileConfig()
        if (config.hasCustomConnection()) {
            config.validate()
            val fallbackModel = llmWalletRepository.resolvePreferredModel(ArkConfig.intentModel)
            val resolvedModel = config.modelName.ifBlank { fallbackModel }
            check(resolvedModel.isNotBlank()) { "Brain model is required when no default model is available." }
            return OpenAiCompatibleProvider(
                id = "agent_custom_provider",
                displayName = config.displayName.ifBlank { "Agent Custom Brain" },
                endpoint = config.endpoint.trim(),
                apiKey = config.apiKey.trim(),
                modelName = resolvedModel
            )
        }
        return llmWalletRepository.resolveOpenAiProvider(ArkConfig.intentModel)
    }

    private suspend fun com.example.watcher.agentframework.integration.AppAgentBrainProfileConfig.toProviderEntity(): LlmProviderEntity {
        val fallbackModel = llmWalletRepository.resolvePreferredModel(ArkConfig.intentModel)
        val resolvedModel = modelName.ifBlank { fallbackModel }
        val resolvedName = displayName.ifBlank {
            buildDefaultBrainName(endpoint = endpoint, modelName = resolvedModel)
        }
        val resolvedId = providerId.ifBlank {
            buildBrainId(resolvedName, endpoint, resolvedModel)
        }
        return LlmProviderEntity(
            id = resolvedId,
            name = resolvedName,
            endpoint = endpoint,
            apiKey = apiKey,
            modelName = resolvedModel,
            enabled = true
        )
    }

    private suspend fun buildAgentRegistration(
        draft: AgentEditorDraft,
        existingProfile: RegisteredAgentProfile?,
        forcedAgentId: String? = null,
        allowExistingId: Boolean = false
    ): AgentRegistration {
        val normalizedId = (forcedAgentId ?: draft.agentId).trim()
        require(normalizedId.isNotBlank()) { "Agent ID is required." }
        require(AGENT_ID_PATTERN.matches(normalizedId)) {
            "Agent ID may only contain letters, numbers, dot, underscore, and hyphen."
        }
        require(draft.name.trim().isNotBlank()) { "Agent name is required." }
        require(draft.goal.trim().isNotBlank()) { "Goal is required." }
        require(draft.systemInstruction.trim().isNotBlank()) { "System instruction is required." }

        if (!allowExistingId) {
            val targetExisting = service.getAgentProfile(normalizedId)
            require(targetExisting == null || targetExisting.agentId == existingProfile?.agentId) {
                "Agent ID already exists: $normalizedId"
            }
        }

        val resolvedBrainFactoryId = existingProfile?.brainFactoryId ?: brainCatalog.defaultFactoryId
        require(brainCatalog.contains(resolvedBrainFactoryId)) {
            "Unsupported brain factory: $resolvedBrainFactoryId"
        }
        val mergedMetadata = (existingProfile?.metadata ?: emptyMap())
            .withAppAgentBrainProfileConfig(materializeBrainConfigForDraft(draft))
        val configDefaults = existingProfile?.config ?: AgentRunConfig()

        return AgentRegistration(
            definition = AgentDefinition(
                agentId = normalizedId,
                name = draft.name.trim(),
                systemInstruction = draft.systemInstruction.trim(),
                goal = draft.goal.trim(),
                description = draft.description.trim(),
                metadata = existingProfile?.definition?.metadata ?: emptyMap()
            ),
            brainFactoryId = resolvedBrainFactoryId,
            config = AgentRunConfig(
                maxSteps = parsePositiveInt(draft.maxSteps, configDefaults.maxSteps, "maxSteps"),
                maxToolCallsPerStep = configDefaults.maxToolCallsPerStep,
                maxConsecutiveFailures = parsePositiveInt(
                    draft.maxConsecutiveFailures,
                    configDefaults.maxConsecutiveFailures,
                    "maxConsecutiveFailures"
                ),
                maxIdleTurns = parsePositiveInt(draft.maxIdleTurns, configDefaults.maxIdleTurns, "maxIdleTurns"),
                maxRuntimeMillis = parsePositiveLong(
                    draft.maxRuntimeMillis,
                    configDefaults.maxRuntimeMillis,
                    "maxRuntimeMillis"
                ),
                maxWaitMillis = configDefaults.maxWaitMillis,
                defaultWaitMillis = configDefaults.defaultWaitMillis,
                maxHistoryItems = configDefaults.maxHistoryItems,
                toolTimeoutMillis = parsePositiveLong(
                    draft.toolTimeoutMillis,
                    configDefaults.toolTimeoutMillis,
                    "toolTimeoutMillis"
                )
            ),
            tags = parseTags(draft.tagsText),
            metadata = mergedMetadata
        )
    }

    private fun buildContextTestAgentId(baseAgentId: String): String {
        val normalizedBase = baseAgentId
            .trim()
            .ifBlank { "agent" }
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "agent" }
            .take(32)
        return "ctx_test_${normalizedBase}_${System.currentTimeMillis()}"
    }

    private fun buildContextTestPrompt(
        knowledgeProbe: String,
        workingProbe: String,
        episodicProbe: String
    ): String {
        return """
            请严格回答三行，不要解释，不要补充：
            Knowledge=$knowledgeProbe 或 NONE
            WorkingMemory=$workingProbe 或 NONE
            EpisodicMemory=$episodicProbe 或 NONE
            你必须根据当前可见的 knowledge 和 memory 回答，禁止编造。
        """.trimIndent()
    }

    private suspend fun cleanupContextTestArtifacts(
        agentId: String,
        runtimeId: String?
    ): ContextCleanupResult {
        val knowledgeCleared = runCatching {
            service.clearAgentKnowledge(agentId)
            service.readAgentKnowledge(agentId, limit = 5).isEmpty()
        }.getOrDefault(false)

        val runtimeMemoryCleared = if (runtimeId == null) {
            true
        } else {
            runCatching {
                service.clearRuntimeMemory(runtimeId)
                service.readRuntimeMemory(runtimeId, limit = 5).isEmpty()
            }.getOrDefault(false)
        }

        val structuredMemoryCleared = if (runtimeId == null) {
            true
        } else {
            runCatching {
                service.clearStructuredMemory(runtimeId)
                service.readStructuredMemory(runtimeId).isEmpty()
            }.getOrDefault(false)
        }

        val invocationDeleted = if (runtimeId == null) {
            true
        } else {
            runCatching {
                service.deleteInvocationRecord(runtimeId)
                service.getInvocation(runtimeId) == null
            }.getOrDefault(false)
        }

        val runtimeDeleted = if (runtimeId == null) {
            true
        } else {
            runCatching {
                service.deleteAutonomousRuntimeRecord(runtimeId)
                service.getAutonomousRuntime(runtimeId) == null
            }.getOrDefault(false)
        }

        val profileDeleted = runCatching {
            service.unregisterAgent(agentId)
            service.getAgentProfile(agentId) == null
        }.getOrDefault(false)

        return ContextCleanupResult(
            knowledgeCleared = knowledgeCleared,
            runtimeMemoryCleared = runtimeMemoryCleared,
            structuredMemoryCleared = structuredMemoryCleared,
            invocationDeleted = invocationDeleted,
            runtimeDeleted = runtimeDeleted,
            profileDeleted = profileDeleted
        )
    }

    private fun buildDefaultBrainName(endpoint: String, modelName: String): String {
        val hostPart = endpoint
            .substringAfter("://", endpoint)
            .substringBefore('/')
            .ifBlank { "brain" }
        return "$hostPart / $modelName"
    }

    private fun buildBrainId(name: String, endpoint: String, modelName: String): String {
        val raw = "$name|$endpoint|$modelName"
        val normalized = raw.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "brain" }
        return "brain_$normalized"
    }

    private fun LlmProviderEntity.toOpenAiProvider(): OpenAiCompatibleProvider {
        return OpenAiCompatibleProvider(
            id = id,
            displayName = name,
            endpoint = endpoint,
            apiKey = apiKey,
            modelName = modelName
        )
    }
}

private data class ContextCleanupResult(
    val knowledgeCleared: Boolean,
    val runtimeMemoryCleared: Boolean,
    val structuredMemoryCleared: Boolean,
    val invocationDeleted: Boolean,
    val runtimeDeleted: Boolean,
    val profileDeleted: Boolean
) {
    val passed: Boolean
        get() = knowledgeCleared &&
            runtimeMemoryCleared &&
            structuredMemoryCleared &&
            invocationDeleted &&
            runtimeDeleted &&
            profileDeleted
}

private fun AgentKnowledgeEntry.toUiModel(title: String): AgentTextRecordUiModel {
    return AgentTextRecordUiModel(
        id = entryId,
        title = title,
        content = content,
        supporting = metadata.entries.joinToString(" | ") { "${it.key}=${it.value}" }.ifBlank { "Knowledge entry" },
        createdAt = createdAt,
        tags = tags.toList().sorted()
    )
}

private fun AgentMemoryEntry.toUiModel(title: String): AgentTextRecordUiModel {
    return AgentTextRecordUiModel(
        id = "${scope.name}_${createdAt}_${content.hashCode()}",
        title = title,
        content = content,
        supporting = scope.name,
        createdAt = createdAt,
        tags = tags.toList().sorted()
    )
}

private fun AutonomousAgentRuntimeRecord.toUiModel(): AgentRunUiModel {
    val title = snapshot?.definition?.name ?: agentId
    return AgentRunUiModel(
        runtimeId = runtimeId,
        title = title,
        lifecycleState = lifecycleState.name,
        stopReason = stopReason?.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        outputs = outputs.takeLast(5),
        errorMessage = errorMessage
    )
}

private fun AutonomousAgentEvent.toUiModel(index: Int): AgentActivityItemUiModel {
    return when (this) {
        is AutonomousAgentEvent.CycleCompleted -> AgentActivityItemUiModel(
            id = "cycle_${cycle}_$index",
            title = "Cycle $cycle",
            detail = "Validation: ${validationStatus.name}",
            timestamp = timestamp
        )

        is AutonomousAgentEvent.FailureRecorded -> AgentActivityItemUiModel(
            id = "failure_${cycle}_$index",
            title = "Failure",
            detail = message,
            timestamp = timestamp
        )

        is AutonomousAgentEvent.LifecycleChanged -> AgentActivityItemUiModel(
            id = "lifecycle_${state.name}_$index",
            title = "Lifecycle",
            detail = stopReason?.let { "${state.name} / ${it.name}" } ?: state.name,
            timestamp = timestamp
        )

        is AutonomousAgentEvent.OutputPublished -> AgentActivityItemUiModel(
            id = "output_$index",
            title = "Output",
            detail = output,
            timestamp = timestamp
        )
    }
}
