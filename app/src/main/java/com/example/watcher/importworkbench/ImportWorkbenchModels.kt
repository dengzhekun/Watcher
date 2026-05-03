package com.example.watcher.importworkbench

enum class WorkbenchResourceState {
    RECEIVED,
    APPLIED,
    NEEDS_MANUAL_ACTION,
    FAILED
}

data class ImportActionTarget(
    val workspace: String,
    val route: String,
    val displayLabel: String
)

data class ImportedResourceInput(
    val resourceType: String,
    val resourceKey: String,
    val title: String,
    val summary: String,
    val received: Boolean,
    val applied: Boolean,
    val requiresManualAction: Boolean,
    val failedMessage: String?,
    val destination: ImportActionTarget,
    val detailLines: List<String> = emptyList()
)

data class ImportedResourceBatch(
    val sourceLabel: String,
    val importedAt: Long,
    val resources: List<ImportedResourceInput>
)

data class WorkbenchEntryCard(
    val resourceType: String,
    val resourceKey: String,
    val title: String,
    val summary: String,
    val state: WorkbenchResourceState,
    val actionTarget: ImportActionTarget,
    val detailLines: List<String>
)
