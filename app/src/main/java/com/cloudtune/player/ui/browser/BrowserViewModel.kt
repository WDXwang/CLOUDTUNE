package com.cloudtune.player.ui.browser

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.player.PlayerControllerManager
import kotlinx.coroutines.launch

/**
 * 云盘文件浏览页 ViewModel。
 *
 * 维护一个目录栈（根目录用 null 表示），支持逐级进入 / 返回 / 刷新；
 * 支持按文件名搜索（本地过滤）、多选（长按进入）与全选；
 * 点击音乐文件时用“当前目录全部音乐”作为队列调用播放器播放。
 */
class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (getApplication<Application>() as CloudTuneApp).container
    private val repository: MusicRepository = container.repository
    private val player: PlayerControllerManager = container.playerManager

    /** 目录栈：栈底为根目录(null)，之后每进入一层压入一个文件夹 */
    private val dirStack = ArrayDeque<CloudFile?>().apply { addLast(null) }

    var uiState by mutableStateOf(BrowserUiState())
        private set

    init {
        refresh()
    }

    /** 当前目录（null = 根目录） */
    private val currentDir: CloudFile? get() = dirStack.last()

    /** 加载指定目录内容 */
    private suspend fun load(dir: CloudFile?) {
        uiState = uiState.copy(
            loading = true, error = null, currentDir = dir,
            query = "", selectionMode = false, selectedIds = emptySet(),
        )
        runCatching { repository.list(dir) }
            .onSuccess { files ->
                // 文件夹在前，音乐文件在后，各自按名称排序
                val sorted = files.sortedWith(
                    compareByDescending<CloudFile> { it.isDir }.thenBy { it.name }
                )
                uiState = uiState.copy(
                    loading = false,
                    files = sorted,
                    canGoBack = dirStack.size > 1,
                )
            }
            .onFailure { e ->
                uiState = uiState.copy(
                    loading = false,
                    error = e.message ?: "加载失败",
                )
            }
    }

    /** 刷新当前目录 */
    fun refresh() {
        viewModelScope.launch { load(currentDir) }
    }

    /** 进入文件夹 */
    fun enterFolder(dir: CloudFile) {
        if (!dir.isDir) return
        dirStack.addLast(dir)
        refresh()
    }

    /** 返回上一级；根目录时不可返回 */
    fun goBack() {
        if (dirStack.size <= 1) return
        dirStack.removeLast()
        refresh()
    }

    // ---------- 搜索 ----------

    fun setQuery(q: String) {
        uiState = uiState.copy(query = q)
    }

    // ---------- 多选 ----------

    /** 进入多选模式（长按触发），并选中指定项 */
    fun enterSelection(file: CloudFile) {
        uiState = uiState.copy(selectionMode = true, selectedIds = setOf(file.id))
    }

    /** 切换某一项的选中状态（仅音乐文件） */
    fun toggleSelect(file: CloudFile) {
        if (file.isDir) return
        val s = uiState.selectedIds.toMutableSet()
        if (!s.add(file.id)) s.remove(file.id)
        uiState = uiState.copy(selectedIds = s)
    }

    /** 全选 / 取消全选（作用于当前搜索结果中的音乐） */
    fun selectAll() {
        val music = visibleMusicFiles()
        uiState = if (uiState.selectedIds.size == music.size && music.isNotEmpty()) {
            uiState.copy(selectedIds = emptySet())
        } else {
            uiState.copy(selectedIds = music.map { it.id }.toSet())
        }
    }

    /** 退出多选模式 */
    fun clearSelection() {
        uiState = uiState.copy(selectionMode = false, selectedIds = emptySet())
    }

    // ---------- 播放 ----------

    /** 当前搜索结果中可见的音乐（保持顺序） */
    private fun visibleMusicFiles(): List<CloudFile> =
        uiState.visibleFiles.filter { it.isMusic() }

    /** 点击某首音乐：用当前可见全部音乐作为队列，从 index 开始播放 */
    fun playMusic(indexInMusic: Int) {
        val music = visibleMusicFiles()
        if (indexInMusic !in music.indices) return
        viewModelScope.launch { player.playFiles(music, indexInMusic) }
    }

    /** 播放当前可见全部音乐 */
    fun playAll() {
        val music = visibleMusicFiles()
        if (music.isEmpty()) return
        viewModelScope.launch { player.playFiles(music, 0) }
    }

    /** 播放选中的音乐（按当前可见顺序） */
    fun playSelected() {
        val selected = uiState.selectedIds
        val music = visibleMusicFiles().filter { it.id in selected }
        if (music.isEmpty()) return
        viewModelScope.launch {
            player.playFiles(music, 0)
            clearSelection()
        }
    }
}

/** 浏览页界面状态 */
data class BrowserUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val currentDir: CloudFile? = null,   // null = 根目录
    val files: List<CloudFile> = emptyList(),
    val canGoBack: Boolean = false,
    val query: String = "",
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    /** 顶部显示的路径名 */
    val pathLabel: String
        get() = currentDir?.name ?: "/"

    /** 按搜索词过滤后的可见条目（文件夹与音乐，按名称匹配） */
    val visibleFiles: List<CloudFile>
        get() = if (query.isBlank()) files
        else files.filter { it.name.contains(query.trim(), ignoreCase = true) }

    /** 可播放音乐数量（全部，未过滤） */
    val musicCount: Int get() = files.count { it.isMusic() }

    /** 可见音乐数量 */
    val visibleMusicCount: Int get() = visibleFiles.count { it.isMusic() }

    /** 全选框状态：true=全选，false=部分或未选 */
    val allSelected: Boolean
        get() = visibleMusicCount > 0 && selectedIds.size == visibleMusicCount
}
