package com.cloudtune.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 应用全局主题。
 *
 * 默认跟随系统深色模式；统一通过 [MaterialTheme.colorScheme] /
 * [MaterialTheme.typography] 暴露给界面使用。
 */
@Composable
fun CloudTuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) CloudTuneDarkColors else CloudTuneLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CloudTuneTypography,
        content = content,
    )
}
