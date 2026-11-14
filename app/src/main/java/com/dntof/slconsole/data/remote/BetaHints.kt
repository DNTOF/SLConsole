// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote

import com.dntof.slconsole.data.repo.ControlRepository
import com.dntof.slconsole.R
import com.dntof.slconsole.util.appString

/** 把 2.6.1 内测端点的失败翻译成界面上能看懂的句子。 */
object BetaHints {
    val ADAPTED_FORBIDDEN: String get() = appString(R.string.beta_hints_api_key_cant_call_adapted)

    val FILES_FORBIDDEN: String get() = appString(R.string.beta_hints_api_key_cant_access_files)

    val ACTION_TIMEOUT: String get() = appString(R.string.beta_hints_plugin_timed_out)

    val SYMLINK_REJECTED: String get() = appString(R.string.beta_hints_path_goes_through_symbolic_link)

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
