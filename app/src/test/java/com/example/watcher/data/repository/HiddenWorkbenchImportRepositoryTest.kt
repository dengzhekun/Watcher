package com.example.watcher.data.repository

import com.example.watcher.WatcherAudienceConfigImport
import com.example.watcher.WatcherExpertCouncilConfigImport
import com.example.watcher.WatcherProviderImportState
import com.example.watcher.data.model.AiAudienceEntity
import com.example.watcher.data.model.AudienceEngineType
import com.example.watcher.data.model.CouncilTemplateEntity
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenWorkbenchImportRepositoryTest {

    @Test
    fun `repository loads pending audience and council imports from external import state`() {
        val gson = Gson()
        val repository = HiddenWorkbenchImportRepository(
            storage = InMemoryStorage(
                providerStateJson = gson.toJson(
                    WatcherProviderImportState(
                        providerId = "xmax_main_chat",
                        providerName = "X-MAX 主站",
                        endpoint = "https://api.example.com/v1",
                        modelName = "gpt-5.5",
                        enabled = true,
                        makeDefault = true,
                        sourceSiteName = "主站",
                        sourceModelMode = "聊天模型",
                        importedAt = 1_775_000_000_000L
                    )
                ),
                audienceConfigJson = gson.toJson(
                    WatcherAudienceConfigImport(
                        enabled = true,
                        roomName = "观察席",
                        focusPrompt = "关注风险信号",
                        responseStyle = "短句"
                    )
                ),
                expertCouncilConfigJson = gson.toJson(
                    WatcherExpertCouncilConfigImport(
                        enabled = true,
                        topic = "联调复盘",
                        memberRoles = listOf("产品", "技术"),
                        workflow = "先分歧后结论"
                    )
                )
            )
        )

        val pending = repository.loadPendingImports()

        assertEquals("主站 / 聊天模型", pending.sourceLabel)
        assertEquals("xmax_main_chat", pending.providerIdHint)
        assertNotNull(pending.audienceImport)
        assertEquals("观察席", pending.audienceImport?.roomName)
        assertEquals("关注风险信号", pending.audienceImport?.focusPrompt)
        assertNotNull(pending.councilImport)
        assertEquals("联调复盘", pending.councilImport?.topic)
        assertEquals(listOf("产品", "技术"), pending.councilImport?.memberRoles)
    }

    @Test
    fun `audience import maps onto existing audience while preserving identity`() {
        val pending = PendingAudienceImport(
            roomName = "观察席",
            focusPrompt = "关注风险信号",
            responseStyle = "短句",
            enabled = true,
            sourceLabel = "主站 / 聊天模型",
            providerIdHint = "xmax_main_chat"
        )
        val existing = AiAudienceEntity(
            id = 42L,
            name = "观察席",
            audienceType = AudienceEngineType.Classic,
            persona = "旧人设",
            socialArchetype = "熟人",
            speakingStyle = "旧风格",
            spendingStyle = "克制",
            socialDrive = "稳定",
            providerId = "legacy_provider",
            enabled = false,
            heartbeatIntervalSeconds = 20,
            includeFrame = true,
            personalMemory = "保留记忆",
            agentStateJson = "{\"state\":true}",
            createdAt = 100L,
            updatedAt = 200L
        )

        val mapped = pending.toAudienceEntity(
            providerId = "xmax_main_chat",
            existing = existing,
            now = 300L
        )

        assertEquals(42L, mapped.id)
        assertEquals("观察席", mapped.name)
        assertEquals(AudienceEngineType.Agent, mapped.audienceType)
        assertEquals("关注风险信号", mapped.persona)
        assertEquals("短句", mapped.speakingStyle)
        assertEquals("xmax_main_chat", mapped.providerId)
        assertTrue(mapped.enabled)
        assertEquals("保留记忆", mapped.personalMemory)
        assertEquals("{\"state\":true}", mapped.agentStateJson)
        assertEquals(100L, mapped.createdAt)
        assertEquals(300L, mapped.updatedAt)
    }

    @Test
    fun `audience import can be saved as disabled draft without provider`() {
        val pending = PendingAudienceImport(
            roomName = "观察席",
            focusPrompt = "关注风险信号",
            responseStyle = "短句",
            enabled = true,
            sourceLabel = "主站 / 聊天模型",
            providerIdHint = null
        )

        val mapped = pending.toAudienceEntity(
            providerId = "",
            enabled = false,
            now = 400L
        )

        assertEquals("观察席", mapped.name)
        assertEquals("", mapped.providerId)
        assertEquals(false, mapped.enabled)
        assertEquals(400L, mapped.createdAt)
        assertEquals(400L, mapped.updatedAt)
    }

    @Test
    fun `council import maps into non default council template`() {
        val pending = PendingCouncilImport(
            topic = "联调复盘",
            memberRoles = listOf("产品", "技术"),
            workflow = "先分歧后结论",
            enabled = true,
            sourceLabel = "主站 / 聊天模型"
        )

        val mapped = pending.toCouncilTemplateEntity(
            existing = null,
            now = 500L
        )

        assertTrue(mapped.templateId.startsWith("imported_council_"))
        assertEquals("联调复盘", mapped.label)
        assertEquals("来自主站 / 聊天模型 的专家团导入草稿。", mapped.description)
        assertEquals("General", mapped.sceneType)
        assertEquals("先分歧后结论", mapped.objective)
        assertEquals("产品、技术", mapped.focus)
        assertEquals("导入来源：主站 / 聊天模型", mapped.background)
        assertEquals(false, mapped.isDefault)
        assertEquals(500L, mapped.updatedAt)
    }

    @Test
    fun `repository returns empty drafts when external import state is absent`() {
        val repository = HiddenWorkbenchImportRepository(storage = InMemoryStorage())

        val pending = repository.loadPendingImports()

        assertNull(pending.providerIdHint)
        assertNull(pending.audienceImport)
        assertNull(pending.councilImport)
        assertEquals("XMAX 外部导入", pending.sourceLabel)
    }

    @Test
    fun `repository clears applied audience and council drafts independently`() {
        val gson = Gson()
        val storage = InMemoryStorage(
            audienceConfigJson = gson.toJson(
                WatcherAudienceConfigImport(
                    enabled = true,
                    roomName = "观察席",
                    focusPrompt = "关注风险信号",
                    responseStyle = "短句"
                )
            ),
            expertCouncilConfigJson = gson.toJson(
                WatcherExpertCouncilConfigImport(
                    enabled = true,
                    topic = "联调复盘",
                    memberRoles = listOf("产品", "技术"),
                    workflow = "先分歧后结论"
                )
            )
        )
        val repository = HiddenWorkbenchImportRepository(storage = storage)

        repository.clearAudienceImport()

        assertNull(repository.loadPendingImports().audienceImport)
        assertNotNull(repository.loadPendingImports().councilImport)

        repository.clearCouncilImport()

        assertNull(repository.loadPendingImports().councilImport)
    }

    private data class InMemoryStorage(
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
}
