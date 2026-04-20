package com.example.watcher.data.repository

import android.content.Context
import com.example.watcher.data.local.LlmProviderDao
import com.example.watcher.data.model.LlmProviderEntity
import com.example.watcher.data.remote.OpenAiCompatibleProvider
import com.example.watcher.data.remote.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val WALLET_PREFS = "llm_wallet_prefs"
private const val KEY_DEFAULT_PROVIDER_ID = "default_provider_id"
private const val KEY_TEST_STATUS_PREFIX = "provider_test_status_"
private const val KEY_TEST_TIME_PREFIX = "provider_test_time_"
private const val KEY_TEST_MESSAGE_PREFIX = "provider_test_message_"

enum class LlmWalletSource {
    SavedProvider,
    ArkFallback
}

enum class ProviderConnectivityStatus {
    Untested,
    Verified,
    Failed
}

data class ProviderConnectivitySnapshot(
    val status: ProviderConnectivityStatus = ProviderConnectivityStatus.Untested,
    val lastTestedAt: Long? = null,
    val message: String? = null
)

data class LlmWalletResolvedConfig(
    val providerId: String?,
    val displayName: String,
    val endpoint: String,
    val apiKey: String,
    val modelName: String,
    val source: LlmWalletSource
) {
    fun bearerToken(): String = "Bearer $apiKey"

    fun toOpenAiProvider(): OpenAiCompatibleProvider {
        return OpenAiCompatibleProvider(
            id = providerId ?: "wallet_default_provider",
            displayName = displayName,
            endpoint = endpoint,
            apiKey = apiKey,
            modelName = modelName
        )
    }
}

class LlmWalletRepository(
    context: Context,
    private val providerDao: LlmProviderDao
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(WALLET_PREFS, Context.MODE_PRIVATE)
    private val secretStore = ProviderSecretStore(appContext)
    private val migrationMutex = Mutex()
    private var secretsMigrated = false

    fun getDefaultProviderId(): String? {
        return prefs.getString(KEY_DEFAULT_PROVIDER_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun setDefaultProviderId(providerId: String?) {
        prefs.edit().apply {
            if (providerId.isNullOrBlank()) {
                remove(KEY_DEFAULT_PROVIDER_ID)
            } else {
                putString(KEY_DEFAULT_PROVIDER_ID, providerId)
            }
        }.apply()
    }

    fun observeProviders(): Flow<List<LlmProviderEntity>> = flow {
        ensureSecretsMigrated()
        emitAll(
            providerDao.observeAll().map { providers ->
                providers.map(::withResolvedSecret)
            }
        )
    }

    suspend fun listProviders(): List<LlmProviderEntity> {
        ensureSecretsMigrated()
        return providerDao.getAll().map(::withResolvedSecret)
    }

    suspend fun listEnabledProviders(): List<LlmProviderEntity> {
        ensureSecretsMigrated()
        return providerDao.getEnabled().map(::withResolvedSecret)
    }

    suspend fun upsertProvider(
        provider: LlmProviderEntity,
        makeDefault: Boolean = false
    ) {
        ensureSecretsMigrated()
        secretStore.putSecret(provider.id, provider.apiKey.trim())
        providerDao.upsert(provider.copy(apiKey = ""))
        val currentDefaultId = getDefaultProviderId()
        if (makeDefault || currentDefaultId.isNullOrBlank()) {
            setDefaultProviderId(provider.id)
        }
    }

    suspend fun deleteProvider(id: String) {
        ensureSecretsMigrated()
        providerDao.deleteById(id)
        secretStore.removeSecret(id)
        clearProviderConnectivitySnapshot(id)
        if (getDefaultProviderId() == id) {
            setDefaultProviderId(listEnabledProviders().firstOrNull()?.id)
        }
    }

    suspend fun getProviderById(id: String): LlmProviderEntity? {
        ensureSecretsMigrated()
        return providerDao.getById(id)?.let(::withResolvedSecret)
    }

    suspend fun resolveOpenAiConfig(
        fallbackModel: String
    ): LlmWalletResolvedConfig {
        val savedProvider = resolveSelectedProvider()
        if (savedProvider != null) {
            return savedProvider.toResolvedConfig()
        }

        require(ArkConfig.apiKey.isNotBlank()) {
            "No global LLM provider configured and API_KEY is empty."
        }
        return LlmWalletResolvedConfig(
            providerId = null,
            displayName = "Ark Default",
            endpoint = "${RetrofitClient.BASE_URL}api/v3",
            apiKey = ArkConfig.apiKey,
            modelName = fallbackModel,
            source = LlmWalletSource.ArkFallback
        )
    }

    suspend fun resolveArkResponsesConfig(
        fallbackModel: String
    ): LlmWalletResolvedConfig {
        val savedProvider = resolveSelectedProvider()
        if (savedProvider != null && savedProvider.isArkResponsesCompatible()) {
            return savedProvider.toResolvedConfig()
        }

        require(ArkConfig.apiKey.isNotBlank()) {
            "No Ark-compatible provider configured and API_KEY is empty."
        }
        return LlmWalletResolvedConfig(
            providerId = null,
            displayName = "Ark Default",
            endpoint = "${RetrofitClient.BASE_URL}api/v3",
            apiKey = ArkConfig.apiKey,
            modelName = fallbackModel,
            source = LlmWalletSource.ArkFallback
        )
    }

    suspend fun resolveOpenAiProvider(
        fallbackModel: String
    ): OpenAiCompatibleProvider {
        return resolveOpenAiConfig(fallbackModel).toOpenAiProvider()
    }

    suspend fun resolvePreferredModel(
        fallbackModel: String
    ): String {
        return resolveSelectedProvider()?.modelName?.takeIf { it.isNotBlank() } ?: fallbackModel
    }

    fun getProviderConnectivitySnapshot(providerId: String): ProviderConnectivitySnapshot {
        val statusValue = prefs.getString("$KEY_TEST_STATUS_PREFIX$providerId", null)
        val status = statusValue?.let {
            runCatching { ProviderConnectivityStatus.valueOf(it) }.getOrNull()
        } ?: ProviderConnectivityStatus.Untested
        val testedAt = prefs.getLong("$KEY_TEST_TIME_PREFIX$providerId", 0L)
            .takeIf { it > 0L }
        val message = prefs.getString("$KEY_TEST_MESSAGE_PREFIX$providerId", null)
            ?.takeIf { it.isNotBlank() }
        return ProviderConnectivitySnapshot(
            status = status,
            lastTestedAt = testedAt,
            message = message
        )
    }

    fun setProviderConnectivitySnapshot(
        providerId: String,
        status: ProviderConnectivityStatus,
        message: String?,
        testedAt: Long = System.currentTimeMillis()
    ) {
        prefs.edit()
            .putString("$KEY_TEST_STATUS_PREFIX$providerId", status.name)
            .putLong("$KEY_TEST_TIME_PREFIX$providerId", testedAt)
            .putString("$KEY_TEST_MESSAGE_PREFIX$providerId", message.orEmpty())
            .apply()
    }

    fun clearProviderConnectivitySnapshot(providerId: String) {
        prefs.edit()
            .remove("$KEY_TEST_STATUS_PREFIX$providerId")
            .remove("$KEY_TEST_TIME_PREFIX$providerId")
            .remove("$KEY_TEST_MESSAGE_PREFIX$providerId")
            .apply()
    }

    private suspend fun resolveSelectedProvider(): LlmProviderEntity? {
        ensureSecretsMigrated()
        val defaultProviderId = getDefaultProviderId()
        val defaultProvider = if (defaultProviderId.isNullOrBlank()) {
            null
        } else {
            getProviderById(defaultProviderId)?.takeIf { provider ->
                provider.enabled
            }
        }
        if (defaultProvider != null) {
            return defaultProvider
        }

        val firstEnabled = listEnabledProviders().firstOrNull()
        if (firstEnabled != null && firstEnabled.id != defaultProviderId) {
            setDefaultProviderId(firstEnabled.id)
        }
        return firstEnabled
    }

    private suspend fun ensureSecretsMigrated() {
        migrationMutex.withLock {
            if (secretsMigrated) return@withLock
            providerDao.getAll().forEach { provider ->
                if (provider.apiKey.isNotBlank()) {
                    if (secretStore.getSecret(provider.id).isBlank()) {
                        secretStore.putSecret(provider.id, provider.apiKey)
                    }
                    providerDao.upsert(provider.copy(apiKey = ""))
                }
            }
            secretsMigrated = true
        }
    }

    private fun withResolvedSecret(provider: LlmProviderEntity): LlmProviderEntity {
        return provider.copy(
            apiKey = secretStore.getSecret(provider.id).ifBlank { provider.apiKey }
        )
    }
}

private fun LlmProviderEntity.toResolvedConfig(): LlmWalletResolvedConfig {
    return LlmWalletResolvedConfig(
        providerId = id,
        displayName = name,
        endpoint = endpoint,
        apiKey = apiKey,
        modelName = modelName,
        source = LlmWalletSource.SavedProvider
    )
}

private fun LlmProviderEntity.isArkResponsesCompatible(): Boolean {
    val normalized = endpoint
        .trim()
        .lowercase()
        .removeSuffix("/")
    return normalized.isBlank() ||
        normalized.contains("ark.cn-beijing.volces.com") ||
        normalized.endsWith("/api/v3") ||
        normalized.endsWith("/api/v3/responses")
}
