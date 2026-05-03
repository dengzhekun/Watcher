package com.example.watcher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryLogicTest {
    @Test
    fun disablesOfficialUpdateCheckForPrivatePackage() {
        assertFalse(shouldCheckOfficialUpdate("com.example.watcher"))
    }

    @Test
    fun keepsOfficialUpdateCheckForOfficialPackage() {
        assertTrue(shouldCheckOfficialUpdate("com.shokz.watcher"))
    }

    @Test
    fun prefersVersionCodeWhenPresent() {
        val result = isAppUpdateAvailable(
            currentVersion = "1.0.99",
            currentVersionCode = 4,
            latestVersion = "1.0.1",
            latestVersionCode = 5
        )

        assertTrue(result)
    }

    @Test
    fun doesNotUpdateWhenLatestVersionCodeIsNotHigher() {
        val result = isAppUpdateAvailable(
            currentVersion = "1.0.4",
            currentVersionCode = 4,
            latestVersion = "9.9.9",
            latestVersionCode = 4
        )

        assertFalse(result)
    }

    @Test
    fun fallsBackToSemanticTokensWhenVersionCodeMissing() {
        val result = isAppUpdateAvailable(
            currentVersion = "1.0.4",
            currentVersionCode = 4,
            latestVersion = "1.0.5",
            latestVersionCode = null
        )

        assertTrue(result)
    }

    @Test
    fun comparesVersionTokensBySegment() {
        assertEquals(
            1,
            compareVersionTokensForUpdate(listOf(1, 0, 10), listOf(1, 0, 4))
        )
    }
}
