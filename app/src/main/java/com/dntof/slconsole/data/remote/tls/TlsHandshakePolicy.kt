// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.data.remote.tls

/** 把握手异常翻译成「改走明文 / 先探一次明文 / 停下来 / 过会儿再试加密」。 */
object TlsHandshakePolicy {
    sealed class Decision {
        data object UsePlaintext : Decision()

        /** 握手没有拿到 TLS 记录。可以发一次明文,只有明文请求真正返回才改传输。 */
        data object ProbePlaintext : Decision()
        data object RetryLater : Decision()
        data object RetryTls : Decision()
        data class Stop(val failure: TransportFailure) : Decision()
    }

    fun onTlsFailure(error: Throwable, tlsSeen: Boolean, allowPlaintextFallback: Boolean): Decision {
        val attempt = TlsFailureClassifier.classify(error)
        return when (val step = TlsDowngrade.afterTls(attempt, tlsSeen, allowPlaintextFallback)) {
            TlsDowngrade.Step.Plaintext -> Decision.UsePlaintext
            TlsDowngrade.Step.Probe -> Decision.ProbePlaintext
            TlsDowngrade.Step.Encrypted -> Decision.RetryLater
            TlsDowngrade.Step.RetryTls -> Decision.RetryTls
            is TlsDowngrade.Step.Halt -> when (step.stop) {
                TlsDowngrade.Stop.OTHER -> Decision.RetryLater
                else -> Decision.Stop(failureFor(step.stop, error))
            }
        }
    }

    fun onPlaintext426(alreadyRetriedTls: Boolean): Decision {
        return when (val step = TlsDowngrade.afterPlaintext426(alreadyRetriedTls)) {
            TlsDowngrade.Step.RetryTls -> Decision.RetryTls
            is TlsDowngrade.Step.Halt -> Decision.Stop(failureFor(step.stop, null))
            else -> Decision.Stop(TransportFailure.TlsRequired())
        }
    }

    fun failureFor(stop: TlsDowngrade.Stop, error: Throwable?): TransportFailure {
        return when (stop) {
            TlsDowngrade.Stop.TOFU -> {
                val fingerprint = error?.let { TlsFailureClassifier.tofu(it)?.fingerprint }.orEmpty()
                TransportFailure.Tofu(fingerprint)
            }
            TlsDowngrade.Stop.PIN_MISMATCH -> {
                val mismatch = error?.let { TlsFailureClassifier.mismatch(it) }
                TransportFailure.Mismatch(
                    pinned = mismatch?.expected.orEmpty(),
                    presented = mismatch?.presented.orEmpty(),
                )
            }
            TlsDowngrade.Stop.CERTIFICATE ->
                TransportFailure.Certificate(TlsMessages.certificate(error.shortMessage()))
            TlsDowngrade.Stop.DOWNGRADE_LATCH -> TransportFailure.Downgrade()
            TlsDowngrade.Stop.TLS_REQUIRED -> TransportFailure.TlsRequired()
            TlsDowngrade.Stop.OTHER -> TransportFailure.Other(TlsMessages.other(error.shortMessage()))
        }
    }
}

internal fun Throwable?.shortMessage(): String {
    val msg = this?.message?.trim().orEmpty()
    if (msg.isNotEmpty()) return msg.take(180)
    return this?.javaClass?.simpleName ?: "未知错误"
}
