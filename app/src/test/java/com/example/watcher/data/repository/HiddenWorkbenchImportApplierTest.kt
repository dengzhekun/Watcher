package com.example.watcher.data.repository

import com.example.watcher.WatcherAudienceConfigImport
import com.example.watcher.WatcherExpertCouncilConfigImport
import com.example.watcher.WatcherProviderImportState
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.CouncilTemplateEntity
import com.example.watcher.data.model.LlmProviderEntity
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenWorkbenchImportApplierTest {

    @Test
    fun `applier saves audience as disabled draft when hinted provider is absent`() = runBlocking {
        val stagedImports = HiddenWorkbenchImportRepository(
            storage = InMemoryHiddenStorage(
                providerStateJson = gson.toJson(
                    WatcherProviderImportState(
                        providerId = "missing_provider",
                        providerName = "Missing Provider",
                        endpoint = "https://api.example.com/v1",
                        modelName = "gpt-5.5",
                        enabled = true,
                        makeDefault = false,
                        sourceSiteName = "XMAX",
                        sourceModelMode = "chat",
                        importedAt = 100L
                    )
                ),
                audienceConfigJson = gson.toJson(
                    WatcherAudienceConfigImport(
                        enabled = true,
                        roomName = "观察席",
                        focusPrompt = "关注风险",
                        responseStyle = "短句"
                    )
                )
            )
        )
        var savedAudience: AiAudienceEntity? = null
        val applier = HiddenWorkbenchImportApplier(
            stagedImports = stagedImports,
            listProviders = { emptyList() },
            listAudiences = { emptyList() },
            saveAudience = { savedAudience = it },
            listCouncilTemplates = { emptyList() },
            saveCouncilTemplate = {}
        )

        val result = applier.applyAudience()

        assertTrue(result.changed)
        assertEquals("AI 观众「观察席」已导入为停用草稿；请在 API 钱包补 Provider 后再启用。", result.message)
        assertEquals("missing_provider", savedAudience?.providerId)
        assertFalse(savedAudience?.enabled ?: true)
        assertNull(stagedImports.loadPendingImports().audienceImport)
    }

    @Test
    fun `applier updates audience with preferred enabled provider and clears staged audience`() = runBlocking {
        val stagedImports = HiddenWorkbenchImportRepository(
            storage = InMemoryHiddenStorage(
                audienceConfigJson = gson.toJson(
                    WatcherAudienceConfigImport(
                        enabled = true,
                        roomName = "观察席",
                        focusPrompt = "新关注点",
                        responseStyle = "短句"
                    )
                )
            )
        )
        val existing = AiAudienceEntity(
            id = 9L,
            name = "观察席",
            persona = "旧关注点",
            providerId = "old_provider",
            enabled = false,
            createdAt = 50L,
            updatedAt = 60L
        )
        val enabledProvider = LlmProviderEntity(
            id = "enabled_provider",
            name = "Enabled Provider",
            endpoint = "https://api.example.com/v1",
            apiKey = "sk-test",
            modelName = "gpt-5.5",
            enabled = true
        )
        var savedAudience: AiAudienceEntity? = null
        val applier = HiddenWorkbenchImportApplier(
            stagedImports = stagedImports,
            listProviders = { listOf(enabledProvider) },
            listAudiences = { listOf(existing) },
            saveAudience = { savedAudience = it },
            listCouncilTemplates = { emptyList() },
            saveCouncilTemplate = {}
        )

        val result = applier.applyAudience()

        assertTrue(result.changed)
        assertEquals("AI 观众「观察席」已更新。", result.message)
        assertEquals(9L, savedAudience?.id)
        assertEquals("enabled_provider", savedAudience?.providerId)
        assertTrue(savedAudience?.enabled ?: false)
        assertEquals(50L, savedAudience?.createdAt)
        assertNull(stagedImports.loadPendingImports().audienceImport)
    }

    @Test
    fun `applier saves council template and clears staged council`() = runBlocking {
        val stagedImports = HiddenWorkbenchImportRepository(
            storage = InMemoryHiddenStorage(
                expertCouncilConfigJson = gson.toJson(
                    WatcherExpertCouncilConfigImport(
                        enabled = true,
                        topic = "联调复盘",
                        memberRoles = listOf("产品", "技术"),
                        workflow = "先风险后结论"
                    )
                )
            )
        )
        var savedTemplate: CouncilTemplateEntity? = null
        val applier = HiddenWorkbenchImportApplier(
            stagedImports = stagedImports,
            listProviders = { emptyList() },
            listAudiences = { emptyList() },
            saveAudience = {},
            listCouncilTemplates = { emptyList() },
            saveCouncilTemplate = { savedTemplate = it }
        )

        val result = applier.applyCouncil()

        assertTrue(result.changed)
        assertEquals("智囊团模板「联调复盘」已导入。", result.message)
        assertEquals("联调复盘", savedTemplate?.label)
        assertEquals("产品、技术", savedTemplate?.focus)
        assertNull(stagedImports.loadPendingImports().councilImport)
    }

    @Test
    fun `applier reports unchanged when no staged audience exists`() = runBlocking {
        val stagedImports = HiddenWorkbenchImportRepository(storage = InMemoryHiddenStorage())
        val applier = HiddenWorkbenchImportApplier(
            stagedImports = stagedImports,
            listProviders = { emptyList() },
            listAudiences = { emptyList() },
            saveAudience = { error("should not save audience") },
            listCouncilTemplates = { emptyList() },
            saveCouncilTemplate = { error("should not save council") }
        )

        val result = applier.applyAudience()

        assertFalse(result.changed)
        assertEquals("当前没有可应用的 AI 观众导入。", result.message)
    }

    private data class InMemoryHiddenStorage(
        val providerStateJson: String? = null,
        var audienceConfigJson: String? = null,
        var expertCouncilConfigJson: String? = null
    ) : HiddenWorkbenchImportRepository.Storage {
        override fun readProviderState(): String? = providerStateJson

        override fun readAudienceConfig(): String? = audienceConfigJson

        override fun readExpertCouncilConfig(): String? = expertCouncilConfigJson

        override fun clearAudienceConfig() {
            audienceConfigJson = null
        }

        override fun clearExpertCouncilConfig() {
            expertCouncilConfigJson = null
        }
    }

    private companion object {
        val gson = Gson()
    }
}
