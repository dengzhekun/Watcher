package com.example.watcher.agentframework.integration

import com.example.watcher.agentframework.service.RegisteredAgentProfile

private const val META_BRAIN_ENDPOINT = "brain.endpoint"
private const val META_BRAIN_API_KEY = "brain.apiKey"
private const val META_BRAIN_MODEL = "brain.modelName"
private const val META_BRAIN_DISPLAY_NAME = "brain.displayName"
private const val META_BRAIN_PROVIDER_ID = "brain.providerId"

data class AppAgentBrainProfileConfig(
    val providerId: String = "",
    val endpoint: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val displayName: String = ""
) {
    fun hasCustomConnection(): Boolean {
        return endpoint.isNotBlank() || apiKey.isNotBlank() || modelName.isNotBlank() || displayName.isNotBlank()
    }

    fun validate() {
        if (providerId.isNotBlank()) return
        if (!hasCustomConnection()) return
        require(endpoint.isNotBlank()) { "Brain endpoint is required when using a custom brain." }
        require(apiKey.isNotBlank()) { "Brain API key is required when using a custom brain." }
    }
}

fun RegisteredAgentProfile.readAppAgentBrainProfileConfig(): AppAgentBrainProfileConfig {
    return metadata.toAppAgentBrainProfileConfig()
}

fun Map<String, String>.toAppAgentBrainProfileConfig(): AppAgentBrainProfileConfig {
    return AppAgentBrainProfileConfig(
        providerId = this[META_BRAIN_PROVIDER_ID].orEmpty(),
        endpoint = this[META_BRAIN_ENDPOINT].orEmpty(),
        apiKey = this[META_BRAIN_API_KEY].orEmpty(),
        modelName = this[META_BRAIN_MODEL].orEmpty(),
        displayName = this[META_BRAIN_DISPLAY_NAME].orEmpty()
    )
}

fun Map<String, String>.withAppAgentBrainProfileConfig(
    config: AppAgentBrainProfileConfig,
    includeApiKey: Boolean = false
): Map<String, String> {
    config.validate()
    val updated = toMutableMap()
    updated.remove(META_BRAIN_PROVIDER_ID)
    updated.remove(META_BRAIN_ENDPOINT)
    updated.remove(META_BRAIN_API_KEY)
    updated.remove(META_BRAIN_MODEL)
    updated.remove(META_BRAIN_DISPLAY_NAME)

    if (config.providerId.isNotBlank()) {
        updated[META_BRAIN_PROVIDER_ID] = config.providerId.trim()
    }

    if (!config.hasCustomConnection()) {
        return updated
    }

    updated[META_BRAIN_ENDPOINT] = config.endpoint.trim()
    if (includeApiKey) {
        updated[META_BRAIN_API_KEY] = config.apiKey.trim()
    }
    config.modelName.trim().takeIf { it.isNotBlank() }?.let {
        updated[META_BRAIN_MODEL] = it
    }
    config.displayName.trim().takeIf { it.isNotBlank() }?.let {
        updated[META_BRAIN_DISPLAY_NAME] = it
    }
    return updated
}
