// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

/**
 * 明文回退规则。hello 里的 tls / cert_fingerprint 不参与这里的判断。
 *
 * - TLS 成功:走加密
 * - 对端不讲 TLS,而且这台服务器没见过加密:允许这一次明文
 * - 证书问题、指纹不符、第一次确认:停止,不回退
 * - 见过加密之后再遇到不讲 TLS:停止
 * - 明文收到 426 / tls_required:再试一次 TLS,不能循环
 */
object TlsDowngrade {
    enum class Attempt {
        SUCCESS,
        TOFU,
        PIN_MISMATCH,
        CERTIFICATE,
        SERVER_PLAINTEXT,
        OTHER,
    }

    enum class Stop {
        TOFU,
        PIN_MISMATCH,
        CERTIFICATE,
        DOWNGRADE_LATCH,
        OTHER,
        TLS_REQUIRED,
    }

    sealed class Step {
        data object Encrypted : Step()
        data object Plaintext : Step()
        data class Halt(val stop: Stop) : Step()
        data object RetryTls : Step()
    }

    fun afterTls(attempt: Attempt, tlsSeen: Boolean, allowPlaintextFallback: Boolean): Step {
        return when (attempt) {
            Attempt.SUCCESS -> Step.Encrypted
            Attempt.TOFU -> Step.Halt(Stop.TOFU)
            Attempt.PIN_MISMATCH -> Step.Halt(Stop.PIN_MISMATCH)
            Attempt.CERTIFICATE -> Step.Halt(Stop.CERTIFICATE)
            Attempt.OTHER -> Step.Halt(Stop.OTHER)
            Attempt.SERVER_PLAINTEXT -> when {
                tlsSeen -> Step.Halt(Stop.DOWNGRADE_LATCH)
                allowPlaintextFallback -> Step.Plaintext
                else -> Step.Halt(Stop.TLS_REQUIRED)
            }
        }
    }

    /** 明文响应已经是 426。还没为这次 426 重试过 TLS 才允许再试一次。 */
    fun afterPlaintext426(alreadyRetriedTls: Boolean): Step {
        return if (alreadyRetriedTls) Step.Halt(Stop.TLS_REQUIRED) else Step.RetryTls
    }
}
