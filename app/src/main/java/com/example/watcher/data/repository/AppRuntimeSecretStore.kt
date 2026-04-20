package com.example.watcher.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val APP_RUNTIME_SECRET_PREFS = "app_runtime_secrets"
private const val KEY_GATEWAY_API_KEY = "gateway_api_key"
private const val KEY_SPEECH_APP_ID = "speech_app_id"
private const val KEY_SPEECH_ACCESS_KEY_ID = "speech_access_key_id"
private const val KEY_SPEECH_ACCESS_KEY_SECRET = "speech_access_key_secret"

internal data class SpeechCredentials(
    val appId: String = "",
    val accessKeyId: String = "",
    val accessKeySecret: String = ""
) {
    fun isConfigured(): Boolean {
        return appId.isNotBlank() &&
            accessKeyId.isNotBlank() &&
            accessKeySecret.isNotBlank()
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

    fun readSpeechCredentials(
        fallback: SpeechCredentials = SpeechCredentials()
    ): SpeechCredentials {
        val stored = SpeechCredentials(
            appId = prefs.getString(KEY_SPEECH_APP_ID, null).orEmpty(),
            accessKeyId = prefs.getString(KEY_SPEECH_ACCESS_KEY_ID, null).orEmpty(),
            accessKeySecret = prefs.getString(KEY_SPEECH_ACCESS_KEY_SECRET, null).orEmpty()
        )
        if (stored.isConfigured()) {
            return stored
        }

        if (fallback.isConfigured()) {
            putSpeechCredentials(fallback)
            return fallback
        }

        return stored
    }

    fun putSpeechCredentials(credentials: SpeechCredentials) {
        prefs.edit()
            .putString(KEY_SPEECH_APP_ID, credentials.appId)
            .putString(KEY_SPEECH_ACCESS_KEY_ID, credentials.accessKeyId)
            .putString(KEY_SPEECH_ACCESS_KEY_SECRET, credentials.accessKeySecret)
            .apply()
    }
}
