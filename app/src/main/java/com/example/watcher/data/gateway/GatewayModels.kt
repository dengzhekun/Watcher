package com.example.watcher.data.gateway

/** Status of a gateway task. */
enum class GatewayTaskStatus { Pending, Running, Completed, Failed, Cancelled }

/** A task submitted through the gateway API. */
data class GatewayTask(
    val id: String,
    val tool: String,
    val params: Map<String, Any?>,
    val status: GatewayTaskStatus = GatewayTaskStatus.Pending,
    val result: Any? = null,
    val error: String? = null,
    val events: MutableList<GatewayEvent> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

/** A real-time event emitted during task execution. */
data class GatewayEvent(
    val type: String,
    val data: Any?,
    val timestamp: Long = System.currentTimeMillis()
)

/** Standard API response wrapper. */
data class GatewayResponse(
    val ok: Boolean,
    val data: Any? = null,
    val error: String? = null
)
