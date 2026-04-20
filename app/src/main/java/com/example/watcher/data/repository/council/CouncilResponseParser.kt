package com.example.watcher.data.repository.council

import com.example.watcher.data.model.CouncilDiscussionSummary
import com.example.watcher.data.model.CouncilDiscussionTurn
import com.example.watcher.data.model.CouncilExpertOpinion
import com.example.watcher.data.model.CouncilSynthesis
import com.example.watcher.data.model.CouncilVoteLevel
import com.google.gson.Gson

internal class CouncilResponseParser {
    private val gson = Gson()

    fun parseOpinion(spec: CouncilExpertSpec, raw: String): CouncilExpertOpinion {
        val payload = runCatching {
            gson.fromJson(raw, CouncilOpinionPayload::class.java)
        }.getOrNull()

        return CouncilExpertOpinion(
            expertId = spec.expertId,
            name = spec.name,
            expertKind = spec.expertKind,
            legacyRole = spec.legacyRole,
            summary = payload?.summary.cleanOrFallback("暂无明确结论"),
            findings = payload?.findings.cleanList(4),
            risks = payload?.risks.cleanList(4),
            nextActions = payload?.nextActions.cleanList(4),
            observationRequests = payload?.observationRequests.cleanList(3),
            voteLevel = CouncilVoteLevel.fromRaw(payload?.voteLevel),
            voteReason = payload?.voteReason.cleanOrFallback("当前未发现足够强的即时提醒信号"),
            confidence = (payload?.confidence ?: 50).coerceIn(0, 100)
        )
    }

    fun applyReview(opinion: CouncilExpertOpinion, raw: String): CouncilExpertOpinion {
        val payload = runCatching {
            gson.fromJson(raw, CouncilReviewPayload::class.java)
        }.getOrNull()
        return opinion.copy(
            agree = payload?.agree.cleanOrFallback(""),
            challenge = payload?.challenge.cleanOrFallback(""),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun parseDiscussionAsk(
        raw: String,
        targetExpertIds: Set<String>
    ): ParsedDiscussionAsk? {
        val payload = runCatching {
            gson.fromJson(raw, CouncilDiscussionAskPayload::class.java)
        }.getOrNull() ?: return null

        if (payload.skip == true) {
            return null
        }

        val toExpertId = payload.toExpertId
            ?.trim()
            ?.takeIf { it.isNotBlank() && it in targetExpertIds }
            ?: return null
        val question = payload.question.cleanOrFallback("")
        if (question.isBlank()) {
            return null
        }
        return ParsedDiscussionAsk(
            toExpertId = toExpertId,
            question = question,
            reason = payload.reason.cleanOrFallback(""),
            observationRequests = payload.observationRequests.cleanList(3)
        )
    }

    fun parseDiscussionReply(raw: String): ParsedDiscussionReply {
        val payload = runCatching {
            gson.fromJson(raw, CouncilDiscussionReplyPayload::class.java)
        }.getOrNull()
        return ParsedDiscussionReply(
            answer = payload?.answer.cleanOrFallback("我先补充到这里，建议综合器结合其他专家意见再判断。"),
            evidence = payload?.evidence.cleanOrFallback(""),
            suggestion = payload?.suggestion.cleanOrFallback("")
        )
    }

    fun parseDiscussionSummary(raw: String): CouncilDiscussionSummary {
        val payload = runCatching {
            gson.fromJson(raw, CouncilDiscussionSummaryPayload::class.java)
        }.getOrNull()
        return CouncilDiscussionSummary(
            headline = payload?.headline.cleanOrFallback("讨论已形成阶段性结论"),
            agreements = payload?.agreements.cleanList(4),
            disagreements = payload?.disagreements.cleanList(4),
            nextFocus = payload?.nextFocus.cleanList(4)
        )
    }

    fun parseSynthesis(raw: String): CouncilSynthesis {
        val payload = runCatching {
            gson.fromJson(raw, CouncilSynthesisPayload::class.java)
        }.getOrNull()
        return CouncilSynthesis(
            situationSummary = payload?.situationSummary.cleanOrFallback("暂无整合结论"),
            topFindings = payload?.topFindings.cleanList(5),
            topRisks = payload?.topRisks.cleanList(5),
            nextActions = payload?.nextActions.cleanList(5),
            finalAdvice = payload?.finalAdvice.cleanOrFallback("继续观察，并围绕关键疑点追问。")
        )
    }

    fun buildReplyDetail(reply: ParsedDiscussionReply): String {
        return listOf(reply.evidence, reply.suggestion)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(240)
    }

    fun turnsDigest(turns: List<CouncilDiscussionTurn>, limit: Int = 10): String {
        return turns.takeLast(limit).joinToString("\n") { turn ->
            val target = turn.toExpertName.takeIf { it.isNotBlank() }?.let { " @$it" }.orEmpty()
            val detail = turn.detail.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty()
            "[${turn.kind.label}] ${turn.fromExpertName}$target: ${turn.message}$detail"
        }
    }

    private fun String?.cleanOrFallback(fallback: String): String {
        return this?.trim()?.takeIf { it.isNotBlank() }?.take(220) ?: fallback
    }

    private fun List<String>?.cleanList(limit: Int): List<String> {
        return this.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit)
    }
}

internal data class ParsedDiscussionAsk(
    val toExpertId: String,
    val question: String,
    val reason: String,
    val observationRequests: List<String> = emptyList()
)

internal data class ParsedDiscussionReply(
    val answer: String,
    val evidence: String,
    val suggestion: String
)
