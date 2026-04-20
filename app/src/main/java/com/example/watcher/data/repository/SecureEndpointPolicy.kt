package com.example.watcher.data.repository

internal fun validateSecureEndpoint(
    endpoint: String,
    label: String
): String? {
    val normalized = endpoint.trim()
    if (normalized.isBlank()) {
        return "$label is required."
    }
    if (!normalized.startsWith("https://", ignoreCase = true)) {
        return "$label must use https:// to protect API keys in transit."
    }
    return null
}

internal fun requireSecureEndpoint(
    endpoint: String,
    label: String
) {
    validateSecureEndpoint(endpoint, label)?.let { message ->
        throw IllegalArgumentException(message)
    }
}
