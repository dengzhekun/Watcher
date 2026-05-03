package com.example.watcher.importworkbench

import com.example.watcher.WatcherExternalImportContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportWorkbenchRepositoryTest {

    @Test
    fun `repository round trips persisted batch into cards`() {
        val repository = ImportWorkbenchRepository(
            storage = InMemoryStorage()
        )
        val batch = ImportedResourceBatch(
            sourceLabel = "XMAX / 聊天模型",
            importedAt = 1_775_000_000_000L,
            resources = listOf(
                ImportedResourceInput(
                    resourceType = "provider",
                    resourceKey = "provider_main",
                    title = "XMAX 主站",
                    summary = "gpt-5.5",
                    received = true,
                    applied = true,
                    requiresManualAction = false,
                    failedMessage = null,
                    destination = ImportActionTarget(
                        workspace = "wallet",
                        route = "api_wallet",
                        displayLabel = "打开 API 钱包"
                    ),
                    detailLines = listOf("接口：https://api.example.com/v1")
                )
            )
        )

        repository.saveBatch(batch)

        val restored = repository.loadBatch()
        val cards = repository.loadCards()

        assertNotNull(restored)
        assertEquals("XMAX / 聊天模型", restored?.sourceLabel)
        assertEquals(1, cards.size)
        assertEquals(WorkbenchResourceState.APPLIED, cards.first().state)
        assertTrue(cards.first().detailLines.any { it.contains("https://api.example.com/v1") })
    }

    @Test
    fun `build watcher import batch marks agent success and manual resources correctly`() {
        val plan = WatcherExternalImportContract.parseImportPayload(
            """
            {
              "providerId": "xmax_main_chat",
              "providerName": "X-MAX 主站",
              "endpoint": "https://api.example.com/v1",
              "apiKey": "sk-test",
              "modelName": "gpt-5.5",
              "sourceSiteName": "主站",
              "sourceModelMode": "聊天模型",
              "agentConfig": {
                "enabled": true,
                "agentId": "watcher_agent",
                "agentName": "Watcher Agent",
                "systemPrompt": "接住任务",
                "entryPoint": "watcher://agent/main"
              },
              "audienceConfig": {
                "enabled": true,
                "roomName": "观察席",
                "focusPrompt": "关注风险",
                "responseStyle": "短句"
              },
              "expertCouncilConfig": {
                "enabled": true,
                "topic": "联调复盘",
                "memberRoles": ["产品", "技术"],
                "workflow": "先分歧后结论"
              }
            }
            """.trimIndent()
        )

        val batch = buildWatcherImportBatch(
            plan = plan,
            importedAt = 1_775_000_000_000L,
            agentApplied = true,
            agentFailureMessage = null
        )

        assertEquals("主站 / 聊天模型", batch.sourceLabel)
        assertEquals(4, batch.resources.size)
        assertTrue(batch.resources.first { it.resourceType == "provider" }.applied)
        assertTrue(batch.resources.first { it.resourceType == "agent" }.applied)
        assertTrue(batch.resources.first { it.resourceType == "audience_group" }.requiresManualAction)
        assertTrue(batch.resources.first { it.resourceType == "expert_council" }.requiresManualAction)
    }

    private class InMemoryStorage : ImportWorkbenchRepository.Storage {
        private var raw: String? = null

        override fun read(): String? = raw

        override fun write(raw: String?) {
            this.raw = raw
        }
    }
}
