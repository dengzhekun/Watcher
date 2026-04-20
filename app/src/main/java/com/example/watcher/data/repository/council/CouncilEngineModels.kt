package com.example.watcher.data.repository.council

import com.example.watcher.data.model.CouncilExpertEntity
import com.example.watcher.data.model.CouncilExpertKind
import com.example.watcher.data.model.CouncilVoteLevel

data class CouncilExpertSpec(
    val expertId: String,
    val name: String,
    val description: String,
    val persona: String,
    val perspective: String,
    val expertKind: CouncilExpertKind = CouncilExpertKind.Specialist,
    val legacyRole: String = "",
    val providerId: String = "",
    val enabled: Boolean = true,
    val selectedForCouncil: Boolean = false,
    val sortOrder: Int = 0
)

internal data class CouncilOpinionPayload(
    val summary: String? = null,
    val findings: List<String>? = null,
    val risks: List<String>? = null,
    val nextActions: List<String>? = null,
    val observationRequests: List<String>? = null,
    val voteLevel: String? = null,
    val voteReason: String? = null,
    val confidence: Int? = null
)

internal data class CouncilReviewPayload(
    val agree: String? = null,
    val challenge: String? = null
)

internal data class CouncilDiscussionAskPayload(
    val toExpertId: String? = null,
    val question: String? = null,
    val reason: String? = null,
    val observationRequests: List<String>? = null,
    val skip: Boolean? = null
)

internal data class CouncilDiscussionReplyPayload(
    val answer: String? = null,
    val evidence: String? = null,
    val suggestion: String? = null
)

internal data class CouncilDiscussionSummaryPayload(
    val headline: String? = null,
    val agreements: List<String>? = null,
    val disagreements: List<String>? = null,
    val nextFocus: List<String>? = null
)

internal data class CouncilSynthesisPayload(
    val situationSummary: String? = null,
    val topFindings: List<String>? = null,
    val topRisks: List<String>? = null,
    val nextActions: List<String>? = null,
    val finalAdvice: String? = null
)

internal fun resolveCouncilExpertSpecs(
    storedExperts: List<CouncilExpertEntity>
): List<CouncilExpertSpec> {
    return storedExperts
        .sortedWith(compareBy<CouncilExpertEntity> { it.sortOrder }.thenBy { it.name.lowercase() })
        .map { it.toSpec() }
}

internal fun CouncilExpertEntity.toSpec(): CouncilExpertSpec {
    return CouncilExpertSpec(
        expertId = expertId,
        name = name,
        description = description,
        persona = promptPersona,
        perspective = perspective,
        expertKind = expertKind,
        legacyRole = legacyRole,
        providerId = providerId,
        enabled = enabled,
        selectedForCouncil = selectedForCouncil,
        sortOrder = sortOrder
    )
}

internal fun CouncilVoteLevel.severity(): Int = when (this) {
    CouncilVoteLevel.Pass -> 0
    CouncilVoteLevel.Watch -> 1
    CouncilVoteLevel.Warn -> 2
    CouncilVoteLevel.Alert -> 3
}
