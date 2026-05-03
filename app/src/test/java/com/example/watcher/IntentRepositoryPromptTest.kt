package com.example.watcher

import com.example.watcher.data.model.BaselineSource
import com.example.watcher.data.repository.buildIntentPromptText
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentRepositoryPromptTest {
    @Test
    fun `captured frame prompt keeps scene baseline mode`() {
        val prompt = buildIntentPrompt(BaselineSource.CapturedFrame, hasImage = true)

        assertTrue(prompt.contains("SceneBaseline"))
        assertTrue(prompt.contains("monitorMode"))
        assertTrue(prompt.contains("baselineSource"))
    }

    @Test
    fun `uploaded image prompt allows reference target mode`() {
        val prompt = buildIntentPrompt(BaselineSource.UploadedImage, hasImage = true)

        assertTrue(prompt.contains("ReferenceTarget"))
        assertTrue(prompt.contains("targetTrigger"))
        assertTrue(prompt.contains("主体/目标"))
    }

    @Test
    fun `text only prompt forces scene baseline`() {
        val prompt = buildIntentPrompt(BaselineSource.CapturedFrame, hasImage = false)

        assertTrue(prompt.contains("SceneBaseline"))
        assertTrue(prompt.contains("checkIntervalSeconds"))
    }

    private fun buildIntentPrompt(baselineSource: BaselineSource, hasImage: Boolean): String {
        return buildIntentPromptText(
            baselineSource = baselineSource,
            hasImage = hasImage
        )
    }
}
