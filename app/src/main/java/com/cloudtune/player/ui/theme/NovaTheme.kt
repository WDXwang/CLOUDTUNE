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
    bg = Color(0xFFEDF0F5),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF5F7FB),
    text = Color(0xFF14161B),
    sub = Color(0xFF6B7480),
    line = Color(0x17111827),          // rgba(17,24,39,.09)
    lineStrong = Color(0x2E111827),    // rgba(17,24,39,.18)
    accent = Color(0xFF2C6FF6),
    accentSoft = Color(0x1A2C6FF6),    // 10%
    accent2 = Color(0xFF6E5BE8),
    accent3 = Color(0xFF22B79A),
    onAccent = Color(0xFFFFFFFF),
    warn = Color(0xFFD98A1F)
)

val DarkNovaPalette = NovaPalette(
    isDark = true,
    bg = Color(0xFF0A0C11),
    surface = Color(0xFF15181F),
    surface2 = Color(0xFF1C2029),
    text = Color(0xFFF2F4F7),
    sub = Color(0xFF9AA3B2),
    line = Color(0x14FFFFFF),          // rgba(255,255,255,.08)
    lineStrong = Color(0x33FFFFFF),    // rgba(255,255,255,.20)
    accent = Color(0xFF5B93FF),
    accentSoft = Color(0x295B93FF),    // 16%
    accent2 = Color(0xFFA78BFA),
    accent3 = Color(0xFF4FD6B8),
    onAccent = Color(0xFF0A0C11),
    warn = Color(0xFFF0B457)
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
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
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
