package com.example.watcher.data.local.litert

enum class LiteRtBackendType { CPU, GPU, NPU }

enum class LiteRtEngineState {
    NotConfigured,
    Idle,
    Initializing,
    Ready,
    Error,
    Closing
}

data class LiteRtModelConfig(
    val modelPath: String,
    val displayName: String = "",
    val backend: LiteRtBackendType = LiteRtBackendType.GPU,
    val visionBackend: LiteRtBackendType? = null,
    val audioBackend: LiteRtBackendType? = null,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f
)

data class LiteRtEngineStatus(
    val state: LiteRtEngineState,
    val modelConfig: LiteRtModelConfig? = null,
    val errorMessage: String? = null,
    val initDurationMs: Long? = null
)
