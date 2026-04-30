package com.example.watcher.data.local.litert

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Discovers the .litertlm model from known locations:
 * 1. App internal storage (filesDir/litert_models/)
 * 2. /data/local/tmp/ (ADB push destination)
 * 3. App assets (if bundled in APK)
 */
class LiteRtAssetInstaller(private val context: Context) {

    private val modelLocator = LiteRtModelLocator(context)

    /**
     * Finds the model from all known locations, or installs from assets if available.
     */
    suspend fun installBundledModelIfNeeded(): String? = withContext(Dispatchers.IO) {
        // 1. Check internal storage
        val internalFile = modelLocator.internalModelFile()
        if (modelLocator.isUsableModelFile(internalFile)) {
            Log.i(TAG, "Model found in internal storage: ${internalFile.absolutePath}")
            return@withContext internalFile.absolutePath
        }

        // 2. Check /data/local/tmp/ (ADB push location)
        val adbFile = modelLocator.adbModelFile()
        if (modelLocator.isUsableModelFile(adbFile)) {
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
                if (modelLocator.isUsableModelFile(internalFile)) {
                    Log.i(TAG, "Model installed: ${internalFile.absolutePath}")
                    return@withContext internalFile.absolutePath
                }
                Log.w(TAG, "Bundled model copy finished but file validation failed")
                internalFile.delete()
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
        const val BUNDLED_MODEL_NAME = LiteRtModelFiles.MODEL_FILENAME
    }
}
