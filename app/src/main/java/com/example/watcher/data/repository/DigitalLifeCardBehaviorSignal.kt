package com.example.watcher.data.repository

internal fun extractDigitalLifeCardBehaviorSignal(raw: String): String {
    val lines = raw.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val filtered = lines.filter {
        it.startsWith("[USER]") || it.startsWith("[INTERACTION]") || it.startsWith("[TIME]")
    }
    return (filtered.ifEmpty { lines }).joinToString("\n")
}
