package com.cloudtune.player.ui.settings

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
 * 设置 ViewModel：展示已添加的存储列表，并提供退出登录（清空全部存储）能力。
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: MusicRepository =
        (app as CloudTuneApp).container.repository

    /** 已添加的全部存储（含停用） */
    val accounts: StateFlow<List<StorageAccount>> = repository.accounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 退出登录：清空本地保存的全部存储配置，随后回到引导设置页 */
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearAccounts()
            onDone()
        }
    }
}
