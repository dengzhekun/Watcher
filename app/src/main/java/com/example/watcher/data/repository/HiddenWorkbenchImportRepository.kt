package com.example.watcher.data.repository

import android.content.Context
import com.example.watcher.WatcherAudienceConfigImport
import com.example.watcher.WatcherExpertCouncilConfigImport
import com.example.watcher.WatcherExternalImportContract
import com.example.watcher.WatcherProviderImportState
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AudienceEngineType
import com.example.watcher.data.model.CouncilTemplateEntity
import com.google.gson.Gson

data class HiddenWorkbenchPendingImports(
    val sourceLabel: String = "XMAX 外部导入",
    val providerIdHint: String? = null,
    val audienceImport: PendingAudienceImport? = null,
    val councilImport: PendingCouncilImport? = null
)

data class PendingAudienceImport(
    val roomName: String,
    val focusPrompt: String,
    val responseStyle: String,
    val enabled: Boolean,
    val sourceLabel: String,
    val providerIdHint: String?
)

data class PendingCouncilImport(
    val topic: String,
    val memberRoles: List<String>,
    val workflow: String,
    val enabled: Boolean,
    val sourceLabel: String
)

class HiddenWorkbenchImportRepository(
    private val storage: Storage
) {
    interface Storage {
        fun readProviderState(): String?
        fun readAudienceConfig(): String?
        fun readExpertCouncilConfig(): String?
        fun clearAudienceConfig()
        fun clearExpertCouncilConfig()
    }

    fun loadPendingImports(): HiddenWorkbenchPendingImports {
        val providerState = storage.readProviderState().decodeJsonOrNull<WatcherProviderImportState>()
        val audienceConfig = storage.readAudienceConfig().decodeJsonOrNull<WatcherAudienceConfigImport>()
        val councilConfig = storage.readExpertCouncilConfig().decodeJsonOrNull<WatcherExpertCouncilConfigImport>()
        val sourceLabel = buildSourceLabel(providerState)
        return HiddenWorkbenchPendingImports(
            sourceLabel = sourceLabel,
            providerIdHint = providerState?.providerId?.takeIf { it.isNotBlank() },
            audienceImport = audienceConfig?.let {
                PendingAudienceImport(
                    roomName = it.roomName.trim(),
                    focusPrompt = it.focusPrompt.trim(),
                    responseStyle = it.responseStyle.trim(),
                    enabled = it.enabled,
                    sourceLabel = sourceLabel,
                    providerIdHint = providerState?.providerId?.takeIf { value -> value.isNotBlank() }
                )
            },
            councilImport = councilConfig?.let {
                PendingCouncilImport(
                    topic = it.topic.trim(),
                    memberRoles = it.memberRoles.map(String::trim).filter(String::isNotBlank),
                    workflow = it.workflow.trim(),
                    enabled = it.enabled,
                    sourceLabel = sourceLabel
                )
            }
        )
    }

    companion object {
        fun fromContext(context: Context): HiddenWorkbenchImportRepository {
            val prefs = context.getSharedPreferences(
                WatcherExternalImportContract.IMPORT_STATE_PREFS,
                Context.MODE_PRIVATE
            )
            return HiddenWorkbenchImportRepository(
                storage = object : Storage {
                    override fun readProviderState(): String? {
                        return prefs.getString(WatcherExternalImportContract.IMPORT_STATE_PROVIDER, null)
                    }

                    override fun readAudienceConfig(): String? {
                        return prefs.getString(WatcherExternalImportContract.IMPORT_STATE_AUDIENCE, null)
                    }

                    override fun readExpertCouncilConfig(): String? {
                        return prefs.getString(
                            WatcherExternalImportContract.IMPORT_STATE_EXPERT_COUNCIL,
                            null
                        )
                    }

                    override fun clearAudienceConfig() {
                        prefs.edit()
                            .remove(WatcherExternalImportContract.IMPORT_STATE_AUDIENCE)
                            .apply()
                    }

                    override fun clearExpertCouncilConfig() {
                        prefs.edit()
                            .remove(WatcherExternalImportContract.IMPORT_STATE_EXPERT_COUNCIL)
                            .apply()
                    }
                }
            )
        }
    }

    fun clearAudienceImport() {
        storage.clearAudienceConfig()
    }

    fun clearCouncilImport() {
        storage.clearExpertCouncilConfig()
    }
}

fun PendingAudienceImport.toAudienceEntity(
    providerId: String,
    existing: AiAudienceEntity? = null,
    now: Long = System.currentTimeMillis(),
    enabled: Boolean = this.enabled
): AiAudienceEntity {
    val normalizedProviderId = providerId.trim()
    return (existing ?: AiAudienceEntity(
        name = roomName,
        audienceType = AudienceEngineType.Agent,
        persona = focusPrompt,
        providerId = normalizedProviderId,
        enabled = enabled,
        createdAt = now,
        updatedAt = now
    )).copy(
        name = roomName,
        audienceType = AudienceEngineType.Agent,
        persona = focusPrompt,
        speakingStyle = responseStyle,
        providerId = normalizedProviderId,
        enabled = enabled,
        updatedAt = now
    )
}

fun PendingCouncilImport.toCouncilTemplateEntity(
    existing: CouncilTemplateEntity? = null,
    now: Long = System.currentTimeMillis()
): CouncilTemplateEntity {
    return (existing ?: CouncilTemplateEntity(
        templateId = buildImportedCouncilTemplateId(topic),
        label = topic,
        description = "",
        sceneType = "General",
        objective = "",
        focus = "",
        background = "",
        isDefault = false,
        updatedAt = now
    )).copy(
        label = topic,
        description = "来自$sourceLabel 的专家团导入草稿。",
        sceneType = "General",
        objective = workflow,
        focus = memberRoles.joinToString("、"),
        background = "导入来源：$sourceLabel",
        isDefault = false,
        updatedAt = now
    )
}

private fun buildSourceLabel(providerState: WatcherProviderImportState?): String {
    val parts = listOfNotNull(
        providerState?.sourceSiteName?.takeIf { it.isNotBlank() },
        providerState?.sourceModelMode?.takeIf { it.isNotBlank() }
    )
    return parts.joinToString(" / ").ifBlank { "XMAX 外部导入" }
}

private fun buildImportedCouncilTemplateId(topic: String): String {
    val normalized = topic
        .lowercase()
        .map { char ->
            when {
                char.isLetterOrDigit() -> char
                else -> '_'
            }
        }
        .joinToString("")
        .trim('_')
        .ifBlank { "template" }
    return "imported_council_$normalized"
}

private inline fun <reified T> String?.decodeJsonOrNull(): T? {
    val raw = this?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { Gson().fromJson(raw, T::class.java) }.getOrNull()
}
