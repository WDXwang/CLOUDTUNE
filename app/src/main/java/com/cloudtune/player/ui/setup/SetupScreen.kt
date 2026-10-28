package com.cloudtune.player.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.core.model.BackendType

/**
 * 首次配置 / 服务器设置页。
 *
 * 选择后端（WebDAV 推荐 / 天翼直连实验）→ 填写表单 → 测试连接 → 保存。
 * 保存成功后回调 [onConfigured] 进入主界面。
 * 天翼直连支持 [onOpenTianyiWebLogin] 进入 App 内 Web 登录页。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onConfigured: () -> Unit,
    onOpenTianyiWebLogin: () -> Unit = {},
    onOpenTianyiQrLogin: () -> Unit = {},
    viewModel: SetupViewModel = viewModel(),
) {
    // 保存成功后跳转主界面
    LaunchedEffect(viewModel.saved) {
        if (viewModel.saved) onConfigured()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("连接你的云盘", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "选择一种方式连接可播放的音乐文件",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        // ===== 后端二选一 =====
        Text("后端类型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                BackendType.WEBDAV to "WebDAV（推荐）",
                BackendType.TIANYI_DIRECT to "天翼直连（实验）",
            )
            options.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = viewModel.backend == type,
                    onClick = { viewModel.selectBackend(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        // ===== 表单 =====
        when (viewModel.backend) {
            BackendType.WEBDAV -> WebDavForm(viewModel)
            BackendType.TIANYI_DIRECT -> TianyiForm(
                viewModel,
                onOpenTianyiWebLogin,
                onOpenTianyiQrLogin,
            )
        }

        // ===== 测试结果 =====
        viewModel.testMessage?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Text(
                msg,
                color = if (viewModel.testOk) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        viewModel.saveError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(24.dp))

        // ===== 操作按钮 =====
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { viewModel.testConnection() },
                enabled = !viewModel.testing && !viewModel.saving,
                modifier = Modifier.weight(1f),
            ) {
                if (viewModel.testing) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
                } else {
                    Text("测试连接")
                }
            }
            Button(
                onClick = { viewModel.save() },
                enabled = !viewModel.saving,
                modifier = Modifier.weight(1f),
            ) {
                Text("保存并进入")
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** WebDAV 表单：服务器地址、dav 路径、账号密码、起始目录 */
@Composable
private fun WebDavForm(vm: SetupViewModel) {
    OutlinedTextField(
        value = vm.baseUrl,
        onValueChange = { vm.baseUrl = it },
        label = { Text("服务器地址") },
        placeholder = { Text("http://192.168.1.10:5244") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.davPath,
        onValueChange = { vm.davPath = it },
        label = { Text("WebDAV 路径") },
        placeholder = { Text("/dav") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.username,
        onValueChange = { vm.username = it },
        label = { Text("用户名") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.password,
        onValueChange = { vm.password = it },
        label = { Text("密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.rootPath,
        onValueChange = { vm.rootPath = it },
        label = { Text("起始目录") },
        placeholder = { Text("/") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 天翼直连表单：扫码登录 + Web 登录 + 手动 Cookie 备选 + 根目录 ID */
@Composable
private fun TianyiForm(
    vm: SetupViewModel,
    onOpenWebLogin: () -> Unit,
    onOpenQrLogin: () -> Unit,
) {
    Button(
        onClick = onOpenQrLogin,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("扫码登录（推荐）")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "用手机天翼云盘 App 扫描二维码登录，无需输入账号密码，也不依赖网页渲染。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (vm.hasTokenSession) {
        Spacer(Modifier.height(8.dp))
        Text(
            "✓ 已通过扫码登录（token 会话，不绑定 IP，切换网络仍有效）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = onOpenWebLogin,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("手机 Web 端登录")
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "也可手动粘贴 Cookie（实验性备选，绑定登录 IP，切换网络需重新登录）。" +
            "注意：登录页（e.189.cn）的 Cookie 无效；Cookie 仅保存在本机。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.tianyiCookie,
        onValueChange = {
            vm.tianyiCookie = it
            // 手动填 Cookie 时清掉 token 会话，避免二义
            if (it.isNotBlank()) vm.clearTokenSession()
        },
        label = { Text("Cookie（备选）") },
        placeholder = { Text("粘贴完整 Cookie 字符串（或由扫码登录自动填入）") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.tianyiRootFolderId,
        onValueChange = { vm.tianyiRootFolderId = it },
        label = { Text("根目录文件夹 ID") },
        placeholder = { Text("-11") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
