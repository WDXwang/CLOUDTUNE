package com.cloudtune.player.player

import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cloudtune.player.CloudTuneApp

/**
 * 后台播放服务：MediaSessionService 会自动提供
 *  - 后台播放能力（带常驻前台服务）
 *  - 系统通知栏 / 锁屏媒体控件（MediaSession + 媒体按钮）
 *  - 蓝牙 / 耳机按键等媒体事件路由
 *
 * 本类只需负责：构造 ExoPlayer + MediaSession，并在合适时机释放。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // 从组合根取仓库（不改动 CloudTuneApp，只读其暴露的 container）
        val container = (application as CloudTuneApp).container
        val repository = container.repository

        // 组装数据源：cloudtune://track 占位 -> 真实直链 -> 边播边缓存
        val dataSourceFactory = CloudStreamResolverFactory.buildDataSourceFactory(this, repository)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        // 音频焦点 / 音乐用法
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        // 分配稳定的音频会话 id，供均衡器绑定（UI 与后台共用同一 session）
        val audioManager = getSystemService(AudioManager::class.java)
        val audioSessionId = audioManager.generateAudioSessionId()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        // 把播放器音频输出固定到上面分配的会话（Media3 在 ExoPlayer 上设置会话 id）
        exoPlayer.setAudioSessionId(audioSessionId)
        player = exoPlayer

        // 均衡器绑定到该会话
        container.equalizerController.attach(audioSessionId)

        // 绑定 MediaSession，系统媒体集成由 Media3 自动处理
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    /** 返回本服务持有的唯一 MediaSession（框架据此处理媒体按钮 / 通知）。 */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** 用户从最近任务划掉 App 时：没在播就直接停掉服务，避免空挂。 */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (player?.isPlaying != true) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        (application as CloudTuneApp).container.equalizerController.release()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
