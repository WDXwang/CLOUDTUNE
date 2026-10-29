package com.cloudtune.player.player

import androidx.media3.common.Player

/**
 * 正在播放项的界面状态（UI 层观察用）。
 */
data class NowPlayingUi(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    /** 当前播放倍速（1.0 为正常） */
    val playbackSpeed: Float = 1f,
    /** 软件音量（0.0~2.0，1.0 为正常，>1.0 为增益） */
    val volume: Float = 1f,
)

/**
 * 播放队列中的一行（UI 层观察用）。
 */
data class QueueItemUi(
    val mediaId: String,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0L,
    val isCurrent: Boolean,
    val isPlaying: Boolean,
)
