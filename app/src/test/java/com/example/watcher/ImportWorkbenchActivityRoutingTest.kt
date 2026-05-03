package com.example.watcher

import com.example.watcher.importworkbench.ImportActionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportWorkbenchActivityRoutingTest {

    @Test
    fun `resolve destination maps supported routes`() {
        assertEquals(
            ImportWorkbenchDestination.ApiWallet,
            ImportActionTarget(
                workspace = "wallet",
                route = "api_wallet",
                displayLabel = "打开 API 钱包"
            ).resolveDestination()
        )
        assertEquals(
            ImportWorkbenchDestination.AgentConfig,
            ImportActionTarget(
                workspace = "agents",
                route = "agent_config",
                displayLabel = "打开 Agent 配置"
            ).resolveDestination()
        )
        assertEquals(
            ImportWorkbenchDestination.TemplateManagement,
            ImportActionTarget(
                workspace = "templates",
                route = "template_management",
                displayLabel = "打开隐藏工作台"
            ).resolveDestination()
        )
    }

    @Test
    fun `resolve destination returns null for unknown routes`() {
        assertNull(
            ImportActionTarget(
                workspace = "future",
                route = "future_route",
                displayLabel = "打开未来工作台"
            ).resolveDestination()
        )
    }
}
