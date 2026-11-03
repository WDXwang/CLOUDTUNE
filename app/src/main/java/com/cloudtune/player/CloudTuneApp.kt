package com.cloudtune.player

import android.app.Application
import android.content.Context
import com.cloudtune.player.core.provider.CloudProvider
import com.cloudtune.player.core.provider.LocalProvider
import com.cloudtune.player.core.provider.MusicProviderRegistry
import com.cloudtune.player.core.provider.SourceProvider
import com.cloudtune.player.data.DefaultCloudDataSourceFactory
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.data.db.AppDatabase
import com.cloudtune.player.data.repo.MusicRepositoryImpl
import com.cloudtune.player.data.settings.EqualizerStore
import com.cloudtune.player.data.settings.PlaybackStore
import com.cloudtune.player.data.settings.SettingsStore
import com.cloudtune.player.player.EqualizerController
import com.cloudtune.player.player.PlayerControllerManager
import com.cloudtune.player.player.VolumeBoostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 入口，同时是手动依赖注入的组合根（Composition Root）。
 *
 * 借鉴 MeloX 架构：MusicProviderRegistry 统一管理网盘/本地/第三方音源，
 * 各 Provider 通过 Mixin 接口按需声明 Search/Playback/HomeFeed 等能力。
 */
class CloudTuneApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        // 首次启动自动导入内置官方源（网易云热歌榜）
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.sourceRepository.ensureBuiltinSources(this@CloudTuneApp)
        }
    }

    companion object {
        lateinit var instance: CloudTuneApp
            private set
    }
}

/**
 * 应用级单例容器。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 配置持久化 */
    val settingsStore: SettingsStore = SettingsStore(appContext)

    /** 播放偏好持久化（倍速） */
    val playbackStore: PlaybackStore = PlaybackStore(appContext)

    /** 本地数据库（收藏） */
    private val database: AppDatabase = AppDatabase.getInstance(appContext)

    /** 历史记录 DAO（历史页直接使用） */
    val historyDao = database.historyDao()

    /** 音乐仓库（屏蔽 WebDAV / 天翼直连差异） */
    val repository: MusicRepository = MusicRepositoryImpl(
        context = appContext,
        settings = settingsStore,
        factory = DefaultCloudDataSourceFactory(),
        favoriteDao = database.favoriteDao(),
        playlistDao = database.playlistDao(),
    )

    /** 第三方音源仓库（洛雪自定义源脚本：导入 / 榜单 / 播放地址） */
    val sourceRepository: com.cloudtune.player.data.source.SourceRepository =
        com.cloudtune.player.data.source.SourceRepository(appContext)

    /**
     * MusicProvider 注册表 — 借鉴 MeloX 的 Provider 架构，
     * 把网盘、本地、第三方音源统一成 Mixin 能力接口。
     */
    val providerRegistry = MusicProviderRegistry().also { reg ->
        reg.register(CloudProvider(repository))
        reg.register(LocalProvider(appContext, repository))
        reg.register(SourceProvider(sourceRepository))
    }

    /** 音量增益控制器（绑定音频会话，实现超过 100% 音量） */
    val volumeBoost = VolumeBoostController()

    /** 播放器控制器管理（连接后台 MediaSession 服务） */
    val playerManager: PlayerControllerManager =
        PlayerControllerManager(appContext, repository, playbackStore, volumeBoost, database.historyDao())

    /** 均衡器控制器（绑定后台播放器音频会话，UI 层共享） */
    val equalizerController: EqualizerController = EqualizerController(EqualizerStore(appContext)).also {
        // 预加载持久化的均衡器设置
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { it.loadFromStore() }
    }

    /** 睡眠定时控制器 */
    val sleepTimer: com.cloudtune.player.player.SleepTimer =
        com.cloudtune.player.player.SleepTimer(playerManager)

    /** DLNA 投屏控制器（SSDP 发现 + SOAP 投放 + 本地 HTTP 分享） */
    val dlnaController: com.cloudtune.player.cast.DlnaController =
        com.cloudtune.player.cast.DlnaController(appContext, repository)
}
