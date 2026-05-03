package com.example.watcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
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
        val existing = walletRepository.getProviderById(plan.request.providerId)
        val provider = WatcherExternalImportContract.toProviderEntity(
            request = plan.request,
            existingCreatedAt = existing?.createdAt
        )
        walletRepository.upsertProvider(
            provider = provider,
            makeDefault = plan.request.makeDefault
        )
        return WatcherExternalImportContract.buildSuccessResult(plan)
    }
}
