package com.example.watcher

import com.example.watcher.data.model.BaselineSource
import com.example.watcher.data.model.CheckResult
import com.example.watcher.data.model.MonitorMode
import com.example.watcher.data.model.TargetTrigger
import com.example.watcher.data.model.VideoTaskCategory
import com.example.watcher.data.repository.ModelOutputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOutputParserTest {
    @Test
    fun `intent parsing clamps interval and keeps baseline`() {
        val raw = """
            {
              "title": "Desk Watch",
              "userRequirement": "Alert me when the laptop leaves the desk",
              "originalSceneDescription": "A laptop sits on a wooden desk",
              "checkIntervalSeconds": 999,
              "promptTemplate": "Return JSON only."
            }
        """.trimIndent()

        val result = ModelOutputParser.parseIntentResult(
            rawText = raw,
            userInput = "Watch the desk",
            baseFrameBase64 = "baseline",
            baselineSource = BaselineSource.CapturedFrame,
            hasImage = true
        )

        assertEquals("Desk Watch", result.title)
        assertEquals(300, result.checkInterval)
        assertEquals("baseline", result.baseFrameBase64)
        assertEquals(MonitorMode.SceneBaseline, result.monitorMode)
    }

    @Test
    fun `intent parsing fills fallbacks when fields are missing`() {
        val result = ModelOutputParser.parseIntentResult(
            rawText = """{"title":"", "checkIntervalSeconds": 1}""",
            userInput = "Watch the hallway",
            baseFrameBase64 = null,
            baselineSource = BaselineSource.CapturedFrame,
            hasImage = false
        )

        assertEquals("Watch the hallway", result.userRequirement)
        assertEquals(2, result.checkInterval)
        assertTrue(result.promptTemplate.contains("Return JSON only"))
        assertEquals(BaselineSource.CapturedFrame, result.baselineSource)
    }

    @Test
    fun `intent parsing supports chinese keys`() {
        val raw = """
            {
              "任务标题": "Water Bucket",
              "用户需求": "Watch the bucket",
              "原始场景描述": "A bucket sits below a tap",
              "打点频率": 12,
              "每次提示词": "Return JSON only."
            }
        """.trimIndent()

        val result = ModelOutputParser.parseIntentResult(
            rawText = raw,
            userInput = "Watch the bucket",
            baseFrameBase64 = null,
            baselineSource = BaselineSource.UploadedImage,
            hasImage = true
        )

        assertEquals("Water Bucket", result.title)
        assertEquals(12, result.checkInterval)
    }

    @Test
    fun `intent parsing keeps reference target mode for uploaded image`() {
        val raw = """
            {
              "title": "Find Person",
              "userRequirement": "Alert me when this person appears",
              "originalSceneDescription": "An adult wearing a dark coat and carrying a backpack",
              "monitorMode": "ReferenceTarget",
              "targetTrigger": "OnAppear",
              "baselineSource": "UploadedImage"
            }
        """.trimIndent()

        val result = ModelOutputParser.parseIntentResult(
            rawText = raw,
            userInput = "Alert me when this person appears",
            baseFrameBase64 = "baseline",
            baselineSource = BaselineSource.UploadedImage,
            hasImage = true
        )

        assertEquals(MonitorMode.ReferenceTarget, result.monitorMode)
        assertEquals(TargetTrigger.OnAppear, result.targetTrigger)
        assertEquals(BaselineSource.UploadedImage, result.baselineSource)
    }

    @Test
    fun `monitor decision parsing accepts strict json`() {
        val raw = """
            {
              "status": "WARNING",
              "summary": "Someone is near the doorway",
              "reason": "A person stands in the monitored area",
              "confidence": 0.83
            }
        """.trimIndent()

        val decision = ModelOutputParser.parseMonitorDecision(raw)

        assertEquals(CheckResult.WARNING, decision.result)
        assertEquals("Someone is near the doorway", decision.summary)
        assertEquals(0.83f, decision.confidence ?: 0f, 0.0001f)
    }

    @Test
    fun `extract json handles reasoning text around payload`() {
        val raw = """
            The model thought about the scene first.
            {
              "status": "NORMAL",
              "summary": "The cup is still present"
            }
            Additional commentary should be ignored.
        """.trimIndent()

        val decision = ModelOutputParser.parseMonitorDecision(raw)

        assertEquals(CheckResult.NORMAL, decision.result)
        assertEquals("The cup is still present", decision.summary)
    }

    @Test
    fun `monitor decision parsing falls back to unknown for malformed output`() {
        val decision = ModelOutputParser.parseMonitorDecision("I am not sure what happened.")

        assertEquals(CheckResult.UNKNOWN, decision.result)
        assertTrue(decision.reason.contains("JSON"))
    }

    @Test
    fun `monitor decision parsing supports chinese keys`() {
        val raw = """
            {
              "状态": "ALERT",
              "摘要": "Water level is high",
              "原因": "The bucket is nearly full",
              "confidence": 0.91
            }
        """.trimIndent()

        val decision = ModelOutputParser.parseMonitorDecision(raw)

        assertEquals(CheckResult.ALERT, decision.result)
        assertEquals("Water level is high", decision.summary)
    }

    @Test
    fun `monitor decision parsing accepts textual confidence`() {
        val raw = """
            {
              "status": "WARNING",
              "summary": "Someone is near the doorway",
              "reason": "Movement detected",
              "confidence": "高"
            }
        """.trimIndent()

        val decision = ModelOutputParser.parseMonitorDecision(raw)

        assertEquals(CheckResult.WARNING, decision.result)
        assertEquals(0.85f, decision.confidence ?: 0f, 0.0001f)
    }

    @Test
    fun `video plan parsing normalizes segmentation for long tasks`() {
        val raw = """
            {
              "title": "Door Review",
              "userRequirement": "Review whether anyone entered the doorway",
              "sceneContext": "A doorway and a storage shelf are visible",
              "recordingDurationSeconds": 900,
              "samplingFps": 3,
              "segmentDurationSeconds": 120,
              "segmentCount": 1,
              "analysisPrompt": "Return JSON only.",
              "confirmationNotes": "Record long enough to cover a full delivery cycle."
            }
        """.trimIndent()

        val plan = ModelOutputParser.parseVideoTaskPlan(raw, "Review the doorway")

        assertEquals("Door Review", plan.title)
        assertEquals(900, plan.recordingDurationSeconds)
        assertTrue(plan.segmentCount > 1)
    }

    @Test
    fun `video plan parsing keeps split prompts from payload`() {
        val raw = """
            {
              "title": "中文提示词任务",
              "userRequirement": "请观察门口发生了什么",
              "sceneContext": "门口和前台都在画面中",
              "segmentAnalysisPrompt": "请分析当前片段，字段值使用简体中文。",
              "finalSummaryPrompt": "请汇总全部片段，字段值使用简体中文。"
            }
        """.trimIndent()

        val plan = ModelOutputParser.parseVideoTaskPlan(raw, "观察门口")

        assertEquals("请分析当前片段，字段值使用简体中文。", plan.segmentAnalysisPrompt)
        assertEquals("请汇总全部片段，字段值使用简体中文。", plan.finalSummaryPrompt)
    }

    @Test
    fun `video plan parsing supports legacy analysis prompt fallback`() {
        val raw = """
            {
              "title": "Legacy Prompt",
              "userRequirement": "Review the hallway",
              "sceneContext": "A hallway is visible",
              "analysisPrompt": "Return per-segment JSON only."
            }
        """.trimIndent()

        val plan = ModelOutputParser.parseVideoTaskPlan(raw, "Review the hallway")

        assertEquals("Return per-segment JSON only.", plan.segmentAnalysisPrompt)
        assertTrue(plan.finalSummaryPrompt.isNotBlank())
    }

    @Test
    fun `video plan parsing infers short dense strategy from one minute request`() {
        val raw = """
            {
              "title": "Quick Review",
              "userRequirement": "看看这一分钟会发生什么",
              "sceneContext": "A service desk is visible",
              "analysisPrompt": "Return JSON only."
            }
        """.trimIndent()

        val plan = ModelOutputParser.parseVideoTaskPlan(raw, "看看这一分钟会发生什么")

        assertEquals(VideoTaskCategory.ShortBurstDense.value, plan.taskCategory)
        assertEquals(60, plan.recordingDurationSeconds)
        assertEquals(6, plan.segmentDurationSeconds)
        assertEquals(6, plan.captureIntervalSeconds)
        assertEquals(10, plan.segmentCount)
    }

    @Test
    fun `video plan parsing keeps model suggestion but respects explicit duration`() {
        val raw = """
            {
              "taskCategory": "long_horizon_summary",
              "strategyReason": "Model recommends sparse sampling.",
              "title": "Child Review",
              "userRequirement": "看这个小孩这两个小时干了什么",
              "sceneContext": "A living room is visible",
              "recordingDurationSeconds": 30,
              "samplingFps": 3,
              "segmentDurationSeconds": 10,
              "captureIntervalSeconds": 60,
              "analysisPrompt": "Return JSON only."
            }
        """.trimIndent()

        val plan = ModelOutputParser.parseVideoTaskPlan(raw, "看这个小孩这两个小时干了什么")

        assertEquals(VideoTaskCategory.LongHorizonSummary.value, plan.taskCategory)
        assertEquals(7200, plan.recordingDurationSeconds)
        assertEquals(10, plan.segmentDurationSeconds)
        assertEquals(60, plan.captureIntervalSeconds)
        assertEquals("Model recommends sparse sampling.", plan.strategyReason)
    }

    @Test
    fun `video analysis parsing extracts timeline events`() {
        val raw = """
            {
              "summary": "A person entered and left with a package.",
              "conclusion": "Delivery completed successfully.",
              "timelineEvents": [
                {
                  "timestampSeconds": 12,
                  "title": "Person enters",
                  "detail": "A courier enters the frame carrying a box.",
                  "confidence": 0.88
                }
              ]
            }
        """.trimIndent()

        val result = ModelOutputParser.parseVideoAnalysis(raw)

        assertEquals("Delivery completed successfully.", result.conclusion)
        assertEquals(1, result.timelineEvents.size)
        assertEquals(12, result.timelineEvents.first().timestampSeconds)
    }

    @Test
    fun `video analysis parsing accepts textual confidence`() {
        val raw = """
            {
              "summary": "检测完成。",
              "conclusion": "发现一条高置信度事件。",
              "timelineEvents": [
                {
                  "timestampSeconds": 6,
                  "title": "有人经过",
                  "detail": "一名人员快速经过画面中心。",
                  "confidence": "高"
                }
              ]
            }
        """.trimIndent()

        val result = ModelOutputParser.parseVideoAnalysis(raw)

        assertEquals(1, result.timelineEvents.size)
        assertEquals(0.85f, result.timelineEvents.first().confidence ?: 0f, 0.0001f)
    }

    @Test
    fun `video analysis parsing keeps event when confidence is invalid`() {
        val raw = """
            {
              "summary": "检测完成。",
              "conclusion": "事件已保留。",
              "timelineEvents": [
                {
                  "timestampSeconds": 9,
                  "title": "门口有人停留",
                  "detail": "目标在门口短暂停留。",
                  "confidence": "未知"
                }
              ]
            }
        """.trimIndent()

        val result = ModelOutputParser.parseVideoAnalysis(raw)

        assertEquals(1, result.timelineEvents.size)
        assertEquals("门口有人停留", result.timelineEvents.first().title)
        assertEquals(null, result.timelineEvents.first().confidence)
    }
}
