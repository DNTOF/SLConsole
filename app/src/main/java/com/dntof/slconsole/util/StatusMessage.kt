// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 界面文案的结构化表示,在语言切换后按当前语言重新解析。
 * [Literal] 用于已经定稿或来自服务器/TLS 的句子,保持发出时的原文。
 */
sealed interface StatusMessage {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Arg> = emptyList(),
    ) : StatusMessage

    data class Literal(val text: String) : StatusMessage

    sealed interface Arg {
        data class Text(val value: String) : Arg
        data class Res(@StringRes val id: Int) : Arg
    }

    fun resolve(): String = when (this) {
        is Resource -> {
            val resolved = resolvedArgs()
            if (resolved.isEmpty()) appString(id) else appString(id, *resolved.toTypedArray())
        }
        is Literal -> text
    }

    private fun resolvedArgs(): List<String> = when (this) {
        is Resource -> args.map { arg ->
            when (arg) {
                is Arg.Text -> arg.value
                is Arg.Res -> appString(arg.id)
            }
        }
        is Literal -> emptyList()
    }
}

fun statusRes(@StringRes id: Int, vararg args: Any?): StatusMessage.Resource {
    val mapped = args.map { arg ->
        when (arg) {
            is StatusMessage.Arg -> arg
            else -> StatusMessage.Arg.Text(arg?.toString() ?: "")
        }
    }
    return StatusMessage.Resource(id, mapped)
}

@Composable
fun StatusMessage.resolveCompose(): String = when (this) {
    is StatusMessage.Resource -> {
        val resolved = args.map { arg ->
            when (arg) {
                is StatusMessage.Arg.Text -> arg.value
                is StatusMessage.Arg.Res -> stringResource(arg.id)
            }
        }
        if (resolved.isEmpty()) stringResource(id) else stringResource(id, *resolved.toTypedArray())
    }
    is StatusMessage.Literal -> text
}
