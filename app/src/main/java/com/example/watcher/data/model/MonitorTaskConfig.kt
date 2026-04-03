package com.example.watcher.data.model

enum class MonitorMode {
    SceneBaseline,
    ReferenceTarget
}

enum class TargetTrigger {
    OnAppear,
    OnDisappear
}

enum class BaselineSource {
    CapturedFrame,
    UploadedImage
}

fun monitorModeLabel(mode: MonitorMode): String {
    return when (mode) {
        MonitorMode.SceneBaseline -> "场景基线比较"
        MonitorMode.ReferenceTarget -> "参考目标检测"
    }
}

fun targetTriggerLabel(trigger: TargetTrigger): String {
    return when (trigger) {
        TargetTrigger.OnAppear -> "出现时提醒"
        TargetTrigger.OnDisappear -> "未出现时提醒"
    }
}

fun baselineSourceLabel(source: BaselineSource): String {
    return when (source) {
        BaselineSource.CapturedFrame -> "当前视频帧"
        BaselineSource.UploadedImage -> "上传图片"
    }
}
