package com.example.watcher.importworkbench

object ImportWorkbenchContract {
    fun toWorkbenchCards(batch: ImportedResourceBatch): List<WorkbenchEntryCard> {
        return batch.resources.map { input ->
            WorkbenchEntryCard(
                resourceType = input.resourceType,
                resourceKey = input.resourceKey,
                title = input.title,
                summary = input.summary,
                state = resolveState(input),
                actionTarget = input.destination,
                detailLines = buildDetails(batch, input)
            )
        }
    }

    private fun resolveState(input: ImportedResourceInput): WorkbenchResourceState {
        return when {
            !input.failedMessage.isNullOrBlank() -> WorkbenchResourceState.FAILED
            input.applied -> WorkbenchResourceState.APPLIED
            input.requiresManualAction -> WorkbenchResourceState.NEEDS_MANUAL_ACTION
            input.received -> WorkbenchResourceState.RECEIVED
            else -> WorkbenchResourceState.RECEIVED
        }
    }

    private fun buildDetails(
        batch: ImportedResourceBatch,
        input: ImportedResourceInput
    ): List<String> {
        return buildList {
            add("source: ${batch.sourceLabel}")
            add("imported_at: ${batch.importedAt}")
            input.failedMessage?.takeIf { it.isNotBlank() }?.let { add("error: $it") }
        }
    }
}
