package com.example.watcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatcherImportStatePersistenceTest {

    @Test
    fun `build import state entries clear sections missing from later imports`() {
        val initialPlan = WatcherExternalImportContract.parseImportPayload(
            """
            {
              "providerId": "xmax_main_chat",
              "providerName": "X-MAX 主站",
              "endpoint": "https://api.example.com/v1",
              "apiKey": "sk-test",
              "modelName": "gpt-5.5",
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
        val laterPlan = WatcherExternalImportContract.parseImportPayload(
            """
            {
              "providerId": "xmax_main_chat",
              "providerName": "X-MAX 主站",
              "endpoint": "https://api.example.com/v1",
              "apiKey": "sk-test",
              "modelName": "gpt-5.5"
            }
            """.trimIndent()
        )

        val persistedState = linkedMapOf<String, String?>()
        applyEntries(persistedState, buildImportStateEntries(initialPlan, importedAt = 1L))
        applyEntries(persistedState, buildImportStateEntries(laterPlan, importedAt = 2L))

        assertTrue(persistedState.containsKey(WatcherExternalImportContract.IMPORT_STATE_PROVIDER))
        assertFalse(persistedState.containsKey(WatcherExternalImportContract.IMPORT_STATE_AGENT))
        assertFalse(persistedState.containsKey(WatcherExternalImportContract.IMPORT_STATE_AUDIENCE))
        assertFalse(persistedState.containsKey(WatcherExternalImportContract.IMPORT_STATE_EXPERT_COUNCIL))
    }

    private fun applyEntries(
        destination: MutableMap<String, String?>,
        entries: Map<String, String?>
    ) {
        entries.forEach { (key, value) ->
            if (value == null) {
                destination.remove(key)
            } else {
                destination[key] = value
            }
        }
    }
}
