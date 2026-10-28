package com.cloudtune.player.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.data.MusicRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置 ViewModel：展示当前服务器配置，并提供退出登录（清空配置）能力。
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: MusicRepository =
        (app as CloudTuneApp).container.repository

    /** 当前已保存的服务器配置（未配置时为 null） */
    val config: StateFlow<ServerConfig?> = repository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 退出登录：清空本地配置，随后回到引导设置页 */
    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.clearConfig()
            onDone()
        }
    }
}
