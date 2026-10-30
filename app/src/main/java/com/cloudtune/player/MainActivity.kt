package com.cloudtune.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.cloudtune.player.ui.navigation.AppNavigation
import com.cloudtune.player.ui.theme.NovaTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

/**
 * 应用主 Activity。
 *
 * - 安装启动屏
 * - Android 13+ 运行时请求通知权限（后台播放需要）
 * - 设置 Compose 内容：主题包裹导航主机
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NovaTheme {
                // Android 13+ 请求通知权限
                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { /* 授权结果无需处理，拒绝也不影响本地浏览 */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                AppNavigation()
            }
        }
    }
}
