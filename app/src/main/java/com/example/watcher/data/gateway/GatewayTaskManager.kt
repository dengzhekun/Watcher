package com.example.watcher.data.gateway

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages lifecycle of tasks submitted through the gateway API.
 * Tasks execute asynchronously; callers poll status via GET /api/tasks/{id}.
 *
 * Tool executors are registered externally via [registerExecutor].
 */
class GatewayTaskManager {

    companion object {
        private const val TAG = "GatewayTaskMgr"
        private const val MAX_TASKS = 50
    }

    fun interface ToolExecutor {
        /**
         * Execute the tool with given params. Should:
         * - Call [onEvent] to emit progress events
         * - Return the final result object (will be serialized to JSON)
         * - Throw on failure
         */
        suspend fun execute(params: Map<String, Any?>, onEvent: (GatewayEvent) -> Unit): Any?
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val tasks = ConcurrentHashMap<String, GatewayTask>()
    private val executors = ConcurrentHashMap<String, ToolExecutor>()
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val onCancelCallbacks = ConcurrentHashMap<String, () -> Unit>()

    fun registerExecutor(toolName: String, executor: ToolExecutor) {
        executors[toolName] = executor
    }

    fun createTask(tool: String, params: Map<String, Any?>): GatewayTask {
        val executor = executors[tool]
        val id = UUID.randomUUID().toString().take(12)
        val task = GatewayTask(id = id, tool = tool, params = params)

        if (executor == null) {
            val failed = task.copy(
                status = GatewayTaskStatus.Failed,
                error = "Unknown tool: $tool. Use GET /api/capabilities to see available tools."
            )
            tasks[id] = failed
            return failed
        }

        tasks[id] = task
        pruneOldTasks()

        val job = scope.launch {
            try {
                updateTask(id) { it.copy(status = GatewayTaskStatus.Running) }
                Log.d(TAG, "Task $id: executing tool=$tool")

                val result = executor.execute(params) { event ->
                    tasks[id]?.let { t ->
                        t.events.add(event)
                        t.updatedAt = System.currentTimeMillis()
                    }
                }

                updateTask(id) {
                    it.copy(status = GatewayTaskStatus.Completed, result = result)
                }
                Log.d(TAG, "Task $id: completed")
            } catch (e: CancellationException) {
                updateTask(id) { it.copy(status = GatewayTaskStatus.Cancelled) }
                Log.d(TAG, "Task $id: cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Task $id: failed", e)
                updateTask(id) {
                    it.copy(status = GatewayTaskStatus.Failed, error = e.message ?: "Execution failed")
                }
            } finally {
                runningJobs.remove(id)
                onCancelCallbacks.remove(id)
            }
        }
        runningJobs[id] = job

        return tasks[id]!!
    }

    fun getTask(id: String): GatewayTask? = tasks[id]

    fun listTasks(): List<GatewayTask> = tasks.values
        .sortedByDescending { it.createdAt }
        .take(20)

    fun cancelTask(id: String): Boolean {
        val task = tasks[id] ?: return false
        if (task.status == GatewayTaskStatus.Completed ||
            task.status == GatewayTaskStatus.Failed ||
            task.status == GatewayTaskStatus.Cancelled
        ) return false
        val callback = onCancelCallbacks.remove(id)
        if (callback != null) {
            // Graceful cancel: callback signals the executor to stop,
            // executor finishes cleanup (e.g. summarize) then exits naturally.
            callback.invoke()
        } else {
            // Hard cancel: no callback registered, force-kill the Job.
            runningJobs.remove(id)?.cancel()
            updateTask(id) { it.copy(status = GatewayTaskStatus.Cancelled) }
        }
        return true
    }

    /** Register a cleanup callback invoked when a task is cancelled. */
    fun onTaskCancel(taskId: String, callback: () -> Unit) {
        onCancelCallbacks[taskId] = callback
    }

    fun release() {
        tasks.clear()
    }

    private fun updateTask(id: String, transform: (GatewayTask) -> GatewayTask) {
        tasks.computeIfPresent(id) { _, task ->
            transform(task).also { it.updatedAt = System.currentTimeMillis() }
        }
    }

    private fun pruneOldTasks() {
        if (tasks.size <= MAX_TASKS) return
        val toRemove = tasks.values
            .filter { it.status in setOf(GatewayTaskStatus.Completed, GatewayTaskStatus.Failed, GatewayTaskStatus.Cancelled) }
            .sortedBy { it.createdAt }
            .take(tasks.size - MAX_TASKS)
        toRemove.forEach { tasks.remove(it.id) }
    }
}
