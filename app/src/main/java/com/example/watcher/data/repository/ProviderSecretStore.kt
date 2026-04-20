package com.example.watcher.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val WALLET_SECRET_PREFS = "llm_wallet_secrets"
private const val KEY_PROVIDER_SECRET_PREFIX = "provider_secret_"

class ProviderSecretStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            WALLET_SECRET_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getSecret(providerId: String): String {
        return prefs.getString(secretKey(providerId), null).orEmpty()
    }

    fun putSecret(providerId: String, secret: String) {
        prefs.edit()
            .putString(secretKey(providerId), secret)
            .apply()
    }

    fun removeSecret(providerId: String) {
        prefs.edit()
            .remove(secretKey(providerId))
            .apply()
    }

    private fun secretKey(providerId: String): String = "$KEY_PROVIDER_SECRET_PREFIX$providerId"
}
