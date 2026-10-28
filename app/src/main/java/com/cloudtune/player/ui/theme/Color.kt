package com.cloudtune.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ===== Apple Music 风格品牌色：红 / 玫粉 / 橙 渐变 =====
val MusicRed = Color(0xFFD92D47)          // 浅色模式主色（苹果红）
val MusicRedDark = Color(0xFFFF8A94)      // 深色模式主色（柔粉红）
val MusicContainer = Color(0xFFFFDAD9)    // 浅色主色容器
val MusicContainerDark = Color(0xFF6E1128) // 深色主色容器

// Apple 风渐变色（红→橙→紫粉）
val GradientRed = Color(0xFFFF5F6D)
val GradientOrange = Color(0xFFFF8A5C)
val GradientPurple = Color(0xFFFF4FA8)

// 背景 / 表面（iOS 风）
val BackgroundLight = Color(0xFFFFF9F8)   // 暖白
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF171113)    // 深黑棕
val SurfaceDark = Color(0xFF201A1C)
val SurfaceVariantDark = Color(0xFF322727)

// 次要色（柔玫红）
val SecondaryLight = Color(0xFF7A4A4F)
val SecondaryDark = Color(0xFFF2B8BE)

// 状态色
val SuccessGreen = Color(0xFF2E9E6B)
val ErrorRed = Color(0xFFD64545)

/** 浅色配色方案（Apple Music 浅色） */
val CloudTuneLightColors = lightColorScheme(
    primary = MusicRed,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = MusicContainer,
    onPrimaryContainer = Color(0xFF40000A),
    secondary = SecondaryLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDADA),
    onSecondaryContainer = Color(0xFF331012),
    background = BackgroundLight,
    onBackground = Color(0xFF20191A),
    surface = SurfaceLight,
    onSurface = Color(0xFF20191A),
    surfaceVariant = Color(0xFFF2E2E1),
    onSurfaceVariant = Color(0xFF514345),
    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
)

/** 深色配色方案（Apple Music 深色） */
val CloudTuneDarkColors = darkColorScheme(
    primary = MusicRedDark,
    onPrimary = Color(0xFF5F0000),
    primaryContainer = MusicContainerDark,
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = SecondaryDark,
    onSecondary = Color(0xFF4B2529),
    secondaryContainer = Color(0xFF633B3F),
    onSecondaryContainer = Color(0xFFFFDADA),
    background = BackgroundDark,
    onBackground = Color(0xFFEBE0E1),
    surface = SurfaceDark,
    onSurface = Color(0xFFEBE0E1),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFD3C1C3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
