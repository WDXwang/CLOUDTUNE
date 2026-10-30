package com.cloudtune.player.ui.storages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.core.model.BackendType
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.core.model.displayName
import com.cloudtune.player.ui.components.MessageView

/**
 * 网盘管理页：查看全部存储，支持新增、启停、重命名、删除。
 *
 * 停用的存储不再出现在文件页聚合首页，也不参与跨盘搜索。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoragesScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    vm: StoragesViewModel = viewModel(),
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()

    var renaming by remember { mutableStateOf<StorageAccount?>(null) }
    var deleting by remember { mutableStateOf<StorageAccount?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("网盘管理") },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, contentDescription = "添加网盘")
                    }
                },
            )
        },
    ) { padding ->
        if (accounts.isEmpty()) {
            MessageView(
                message = "还没有添加任何网盘，点击右上角 ＋ 添加",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(accounts, key = { it.id }) { account ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                account.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                accountSummary(account),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = account.enabled,
                            onCheckedChange = { vm.setEnabled(account, it) },
                        )
                        IconButton(onClick = { renaming = account }) {
                            Icon(Icons.Filled.Edit, contentDescription = "重命名")
                        }
                        IconButton(onClick = { deleting = account }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    onClick = onAdd,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("＋ 添加网盘", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // 重命名弹窗
    renaming?.let { account ->
        var input by remember(account.id) { mutableStateOf(account.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名网盘") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.rename(account, input)
                    renaming = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("取消") }
            },
        )
    }

    // 删除确认
    deleting?.let { account ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除网盘") },
            text = { Text("确定删除「${account.name}」吗？仅移除本机配置，不会影响云端文件。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(account)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            },
        )
    }
}

/** 一行摘要：后端类型 + 关键信息（不展示敏感凭据） */
private fun accountSummary(account: StorageAccount): String {
    val c = account.config
    return when (c.backend) {
        BackendType.WEBDAV -> "${c.backend.displayName} · ${c.baseUrl}"
        BackendType.TIANYI_DIRECT -> "${c.backend.displayName} · " +
            if (c.tianyiSessionKey.isNotBlank()) "已扫码登录" else "Cookie 登录"
        BackendType.PAN123 -> "${c.backend.displayName} · " +
            if (c.pan123Passport.isNotBlank()) c.pan123Passport else "已登录"
        BackendType.QUARK -> "${c.backend.displayName} · Cookie 已配置"
        BackendType.GUANGYA -> "${c.backend.displayName} · " +
            if (c.guangYaAccount.isNotBlank()) c.guangYaAccount else "已登录"
    }
}
