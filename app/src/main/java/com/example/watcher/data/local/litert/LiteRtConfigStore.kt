package com.example.watcher.data.local.litert

import android.content.Context

class LiteRtConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "litert_config", Context.MODE_PRIVATE
    )

    fun saveConfig(config: LiteRtModelConfig) {
        prefs.edit()
            .putString(KEY_MODEL_PATH, config.modelPath)
            .putString(KEY_DISPLAY_NAME, config.displayName)
            .putString(KEY_BACKEND, config.backend.name)
            .putString(KEY_VISION_BACKEND, config.visionBackend?.name ?: "")
            .putString(KEY_AUDIO_BACKEND, config.audioBackend?.name ?: "")
            .putInt(KEY_MAX_TOKENS, config.maxTokens)
            .putFloat(KEY_TEMPERATURE, config.temperature)
            .putInt(KEY_TOP_K, config.topK)
            .putFloat(KEY_TOP_P, config.topP)
            .apply()
    }

    fun loadConfig(): LiteRtModelConfig? {
        val modelPath = prefs.getString(KEY_MODEL_PATH, null) ?: return null
        return LiteRtModelConfig(
            modelPath = modelPath,
            displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: "",
            backend = runCatching {
                LiteRtBackendType.valueOf(prefs.getString(KEY_BACKEND, "GPU")!!)
            }.getOrDefault(LiteRtBackendType.GPU),
            visionBackend = prefs.getString(KEY_VISION_BACKEND, "")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LiteRtBackendType.valueOf(it) }.getOrNull() },
            audioBackend = prefs.getString(KEY_AUDIO_BACKEND, "")
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { LiteRtBackendType.valueOf(it) }.getOrNull() },
            maxTokens = prefs.getInt(KEY_MAX_TOKENS, 1024),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            topK = prefs.getInt(KEY_TOP_K, 40),
            topP = prefs.getFloat(KEY_TOP_P, 0.95f)
        )
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_BACKEND = "backend"
        const val KEY_VISION_BACKEND = "vision_backend"
        const val KEY_AUDIO_BACKEND = "audio_backend"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_TOP_K = "top_k"
        const val KEY_TOP_P = "top_p"
    }
}
