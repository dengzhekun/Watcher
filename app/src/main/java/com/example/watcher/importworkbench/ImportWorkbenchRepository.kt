package com.example.watcher.importworkbench

import android.content.Context
import com.example.watcher.WatcherExternalImportPlan
import com.google.gson.Gson

class ImportWorkbenchRepository(
    private val storage: Storage
) {
    interface Storage {
        fun read(): String?
        fun write(raw: String?)
    }

    fun saveBatch(batch: ImportedResourceBatch) {
        storage.write(gson.toJson(batch))
    }

    fun loadBatch(): ImportedResourceBatch? {
        val raw = storage.read()?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { gson.fromJson(raw, ImportedResourceBatch::class.java) }.getOrNull()
    }

    fun loadCards(): List<WorkbenchEntryCard> {
        return loadBatch()?.let(ImportWorkbenchContract::toWorkbenchCards).orEmpty()
    }

    companion object {
        private const val PREFS_NAME = "import_workbench_state"
        private const val KEY_LATEST_BATCH = "latest_batch_json"
        private val gson = Gson()

        fun fromContext(context: Context): ImportWorkbenchRepository {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return ImportWorkbenchRepository(
                object : Storage {
                    override fun read(): String? = prefs.getString(KEY_LATEST_BATCH, null)

                    override fun write(raw: String?) {
                        prefs.edit().putString(KEY_LATEST_BATCH, raw).apply()
                    }
                }
            )
        }
    }
}

fun buildWatcherImportBatch(
    plan: WatcherExternalImportPlan,
    importedAt: Long,
    agentApplied: Boolean,
    agentFailureMessage: String?
): ImportedResourceBatch {
    val sourceLabel = listOfNotNull(
        plan.request.sourceSiteName.takeIf { it.isNotBlank() },
        plan.request.sourceModelMode.takeIf { it.isNotBlank() }
    ).joinToString(" / ").ifBlank { "XMAX 外部导入" }

    val resources = buildList {
        add(
            ImportedResourceInput(
                resourceType = "provider",
                resourceKey = plan.request.providerId,
                title = plan.request.providerName.ifBlank { plan.request.providerId },
                summary = plan.request.modelName,
                received = true,
                applied = true,
                requiresManualAction = false,
                failedMessage = null,
                destination = ImportActionTarget(
                    workspace = "wallet",
                    route = "api_wallet",
                    displayLabel = "打开 API 钱包"
                ),
                detailLines = listOfNotNull(
                    plan.request.endpoint.takeIf { it.isNotBlank() }?.let { "接口：$it" },
                    plan.request.modelName.takeIf { it.isNotBlank() }?.let { "模型：$it" }
                )
            )
        )

        plan.agentConfig?.let { agent ->
            val failureMessage = agentFailureMessage?.takeIf { it.isNotBlank() }
            val applied = agent.enabled && failureMessage == null && agentApplied
            add(
                ImportedResourceInput(
                    resourceType = "agent",
                    resourceKey = agent.agentId.ifBlank { "imported_agent" },
                    title = agent.agentName.ifBlank { "Imported Agent" },
                    summary = if (applied) {
                        "已注册到 Agent Runtime"
                    } else if (!agent.enabled) {
                        "已导入，默认停用"
                    } else if (failureMessage != null) {
                        "自动注册失败，需手动处理"
                    } else {
                        "已导入，待校对后启用"
                    },
                    received = true,
                    applied = applied,
                    requiresManualAction = !applied,
                    failedMessage = failureMessage,
                    destination = ImportActionTarget(
                        workspace = "agents",
                        route = "agent_config",
                        displayLabel = "打开 Agent 配置"
                    ),
                    detailLines = listOfNotNull(
                        agent.agentId.takeIf { it.isNotBlank() }?.let { "Agent ID：$it" },
                        agent.entryPoint.takeIf { it.isNotBlank() }?.let { "入口：$it" },
                        agent.systemPrompt.takeIf { it.isNotBlank() }?.let { "提示词：${it.previewLine()}" }
                    )
                )
            )
        }

        plan.audienceConfig?.let { audience ->
            add(
                ImportedResourceInput(
                    resourceType = "audience_group",
                    resourceKey = audience.roomName.ifBlank { "audience_group" },
                    title = audience.roomName.ifBlank { "AI 观众" },
                    summary = "需在工作台确认后落库",
                    received = true,
                    applied = false,
                    requiresManualAction = true,
                    failedMessage = null,
                    destination = ImportActionTarget(
                        workspace = "templates",
                        route = "template_management",
                        displayLabel = "打开隐藏工作台"
                    ),
                    detailLines = listOfNotNull(
                        audience.roomName.takeIf { it.isNotBlank() }?.let { "房间：$it" },
                        audience.responseStyle.takeIf { it.isNotBlank() }?.let { "风格：$it" },
                        audience.focusPrompt.takeIf { it.isNotBlank() }?.let { "关注点：${it.previewLine()}" }
                    )
                )
            )
        }

        plan.expertCouncilConfig?.let { council ->
            add(
                ImportedResourceInput(
                    resourceType = "expert_council",
                    resourceKey = council.topic.ifBlank { "expert_council" },
                    title = council.topic.ifBlank { "专家团" },
                    summary = "需在工作台确认后落库",
                    received = true,
                    applied = false,
                    requiresManualAction = true,
                    failedMessage = null,
                    destination = ImportActionTarget(
                        workspace = "templates",
                        route = "template_management",
                        displayLabel = "打开隐藏工作台"
                    ),
                    detailLines = listOfNotNull(
                        council.topic.takeIf { it.isNotBlank() }?.let { "主题：$it" },
                        council.memberRoles.takeIf { it.isNotEmpty() }?.let { "角色：${it.joinToString("、")}" },
                        council.workflow.takeIf { it.isNotBlank() }?.let { "流程：${it.previewLine()}" }
                    )
                )
            )
        }
    }

    return ImportedResourceBatch(
        sourceLabel = sourceLabel,
        importedAt = importedAt,
        resources = resources
    )
}

private fun String.previewLine(maxLength: Int = 72): String {
    val normalized = replace('\n', ' ').trim()
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        normalized.take(maxLength - 1) + "…"
    }
}
