package com.dntof.slconsole.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

/** 液态玻璃总开关。默认 false,由设置页写入 DataStore。 */
val LocalLiquidGlass = staticCompositionLocalOf { false }

enum class AppLayout { Compact, Medium, Expanded }

val LocalAppLayout = staticCompositionLocalOf { AppLayout.Compact }

/** 整屏彩色背景的模糊底板。内容快照到达后不再被装饰底板覆盖。 */
class GlassPlateState {
    var bitmap: ImageBitmap? by mutableStateOf(null)
    var widthPx: Int by mutableIntStateOf(1)
    var heightPx: Int by mutableIntStateOf(1)
    var contentSnapshot: Boolean by mutableStateOf(false)
}

val LocalGlassPlate = staticCompositionLocalOf<GlassPlateState?> { null }

/**
 * 内容快照。玻璃只采样模糊后的位图,不把 GraphicsLayer 画进另一个 GraphicsLayer。
 * 硬件渲染的 RenderNode 树只要出现环,prepareTree 就会栈溢出。
 */
class LiveBlurState {
    var sharp: GraphicsLayer? = null
    var safeFrames: Int = 0

    /** 正在把内容录进快照。这段时间玻璃不能再引用任何离屏层。 */
    var recording: Boolean = false
    var failed: Boolean = false
    var onFailure: ((Throwable) -> Unit)? = null
    var capturedWidth: Int = 1
    var capturedHeight: Int = 1

    /** 下一次绘制是快照写回触发的,不要因此再排一次快照。 */
    var suppressNext: Boolean = false

    /** 正在把层导出成位图。这段时间不要重录,避免和快照抢同一块显示列表。 */
    var snapshotting: Boolean = false
    var lastRequestNs: Long = 0L
    val requests = Channel<Unit>(Channel.CONFLATED)

    fun reportFailure(error: Throwable) {
        if (failed) return
        failed = true
        android.util.Log.e("GlassGuard", "liquid glass draw failed", error)
        val callback = onFailure
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            callback?.invoke(error)
        }
    }
}

val LocalLiveBlur = staticCompositionLocalOf<LiveBlurState?> { null }

/**
 * 保留给调用方。模糊结果是位图,不需要帧时钟去推动另一层 RenderNode。
 */
@Composable
fun LiveBlurClock(state: LiveBlurState, enabled: Boolean) = Unit

/**
 * 把内容录进独立的 [GraphicsLayer],再把像素快照模糊成位图。
 * 玻璃表面只画这张位图。录制出来的层不会被画进另一个层,也不会被屏幕上的玻璃再次引用。
 */
@Composable
fun Modifier.captureBackdrop(): Modifier {
    val glass = LocalLiquidGlass.current
    val state = LocalLiveBlur.current
    val plate = LocalGlassPlate.current
    if (!glass || state == null || plate == null) return this
    val sharp = rememberGraphicsLayer()
    DisposableEffect(sharp, state) {
        state.sharp = sharp
        onDispose {
            if (state.sharp === sharp) state.sharp = null
        }
    }
    LaunchedEffect(sharp, state, plate) {
        for (ignored in state.requests) {
            val layer = state.sharp ?: continue
            val width = state.capturedWidth
            val height = state.capturedHeight
            state.snapshotting = true
            try {
                val image = layer.toImageBitmap()
                val software = image.asAndroidBitmap().let { raw ->
                    if (raw.config == Bitmap.Config.HARDWARE) {
                        raw.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        raw
                    }
                } ?: continue
                val blurred = withContext(Dispatchers.Default) {
                    blurContentSnapshot(software)
                }
                // 先挡住紧接着的那一次重绘,避免快照写回自己再触发快照。
                state.suppressNext = true
                plate.contentSnapshot = true
                plate.widthPx = width
                plate.heightPx = height
                plate.bitmap = blurred.asImageBitmap()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                android.util.Log.e("GlassGuard", "backdrop snapshot failed", error)
            } finally {
                state.snapshotting = false
            }
        }
    }
    return this.drawWithContent {
        if (state.failed || state.snapshotting) {
            drawContent()
            if (!state.failed) state.safeFrames++
            return@drawWithContent
        }
        val layerSize = IntSize(
            size.width.roundToInt().coerceAtLeast(1),
            size.height.roundToInt().coerceAtLeast(1),
        )
        var recorded = false
        val echo = state.suppressNext
        state.suppressNext = false
        try {
            // 录制时玻璃只画纯色兜底,显示列表里不会出现任何 GraphicsLayer。
            state.recording = true
            state.capturedWidth = layerSize.width
            state.capturedHeight = layerSize.height
            sharp.record(layerSize) {
                this@drawWithContent.drawContent()
                recorded = true
            }
            state.recording = false
            val now = System.nanoTime()
            if (!echo && !state.snapshotting && now - state.lastRequestNs > 200_000_000L) {
                state.lastRequestNs = now
                state.requests.trySend(Unit)
            }
            drawContent()
            state.safeFrames++
        } catch (error: Throwable) {
            state.recording = false
            if (error is CancellationException) throw error
            if (!recorded) {
                runCatching { drawContent() }
            }
            state.reportFailure(error)
        }
    }
}

enum class GlassRole { Chrome, Panel, Row }

@Composable
fun rememberGlassPlateState(): GlassPlateState = remember { GlassPlateState() }

/**
 * 全屏底色。玻璃关闭时是纯色背景;打开时叠上樱粉 / 长春花 / 薄荷色块,
 * 并异步生成一张低分辨率模糊底板,供玻璃表面采样。
 */
@Composable
fun AppBackdrop(glassEnabled: Boolean, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val plate = LocalGlassPlate.current
    val background = MaterialTheme.colorScheme.background
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(glassEnabled, dark, size, plate) {
        val target = plate ?: return@LaunchedEffect
        if (!glassEnabled || size.width <= 0 || size.height <= 0) {
            target.bitmap = null
            target.contentSnapshot = false
            return@LaunchedEffect
        }
        val widthPx = size.width
        val heightPx = size.height
        if (target.contentSnapshot) return@LaunchedEffect
        val bitmap = try {
            withContext(Dispatchers.Default) {
                renderGlassPlate(widthPx, heightPx, dark)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (!target.contentSnapshot) target.bitmap = null
            return@LaunchedEffect
        }
        if (target.contentSnapshot) return@LaunchedEffect
        target.bitmap = bitmap.asImageBitmap()
        target.widthPx = widthPx
        target.heightPx = heightPx
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .background(background),
    ) {
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
 * 玻璃关闭时是带细边框的纯色表面;打开时采样模糊底板,并叠上半透明罩、高光和渐变描边。
 * 底板尚未就绪或生成失败时退回半透明纯色,不依赖特定系统版本的模糊 API。
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
    val plate = LocalGlassPlate.current ?: return this
    val sampled = plate.contentSnapshot
    val tintAlpha = when (role) {
        GlassRole.Chrome -> if (sampled) if (dark) 0.22f else 0.18f else if (dark) 0.55f else 0.50f
        GlassRole.Panel -> if (sampled) if (dark) 0.34f else 0.28f else if (dark) 0.62f else 0.58f
        GlassRole.Row -> if (dark) 0.72f else 0.68f
    }
    val tint = if (dark) Color(0xFF120E18).copy(alpha = tintAlpha) else Color.White.copy(alpha = tintAlpha)
    val fallback = if (dark) Color(0xFF231C2E).copy(alpha = 0.90f) else Color(0xFFFFF7FB).copy(alpha = 0.90f)
    return this.then(
        LiquidGlassElement(
            shape = shape,
            plate = plate,
            live = LocalLiveBlur.current,
            tint = tint,
            fallback = fallback,
            highlightAlpha = if (dark) 0.22f else 0.45f,
            borderStart = if (dark) 0.70f else 0.95f,
            borderEnd = if (dark) 0.12f else 0.28f,
            framed = framed,
        ),
    )
}

private data class LiquidGlassElement(
    val shape: Shape,
    val plate: GlassPlateState,
    val live: LiveBlurState?,
    val tint: Color,
    val fallback: Color,
    val highlightAlpha: Float,
    val borderStart: Float,
    val borderEnd: Float,
    val framed: Boolean,
) : ModifierNodeElement<LiquidGlassNode>() {
    override fun create(): LiquidGlassNode = LiquidGlassNode(
        shape, plate, live, tint, fallback, highlightAlpha, borderStart, borderEnd, framed,
    )

    override fun update(node: LiquidGlassNode) {
        node.updateStyle(
            shape, plate, live, tint, fallback, highlightAlpha, borderStart, borderEnd, framed,
        )
    }
}

private class LiquidGlassNode(
    var shape: Shape,
    var plate: GlassPlateState,
    var live: LiveBlurState?,
    var tint: Color,
    var fallback: Color,
    var highlightAlpha: Float,
    var borderStart: Float,
    var borderEnd: Float,
    var framed: Boolean,
) : Modifier.Node(), DrawModifierNode, ObserverModifierNode {

    private val path = Path()
    private var bitmap: ImageBitmap? = null
    private var plateWidth = 1
    private var plateHeight = 1

    fun updateStyle(
        shape: Shape,
        plate: GlassPlateState,
        live: LiveBlurState?,
        tint: Color,
        fallback: Color,
        highlightAlpha: Float,
        borderStart: Float,
        borderEnd: Float,
        framed: Boolean,
    ) {
        val plateChanged = this.plate !== plate
        val liveChanged = this.live !== live
        this.shape = shape
        this.plate = plate
        this.live = live
        this.tint = tint
        this.fallback = fallback
        this.highlightAlpha = highlightAlpha
        this.borderStart = borderStart
        this.borderEnd = borderEnd
        this.framed = framed
        if (plateChanged || liveChanged) observePlate()
        invalidateDraw()
    }

    override fun onAttach() {
        observePlate()
    }

    private fun observePlate() {
        observeReads {
            bitmap = plate.bitmap
            plateWidth = plate.widthPx
            plateHeight = plate.heightPx
        }
    }

    override fun onObservedReadsChanged() {
        observePlate()
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        try {
            drawGlass()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            drawRect(fallback)
            drawContent()
            live?.reportFailure(error)
        }
    }

    private fun ContentDrawScope.drawGlass() {
        val outline = shape.createOutline(size, layoutDirection, this)
        path.reset()
        path.addOutline(outline)
        clipPath(path) {
            val coords = runCatching { requireLayoutCoordinates() }.getOrNull()
            val pos = if (coords != null && coords.isAttached) coords.positionInRoot() else Offset.Zero
            // 录制进快照时只留纯色,避免把底板或离屏层再画进正在录的层。
            val image = if (live?.recording == true) null else bitmap
            if (image != null && plateWidth > 0 && plateHeight > 0) {
                val scaleX = image.width.toFloat() / plateWidth
                val scaleY = image.height.toFloat() / plateHeight
                val srcLeft = (pos.x * scaleX).roundToInt().coerceIn(0, image.width - 1)
                val srcTop = (pos.y * scaleY).roundToInt().coerceIn(0, image.height - 1)
                val srcW = (size.width * scaleX).roundToInt().coerceAtLeast(1)
                    .coerceAtMost(image.width - srcLeft)
                val srcH = (size.height * scaleY).roundToInt().coerceAtLeast(1)
                    .coerceAtMost(image.height - srcTop)
                drawImage(
                    image,
                    srcOffset = IntOffset(srcLeft, srcTop),
                    srcSize = IntSize(srcW, srcH),
                    dstSize = IntSize(
                        size.width.roundToInt().coerceAtLeast(1),
                        size.height.roundToInt().coerceAtLeast(1),
                    ),
                )
            } else {
                drawRect(fallback)
            }
            drawRect(tint)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = highlightAlpha), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.55f,
                ),
                size = Size(size.width, size.height * 0.55f),
            )
        }
        drawContent()
        if (framed) {
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = borderStart), Color.White.copy(alpha = borderEnd)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** 把内容快照缩小后再做盒式模糊。结果是位图,不进入 RenderNode 树。 */
internal fun blurContentSnapshot(source: Bitmap): Bitmap {
    val bw = (source.width / 8).coerceIn(48, 200)
    val bh = (source.height.toFloat() / source.width.coerceAtLeast(1) * bw).roundToInt().coerceIn(64, 360)
    val small = Bitmap.createScaledBitmap(source, bw, bh, true)
    boxBlur(small, radius = 6)
    return small
}

/** 把当前窗口的彩色背景画到小图上再做两次盒式模糊。任何 API 都能跑,失败时调用方退回纯色。 */
internal fun renderGlassPlate(widthPx: Int, heightPx: Int, dark: Boolean): Bitmap {
    val bw = (widthPx / 5).coerceIn(72, 280)
    val bh = (heightPx.toFloat() / widthPx * bw).roundToInt().coerceIn(96, 520)
    val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(if (dark) 0xFF131019.toInt() else 0xFFFBF4F8.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun blob(cx: Float, cy: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            color,
            color and 0x00FFFFFF,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }
    if (dark) {
        blob(bw * 0.12f, bh * 0.06f, bw * 0.85f, 0xCCFF7AA8.toInt())
        blob(bw * 0.92f, bh * 0.20f, bw * 0.70f, 0x997C8CFF.toInt())
        blob(bw * 0.35f, bh * 0.92f, bw * 0.90f, 0x665ED0B0.toInt())
    } else {
        blob(bw * 0.10f, bh * 0.04f, bw * 0.80f, 0xB3FF8FB8.toInt())
        blob(bw * 0.95f, bh * 0.18f, bw * 0.68f, 0x998EA0FF.toInt())
        blob(bw * 0.40f, bh * 0.95f, bw * 0.85f, 0x737DDEC4)
    }
    boxBlur(bitmap, radius = 12)
    return bitmap
}

private fun boxBlur(bitmap: Bitmap, radius: Int) {
    if (radius < 1) return
    val w = bitmap.width
    val h = bitmap.height
    val pix = IntArray(w * h)
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)
    val tmp = IntArray(pix.size)
    repeat(2) {
        boxBlurHorizontal(pix, tmp, w, h, radius)
        boxBlurVertical(tmp, pix, w, h, radius)
    }
    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
}

private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val div = r * 2 + 1
    for (y in 0 until h) {
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0
        val row = y * w
        for (k in -r..r) {
            val c = src[row + k.coerceIn(0, w - 1)]
            sumA += c ushr 24
            sumR += (c shr 16) and 0xFF
            sumG += (c shr 8) and 0xFF
            sumB += c and 0xFF
        }
        for (x in 0 until w) {
            dst[row + x] = argb(sumA / div, sumR / div, sumG / div, sumB / div)
            val remove = src[row + (x - r).coerceIn(0, w - 1)]
            val add = src[row + (x + r + 1).coerceIn(0, w - 1)]
            sumA += (add ushr 24) - (remove ushr 24)
            sumR += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            sumG += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            sumB += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val div = r * 2 + 1
    for (x in 0 until w) {
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0
        for (k in -r..r) {
            val c = src[k.coerceIn(0, h - 1) * w + x]
            sumA += c ushr 24
            sumR += (c shr 16) and 0xFF
            sumG += (c shr 8) and 0xFF
            sumB += c and 0xFF
        }
        for (y in 0 until h) {
            dst[y * w + x] = argb(sumA / div, sumR / div, sumG / div, sumB / div)
            val remove = src[(y - r).coerceIn(0, h - 1) * w + x]
            val add = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
            sumA += (add ushr 24) - (remove ushr 24)
            sumR += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            sumG += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            sumB += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun argb(a: Int, r: Int, g: Int, b: Int): Int {
    return (a.coerceIn(0, 255) shl 24) or
        (r.coerceIn(0, 255) shl 16) or
        (g.coerceIn(0, 255) shl 8) or
        b.coerceIn(0, 255)
}
