package com.example.watcher.data.repository

import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.data.model.LlmProviderEntity

data class HiddenWorkbenchApplyResult(
    val message: String,
    val changed: Boolean
)

class HiddenWorkbenchImportApplier(
    private val stagedImports: HiddenWorkbenchImportRepository,
    private val listProviders: suspend () -> List<LlmProviderEntity>,
    private val listAudiences: suspend () -> List<AiAudienceEntity>,
    private val saveAudience: suspend (AiAudienceEntity) -> Unit,
    private val listCouncilTemplates: suspend () -> List<CouncilTemplateEntity>,
    private val saveCouncilTemplate: suspend (CouncilTemplateEntity) -> Unit
) {
    suspend fun applyAudience(): HiddenWorkbenchApplyResult {
        val pending = stagedImports.loadPendingImports().audienceImport
            ?: return HiddenWorkbenchApplyResult(
                message = "当前没有可应用的 AI 观众导入。",
                changed = false
            )

        val providers = listProviders()
        val provider = resolveProvider(
            providers = providers,
            providerIdHint = pending.providerIdHint
        )
        val providerId = provider?.id ?: pending.providerIdHint.orEmpty()
        val shouldEnableAudience = pending.enabled && provider != null
        val existing = listAudiences().firstOrNull { it.name == pending.roomName }

        saveAudience(
            pending.toAudienceEntity(
                providerId = providerId,
                existing = existing,
                enabled = shouldEnableAudience
            )
        )
        stagedImports.clearAudienceImport()

        return HiddenWorkbenchApplyResult(
            message = when {
                provider == null -> {
                    "AI 观众「${pending.roomName}」已导入为停用草稿；请在 API 钱包补 Provider 后再启用。"
                }
                existing == null -> "AI 观众「${pending.roomName}」已导入。"
                else -> "AI 观众「${pending.roomName}」已更新。"
            },
            changed = true
        )
    }

    suspend fun applyCouncil(): HiddenWorkbenchApplyResult {
        val pending = stagedImports.loadPendingImports().councilImport
            ?: return HiddenWorkbenchApplyResult(
                message = "当前没有可应用的专家团导入。",
                changed = false
            )

        val existing = listCouncilTemplates().firstOrNull { it.label == pending.topic }
        saveCouncilTemplate(pending.toCouncilTemplateEntity(existing = existing))
        stagedImports.clearCouncilImport()

        return HiddenWorkbenchApplyResult(
            message = if (existing == null) {
                "智囊团模板「${pending.topic}」已导入。"
            } else {
                "智囊团模板「${pending.topic}」已更新。"
            },
            changed = true
        )
    }
}

private fun resolveProvider(
    providers: List<LlmProviderEntity>,
    providerIdHint: String?
): LlmProviderEntity? {
    return providerIdHint
        ?.let { providerId -> providers.firstOrNull { it.id == providerId } }
        ?: providers.firstOrNull { it.enabled }
        ?: providers.firstOrNull()
}
