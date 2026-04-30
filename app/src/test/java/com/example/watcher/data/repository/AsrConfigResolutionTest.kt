package com.example.watcher.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AsrConfigResolutionTest {
    @Test
    fun editorResolutionShowsLegacyConfigForMigration() {
        val resolved = resolveEditorAsrConfig(
            stored = VolcengineAsrCredentials(),
            legacy = VolcengineAsrCredentials(
                appKey = "legacy-app",
                accessKey = "legacy-access"
            ),
            fallback = VolcengineAsrCredentials(
                appKey = "fallback-app",
                accessKey = "fallback-access",
                resourceId = DEFAULT_DOUBAO_STREAMING_ASR_RESOURCE_ID
            )
        )

        assertEquals(AsrConfigSource.LegacyRuntime, resolved.source)
        assertEquals("legacy-app", resolved.credentials.appKey)
        assertEquals("legacy-access", resolved.credentials.accessKey)
        assertEquals(DEFAULT_DOUBAO_STREAMING_ASR_RESOURCE_ID, resolved.credentials.resourceId)
    }

    @Test
    fun runtimeResolutionIgnoresLegacyConfigAndUsesFallback() {
        val resolved = resolveRuntimeAsrCredentials(
            stored = VolcengineAsrCredentials(),
            fallback = VolcengineAsrCredentials(
                appKey = "fallback-app",
                accessKey = "fallback-access",
                resourceId = DEFAULT_DOUBAO_STREAMING_ASR_RESOURCE_ID
            )
        )

        assertEquals("fallback-app", resolved.appKey)
        assertEquals("fallback-access", resolved.accessKey)
        assertEquals(DEFAULT_DOUBAO_STREAMING_ASR_RESOURCE_ID, resolved.resourceId)
    }

    @Test
    fun runtimeResolutionPrefersSavedWalletConfig() {
        val resolved = resolveRuntimeAsrCredentials(
            stored = VolcengineAsrCredentials(
                appKey = "wallet-app",
                accessKey = "wallet-access",
                resourceId = "wallet-resource"
            ),
            fallback = VolcengineAsrCredentials(
                appKey = "fallback-app",
                accessKey = "fallback-access",
                resourceId = DEFAULT_DOUBAO_STREAMING_ASR_RESOURCE_ID
            )
        )

        assertEquals("wallet-app", resolved.appKey)
        assertEquals("wallet-access", resolved.accessKey)
        assertEquals("wallet-resource", resolved.resourceId)
    }
}
