package com.example.watcher.data.repository

import com.example.watcher.data.local.CouncilExpertDao
import com.example.watcher.data.model.CouncilExpertDefaults
import com.example.watcher.data.model.CouncilExpertEntity
import java.util.UUID

class CouncilExpertRepository(private val dao: CouncilExpertDao) {
    val experts = dao.observeAll()

    suspend fun updateExpert(entity: CouncilExpertEntity) {
        val active = entity.enabled && entity.selectedForCouncil
        dao.upsert(
            entity.copy(
                enabled = active,
                selectedForCouncil = active,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun createExpert(): CouncilExpertEntity {
        val entity = CouncilExpertDefaults.newCustomExpert(sortOrder = dao.maxSortOrder() + 1)
        dao.upsert(entity)
        return entity
    }

    suspend fun importExpert(template: CouncilExpertEntity): CouncilExpertEntity {
        val now = System.currentTimeMillis()
        val imported = template.copy(
            expertId = "expert_import_${UUID.randomUUID().toString().take(8)}",
            enabled = false,
            selectedForCouncil = false,
            sortOrder = dao.maxSortOrder() + 1,
            isSystemPreset = false,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(imported)
        return imported
    }

    suspend fun duplicateExpert(entity: CouncilExpertEntity): CouncilExpertEntity {
        val now = System.currentTimeMillis()
        val duplicated = entity.copy(
            expertId = "expert_copy_${now.toString().takeLast(8)}",
            legacyRole = "",
            name = "${entity.name} 副本",
            isSystemPreset = false,
            selectedForCouncil = false,
            sortOrder = dao.maxSortOrder() + 1,
            createdAt = now,
            updatedAt = now
        )
        dao.upsert(duplicated)
        return duplicated
    }

    suspend fun deleteExpert(expertId: String) {
        dao.deleteByExpertId(expertId)
    }

    suspend fun restoreMissingSystemPresets(): Int {
        val existingIds = dao.getAll().mapTo(linkedSetOf()) { it.expertId }
        val missingPresets = CouncilExpertDefaults.all.filterNot { it.expertId in existingIds }
        missingPresets.forEach { preset ->
            dao.upsert(preset.toEntity())
        }
        return missingPresets.size
    }

    suspend fun resetExpert(expertId: String) {
        val current = dao.getByExpertId(expertId) ?: return
        val preset = CouncilExpertDefaults.findByExpertId(expertId) ?: return
        dao.upsert(
            preset.toEntity().copy(
                expertId = current.expertId,
                sortOrder = current.sortOrder
            )
        )
    }
}
