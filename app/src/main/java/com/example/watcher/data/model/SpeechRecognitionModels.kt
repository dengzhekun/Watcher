package com.example.watcher.data.model

data class SpeechTranscriptEntry(
    val id: Long,
    val text: String,
    val timestamp: Long,
    val displayTimestamp: String,
    val isFinal: Boolean
)

data class LiveSpeechState(
    val isActive: Boolean = false,
    val isListening: Boolean = false,
    val isMicEnabled: Boolean = true,
    val transcripts: List<SpeechTranscriptEntry> = emptyList(),
    val errorMessage: String? = null
)
