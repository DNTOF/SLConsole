// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 DNT_OF

package com.dntof.slconsole.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// "Tako × Sakura" 配色:深紫夜色底 + 樱粉主色 + 长春花紫辅色 + 薄荷点缀(动漫阅读器风格)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9EC6),
    onPrimary = Color(0xFF38101F),
    primaryContainer = Color(0xFF4E1F33),
    onPrimaryContainer = Color(0xFFFFD3E3),
    secondary = Color(0xFFA5AFFF),
    onSecondary = Color(0xFF171A4A),
    secondaryContainer = Color(0xFF2F3469),
    onSecondaryContainer = Color(0xFFDDE0FF),
    tertiary = Color(0xFF7FDFC1),
    onTertiary = Color(0xFF00382C),
    tertiaryContainer = Color(0xFF17453A),
    onTertiaryContainer = Color(0xFFA3F0DC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131019),
    onBackground = Color(0xFFEAE4F0),
    surface = Color(0xFF191521),
    onSurface = Color(0xFFEAE4F0),
    surfaceVariant = Color(0xFF251F31),
    onSurfaceVariant = Color(0xFFC9C1D8),
    surfaceContainerLowest = Color(0xFF0E0B13),
    surfaceContainerLow = Color(0xFF161220),
    surfaceContainer = Color(0xFF1C1725),
    surfaceContainerHigh = Color(0xFF231D2E),
    surfaceContainerHighest = Color(0xFF2B2438),
    outline = Color(0xFF655C77),
    outlineVariant = Color(0xFF2F2838),
    inverseSurface = Color(0xFFEFEAF4),
    inverseOnSurface = Color(0xFF221D2C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2447F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E8),
    onPrimaryContainer = Color(0xFF3E0D24),
    secondary = Color(0xFF4C55C4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E2FF),
    onSecondaryContainer = Color(0xFF00105C),
    tertiary = Color(0xFF22695B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB7EDE0),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF4F8),
    onBackground = Color(0xFF241B26),
    surface = Color(0xFFFEFAFC),
    onSurface = Color(0xFF241B26),
    surfaceVariant = Color(0xFFEDDFEA),
    onSurfaceVariant = Color(0xFF4B3F51),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF6F9),
    surfaceContainer = Color(0xFFF5EDF3),
    surfaceContainerHigh = Color(0xFFEFE7EE),
    surfaceContainerHighest = Color(0xFFE8DFE8),
    outline = Color(0xFF84778E),
    outlineVariant = Color(0xFFD9CDDC),
)

// small = 全胶囊(按钮/徽章),medium/large = 卡片与大容器
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(50),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SLConsoleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
