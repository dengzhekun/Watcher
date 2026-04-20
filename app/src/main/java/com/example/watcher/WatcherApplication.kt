package com.example.watcher

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.watcher.agentframework.integration.AppAgentBrainConnectionTester
import com.example.watcher.agentframework.integration.AppDefaultAgentBrainFactory
import com.example.watcher.agentframework.integration.APP_DEFAULT_LLM_BRAIN_FACTORY_ID
import com.example.watcher.agentframework.service.AgentBrainCatalog
import com.example.watcher.agentframework.service.AgentBrainConnectionTester
import com.example.watcher.agentframework.service.AgentFrameworkService
import com.example.watcher.agentframework.service.StaticAgentBrainCatalog
import com.example.watcher.data.local.AppDatabase
import com.example.watcher.data.local.litert.CompositeAgentBrainConnectionTester
import com.example.watcher.data.local.litert.LITERT_BRAIN_FACTORY_ID
import com.example.watcher.data.local.litert.LiteRtAgentBrainFactory
import com.example.watcher.data.local.litert.LiteRtAssetInstaller
import com.example.watcher.data.local.litert.LiteRtBackendType
import com.example.watcher.data.local.litert.LiteRtConfigStore
import com.example.watcher.data.local.litert.LiteRtModelDownloader
import com.example.watcher.data.local.litert.LiteRtConnectionTester
import com.example.watcher.data.local.litert.LiteRtEngineManager
import com.example.watcher.data.local.litert.LiteRtLlmProvider
import com.example.watcher.data.local.litert.LiteRtModelConfig
import com.example.watcher.data.repository.LlmWalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WatcherApplication : Application() {
    val agentFrameworkContainer: AgentFrameworkContainer by lazy {
        AgentFrameworkContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            // Install bundled model from assets on first launch
            val container = agentFrameworkContainer
            val installedPath = container.liteRtAssetInstaller.installBundledModelIfNeeded()

            // Determine config: saved config or auto-config from bundled model
            val config = container.liteRtConfigStore.loadConfig()
                ?: installedPath?.let {
                    LiteRtModelConfig(
                        modelPath = it,
                        displayName = "Gemma 4 E2B",
                        backend = LiteRtBackendType.GPU,
                        visionBackend = LiteRtBackendType.GPU
                    ).also { cfg -> container.liteRtConfigStore.saveConfig(cfg) }
                }

            if (config != null) {
                runCatching {
                    container.liteRtEngineManager.initialize(config)
                }.onFailure { e ->
                    Log.w("WatcherApplication", "LiteRT auto-init failed: ${e.message}")
                }
            }
        }
    }
}

class AgentFrameworkContainer(
    application: Application
) {
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(application)
    }

    val llmWalletRepository: LlmWalletRepository by lazy {
        LlmWalletRepository(application, database.llmProviderDao())
    }

    private val defaultBrainFactory: AppDefaultAgentBrainFactory by lazy {
        AppDefaultAgentBrainFactory(llmWalletRepository)
    }

    // LiteRT-LM on-device inference
    val liteRtAssetInstaller: LiteRtAssetInstaller by lazy { LiteRtAssetInstaller(application) }
    val liteRtModelDownloader: LiteRtModelDownloader by lazy { LiteRtModelDownloader(application) }
    val liteRtConfigStore: LiteRtConfigStore by lazy { LiteRtConfigStore(application) }
    val liteRtEngineManager: LiteRtEngineManager by lazy { LiteRtEngineManager(application) }
    val liteRtProvider: LiteRtLlmProvider by lazy { LiteRtLlmProvider(liteRtEngineManager, application) }
    private val liteRtBrainFactory: LiteRtAgentBrainFactory by lazy {
        LiteRtAgentBrainFactory(liteRtProvider)
    }

    val brainCatalog: AgentBrainCatalog by lazy {
        StaticAgentBrainCatalog(
            defaultFactoryId = APP_DEFAULT_LLM_BRAIN_FACTORY_ID,
            registeredFactories = listOf(defaultBrainFactory, liteRtBrainFactory)
        )
    }

    val brainConnectionTester: AgentBrainConnectionTester by lazy {
        CompositeAgentBrainConnectionTester(
            testers = mapOf(
                APP_DEFAULT_LLM_BRAIN_FACTORY_ID to AppAgentBrainConnectionTester(defaultBrainFactory),
                LITERT_BRAIN_FACTORY_ID to LiteRtConnectionTester(liteRtProvider, liteRtEngineManager)
            )
        )
    }

    val service: AgentFrameworkService by lazy {
        AgentFrameworkService.builder()
            .persistentStorage(application.filesDir)
            .addBrainCatalog(brainCatalog)
            .build()
    }
}

fun Context.watcherApplication(): WatcherApplication {
    return applicationContext as WatcherApplication
}
