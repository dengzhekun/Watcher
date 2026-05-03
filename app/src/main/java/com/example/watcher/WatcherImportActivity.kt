package com.example.watcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.watcher.data.repository.WatcherImportedAgentRegistrar
import com.example.watcher.importworkbench.ImportWorkbenchRepository
import com.example.watcher.importworkbench.buildWatcherImportBatch
import com.google.gson.Gson
import kotlinx.coroutines.launch

class WatcherImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val result = runCatching { importPayload() }
            val resultIntent = result.fold(
                onSuccess = { success ->
                    Intent()
                        .putExtra(WatcherExternalImportContract.EXTRA_RESULT_MESSAGE, success.message)
                        .putExtra(WatcherExternalImportContract.EXTRA_RESULT_MESSAGE_LEGACY, success.message)
                        .putExtra(WatcherExternalImportContract.EXTRA_GENERIC_RESULT_MESSAGE, success.message)
                        .putExtra(
                            WatcherExternalImportContract.EXTRA_GENERIC_RESULT_PAYLOAD,
                            success.resultPayloadJson
                        )
                },
                onFailure = { error ->
                    val message = WatcherExternalImportContract.buildFailureMessage(error)
                    Intent()
                        .putExtra(WatcherExternalImportContract.EXTRA_RESULT_MESSAGE, message)
                        .putExtra(WatcherExternalImportContract.EXTRA_RESULT_MESSAGE_LEGACY, message)
                        .putExtra(WatcherExternalImportContract.EXTRA_GENERIC_RESULT_MESSAGE, message)
                }
            )
            setResult(
                if (result.isSuccess) Activity.RESULT_OK else Activity.RESULT_CANCELED,
                resultIntent
            )
            finish()
        }
    }

    private suspend fun importPayload(): WatcherExternalImportSuccessResult {
        val payload = intent.getStringExtra(WatcherExternalImportContract.EXTRA_IMPORT_PAYLOAD)
            ?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(WatcherExternalImportContract.EXTRA_IMPORT_PAYLOAD_LEGACY)
                ?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(WatcherExternalImportContract.EXTRA_GENERIC_IMPORT_PAYLOAD)
                ?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("缺少导入 payload")

        val plan = WatcherExternalImportContract.parseImportPayload(payload)
        val walletRepository = applicationContext
            .watcherApplication()
            .agentFrameworkContainer
            .llmWalletRepository
        val container = applicationContext
            .watcherApplication()
            .agentFrameworkContainer
        val existing = walletRepository.getProviderById(plan.request.providerId)
        val provider = WatcherExternalImportContract.toProviderEntity(
            request = plan.request,
            existingCreatedAt = existing?.createdAt
        )
        walletRepository.upsertProvider(
            provider = provider,
            makeDefault = plan.request.makeDefault
        )
        val importedAt = System.currentTimeMillis()
        val agentFailureMessage = plan.agentConfig
            ?.takeIf { it.enabled }
            ?.let { agentConfig ->
                runCatching {
                    container.service.registerAgent(
                        WatcherImportedAgentRegistrar.buildRegistration(
                            agentConfig = agentConfig,
                            providerId = provider.id,
                            sourceSiteName = plan.request.sourceSiteName,
                            sourceModelMode = plan.request.sourceModelMode
                        )
                    )
                }.exceptionOrNull()?.message
            }
        val agentApplied = plan.agentConfig?.enabled == true && agentFailureMessage == null

        persistImportState(plan, importedAt)
        ImportWorkbenchRepository.fromContext(applicationContext).saveBatch(
            buildWatcherImportBatch(
                plan = plan,
                importedAt = importedAt,
                agentApplied = agentApplied,
                agentFailureMessage = agentFailureMessage
            )
        )

        val effectivePlan = if (agentFailureMessage.isNullOrBlank()) {
            plan
        } else {
            plan.copy(
                warnings = plan.warnings + "agentConfig 自动注册失败：$agentFailureMessage"
            )
        }
        return WatcherExternalImportContract.buildSuccessResult(effectivePlan)
    }

    private fun persistImportState(
        plan: WatcherExternalImportPlan,
        importedAt: Long
    ) {
        val prefs = getSharedPreferences(
            WatcherExternalImportContract.IMPORT_STATE_PREFS,
            Context.MODE_PRIVATE
        )
        val editor = prefs.edit()
        buildImportStateEntries(plan, importedAt).forEach { (key, value) ->
            editor.putString(key, value)
        }
        editor.apply()
    }
}

internal fun buildImportStateEntries(
    plan: WatcherExternalImportPlan,
    importedAt: Long,
    gson: Gson = Gson()
): Map<String, String?> {
    return linkedMapOf(
        WatcherExternalImportContract.IMPORT_STATE_PROVIDER to
            WatcherExternalImportContract.buildProviderImportStateJson(plan, importedAt),
        WatcherExternalImportContract.IMPORT_STATE_AGENT to
            plan.agentConfig?.let(gson::toJson),
        WatcherExternalImportContract.IMPORT_STATE_AUDIENCE to
            plan.audienceConfig?.let(gson::toJson),
        WatcherExternalImportContract.IMPORT_STATE_EXPERT_COUNCIL to
            plan.expertCouncilConfig?.let(gson::toJson)
    )
}
