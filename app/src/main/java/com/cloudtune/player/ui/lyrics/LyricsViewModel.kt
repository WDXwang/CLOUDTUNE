package com.cloudtune.player.ui.lyrics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.data.lyrics.LyricLine
import com.cloudtune.player.data.lyrics.LyricsParser
import com.cloudtune.player.data.lyrics.LyricsRepository
import com.cloudtune.player.player.NowPlayingUi
import com.cloudtune.player.player.PlayerControllerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 歌词获取状态 */
sealed interface LyricsStatus {
    object Idle : LyricsStatus
    object Loading : LyricsStatus
    object Loaded : LyricsStatus
    object NoLyrics : LyricsStatus
    object Error : LyricsStatus
}

/**
 * 歌词页 ViewModel：跟踪当前歌曲，自动从网络拉取并解析 LRC。
 */
class LyricsViewModel(app: Application) : AndroidViewModel(app) {

    private val player: PlayerControllerManager =
        (app as CloudTuneApp).container.playerManager
    private val repository = LyricsRepository()

    /** 当前播放项 */
    val nowPlaying: StateFlow<NowPlayingUi> = player.nowPlaying

    private val _status = MutableStateFlow<LyricsStatus>(LyricsStatus.Idle)
    val status: StateFlow<LyricsStatus> = _status.asStateFlow()

    private val _lines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lines: StateFlow<List<LyricLine>> = _lines.asStateFlow()

    private var lastKey = ""
    private var fetchJob: Job? = null

    init {
        viewModelScope.launch { observeAndFetch() }
    }

    private suspend fun observeAndFetch() {
        player.nowPlaying.collect { np ->
            val key = np.mediaId
            if (key.isEmpty() || key == lastKey) return@collect
            lastKey = key
            fetchJob?.cancel()
            fetchJob = viewModelScope.launch {
                _status.value = LyricsStatus.Loading
                _lines.value = emptyList()
                // 等一小段，确保拿到切歌后的稳定标题
                delay(400)
                val lrc = try {
                    repository.fetchLyrics(np.title, np.artist)
                } catch (t: Throwable) {
                    null
                }
                if (lrc == null) {
                    _status.value = LyricsStatus.NoLyrics
                } else {
                    val parsed = LyricsParser.parse(lrc)
                    if (parsed.isEmpty()) _status.value = LyricsStatus.NoLyrics
                    else {
                        _lines.value = parsed
                        _status.value = LyricsStatus.Loaded
                    }
                }
            }
        }
    }

    fun positionFlow(): Flow<Long> = player.positionFlow()
}
