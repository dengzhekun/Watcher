package com.example.watcher.data.local.litert

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class DownloadProgress(
    val state: DownloadState = DownloadState.Idle,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

    val progressMB: String
        get() = "%.0f / %.0f MB".format(
            bytesDownloaded / 1_048_576.0,
            totalBytes / 1_048_576.0
        )
}

enum class DownloadState {
    Idle,
    Downloading,
    Completed,
    Failed
}

class LiteRtModelDownloader(private val context: Context) {

    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: Flow<DownloadProgress> = _progress.asStateFlow()

    private val modelLocator = LiteRtModelLocator(context)
    private val modelsDir = modelLocator.modelsDir()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No read timeout for large downloads
        .followRedirects(true)
        .build()

    fun getModelFile(): File = modelLocator.internalModelFile()

    fun isModelDownloaded(): Boolean {
        val file = getModelFile()
        return modelLocator.isUsableModelFile(file)
    }

    suspend fun downloadModel(): Result<String> = withContext(Dispatchers.IO) {
        val targetFile = getModelFile()
        val tempFile = File(modelsDir, "${LiteRtModelFiles.MODEL_FILENAME}.tmp")

        // Resume support: check existing temp file
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

        _progress.value = DownloadProgress(
            state = DownloadState.Downloading,
            bytesDownloaded = existingBytes
        )

        try {
            val requestBuilder = Request.Builder().url(DOWNLOAD_URL)
            if (existingBytes > 0) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
                Log.i(TAG, "Resuming download from byte $existingBytes")
            }

            val response = client.newCall(requestBuilder.build()).execute()

            response.use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    val error = "Download failed: HTTP ${resp.code}"
                    _progress.value = DownloadProgress(state = DownloadState.Failed, errorMessage = error)
                    return@withContext Result.failure(Exception(error))
                }

                val totalBytes = if (resp.code == 206) {
                    // Partial content - parse total from Content-Range header
                    val contentRange = resp.header("Content-Range")
                    contentRange?.substringAfter("/")?.toLongOrNull()
                        ?: (existingBytes + (resp.body?.contentLength() ?: 0))
                } else {
                    resp.body?.contentLength() ?: 0
                }

                _progress.value = _progress.value.copy(totalBytes = totalBytes)

                val body = resp.body ?: run {
                    _progress.value = DownloadProgress(state = DownloadState.Failed, errorMessage = "Empty response body")
                    return@withContext Result.failure(Exception("Empty response body"))
                }

                val outputStream = if (existingBytes > 0 && resp.code == 206) {
                    java.io.FileOutputStream(tempFile, true)
                } else {
                    tempFile.outputStream()
                }

                var downloadedSoFar = existingBytes
                val buffer = ByteArray(1024 * 1024) // 1MB buffer

                body.byteStream().use { input ->
                    outputStream.use { output ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSoFar += bytesRead
                            _progress.value = _progress.value.copy(bytesDownloaded = downloadedSoFar)
                        }
                    }
                }

                // Rename temp to final
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                _progress.value = DownloadProgress(
                    state = DownloadState.Completed,
                    bytesDownloaded = downloadedSoFar,
                    totalBytes = totalBytes
                )
                Log.i(TAG, "Model download completed: ${targetFile.absolutePath}")
                Result.success(targetFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _progress.value = DownloadProgress(
                state = DownloadState.Failed,
                bytesDownloaded = tempFile.length(),
                errorMessage = e.message ?: "Download failed"
            )
            Result.failure(e)
        }
    }

    fun resetProgress() {
        _progress.value = DownloadProgress()
    }

    companion object {
        private const val TAG = "LiteRtDownloader"
        private const val DOWNLOAD_URL =
            "https://hf-mirror.com/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    }
}
