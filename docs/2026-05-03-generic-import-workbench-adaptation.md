# Watcher 通用导入工作台适配说明

## 目标

Watcher 侧已经从单一的 XMAX Provider 导入，推进到“通用导入工作台”模式。

目标不是让外部项目直接改 Watcher 内部数据库，而是让外部项目输出一份稳定、可校验、可回显的导入 payload，由 Watcher 负责：

1. 导入 Provider。
2. 尝试自动注册 Agent。
3. 将 AI 观众、专家团等资源放入统一工作台，等待人工确认或后续自动落库。
4. 在工作台内给出每一类资源的状态、详情和下一步动作。

## 当前资源模型

Watcher 当前按以下资源类型组织工作台：

1. `provider`
2. `agent`
3. `audience_group`
4. `expert_council`

每个资源在工作台中都对应一张卡片，卡片包含：

- `resourceType`: 资源类型
- `resourceKey`: 外部资源主键或稳定标识
- `title`: 展示标题
- `summary`: 当前状态摘要
- `detailLines`: 可展开的详情
- `destination`: 建议用户下一步去哪里处理

## 状态语义

Watcher 工作台当前使用以下状态：

1. `APPLIED`: 已落库或已自动接入。
2. `RECEIVED`: 已收到，但还没自动落地。
3. `NEEDS_MANUAL_ACTION`: 已收到，但明确需要人工确认。
4. `FAILED`: 自动处理失败，需要人工修复。

建议外部项目按下面的语义理解：

- Provider 导入成功后一般应为 `APPLIED`。
- Agent 如果能自动注册，标记为 `APPLIED`；不能自动注册时标记为 `FAILED` 或 `NEEDS_MANUAL_ACTION`。
- AI 观众、专家团这类跨系统资源，默认按 `NEEDS_MANUAL_ACTION` 处理更稳。

## 动作目标

Watcher 当前支持的动作目标是：

1. `api_wallet`: 打开 API 钱包。
2. `agent_config`: 打开 Agent 配置页。
3. `template_management`: 打开模板/隐藏工作台入口。

外部项目如果要与 Watcher 对齐，不要发明新的 route 名称，优先复用这三个。

如果必须新增 route，需同步更新 Watcher 侧动作映射，否则工作台只会显示资源，不会提供有效跳转。

## 外部项目导出 payload 的最小要求

最小 Provider 导入字段：

```json
{
  "providerId": "xmax_main_chat",
  "providerName": "X-MAX 主站",
  "endpoint": "https://api.example.com/v1",
  "apiKey": "sk-xxx",
  "modelName": "gpt-5.5",
  "enabled": true,
  "makeDefault": true,
  "sourceSiteName": "主站",
  "sourceModelMode": "聊天模型"
}
```

如果外部项目还想让 Watcher 接住 Agent / AI 观众 / 专家团，需要继续补这些区块：

```json
{
  "agentConfig": {
    "enabled": true,
    "agentId": "watcher_agent",
    "agentName": "Watcher Agent",
    "systemPrompt": "接住任务",
    "entryPoint": "watcher://agent/main"
  },
  "audienceConfig": {
    "enabled": true,
    "roomName": "观察席",
    "focusPrompt": "关注风险",
    "responseStyle": "短句"
  },
  "expertCouncilConfig": {
    "enabled": true,
    "topic": "联调复盘",
    "memberRoles": ["产品", "技术"],
    "workflow": "先分歧后结论"
  }
}
```

## 外部项目实现建议

### 1. 对导入能力做显式声明

外部项目建议在自己的文档或设置页明确写出：

- 支持导出 Watcher/通用工作台 payload
- 当前支持哪些资源类型
- 哪些资源只是“传过去待确认”，哪些能“自动落库”

### 2. 保持主键稳定

以下字段必须稳定，不能每次随机生成：

- `providerId`
- `agentConfig.agentId`
- `audienceConfig.roomName` 或你定义的稳定 audience key
- `expertCouncilConfig.topic` 或你定义的稳定 council key

否则 Watcher 只能把每次导入视为全新资源。

### 3. 明确自动接入边界

建议外部项目把资源分成两类：

1. 可以自动接入的：Provider、部分 Agent。
2. 需要人工确认的：AI 观众、专家团、复杂工作流。

这样接入更稳，失败面更小。

### 4. 导入结果要回显

外部项目发起导入后，应读取 Watcher 返回的：

- `RESULT_MESSAGE`
- `RESULT_PAYLOAD`

并把结果展示给用户，而不是只提示“已发送”。

## 给第三方作者的复制提示词

下面这段可以直接发给外部项目作者：

```text
请为你的项目增加“导出到 Watcher 通用导入工作台”的能力。

目标：
1. 输出一份稳定 JSON payload，由 Watcher 导入。
2. 至少支持 Provider 导出。
3. 如果你项目里有 Agent、AI 观众、专家团、专家角色、工作流，也一并映射到 payload。
4. 不要直接依赖 Watcher 私有数据库；通过导入 payload 对接。

请按下面规则实现：

- Provider 必填字段：
  - providerId
  - providerName
  - endpoint
  - apiKey
  - modelName
  - enabled
  - makeDefault
  - sourceSiteName
  - sourceModelMode

- Agent 可选字段：
  - agentConfig.enabled
  - agentConfig.agentId
  - agentConfig.agentName
  - agentConfig.systemPrompt
  - agentConfig.entryPoint

- AI 观众可选字段：
  - audienceConfig.enabled
  - audienceConfig.roomName
  - audienceConfig.focusPrompt
  - audienceConfig.responseStyle

- 专家团可选字段：
  - expertCouncilConfig.enabled
  - expertCouncilConfig.topic
  - expertCouncilConfig.memberRoles
  - expertCouncilConfig.workflow

兼容要求：
- endpoint 必须默认输出 https。
- 所有资源主键必须稳定，不能每次随机。
- 对暂时无法自动落库的资源，也要输出到 payload，让 Watcher 工作台能显示为“待人工处理”。
- 导出后，请展示 Watcher 返回的 result message / result payload，方便用户知道哪些资源成功、哪些只是收到待处理。

请同时补一份文档，说明：
- 你导出了哪些资源
- 哪些资源会在 Watcher 里自动生效
- 哪些资源进入通用工作台等待确认
```

## 后续建议

如果要继续把这条链路做成更通用的协议，下一步建议补：

1. 外部 payload 的显式版本号。
2. 更通用的 `resourceKey` / `resourceId` 规则。
3. 对 `audience_group` / `expert_council` 的正式落库接口。
4. 对未知 `route` 的能力声明与协商机制。
