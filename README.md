# Watcher

Watcher 是一个运行在 Android 设备上的本地视频观察与 AI 工作台。

它不是只做“连上摄像头看看画面”，也不是只做“把一段视频上传给模型问问题”。Watcher 想做的是另一件事：把实时视频流、持续观察、结构化分析、直播互动、多 Agent 协作、用户行为建模和本地模型实验，统一进同一个长期运行的系统里。

如果你把它当成一个产品去理解，它更接近：

- 一个可以持续盯住现场的视频观察系统
- 一个可以把视频流变成 AI 直播间的互动引擎
- 一个可以让多角色 AI 围绕同一画面协同研判的智囊团系统
- 一个可以把历史事实继续沉淀成长期画像和记忆的行为建模工作台

## 一句话理解

Watcher 是一个围绕同一条视频流持续工作的 AI 观察系统。

它会同时处理三类事情：

1. 当下正在发生什么：实时监控、实时解说、实时互动、实时告警。
2. 一段时间里发生了什么：分段录制、分段分析、最终汇总、时间线回看。
3. 长期来看意味着什么：历史沉淀、行为模式、画像整理、Agent 记忆和知识演化。

所以它不是一次性推理工具，而是“持续观察 + 持续分析 + 持续沉淀”的组合体。

## 这个项目想解决什么问题

大多数视频 AI Demo 只覆盖单点能力：

- 要么只能实时看，缺少结构化结论和历史沉淀
- 要么只能离线分析，不能围绕实时流持续工作
- 要么只有单个 AI 回答，没有多角色协作
- 要么只有云端接口，没有端侧模型和本地实验空间
- 要么只有功能，没有统一的数据、配置和 Agent 运行底座

Watcher 想把这些断开的环节连起来，形成一条完整链路：

`实时视频流 -> 持续观察 -> 实时互动/多角色分析 -> 历史沉淀 -> 行为建模 -> Agent 与模型持续进化`

这也是它和普通“监控 App”“视频理解 Demo”“聊天式 AI 助手”最不一样的地方。

## 能力地图

Watcher 目前最核心的能力，可以分成 `6` 个主功能模块和 `4` 个系统级管理能力。

### 六大核心功能模块

#### 1. 实时监控

这是 Watcher 最直接也最基础的能力。系统会围绕实时视频流持续执行监控任务，而不是只在用户点击一次按钮后分析一帧。

你能得到的能力：

- 接入 MJPEG 视频流，默认兼容 `ESP32-CAM`
- 用自然语言描述监控目标，而不是手工写固定规则
- 支持巡检间隔、基线图、变化检测、告警冷却、截图留证
- 支持会话录制、事件截图和运行日志
- 支持补光灯、设备信息读取、局域网扫描和基础配网辅助

这部分的价值不只是“发现异常”，而是把异常发现过程和证据链一起保存下来，方便后续复盘和二次分析。

#### 2. 视频流分析

Watcher 并不把视频分析理解为“上传一个完整视频，等模型返回一句话结论”。它更偏向长期场景中的结构化工作流。

当前视频分析能力的核心链路是：

- 根据自然语言需求生成执行计划
- 将长时观察拆成多个分段
- 按配置持续录制片段
- 每个片段单独分析
- 最终再做全局汇总

你能看到的结果包括：

- 分段摘要
- 时间线事件
- 阶段性流式输出
- 最终结论
- 每段视频的本地回看记录

它适合做的事情包括：

- 长时间场景观察
- 周期性行为巡检
- 某一类事件的长程复盘
- 不方便人工反复回看整段视频的场景

#### 3. AI 直播玩法

Watcher 的一个很特别的方向，是把视频流变成“可被 AI 参与的直播现场”。

在横屏沉浸式 `Live` 模式下，系统不只是显示画面，还会围绕画面形成实时解说与互动：

- 实时视频解说
- 语音识别触发
- AI 观众互动
- 不同互动模式下的现场反馈

当前已经具备两类观众引擎：

- `Classic AI Audience`
- `Agent Audience`

这意味着 Watcher 不只是“看懂视频”，而是在尝试把“视频理解”转成一种更有表达感、更像直播间的 AI 体验。

#### 4. AI 智囊团模式

`Council Mode` 是 Watcher 最有辨识度的功能之一。

它不是让一个模型对画面输出一个答案，而是让多个专家角色围绕同一画面、同一语音上下文、同一任务目标进行并行研判，然后再给出综合判断。

这个模块当前强调的是：

- 多专家并行分析
- 不同角色给出不同角度意见
- 讨论过程和综合结论都可见
- 支持模板化的专家配置和场景类型
- 更适合复杂现场、告警研判、访谈/演讲/会议等需要多视角判断的场景

从产品形态上看，它更像一个“围绕视频现场工作的 AI 专家小组”，而不是简单的多模型轮询。

#### 5. 用户行为建模

Watcher 不满足于只告诉你“刚才发生了什么”，它还在继续向“这个人/这个场景长期表现出什么模式”推进。

这部分能力主要通过 `Digital Life Card` 工作台来承载。当前相关能力包括：

- Blackboard 式事实沉淀
- 场景档案管理
- 行为 claim 管理
- 推理日志沉淀
- 观察目标管理
- 画像整理与再生成

它试图解决的问题是：

- 如何把一次次观察结果变成可积累的行为事实
- 如何把跨天、跨场景的信息拼起来
- 如何让长期行为模式不在每次会话后被丢掉

这让 Watcher 开始从“视频 AI 工具”往“长期认知系统”演化。

#### 6. 本地大模型

Watcher 不是纯云端架构。项目里已经给端侧模型留出了独立入口和运行链路。

当前 `LiteRT` 相关能力包括：

- 本地模型安装
- 模型下载
- 本地装载和重新加载
- 后端配置管理
- 基础多模态对话
- 启动时尝试自动初始化已配置的本地模型引擎

这部分很重要，因为它意味着：

- 项目不完全依赖远程 provider
- 可以做更贴近设备侧的实验
- 后续 Agent 和本地模型之间可以形成更直接的耦合方式

### 四个系统级管理能力

上面六个模块是“业务能力”，下面四个模块决定了 Watcher 有没有成为“系统”的资格。

#### 1. 统一历史数据管理

Watcher 不把监控记录和视频分析记录拆成互相隔离的页面，而是尽量往统一历史工作台收口。

这里的价值是：

- 统一回看实时监控和视频分析结果
- 统一查看截图、录像、分段媒资和最终结论
- 统一承接证据链，而不是只保留一句摘要
- 让一次运行结果可以被后续复盘、建模和模板化使用

如果没有这一层，前面的很多 AI 能力最后都会沦为“看完即丢”的瞬时输出。

#### 2. 统一配置中心

Watcher 里的很多能力都不是孤立运行的：视频流、设备、专家模板、运行参数、任务配置之间是相互关联的。

因此项目一直在做的事情，是把这些配置往统一管理收口，而不是让每个页面各自维护一套配置状态。

这层统一配置的重要性在于：

- 降低多功能产品的理解成本
- 保证相同视频流可被多个能力复用
- 让模板、专家和运行参数可以持续演进
- 让系统具备更强的“工作台”属性，而不是“功能集合”属性

#### 3. 全局 API Key 钱包

`API Wallet` 是 Watcher 里非常关键的一层基础设施。

它负责：

- 统一管理 OpenAI-compatible provider
- 管理 endpoint、模型来源和连接测试
- 承担全局 provider 配置入口
- 为 Agent、视频分析、直播玩法等上层能力提供稳定模型来源

这层设计的意义在于：

- 不让模型配置散落在不同页面
- 支持多个 provider 共存
- 让 Agent 可以绑定单独的 Brain，而不一定依赖全局默认 provider

#### 4. 独立 Agent 框架

Watcher 不是只在业务代码里临时塞几个 Agent。它已经在代码层抽出了相对独立的 `Agent Framework`。

这套框架当前关注的是：

- Agent 注册与身份管理
- Brain 与连接测试
- 运行时生命周期
- Memory 与 Knowledge 管理
- Autonomous Runtime
- Multi-agent 协作与外部调用边界

这意味着项目中的 Agent 能力，不再只是页面内的临时交互，而是开始具备独立运行时、独立存储和独立演化空间。

## 这些能力是怎么连起来的

Watcher 最重要的，不只是模块多，而是这些模块之间能连成闭环。

一个典型闭环可以是这样：

1. 设备接入实时视频流。
2. 用户发起实时监控或视频分析任务。
3. 系统围绕视频流持续采样、分析和输出结果。
4. 横屏进入 `Live` 模式时，结果开始变成实时解说和互动玩法。
5. 横屏进入 `Council` 模式时，多专家围绕同一上下文进行协同分析。
6. 运行过程中的事件、媒资、分段和结论进入历史工作台。
7. 历史事实继续进入 `Digital Life Card` 和 Blackboard，变成长期行为建模材料。
8. Agent Framework、API Wallet 和 LiteRT 再为下一轮运行提供更稳的模型、记忆和自治能力。

这条链路的含义是：Watcher 不是把“监控”“分析”“直播”“建模”分成四个孤立产品，而是在试图让它们互相喂数据、互相提供上下文。

## 为什么这个项目有创新点

Watcher 的创新点，不是某一个单独 feature，而是它在产品结构上的组合方式。

### 1. 从一次性 AI 调用，走向持续运行系统

很多 AI 视频产品还停留在“用户上传一个东西，模型返回一句话”的阶段。Watcher 更强调持续运行：

- 视频流持续接入
- 监控任务持续执行
- 分段分析持续进行
- 历史结果持续沉淀
- 画像和 Agent 能力持续演化

### 2. 从单点视频理解，走向完整闭环

Watcher 试图打通的是：

- 实时监控
- 分段视频分析
- 直播式实时表达
- 多角色协同研判
- 长期行为建模

这几个环节通常不会同时出现在同一个应用里。

### 3. 从单 Agent 交互，走向多角色协作

项目里既有直播场景下的 AI 观众，也有 `Council Mode` 的专家协作，还有独立的 Agent Framework。

这使 Watcher 的 AI 形态不是单一助手，而是更接近：

- 直播陪伴者
- 现场评论员
- 多专家团队
- 可独立调用的自治 Agent

### 4. 从纯云端依赖，走向端侧实验空间

`LiteRT` 的存在很关键。它让项目不只是一个“远程 API 壳子”，而是保留了端侧推理和本地 Brain 的演进空间。

### 5. 从功能堆叠，走向系统底座

统一历史、统一配置、API Wallet、Agent Framework 这几层一起出现，说明这个项目已经不只是做功能拼装，而是在形成自己的底层组织方式。

## 典型使用场景

### 场景 1：实时值守与异常告警

接入摄像头后，让 Watcher 按自然语言任务持续观察现场。一旦发现异常，就保存截图、记录事件、生成日志，并在历史工作台中保留证据链。

### 场景 2：长时视频观察与复盘

把一段长时段场景拆成多个视频片段，逐段分析并最终汇总，避免人工回看整段视频的成本。

### 场景 3：AI 直播互动实验

在横屏 `Live` 模式中，让视频流进入解说和互动链路，结合语音识别、AI 观众和实时解说，形成直播化玩法。

### 场景 4：复杂现场的多专家研判

在 `Council Mode` 里启用不同专家角色，对同一画面进行多视角分析，更适合复杂现场、访谈演练、会议观察、演讲反馈等场景。

### 场景 5：长期用户行为建模

把跨场景、跨时间的观察结果沉淀到 `Digital Life Card`，逐步形成更稳定的行为模式、画像和推理日志。

### 场景 6：本地模型与 Agent Runtime 实验

通过 `LiteRT`、`Agent Config` 和 `API Wallet` 组合不同模型来源、Brain 和 Agent 运行方式，探索更偏研究型和原型型的玩法。

## 主要入口

### 主工作台

主工作区围绕同一条视频流组织，目前包含这些核心页面：

- `实时监控`
- `总览`
- `视频分析`
- `历史记录`
- `管理中心`

横屏时可进一步进入两种沉浸式模式：

- `Live`
- `Council`

### 独立入口

- `API Wallet`
  统一管理 provider、endpoint、模型来源和连接测试
- `Agent Config`
  管理 Agent、Brain、Memory、Knowledge 和运行记录
- `Digital Life Card`
  用户行为建模工作台
- `LiteRt`
  本地模型实验入口

## 快速开始

### 环境要求

- Android Studio
- Android SDK 35
- JDK 11
- Android 10 及以上设备或模拟器

当前编译参数：

- `minSdk = 29`
- `compileSdk = 35`
- `targetSdk = 35`

### 最小配置

项目会从根目录 `local.properties` 读取开发期密钥。最小示例：

```properties
API_KEY=your_api_key
SPEECH_APP_ID=your_speech_app_id
SPEECH_ACCESS_KEY_ID=your_speech_access_key_id
SPEECH_ACCESS_KEY_SECRET=your_speech_access_key_secret
```

如果需要 release 签名，还可以补充：

```properties
RELEASE_STORE_FILE=xxx.jks
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_alias
RELEASE_KEY_PASSWORD=your_key_password
```

### 构建命令

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

常用检查命令：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

### 建议首次体验路径

1. 在 `local.properties` 中配置开发期密钥。
2. 构建并安装 `debug` 版本。
3. 配置视频流地址并确认实时画面可用。
4. 先体验 `实时监控`，理解持续观察和告警链路。
5. 再体验 `视频分析`，理解分段执行和最终汇总。
6. 横屏进入 `Live` 和 `Council`，体验实时互动与多专家协作。
7. 打开 `历史记录` 查看媒资、证据链和运行结果。
8. 打开 `Digital Life Card`、`API Wallet`、`Agent Config` 和 `LiteRt`，理解长期建模与底层能力。

## 配置与安全

### AI Provider 与 API Wallet

- AI provider 统一通过 `API Wallet` 管理
- provider endpoint 必须使用 `https://`
- provider API Key 保存在应用本地加密存储
- Agent 可以绑定单独的 Brain，而不必依赖全局默认 provider

### 网关与局域网访问

Watcher 支持在本机开启嵌入式 Gateway API，用于局域网自动化和远程控制。

当前特点：

- 默认地址是 `http://<local-ip>:<port>`
- 使用 `X-API-Key` 做鉴权
- 支持能力发现、任务创建、任务状态查询、抓取快照
- 支持 Agent 相关运行接口
- 更适合作为内网开发能力，而不是公网安全方案

### 开发期密钥与运行时密钥

- `local.properties` 不应提交到仓库
- 开发期密钥只会注入 `debug` 构建
- `release` 构建默认不会把这些值打进 `BuildConfig`
- 运行时 provider 密钥、语音识别凭据和网关 API key 都走应用本地加密存储
- Android 备份与设备迁移已排除相关敏感数据文件

### 当前安全边界

当前版本已经比早期版本更收敛，但它仍然偏开发型系统。使用时仍需注意：

- 局域网视频流和 Gateway API 仍可能使用明文 HTTP
- 历史上如果已有第三方凭据提交过仓库，仍需要服务端轮换
- 公开截图、录屏或演示时，需要注意脱敏局域网地址、网关地址、密钥和个人数据

## 技术架构

Watcher 当前不是一个通用 SDK，更像一个持续演进中的产品原型与研究工作台。

它的大致架构可以理解为六层：

- 视频流与设备层
  负责流接入、设备发现、局域网扫描、基础配网和实时画面管理
- 任务规划与执行层
  负责实时监控、视频分析、直播解说、语音触发和结果生成
- Audience / Council 层
  负责 AI 观众、专家协作、多角色实时研判
- 行为建模层
  负责 Blackboard、场景档案、行为 claim、推理日志和画像整理
- 数据与持久化层
  负责本地数据库、历史记录、模板、媒资和运行状态沉淀
- 模型与 Agent 层
  负责 `API Wallet`、`Agent Framework`、LiteRT 本地模型和 Brain 管理

### 项目结构

```text
app/
  src/main/java/com/example/watcher/
    agentframework/         Agent 框架与自治运行时
    data/local/             Room、本地模型与配置存储
    data/model/             数据模型
    data/remote/            Retrofit 服务与 OpenAI-compatible provider
    data/repository/        监控、视频分析、直播解说、行为建模等核心逻辑
    data/gateway/           局域网 Gateway API
    ui/components/          通用 Compose 组件
    ui/screens/             主工作区和独立页面
    ui/viewmodel/           状态编排
docs/                       设计记录、补丁与迭代文档
tools/                      辅助脚本
```

### 技术栈

- Kotlin
- Jetpack Compose
- Android ViewModel + StateFlow
- Room
- Retrofit + OkHttp
- NanoHTTPD
- AndroidX Security Crypto
- LiteRT-LM
- OpenAI-compatible Chat Completions / Responses 接口

## 推荐阅读路径

如果你是第一次进入这个仓库，建议按下面顺序阅读：

1. `app/src/main/java/com/example/watcher/ui/screens/MainScreen.kt`
2. `app/src/main/java/com/example/watcher/ui/viewmodel/IntentViewModel.kt`
3. `app/src/main/java/com/example/watcher/data/repository/MonitorManager.kt`
4. `app/src/main/java/com/example/watcher/data/repository/VideoProcessRepository.kt`
5. `app/src/main/java/com/example/watcher/DigitalLifeCardActivity.kt`
6. `app/src/main/java/com/example/watcher/WatcherApplication.kt`
7. `app/src/main/java/com/example/watcher/data/gateway/GatewayServer.kt`
8. `docs/agent-framework.md`

## 相关文档

- `docs/agent-framework.md`
- `docs/2026-03-26-product-iteration.md`
- `docs/2026-03-27-product-iteration.md`
- `docs/2026-04-07-database-field-summary.md`

## 当前项目状态

Watcher 目前更适合被理解为：

- 一个正在快速演进的产品原型
- 一个可持续扩展的视频 AI 工作台
- 一个把多种 AI 交互形态放在同一条视频流上做实验的研究型项目

它已经有比较清晰的主干能力，但文档、界面、配置和体验仍在快速迭代中。如果你是第一次看到这个仓库，最值得关注的不是某一个零散功能，而是这套系统正在逐渐形成的整体形态：

`实时视频流`、`持续观察`、`实时互动`、`多角色协作`、`长期记忆`、`端侧模型`

这些东西在 Watcher 里不是并列摆放，而是在慢慢被接成一个完整系统。
