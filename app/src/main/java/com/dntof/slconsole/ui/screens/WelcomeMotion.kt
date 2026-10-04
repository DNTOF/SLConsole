package com.dntof.slconsole.ui.screens

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()

/** 系统「动画时长缩放」设为 0（关闭动画）时返回 true，欢迎页就不动。 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) == 0f
    }
}

/**
 * 欢迎页背景用的慢速相位，0 到 1 循环，一圈 18 秒。
 * 只在欢迎页在画面里时调用，离开这一步动画就停了。关闭动画时返回一个固定值。
 */
@Composable
fun rememberWelcomePhase(reduceMotion: Boolean): State<Float> {
    if (reduceMotion) return remember { mutableFloatStateOf(0.125f) }
    val transition = rememberInfiniteTransition(label = "welcome")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing)),
        label = "welcomePhase",
    )
}

/**
 * 两团缓慢漂移的柔光。渐变笔刷只在尺寸或配色变化时建一次，
 * 每帧只读相位、做平移，不分配对象；读相位放在绘制阶段，不会触发重组。
 */
@Composable
fun WelcomeBackdrop(phase: State<Float>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    // 粉色主色加一点靛蓝。浅色下靛蓝压低一些，免得发灰。
    val first = scheme.primary.copy(alpha = if (dark) 0.26f else 0.20f)
    val second = scheme.secondary.copy(alpha = if (dark) 0.22f else 0.13f)
    Spacer(
        modifier.drawWithCache {
            val big = size.minDimension * 0.85f
            val small = size.minDimension * 0.65f
            val firstBrush = Brush.radialGradient(listOf(first, Color.Transparent), center = Offset.Zero, radius = big)
            val secondBrush = Brush.radialGradient(listOf(second, Color.Transparent), center = Offset.Zero, radius = small)
            val w = size.width
            val h = size.height
            onDrawBehind {
                val p = phase.value * TWO_PI
                translate(w * (0.15f + 0.10f * cos(p)), h * (0.20f + 0.06f * sin(p))) {
                    drawCircle(firstBrush, radius = big, center = Offset.Zero)
                }
                translate(w * (0.88f + 0.08f * sin(p)), h * (0.66f + 0.07f * cos(2f * p))) {
                    drawCircle(secondBrush, radius = small, center = Offset.Zero)
                }
            }
        },
    )
}

/** 图标后面的一圈柔光，随相位轻轻呼吸。 */
fun Modifier.logoGlow(phase: State<Float>, color: Color, radius: Dp): Modifier = drawWithCache {
    val r = radius.toPx()
    val brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset.Zero, radius = r)
    val cx = size.width / 2f
    val cy = size.height / 2f
    onDrawBehind {
        val breathe = 0.7f + 0.3f * sin(phase.value * TWO_PI * 3f)
        translate(cx, cy) {
            drawCircle(brush, radius = r, center = Offset.Zero, alpha = breathe)
        }
    }
}

/**
 * 依次淡入并上移。progress 是整段入场的 0 到 1，index 决定先后。
 * 只在图层阶段读进度，不触发重组。
 */
fun Modifier.staggerIn(progress: () -> Float, index: Int, distance: Dp = 18.dp): Modifier = graphicsLayer {
    val start = index * 0.13f
    val local = ((progress() - start) / 0.48f).coerceIn(0f, 1f)
    val inv = 1f - local
    val eased = 1f - inv * inv * inv
    alpha = eased
    translationY = (1f - eased) * distance.toPx()
}
