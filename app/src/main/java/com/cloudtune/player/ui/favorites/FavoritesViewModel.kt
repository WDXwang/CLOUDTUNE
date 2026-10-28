package com.cloudtune.player.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.MusicTrack
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.player.PlayerControllerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「我的收藏」ViewModel：
 * - 从仓库订阅本地收藏列表；
 * - 负责把收藏列表交给播放器播放；
 * - 负责取消收藏。
 */
class FavoritesViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CloudTuneApp).container
    private val repository: MusicRepository = container.repository
    private val playerManager: PlayerControllerManager = container.playerManager

    /** 收藏列表（按添加时间倒序），本地数据库驱动自动更新 */
    val favorites: StateFlow<List<MusicTrack>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 播放收藏列表中的第 [index] 首 */
    fun playAt(tracks: List<MusicTrack>, index: Int) {
        if (index !in tracks.indices) return
        viewModelScope.launch {
            // 把收藏的 CloudFile 列表交给播放器从指定位置开始播放
            playerManager.playFiles(tracks.map { it.file }, index)
        }
    }

    /** 取消收藏某首曲目 */
    fun toggleFavorite(track: MusicTrack) {
        viewModelScope.launch { repository.toggleFavorite(track) }
    }
}
