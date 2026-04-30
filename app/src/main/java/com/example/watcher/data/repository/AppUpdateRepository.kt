package com.example.watcher.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

data class AppUpdatePrompt(
    val currentVersion: String,
    val latestVersion: String,
    val downloadPageUrl: String,
    val downloadUrl: String?,
    val updatedAt: String?
)

private data class AppUpdateMetadata(
    val version: String? = null,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val apkUrl: String? = null,
    val updatedAt: String? = null
)

internal fun isAppUpdateAvailable(
    currentVersion: String,
    currentVersionCode: Long,
    latestVersion: String,
    latestVersionCode: Long?
): Boolean {
    if (latestVersionCode != null) {
        return latestVersionCode > currentVersionCode
    }

    val currentTokens = extractVersionTokensForUpdate(currentVersion)
    val latestTokens = extractVersionTokensForUpdate(latestVersion)

    if (currentTokens.isNotEmpty() && latestTokens.isNotEmpty()) {
        return compareVersionTokensForUpdate(latestTokens, currentTokens) > 0
    }

    return false
}

internal fun extractVersionTokensForUpdate(value: String): List<Int> {
    return VERSION_NUMBER_REGEX_FOR_UPDATE.findAll(value)
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
}

internal fun compareVersionTokensForUpdate(left: List<Int>, right: List<Int>): Int {
    val maxSize = maxOf(left.size, right.size)
    repeat(maxSize) { index ->
        val leftValue = left.getOrElse(index) { 0 }
        val rightValue = right.getOrElse(index) { 0 }
        if (leftValue != rightValue) {
            return leftValue.compareTo(rightValue)
        }
    }
    return 0
}

class AppUpdateRepository(
    private val context: Context,
    private val gson: Gson = Gson()
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun checkForUpdate(): Result<AppUpdatePrompt?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(METADATA_URL)
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Update metadata request failed: HTTP ${response.code}")
                }

                val body = response.body?.string().orEmpty()
                val metadata = gson.fromJson(body, AppUpdateMetadata::class.java)
                val latestVersionCode = metadata.versionCode
                val latestVersion = metadata.version
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: metadata.versionName?.trim().orEmpty()

                if (latestVersion.isBlank() && latestVersionCode == null) {
                    return@use null
                }

                val currentVersion = currentVersionName()
                val updateAvailable = isUpdateAvailable(
                    currentVersion = currentVersion,
                    currentVersionCode = currentVersionCode(),
                    latestVersion = latestVersion,
                    latestVersionCode = latestVersionCode
                )

                if (!updateAvailable) {
                    return@use null
                }

                AppUpdatePrompt(
                    currentVersion = currentVersion,
                    latestVersion = normalizeVersionLabel(
                        latestVersion.ifBlank { latestVersionCode.toString() }
                    ),
                    downloadPageUrl = DOWNLOAD_PAGE_URL,
                    downloadUrl = resolveAbsoluteUrl(metadata.apkUrl),
                    updatedAt = metadata.updatedAt
                )
            }
        }
    }

    private fun isUpdateAvailable(
        currentVersion: String,
        currentVersionCode: Long,
        latestVersion: String,
        latestVersionCode: Long?
    ): Boolean {
        return isAppUpdateAvailable(
            currentVersion = currentVersion,
            currentVersionCode = currentVersionCode,
            latestVersion = latestVersion,
            latestVersionCode = latestVersionCode
        )
    }

    private fun currentVersionName(): String {
        return packageInfo().versionName?.trim().takeUnless { it.isNullOrBlank() } ?: "0"
    }

    private fun currentVersionCode(): Long {
        val packageInfo = packageInfo()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun packageInfo() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private fun resolveAbsoluteUrl(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return null
        }
        return runCatching { URI(DOWNLOAD_PAGE_URL).resolve(trimmed).toString() }
            .getOrDefault(trimmed)
    }

    private fun normalizeVersionLabel(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith("v", ignoreCase = true)) trimmed else "v$trimmed"
    }

    private companion object {
        const val DOWNLOAD_PAGE_URL = "http://shokz-watcher.cn/download.html"
        const val METADATA_URL = "http://shokz-watcher.cn/download/app/latest.json"
    }
}

private val VERSION_NUMBER_REGEX_FOR_UPDATE = Regex("\\d+")
