package com.cloudtune.player.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.data.settings.PlaybackStore
import com.cloudtune.player.core.model.CloudFile
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/**
 * 前台（UI 层）一侧的播放控制器：通过 [MediaController] 连接后台 [PlaybackService] 里的
 * [androidx.media3.session.MediaSession]，把播放器状态暴露成可观察的 [StateFlow]。
 *
 * MediaController 只连接一次（进程级），UI 直接调用本类方法即可，无需关心服务生命周期。
 * 注意：MediaController 必须在带 Looper 的线程上创建（这里由 AppContainer 在主线程构造），
 * 所有 Listener 回调也都派发在该线程（主线程）。
 */
class PlayerControllerManager(
    context: Context,
    repository: MusicRepository,
    private val playbackStore: PlaybackStore,
    private val boost: VolumeBoostController,
    private val historyDao: com.cloudtune.player.data.db.HistoryDao,
) {
    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 上次保存的播放倍速；每次开始播放新队列时套用。 */
    @Volatile
    private var defaultSpeed = 1f

    /** 上次保存的软件音量（0.0~2.0）；连接后套用。 */
    @Volatile
    private var defaultVolume = 1f

    init {
        scope.launch {
            defaultSpeed = playbackStore.loadSpeed()
            defaultVolume = playbackStore.loadVolume()
        }
    }

    // 异步连接后台 MediaSession（只建一次）
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
        ).buildAsync()

    @Volatile
    private var controller: MediaController? = null

    private val _nowPlaying = MutableStateFlow(NowPlayingUi())
    /** 正在播放项的实时状态 */
    val nowPlaying: StateFlow<NowPlayingUi> = _nowPlaying.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItemUi>>(emptyList())
    /** 当前播放队列 */
    val queue: StateFlow<List<QueueItemUi>> = _queue.asStateFlow()

    /** 播放器监听器：任何相关状态变化都刷新对应的 StateFlow */
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refreshNowPlaying()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            recordHistory(mediaItem)
            refreshNowPlaying()
        }
        override fun onPlaybackStateChanged(playbackState: Int) = refreshNowPlaying()
        override fun onRepeatModeChanged(repeatMode: Int) = refreshNowPlaying()
        override fun onShuffleModeEnabledChanged(shuffleEnabled: Boolean) = refreshNowPlaying()
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = refreshNowPlaying()
        override fun onTimelineChanged(timeline: Timeline, reason: Int) = refreshQueue()
    }

    /**
     * 挂起直到 MediaController 连接完成（只在首次成功时挂一次监听器）。
     * 用 suspendCancellableCoroutine 给 ListenableFuture 加回调，避免引入 guava 协程扩展库。
     */
    private suspend fun awaitController(): MediaController {
        controller?.let { return it }
        return suspendCancellableCoroutine { cont ->
            controllerFuture.addListener({
                val c = controllerFuture.get()
                if (controller == null) {
                    controller = c
                    c.addListener(playerListener)
                    // 套用已保存的软件音量：0~1 用播放器 volume，>1 用 LoudnessEnhancer
                    applyVolumeTo(c, defaultVolume)
                    // 连上后先拉一次初始状态
                    refreshNowPlaying()
                    refreshQueue()
                }
                cont.resume(c)
            }, ContextCompat.getMainExecutor(appContext))
        }
    }

    /** 从当前 MediaItem 汇总正在播放界面状态 */
    private fun refreshNowPlaying() {
        val c = controller ?: run {
            _nowPlaying.value = NowPlayingUi()
            return
        }
        val meta = c.currentMediaItem?.mediaMetadata
        _nowPlaying.value = NowPlayingUi(
            mediaId = c.currentMediaItem?.mediaId ?: "",
            title = meta?.title?.toString() ?: "",
            artist = meta?.artist?.toString() ?: "",
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition,
            // duration 在未知时为 Long.MIN_VALUE，规范为 0
            durationMs = c.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L,
            repeatMode = c.repeatMode,
            shuffleEnabled = c.shuffleModeEnabled,
            playbackSpeed = c.playbackParameters.speed,
            volume = c.volume.coerceIn(0f, 2f),
        )
    }

    /** 遍历当前 Timeline，生成队列 UI 列表并标记当前项 */
    private fun refreshQueue() {
        val c = controller ?: return
        val currentIndex = c.currentMediaItemIndex
        val items = ArrayList<QueueItemUi>(c.mediaItemCount)
        for (i in 0 until c.mediaItemCount) {
            val mi = c.getMediaItemAt(i)
            val dur = mi.mediaMetadata.durationMs.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L
            items += QueueItemUi(
                mediaId = mi.mediaId,
                title = mi.mediaMetadata.title?.toString() ?: "",
                artist = mi.mediaMetadata.artist?.toString() ?: "",
                durationMs = dur,
                isCurrent = i == currentIndex,
                // 当前项且正在播放时才高亮“播放中”
                isPlaying = c.isPlaying && i == currentIndex,
            )
        }
        _queue.value = items
    }

    /** 播放位置流：收集后每 500ms 推送一次 currentPosition（暂停/停止期间也持续推送当前位置）。 */
    fun positionFlow(): Flow<Long> = flow {
        // 收集时先确保已连接后台服务，否则 controller 为 null
        val c = awaitController()
        while (true) {
            emit(c.currentPosition)
            delay(500)
        }
    }

    /** 主动建立与后台服务的连接（ViewModel 初始化时调用）。 */
    suspend fun ensureConnected() {
        awaitController()
    }

    /** 用一批云端文件替换队列并从 [startIndex] 开始播放 */
    /** 当前正在播放的 CloudFile（解码自 MediaItem URI），无则 null。 */
    fun currentFile(): CloudFile? = runCatching {
        controller?.currentMediaItem?.localConfiguration?.uri?.let { CloudMedia.decode(it) }
    }.getOrNull()

    suspend fun playFiles(files: List<CloudFile>, startIndex: Int = 0) {
        val c = awaitController()
        val items = files.map { CloudMedia.mediaItem(it) }
        c.setMediaItems(items, startIndex, 0)
        c.prepare()
        // 套用上次保存的倍速
        c.setPlaybackParameters(PlaybackParameters(defaultSpeed))
        c.play()
    }

    /** 播放 / 暂停切换 */
    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNext() }

    fun previous() { controller?.seekToPrevious() }

    /** 快退 15 秒（ExoPlayer 已配置 seekBackIncrementMs=15000）。 */
    fun seekBack() { controller?.seekBack() }

    /** 快进 15 秒。 */
    fun seekForward() { controller?.seekForward() }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    /** 循环模式：OFF -> ALL -> ONE -> OFF */
    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    /** 设置播放倍速（实时生效并持久化，下次播放自动套用） */
    fun setPlaybackSpeed(speed: Float) {
        val c = controller ?: return
        c.setPlaybackParameters(PlaybackParameters(speed))
        defaultSpeed = speed
        scope.launch { playbackStore.saveSpeed(speed) }
    }

    /**
     * 把整体音量（0..2）应用到播放器：
     * 0~1 用播放器自身 volume（安全范围），>1 时 volume=1 并用 LoudnessEnhancer 增益。
     */
    private fun applyVolumeTo(c: MediaController, volume: Float) {
        val v = volume.coerceIn(0f, 2f)
        if (v <= 1f) {
            c.volume = v
            boost.setGain(0)
        } else {
            c.volume = 1f
            boost.setGain(((v - 1f) * 1000f).toInt())
        }
    }

    /**
     * 设置软件音量（0.0~2.0，实时生效并持久化）。
     * 1.0 为正常音量；>1.0 为增益放大（在系统最大音量基础上再提升，过高可能破音）。
     */
    fun setVolume(volume: Float) {
        val c = controller ?: return
        val v = volume.coerceIn(0f, 2f)
        applyVolumeTo(c, v)
        defaultVolume = v
        _nowPlaying.value = _nowPlaying.value.copy(volume = v)
        scope.launch { playbackStore.saveVolume(v) }
    }

    fun removeQueueItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
    }

    /** 跳转到队列中指定项并播放（队列界面点击某一项）。 */
    fun playQueueItem(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0)
        c.play()
    }

    /** 切歌时把当前曲目写入历史记录（去重、刷新时间、累加次数）。 */
    private fun recordHistory(mediaItem: MediaItem?) {
        val uri = mediaItem?.localConfiguration?.uri ?: return
        scope.launch {
            runCatching {
                val file = CloudMedia.decode(uri) ?: return@runCatching
                val md = mediaItem.mediaMetadata
                val json = Json { ignoreUnknownKeys = true }
                val dur = md.durationMs?.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L
                historyDao.record(
                    id = file.id,
                    title = md.title?.toString()?.ifBlank { null }
                        ?: com.cloudtune.player.core.util.MusicUtils.baseName(file.name),
                    artist = md.artist?.toString()?.ifBlank { null },
                    album = md.albumTitle?.toString()?.ifBlank { null },
                    durationMs = dur,
                    fileJson = json.encodeToString(CloudFile.serializer(), file),
                    playedAt = System.currentTimeMillis(),
                )
            }
        }
    }
}
