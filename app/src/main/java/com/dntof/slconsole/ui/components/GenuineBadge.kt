// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.security.MessageDigest

/** 指纹第二段（异或后），见 SignatureCheck.officialFingerprint。 */
internal val badgeSeed = intArrayOf(0xB4, 0xC2, 0x6C, 0x41, 0x7D, 0x17, 0xDA, 0xAA, 0x3C, 0x7C, 0x0E)

/**
 * 徽标配色按证书指纹加密保存：浅色 6 个、深色 6 个、星星 5 个。
 * 只有用官方证书的指纹才能解出正确的颜色，单纯去掉签名判断只会得到乱色。
 */
private val encodedPalette = longArrayOf(
    0xC2D6B274L, 0xC952C352L, 0x8CFF7570L, 0x73C46006L, 0x2A3F6775L, 0xB517B01AL,
    0xC1035F4BL, 0x5502C5F0L, 0xC2ABA46CL, 0xC9150814L, 0x8C72BDB6L, 0x7329EF98L,
    0x2AFAA9DAL, 0xB5A22D91L, 0xC13C5222L, 0x5558A1F2L, 0xC2D2B4EBL,
)
/** 正确配色的 SHA-256 前 4 字节。用摘要而不是简单求和，避免改动几位后正好抵消。 */
private const val PALETTE_CHECKSUM = 0x37E09E39

/** 用证书指纹解出徽标配色；指纹不对时返回 null。 */
fun decodeBadgePalette(key: ByteArray): List<Color>? {
    if (key.size != 32) return null
    val raw = ByteArray(encodedPalette.size * 4)
    val colors = encodedPalette.mapIndexed { i, encoded ->
        val o = (i * 4) % 32
        val k = ((key[o].toLong() and 0xFF) shl 24) or ((key[o + 1].toLong() and 0xFF) shl 16) or
            ((key[o + 2].toLong() and 0xFF) shl 8) or (key[o + 3].toLong() and 0xFF)
        val argb = ((encoded xor k) and 0xFFFFFFFFL).toInt()
        raw[i * 4] = (argb ushr 24).toByte()
        raw[i * 4 + 1] = (argb ushr 16).toByte()
        raw[i * 4 + 2] = (argb ushr 8).toByte()
        raw[i * 4 + 3] = argb.toByte()
        Color(argb)
    }
    val d = MessageDigest.getInstance("SHA-256").digest(raw)
    val check = ((d[0].toInt() and 0xFF) shl 24) or ((d[1].toInt() and 0xFF) shl 16) or
        ((d[2].toInt() and 0xFF) shl 8) or (d[3].toInt() and 0xFF)
    return if (check == PALETTE_CHECKSUM) colors else null
}

/**
 * 官方签名徽标。版式仿 Windows 7「系统」属性页右下角的正版软件徽标：
 * 小圆角牌子、两行左对齐文字、右上角一个小星形图标（自己画的，不用任何微软标志）。
 * 配色是柔和的二次元渐变：樱花粉 → 薰衣草紫 → 天空蓝，深色模式整体压暗约 18%，免得发光。
 * 颜色由 [decodeBadgePalette] 用证书指纹解出。
 * 文字用深紫色，浅色模式主行对比度 ≥ 6.6:1，深色模式 ≥ 6:1，都满足 WCAG AA。
 */
@Composable
fun GenuineBadge(palette: List<Color>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        BadgeColors(
            background = palette.subList(6, 9),
            border = palette[9],
            primaryText = palette[10],
            secondaryText = palette[11],
        )
    } else {
        BadgeColors(
            background = palette.subList(0, 3),
            border = palette[3],
            primaryText = palette[4],
            secondaryText = palette[5],
        )
    }
    val sparkle = palette.subList(12, 17)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .width(156.dp)
            .height(50.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors.background,
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
            .border(1.dp, colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = "官方签名：使用 DNT_OF 系列程序" },
    ) {
        Canvas(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 3.dp, end = 4.dp)
                .size(19.dp),
        ) {
            drawSparkle(
                center = Offset(size.width * 0.58f, size.height * 0.56f),
                radius = size.minDimension * 0.44f,
                brush = Brush.linearGradient(
                    sparkle.subList(0, 3),
                ),
            )
            drawSparkle(
                center = Offset(size.width * 0.16f, size.height * 0.18f),
                radius = size.minDimension * 0.17f,
                brush = Brush.linearGradient(sparkle.subList(3, 5)),
            )
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp, end = 23.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 9.sp, color = colors.secondaryText)) { append("使用 ") }
                    withStyle(SpanStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)) {
                        append("DNT_OF 系列程序")
                    }
                },
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    color = colors.primaryText,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 15.sp,
                ),
            )
            Text(
                "官方签名",
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    color = colors.secondaryText,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    letterSpacing = 1.5.sp,
                ),
            )
        }
    }
}

private class BadgeColors(
    val background: List<Color>,
    val border: Color,
    val primaryText: Color,
    val secondaryText: Color,
)

/** 四角星：四个尖角，中间向内收的弧边。 */
private fun DrawScope.drawSparkle(center: Offset, radius: Float, brush: Brush) {
    val waist = radius * 0.22f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + waist, center.y - waist, center.x + radius, center.y)
        quadraticTo(center.x + waist, center.y + waist, center.x, center.y + radius)
        quadraticTo(center.x - waist, center.y + waist, center.x - radius, center.y)
        quadraticTo(center.x - waist, center.y - waist, center.x, center.y - radius)
        close()
    }
    drawPath(path, brush)
}
