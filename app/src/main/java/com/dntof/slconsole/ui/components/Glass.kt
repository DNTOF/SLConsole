package com.dntof.slconsole.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
 * 背景模糊层。只在 API 31+ 使用,而且这层的显示列表里只有缩小后的背景,
 * 背景录制时玻璃表面完全不参与,所以两层不会互相引用。
 */
class LiveBlurState {
    var safeFrames: Int = 0

    /** 正在录背景。玻璃表面在这段时间什么都不画,包括自己的文字。 */
    var recording: Boolean = false
    var failed: Boolean = false
    var onFailure: ((Throwable) -> Unit)? = null
    var capturedWidth: Int = 1
    var capturedHeight: Int = 1
    var blurNode: RenderNode? = null
    val gpu: Boolean = Build.VERSION.SDK_INT >= 31

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
 * 保留给调用方。模糊在同一次绘制里完成,不需要额外的帧时钟。
 */
@Composable
fun LiveBlurClock(state: LiveBlurState, enabled: Boolean) = Unit

/**
 * 录下不含玻璃表面的背景,再用一块独立的缩小 RenderNode 做 GPU 模糊。
 * 这块模糊层不会被画进背景录制,背景录制也不会包含它,避免 RenderNode 成环。
 * 只在内容本身重绘时更新(滚动、数据变化),不读回像素,也不挡第一帧。
 */
@Composable
fun Modifier.captureBackdrop(): Modifier {
    val glass = LocalLiquidGlass.current
    val state = LocalLiveBlur.current
    if (!glass || state == null || !state.gpu) return this
    val sharp = rememberGraphicsLayer()
    val view = androidx.compose.ui.platform.LocalView.current
    return this.drawWithContent {
        if (state.failed) {
            drawContent()
            return@drawWithContent
        }
        val layerSize = IntSize(
            size.width.roundToInt().coerceAtLeast(1),
            size.height.roundToInt().coerceAtLeast(1),
        )
        var recorded = false
        try {
            // 第一帧先把内容画出来,模糊层从下一帧开始,避免启动时卡在离屏录制上。
            if (state.safeFrames == 0) {
                drawContent()
                state.safeFrames = 1
                view.postInvalidateOnAnimation()
                return@drawWithContent
            }
            state.recording = true
            state.capturedWidth = layerSize.width
            state.capturedHeight = layerSize.height
            sharp.record(layerSize) {
                this@drawWithContent.drawContent()
                recorded = true
            }
            state.recording = false
            updateGpuBlur(state, sharp, layerSize.width, layerSize.height)
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

private fun updateGpuBlur(state: LiveBlurState, sharp: GraphicsLayer, width: Int, height: Int) {
    if (!state.gpu) return
    try {
        updateGpuBlurInner(state, sharp, width, height)
    } catch (error: Throwable) {
        android.util.Log.e("GlassGuard", "gpu blur update failed", error)
    }
}

private fun updateGpuBlurInner(state: LiveBlurState, sharp: GraphicsLayer, width: Int, height: Int) {
    val source = androidRenderNode(sharp) ?: return
    val dw = (width / 4).coerceAtLeast(8)
    val dh = (height / 4).coerceAtLeast(8)
    val node = state.blurNode ?: RenderNode("slc-glass-blur").also { state.blurNode = it }
    if (node.width != dw || node.height != dh) {
        node.setPosition(0, 0, dw, dh)
    }
    // 半径按缩小后的像素计,放大回屏幕大约是 28px,能看出背后的色块但不会糊成一片白。
    val radius = 7f
    val blur = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
    val saturate = android.graphics.ColorMatrix().apply { setSaturation(1.15f) }
    node.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturate), blur))
    val canvas = node.beginRecording()
    try {
        canvas.save()
        canvas.scale(dw.toFloat() / width, dh.toFloat() / height)
        canvas.drawRenderNode(source)
        canvas.restore()
    } finally {
        node.endRecording()
    }
}

private val graphicsLayerImplMethod by lazy(LazyThreadSafetyMode.NONE) {
    GraphicsLayer::class.java.getMethod("getImpl\$ui_graphics_release")
}

private fun androidRenderNode(layer: GraphicsLayer): RenderNode? {
    return try {
        val impl = graphicsLayerImplMethod.invoke(layer) ?: return null
        val field = impl.javaClass.getDeclaredField("renderNode")
        field.isAccessible = true
        val value = field.get(impl)
        value as? RenderNode
    } catch (error: Throwable) {
        android.util.Log.e("GlassGuard", "backdrop render node unavailable", error)
        null
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
    val gpu = LocalLiveBlur.current?.gpu == true
    // 只有顶栏和底栏采样实时背景。卡片若也采样,就会把刚录进去的自己的字再糊一遍。
    val samplesBackdrop = role == GlassRole.Chrome && gpu
    val tintAlpha = when (role) {
        GlassRole.Chrome -> if (samplesBackdrop) if (dark) 0.10f else 0.06f else if (dark) 0.55f else 0.50f
        GlassRole.Panel -> if (dark) 0.22f else 0.16f
        GlassRole.Row -> if (dark) 0.55f else 0.48f
    }
    val tint = if (dark) Color(0xFF120E18).copy(alpha = tintAlpha) else Color.White.copy(alpha = tintAlpha)
    val fallback = if (dark) Color(0xFF231C2E).copy(alpha = 0.90f) else Color(0xFFFFF7FB).copy(alpha = 0.90f)
    return this.then(
        LiquidGlassElement(
            shape = shape,
            plate = plate,
            live = if (samplesBackdrop) LocalLiveBlur.current else null,
            tint = tint,
            fallback = fallback,
            highlightAlpha = if (samplesBackdrop) 0.10f else if (dark) 0.16f else 0.22f,
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
        // 录背景时不能把模糊层再画进去,否则两层互相引用。内容本身要留下,底栏才能模糊到它。
        if (live?.recording == true) {
            drawContent()
            return
        }
        val outline = shape.createOutline(size, layoutDirection, this)
        path.reset()
        path.addOutline(outline)
        clipPath(path) {
            val coords = runCatching { requireLayoutCoordinates() }.getOrNull()
            val pos = if (coords != null && coords.isAttached) coords.positionInRoot() else Offset.Zero
            val node = live?.blurNode
            val fullW = live?.capturedWidth ?: 0
            val fullH = live?.capturedHeight ?: 0
            if (node != null && fullW > 0 && fullH > 0 && node.width > 0 && node.height > 0) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(-pos.x, -pos.y)
                    native.scale(fullW.toFloat() / node.width, fullH.toFloat() / node.height)
                    native.drawRenderNode(node)
                    native.restore()
                }
            } else {
                val image = bitmap
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
