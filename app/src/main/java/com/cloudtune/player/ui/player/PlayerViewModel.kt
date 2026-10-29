package com.cloudtune.player.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.player.PlayerControllerManager
import com.cloudtune.player.player.NowPlayingUi
import com.cloudtune.player.player.QueueItemUi
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 全屏播放器 ViewModel。
 *
 * 直接转发 [PlayerControllerManager] 暴露的状态流与控制方法，
 * 界面（PlayerScreen）只负责渲染与交互，不直接接触播放器细节。
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    /** 手动 DI：从 Application 容器取播放器控制器 */
    private val playerManager =
        (app as CloudTuneApp).container.playerManager

    init {
        // 进入播放界面即建立与后台服务的连接，刷新状态
        viewModelScope.launch { playerManager.ensureConnected() }
    }

    /** 当前正在播放的曲目信息（标题 / 艺术家 / 播放状态 / 进度 / 模式） */
    val nowPlaying: StateFlow<NowPlayingUi> = playerManager.nowPlaying

    /** 当前播放队列 */
    val queue: StateFlow<List<QueueItemUi>> = playerManager.queue

    /** 播放位置流（毫秒），由后台播放器周期性推送 */
    fun positionFlow(): Flow<Long> = playerManager.positionFlow()

    fun togglePlay() = playerManager.togglePlay()

    fun next() = playerManager.next()

    fun previous() = playerManager.previous()

    /** 拖动进度条松手后跳转 */
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)

    /** 切换播放模式：顺序 → 列表循环 → 单曲循环 → 顺序 … */
    fun cycleRepeatMode() = playerManager.cycleRepeatMode()

    /** 开关随机播放 */
    fun toggleShuffle() = playerManager.toggleShuffle()

    /** 设置播放倍速 */
    fun setPlaybackSpeed(speed: Float) = playerManager.setPlaybackSpeed(speed)

    /** 设置软件音量（0.0~2.0，>1.0 为增益） */
    fun setVolume(volume: Float) = playerManager.setVolume(volume)
}
