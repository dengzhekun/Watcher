package com.example.watcher.data.gateway

import android.graphics.Bitmap
import android.util.Log
import com.example.watcher.agentframework.autonomy.SignalChannel
import com.example.watcher.agentframework.service.AgentFrameworkService
import com.example.watcher.agentframework.service.AgentKnowledgeSeed
import com.example.watcher.agentframework.service.AgentMemorySeed
import com.example.watcher.agentframework.service.AgentSignalSeed
import com.example.watcher.agentframework.service.AutonomousAgentStartRequest
import com.example.watcher.agentframework.core.AgentMemoryScope
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking

/**
 * Embedded HTTP server exposing Watcher capabilities to LAN clients.
 *
 * Usage:
 *   val server = GatewayServer(port = 8080, apiKey = "xxx", frameProvider = { ... })
 *   server.start()
 *   // ... later
 *   server.stop()
 */
class GatewayServer(
    port: Int = DEFAULT_PORT,
    private val apiKey: String,
    private val localIpProvider: () -> String,
    private val frameProvider: () -> Bitmap?,
    private val taskManager: GatewayTaskManager,
    private val agentService: AgentFrameworkService? = null,
    private val commentaryStateProvider: (() -> Any)? = null,
    private val commentaryEntriesProvider: ((since: Long) -> List<Any>)? = null,
    private val onCommentaryAsk: ((List<String>) -> Unit)? = null,
    private val streamStatusProvider: (() -> Map<String, Any?>)? = null,
    private val onStreamHandoff: (() -> String?)? = null,
    private val onStreamReclaim: (() -> Unit)? = null,
    private val onStreamRelease: (() -> Unit)? = null
) : NanoHTTPD(port) {

    private val baseUrl: String get() = "http://${localIpProvider()}:$listeningPort"

    companion object {
        private const val TAG = "GatewayServer"
        const val DEFAULT_PORT = 8080
    }

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        val uri = session.uri.trimEnd('/')
        Log.d(TAG, "$method $uri")

        // CORS headers for browser-based agents
        if (method == Method.OPTIONS) {
            return corsResponse(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
        }

        // Health check — no auth required
        if (uri == "/api/health" && method == Method.GET) {
            return jsonResponse(GatewayRoutes.ok(GatewayRoutes.health(frameProvider() != null)))
        }

        // Auth check for all other /api/ routes
        if (uri.startsWith("/api/") && apiKey.isNotBlank()) {
            val provided = session.headers["x-api-key"] ?: session.parms["api_key"]
            if (provided != apiKey) {
                return jsonResponse(GatewayRoutes.error("Invalid or missing API key"), Response.Status.UNAUTHORIZED)
            }
        }

        return try {
            route(method, uri, session)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling $method $uri", e)
            jsonResponse(GatewayRoutes.error(e.message ?: "Internal error"), Response.Status.INTERNAL_ERROR)
        }
    }

    private fun route(method: Method, uri: String, session: IHTTPSession): Response = when {
        // Capabilities
        method == Method.GET && uri == "/api/capabilities" ->
            jsonResponse(GatewayRoutes.toJson(GatewayRoutes.capabilities(baseUrl)))

        // Snapshot
        method == Method.GET && uri == "/api/stream/snapshot" -> {
            val result = GatewayRoutes.snapshot(frameProvider)
            if (result != null) {
                corsResponse(newFixedLengthResponse(Response.Status.OK, result.mimeType, ByteArrayInputStream(result.bytes), result.bytes.size.toLong()))
            } else {
                jsonResponse(GatewayRoutes.error("No frame available — stream may not be connected"), Response.Status.SERVICE_UNAVAILABLE)
            }
        }

        // Create task
        method == Method.POST && uri == "/api/tasks" -> {
            val body = readBody(session)
            val params = GatewayRoutes.parseBody(body)
            val tool = params["tool"] as? String
            if (tool.isNullOrBlank()) {
                jsonResponse(GatewayRoutes.error("Missing required field: tool"), Response.Status.BAD_REQUEST)
            } else {
                val task = taskManager.createTask(tool, params - "tool")
                jsonResponse(GatewayRoutes.ok(task), Response.Status.CREATED)
            }
        }

        // List tasks
        method == Method.GET && uri == "/api/tasks" ->
            jsonResponse(GatewayRoutes.ok(taskManager.listTasks()))

        // Get task by ID
        method == Method.GET && uri.matches(Regex("/api/tasks/[^/]+")) -> {
            val taskId = uri.removePrefix("/api/tasks/")
            val task = taskManager.getTask(taskId)
            if (task != null) jsonResponse(GatewayRoutes.ok(task))
            else jsonResponse(GatewayRoutes.error("Task not found: $taskId"), Response.Status.NOT_FOUND)
        }

        // Get task snapshot (current frame for a running monitor task)
        method == Method.GET && uri.matches(Regex("/api/tasks/[^/]+/snapshot")) -> {
            val taskId = uri.removePrefix("/api/tasks/").removeSuffix("/snapshot")
            val task = taskManager.getTask(taskId)
            if (task == null) {
                jsonResponse(GatewayRoutes.error("Task not found: $taskId"), Response.Status.NOT_FOUND)
            } else if (task.status != GatewayTaskStatus.Running) {
                jsonResponse(GatewayRoutes.error("Task is not running"), Response.Status.BAD_REQUEST)
            } else {
                val result = GatewayRoutes.snapshot(frameProvider)
                if (result != null) {
                    corsResponse(newFixedLengthResponse(Response.Status.OK, result.mimeType, ByteArrayInputStream(result.bytes), result.bytes.size.toLong()))
                } else {
                    jsonResponse(GatewayRoutes.error("No frame available"), Response.Status.SERVICE_UNAVAILABLE)
                }
            }
        }

        // Get task events
        method == Method.GET && uri.matches(Regex("/api/tasks/[^/]+/events")) -> {
            val taskId = uri.removePrefix("/api/tasks/").removeSuffix("/events")
            val task = taskManager.getTask(taskId)
            if (task != null) {
                jsonResponse(GatewayRoutes.ok(task.events))
            } else {
                jsonResponse(GatewayRoutes.error("Task not found: $taskId"), Response.Status.NOT_FOUND)
            }
        }

        // Cancel task
        method == Method.DELETE && uri.matches(Regex("/api/tasks/[^/]+")) -> {
            val taskId = uri.removePrefix("/api/tasks/")
            val cancelled = taskManager.cancelTask(taskId)
            if (cancelled) jsonResponse(GatewayRoutes.ok("Task cancelled"))
            else jsonResponse(GatewayRoutes.error("Task not found or already finished: $taskId"), Response.Status.NOT_FOUND)
        }

        // List agents
        method == Method.GET && uri == "/api/agents" -> {
            val service = agentService ?: return jsonResponse(
                GatewayRoutes.error("Agent service not configured"),
                Response.Status.NOT_IMPLEMENTED
            )
            jsonResponse(GatewayRoutes.ok(runBlocking { service.listAgents() }))
        }

        // Get agent
        method == Method.GET && uri.matches(Regex("/api/agents/[^/]+")) -> {
            val service = agentService ?: return jsonResponse(
                GatewayRoutes.error("Agent service not configured"),
                Response.Status.NOT_IMPLEMENTED
            )
            val agentId = uri.removePrefix("/api/agents/")
            val profile = runBlocking { service.getAgentProfile(agentId) }
            if (profile != null) jsonResponse(GatewayRoutes.ok(profile))
            else jsonResponse(GatewayRoutes.error("Agent not found: $agentId"), Response.Status.NOT_FOUND)
        }

        // List runtimes for agent
        method == Method.GET && uri.matches(Regex("/api/agents/[^/]+/runs")) -> {
            val service = agentService ?: return jsonResponse(
                GatewayRoutes.error("Agent service not configured"),
                Response.Status.NOT_IMPLEMENTED
            )
            val agentId = uri.removePrefix("/api/agents/").removeSuffix("/runs")
            jsonResponse(GatewayRoutes.ok(runBlocking { service.listAutonomousRuntimes(agentId) }))
        }

        // Start runtime
        method == Method.POST && uri.matches(Regex("/api/agents/[^/]+/runs")) -> {
            val service = agentService ?: return jsonResponse(
                GatewayRoutes.error("Agent service not configured"),
                Response.Status.NOT_IMPLEMENTED
            )
            val agentId = uri.removePrefix("/api/agents/").removeSuffix("/runs")
            val params = GatewayRoutes.parseBody(readBody(session))
            val record = runBlocking {
                service.startAutonomousAgent(
                    AutonomousAgentStartRequest(
                        agentId = agentId,
                        initialSignals = parseSignals(params["signals"]),
                        preloadMemory = parseMemorySeeds(params["preloadMemory"]),
                        preloadKnowledge = parseKnowledgeSeeds(params["preloadKnowledge"])
                    )
                )
            }
            jsonResponse(GatewayRoutes.ok(record), Response.Status.CREATED)
        }

        // Get runtime
        method == Method.GET && uri.matches(Regex("/api/agents/runs/[^/]+")) -> {
            val service = agentService ?: return jsonResponse(
                GatewayRoutes.error("Agent service not configured"),
                Response.Status.NOT_IMPLEMENTED
            )
            val runtimeId = uri.removePrefix("/api/agents/runs/")
            val runtime = runBlocking { service.getAutonomousRuntime(runtimeId) }
            if (runtime != null) jsonResponse(GatewayRoutes.ok(runtime))
            else jsonResponse(GatewayRoutes.error("Autonomous runtime not found: $runtimeId"), Response.Status.NOT_FOUND)
        }

        // Runtime events
        method == Method.GET && uri.matches(Regex("/api/agents/runs/[^/]+/events")) -> {
            val service = agentService ?: return jsonResponse(
                GatewayRoutes.error("Agent service not configured"),
                Response.Status.NOT_IMPLEMENTED
            )
            val runtimeId = uri.removePrefix("/api/agents/runs/").removeSuffix("/events")
            val runtime = runBlocking { service.getAutonomousRuntime(runtimeId) }
            if (runtime == null) {
                jsonResponse(GatewayRoutes.error("Autonomous runtime not found: $runtimeId"), Response.Status.NOT_FOUND)
            } else {
                jsonResponse(GatewayRoutes.ok(runBlocking { service.getAutonomousRuntimeEvents(runtimeId) }))
            }
        }

        // Send runtime signal
        method == Method.POST && uri.matches(Regex("/api/agents/runs/[^/]+/signals")) -> {
            val service = agentService ?: return jsonResponse(
                GatewayRoutes.error("Agent service not configured"),
                Response.Status.NOT_IMPLEMENTED
            )
            val runtimeId = uri.removePrefix("/api/agents/runs/").removeSuffix("/signals")
            val params = GatewayRoutes.parseBody(readBody(session))
            val signal = parseSingleSignal(params)
                ?: return jsonResponse(
                    GatewayRoutes.error("Missing required signal payload"),
                    Response.Status.BAD_REQUEST
                )
            val runtime = runBlocking { service.submitAutonomousSignal(runtimeId, signal) }
            jsonResponse(GatewayRoutes.ok(runtime))
        }

        // Stop runtime
        method == Method.DELETE && uri.matches(Regex("/api/agents/runs/[^/]+")) -> {
            val service = agentService ?: return jsonResponse(
                GatewayRoutes.error("Agent service not configured"),
                Response.Status.NOT_IMPLEMENTED
            )
            val runtimeId = uri.removePrefix("/api/agents/runs/")
            val stopped = runBlocking { service.stopAutonomousRuntime(runtimeId) }
            if (stopped) jsonResponse(GatewayRoutes.ok("Autonomous runtime stopped"))
            else jsonResponse(GatewayRoutes.error("Autonomous runtime not found: $runtimeId"), Response.Status.NOT_FOUND)
        }

        // Stream status — check ownership and reclaim state
        method == Method.GET && uri == "/api/stream/status" -> {
            val provider = streamStatusProvider
                ?: return jsonResponse(GatewayRoutes.error("Stream management not available"), Response.Status.NOT_IMPLEMENTED)
            jsonResponse(GatewayRoutes.ok(provider()))
        }

        // Stream handoff — phone releases connection, returns ESP32 URL for direct access
        method == Method.POST && uri == "/api/stream/handoff" -> {
            val handler = onStreamHandoff
            if (handler == null) {
                jsonResponse(GatewayRoutes.error("Stream management not available"), Response.Status.NOT_IMPLEMENTED)
            } else {
                val currentOwner = (streamStatusProvider?.invoke()?.get("owner") as? String) ?: "phone"
                if (currentOwner == "remote") {
                    jsonResponse(GatewayRoutes.error("Stream already handed off to remote client"), Response.Status.CONFLICT)
                } else {
                    val url = handler()
                    if (url.isNullOrBlank()) {
                        jsonResponse(GatewayRoutes.error("Phone has no active stream connection — device may not be connected"), Response.Status.SERVICE_UNAVAILABLE)
                    } else {
                        jsonResponse(GatewayRoutes.ok(mapOf("streamUrl" to url, "message" to "Phone connection released. Connect to streamUrl directly. Poll GET /api/stream/status to check for reclaim requests.")))
                    }
                }
            }
        }

        // Stream reclaim — phone requests the stream back (sets flag for remote to see)
        method == Method.POST && uri == "/api/stream/reclaim" -> {
            val handler = onStreamReclaim
                ?: return jsonResponse(GatewayRoutes.error("Stream management not available"), Response.Status.NOT_IMPLEMENTED)
            handler()
            jsonResponse(GatewayRoutes.ok(mapOf("message" to "Reclaim requested. Waiting for remote client to release.")))
        }

        // Stream release — remote client confirms it has disconnected from ESP32
        method == Method.POST && uri == "/api/stream/release" -> {
            val handler = onStreamRelease
                ?: return jsonResponse(GatewayRoutes.error("Stream management not available"), Response.Status.NOT_IMPLEMENTED)
            handler()
            jsonResponse(GatewayRoutes.ok(mapOf("message" to "Stream released. Phone is reconnecting.")))
        }

        // Commentary state
        method == Method.GET && uri == "/api/commentary/state" -> {
            val provider = commentaryStateProvider
                ?: return jsonResponse(GatewayRoutes.error("Commentary not available"), Response.Status.NOT_IMPLEMENTED)
            jsonResponse(GatewayRoutes.ok(provider()))
        }

        // Commentary entries (supports ?since=<timestamp> for incremental polling)
        method == Method.GET && uri == "/api/commentary/entries" -> {
            val provider = commentaryEntriesProvider
                ?: return jsonResponse(GatewayRoutes.error("Commentary not available"), Response.Status.NOT_IMPLEMENTED)
            val since = session.parms["since"]?.toLongOrNull() ?: 0L
            jsonResponse(GatewayRoutes.ok(provider(since)))
        }

        // Commentary ASK — inject observation requests into consumers
        method == Method.POST && uri == "/api/commentary/ask" -> {
            val handler = onCommentaryAsk
                ?: return jsonResponse(GatewayRoutes.error("Commentary not available"), Response.Status.NOT_IMPLEMENTED)
            val body = GatewayRoutes.parseBody(readBody(session))
            val requests = (body["requests"] as? List<*>)?.filterIsInstance<String>()
            if (requests.isNullOrEmpty()) {
                jsonResponse(GatewayRoutes.error("Missing required field: requests (string array)"), Response.Status.BAD_REQUEST)
            } else {
                handler(requests)
                jsonResponse(GatewayRoutes.ok(mapOf("accepted" to requests.size)))
            }
        }

        // 404
        else -> jsonResponse(GatewayRoutes.error("Not found: $uri"), Response.Status.NOT_FOUND)
    }

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: ""
    }

    private fun jsonResponse(json: String, status: Response.Status = Response.Status.OK): Response {
        return corsResponse(newFixedLengthResponse(status, "application/json", json))
    }

    private fun corsResponse(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-API-Key")
        return response
    }

    private fun parseSignals(raw: Any?): List<AgentSignalSeed> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val channel = (map["channel"] as? String)
                ?.let { runCatching { SignalChannel.valueOf(it) }.getOrNull() }
                ?: return@mapNotNull null
            val content = map["content"] as? String ?: return@mapNotNull null
            AgentSignalSeed(
                channel = channel,
                content = content,
                metadata = parseStringMap(map["metadata"])
            )
        }
    }

    private fun parseSingleSignal(raw: Map<String, Any?>): AgentSignalSeed? {
        val channel = (raw["channel"] as? String)
            ?.let { runCatching { SignalChannel.valueOf(it) }.getOrNull() }
            ?: return null
        val content = raw["content"] as? String ?: return null
        return AgentSignalSeed(
            channel = channel,
            content = content,
            metadata = parseStringMap(raw["metadata"])
        )
    }

    private fun parseMemorySeeds(raw: Any?): List<AgentMemorySeed> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val scope = when ((map["scope"] as? String)?.lowercase()) {
                "episodic" -> AgentMemoryScope.Episodic
                "working" -> AgentMemoryScope.Working
                else -> null
            } ?: return@mapNotNull null
            val content = map["content"] as? String ?: return@mapNotNull null
            AgentMemorySeed(
                scope = scope,
                content = content,
                tags = parseStringSet(map["tags"])
            )
        }
    }

    private fun parseKnowledgeSeeds(raw: Any?): List<AgentKnowledgeSeed> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val content = map["content"] as? String ?: return@mapNotNull null
            AgentKnowledgeSeed(
                content = content,
                tags = parseStringSet(map["tags"]),
                metadata = parseStringMap(map["metadata"])
            )
        }
    }

    private fun parseStringSet(raw: Any?): Set<String> {
        return (raw as? List<*>)
            .orEmpty()
            .mapNotNull { it as? String }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun parseStringMap(raw: Any?): Map<String, String> {
        val map = raw as? Map<*, *> ?: return emptyMap()
        return map.entries.mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val value = entry.value?.toString() ?: return@mapNotNull null
            key to value
        }.toMap()
    }
}
