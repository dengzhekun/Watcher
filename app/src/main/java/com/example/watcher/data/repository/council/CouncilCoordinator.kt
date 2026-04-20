package com.example.watcher.data.repository.council

import com.example.watcher.data.model.CouncilAlert
import com.example.watcher.data.model.CouncilExpertOpinion
import com.example.watcher.data.model.CouncilVoteLevel

internal class CouncilCoordinator {
    fun buildAlert(opinions: List<CouncilExpertOpinion>): CouncilAlert? {
        if (opinions.isEmpty()) return null

        val alerts = opinions.filter { it.voteLevel == CouncilVoteLevel.Alert }
        val warnsOrAbove = opinions.filter { it.voteLevel.severity() >= CouncilVoteLevel.Warn.severity() }

        return when {
            alerts.size >= 2 -> CouncilAlert(
                level = CouncilVoteLevel.Alert,
                message = alerts.joinToString("；") { "${it.name}：${it.voteReason}" }.take(180),
                triggeredBy = alerts.map { it.name }
            )

            warnsOrAbove.size >= 3 -> CouncilAlert(
                level = CouncilVoteLevel.Warn,
                message = warnsOrAbove.joinToString("；") { "${it.name}：${it.voteReason}" }.take(180),
                triggeredBy = warnsOrAbove.map { it.name }.take(4)
            )

            opinions.any { it.voteLevel == CouncilVoteLevel.Watch } -> {
                val watchers = opinions.filter { it.voteLevel == CouncilVoteLevel.Watch }
                CouncilAlert(
                    level = CouncilVoteLevel.Watch,
                    message = watchers.joinToString("；") { "${it.name}：${it.voteReason}" }.take(180),
                    triggeredBy = watchers.map { it.name }.take(3)
                )
            }

            else -> null
        }
    }
}
