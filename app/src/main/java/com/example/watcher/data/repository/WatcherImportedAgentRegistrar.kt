package com.example.watcher.data.repository

import com.example.watcher.WatcherAgentConfigImport
import com.example.watcher.agentframework.core.AgentDefinition
import com.example.watcher.agentframework.core.AgentRunConfig
import com.example.watcher.agentframework.integration.AppAgentBrainProfileConfig
import com.example.watcher.agentframework.integration.withAppAgentBrainProfileConfig
import com.example.watcher.agentframework.service.AgentRegistration

object WatcherImportedAgentRegistrar {
    const val DEFAULT_IMPORTED_GOAL = "Safely execute the imported workflow and ask for clarification when requirements are ambiguous."

    private const val META_IMPORT_ENTRY_POINT = "import.entryPoint"
    private const val META_IMPORT_SOURCE_SITE_NAME = "import.sourceSiteName"
    private const val META_IMPORT_SOURCE_MODEL_MODE = "import.sourceModelMode"

    fun buildRegistration(
        agentConfig: WatcherAgentConfigImport,
        providerId: String,
        sourceSiteName: String = "",
        sourceModelMode: String = ""
    ): AgentRegistration {
        require(agentConfig.enabled) { "Imported agent config must be enabled before registration." }

        val normalizedAgentId = agentConfig.agentId.trim()
        val normalizedName = agentConfig.agentName.trim()
        val normalizedPrompt = agentConfig.systemPrompt.trim()
        val normalizedEntryPoint = agentConfig.entryPoint.trim()
        val normalizedProviderId = providerId.trim()
        val normalizedSourceSiteName = sourceSiteName.trim()
        val normalizedSourceModelMode = sourceModelMode.trim()

        require(normalizedAgentId.isNotBlank()) { "Imported agentId is required." }
        require(normalizedName.isNotBlank()) { "Imported agentName is required." }
        require(normalizedPrompt.isNotBlank()) { "Imported systemPrompt is required." }
        require(normalizedProviderId.isNotBlank()) { "Imported providerId is required." }

        val durableImportMetadata = buildMap {
            if (normalizedEntryPoint.isNotBlank()) put(META_IMPORT_ENTRY_POINT, normalizedEntryPoint)
            if (normalizedSourceSiteName.isNotBlank()) put(META_IMPORT_SOURCE_SITE_NAME, normalizedSourceSiteName)
            if (normalizedSourceModelMode.isNotBlank()) put(META_IMPORT_SOURCE_MODEL_MODE, normalizedSourceModelMode)
        }

        val registrationMetadata = durableImportMetadata.withAppAgentBrainProfileConfig(
            AppAgentBrainProfileConfig(providerId = normalizedProviderId)
        )

        return AgentRegistration(
            definition = AgentDefinition(
                agentId = normalizedAgentId,
                name = normalizedName,
                systemInstruction = normalizedPrompt,
                goal = DEFAULT_IMPORTED_GOAL,
                metadata = durableImportMetadata
            ),
            config = AgentRunConfig(),
            metadata = registrationMetadata
        )
    }
}
