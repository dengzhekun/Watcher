package com.example.watcher.data.repository

internal fun normalizeBaseUrl(baseUrl: String): String {
    return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}
