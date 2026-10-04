package com.dntof.slconsole.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Shadow
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
 * 官方签名徽标，仿 Windows 7「系统」属性页右下角的正版软件徽标：
 * 深蓝渐变底、顶部一层浅色高光、细边框、小圆角，白字左对齐，右上角一个小星形图标。
 * 星形是自己画的，不用任何微软标志。原版是一块蓝牌子，浅色和深色模式都保持同一套颜色。
 */
@Composable
fun GenuineBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val textShadow = Shadow(Color(0x66000A1E), Offset(0f, 1.5f), 2f)
    Box(
        modifier
            .width(156.dp)
            .height(50.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to Color(0xFF4F8BC2),
                    0.4f to Color(0xFF285D9A),
                    1f to Color(0xFF143768),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
            .border(1.dp, Color(0xFF8AA3C0), shape)
            .clearAndSetSemantics { contentDescription = "官方签名：使用 DNT_OF 系列程序，安全 稳定 声誉" },
    ) {
        // 上半部分的玻璃高光，原版顶部几行明显更亮。
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .padding(1.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.36f),
                        0.6f to Color.White.copy(alpha = 0.12f),
                        1f to Color.White.copy(alpha = 0f),
                    ),
                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                ),
        )
        // 内侧一圈很淡的亮边。
        Box(
            Modifier
                .matchParentSize()
                .padding(1.dp)
                .border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(3.dp)),
        )
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
                    listOf(Color(0xFFFFFFFF), Color(0xFF9ED4F7), Color(0xFF5FB2EA)),
                ),
            )
            drawSparkle(
                center = Offset(size.width * 0.16f, size.height * 0.18f),
                radius = size.minDimension * 0.17f,
                brush = Brush.linearGradient(listOf(Color(0xFFFFD6A8), Color(0xFFF29A4A))),
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
                    withStyle(SpanStyle(fontSize = 9.sp, color = Color(0xFFE3F0FB))) { append("使用 ") }
                    withStyle(SpanStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)) {
                        append("DNT_OF 系列程序")
                    }
                },
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 15.sp,
                    shadow = textShadow,
                ),
            )
            Text(
                "安全 稳定 声誉",
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    color = Color(0xFFD3E7F8),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    letterSpacing = 1.5.sp,
                    shadow = textShadow,
                ),
            )
        }
    }
}

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
