package com.cloudtune.player.ui.sources

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.data.source.MusicSourceInfo
import com.cloudtune.player.data.source.SourceBoard
import com.cloudtune.player.data.source.SourceSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音源管理页 ViewModel：脚本导入 / 榜单加载 / 第三方直链播放。
 */
class SourcesViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CloudTuneApp).container
    private val repository = container.sourceRepository
    private val player = container.playerManager

    val sources: StateFlow<List<MusicSourceInfo>> = repository.sources

    /** 操作结果提示 */
    var message by mutableStateOf<String?>(null)
        private set

    var importing by mutableStateOf(false)
        private set

    /** 当前展开的音源（null 表示音源列表） */
    var selected by mutableStateOf<MusicSourceInfo?>(null)
        private set

    var boards by mutableStateOf<List<SourceBoard>>(emptyList())
        private set

    var songs by mutableStateOf<List<SourceSong>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    /** 正在解析播放地址的歌曲 id */
    var loadingSongId by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    /** 从系统文件选择器导入 .js 脚本。 */
    fun import(uri: Uri) {
        if (importing) return
        importing = true
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { it.readText(Charsets.UTF_8) }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                message = "读取脚本失败，请确认文件为 UTF-8 编码的 .js 音源脚本"
                importing = false
                return@launch
            }
            repository.import(text.removePrefix("\uFEFF"))
                .onSuccess { message = "已导入音源「${it.name}」" }
                .onFailure { message = "导入失败：${it.message ?: "未知错误"}" }
            importing = false
        }
    }

    fun delete(info: MusicSourceInfo) {
        viewModelScope.launch {
            if (selected?.id == info.id) {
                selected = null
                boards = emptyList()
                songs = emptyList()
            }
            runCatching { repository.delete(info.id) }
                .onSuccess { message = "已删除「${info.name}」" }
                .onFailure { message = "删除失败：${it.message}" }
        }
    }

    fun select(info: MusicSourceInfo) {
        selected = info
        boards = emptyList()
        songs = emptyList()
        message = null
        loadLeaderboard()
    }

    fun backToList() {
        selected = null
        boards = emptyList()
        songs = emptyList()
        message = null
    }

    /** 拉取榜单目录（脚本返回 boards 或直接返回歌曲）。 */
    fun loadLeaderboard() {
        val info = selected ?: return
        if (loading) return
        loading = true
        message = null
        viewModelScope.launch {
            repository.loadBoard(info, null)
                .onSuccess { payload ->
                    boards = payload.boards
                    songs = payload.songs
                    if (payload.boards.isEmpty() && payload.songs.isEmpty()) {
                        message = "该音源未返回榜单数据（脚本需实现 leaderboard action）"
                    }
                }
                .onFailure { message = "获取榜单失败：${it.message ?: "未知错误"}" }
            loading = false
        }
    }

    /** 选中具体榜单，拉取歌曲列表。 */
    fun loadSongs(board: SourceBoard) {
        val info = selected ?: return
        if (loading) return
        loading = true
        message = null
        viewModelScope.launch {
            repository.loadBoard(info, board.id)
                .onSuccess { payload ->
                    songs = payload.songs.ifEmpty { board.songs }
                    if (songs.isEmpty()) message = "该榜单没有返回歌曲"
                }
                .onFailure { message = "获取榜单歌曲失败：${it.message ?: "未知错误"}" }
            loading = false
        }
    }

    /** 解析直链并交给播放器播放。 */
    fun play(song: SourceSong) {
        val info = selected ?: return
        if (loadingSongId != null) return
        loadingSongId = song.id
        message = null
        viewModelScope.launch {
            repository.resolveUrl(info, song)
                .onSuccess { url ->
                    runCatching { player.playUrl(url, song.name, song.artist) }
                        .onFailure { message = "播放失败：${it.message ?: "未知错误"}" }
                }
                .onFailure { message = "获取播放地址失败：${it.message ?: "未知错误"}" }
            loadingSongId = null
        }
    }
}
