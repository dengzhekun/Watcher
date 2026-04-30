package com.example.watcher.data.repository

import android.graphics.Bitmap
import com.example.watcher.data.model.VideoTaskPlan
import com.example.watcher.data.remote.ContentItem
import com.example.watcher.data.remote.DoubaoApiService
import com.example.watcher.data.remote.DoubaoImageRequest
import com.example.watcher.data.remote.DoubaoRequest
import com.example.watcher.data.remote.DoubaoResponse
import com.example.watcher.data.remote.ImageContentItem
import com.example.watcher.data.remote.ImageMessage
import com.example.watcher.data.remote.Message

internal class VideoTaskPlanner(
    private val apiService: DoubaoApiService,
    private val planningModel: String,
    private val apiKey: String
) {
    suspend fun planVideoTask(userInput: String, frame: Bitmap?): Result<VideoTaskPlan> {
        return runCatching {
            requireApiKey()
            val response = requestPlanningResponse(userInput, frame)
            val content = response.requireOutputText("video task planning")
            ModelOutputParser.parseVideoTaskPlan(content, userInput)
        }
    }

    private suspend fun requestPlanningResponse(userInput: String, frame: Bitmap?): DoubaoResponse {
        return if (frame != null) {
            apiService.analyzeImage(
                authorization = bearerToken(),
                request = DoubaoImageRequest(
                    model = planningModel,
                    input = listOf(
                        ImageMessage(
                            role = "system",
                            content = listOf(
                                ImageContentItem(
                                    type = "input_text",
                                    text = buildStructuredPlanningPrompt()
                                )
                            )
                        ),
                        ImageMessage(
                            role = "user",
                            content = buildPlanningContent(userInput, frame)
                        )
                    )
                )
            )
        } else {
            apiService.analyzeIntent(
                authorization = bearerToken(),
                request = DoubaoRequest(
                    model = planningModel,
                    input = listOf(
                        Message(
                            role = "system",
                            content = listOf(
                                ContentItem(
                                    type = "input_text",
                                    text = buildStructuredPlanningPrompt()
                                )
                            )
                        ),
                        Message(
                            role = "user",
                            content = listOf(
                                ContentItem(
                                    type = "input_text",
                                    text = userInput
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    private fun requireApiKey() {
        check(apiKey.isNotBlank()) {
            "API_KEY is missing. Set it in local.properties first."
        }
    }

    private fun bearerToken(): String = "Bearer $apiKey"

    private fun buildPlanningContent(userInput: String, frame: Bitmap?): List<ImageContentItem> {
        val items = mutableListOf<ImageContentItem>()
        if (frame != null) {
            items += ImageContentItem(
                type = "input_image",
                imageUrl = "data:image/jpeg;base64,${BitmapEncoding.toBase64(frame)}"
            )
        }
        items += ImageContentItem(type = "input_text", text = userInput)
        return items
    }

    private fun buildStructuredPlanningPrompt(): String {
        return """
            你需要将用户的视频分析意图转换为结构化执行计划。
            只返回 JSON。
            JSON 必须且只能包含以下字段：
            {
              "taskCategory": string,
              "strategyReason": string,
              "title": string,
              "userRequirement": string,
              "sceneContext": string,
              "recordingDurationSeconds": integer,
              "samplingFps": integer,
              "segmentDurationSeconds": integer,
              "captureIntervalSeconds": integer,
              "segmentCount": integer,
              "segmentAnalysisPrompt": string,
              "finalSummaryPrompt": string,
              "confirmationNotes": string,
              "autoStartStreamingOutput": boolean,
              "finalSummaryEnabled": boolean
            }
            约束要求：
            - taskCategory 只能是 long_horizon_summary、continuous_watch、short_burst_dense 之一。
            - recordingDurationSeconds 表示总观察时长。
            - samplingFps 表示模型采样密度，不是摄像头录制帧率。
            - segmentDurationSeconds 表示每次录制片段的时长。
            - captureIntervalSeconds 表示两次录制开始之间的间隔。
            - segmentCount 必须与 recordingDurationSeconds 和 captureIntervalSeconds 保持一致。
            - sceneContext 只描述稳定、可观察的场景事实。
            - segmentAnalysisPrompt 必须是给用户可直接编辑的简体中文提示词，用于指导单个片段分析。
            - finalSummaryPrompt 必须是给用户可直接编辑的简体中文提示词，用于指导全局汇总。
            - 两个提示词都要明确：JSON 字段名保持英文，字段值与说明文字使用简体中文。
            - confidence 优先使用 0 到 1 之间的数字；如果无法量化，也可以使用“高”“中”“低”。
            - timelineEvents 中的每一项都必须包含 timestampSeconds、title、detail、confidence。
            - 这些参数只是推荐值，用户后续还可以手动调整。
        """.trimIndent()
    }
}
