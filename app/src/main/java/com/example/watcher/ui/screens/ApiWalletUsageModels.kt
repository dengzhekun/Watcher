package com.example.watcher.ui.screens

import com.example.watcher.data.model.LlmProviderEntity

internal enum class ProviderUsageState {
    Active,
    Selectable,
    Fallback,
    NotIntegrated
}

internal data class ProviderUsageProbe(
    val feature: String,
    val state: ProviderUsageState,
    val detail: String
)

internal fun maskApiKey(apiKey: String): String {
    if (apiKey.isBlank()) return "API 密钥为空"
    if (apiKey.length <= 8) return "密钥：${"*".repeat(apiKey.length)}"
    return "密钥：${apiKey.take(4)}****${apiKey.takeLast(4)}"
}

internal fun buildUsageProbes(
    provider: LlmProviderEntity,
    isDefault: Boolean
): List<ProviderUsageProbe> {
    val enabledDefault = provider.enabled && isDefault
    val arkCompatible = provider.isArkResponsesCompatible()

    fun openAiDefaultProbe(feature: String): ProviderUsageProbe {
        return when {
            enabledDefault -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Active,
                detail = "这个功能会在调用时解析当前钱包默认项，因此现在就会使用这个供应商。"
            )

            provider.enabled -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Selectable,
                detail = "将这个供应商设为默认后，该功能就会通过它的接口地址、API 密钥和模型发起请求。"
            )

            else -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Fallback,
                detail = "禁用的供应商会被全局钱包解析跳过，因此该功能不会自动选中它。"
            )
        }
    }

    fun arkDefaultProbe(feature: String): ProviderUsageProbe {
        return when {
            enabledDefault && arkCompatible -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Active,
                detail = "这个兼容 Ark 的供应商当前就是默认项，因此该功能现在可以直接使用它。"
            )

            enabledDefault && !arkCompatible -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Fallback,
                detail = "这个功能要求接口兼容 Ark Responses，因此会回退到本地 API_KEY。"
            )

            provider.enabled && arkCompatible -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Selectable,
                detail = "将这个供应商设为默认后，这个兼容 Ark 的功能就可以走它。"
            )

            provider.enabled && !arkCompatible -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Fallback,
                detail = "即便设为默认，这个功能仍会回退，因为该接口不兼容 Ark Responses。"
            )

            else -> ProviderUsageProbe(
                feature = feature,
                state = ProviderUsageState.Fallback,
                detail = "这个供应商已禁用全局解析，因此该功能无法自动选中它。"
            )
        }
    }

    return listOf(
        ProviderUsageProbe(
            feature = "已保存 Brain 绑定",
            state = ProviderUsageState.Selectable,
            detail = "Agent Config 可以把已保存的 Brain 直接绑定到这个供应商，而不必把它设为全局默认。"
        ),
        openAiDefaultProbe("默认 Agent 与 Brain 测试"),
        openAiDefaultProbe("智囊团配置生成器"),
        arkDefaultProbe("意图解析"),
        arkDefaultProbe("实时监控"),
        arkDefaultProbe("直播解说视频"),
        arkDefaultProbe("解说与场景记忆"),
        ProviderUsageProbe(
            feature = "视频流程",
            state = ProviderUsageState.NotIntegrated,
            detail = "这条流程目前仍直接读取本地 API_KEY 和固定 Ark 模型，尚未接入 ApiWallet。"
        )
    )
}

private fun LlmProviderEntity.isArkResponsesCompatible(): Boolean {
    val normalized = endpoint
        .trim()
        .lowercase()
        .removeSuffix("/")
    return normalized.isBlank() ||
        normalized.contains("ark.cn-beijing.volces.com") ||
        normalized.endsWith("/api/v3") ||
        normalized.endsWith("/api/v3/responses")
}
