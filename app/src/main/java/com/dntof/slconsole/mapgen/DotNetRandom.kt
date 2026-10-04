// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.mapgen

import kotlin.math.abs
import kotlin.math.floor

/**
 * .NET Framework/Mono System.Random 的逐位移植(减法滞后斐波那契)。
 * 与 Web 端 mapgen/rng.ts 同源,保证同 seed 生成完全一致的地图布局。
 *
 * 注意:JVM 的 java.util.Random 是不同算法,不可替代;
 * 构造期运算用 Long(JS number 不回绕,seed == Int.MIN_VALUE 时 mj 超出 Int 范围)。
 */
class DotNetRandom(seed: Int) {
    private val seedArray = LongArray(56)
    private var inext = 0
    private var inextp = 0

    init {
        val absSeed = if (seed == Int.MIN_VALUE) Int.MIN_VALUE.toLong() else abs(seed).toLong()
        var mj = MSEED - absSeed
        seedArray[55] = mj
        var mk = 1L
        for (i in 1..54) {
            val ii = (21 * i) % 55
            seedArray[ii] = mk
            mk = mj - mk
            if (mk < 0) mk += MBIG
            mj = seedArray[ii]
        }
        for (k in 1..4) {
            for (i in 1..55) {
                seedArray[i] -= seedArray[1 + (i + 30) % 55]
                if (seedArray[i] < 0) seedArray[i] += MBIG
            }
        }
        inext = 0
        inextp = 21
    }

    /** 等价 .NET Sample(),返回 [0,1)。 */
    private fun sample(): Double {
        var i = inext + 1
        if (i >= 56) i = 1
        var p = inextp + 1
        if (p >= 56) p = 1
        var ret = (seedArray[i] - seedArray[p]).toInt()
        if (ret == MBIG) ret--
        if (ret < 0) ret += MBIG
        seedArray[i] = ret.toLong()
        inext = i
        inextp = p
        return ret * (1.0 / MBIG)
    }

    /** 浮点截断语义(非取模)。 */
    fun nextInt(maxValue: Int): Int = floor(sample() * maxValue).toInt()

    fun nextDouble(): Double = sample()

    companion object {
        private const val MBIG = 2147483647
        private const val MSEED = 161803398L
    }
}
