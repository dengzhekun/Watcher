package com.example.watcher.data.repository.agent

import android.util.Log
import com.google.gson.Gson

internal class AgentAudienceResponseParser(
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "AgentAudienceParser"
        const val SILENCE_TOKEN = "[SILENCE]"
    }

    fun parseDecision(response: String, state: AgentRuntimeState): AgentDecision {
        val jsonBlock = extractJsonBlock(response)
        if (jsonBlock != null) {
            try {
                val payload = gson.fromJson(jsonBlock, AgentResponsePayload::class.java)
                return AgentDecision(
                    speak = payload.speak ?: false,
                    content = payload.content?.trim(),
                    emotion = payload.emotion?.takeIf { it.isNotBlank() } ?: state.emotion,
                    emotionIntensity = (payload.emotionIntensity ?: state.emotionIntensity).coerceIn(0, 100),
                    goal = payload.goal?.takeIf { it.isNotBlank() } ?: state.currentGoal,
                    focus = payload.focus?.takeIf { it.isNotBlank() } ?: state.focusTarget,
                    action = normalizeAction(payload.action),
                    memory = payload.memory?.trim().orEmpty(),
                    relationUpdates = payload.relationUpdates.orEmpty()
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse agent JSON, fallback to plain text", e)
            }
        }

        val lineCleanRegex = Regex("^(第[一二三1-3]行[：:]\\s*|[1-3][.、：:]\\s*)")
        val lines = response.lines()
            .map { it.trim().replace(lineCleanRegex, "") }
            .filter { it.isNotBlank() }
        val content = lines.firstOrNull()?.takeIf { !it.contains(SILENCE_TOKEN, ignoreCase = true) }
        val action = lines.getOrNull(2)?.trim().orEmpty()
        return AgentDecision(
            speak = content != null,
            content = content,
            emotion = lines.getOrNull(1)?.takeIf { it.length <= 12 } ?: state.emotion,
            emotionIntensity = state.emotionIntensity,
            goal = state.currentGoal,
            focus = state.focusTarget,
            action = normalizeAction(action),
            memory = "",
            relationUpdates = emptyList()
        )
    }

    private fun extractJsonBlock(response: String): String? {
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        return if (start >= 0 && end > start) response.substring(start, end + 1) else null
    }

    private fun normalizeAction(action: String?): String {
        val normalized = action?.trim().orEmpty()
        return when {
            normalized.isBlank() -> "none"
            normalized.equals("none", ignoreCase = true) -> "none"
            normalized.equals("like", ignoreCase = true) || normalized.contains("点赞") -> "like"
            normalized.startsWith("gift:", ignoreCase = true) -> normalized
            normalized.contains("小花") -> "gift:小花"
            normalized.contains("超级火箭") -> "gift:超级火箭"
            normalized.contains("火箭") -> "gift:火箭"
            normalized.contains("醒目留言") -> "gift:醒目留言"
            normalized.contains("皇冠") -> "gift:皇冠"
            else -> "none"
        }
    }
}
