package com.example.watcher.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val APP_RUNTIME_SECRET_PREFS = "app_runtime_secrets"
private const val KEY_GATEWAY_API_KEY = "gateway_api_key"
private const val KEY_VOLCENGINE_ASR_APP_KEY = "volcengine_asr_app_key"
private const val KEY_VOLCENGINE_ASR_ACCESS_KEY = "volcengine_asr_access_key"
private const val KEY_VOLCENGINE_ASR_RESOURCE_ID = "volcengine_asr_resource_id"

private const val LEGACY_KEY_SPEECH_APP_ID = "speech_app_id"
private const val LEGACY_KEY_SPEECH_ACCESS_KEY_ID = "speech_access_key_id"

internal data class VolcengineAsrCredentials(
    val appKey: String = "",
    val accessKey: String = "",
    val resourceId: String = ""
) {
    fun isConfigured(): Boolean {
        return appKey.isNotBlank() &&
            accessKey.isNotBlank() &&
            resourceId.isNotBlank()
    }

    fun merge(fallback: VolcengineAsrCredentials): VolcengineAsrCredentials {
        return VolcengineAsrCredentials(
            appKey = appKey.ifBlank { fallback.appKey },
            accessKey = accessKey.ifBlank { fallback.accessKey },
            resourceId = resourceId.ifBlank { fallback.resourceId }
        )
    }
}

internal class AppRuntimeSecretStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            APP_RUNTIME_SECRET_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getGatewayApiKey(): String {
        return prefs.getString(KEY_GATEWAY_API_KEY, null).orEmpty()
    }

    fun putGatewayApiKey(apiKey: String) {
        prefs.edit()
            .putString(KEY_GATEWAY_API_KEY, apiKey)
            .apply()
    }

    fun readVolcengineAsrCredentials(
        fallback: VolcengineAsrCredentials = VolcengineAsrCredentials()
    ): VolcengineAsrCredentials {
        val stored = getStoredVolcengineAsrCredentials()
        if (stored.isConfigured()) {
            return stored
        }

        if (fallback.isConfigured()) {
            return fallback
        }

        return stored.merge(fallback)
    }

    fun putVolcengineAsrCredentials(credentials: VolcengineAsrCredentials) {
        prefs.edit()
            .putString(KEY_VOLCENGINE_ASR_APP_KEY, credentials.appKey)
            .putString(KEY_VOLCENGINE_ASR_ACCESS_KEY, credentials.accessKey)
            .putString(KEY_VOLCENGINE_ASR_RESOURCE_ID, credentials.resourceId)
            .remove(LEGACY_KEY_SPEECH_APP_ID)
            .remove(LEGACY_KEY_SPEECH_ACCESS_KEY_ID)
            .apply()
    }

    fun getStoredVolcengineAsrCredentials(): VolcengineAsrCredentials {
        return VolcengineAsrCredentials(
            appKey = prefs.getString(KEY_VOLCENGINE_ASR_APP_KEY, null).orEmpty(),
            accessKey = prefs.getString(KEY_VOLCENGINE_ASR_ACCESS_KEY, null).orEmpty(),
            resourceId = prefs.getString(KEY_VOLCENGINE_ASR_RESOURCE_ID, null).orEmpty()
        )
    }

    fun getLegacySpeechCredentials(): VolcengineAsrCredentials {
        return VolcengineAsrCredentials(
            appKey = prefs.getString(LEGACY_KEY_SPEECH_APP_ID, null).orEmpty(),
            accessKey = prefs.getString(LEGACY_KEY_SPEECH_ACCESS_KEY_ID, null).orEmpty(),
            resourceId = ""
        )
    }

    fun hasLegacySpeechCredentials(): Boolean {
        val legacy = getLegacySpeechCredentials()
        return legacy.appKey.isNotBlank() || legacy.accessKey.isNotBlank()
    }

    fun clearVolcengineAsrCredentials() {
        prefs.edit()
            .remove(KEY_VOLCENGINE_ASR_APP_KEY)
            .remove(KEY_VOLCENGINE_ASR_ACCESS_KEY)
            .remove(KEY_VOLCENGINE_ASR_RESOURCE_ID)
            .remove(LEGACY_KEY_SPEECH_APP_ID)
            .remove(LEGACY_KEY_SPEECH_ACCESS_KEY_ID)
            .apply()
    }
}
