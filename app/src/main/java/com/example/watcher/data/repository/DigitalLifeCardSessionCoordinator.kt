package com.example.watcher.data.repository

import android.graphics.Bitmap
import com.example.watcher.data.model.CommentaryEntry
import com.example.watcher.data.model.MatchBreakdown
import com.example.watcher.data.model.SceneProbeSnapshot
import com.example.watcher.data.model.SceneProfile
import java.io.File

class DigitalLifeCardSessionCoordinator(
    private val liveCommentaryRepository: LiveCommentaryRepository,
    private val blackboardManager: BlackboardManager,
    private val placeClusterManager: PlaceClusterManager,
    private val sceneProfileRepository: SceneProfileRepository,
    private val portraitModelService: DigitalLifeCardPortraitModelService,
    private val portraitCuratorAgent: PortraitCuratorAgent
) {
    suspend fun startObservation(
        initialFrame: Bitmap?,
        latestFrameProvider: () -> Bitmap?,
        outputRoot: File
    ): StartObservationResult {
        liveCommentaryRepository.sceneMemoryManager.reset()
        val placeSnapshot = placeClusterManager.snapshot()
        val sceneAnalysis = sceneProfileRepository.analyzeScene(
            frame = initialFrame,
            placeSnapshot = placeSnapshot
        )
        val lastSceneProbe = sceneAnalysis?.probe

        val sceneProfile = sceneAnalysis?.recallResult?.let { recall ->
            liveCommentaryRepository.sceneMemoryManager.preloadSceneProfile(
                profile = recall.profile,
                probeSummary = recall.probeSummary,
                matchedAnchors = recall.matchedAnchors
            )
            recall.profile
        } ?: sceneProfileRepository.createPlaceholderScene(lastSceneProbe).also { profile ->
            liveCommentaryRepository.sceneMemoryManager.preloadSceneProfile(
                profile = profile,
                probeSummary = lastSceneProbe?.summary ?: "当前画面尚未建立场景摘要",
                matchedAnchors = emptyList()
            )
        }

        val sceneLabel = portraitModelService.displaySceneLabel(sceneProfile)
        portraitCuratorAgent.currentSceneId = sceneProfile.sceneId
        portraitCuratorAgent.currentSceneLabel = sceneLabel
        portraitCuratorAgent.start()
        portraitCuratorAgent.refreshMemoryDebug()
        liveCommentaryRepository.startCommentary(
            outputRoot = outputRoot,
            latestFrameProvider = latestFrameProvider,
            speechProvider = null
        )
        return StartObservationResult(
            sceneProfile = sceneProfile,
            sceneLabel = sceneLabel,
            lastSceneProbe = lastSceneProbe,
            lastMatchedSceneId = sceneAnalysis?.recallResult?.profile?.sceneId,
            lastMatchBreakdown = sceneAnalysis?.recallResult?.matchBreakdown
        )
    }

    suspend fun stopObservation(
        currentSceneId: String?,
        currentSceneLabel: String,
        lastSceneProbe: SceneProbeSnapshot?,
        date: String,
        flushIngestion: suspend () -> Unit = {}
    ): StopObservationResult {
        liveCommentaryRepository.stopCommentary()
        liveCommentaryRepository.awaitCommentaryDrain()
        flushIngestion()
        val snapshotState = liveCommentaryRepository.commentaryState.value
        blackboardManager.syncDaySnapshot(snapshotState)
        val updatedScene = portraitModelService.persistSceneProfileSnapshot(
            currentSceneId = currentSceneId,
            currentSceneProfileId = liveCommentaryRepository.sceneMemoryManager.currentSceneProfileId,
            commentaryState = snapshotState,
            date = date,
            probe = lastSceneProbe
        )
        val finalSceneId = updatedScene?.sceneId ?: currentSceneId

        return if (portraitCuratorAgent.isRunning) {
            portraitCuratorAgent.beginConsolidation()
            portraitCuratorAgent.refreshMemoryDebug()
            StopObservationResult(
                updatedSceneProfile = updatedScene,
                finalSceneId = finalSceneId,
                enteredConsolidation = true
            )
        } else {
            portraitCuratorAgent.currentSceneId = finalSceneId
            portraitCuratorAgent.currentSceneLabel = updatedScene?.let(portraitModelService::displaySceneLabel)
                ?: currentSceneLabel
            portraitCuratorAgent.refreshMemoryDebug()
            StopObservationResult(
                updatedSceneProfile = updatedScene,
                finalSceneId = finalSceneId,
                enteredConsolidation = false
            )
        }
    }

    suspend fun requestAgentStop() {
        portraitCuratorAgent.stop()
    }

    suspend fun regenerateSceneModel(
        sceneId: String,
        fallbackSceneLabel: String,
        completedEntries: List<CommentaryEntry>
    ) {
        if (portraitCuratorAgent.isRunning) {
            portraitCuratorAgent.stopAndAwaitTermination()
        } else {
            portraitCuratorAgent.clearTerminalRuntime()
        }
        portraitModelService.clearSceneBehaviorModel(sceneId)
        portraitCuratorAgent.currentSceneId = sceneId
        portraitCuratorAgent.currentSceneLabel = portraitModelService.resolveSceneLabel(sceneId, fallbackSceneLabel)
        portraitCuratorAgent.start()
        portraitCuratorAgent.refreshMemoryDebug()
        completedEntries
            .sortedBy { it.segmentIndex }
            .forEach { entry ->
                portraitCuratorAgent.feedObservation(extractDigitalLifeCardBehaviorSignal(entry.text))
            }
    }
}

data class StartObservationResult(
    val sceneProfile: SceneProfile,
    val sceneLabel: String,
    val lastSceneProbe: SceneProbeSnapshot?,
    val lastMatchedSceneId: String?,
    val lastMatchBreakdown: MatchBreakdown?
)

data class StopObservationResult(
    val updatedSceneProfile: SceneProfile?,
    val finalSceneId: String?,
    val enteredConsolidation: Boolean
)
