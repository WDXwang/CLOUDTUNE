package com.cloudtune.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * NOVA PLAYER · One UI 9 主题
 *
 * 已迁入 com.cloudtune.player.ui.theme。
 * 依赖：Compose BOM + material3；组件文件另需 material-icons-extended。
 * 颜色全部显式赋值，不启用动态取色（dynamicColor），保证与设计稿一致。
 */

@Immutable
data class NovaPalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val sub: Color,
    val line: Color,
    val lineStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val accent2: Color,
    val accent3: Color,
    val onAccent: Color,
    val warn: Color
)

val LightNovaPalette = NovaPalette(
    isDark = false,
    bg = Color(0xFFF6F7F8),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF0F2F4),
    text = Color(0xFF0E0E10),
    sub = Color(0xFF6E7379),
    line = Color(0x14141418),
    lineStrong = Color(0x2B141418),
    accent = Color(0xFF9BD400),
    accentSoft = Color(0x1F9BD400),
    accent2 = Color(0xFF1DBFA0),
    accent3 = Color(0xFF2C6FF6),
    onAccent = Color(0xFF0E0E10),
    warn = Color(0xFFD98A1F)
)

// 汽水音乐风格：纯黑底 + 柠檬绿强调色 + 大圆角深灰卡片
val DarkNovaPalette = NovaPalette(
    isDark = true,
    bg = Color(0xFF000000),
    surface = Color(0xFF141416),
    surface2 = Color(0xFF1F1F22),
    text = Color(0xFFFFFFFF),
    sub = Color(0xFF9A9AA0),
    line = Color(0x14FFFFFF),
    lineStrong = Color(0x2EFFFFFF),
    accent = Color(0xFFD3FF4E),
    accentSoft = Color(0x1FD3FF4E),
    accent2 = Color(0xFF6BE2C4),
    accent3 = Color(0xFFA78BFA),
    onAccent = Color(0xFF0E0E10),
    warn = Color(0xFFFFB84D)
)

private val LocalNovaPalette = staticCompositionLocalOf { LightNovaPalette }

object NovaTheme {
    val colors: NovaPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalNovaPalette.current
}

private fun NovaPalette.asColorScheme(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentSoft,
            onPrimaryContainer = accent,
            secondary = accent2,
            onSecondary = onAccent,
            secondaryContainer = accentSoft,
            onSecondaryContainer = accent2,
            tertiary = accent3,
            onTertiary = onAccent,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surface2,
            onSurfaceVariant = sub,
            outline = lineStrong,
            outlineVariant = line,
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentSoft,
            onPrimaryContainer = accent,
            secondary = accent2,
            onSecondary = onAccent,
            secondaryContainer = accentSoft,
            onSecondaryContainer = accent2,
            tertiary = accent3,
            onTertiary = onAccent,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surface2,
            onSurfaceVariant = sub,
            outline = lineStrong,
            outlineVariant = line,
            scrim = Color.Black
        )
    }

private val NovaSans = FontFamily.SansSerif
private val NovaMono = FontFamily.Monospace

object NovaType {
    /** 页面主标题 32sp ExtraBold */
    val PageTitle = TextStyle(
        fontFamily = NovaSans,
        fontSize = 32.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.96).sp
    )

    /** 播放页曲名 26sp ExtraBold，最多两行 */
    val TrackTitle = TextStyle(
        fontFamily = NovaSans,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.65).sp
    )

    /** 空态标题 22sp Bold */
    val EmptyTitle = TextStyle(
        fontFamily = NovaSans,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.44).sp
    )

    /** 列表行标题 14.5sp Medium */
    val RowTitle = TextStyle(
        fontFamily = NovaSans,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.15).sp
    )

    /** 等宽读数（时长、大小、编号） */
    val Mono = TextStyle(
        fontFamily = NovaMono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )

    /** 等宽大字读数（当前播放时间） */
    val MonoLarge = TextStyle(
        fontFamily = NovaMono,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )

    /** 页头小标 11sp Medium */
    val Kicker = TextStyle(
        fontFamily = NovaSans,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium
    )

    /** 说明文字 13sp */
    val Hint = TextStyle(
        fontFamily = NovaSans,
        fontSize = 13.sp,
        lineHeight = 21.sp
    )

    /** 标签栏文字 11sp Medium */
    val TabLabel = TextStyle(
        fontFamily = NovaSans,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium
    )
}

private val NovaTypography = Typography(
    displaySmall = NovaType.PageTitle,
    headlineMedium = NovaType.TrackTitle,
    titleLarge = NovaType.EmptyTitle,
    titleMedium = NovaType.RowTitle,
    bodySmall = NovaType.Hint,
    labelMedium = NovaType.Kicker,
    labelSmall = NovaType.TabLabel
)

private val NovaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)
)

/**
 * 全局主题入口。
 *
 * @param darkTheme 由持久化的用户选择决定，默认跟随系统。
 *   Web 原型对应 localStorage("nova-theme")，Android 对应 DataStore。
 */
@Composable
fun NovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkNovaPalette else LightNovaPalette
    CompositionLocalProvider(LocalNovaPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.asColorScheme(),
            shapes = NovaShapes,
            typography = NovaTypography,
            content = content
        )
    }
}
