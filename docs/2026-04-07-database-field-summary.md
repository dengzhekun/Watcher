# 数据库字段总结

更新时间：2026-04-07  
适用范围：当前 `AppDatabase` 中实际注册的 Room 实体与字段。  
说明：本文档只总结当前代码里仍然有效、可直接使用的字段，不包含仅存在于旧 migration 但已不在当前 Entity 中的历史字段。

## 1. 数据库总览

当前数据库定义见：

- `app/src/main/java/com/example/watcher/data/local/AppDatabase.kt`

当前注册的表：

- `llm_providers`
- `ai_audiences`
- `ai_audience_messages`
- `council_experts`
- `monitor_tasks`
- `monitor_runs`
- `monitor_events`
- `monitor_media`
- `video_stream_settings`
- `video_process_tasks`
- `video_process_runs`
- `video_segment_runs`
- `timeline_events`
- `monitor_templates`
- `video_templates`
- `council_templates`

## 2. 字段说明

### 2.1 `llm_providers`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/AiAudienceModels.kt`

字段：

- `id`：模型接口唯一 ID。
- `name`：接口显示名称。
- `endpoint`：模型接口地址。
- `apiKey`：接口密钥。
- `modelName`：实际调用的模型名。
- `enabled`：该接口当前是否启用。
- `createdAt`：创建时间。
- `updatedAt`：最后修改时间。

用途：

- 统一管理模型服务来源。
- 给 AI 观众、智囊团专家分配具体模型。

### 2.2 `ai_audiences`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/AiAudienceModels.kt`

字段：

- `id`：观众主键。
- `name`：观众名称。
- `audienceType`：观众引擎类型，当前有 `Classic` 和 `Agent`。
- `persona`：观众核心人设描述。
- `socialArchetype`：社交原型标签。
- `speakingStyle`：说话风格标签。
- `spendingStyle`：消费/打赏风格标签。
- `socialDrive`：社交动机标签。
- `providerId`：绑定的模型接口 ID。
- `enabled`：是否启用。
- `heartbeatIntervalSeconds`：自动发言心跳间隔。
- `includeFrame`：发言时是否显式带入画面信息。
- `personalMemory`：该观众的个人长期记忆。
- `agentStateJson`：Agent 观众运行状态快照。
- `createdAt`：创建时间。
- `updatedAt`：最后修改时间。

用途：

- 配置直播房间里的 AI 观众/Agent 观众。
- 保存每个观众的表达风格和长期记忆。
- 可用于反推用户偏好的陪伴风格、互动密度、角色审美。

### 2.3 `ai_audience_messages`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/AiAudienceModels.kt`

字段：

- `id`：消息主键。
- `audienceId`：发言观众 ID。
- `audienceName`：发言观众名称快照。
- `content`：消息内容。
- `mentionedAudienceId`：被提及的观众 ID。
- `mentionedAudienceName`：被提及观众名称。
- `triggerType`：消息触发类型，如心跳、提及、事件等。
- `timestamp`：消息发生时间。
- `createdAt`：写入数据库时间。

用途：

- 保存直播模式下 AI/Agent 观众发言历史。
- 可用于做高频话题、互动网络、消息节奏统计。

### 2.4 `council_experts`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/CouncilExpertConfigs.kt`

字段：

- `role`：专家角色主键，如 `Observer`、`Risk`、`Strategy`。
- `name`：专家显示名称。
- `promptPersona`：专家工作风格描述。
- `perspective`：专家关注点描述。
- `providerId`：优先绑定的模型接口 ID。
- `enabled`：是否启用该专家。
- `sortOrder`：展示顺序。
- `updatedAt`：最后修改时间。

用途：

- 配置智囊团的席位和职责。
- 可用于总结用户偏好的分析视角。

### 2.5 `monitor_tasks`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/MonitorTask.kt`

字段：

- `id`：任务主键。
- `title`：任务标题。
- `userInput`：用户原始输入。
- `userRequirement`：归纳后的任务需求。
- `originalSceneDescription`：原始场景描述。
- `checkInterval`：监控检查间隔。
- `promptTemplate`：监控任务 prompt 模板。
- `baseFrameBase64`：基准图的 Base64 内容。
- `baselineImagePath`：基准图文件路径。
- `monitorMode`：监控模式。
- `targetTrigger`：触发条件。
- `baselineSource`：基准图来源。
- `createdAt`：创建时间。
- `updatedAt`：最后修改时间。
- `lastUsedAt`：最近使用时间。
- `runCount`：累计执行次数。
- `lastStatus`：最近执行状态。
- `lastSummary`：最近执行总结。

用途：

- 保存实时监控任务定义。
- 可用于分析用户长期关注的监控目标和场景。

### 2.6 `monitor_runs`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/HistoryModels.kt`

字段：

- `id`：运行记录主键。
- `taskId`：关联的监控任务 ID。
- `taskTitle`：运行时任务标题快照。
- `taskRequirement`：运行时任务需求快照。
- `monitorMode`：本次运行模式。
- `targetTrigger`：本次触发策略。
- `baselineSource`：本次基准来源。
- `status`：运行状态。
- `startedAt`：开始时间。
- `endedAt`：结束时间。
- `baselineImagePath`：运行中使用的基准图路径。
- `sessionVideoPath`：本次监控会话录像路径。
- `lastResult`：最近一次检查结果。
- `lastSummary`：最近一次总结。
- `lastReason`：最近一次说明/原因。
- `alertCount`：告警次数。
- `warningCount`：警告次数。
- `unknownCount`：未知次数。
- `normalCount`：正常次数。
- `totalCheckCount`：总检查次数。
- `skippedCount`：跳过次数。
- `failureCount`：失败次数。
- `createdAt`：创建时间。
- `updatedAt`：最后修改时间。

用途：

- 保存监控执行历史。
- 可用于统计用户常见风险、异常密度、监控效果。

### 2.7 `monitor_events`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/HistoryModels.kt`

字段：

- `id`：事件主键。
- `runId`：所属运行记录 ID。
- `timestamp`：事件发生时间。
- `result`：检查结果。
- `message`：事件描述。
- `action`：系统动作。
- `frameImagePath`：事件截图路径。
- `confidence`：判断置信度。
- `createdAt`：写入时间。

用途：

- 保存运行过程中的每次关键事件。
- 可用于还原历史过程和做风险归因。

### 2.8 `monitor_media`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/HistoryModels.kt`

字段：

- `id`：媒体主键。
- `runId`：所属运行记录 ID。
- `mediaType`：媒体类型，如快照、基准图、事件帧、会话视频。
- `localFilePath`：本地文件路径。
- `createdAt`：生成时间。

用途：

- 作为监控历史的媒体索引。
- 主要是资源定位，不是高价值画像字段。

### 2.9 `video_stream_settings`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/VideoStreamSettings.kt`

字段：

- `id`：固定配置主键。
- `ipAddress`：视频流设备地址。
- `port`：设备端口。
- `resolution`：分辨率配置。
- `quality`：画质参数。
- `brightness`：亮度。
- `contrast`：对比度。
- `enabled`：流服务是否启用。
- `ledControlEnabled`：是否允许控制补光灯。
- `ledAutoLightEnabled`：是否自动补光。
- `ledTargetBrightness`：目标亮度。
- `changeDetectionEnabled`：是否启用变化检测。
- `changeThresholdPercent`：变化检测阈值。
- `notificationCooldownSeconds`：通知冷却时间。
- `videoAnalysisStreamingEnabled`：视频分析时是否启用流式输出。
- `deviceProfile`：设备类型。
- `preferredWifiSsid`：偏好连接的 Wi-Fi 名称。

用途：

- 保存视频设备和采集偏好。
- 更偏“使用环境配置”，不是直接的用户画像核心。

### 2.10 `video_process_tasks`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/VideoProcessModels.kt`

字段：

- `id`：任务主键。
- `templateId`：来源模板 ID。
- `templateLabel`：模板名快照。
- `taskCategory`：任务类别。
- `strategyReason`：任务规划原因。
- `title`：任务标题。
- `userInput`：用户原始输入。
- `userRequirement`：用户目标。
- `sceneContext`：场景上下文。
- `segmentAnalysisPrompt`：分段分析 prompt。
- `finalSummaryPrompt`：总结合成 prompt。
- `plannedDurationSeconds`：计划时长。
- `plannedSamplingFps`：计划采样帧率。
- `plannedSegmentDurationSeconds`：计划单段时长。
- `captureIntervalSeconds`：采样间隔。
- `plannedSegmentCount`：计划段数。
- `autoStartStreamingOutput`：是否自动开启流式输出。
- `finalSummaryEnabled`：是否启用最终总结。
- `confirmationNotes`：确认说明。
- `createdAt`：创建时间。
- `updatedAt`：最后修改时间。
- `lastUsedAt`：最近使用时间。
- `runCount`：累计运行次数。

用途：

- 保存视频分析任务定义。
- 可用于总结用户常见分析目标、时长偏好、分析粒度偏好。

### 2.11 `video_process_runs`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/VideoProcessModels.kt`

字段：

- `id`：运行记录主键。
- `taskId`：关联的视频任务 ID。
- `templateId`：运行时模板 ID。
- `templateLabel`：运行时模板名快照。
- `taskTitle`：任务标题快照。
- `taskRequirement`：任务需求快照。
- `status`：运行状态。
- `recordingStartedAt`：录制开始时间。
- `recordingEndedAt`：录制结束时间。
- `totalDurationSeconds`：总时长。
- `segmentDurationSeconds`：单段时长。
- `captureIntervalSeconds`：采样间隔。
- `segmentCount`：分段数量。
- `finalSummary`：最终总结。
- `finalConclusion`：最终结论。
- `rawModelSummary`：模型原始输出总结。
- `mergedVideoPath`：合成视频路径。
- `errorMessage`：错误信息。
- `createdAt`：创建时间。
- `updatedAt`：最后修改时间。

用途：

- 保存视频分析执行历史。
- 可用于评估用户最常分析的主题、结果质量和失败点。

### 2.12 `video_segment_runs`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/VideoProcessModels.kt`

字段：

- `id`：分段主键。
- `runId`：所属视频运行 ID。
- `segmentIndex`：段序号。
- `status`：该段状态。
- `durationSeconds`：该段时长。
- `localFilePath`：本地文件路径。
- `arkFileId`：上传后的文件 ID。
- `summary`：该段总结。
- `conclusion`：该段结论。
- `errorMessage`：该段错误信息。
- `createdAt`：创建时间。
- `updatedAt`：最后修改时间。

用途：

- 保存视频任务的分段结果。
- 可用于细粒度分析用户偏好关注的时间片段。

### 2.13 `timeline_events`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/VideoProcessModels.kt`

字段：

- `id`：事件主键。
- `runId`：所属视频运行 ID。
- `segmentRunId`：所属视频分段 ID。
- `timestampSeconds`：事件时间点。
- `title`：事件标题。
- `detail`：事件详情。
- `confidence`：事件置信度。
- `createdAt`：创建时间。

用途：

- 用于记录视频分析出的关键事件时间线。
- 适合做重点信息抽取、摘要重建、热点归纳。

### 2.14 `monitor_templates`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/TemplateEntities.kt`

字段：

- `templateId`：模板主键。
- `label`：模板名称。
- `description`：模板描述。
- `userRequirement`：适用的用户需求。
- `originalSceneDescription`：默认场景描述。
- `checkIntervalSeconds`：默认检查间隔。
- `promptTemplate`：默认 prompt 模板。
- `monitorMode`：默认监控模式。
- `targetTrigger`：默认触发策略。
- `baselineSource`：默认基准来源。
- `isDefault`：是否系统默认模板。
- `updatedAt`：最后修改时间。

用途：

- 保存监控任务模板。
- 可用于做模板推荐和用户偏好模板统计。

### 2.15 `video_templates`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/TemplateEntities.kt`

字段：

- `templateId`：模板主键。
- `label`：模板名称。
- `description`：模板描述。
- `taskCategory`：模板类别。
- `strategyReason`：模板设计思路。
- `userRequirement`：模板目标。
- `sceneContext`：默认场景上下文。
- `segmentAnalysisPrompt`：默认分段分析 prompt。
- `finalSummaryPrompt`：默认总结合成 prompt。
- `recordingDurationSeconds`：默认录制时长。
- `segmentDurationSeconds`：默认分段时长。
- `captureIntervalSeconds`：默认采样间隔。
- `samplingFps`：默认采样帧率。
- `autoStartStreamingOutput`：是否自动流式输出。
- `finalSummaryEnabled`：是否启用总结。
- `isDefault`：是否系统默认模板。
- `updatedAt`：最后修改时间。

用途：

- 保存视频分析模板。
- 可用于分析用户常用分析策略。

### 2.16 `council_templates`

来源文件：

- `app/src/main/java/com/example/watcher/data/model/TemplateEntities.kt`

字段：

- `templateId`：模板主键。
- `label`：模板名称。
- `description`：模板描述。
- `sceneType`：场景类型。
- `objective`：目标。
- `focus`：聚焦点。
- `isDefault`：是否系统默认模板。
- `updatedAt`：最后修改时间。

用途：

- 保存智囊团模式模板。
- 可用于判断用户常见决策场景和分析重点。

## 3. 更适合做个性化服务的字段组

### 3.1 用户需求和任务偏好

重点字段：

- `monitor_tasks.userInput`
- `monitor_tasks.userRequirement`
- `monitor_tasks.originalSceneDescription`
- `video_process_tasks.userInput`
- `video_process_tasks.userRequirement`
- `video_process_tasks.sceneContext`
- `video_process_tasks.taskCategory`
- `video_process_tasks.strategyReason`
- `council_templates.sceneType`
- `council_templates.objective`
- `council_templates.focus`

### 3.2 用户长期使用行为

重点字段：

- `monitor_tasks.lastUsedAt`
- `monitor_tasks.runCount`
- `video_process_tasks.lastUsedAt`
- `video_process_tasks.runCount`
- `monitor_runs.alertCount`
- `monitor_runs.warningCount`
- `monitor_runs.totalCheckCount`
- `video_process_runs.status`
- `video_process_runs.finalSummary`
- `timeline_events.title`
- `timeline_events.detail`

### 3.3 用户偏好的 AI 陪伴与分析风格

重点字段：

- `ai_audiences.persona`
- `ai_audiences.socialArchetype`
- `ai_audiences.speakingStyle`
- `ai_audiences.spendingStyle`
- `ai_audiences.socialDrive`
- `ai_audiences.personalMemory`
- `council_experts.promptPersona`
- `council_experts.perspective`
- `council_experts.enabled`

### 3.4 用户设备与使用环境习惯

重点字段：

- `video_stream_settings.resolution`
- `video_stream_settings.quality`
- `video_stream_settings.brightness`
- `video_stream_settings.contrast`
- `video_stream_settings.changeDetectionEnabled`
- `video_stream_settings.changeThresholdPercent`
- `video_stream_settings.notificationCooldownSeconds`
- `video_stream_settings.videoAnalysisStreamingEnabled`
- `video_stream_settings.deviceProfile`

## 4. 当前不建议直接作为用户画像主字段的内容

- `apiKey`
- `endpoint`
- `baseFrameBase64`
- `baselineImagePath`
- `localFilePath`
- `mergedVideoPath`
- `arkFileId`
- `agentStateJson`

说明：

- 这些字段更偏系统配置、原始素材路径或中间状态。
- 它们可以辅助个性化，但不适合作为用户画像核心字段。

## 5. 后续校对建议

后续可以继续做三轮校对：

- 第一轮：删掉对个性化服务没有意义的字段。
- 第二轮：筛出必须沉淀进“用户长期档案”的字段。
- 第三轮：定义哪些字段需要新增汇总表或用户画像表来承接。
