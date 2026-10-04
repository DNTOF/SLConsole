package com.dntof.slconsole.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dntof.slconsole.R
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme

/** 作者主页。地址留空的按钮不会显示。 */
object AuthorLinks {
    const val GITHUB = "https://github.com/DNTOF"
    const val AFDIAN = "https://afdian.com/a/DNT_OF"
    const val BILIBILI = "https://space.bilibili.com/3493125592975851"
}

private val AuthorOrange = Color(0xFFD97757)
private val AuthorInk = Color(0xFF141413)
private val AuthorCream = Color(0xFFECE9E0)
private val AuthorCardLight = Color(0xFFF5F3EC)
private val AuthorMutedLight = Color(0xFF6B6860)
private val AuthorMutedDark = Color(0xFFB7B2A8)
private val AuthorEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val CardShape = RoundedCornerShape(20.dp)
private val Pill = RoundedCornerShape(percent = 50)

private val AuthorSerif = FontFamily(Font(R.font.lora_regular, FontWeight.Normal))
private val AuthorSans = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AuthorCard(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val card = if (dark) AuthorInk else AuthorCardLight
    val ink = if (dark) AuthorCream else AuthorInk
    val muted = if (dark) AuthorMutedDark else AuthorMutedLight
    val border = if (dark) AuthorCream.copy(alpha = 0.16f) else AuthorInk.copy(alpha = 0.12f)
    val reducedMotion = prefersReducedMotion()
    var shown by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = AuthorEase),
        label = "authorCard",
    )
    val rise = with(LocalDensity.current) { 16.dp.toPx() }
    val links = listOf(
        AuthorLink("GitHub", AuthorLinks.GITHUB, primary = true),
        AuthorLink("爱发电", AuthorLinks.AFDIAN, primary = false),
        AuthorLink("哔哩哔哩", AuthorLinks.BILIBILI, primary = false),
    ).filter { it.url.isNotBlank() }

    CompositionLocalProvider(LocalIndication provides ripple(color = AuthorOrange)) {
        Column(
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress
                    translationY = (1f - progress) * rise
                }
                .clip(CardShape)
                .background(card)
                .border(1.dp, border, CardShape)
                .drawBehind {
                    val center = Offset(size.width * 0.92f, size.height * 0.02f)
                    val radius = size.minDimension * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AuthorOrange.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "作者",
                    color = muted,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 21.sp,
                )
                Text(
                    "DNT_OF",
                    color = ink,
                    fontFamily = AuthorSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 40.sp,
                    lineHeight = 48.sp,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                links.forEach { link ->
                    if (link.primary) {
                        AuthorPrimaryButton(link.label, link.url)
                    } else {
                        AuthorOutlineButton(link.label, link.url, ink, border)
                    }
                }
            }
        }
    }
}

private data class AuthorLink(val label: String, val url: String, val primary: Boolean)

@Composable
private fun AuthorPrimaryButton(label: String, url: String) {
    val context = LocalContext.current
    Button(
        onClick = { openExternal(context, url) },
        modifier = Modifier.heightIn(min = 44.dp),
        shape = Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthorOrange,
            contentColor = AuthorInk,
        ),
    ) {
        Text(
            label,
            fontFamily = AuthorSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun AuthorOutlineButton(label: String, url: String, ink: Color, border: Color) {
    val context = LocalContext.current
    val chinese = label.any { it.code > 0x2E80 }
    OutlinedButton(
        onClick = { openExternal(context, url) },
        modifier = Modifier.heightIn(min = 44.dp),
        shape = Pill,
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
    ) {
        Text(
            label,
            fontFamily = if (chinese) FontFamily.Serif else AuthorSans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = if (chinese) 24.sp else 20.sp,
        )
    }
}

@Composable
private fun prefersReducedMotion(): Boolean {
    val context = LocalContext.current
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale == 0f
}

private fun openExternal(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
