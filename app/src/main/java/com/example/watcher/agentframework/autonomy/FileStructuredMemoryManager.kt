package com.example.watcher.agentframework.autonomy

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FileBackedStructuredMemoryStore(
    private val rootDir: File,
    private val gson: Gson = Gson(),
    private val onReadError: ((File, Exception) -> Unit)? = null
) : StructuredMemoryStore {
    private val mutex = Mutex()
    private val type = object : TypeToken<List<StructuredMemoryEntry>>() {}.type

    init {
        rootDir.mkdirs()
    }

    override suspend fun read(sessionId: String): List<StructuredMemoryEntry> {
        return mutex.withLock {
            val file = sessionFile(sessionId)
            if (!file.exists()) return@withLock emptyList()
            runCatching {
                gson.fromJson<List<StructuredMemoryEntry>>(file.readText(), type)
            }.onFailure { e -> onReadError?.invoke(file, e as Exception) }
                .getOrNull().orEmpty()
        }
    }

    override suspend fun write(sessionId: String, entries: List<StructuredMemoryEntry>) {
        mutex.withLock {
            val file = sessionFile(sessionId)
            if (entries.isEmpty()) {
                file.delete()
            } else {
                atomicWriteText(file, gson.toJson(entries, type))
            }
        }
    }

    override suspend fun clear(sessionId: String) {
        mutex.withLock {
            sessionFile(sessionId).delete()
        }
    }

    private fun sessionFile(sessionId: String): File = File(rootDir, "${safeId(sessionId)}.json")
}

class FileStructuredMemoryManager(
    rootDir: File,
    gson: Gson = Gson(),
    maxEntriesPerSession: Int = 400
) : DefaultStructuredMemoryManager(
    store = FileBackedStructuredMemoryStore(rootDir, gson),
    maxEntriesPerSession = maxEntriesPerSession
)

private fun atomicWriteText(target: File, content: String) {
    val tmp = File(target.parentFile, "${target.name}.tmp")
    tmp.writeText(content)
    if (!tmp.renameTo(target)) {
        target.delete()
        if (!tmp.renameTo(target)) {
            tmp.delete()
            target.writeText(content)
        }
    }
}

private fun safeId(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
