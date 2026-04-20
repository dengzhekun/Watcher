package com.example.watcher.data.local.litert

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Discovers the .litertlm model from known locations:
 * 1. App internal storage (filesDir/litert_models/)
 * 2. /data/local/tmp/ (ADB push destination)
 * 3. App assets (if bundled in APK)
 */
class LiteRtAssetInstaller(private val context: Context) {

    private val modelsDir = File(context.filesDir, "litert_models").apply { mkdirs() }

    /**
     * Finds the model from all known locations, or installs from assets if available.
     */
    suspend fun installBundledModelIfNeeded(): String? = withContext(Dispatchers.IO) {
        // 1. Check internal storage
        val internalFile = File(modelsDir, BUNDLED_MODEL_NAME)
        if (internalFile.exists() && internalFile.length() > 0) {
            Log.i(TAG, "Model found in internal storage: ${internalFile.absolutePath}")
            return@withContext internalFile.absolutePath
        }

        // 2. Check /data/local/tmp/ (ADB push location)
        val adbFile = File("/data/local/tmp/$BUNDLED_MODEL_NAME")
        if (adbFile.exists() && adbFile.length() > 0) {
            Log.i(TAG, "Model found at ADB location: ${adbFile.absolutePath}")
            return@withContext adbFile.absolutePath
        }

        // 3. Try assets (if model was bundled in APK)
        val assetList = runCatching {
            context.assets.list(ASSET_MODEL_DIR)
        }.getOrNull()
        if (assetList != null && BUNDLED_MODEL_NAME in assetList) {
            Log.i(TAG, "Installing bundled model from assets...")
            try {
                context.assets.open("$ASSET_MODEL_DIR/$BUNDLED_MODEL_NAME").use { input ->
                    internalFile.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 1024 * 1024)
                    }
                }
                Log.i(TAG, "Model installed: ${internalFile.absolutePath}")
                return@withContext internalFile.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to install bundled model", e)
                internalFile.delete()
            }
        }

        Log.i(TAG, "No model found in any location")
        null
    }

    companion object {
        private const val TAG = "LiteRtAssetInstaller"
        private const val ASSET_MODEL_DIR = "litert_models"
        const val BUNDLED_MODEL_NAME = "gemma-4-E2B-it.litertlm"
    }
}
