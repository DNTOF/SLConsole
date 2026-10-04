package com.dntof.slconsole.data.remote

import com.dntof.slconsole.data.repo.ControlRepository

/** 把 2.6.1 内测端点的失败翻译成界面上能看懂的句子。 */
object BetaHints {
    const val ADAPTED_FORBIDDEN =
        "这把 API Key 还不能调用适配插件动作。请在 apikey.config 里这把 Key 的 endpoints_override 加上 \"/control/adapted/\": true，也可以只放开某一个插件。admin 和 duty Key 默认都没有这项，不要去改 Key 的角色。"

    const val FILES_FORBIDDEN =
        "这把 API Key 还不能访问文件。请在 apikey.config 里这把 Key 的 endpoints_override 加上 \"/control/files/\": true。文件端点默认拒绝，admin Key 也一样，不要去改 Key 的角色。"

    const val ACTION_TIMEOUT = "插件处理超时"

    const val SYMLINK_REJECTED =
        "路径经过符号链接或目录联接，服务器已拒绝，以免读到 FileRoot 外面。"

    fun actionFailure(failure: ControlRepository.ControlOutcome.Failure): String = when {
        failure.status == 504 || failure.message.contains("超时") -> ACTION_TIMEOUT
        failure.status == 403 -> ADAPTED_FORBIDDEN
        failure.status in 400..499 -> failure.message
        else -> failure.message
    }

    fun fileFailure(failure: ControlRepository.ControlOutcome.Failure): String = when {
        failure.status == 403 -> FILES_FORBIDDEN
        failure.message.contains("符号链接") || failure.message.contains("junction", ignoreCase = true) ->
            SYMLINK_REJECTED
        else -> failure.message
    }
}
