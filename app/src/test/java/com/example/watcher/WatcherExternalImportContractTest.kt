package com.example.watcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `parse import payload captures extension sections and insecure tls warning`() {
        val plan = WatcherExternalImportContract.parseImportPayload(
            """
            {
              "providerId": "xmax_main_chat",
              "providerName": "X-MAX 主站",
              "endpoint": "https://api.example.com/v1",
              "apiKey": "sk-test",
              "modelName": "gpt-5.5",
              "allowInsecureTls": true,
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

        checkNotNull(plan.agentConfig)
        checkNotNull(plan.audienceConfig)
        checkNotNull(plan.expertCouncilConfig)
        assertEquals("watcher_agent", plan.agentConfig.agentId)
        assertEquals("X-MAX 主站", plan.request.providerName)
        assertEquals("观察席", plan.audienceConfig.roomName)
        assertEquals(listOf("产品", "技术"), plan.expertCouncilConfig.memberRoles)
        assertTrue(plan.ignoredSections.isEmpty())
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
              "audienceConfig": {
                "enabled": true,
                "roomName": "观察席",
                "focusPrompt": "关注风险",
                "responseStyle": "短句"
              }
            }
            """.trimIndent()
        )

        val result = WatcherExternalImportContract.buildSuccessResult(plan)

        assertEquals("partial_success", result.status)
        assertEquals(listOf("provider", "audienceConfig"), result.imported)
        assertTrue(result.ignored.isEmpty())
        assertTrue(result.message.contains("导入成功"))
        assertTrue(result.resultPayloadJson.contains("\"status\":\"partial_success\""))
    }

    @Test
    fun `parse import payload rejects invalid extension section`() {
        val error = runCatching {
            WatcherExternalImportContract.parseImportPayload(
                """
                {
                  "providerId": "xmax_main_chat",
                  "providerName": "X-MAX 主站",
                  "endpoint": "https://api.example.com/v1",
                  "apiKey": "sk-test",
                  "modelName": "gpt-5.5",
                  "agentConfig": {
                    "enabled": true,
                    "agentId": "",
                    "agentName": "Agent",
                    "systemPrompt": "prompt"
                  }
                }
                """.trimIndent()
            )
        }.exceptionOrNull()

        checkNotNull(error)
        assertFalse(error.message.isNullOrBlank())
        assertTrue(error.message!!.contains("agentConfig"))
    }

    @Test
    fun `build import status summarizes provider and staged XMAX sections`() {
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
                "enabled": false,
                "roomName": "观察席",
                "focusPrompt": "",
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
        val importedAt = 1_775_000_000_000L
        val provider = WatcherExternalImportContract.toProviderEntity(
            request = plan.request,
            now = importedAt
        )

        val status = WatcherExternalImportContract.buildImportStatus(
            providers = listOf(provider),
            defaultProviderId = "xmax_main_chat",
            providerStateJson = WatcherExternalImportContract.buildProviderImportStateJson(plan, importedAt),
            agentConfigJson = requireNotNull(plan.agentConfig).let { com.google.gson.Gson().toJson(it) },
            audienceConfigJson = requireNotNull(plan.audienceConfig).let { com.google.gson.Gson().toJson(it) },
            expertCouncilConfigJson = requireNotNull(plan.expertCouncilConfig).let { com.google.gson.Gson().toJson(it) }
        )

        assertTrue(status.hasImportedPayload)
        assertEquals("xmax_main_chat", status.providerId)
        assertTrue(status.providerFound)
        assertTrue(status.providerEnabled)
        assertTrue(status.providerIsDefault)
        assertEquals("主站 / 聊天模型", status.sourceLabel)
        assertEquals(importedAt, status.lastImportedAt)
        assertEquals(4, status.sections.size)
        assertEquals("Provider", status.sections[0].title)
        assertTrue(status.sections[0].imported)
        assertTrue(status.sections[0].enabled)
        assertTrue(status.sections[0].summary.contains("X-MAX 主站"))
        assertEquals("Agent", status.sections[1].title)
        assertEquals("Watcher Agent", status.sections[1].summary)
        assertEquals("AI 观众", status.sections[2].title)
        assertFalse(status.sections[2].enabled)
        assertEquals("专家团", status.sections[3].title)
        assertTrue(status.nextStepHint.contains("测试"))
    }
}
