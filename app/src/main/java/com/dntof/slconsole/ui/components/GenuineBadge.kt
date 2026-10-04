// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * 官方签名徽标。版式仿 Windows 7「系统」属性页右下角的正版软件徽标：
 * 小圆角牌子、两行左对齐文字、右上角一个小星形图标（自己画的，不用任何微软标志）。
 * 配色换成柔和的二次元渐变：樱花粉 → 薰衣草紫 → 天空蓝，深色模式整体压暗约 18%，免得发光。
 * 文字用深紫色，浅色模式主行对比度 ≥ 6.6:1，深色模式 ≥ 6:1，都满足 WCAG AA。
 */
@Composable
fun GenuineBadge(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        BadgeColors(
            background = listOf(Color(0xFFCA97AB), Color(0xFFA595C6), Color(0xFF8BAEC9)),
            border = Color(0xFF8E7DB4),
            primaryText = Color(0xFF241C33),
            secondaryText = Color(0xFF2E2640),
        )
    } else {
        BadgeColors(
            background = listOf(Color(0xFFF6B8D1), Color(0xFFC9B6F2), Color(0xFFA9D4F5)),
            border = Color(0xFFC3A9DE),
            primaryText = Color(0xFF3A3150),
            secondaryText = Color(0xFF4A4060),
        )
    }
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
            .clearAndSetSemantics { contentDescription = "官方签名：使用 DNT_OF 系列程序，安全 稳定 声誉" },
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
                    listOf(Color(0xFFFFFFFF), Color(0xFFFFDDEB), Color(0xFFF59AC2)),
                ),
            )
            drawSparkle(
                center = Offset(size.width * 0.16f, size.height * 0.18f),
                radius = size.minDimension * 0.17f,
                brush = Brush.linearGradient(listOf(Color(0xFFFFF1C4), Color(0xFFF2BE4E))),
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
                "安全 稳定 声誉",
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
