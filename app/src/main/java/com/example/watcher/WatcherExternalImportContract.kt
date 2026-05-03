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

    fun buildFailureMessage(error: Throwable): String {
        val detail = error.message?.trim().orEmpty().ifBlank { "未知错误" }
        return "导入失败：$detail"
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
}
