package com.example.watcher.data.gateway

import android.graphics.Bitmap
import com.google.gson.Gson
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Route handler implementations for the gateway API.
 * Stateless — receives dependencies through method parameters.
 */
internal object GatewayRoutes {

    private val gson = Gson()

    // ── GET /api/capabilities ─────────────────────────────────────

    fun capabilities(baseUrl: String): Map<String, Any> = mapOf(
        "service" to mapOf(
            "name" to "Watcher",
            "version" to "1.0",
            "description" to "Watcher is an AI-powered visual monitoring service running on a mobile phone with a camera. It can continuously monitor camera feeds for changes, record and analyze video segments, and run multi-expert council analysis in real-time. External AI agents can remotely invoke these capabilities via this REST API."
        ),
        "auth" to mapOf(
            "type" to "api_key",
            "header" to "X-API-Key",
            "note" to "All /api/ endpoints except /api/health require X-API-Key header."
        ),
        "base_url" to baseUrl,
        "endpoints" to mapOf(
            "health" to mapOf("method" to "GET", "url" to "$baseUrl/api/health", "auth" to false, "description" to "Health check. Returns stream connection status."),
            "capabilities" to mapOf("method" to "GET", "url" to "$baseUrl/api/capabilities", "auth" to true, "description" to "This endpoint. Returns full API schema."),
            "snapshot" to mapOf("method" to "GET", "url" to "$baseUrl/api/stream/snapshot", "auth" to true, "returns" to "image/jpeg", "description" to "Returns current camera frame as JPEG image."),
            "create_task" to mapOf("method" to "POST", "url" to "$baseUrl/api/tasks", "auth" to true, "body" to "application/json", "description" to "Create and execute a task. Body: {\"tool\":\"<name>\", ...params}"),
            "list_tasks" to mapOf("method" to "GET", "url" to "$baseUrl/api/tasks", "auth" to true, "description" to "List all tasks (max 20, newest first)."),
            "get_task" to mapOf("method" to "GET", "url" to "$baseUrl/api/tasks/{id}", "auth" to true, "description" to "Get task status, result, and accumulated events."),
            "get_task_snapshot" to mapOf("method" to "GET", "url" to "$baseUrl/api/tasks/{id}/snapshot", "auth" to true, "returns" to "image/jpeg", "description" to "Get current camera frame for a running task."),
            "get_task_events" to mapOf("method" to "GET", "url" to "$baseUrl/api/tasks/{id}/events", "auth" to true, "description" to "Get task event history array."),
            "cancel_task" to mapOf("method" to "DELETE", "url" to "$baseUrl/api/tasks/{id}", "auth" to true, "description" to "Cancel a running task (e.g. stop monitoring)."),
            "list_agents" to mapOf("method" to "GET", "url" to "$baseUrl/api/agents", "auth" to true, "description" to "List registered autonomous agents."),
            "get_agent" to mapOf("method" to "GET", "url" to "$baseUrl/api/agents/{id}", "auth" to true, "description" to "Get one registered agent profile."),
            "start_agent_runtime" to mapOf("method" to "POST", "url" to "$baseUrl/api/agents/{id}/runs", "auth" to true, "description" to "Start a long-lived autonomous runtime for an agent."),
            "list_agent_runtimes" to mapOf("method" to "GET", "url" to "$baseUrl/api/agents/{id}/runs", "auth" to true, "description" to "List autonomous runtimes for an agent."),
            "get_agent_runtime" to mapOf("method" to "GET", "url" to "$baseUrl/api/agents/runs/{runtimeId}", "auth" to true, "description" to "Get one autonomous runtime snapshot."),
            "send_agent_signal" to mapOf("method" to "POST", "url" to "$baseUrl/api/agents/runs/{runtimeId}/signals", "auth" to true, "description" to "Send a signal to a running autonomous agent."),
            "get_agent_runtime_events" to mapOf("method" to "GET", "url" to "$baseUrl/api/agents/runs/{runtimeId}/events", "auth" to true, "description" to "List autonomous runtime events."),
            "stop_agent_runtime" to mapOf("method" to "DELETE", "url" to "$baseUrl/api/agents/runs/{runtimeId}", "auth" to true, "description" to "Stop a running autonomous agent runtime."),
            "stream_status" to mapOf("method" to "GET", "url" to "$baseUrl/api/stream/status", "auth" to true, "description" to "Check stream ownership. Returns {owner: 'phone'|'remote', reclaimRequested: bool, streamUrl: string}. Remote clients should poll this to detect reclaim requests."),
            "stream_handoff" to mapOf("method" to "POST", "url" to "$baseUrl/api/stream/handoff", "auth" to true, "description" to "Phone releases its ESP32 connection and returns the direct stream URL. Remote client connects to streamUrl directly."),
            "stream_reclaim" to mapOf("method" to "POST", "url" to "$baseUrl/api/stream/reclaim", "auth" to true, "description" to "Phone requests the stream back. Sets reclaimRequested flag. Remote client should poll /api/stream/status, disconnect when it sees the flag, then call /api/stream/release."),
            "stream_release" to mapOf("method" to "POST", "url" to "$baseUrl/api/stream/release", "auth" to true, "description" to "Remote client confirms it has disconnected from ESP32. Phone automatically reconnects."),
            "commentary_state" to mapOf("method" to "GET", "url" to "$baseUrl/api/commentary/state", "auth" to true, "description" to "Get live AI commentary state (requires commentary active in app). Returns isActive, entries, scene/entity memory, and memory summaries."),
            "commentary_entries" to mapOf("method" to "GET", "url" to "$baseUrl/api/commentary/entries?since={timestamp}", "auth" to true, "description" to "Get commentary entries. Use ?since=<wallClockStartTime> for incremental polling. Each entry has segmentIndex, wallClockStartTime, displayTimestamp, text, status, streamingText."),
            "commentary_ask" to mapOf("method" to "POST", "url" to "$baseUrl/api/commentary/ask", "auth" to true, "body" to "application/json", "description" to "Send observation requests to AI commentators. Body: {\"requests\": [\"describe the person in red\", ...]}. Max 8 concurrent requests, auto-expire after serving.")
        ),
        "task_lifecycle" to mapOf(
            "statuses" to listOf("Pending", "Running", "Completed", "Failed", "Cancelled"),
            "flow" to "Pending → Running → Completed/Failed. Long-running tasks (monitor) stay Running until DELETE cancels them.",
            "events" to "Each task has an 'events' array. Poll GET /api/tasks/{id} to see new events. Each event has 'type', 'data', and 'timestamp'."
        ),
        "agent_guide" to mapOf(
            "language" to "The API accepts both English and Chinese in all text fields (objective, task, etc.). Responses include Chinese text from AI analysis. Use UTF-8 encoding.",
            "calling_convention" to listOf(
                "1. POST /api/tasks with JSON body {\"tool\":\"<name>\", \"param1\":\"value1\", ...}",
                "2. Save the returned task 'id' from response.data.id",
                "3. Poll GET /api/tasks/{id} periodically to check status and read events",
                "4. For monitor tasks: events contain 'check_result' with fields: result (NORMAL/WARNING/ALERT), summary, reason, confidence",
                "5. To stop a long-running task: DELETE /api/tasks/{id}"
            ),
            "http_client_notes" to listOf(
                "This is a LAN-only service on a private IP. Standard HTTP fetch libraries (like browser fetch or MCP tools) may block private IPs due to SSRF protection. Use system-level HTTP clients instead:",
                "- Linux/macOS: curl",
                "- Windows: Use curl.exe full path (C:\\Windows\\System32\\curl.exe) to avoid PowerShell alias conflict, or use Invoke-RestMethod",
                "- Python: requests library works fine with private IPs",
                "- Node.js: axios or native http module works fine"
            ),
            "windows_powershell_example" to "Invoke-RestMethod -Uri '$baseUrl/api/tasks' -Method Post -Headers @{'X-API-Key'='YOUR_KEY'} -ContentType 'application/json' -Body '{\"tool\":\"monitor\",\"objective\":\"detect person entering room\",\"checkIntervalSeconds\":10}'",
            "curl_example" to "curl -X POST -H 'X-API-Key: YOUR_KEY' -H 'Content-Type: application/json' -d '{\"tool\":\"monitor\",\"objective\":\"detect person entering room\",\"checkIntervalSeconds\":10}' $baseUrl/api/tasks",
            "python_example" to "requests.post('$baseUrl/api/tasks', headers={'X-API-Key':'YOUR_KEY'}, json={'tool':'monitor','objective':'detect person entering room','checkIntervalSeconds':10})"
        ),
        "tools" to listOf(
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "snapshot",
                    "description" to "获取当前摄像头画面的实时截图。返回截图元信息，实际图片通过 GET /api/stream/snapshot 获取。",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to emptyMap<String, Any>(),
                        "required" to emptyList<String>()
                    )
                ),
                "example" to mapOf(
                    "request" to mapOf("tool" to "snapshot"),
                    "response_fields" to "format, size, message"
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "monitor",
                    "description" to "创建持续监控任务。系统会按设定间隔分析摄像头画面，检测是否满足监控条件。检测到目标事件时产生 ALERT 事件。任务持续运行直到被 DELETE 取消。通过 GET /api/tasks/{id} 轮询获取 events 数组中的 check_result 事件。",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "objective" to mapOf("type" to "string", "description" to "监控目标，用自然语言描述需要检测什么。例如：'有人进入房间就通知我'、'检测桌上的快递是否被取走'"),
                            "checkIntervalSeconds" to mapOf("type" to "integer", "description" to "检查间隔（秒），范围 2-300，默认 30。间隔越短检测越及时但消耗更多 API 额度", "default" to 30),
                            "triggerCondition" to mapOf("type" to "string", "description" to "可选的触发条件补充说明，帮助 AI 更准确判断")
                        ),
                        "required" to listOf("objective")
                    )
                ),
                "example" to mapOf(
                    "request" to mapOf("tool" to "monitor", "objective" to "有人站起来就通知我", "checkIntervalSeconds" to 10),
                    "events" to listOf(
                        mapOf("type" to "check_result", "data" to mapOf("result" to "NORMAL|WARNING|ALERT", "summary" to "分析摘要", "totalChecks" to "累计检查次数", "alerts" to "告警次数"))
                    )
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "video_analysis",
                    "description" to "Record video segments from the camera and analyze each with AI. Records for the specified duration, splits into segments, analyzes each segment, then generates a final summary. Best for tasks requiring continuous observation before drawing conclusions. Returns summary, conclusion, and timeline events.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "task" to mapOf("type" to "string", "description" to "Analysis task description. Tell the AI what to look for in the video. Example: 'Count how many people enter and leave the room'"),
                            "durationSeconds" to mapOf("type" to "integer", "description" to "Total recording duration in seconds (5-600). Default 60.", "default" to 60),
                            "segmentSeconds" to mapOf("type" to "integer", "description" to "Duration of each analysis segment in seconds (2-300). Default 10. Shorter segments = more granular analysis.", "default" to 10)
                        ),
                        "required" to listOf("task")
                    )
                ),
                "example" to mapOf(
                    "request" to mapOf("tool" to "video_analysis", "task" to "Observe and describe all activities in the scene", "durationSeconds" to 30, "segmentSeconds" to 10),
                    "events" to listOf(
                        mapOf("type" to "recording", "data" to "Progress updates during recording"),
                        mapOf("type" to "analyzing", "data" to "Progress updates during AI analysis"),
                        mapOf("type" to "completed", "data" to "Final status with summary")
                    ),
                    "result_fields" to "runId, status, summary, conclusion, segmentCount, durationSeconds, timelineEvents[]"
                )
            ),
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "council",
                    "description" to "启动智囊团多专家实时分析。多个 AI 专家（风险雷达、意图解码、策略引擎等）同时分析摄像头画面和语音，经过讨论后给出综合建议。适合面试、会议、谈判等需要深度分析的场景。（尚未实现）",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "sceneType" to mapOf("type" to "string", "description" to "场景类型", "enum" to listOf("Meeting", "Interview", "Speech", "General"), "default" to "General"),
                            "objective" to mapOf("type" to "string", "description" to "分析目标，描述智囊团需要帮你判断什么"),
                            "focus" to mapOf("type" to "string", "description" to "重点关注的维度，用分号分隔"),
                            "speakerRole" to mapOf("type" to "string", "description" to "用户在场景中的角色"),
                            "targetRole" to mapOf("type" to "string", "description" to "对方的角色"),
                            "background" to mapOf("type" to "string", "description" to "补充背景信息")
                        ),
                        "required" to listOf("objective")
                    )
                ),
                "status" to "not_implemented"
            )
        )
    )

    // ── GET /api/health ───────────────────────────────────────────

    fun health(hasFrame: Boolean): Map<String, Any> = mapOf(
        "status" to "ok",
        "streamConnected" to hasFrame,
        "timestamp" to System.currentTimeMillis()
    )

    // ── GET /api/stream/snapshot ──────────────────────────────────

    data class SnapshotResult(val bytes: ByteArray, val mimeType: String)

    fun snapshot(frameProvider: () -> Bitmap?): SnapshotResult? {
        val bitmap = frameProvider() ?: return null
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return SnapshotResult(bytes = out.toByteArray(), mimeType = "image/jpeg")
    }

    // ── JSON helpers ──────────────────────────────────────────────

    fun toJson(obj: Any): String = gson.toJson(obj)

    fun ok(data: Any? = null): String = toJson(GatewayResponse(ok = true, data = data))

    fun error(message: String): String = toJson(GatewayResponse(ok = false, error = message))

    fun parseBody(body: String): Map<String, Any?> {
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(body, Map::class.java) as? Map<String, Any?> ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
