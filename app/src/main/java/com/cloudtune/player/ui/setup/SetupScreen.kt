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
 * 添加网盘页（首次配置也从这里进入）。
 *
 * 选择后端（WebDAV / 天翼云盘 / 123 云盘 / 夸克网盘）→ 填写表单 → 测试连接 → 保存。
 * 保存成功后回调 [onConfigured] 进入主界面 / 返回网盘管理页。
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
        Text("添加网盘", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "可以添加多条存储（多个 WebDAV / 多个网盘账号），在文件页统一聚合浏览",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        // ===== 显示名称 =====
        OutlinedTextField(
            value = viewModel.accountName,
            onValueChange = { viewModel.accountName = it },
            label = { Text("显示名称（可留空）") },
            placeholder = { Text("例如：家里的 AList") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        // ===== 后端选择 =====
        Text("后端类型", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                BackendType.WEBDAV to "WebDAV",
                BackendType.TIANYI_DIRECT to "天翼",
                BackendType.PAN123 to "123",
                BackendType.QUARK to "夸克",
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
            BackendType.PAN123 -> Pan123Form(viewModel)
            BackendType.QUARK -> QuarkForm(viewModel)
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
                Text("保存")
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

/** 123 云盘表单：账号密码换取 token（token 仅保存在本机） */
@Composable
private fun Pan123Form(vm: SetupViewModel) {
    Text(
        "使用 123 云盘账号密码登录，换取 JWT 会话（实验性，接口可能随官方调整失效）。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.pan123Passport,
        onValueChange = { vm.pan123Passport = it },
        label = { Text("账号（手机号 / 邮箱）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.pan123Password,
        onValueChange = { vm.pan123Password = it },
        label = { Text("密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { vm.loginPan123() },
        enabled = !vm.pan123LoggingIn,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (vm.pan123LoggingIn) "登录中…" else "登录获取会话")
    }
    if (vm.pan123Token.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
            "✓ 已获取登录会话，可以测试连接",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    vm.pan123LoginMessage?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.pan123RootFolderId,
        onValueChange = { vm.pan123RootFolderId = it },
        label = { Text("起始目录 ID") },
        placeholder = { Text("0（根目录）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 夸克网盘表单：粘贴浏览器 Cookie */
@Composable
private fun QuarkForm(vm: SetupViewModel) {
    Text(
        "在电脑浏览器登录 pan.quark.cn 后，从开发者工具复制 Cookie（需包含 __puus 等字段），" +
            "粘贴到下方。Cookie 仅保存在本机，换号需重新获取。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.quarkCookie,
        onValueChange = { vm.quarkCookie = it },
        label = { Text("Cookie") },
        placeholder = { Text("粘贴完整 Cookie 字符串") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = vm.quarkRootFid,
        onValueChange = { vm.quarkRootFid = it },
        label = { Text("起始目录 fid") },
        placeholder = { Text("0（根目录）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
