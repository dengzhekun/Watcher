package com.example.watcher

import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.repository.requireSecureEndpoint
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class WatcherAgentConfigImport(
    val enabled: Boolean,
    val agentId: String,
    val agentName: String,
    val systemPrompt: String,
    val entryPoint: String
)

data class WatcherAudienceConfigImport(
    val enabled: Boolean,
    val roomName: String,
    val focusPrompt: String,
    val responseStyle: String
)

data class WatcherExpertCouncilConfigImport(
    val enabled: Boolean,
    val topic: String,
    val memberRoles: List<String>,
    val workflow: String
)

data class WatcherExternalImportRequest(
    val providerId: String,
    val providerName: String,
    val endpoint: String,
    val apiKey: String,
    val modelName: String,
    val enabled: Boolean = true,
    val makeDefault: Boolean = false,
    val allowInsecureTls: Boolean = false,
    val sourceSiteName: String = "",
    val sourceModelMode: String = ""
)

data class WatcherExternalImportPlan(
    val request: WatcherExternalImportRequest,
    val agentConfig: WatcherAgentConfigImport? = null,
    val audienceConfig: WatcherAudienceConfigImport? = null,
    val expertCouncilConfig: WatcherExpertCouncilConfigImport? = null,
    val ignoredSections: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class WatcherExternalImportSuccessResult(
    val status: String,
    val imported: List<String>,
    val ignored: List<String>,
    val warnings: List<String>,
    val message: String,
    val resultPayloadJson: String
)

data class WatcherProviderImportState(
    val providerId: String,
    val providerName: String,
    val endpoint: String,
    val modelName: String,
    val enabled: Boolean,
    val makeDefault: Boolean,
    val sourceSiteName: String,
    val sourceModelMode: String,
    val importedAt: Long
)

data class WatcherXmaxImportStatusSection(
    val title: String,
    val imported: Boolean,
    val enabled: Boolean,
    val summary: String,
    val source: String,
    val lastImportedAt: Long?,
    val nextStep: String,
    val detailLines: List<String> = emptyList(),
    val actionLabel: String? = null,
    val actionTarget: WatcherImportWorkspaceTarget? = null
)

enum class WatcherImportWorkspaceTarget {
    AgentConfig,
    HiddenWorkbench
}

data class WatcherXmaxImportStatus(
    val hasImportedPayload: Boolean = false,
    val providerId: String? = null,
    val providerFound: Boolean = false,
    val providerEnabled: Boolean = false,
    val providerIsDefault: Boolean = false,
    val sourceLabel: String = "尚未导入",
    val lastImportedAt: Long? = null,
    val sections: List<WatcherXmaxImportStatusSection> = emptyList(),
    val nextStepHint: String = "从 XMAX 发起一次导入后，这里会显示 Provider、Agent、AI 观众和专家团的接入状态。"
)

object WatcherExternalImportContract {
    const val ACTION_OPEN_WALLET = "com.xmax.watcher.action.OPEN_WALLET"
    const val ACTION_OPEN_WALLET_LEGACY = "com.example.watcher.action.OPEN_WALLET"
    const val EXTRA_IMPORT_PAYLOAD = "com.xmax.watcher.extra.IMPORT_PAYLOAD"
    const val EXTRA_IMPORT_PAYLOAD_LEGACY = "com.example.watcher.extra.IMPORT_PAYLOAD"
    const val EXTRA_RESULT_MESSAGE = "com.xmax.watcher.extra.RESULT_MESSAGE"
    const val EXTRA_RESULT_MESSAGE_LEGACY = "com.example.watcher.extra.RESULT_MESSAGE"
    const val EXTRA_GENERIC_IMPORT_PAYLOAD = "com.xmax.external.extra.IMPORT_PAYLOAD"
    const val EXTRA_GENERIC_RESULT_MESSAGE = "com.xmax.external.extra.RESULT_MESSAGE"
    const val EXTRA_GENERIC_RESULT_PAYLOAD = "com.xmax.external.extra.RESULT_PAYLOAD"
    const val IMPORT_STATE_PREFS = "watcher_external_import_state"
    const val IMPORT_STATE_PROVIDER = "provider_import_state_json"
    const val IMPORT_STATE_AGENT = "agent_config_json"
    const val IMPORT_STATE_AUDIENCE = "audience_config_json"
    const val IMPORT_STATE_EXPERT_COUNCIL = "expert_council_config_json"

    private val gson = Gson()

    fun parseImportPayload(raw: String): WatcherExternalImportPlan {
        val root = JsonParser.parseString(raw).asJsonObject
        val request = WatcherExternalImportRequest(
            providerId = root.requireString("providerId"),
            providerName = root.requireString("providerName"),
            endpoint = root.requireString("endpoint"),
            apiKey = root.requireString("apiKey"),
            modelName = root.requireString("modelName"),
            enabled = root.readBoolean("enabled", defaultValue = true),
            makeDefault = root.readBoolean("makeDefault", defaultValue = false),
            allowInsecureTls = root.readBoolean("allowInsecureTls", defaultValue = false),
            sourceSiteName = root.readOptionalString("sourceSiteName"),
            sourceModelMode = root.readOptionalString("sourceModelMode")
        )
        requireSecureEndpoint(request.endpoint, "Provider endpoint")

        val agentConfig = root.getAsJsonObjectOrNull("agentConfig")?.let(::parseAgentConfig)
        val audienceConfig = root.getAsJsonObjectOrNull("audienceConfig")?.let(::parseAudienceConfig)
        val expertCouncilConfig = root.getAsJsonObjectOrNull("expertCouncilConfig")?.let(::parseExpertCouncilConfig)
        val warnings = buildList {
            if (request.allowInsecureTls) {
                add("allowInsecureTls 已收到，但当前 Watcher 版本仍要求 https 端点，已忽略该设置。")
            }
            if (agentConfig != null) {
                add("agentConfig 已收到，已写入 Watcher 暂存位，待后续 Agent Runtime 接入。")
            }
            if (audienceConfig != null) {
                add("audienceConfig 已收到，已写入 Watcher 暂存位，待后续 AI 观众接入。")
            }
            if (expertCouncilConfig != null) {
                add("expertCouncilConfig 已收到，已写入 Watcher 暂存位，待后续专家团接入。")
            }
        }
        return WatcherExternalImportPlan(
            request = request,
            agentConfig = agentConfig,
            audienceConfig = audienceConfig,
            expertCouncilConfig = expertCouncilConfig,
            ignoredSections = emptyList(),
            warnings = warnings
        )
    }

    fun toProviderEntity(
        request: WatcherExternalImportRequest,
        existingCreatedAt: Long? = null,
        now: Long = System.currentTimeMillis()
    ): LlmProviderEntity {
        return LlmProviderEntity(
            id = request.providerId,
            name = request.providerName.trim(),
            endpoint = request.endpoint.trim(),
            apiKey = request.apiKey.trim(),
            modelName = request.modelName.trim(),
            enabled = request.enabled,
            createdAt = existingCreatedAt ?: now,
            updatedAt = now
        )
    }

    fun buildSuccessResult(plan: WatcherExternalImportPlan): WatcherExternalImportSuccessResult {
        val status = if (plan.warnings.isEmpty()) "ok" else "partial_success"
        val imported = buildList {
            add("provider")
            if (plan.agentConfig != null) add("agentConfig")
            if (plan.audienceConfig != null) add("audienceConfig")
            if (plan.expertCouncilConfig != null) add("expertCouncilConfig")
        }
        val resultPayload = linkedMapOf<String, Any>(
            "status" to status,
            "imported" to imported,
            "ignored" to plan.ignoredSections,
            "warnings" to plan.warnings
        )
        val displayName = plan.request.providerName.ifBlank {
            plan.request.sourceSiteName.ifBlank { plan.request.providerId }
        }
        val message = buildString {
            append("导入成功：已保存 ")
            append(displayName)
            if (plan.request.sourceModelMode.isNotBlank()) {
                append("（")
                append(plan.request.sourceModelMode)
                append("）")
            }
            if (plan.warnings.isNotEmpty()) {
                append("。")
                append(plan.warnings.joinToString(separator = "；"))
            }
        }
        return WatcherExternalImportSuccessResult(
            status = status,
            imported = imported,
            ignored = plan.ignoredSections,
            warnings = plan.warnings,
            message = message,
            resultPayloadJson = gson.toJson(resultPayload)
        )
    }

    fun buildProviderImportStateJson(
        plan: WatcherExternalImportPlan,
        importedAt: Long = System.currentTimeMillis()
    ): String {
        return gson.toJson(
            WatcherProviderImportState(
                providerId = plan.request.providerId,
                providerName = plan.request.providerName,
                endpoint = plan.request.endpoint,
                modelName = plan.request.modelName,
                enabled = plan.request.enabled,
                makeDefault = plan.request.makeDefault,
                sourceSiteName = plan.request.sourceSiteName,
                sourceModelMode = plan.request.sourceModelMode,
                importedAt = importedAt
            )
        )
    }

    fun buildImportStatus(
        providers: List<LlmProviderEntity>,
        defaultProviderId: String?,
        providerStateJson: String?,
        agentConfigJson: String?,
        audienceConfigJson: String?,
        expertCouncilConfigJson: String?
    ): WatcherXmaxImportStatus {
        val providerState = providerStateJson.decodeJsonOrNull<WatcherProviderImportState>()
        val agentConfig = agentConfigJson.decodeJsonOrNull<WatcherAgentConfigImport>()
        val audienceConfig = audienceConfigJson.decodeJsonOrNull<WatcherAudienceConfigImport>()
        val expertCouncilConfig = expertCouncilConfigJson.decodeJsonOrNull<WatcherExpertCouncilConfigImport>()
        val hasImportedPayload = providerState != null ||
            agentConfig != null ||
            audienceConfig != null ||
            expertCouncilConfig != null

        if (!hasImportedPayload) {
            return WatcherXmaxImportStatus()
        }

        val matchingProvider = providerState
            ?.let { state -> providers.firstOrNull { it.id == state.providerId } }
        val sourceLabel = providerState.sourceLabel()
        val lastImportedAt = providerState?.importedAt ?: matchingProvider?.updatedAt
        val sections = buildList {
            add(
                buildProviderStatusSection(
                    provider = matchingProvider,
                    providerState = providerState,
                    defaultProviderId = defaultProviderId,
                    sourceLabel = sourceLabel,
                    lastImportedAt = lastImportedAt
                )
            )
            add(
                agentConfig.toStatusSection(
                    title = "Agent",
                    summary = { it.agentName.ifBlank { it.agentId.ifBlank { "已暂存 Agent 配置" } } },
                    details = { it.toDetailLines() },
                    sourceLabel = sourceLabel,
                    lastImportedAt = lastImportedAt,
                    missingStep = "从 XMAX 重新导入 Agent 配置。",
                    importedStep = "下一步：打开 Agent 配置页，校对提示词并启用该 Agent。",
                    actionLabel = "打开 Agent 配置",
                    actionTarget = WatcherImportWorkspaceTarget.AgentConfig
                )
            )
            add(
                audienceConfig.toStatusSection(
                    title = "AI 观众",
                    summary = { it.roomName.ifBlank { "已暂存 AI 观众配置" } },
                    details = { it.toDetailLines() },
                    sourceLabel = sourceLabel,
                    lastImportedAt = lastImportedAt,
                    missingStep = "从 XMAX 重新导入 AI 观众配置。",
                    importedStep = "下一步：打开隐藏工作台，在 AI 观众里落这组配置。",
                    actionLabel = "打开隐藏工作台",
                    actionTarget = WatcherImportWorkspaceTarget.HiddenWorkbench
                )
            )
            add(
                expertCouncilConfig.toStatusSection(
                    title = "专家团",
                    summary = {
                        it.topic.ifBlank {
                            it.memberRoles.joinToString("、").ifBlank { "已暂存专家团配置" }
                        }
                    },
                    details = { it.toDetailLines() },
                    sourceLabel = sourceLabel,
                    lastImportedAt = lastImportedAt,
                    missingStep = "从 XMAX 重新导入专家团配置。",
                    importedStep = "下一步：打开隐藏工作台，在专家团里落专家角色与工作流。",
                    actionLabel = "打开隐藏工作台",
                    actionTarget = WatcherImportWorkspaceTarget.HiddenWorkbench
                )
            )
        }
        val nextStepHint = when {
            matchingProvider == null -> "Provider 已收到但未在钱包中找到，请从 XMAX 重新导入或手动补齐供应商。"
            !matchingProvider.enabled -> "Provider 当前已禁用；启用后再测试连通性。"
            matchingProvider.id == defaultProviderId -> "Provider 已作为默认钱包；下一步建议测试连通性。"
            else -> "Provider 已保存；下一步建议设为默认并测试连通性。"
        }

        return WatcherXmaxImportStatus(
            hasImportedPayload = true,
            providerId = providerState?.providerId,
            providerFound = matchingProvider != null,
            providerEnabled = matchingProvider?.enabled == true,
            providerIsDefault = matchingProvider != null && matchingProvider.id == defaultProviderId,
            sourceLabel = sourceLabel,
            lastImportedAt = lastImportedAt,
            sections = sections,
            nextStepHint = nextStepHint
        )
    }

    fun buildFailureMessage(error: Throwable): String {
        val detail = error.message?.trim().orEmpty().ifBlank { "未知错误" }
        return "导入失败：$detail"
    }

    private fun buildProviderStatusSection(
        provider: LlmProviderEntity?,
        providerState: WatcherProviderImportState?,
        defaultProviderId: String?,
        sourceLabel: String,
        lastImportedAt: Long?
    ): WatcherXmaxImportStatusSection {
        val imported = providerState != null || provider != null
        val enabled = provider?.enabled ?: providerState?.enabled ?: false
        val summary = when {
            provider != null -> "${provider.name} · ${provider.modelName}"
            providerState != null -> "${providerState.providerName} · ${providerState.modelName}"
            else -> "尚未导入 Provider"
        }
        val nextStep = when {
            provider == null -> "从 XMAX 重新导入或手动创建 Provider。"
            !provider.enabled -> "启用 Provider 后再测试连通性。"
            provider.id == defaultProviderId -> "在下方供应商卡片执行连通性测试。"
            else -> "设为默认后执行连通性测试。"
        }
        return WatcherXmaxImportStatusSection(
            title = "Provider",
            imported = imported,
            enabled = enabled,
            summary = summary,
            source = sourceLabel,
            lastImportedAt = lastImportedAt,
            nextStep = nextStep,
            detailLines = buildList {
                providerState?.providerId?.takeIf { it.isNotBlank() }?.let { add("Provider ID：$it") }
                providerState?.endpoint?.takeIf { it.isNotBlank() }?.let { add("接口：$it") }
                providerState?.modelName?.takeIf { it.isNotBlank() }?.let { add("模型：$it") }
            }
        )
    }

    private fun JsonObject.requireString(key: String): String {
        val value = readOptionalString(key)
        require(value.isNotBlank()) { "缺少 $key" }
        return value
    }

    private fun JsonObject.readOptionalString(key: String): String {
        val element = get(key) ?: return ""
        if (element.isJsonNull) return ""
        return element.asString?.trim().orEmpty()
    }

    private fun JsonObject.readBoolean(key: String, defaultValue: Boolean): Boolean {
        val element = get(key) ?: return defaultValue
        return runCatching { element.asBoolean }.getOrDefault(defaultValue)
    }

    private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonObject) return null
        return element.asJsonObject
    }

    private fun parseAgentConfig(json: JsonObject): WatcherAgentConfigImport {
        val enabled = json.readBoolean("enabled", defaultValue = true)
        val config = WatcherAgentConfigImport(
            enabled = enabled,
            agentId = json.readOptionalString("agentId"),
            agentName = json.readOptionalString("agentName"),
            systemPrompt = json.readOptionalString("systemPrompt"),
            entryPoint = json.readOptionalString("entryPoint")
        )
        require(!enabled || config.agentId.isNotBlank()) { "agentConfig 缺少 agentId" }
        require(!enabled || config.agentName.isNotBlank()) { "agentConfig 缺少 agentName" }
        require(!enabled || config.systemPrompt.isNotBlank()) { "agentConfig 缺少 systemPrompt" }
        return config
    }

    private fun parseAudienceConfig(json: JsonObject): WatcherAudienceConfigImport {
        val enabled = json.readBoolean("enabled", defaultValue = true)
        val config = WatcherAudienceConfigImport(
            enabled = enabled,
            roomName = json.readOptionalString("roomName"),
            focusPrompt = json.readOptionalString("focusPrompt"),
            responseStyle = json.readOptionalString("responseStyle")
        )
        require(!enabled || config.roomName.isNotBlank()) { "audienceConfig 缺少 roomName" }
        require(!enabled || config.focusPrompt.isNotBlank()) { "audienceConfig 缺少 focusPrompt" }
        return config
    }

    private fun parseExpertCouncilConfig(json: JsonObject): WatcherExpertCouncilConfigImport {
        val enabled = json.readBoolean("enabled", defaultValue = true)
        val roles = json.getAsJsonArray("memberRoles")
            ?.toStringList()
            .orEmpty()
        val config = WatcherExpertCouncilConfigImport(
            enabled = enabled,
            topic = json.readOptionalString("topic"),
            memberRoles = roles,
            workflow = json.readOptionalString("workflow")
        )
        require(!enabled || config.topic.isNotBlank()) { "expertCouncilConfig 缺少 topic" }
        require(!enabled || config.memberRoles.isNotEmpty()) { "expertCouncilConfig 缺少 memberRoles" }
        return config
    }

    private fun JsonArray.toStringList(): List<String> {
        return mapNotNull { element ->
            runCatching { element.asString.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    private inline fun <reified T> String?.decodeJsonOrNull(): T? {
        val raw = this?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { gson.fromJson(raw, T::class.java) }.getOrNull()
    }

    private fun WatcherProviderImportState?.sourceLabel(): String {
        val parts = listOfNotNull(
            this?.sourceSiteName?.takeIf { it.isNotBlank() },
            this?.sourceModelMode?.takeIf { it.isNotBlank() }
        )
        return parts.joinToString(" / ").ifBlank { "XMAX 外部导入" }
    }

    private fun <T> T?.toStatusSection(
        title: String,
        summary: (T) -> String,
        details: (T) -> List<String>,
        sourceLabel: String,
        lastImportedAt: Long?,
        missingStep: String,
        importedStep: String,
        actionLabel: String? = null,
        actionTarget: WatcherImportWorkspaceTarget? = null
    ): WatcherXmaxImportStatusSection where T : Any {
        val enabled = when (this) {
            is WatcherAgentConfigImport -> this.enabled
            is WatcherAudienceConfigImport -> this.enabled
            is WatcherExpertCouncilConfigImport -> this.enabled
            else -> false
        }
        return WatcherXmaxImportStatusSection(
            title = title,
            imported = this != null,
            enabled = this != null && enabled,
            summary = this?.let(summary) ?: "尚未导入 $title",
            source = sourceLabel,
            lastImportedAt = if (this != null) lastImportedAt else null,
            nextStep = if (this != null) importedStep else missingStep,
            detailLines = this?.let(details).orEmpty(),
            actionLabel = if (this != null) actionLabel else null,
            actionTarget = if (this != null) actionTarget else null
        )
    }

    private fun WatcherAgentConfigImport.toDetailLines(): List<String> {
        return buildList {
            agentId.takeIf { it.isNotBlank() }?.let { add("Agent ID：$it") }
            entryPoint.takeIf { it.isNotBlank() }?.let { add("入口：$it") }
            systemPrompt.takeIf { it.isNotBlank() }?.let { add("提示词：${it.previewLine()}") }
        }
    }

    private fun WatcherAudienceConfigImport.toDetailLines(): List<String> {
        return buildList {
            roomName.takeIf { it.isNotBlank() }?.let { add("房间：$it") }
            responseStyle.takeIf { it.isNotBlank() }?.let { add("风格：$it") }
            focusPrompt.takeIf { it.isNotBlank() }?.let { add("关注点：${it.previewLine()}") }
        }
    }

    private fun WatcherExpertCouncilConfigImport.toDetailLines(): List<String> {
        return buildList {
            topic.takeIf { it.isNotBlank() }?.let { add("主题：$it") }
            memberRoles.takeIf { it.isNotEmpty() }?.let { add("角色：${it.joinToString("、")}") }
            workflow.takeIf { it.isNotBlank() }?.let { add("流程：${it.previewLine()}") }
        }
    }

    private fun String.previewLine(maxLength: Int = 72): String {
        val normalized = replace('\n', ' ').trim()
        return if (normalized.length <= maxLength) {
            normalized
        } else {
            normalized.take(maxLength - 1) + "…"
        }
    }
}
