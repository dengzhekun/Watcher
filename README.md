# Watcher

Watcher 是一个运行在 Android 设备上的本地视频观察工作台。它围绕同一条摄像头视频流，把实时监控、分段视频分析、直播解说、AI/Agent 观众互动、历史回看和模板管理整合到一个持续运行的移动端应用里。

这个项目的重点不是单次调用模型，而是把以下几类能力串成一条完整工作链路：

- 连接并预览局域网视频流
- 用自然语言定义“要观察什么”
- 执行实时监控或视频分段分析
- 在直播模式下叠加实时解说、语音识别和 AI 观众
- 将记录、截图、视频片段、时间线和调试状态沉淀为本地历史

## 项目定位

Watcher 更接近一个持续迭代中的产品原型，而不是一个通用 SDK。

它适合以下场景：

- 使用 `ESP32-CAM` 或兼容 MJPEG 设备进行局域网视频观察
- 对固定机位画面做长时间巡检、留证和回看
- 把“视频分析”从一次性提问，变成可计划、可分段、可回放的工作流
- 在直播或陪看场景中叠加 AI 解说和 AI 观众互动

## 功能概览

### 实时监控

- 连接局域网 MJPEG 视频流，默认兼容 `ESP32-CAM`
- 支持基于当前画面或基线图的监控任务
- 支持巡检间隔、变化检测、告警冷却等执行策略
- 支持自动截图、快照留存、会话视频录制
- 支持设备补光、LED 自动亮灯等设备侧控制

### 视频分析

- 根据自然语言需求生成视频分析计划
- 支持模板任务和自由任务走同一套执行链路
- 把长时间观察拆成“录制分段 -> 分段分析 -> 最终汇总”
- 支持时间线事件、分段摘要、最终总结
- 支持流式输出和历史回看

### 直播模式

- 横屏进入沉浸式直播间界面
- 支持实时视频解说
- 支持语音识别，将主播语音作为互动触发源
- 支持两类虚拟观众引擎：
- `Classic AI 观众`
- `Agent 观众`
- 支持弹幕、点赞、礼物、关系更新、记忆压缩和调试快照

### 历史与管理

- 统一查看实时监控记录和视频分析记录
- 回看分段视频、关键截图、本地媒体和时间线事件
- 管理监控模板和视频分析模板
- 管理模型供应商和 AI 观众角色
- 支持模板导入导出分享

## 当前页面结构

主界面是一个多页工作台，支持左右滑动和底部悬浮导航切换：

- `实时监控`
- `总览`
- `视频分析`
- `历史记录`
- `管理中心`

其中：

- `总览` 用来承接当前视频流状态、最近任务和入口跳转
- `实时监控` 聚焦巡检、告警和留证
- `视频分析` 聚焦计划生成、参数确认、分段执行和结果汇总
- `历史记录` 聚合回放和结果复盘
- `管理中心` 聚焦模板、Provider、AI/Agent 观众配置

## 典型使用流程

### 流程一：实时监控

1. 配置视频流地址或扫描局域网设备
2. 预览当前视频流画面
3. 输入自然语言需求，生成监控任务
4. 选择巡检模式、间隔、基线来源等参数
5. 启动监控
6. 应用周期性分析当前帧并写入日志、截图和运行记录
7. 在历史页面回看整次监控会话

### 流程二：视频分析

1. 输入自然语言需求或选择视频模板
2. 系统生成分析计划
3. 用户确认录制时长、分段长度、采样节奏、是否流式输出等参数
4. 应用按计划录制多个视频分段
5. 每个分段依次上传、预处理并进行分析
6. 应用整理时间线事件、片段结论和最终摘要
7. 在历史页面按分段回看结果

### 流程三：直播模式

1. 横屏进入直播模式
2. 启动实时视频解说
3. 启动实时语音识别
4. 启动 AI 观众或 Agent 观众
5. 观众基于画面、主播语音、最近弹幕和历史记忆决定是否互动
6. 在管理中心查看 prompt、运行态和调试信息

## 系统组成

Watcher 的核心能力大致可以拆成六层：

### 1. 视频流接入层

负责连接局域网视频流并向上游提供最新画面。

主要内容：

- MJPEG 流预览
- ESP32-CAM 设备参数控制
- 局域网设备扫描
- 设备配网辅助

相关文件：

- [app/src/main/java/com/example/watcher/data/model/VideoStreamSettings.kt](app/src/main/java/com/example/watcher/data/model/VideoStreamSettings.kt)
- [app/src/main/java/com/example/watcher/ui/components/VideoStreamSettingsDialog.kt](app/src/main/java/com/example/watcher/ui/components/VideoStreamSettingsDialog.kt)
- [app/src/main/java/com/example/watcher/data/repository/LanStreamScanner.kt](app/src/main/java/com/example/watcher/data/repository/LanStreamScanner.kt)
- [app/src/main/java/com/example/watcher/data/repository/StreamDeviceCoordinator.kt](app/src/main/java/com/example/watcher/data/repository/StreamDeviceCoordinator.kt)

### 2. 任务规划层

负责把自然语言需求转成结构化任务。

主要内容：

- 实时监控任务意图解析
- 视频分析任务规划
- 模板任务与自由任务统一建模

相关文件：

- [app/src/main/java/com/example/watcher/ui/viewmodel/IntentViewModel.kt](app/src/main/java/com/example/watcher/ui/viewmodel/IntentViewModel.kt)
- [app/src/main/java/com/example/watcher/data/repository/IntentRepository.kt](app/src/main/java/com/example/watcher/data/repository/IntentRepository.kt)
- [app/src/main/java/com/example/watcher/data/repository/VideoProcessRepository.kt](app/src/main/java/com/example/watcher/data/repository/VideoProcessRepository.kt)

### 3. 执行层

负责真正执行实时监控、视频录制、分段分析和直播解说。

主要内容：

- 监控巡检循环
- 视频分段录制与分析
- 直播实时解说
- 语音识别

相关文件：

- [app/src/main/java/com/example/watcher/data/repository/MonitorManager.kt](app/src/main/java/com/example/watcher/data/repository/MonitorManager.kt)
- [app/src/main/java/com/example/watcher/data/repository/MjpegVideoRecorder.kt](app/src/main/java/com/example/watcher/data/repository/MjpegVideoRecorder.kt)
- [app/src/main/java/com/example/watcher/data/repository/VideoProcessRepository.kt](app/src/main/java/com/example/watcher/data/repository/VideoProcessRepository.kt)
- [app/src/main/java/com/example/watcher/data/repository/LiveCommentaryRepository.kt](app/src/main/java/com/example/watcher/data/repository/LiveCommentaryRepository.kt)
- [app/src/main/java/com/example/watcher/data/repository/LiveSpeechRecognitionManager.kt](app/src/main/java/com/example/watcher/data/repository/LiveSpeechRecognitionManager.kt)

### 4. AI 互动层

负责直播模式下的虚拟观众。

当前有两套实现：

- `Classic AI 观众`
- `Agent 观众`

二者是并行实现，不是直接覆盖关系。当前方向是让 Agent 观众逐步替换经典实现，但目前两者仍可并存、分别配置和对比体验。

相关文件：

- [app/src/main/java/com/example/watcher/data/repository/AiAudienceManager.kt](app/src/main/java/com/example/watcher/data/repository/AiAudienceManager.kt)
- [app/src/main/java/com/example/watcher/data/repository/agent/AgentAudienceManager.kt](app/src/main/java/com/example/watcher/data/repository/agent/AgentAudienceManager.kt)
- [app/src/main/java/com/example/watcher/data/repository/agent/AgentAudiencePromptBuilder.kt](app/src/main/java/com/example/watcher/data/repository/agent/AgentAudiencePromptBuilder.kt)

### 5. 记忆与历史层

负责保存运行结果、观众记忆和模板数据。

主要内容：

- Room 数据库存储
- 监控历史、视频分析历史
- 模板持久化
- 观众消息和运行态持久化

相关文件：

- [app/src/main/java/com/example/watcher/data/local/AppDatabase.kt](app/src/main/java/com/example/watcher/data/local/AppDatabase.kt)
- [app/src/main/java/com/example/watcher/data/repository/HistoryRepository.kt](app/src/main/java/com/example/watcher/data/repository/HistoryRepository.kt)
- [app/src/main/java/com/example/watcher/data/repository/TemplateRepository.kt](app/src/main/java/com/example/watcher/data/repository/TemplateRepository.kt)
- [app/src/main/java/com/example/watcher/data/repository/TemplateShareManager.kt](app/src/main/java/com/example/watcher/data/repository/TemplateShareManager.kt)
- [app/src/main/java/com/example/watcher/data/repository/CommentaryMemoryManager.kt](app/src/main/java/com/example/watcher/data/repository/CommentaryMemoryManager.kt)
- [app/src/main/java/com/example/watcher/data/repository/SceneMemoryManager.kt](app/src/main/java/com/example/watcher/data/repository/SceneMemoryManager.kt)

### 6. UI 编排层

负责把所有状态整合成一个连续的多页工作台体验。

相关文件：

- [app/src/main/java/com/example/watcher/ui/screens/MainScreen.kt](app/src/main/java/com/example/watcher/ui/screens/MainScreen.kt)
- [app/src/main/java/com/example/watcher/ui/components/WatcherScaffold.kt](app/src/main/java/com/example/watcher/ui/components/WatcherScaffold.kt)
- [app/src/main/java/com/example/watcher/ui/components/WatcherMotion.kt](app/src/main/java/com/example/watcher/ui/components/WatcherMotion.kt)

## 技术栈

- Kotlin
- Jetpack Compose
- Android ViewModel + StateFlow
- Room
- Retrofit + OkHttp
- JCodec
- OpenAI-compatible Chat Completions 接口

## 项目结构

```text
app/
  src/main/java/com/example/watcher/
    data/local/         Room 数据库、DAO
    data/model/         数据模型
    data/remote/        Retrofit 服务、流式客户端、LLM provider
    data/repository/    监控、视频分析、直播解说、设备控制、AI 观众等核心逻辑
    ui/components/      通用 Compose 组件
    ui/screens/         页面
    ui/theme/           主题与视觉风格
    ui/viewmodel/       状态编排入口
docs/                  产品迭代记录、说明文档、补丁
tools/                 辅助工具
```

## 运行环境

### Android 要求

- Android Studio
- Android SDK 35
- JDK 11
- Android 10 及以上设备或模拟器

### 最低系统版本

- `minSdk = 29`

### 编译目标

- `compileSdk = 35`
- `targetSdk = 35`

## 配置说明

### 1. `local.properties`

项目会从根目录 `local.properties` 中读取 API Key：

```properties
API_KEY=your_api_key
```

这个 `API_KEY` 主要用于项目内置的模型调用链路，例如：

- 意图解析
- 实时监控分析
- 视频分析
- 直播实时解说

注意：

- `local.properties` 不应提交到仓库
- 目前 `app/build.gradle.kts` 只读取 `API_KEY`

### 2. 视频流配置

默认设备配置面向 `ESP32-CAM`：

- 默认设备 IP：`192.168.4.1`
- 默认控制端口：`80`
- 默认视频流端口：`81`
- 默认视频流地址：`http://<ip>:81/stream`

如果使用非 ESP32 设备，可以切换为 MJPEG-only 配置。

参考文件：

- [app/src/main/java/com/example/watcher/data/model/VideoStreamSettings.kt](app/src/main/java/com/example/watcher/data/model/VideoStreamSettings.kt)

### 3. 局域网访问

应用当前允许局域网明文 HTTP 访问，相关配置位于：

- [app/src/main/res/xml/network_security_config.xml](app/src/main/res/xml/network_security_config.xml)

这意味着：

- 适合开发环境和内网环境
- 不适合直接作为公网安全方案使用

### 4. AI/Agent 观众 Provider

AI 观众和 Agent 观众使用应用内新增的 OpenAI-compatible provider。

这部分不是通过 `local.properties` 完成，而是在应用管理中心中单独配置：

- Provider 名称
- 接口地址
- API Key
- 模型名称

当前 Provider 数据会持久化在本地数据库中。

相关文件：

- [app/src/main/java/com/example/watcher/data/model/AiAudienceModels.kt](app/src/main/java/com/example/watcher/data/model/AiAudienceModels.kt)
- [app/src/main/java/com/example/watcher/data/remote/OpenAiCompatibleProvider.kt](app/src/main/java/com/example/watcher/data/remote/OpenAiCompatibleProvider.kt)

## 构建与运行

在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

测试命令：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat lintDebug
```

## 第一次启动建议

如果你是第一次运行这个项目，推荐按下面的顺序体验：

1. 配置 `local.properties`
2. 启动 App
3. 在设置里配置视频流地址
4. 确认预览画面可以正常显示
5. 先体验“实时监控”
6. 再体验“视频分析”
7. 最后配置 Provider 和 AI/Agent 观众，进入直播模式

## 数据持久化

应用使用 Room，本地数据库名为：

```text
watcher_database
```

主要持久化内容包括：

- 监控任务和运行记录
- 监控截图和媒体文件索引
- 视频分析任务、运行记录、分段和时间线
- 视频流设置
- 模板
- LLM Provider
- AI/Agent 观众
- 观众消息记录

数据库入口：

- [app/src/main/java/com/example/watcher/data/local/AppDatabase.kt](app/src/main/java/com/example/watcher/data/local/AppDatabase.kt)

## 历史记录能力

历史页面不是简单的日志页，而是一个统一的历史工作台。

它可以查看：

- 实时监控记录
- 视频分析记录
- 分段视频
- 监控截图
- 附加快照
- 时间线事件
- 最终结论

这使得 Watcher 的结果不仅能“看当下”，也能“回看和复核”。

## 关于 Agent 观众

当前仓库里，Agent 观众已经是独立实现，不再只是经典 AI 观众 prompt 的简单改写。

它目前具备这些方向上的能力基础：

- 持续存在的人设和长期状态
- 主观关系、社交画像和短期记忆
- 对主播语音、最近弹幕、视频画面和直播上下文的综合反应
- 运行态调试和 prompt 复制
- 与经典 AI 观众并行存在，便于灰度替换

当前实现上，Agent 观众仍然是单体 LLM 驱动的角色代理，而不是完整意义上的多工具自治系统。它更像“具备持久状态和多轮上下文的角色化互动 agent”。

## 直播解说的当前策略

直播实时解说采用分段录制和异步分析，不是严格意义上的逐帧在线理解。

当前重点是：

- 尽量保持实时性
- 优先保证最新分段
- 在高积压时主动跳过旧分段
- 用 `Skipped` 区分“主动跳过”和“分析失败”

这意味着在网络环境较差时，系统会优先保住新鲜度，而不是保证每个片段都被完整分析。

## 默认模型链路

项目中默认模型配置集中在：

- [app/src/main/java/com/example/watcher/data/repository/ArkConfig.kt](app/src/main/java/com/example/watcher/data/repository/ArkConfig.kt)

当前默认链路包括：

- `intentModel`
- `monitorModel`
- `videoPlanningModel`
- `videoAnalysisModel`

这部分目前是代码内配置，不是用户可视化配置。

## 安全与公开仓库注意事项

如果你准备把这个项目公开到 GitHub，建议先检查以下内容：

- `local.properties` 不要上传
- 各类本地缓存目录不要上传
- 任何写死在源码中的第三方服务凭据都应迁移到安全配置
- 如需公开截图或录屏，先确认其中不包含局域网地址、私有模型地址或个人数据

## 当前实现特点

- 项目偏本地单机使用，状态和配置主要保存在设备本地
- 很多能力依赖真实视频流和局域网环境
- 直播模式是复合功能区，实时解说、语音识别和 AI 观众通常同时运行
- 经典 AI 观众和 Agent 观众当前并行存在，便于对比和渐进迁移
- 文档和产品形态仍在快速迭代中

## 已知限制

- 默认网络策略偏开发环境，不是生产安全配置
- 没有云端任务编排，主要依赖本地设备执行
- 很多体验必须依赖真实摄像头和可用模型接口才能完整验证
- 一些 `docs/` 文件更接近迭代记录，不是稳定外部文档

## 仓库内值得先读的文件

如果你要继续开发这个项目，推荐先读以下文件：

1. [app/src/main/java/com/example/watcher/ui/screens/MainScreen.kt](app/src/main/java/com/example/watcher/ui/screens/MainScreen.kt)
2. [app/src/main/java/com/example/watcher/ui/viewmodel/IntentViewModel.kt](app/src/main/java/com/example/watcher/ui/viewmodel/IntentViewModel.kt)
3. [app/src/main/java/com/example/watcher/data/repository/MonitorManager.kt](app/src/main/java/com/example/watcher/data/repository/MonitorManager.kt)
4. [app/src/main/java/com/example/watcher/data/repository/VideoProcessRepository.kt](app/src/main/java/com/example/watcher/data/repository/VideoProcessRepository.kt)
5. [app/src/main/java/com/example/watcher/data/repository/LiveCommentaryRepository.kt](app/src/main/java/com/example/watcher/data/repository/LiveCommentaryRepository.kt)
6. [app/src/main/java/com/example/watcher/data/repository/agent/AgentAudienceManager.kt](app/src/main/java/com/example/watcher/data/repository/agent/AgentAudienceManager.kt)

## 后续建议

如果你想把这个仓库整理成更适合公开展示的版本，下一步最值得补的是：

- 一组产品截图或 GIF
- 一个最小可运行演示流程
- 示例模板
- 脱敏后的 Provider 配置示例
- 直播模式和 Agent 观众的独立设计文档

