package com.example.watcher.importworkbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportWorkbenchContractTest {
    @Test
    fun `to workbench entries maps generic resources and statuses`() {
        val batch = ImportedResourceBatch(
            sourceLabel = "External import",
            importedAt = 1_775_000_000_000L,
            resources = listOf(
                ImportedResourceInput(
                    resourceType = "provider",
                    resourceKey = "provider_main",
                    title = "Primary Provider",
                    summary = "gpt-5.5",
                    received = true,
                    applied = true,
                    requiresManualAction = false,
                    failedMessage = null,
                    destination = ImportActionTarget(
                        workspace = "wallet",
                        route = "providers/provider_main",
                        displayLabel = "Open Wallet"
                    )
                ),
                ImportedResourceInput(
                    resourceType = "agent",
                    resourceKey = "agent_ops",
                    title = "Ops Agent",
                    summary = "Needs prompt review",
                    received = true,
                    applied = false,
                    requiresManualAction = true,
                    failedMessage = null,
                    destination = ImportActionTarget(
                        workspace = "agents",
                        route = "agents/agent_ops",
                        displayLabel = "Open Agent Config"
                    )
                ),
                ImportedResourceInput(
                    resourceType = "expert_council",
                    resourceKey = "council_1",
                    title = "Safety Council",
                    summary = "2 members",
                    received = true,
                    applied = false,
                    requiresManualAction = false,
                    failedMessage = "Role mapping failed",
                    destination = ImportActionTarget(
                        workspace = "council",
                        route = "council/council_1",
                        displayLabel = "Open Council"
                    )
                )
            )
        )

        val cards = ImportWorkbenchContract.toWorkbenchCards(batch)

        assertEquals(3, cards.size)
        assertEquals(WorkbenchResourceState.APPLIED, cards[0].state)
        assertEquals("wallet", cards[0].actionTarget.workspace)
        assertEquals(WorkbenchResourceState.NEEDS_MANUAL_ACTION, cards[1].state)
        assertEquals("Open Agent Config", cards[1].actionTarget.displayLabel)
        assertEquals(WorkbenchResourceState.FAILED, cards[2].state)
        assertTrue(cards[2].detailLines.any { it.contains("Role mapping failed") })
    }

    @Test
    fun `to workbench entries uses received state when only received`() {
        val cards = ImportWorkbenchContract.toWorkbenchCards(
            ImportedResourceBatch(
                sourceLabel = "External import",
                importedAt = 1_775_000_000_000L,
                resources = listOf(
                    ImportedResourceInput(
                        resourceType = "audience_group",
                        resourceKey = "aud_1",
                        title = "Audience Group A",
                        summary = "Queued",
                        received = true,
                        applied = false,
                        requiresManualAction = false,
                        failedMessage = null,
                        destination = ImportActionTarget(
                            workspace = "audiences",
                            route = "groups/aud_1",
                            displayLabel = "Open Audience Groups"
                        )
                    )
                )
            )
        )

        assertEquals(1, cards.size)
        assertEquals(WorkbenchResourceState.RECEIVED, cards[0].state)
        assertTrue(cards[0].detailLines.any { it.contains("External import") })
    }
}
