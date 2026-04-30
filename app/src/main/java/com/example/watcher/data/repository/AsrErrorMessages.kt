package com.example.watcher.data.repository

import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun mapAsrNetworkError(
    throwable: Throwable,
    host: String = "openspeech.bytedance.com"
): String {
    val root = generateSequence(throwable) { it.cause }.last()
    return when (root) {
        is UnknownHostException ->
            "无法解析域名 $host。请检查设备网络、DNS、代理/VPN，或确认当前网络可以访问火山语音服务。"

        is SocketTimeoutException ->
            "连接 $host 超时。请检查当前网络质量，或稍后重试。"

        else -> throwable.message ?: root.message ?: "连接失败"
    }
}
