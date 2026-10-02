package com.dntof.slconsole.ui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/** 液态玻璃总开关。默认 false,由设置页写入 DataStore。 */
val LocalLiquidGlass = staticCompositionLocalOf { false }

enum class AppLayout { Compact, Medium, Expanded }

val LocalAppLayout = staticCompositionLocalOf { AppLayout.Compact }

/**
 * 卡片采样的背景。只有色块,没有卡片自己的字。
 * 顶栏和底栏另用 [LocalChromeBackdrop],那一层包含正在滚动的内容。
 */
val LocalOrbBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 顶栏、底栏和侧栏采样的内容层。玻璃表面本身不在这层里面。 */
val LocalChromeBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

enum class GlassRole { Chrome, Panel, Row }

/**
 * 两块背景层。同一段绘制命令可以共用,两层各自有独立的离屏缓冲。
 */
@Composable
fun rememberGlassBackdrops(): Pair<LayerBackdrop, LayerBackdrop> {
    val background = MaterialTheme.colorScheme.background
    val onDraw = remember(background) {
        val block: ContentDrawScope.() -> Unit = {
            drawRect(background)
            drawContent()
        }
        block
    }
    return rememberLayerBackdrop(onDraw = onDraw) to rememberLayerBackdrop(onDraw = onDraw)
}

/**
 * 全屏底色。玻璃关闭时是纯色;打开时叠上樱粉、长春花和薄荷色块,供 Backdrop 采样。
 */
@Composable
fun AppBackdrop(glassEnabled: Boolean, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val background = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(background)) {
        if (glassEnabled) {
            val sakura = if (dark) Color(0xFFFF7AA8) else Color(0xFFFF8FB8)
            val periwinkle = if (dark) Color(0xFF7C8CFF) else Color(0xFF8EA0FF)
            val mint = if (dark) Color(0xFF5ED0B0) else Color(0xFF7DDEC4)
            Orb(Modifier.size(460.dp).offset(x = (-120).dp, y = (-140).dp), sakura, if (dark) 0.55f else 0.42f)
            Orb(Modifier.size(380.dp).offset(x = 180.dp, y = 40.dp), periwinkle, if (dark) 0.40f else 0.34f)
            Orb(Modifier.size(520.dp).offset(x = (-40).dp, y = 420.dp), mint, if (dark) 0.28f else 0.30f)
        }
    }
}

@Composable
private fun Orb(modifier: Modifier, color: Color, alpha: Float) {
    Box(
        modifier.background(
            Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent)),
            CircleShape,
        ),
    )
}

/**
 * 玻璃关闭时是带细边框的纯色表面。打开时用 Backdrop 采样背后的层。
 * 模糊需要 Android 12,折射需要 Android 13;更低版本只留半透明罩色。
 */
@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: GlassRole = GlassRole.Panel,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable () -> Unit,
) {
    val glass = LocalLiquidGlass.current
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "glassPress",
    )
    val glassModifier = if (glass) {
        Modifier.liquidGlass(shape = shape, role = role, dark = dark)
    } else {
        Modifier
    }
    val motion = Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
    val container = if (glass) Color.Transparent else scheme.surfaceContainerHigh
    val border = if (glass) {
        null
    } else {
        androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant)
    }
    val elevation = if (!glass && role == GlassRole.Chrome) 8.dp else 0.dp
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.then(motion).then(glassModifier),
            enabled = enabled,
            shape = shape,
            color = container,
            contentColor = scheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = if (glass) 0.dp else elevation,
            border = border,
            interactionSource = interaction,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.then(motion).then(glassModifier),
            shape = shape,
            color = container,
            contentColor = scheme.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = elevation,
            border = border,
            content = content,
        )
    }
}

@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    role: GlassRole,
    dark: Boolean,
    framed: Boolean = true,
): Modifier {
    if (!LocalLiquidGlass.current) return this
    // 卡片只采样色块层,避免把正在录进内容层的自己的字再糊一遍。
    val backdrop = when (role) {
        GlassRole.Chrome -> LocalChromeBackdrop.current
        GlassRole.Panel, GlassRole.Row -> LocalOrbBackdrop.current
    } ?: return this
    val blurReady = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val tintAlpha = when (role) {
        GlassRole.Chrome -> if (blurReady) if (dark) 0.16f else 0.12f else if (dark) 0.62f else 0.68f
        GlassRole.Panel -> if (blurReady) if (dark) 0.22f else 0.18f else if (dark) 0.72f else 0.78f
        GlassRole.Row -> if (blurReady) if (dark) 0.42f else 0.36f else if (dark) 0.78f else 0.82f
    }
    val tint = if (dark) Color(0xFF120E18).copy(alpha = tintAlpha) else Color.White.copy(alpha = tintAlpha)
    // 折射着色器只放在胶囊和侧栏上。每张卡片再跑一遍会把滚动帧拖垮。
    val canRefract = role == GlassRole.Chrome &&
        blurReady &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        shape is CornerBasedShape
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(if (role == GlassRole.Chrome) 16.dp.toPx() else 8.dp.toPx())
            if (canRefract) {
                lens(10.dp.toPx(), 18.dp.toPx())
            }
        },
        highlight = { if (framed && role == GlassRole.Chrome) Highlight.Default else null },
        shadow = { null },
        onDrawSurface = { drawRect(tint) },
    )
}
