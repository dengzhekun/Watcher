package com.example.watcher.data.repository.context

data class LiveSharedContextProfile(
    val recentMessagesLimit: Int,
    val recentSpeechLimit: Int,
    val speechWindowMs: Long,
    val recentVisualLimit: Int,
    val mentionLimit: Int
)

object LiveSharedContextProfiles {
    val AgentAudience = LiveSharedContextProfile(
        recentMessagesLimit = 10,
        recentSpeechLimit = 8,
        speechWindowMs = 2 * 60 * 1000L,
        recentVisualLimit = 3,
        mentionLimit = 8
    )

    val ClassicAudience = LiveSharedContextProfile(
        recentMessagesLimit = 10,
        recentSpeechLimit = 8,
        speechWindowMs = 2 * 60 * 1000L,
        recentVisualLimit = 1,
        mentionLimit = 8
    )

    val Council = LiveSharedContextProfile(
        recentMessagesLimit = 0,
        recentSpeechLimit = 10,
        speechWindowMs = 3 * 60 * 1000L,
        recentVisualLimit = 4,
        mentionLimit = 0
    )
}
