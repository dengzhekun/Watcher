package com.example.watcher.data.local.litert

import android.content.Context
import java.io.File

enum class LiteRtModelSource {
    SavedConfig,
    InternalStorage,
    AdbPush
}

data class LiteRtDiscoveredModel(
    val file: File,
    val source: LiteRtModelSource
) {
    val path: String
        get() = file.absolutePath
}

object LiteRtModelFiles {
    const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    const val DEFAULT_DISPLAY_NAME = "Gemma 4 E2B"
    const val MIN_MODEL_SIZE_BYTES = 1_000_000_000L
}

class LiteRtModelLocator(context: Context) {

    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, "litert_models").apply { mkdirs() }

    fun modelsDir(): File = modelsDir

    fun internalModelFile(): File = File(modelsDir, LiteRtModelFiles.MODEL_FILENAME)

    fun adbModelFile(): File = File("/data/local/tmp/${LiteRtModelFiles.MODEL_FILENAME}")

    fun isUsableModelFile(file: File?): Boolean {
        return file != null &&
            file.exists() &&
            file.isFile &&
            file.length() >= LiteRtModelFiles.MIN_MODEL_SIZE_BYTES
    }

    fun resolveAvailableModel(preferredPath: String? = null): LiteRtDiscoveredModel? {
        val candidates = linkedSetOf<LiteRtDiscoveredModel>()

        preferredPath?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { candidates += LiteRtDiscoveredModel(File(it), LiteRtModelSource.SavedConfig) }

        candidates += LiteRtDiscoveredModel(internalModelFile(), LiteRtModelSource.InternalStorage)
        candidates += LiteRtDiscoveredModel(adbModelFile(), LiteRtModelSource.AdbPush)

        return candidates.firstOrNull { candidate -> isUsableModelFile(candidate.file) }
    }

    fun hasAvailableModel(preferredPath: String? = null): Boolean {
        return resolveAvailableModel(preferredPath) != null
    }

    fun resolveConfig(savedConfig: LiteRtModelConfig?): LiteRtModelConfig? {
        val discoveredModel = resolveAvailableModel(savedConfig?.modelPath) ?: return null
        return savedConfig?.copy(modelPath = discoveredModel.path) ?: defaultConfig(discoveredModel.path)
    }

    fun defaultConfig(modelPath: String): LiteRtModelConfig {
        return LiteRtModelConfig(
            modelPath = modelPath,
            displayName = LiteRtModelFiles.DEFAULT_DISPLAY_NAME,
            backend = LiteRtBackendType.GPU,
            visionBackend = LiteRtBackendType.GPU
        )
    }
}
