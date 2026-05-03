package com.example.watcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatcherExternalImportContractTest {
    @Test
    fun `parse import payload keeps provider fields and default flags`() {
        val plan = WatcherExternalImportContract.parseImportPayload(
            """
            {
              "providerId": "xmax_main_chat",
              "providerName": "X-MAX 主站",
              "endpoint": "https://api.example.com/v1",
              "apiKey": "sk-test",
              "modelName": "gpt-5.5",
              "enabled": true,
              "makeDefault": true,
              "allowInsecureTls": false,
              "sourceSiteName": "主站",
              "sourceModelMode": "聊天模型"
            }
            """.trimIndent()
        )

        assertEquals("xmax_main_chat", plan.request.providerId)
        assertEquals("X-MAX 主站", plan.request.providerName)
        assertEquals("https://api.example.com/v1", plan.request.endpoint)
        assertEquals("sk-test", plan.request.apiKey)
        assertEquals("gpt-5.5", plan.request.modelName)
        assertTrue(plan.request.enabled)
        assertTrue(plan.request.makeDefault)
        assertTrue(plan.ignoredSections.isEmpty())
        assertTrue(plan.warnings.isEmpty())
    }

    @Test
    fun `parse import payload records unsupported sections and insecure tls warning`() {
        val plan = WatcherExternalImportContract.parseImportPayload(
            """
            {
              "providerId": "xmax_main_chat",
              "providerName": "X-MAX 主站",
              "endpoint": "https://api.example.com/v1",
              "apiKey": "sk-test",
              "modelName": "gpt-5.5",
              "allowInsecureTls": true,
              "agentConfig": {"agentId": "watcher_agent"},
              "audienceConfig": {"enabled": true},
              "expertCouncilConfig": {"enabled": true}
            }
            """.trimIndent()
        )

        assertEquals(
            listOf("agentConfig", "audienceConfig", "expertCouncilConfig"),
            plan.ignoredSections
        )
        assertTrue(plan.warnings.any { it.contains("allowInsecureTls") })
        assertTrue(plan.warnings.any { it.contains("agentConfig") })
        assertTrue(plan.warnings.any { it.contains("audienceConfig") })
        assertTrue(plan.warnings.any { it.contains("expertCouncilConfig") })
    }

    @Test
    fun `parse import payload rejects insecure endpoint`() {
        val error = runCatching {
            WatcherExternalImportContract.parseImportPayload(
                """
                {
                  "providerId": "xmax_main_chat",
                  "providerName": "X-MAX 主站",
                  "endpoint": "http://api.example.com/v1",
                  "apiKey": "sk-test",
                  "modelName": "gpt-5.5"
                }
                """.trimIndent()
            )
        }.exceptionOrNull()

        checkNotNull(error)
        assertEquals(
            "Provider endpoint must use https:// to protect API keys in transit.",
            error.message
        )
    }

    @Test
    fun `success payload becomes partial when unsupported sections are present`() {
        val plan = WatcherExternalImportContract.parseImportPayload(
            """
            {
              "providerId": "xmax_main_chat",
              "providerName": "X-MAX 主站",
              "endpoint": "https://api.example.com/v1",
              "apiKey": "sk-test",
              "modelName": "gpt-5.5",
              "audienceConfig": {"enabled": true}
            }
            """.trimIndent()
        )

        val result = WatcherExternalImportContract.buildSuccessResult(plan)

        assertEquals("partial_success", result.status)
        assertEquals(listOf("provider"), result.imported)
        assertEquals(listOf("audienceConfig"), result.ignored)
        assertTrue(result.message.contains("导入成功"))
        assertTrue(result.resultPayloadJson.contains("\"status\":\"partial_success\""))
    }
}
