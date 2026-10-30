package com.cloudtune.player.ui.storages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.data.MusicRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 网盘管理 ViewModel：列出全部存储，支持启停 / 重命名 / 删除。
 */
class StoragesViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: MusicRepository =
        (app as CloudTuneApp).container.repository

    /** 全部存储（含已停用的） */
    val accounts: StateFlow<List<StorageAccount>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 启用 / 停用某条存储 */
    fun setEnabled(account: StorageAccount, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { repository.updateAccount(account.copy(enabled = enabled)) }
        }
    }

    /** 重命名 */
    fun rename(account: StorageAccount, name: String) {
        val newName = name.trim()
        if (newName.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.updateAccount(account.copy(name = newName)) }
        }
    }

    /** 删除某条存储 */
    fun delete(account: StorageAccount) {
        viewModelScope.launch {
            runCatching { repository.removeAccount(account.id) }
        }
    }
}
