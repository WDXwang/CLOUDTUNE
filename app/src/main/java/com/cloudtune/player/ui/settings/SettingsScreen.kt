package com.cloudtune.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.core.model.displayName

/**
 * 设置界面：网盘管理入口、均衡器、历史播放、退出登录、风险免责说明、关于。
 *
 * @param onBack          返回上一页
 * @param onOpenStorages  打开网盘管理页
 * @param onOpenEqualizer 打开均衡器
 * @param onLoggedOut     退出登录成功后回到引导设置页
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenStorages: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    vm: SettingsViewModel = viewModel(),
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    // 退出登录二次确认弹窗
    var showLogoutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 顶部栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(16.dp))

        // 网盘管理入口
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            onClick = onOpenStorages,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("网盘管理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        storageSummary(accounts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 音效：均衡器入口
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            onClick = onOpenEqualizer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("均衡器", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "调节各频段增益与音效预置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 历史播放记录入口
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            onClick = onOpenHistory,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("历史播放", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "查看最近播放过的歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 账号操作：退出登录
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "退出登录将清除本地保存的全部网盘配置与 Cookie，下次打开需重新添加。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("退出登录")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 风险与免责说明（云盘非官方接入）
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "风险提示与免责声明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "· 天翼云盘、123 云盘、夸克网盘等直连模式使用非官方 Web API，" +
                        "通过 Cookie / 账号密码换取会话实现访问；" +
                        "该方式可能随官方接口调整而失效，账号存在被风控或封禁的风险。\n" +
                        "· 本应用仅用于个人学习与技术研究，请遵守云服务的用户协议；" +
                        "由此产生的任何账号或数据问题，开发者概不负责。\n" +
                        "· 推荐优先使用自建 AList / WebDAV 后端，数据与风险隔离在自己的服务端。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // 关于
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("CloudTune · 云盘音乐", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "版本 1.6.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "一款可以直接播放天翼云盘 / WebDAV / 123 云盘 / 夸克网盘音乐的本地播放器，" +
                        "支持多网盘聚合浏览、全屏播放、播放队列、本地收藏与多后端接入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // 退出登录确认弹窗
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定清除全部网盘配置并退出吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    vm.logout(onDone = onLoggedOut)
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            },
        )
    }
}

/** 一行摘要：已添加几个网盘、其中几个已启用，附上名称概览 */
private fun storageSummary(accounts: List<StorageAccount>): String {
    if (accounts.isEmpty()) return "尚未添加网盘"
    val enabled = accounts.count { it.enabled }
    val names = accounts.take(3).joinToString("、") { it.name.ifBlank { it.config.backend.displayName } }
    val more = if (accounts.size > 3) " 等 ${accounts.size} 个" else ""
    return "已添加 $enabled/${accounts.size} 个可用 · $names$more"
}
