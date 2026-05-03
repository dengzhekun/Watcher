package com.example.watcher.data.repository

import com.example.watcher.WatcherAgentConfigImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatcherImportedAgentRegistrarTest {

    @Test
    fun `buildRegistration maps imported config with conservative default goal`() {
        val registration = WatcherImportedAgentRegistrar.buildRegistration(
            agentConfig = WatcherAgentConfigImport(
                enabled = true,
                agentId = " imported_agent ",
                agentName = " Imported Agent ",
                systemPrompt = " You are imported. ",
                entryPoint = " watcher://agent/main "
            ),
            providerId = " imported_provider ",
            sourceSiteName = " XMAX Main ",
            sourceModelMode = " Chat "
        )

        assertEquals("imported_agent", registration.definition.agentId)
        assertEquals("Imported Agent", registration.definition.name)
        assertEquals("You are imported.", registration.definition.systemInstruction)
        assertEquals(WatcherImportedAgentRegistrar.DEFAULT_IMPORTED_GOAL, registration.definition.goal)
    }

    @Test
    fun `buildRegistration preserves import metadata in registration and definition`() {
        val registration = WatcherImportedAgentRegistrar.buildRegistration(
            agentConfig = WatcherAgentConfigImport(
                enabled = true,
                agentId = "imported_agent",
                agentName = "Imported Agent",
                systemPrompt = "You are imported.",
                entryPoint = "watcher://agent/main"
            ),
            providerId = "provider_a",
            sourceSiteName = "xmax",
            sourceModelMode = "chat"
        )

        assertEquals("provider_a", registration.metadata["brain.providerId"])
        assertEquals("watcher://agent/main", registration.metadata["import.entryPoint"])
        assertEquals("xmax", registration.metadata["import.sourceSiteName"])
        assertEquals("chat", registration.metadata["import.sourceModelMode"])

        assertEquals("watcher://agent/main", registration.definition.metadata["import.entryPoint"])
        assertEquals("xmax", registration.definition.metadata["import.sourceSiteName"])
        assertEquals("chat", registration.definition.metadata["import.sourceModelMode"])
    }

    @Test
    fun `buildRegistration rejects disabled imported agent config`() {
        val error = runCatching {
            WatcherImportedAgentRegistrar.buildRegistration(
                agentConfig = WatcherAgentConfigImport(
                    enabled = false,
                    agentId = "imported_agent",
                    agentName = "Imported Agent",
                    systemPrompt = "You are imported.",
                    entryPoint = "watcher://agent/main"
                ),
                providerId = "provider_a"
            )
        }.exceptionOrNull()

        checkNotNull(error)
        assertTrue(error.message!!.contains("enabled"))
    }
}
