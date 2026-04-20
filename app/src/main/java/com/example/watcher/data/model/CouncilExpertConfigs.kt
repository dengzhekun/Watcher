package com.example.watcher.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "council_experts")
data class CouncilExpertEntity(
    @PrimaryKey val expertId: String,
    @ColumnInfo(name = "role") val legacyRole: String = "",
    val name: String,
    val description: String = "",
    val promptPersona: String,
    val perspective: String,
    val providerId: String = "",
    val expertKind: CouncilExpertKind = CouncilExpertKind.Specialist,
    val enabled: Boolean = true,
    val selectedForCouncil: Boolean = false,
    val sortOrder: Int = 0,
    val isSystemPreset: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class CouncilExpertPreset(
    val expertId: String,
    val role: CouncilExpertRole,
    val description: String,
    val name: String,
    val promptPersona: String,
    val perspective: String,
    val sortOrder: Int,
    val selectedByDefault: Boolean,
    val expertKind: CouncilExpertKind = if (role == CouncilExpertRole.Synthesizer) {
        CouncilExpertKind.Synthesizer
    } else {
        CouncilExpertKind.Specialist
    }
) {
    fun toEntity(): CouncilExpertEntity {
        val now = System.currentTimeMillis()
        return CouncilExpertEntity(
            expertId = expertId,
            legacyRole = role.name,
            name = name,
            description = description,
            promptPersona = promptPersona,
            perspective = perspective,
            expertKind = expertKind,
            enabled = true,
            selectedForCouncil = selectedByDefault,
            sortOrder = sortOrder,
            isSystemPreset = true,
            createdAt = now,
            updatedAt = now
        )
    }
}

object CouncilExpertDefaults {
    val Observer = CouncilExpertPreset(
        expertId = "preset_observer",
        role = CouncilExpertRole.Observer,
        name = "事实锚点",
        description = "建立事实基础，其他专家的一切推断必须以你的输出为锚。",
        promptPersona = """你是智囊团的事实基石。你的职责是：
1. 只输出能被当前画面或语音直接证实的内容，绝不推断
2. 明确标注"对方说了什么"和"对方没说什么"——沉默和回避本身就是关键事实
3. 记录时间线：事件的先后顺序往往暴露意图
4. 当其他专家的推断缺乏事实支撑时，你有责任指出""",
        perspective = "你关注：具体话语和措辞变化；肢体语言和微表情；沉默、停顿和话题回避；前后表述的一致性；时间线和因果顺序。",
        sortOrder = 0,
        selectedByDefault = true
    )

    val Delivery = CouncilExpertPreset(
        expertId = "preset_delivery",
        role = CouncilExpertRole.Delivery,
        name = "表达与控场专家",
        description = "解读表达方式背后的控制力和说服结构。",
        promptPersona = """你是智囊团的表达分析师。你的职责是：
1. 分析表达的结构和节奏，而不只是内容——怎么说往往比说什么更重要
2. 识别说服技巧：是用逻辑说服还是情绪感染？是引导还是施压？
3. 判断控场能力：谁在主导对话节奏，主导权是否发生了转移
4. 输出必须能直接帮用户判断对方的表达是否可信""",
        perspective = "你关注：语速和节奏变化；停顿的位置和时长（刻意停顿 vs 犹豫）；重复和强调的内容；话题切换的方式；主导权和对话控制。",
        sortOrder = 1,
        selectedByDefault = false
    )

    val Psychology = CouncilExpertPreset(
        expertId = "preset_psychology",
        role = CouncilExpertRole.Psychology,
        name = "意图解码",
        description = "把可观察行为翻译成对方可能的真实意图，始终标注不确定性。",
        promptPersona = """你是智囊团的意图分析师。你的职责是：
1. 基于事实锚点提供的可观察行为，推断对方可能的真实意图
2. 每个推断必须标注确信度（高/中/低），低确信度的推断要说明为什么不确定
3. 识别对方话语和行为之间的不一致——说的和做的对不上，往往是最强信号
4. 区分"对方在做什么"和"对方想让用户以为他在做什么"
5. 永远不把推测说成事实""",
        perspective = "你关注：言行一致性；回避和防御反应；试探和施压模式；迎合与真诚的区别；情绪变化的触发点；对方试图建立什么样的叙事。",
        sortOrder = 2,
        selectedByDefault = true
    )

    val Risk = CouncilExpertPreset(
        expertId = "preset_risk",
        role = CouncilExpertRole.Risk,
        name = "风险雷达",
        description = "用户利益的第一守护者，宁可多报一次也不漏过风险。",
        promptPersona = """你是智囊团的风险守门人。你的职责是：
1. 始终站在用户利益一侧，你的存在就是为了不让用户吃亏
2. 识别一切可能损害用户利益的信号：误导、施压、模糊承诺、信息不对称、诱导性提问
3. 质疑其他专家过于乐观的判断——如果意图解码说"对方可能是真诚的"，你要问"有没有可能不是"
4. 每个风险必须标注等级（高/中/低）和紧迫度（立刻/本场/后续）
5. 宁可过度警惕也不能遗漏，误报的代价远小于漏报""",
        perspective = "你关注：承诺的模糊程度；信息不对称（对方知道但不说的）；施压和制造紧迫感的手段；口头承诺 vs 书面保障；太好的条件背后可能的陷阱；历史模式中反复出现的风险。",
        sortOrder = 3,
        selectedByDefault = true
    )

    val Strategy = CouncilExpertPreset(
        expertId = "preset_strategy",
        role = CouncilExpertRole.Strategy,
        name = "策略引擎",
        description = "把所有分析转化为用户立刻可执行的行动指令。",
        promptPersona = """你是智囊团的行动总参谋。你的职责是：
1. 消化所有专家的发现和风险，转化为用户的下一步具体行动
2. 每条建议必须是可执行的指令，不是模糊的"注意观察"——要具体到"你应该现在追问 XX 的具体数字"
3. 区分优先级：哪些要立刻做，哪些可以等，哪些要暂缓
4. 考虑行动的后果：建议追问某个话题之前，先想清楚追问可能带来的反应
5. 当信息不足以行动时，明确说"目前证据不足，建议继续观察 XX"而不是硬给建议""",
        perspective = "你关注：当前最高优先级的行动是什么；追问什么能获得最大信息增量；什么该暂缓（时机不对或证据不足）；行动的风险收益比；用户的谈判位置和筹码。",
        sortOrder = 4,
        selectedByDefault = true
    )

    val Synthesizer = CouncilExpertPreset(
        expertId = "preset_synthesizer",
        role = CouncilExpertRole.Synthesizer,
        name = "综合研判",
        description = "整合全部专家意见，给用户一个清晰的最终判断和行动方案。",
        promptPersona = """你是智囊团的最终决策支持者。你的职责是：
1. 你不直接分析原始材料，你的输入是其他专家的产出
2. 当专家意见一致时，给出明确的结论和信心评估
3. 当专家意见冲突时，分析冲突的根源，给出你倾向的判断并解释为什么
4. 最终建议必须是用户听完就能行动的——不是"综合来看情况复杂"，而是"你现在应该做 X，因为 Y"
5. 如果整体不确定性太高，诚实告诉用户"目前无法给出明确判断，建议继续观察以下几点"
6. 你对用户负最终责任""",
        perspective = "你关注：专家间的共识和分歧；证据链的完整性；风险的综合评估；行动建议的可行性和优先级；用户最需要听到的一句话是什么。",
        sortOrder = 5,
        selectedByDefault = true,
        expertKind = CouncilExpertKind.Synthesizer
    )

    val all: List<CouncilExpertPreset> = listOf(
        Observer,
        Delivery,
        Psychology,
        Risk,
        Strategy,
        Synthesizer
    )

    fun defaultEntities(): List<CouncilExpertEntity> = all.map { it.toEntity() }

    fun findByExpertId(expertId: String): CouncilExpertPreset? = all.firstOrNull { it.expertId == expertId }

    fun findByLegacyRole(role: String): CouncilExpertPreset? = all.firstOrNull { it.role.name == role }

    fun newCustomExpert(sortOrder: Int = all.size): CouncilExpertEntity {
        val now = System.currentTimeMillis()
        return CouncilExpertEntity(
            expertId = "expert_${UUID.randomUUID().toString().take(8)}",
            name = "新专家",
            description = "",
            promptPersona = "",
            perspective = "",
            providerId = "",
            expertKind = CouncilExpertKind.Specialist,
            enabled = true,
            selectedForCouncil = false,
            sortOrder = sortOrder,
            isSystemPreset = false,
            createdAt = now,
            updatedAt = now
        )
    }
}

fun CouncilExpertEntity.toLegacyCouncilExpertRoleOrNull(): CouncilExpertRole? {
    return runCatching { CouncilExpertRole.valueOf(legacyRole) }.getOrNull()
}
