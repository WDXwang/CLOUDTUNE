package com.cloudtune.player.ui.playlists

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.PlaylistInfo
import com.cloudtune.player.data.MusicRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 歌单列表页 ViewModel。
 */
class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository: MusicRepository =
        (app as CloudTuneApp).container.repository

    /** 歌单列表（随数据库实时更新） */
    val playlists: StateFlow<List<PlaylistInfo>> =
        repository.observePlaylists().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    var error by mutableStateOf<String?>(null)
        private set

    fun create(name: String) {
        viewModelScope.launch {
            runCatching { repository.createPlaylist(name) }
                .onFailure { error = it.message ?: "创建失败" }
        }
    }

    // ---------- 按歌手自动生成歌单 ----------

    /** 是否正在扫描并生成歌单 */
    var generating by mutableStateOf(false)
        private set

    /** 生成结果提示（扫描完成后弹出，null 表示无） */
    var generateMessage by mutableStateOf<String?>(null)
        private set

    fun clearGenerateMessage() { generateMessage = null }

    /**
     * 扫描全部已启用网盘中的音乐，按「歌手 - 歌名」文件名约定分组，
     * 为每位歌手创建（或覆盖重建）一个同名歌单。
     */
    fun generateByArtist() {
        if (generating) return
        generating = true
        generateMessage = null
        viewModelScope.launch {
            try {
                val accounts = repository.accounts.first().filter { it.enabled }
                if (accounts.isEmpty()) {
                    generateMessage = "还没有启用的网盘，请先添加存储"
                    return@launch
                }
                // 逐个网盘递归收集音乐；单个失败不影响其它网盘
                val all = ArrayList<com.cloudtune.player.core.model.CloudFile>()
                for (a in accounts) {
                    runCatching { all.addAll(repository.scanMusic(a.id)) }
                }
                if (all.isEmpty()) {
                    generateMessage = "没有扫描到任何音乐文件"
                    return@launch
                }
                // 按歌手分组；文件名不符合「歌手 - 歌名」的跳过
                val byArtist = LinkedHashMap<String, MutableList<com.cloudtune.player.core.model.CloudFile>>()
                var skipped = 0
                for (f in all) {
                    val artist = com.cloudtune.player.core.util.MusicUtils.parseArtist(f.name)
                    if (artist == null) {
                        skipped++
                    } else {
                        byArtist.getOrPut(artist) { mutableListOf() }.add(f)
                    }
                }
                if (byArtist.isEmpty()) {
                    generateMessage =
                        "共扫描到 ${all.size} 首音乐，但文件名都不含「歌手 - 歌名」，无法识别歌手"
                    return@launch
                }
                // 同名歌单先删除再重建，保证重复执行时内容是最新的
                val existing = playlists.value.associate { it.name to it.id }
                var playlistCount = 0
                var trackCount = 0
                for ((artist, files) in byArtist.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
                    existing[artist]?.let { runCatching { repository.deletePlaylist(it) } }
                    val pid = repository.createPlaylist(artist)
                    for (f in files.sortedBy { it.name }) {
                        repository.addToPlaylist(
                            pid,
                            com.cloudtune.player.core.model.MusicTrack(
                                file = f,
                                title = com.cloudtune.player.core.util.MusicUtils.parseTitle(f.name),
                                artist = artist,
                            ),
                        )
                    }
                    playlistCount++
                    trackCount += files.size
                }
                generateMessage = buildString {
                    append("已生成 $playlistCount 个歌手歌单，共 $trackCount 首")
                    if (skipped > 0) append("；$skipped 首文件名不含歌手，已跳过")
                }
            } catch (e: Exception) {
                generateMessage = "生成失败：${e.message ?: "未知错误"}"
            } finally {
                generating = false
            }
        }
    }
}
