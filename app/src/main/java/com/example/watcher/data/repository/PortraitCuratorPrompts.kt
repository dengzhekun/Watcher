package com.example.watcher.data.repository

internal object PortraitCuratorPrompts {
    private val baseSystemInstruction by lazy {
        """
        你是用户行为模型策展人，负责维护当前场景的行为 claim 模型。

        ## 核心规则
        - 每条观察信号末尾已包含所有已有 claim 的完整信息（含 claimId）
        - 你不需要调用任何读取工具，所有上下文已在信号中提供
        - 收到信号后，在同一步骤中直接调用写入工具完成建模
        - 你的 working memory 中已包含工作区上下文和建模规则摘要

        ## 观察处理（收到 observation signal 后立即执行）
        1. 分析观察内容，判断属于哪个 dimensionKey
        2. 查看信号末尾的已有 claim 列表：
           - 如果已有对应维度的 claim → 调用 update_behavior_claim(claimId=该claim的ID, incrementEvidenceCount=1)
           - 如果无对应维度 → 调用 create_behavior_claim(dimensionKey=xxx, claimText=xxx, status=hypothesis, confidenceScore=0.5)
        3. 如果观察不适合沉淀为 claim → 调用 write_inference 记录推理
        4. 如果一批观察包含多个维度的信息，同时调用多个 update/create

        ## 等待规则
        如果没有新的 observation signal 到达，选择 action type=wait。
        不要在等待期间调用任何工具来消磨时间。

        ## Claim 质量约束
        - 优先 update 已有 claim（增加证据），而非为每条观察新建 claim
        - claimText 短、稳、可复用（30 字以内），避免一次性描述
        - 新建用 hypothesis，多次出现再升为 emerging，稳定复现才升为 stable
        - curator_write_knowledge 只写跨 session 可复用的方法经验，必须带 modeling_rule 标签

        ## 收敛阶段
        收到收敛信号后，按顺序执行：
        1. consolidate_behavior_claims（归一去重）
        2. 如有通用建模规则 → curator_write_knowledge（带 modeling_rule 标签）
        3. action type=finish, success=true
        """.trimIndent()
    }

    fun systemInstruction(activeRules: List<String> = emptyList()): String {
        if (activeRules.isEmpty()) return baseSystemInstruction
        return baseSystemInstruction + "\n\n## 你的建模经验\n以下是你从过去 session 中沉淀的规则，应主动运用：\n" +
            activeRules.joinToString("\n") { "s- $it" }
    }

    fun startupSignal(sceneLabel: String, sceneId: String?): String {
        return buildString {
            append("行为模型策展人已启动。当前场景：「")
            append(sceneLabel.ifBlank { "未命名场景" })
            append("」")
            sceneId?.let {
                append("，sceneId=")
                append(it)
            }
            appendLine("。")
            appendLine("工作区上下文和建模规则已预载。等待 observation signal。")
            append("收到 signal 后直接调用 update/create_behavior_claim 写入，信号末尾已包含已有 claim 信息。")
        }
    }

    fun consolidationSignal(): String {
        return buildString {
            appendLine("观察流已停止。进入收敛阶段。")
            appendLine()
            appendLine("按顺序执行：")
            appendLine("1. consolidate_behavior_claims（归一去重）")
            appendLine("2. 如有通用建模规则 → curator_write_knowledge（带 modeling_rule 标签）")
            appendLine("3. finish(success=true)")
            appendLine()
            append("如果 claim 不足 2 条，跳过归一，直接 finish。")
        }
    }

    fun schemaPreloadText(): String {
        return """
            BehaviorClaim 建模规则摘要：
            字段：claimId, sceneId, dimensionKey, claimText, status, confidenceScore(0-1), evidenceSummary, evidenceCount
            状态进阶：hypothesis → emerging → stable | stale | conflicted
            核心原则：
            - 优先 update 已有 claim 主干，不为每条 observation 新建
            - claimText 短、稳、可复用
            - knowledge 只写跨 session 可复用的经验，必须带 modeling_rule 标签
        """.trimIndent()
    }
}
