package com.example.watcher.data.model

data class CouncilEntryDraft(
    val scene: String = "",
    val speakerRole: String = "",
    val targetRole: String = "",
    val userNeed: String = "",
    val concern: String = "",
    val background: String = "",
    val sourceTemplateId: String? = null
) {
    fun canGenerate(): Boolean {
        return listOf(scene, speakerRole, targetRole, userNeed, concern, background)
            .any { it.isNotBlank() }
    }
}

data class CouncilEntryGeneratedConfig(
    val title: String,
    val summary: String,
    val config: CouncilConfig,
    val suggestedExperts: List<String>,
    val promptPreview: String,
    val providerName: String,
    val generatedAt: Long = System.currentTimeMillis()
)

data class CouncilEntryUiState(
    val draft: CouncilEntryDraft = CouncilEntryDraft(),
    val generated: CouncilEntryGeneratedConfig? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)
