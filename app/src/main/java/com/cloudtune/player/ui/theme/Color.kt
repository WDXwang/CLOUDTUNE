package com.cloudtune.player.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ===== Samsung One UI 9 风格：柔和蓝紫强调 + 极简近白/近黑背景 =====
val MusicBlue = Color(0xFF4A6CF7)          // 浅色主色（One UI 蓝紫）
val MusicBlueDark = Color(0xFFA7B8FF)      // 深色主色（柔和亮蓝）
val MusicContainer = Color(0xFFDCE3FF)     // 浅色主色容器（淡蓝）
val MusicContainerDark = Color(0xFF39457F) // 深色主色容器（深蓝）

// One UI 风格渐变色（蓝 → 紫 → 淡紫）
val GradientRed = Color(0xFF8B7CF7)
val GradientOrange = Color(0xFF4A9CF7)
val GradientPurple = Color(0xFFB39DFF)

// 背景 / 表面（三星浅色 / 深色风）
val BackgroundLight = Color(0xFFF6F7F9)   // 柔和浅灰白
val SurfaceLight = Color(0xFFFFFFFF)
val BackgroundDark = Color(0xFF000000)    // 纯黑（One UI 深色）
val SurfaceDark = Color(0xFF141519)
val SurfaceVariantDark = Color(0xFF1E2026)

// 次要色（柔和灰紫）
val SecondaryLight = Color(0xFF5B5F7A)
val SecondaryDark = Color(0xFFBFC7E0)

// 状态色
val SuccessGreen = Color(0xFF2E9E6B)
val ErrorRed = Color(0xFFD64545)

/** 浅色配色方案（One UI 9 浅色） */
val CloudTuneLightColors = lightColorScheme(
    primary = MusicBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = MusicContainer,
    onPrimaryContainer = Color(0xFF001152),
    secondary = SecondaryLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E0FF),
    onSecondaryContainer = Color(0xFF1C1B3A),
    background = BackgroundLight,
    onBackground = Color(0xFF191B20),
    surface = SurfaceLight,
    onSurface = Color(0xFF191B20),
    surfaceVariant = Color(0xFFE2E6EC),
    onSurfaceVariant = Color(0xFF44474E),
    error = ErrorRed,
    onError = Color(0xFFFFFFFF),
)

/** 深色配色方案（One UI 9 深色） */
val CloudTuneDarkColors = darkColorScheme(
    primary = MusicBlueDark,
    onPrimary = Color(0xFF102065),
    primaryContainer = MusicContainerDark,
    onPrimaryContainer = Color(0xFFDCE3FF),
    secondary = SecondaryDark,
    onSecondary = Color(0xFF363A4F),
    secondaryContainer = Color(0xFF3D4158),
    onSecondaryContainer = Color(0xFFDEE0FF),
    background = BackgroundDark,
    onBackground = Color(0xFFE3E2E6),
    surface = SurfaceDark,
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFC6C6CF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)
