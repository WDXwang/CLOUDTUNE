package com.cloudtune.player.ui.browser

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.player.PlayerControllerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 云盘文件浏览页 ViewModel（多网盘聚合）。
 *
 * 两种层级：
 * - **聚合首页**（[BrowserUiState.atHome] = true）：展示全部已启用的存储卡片；
 * - **存储内浏览**：进入某个存储后维护目录栈，支持逐级进入 / 返回 / 刷新 / 本地过滤；
 *
 * 聚合首页的搜索框执行「跨盘搜索」：并行在各启用存储内做有上限的递归搜索。
 * 点击音乐时用当前可见音乐作为队列播放，播放器按文件自带的 accountId 路由到对应网盘。
 */
class BrowserViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (getApplication<Application>() as CloudTuneApp).container
    private val repository: MusicRepository = container.repository
    private val player: PlayerControllerManager = container.playerManager

    /** 当前展示的存储列表（来自持久化账号） */
    private var allAccounts: List<StorageAccount> = emptyList()

    /** 存储内浏览的目录栈；null 表示该存储的根目录 */
    private val dirStack = ArrayDeque<CloudFile?>()

    /** 当前所在存储；null 表示聚合首页 */
    private var currentAccountId: String? = null

    private var searchJob: Job? = null

    var uiState by mutableStateOf(BrowserUiState())
        private set

    init {
        // 账号列表变化（新增 / 删除 / 启停）实时刷新首页
        viewModelScope.launch {
            repository.accounts.collectLatest { list ->
                allAccounts = list
                uiState = uiState.copy(accounts = list.filter { it.enabled })
                if (currentAccountId == null) {
                    // 首页：只看启用项，不做文件加载
                    uiState = uiState.copy(loading = false)
                } else if (list.none { it.id == currentAccountId }) {
                    // 当前存储被删除 → 回到首页
                    backToHome()
                }
            }
        }
        uiState = uiState.copy(loading = false)
    }

    /** 当前目录（null = 所进入存储的根目录） */
    private val currentDir: CloudFile? get() = dirStack.lastOrNull()

    private fun currentAccount(): StorageAccount? =
        allAccounts.firstOrNull { it.id == currentAccountId }

    // ---------- 存储间导航 ----------

    /** 从聚合首页进入某条存储 */
    fun openAccount(account: StorageAccount) {
        if (!account.enabled) return
        currentAccountId = account.id
        dirStack.clear()
        dirStack.addLast(null)
        uiState = uiState.copy(
            atHome = false,
            query = "",
            selectionMode = false,
            selectedIds = emptySet(),
            error = null,
        )
        refresh()
    }

    /** 返回聚合首页 */
    private fun backToHome() {
        currentAccountId = null
        dirStack.clear()
        uiState = uiState.copy(
            atHome = true,
            files = emptyList(),
            query = "",
            currentDir = null,
            accountId = null,
            accountName = null,
            canGoBack = false,
            selectionMode = false,
            selectedIds = emptySet(),
            error = null,
            loading = false,
        )
    }

    /** 刷新当前层级 */
    fun refresh() {
        val accountId = currentAccountId ?: return
        viewModelScope.launch { load(accountId, currentDir) }
    }

    private suspend fun load(accountId: String, dir: CloudFile?) {
        val account = allAccounts.firstOrNull { it.id == accountId }
        uiState = uiState.copy(
            loading = true, error = null, atHome = false,
            accountId = accountId, accountName = account?.name,
            currentDir = dir,
            query = "", selectionMode = false, selectedIds = emptySet(),
        )
        runCatching { repository.list(accountId, dir) }
            .onSuccess { files ->
                // 文件夹在前，文件在后，各自按名称排序
                val sorted = files.sortedWith(
                    compareByDescending<CloudFile> { it.isDir }.thenBy { it.name }
                )
                uiState = uiState.copy(
                    loading = false,
                    files = sorted,
                    canGoBack = true,
                )
            }
            .onFailure { e ->
                uiState = uiState.copy(
                    loading = false,
                    error = e.message ?: "加载失败",
                )
            }
    }

    /** 进入文件夹 */
    fun enterFolder(dir: CloudFile) {
        if (!dir.isDir) return
        dirStack.addLast(dir)
        refresh()
    }

    /** 返回上一级；存储根目录时返回聚合首页 */
    fun goBack() {
        if (currentAccountId == null) return
        if (dirStack.size <= 1) {
            backToHome()
            return
        }
        dirStack.removeLast()
        refresh()
    }

    // ---------- 搜索 ----------

    /**
     * 首页：跨网盘搜索（防抖 400ms，并行查询各启用存储，深度 / 条数有上限）。
     * 存储内：维持本地过滤语义，不触发网络请求。
     */
    fun setQuery(q: String) {
        uiState = uiState.copy(query = q, error = null)
        if (uiState.atHome) startCrossSearch(q)
    }

    private fun startCrossSearch(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.isBlank()) {
            uiState = uiState.copy(files = emptyList(), loading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            uiState = uiState.copy(loading = true)
            val targets = allAccounts.filter { it.enabled }
            // 并行搜索各存储；单个存储失败不影响其它存储
            val results = kotlinx.coroutines.coroutineScope {
                targets.map { account ->
                    async {
                        runCatching { repository.search(account.id, q) }.getOrDefault(emptyList())
                    }
                }.awaitAll().flatten().toMutableList()
            }
            uiState = uiState.copy(
                loading = false,
                files = results.sortedWith(
                    compareByDescending<CloudFile> { it.isDir }.thenBy { it.accountName }.thenBy { it.name }
                ),
            )
        }
    }

    // ---------- 多选 ----------

    /** 进入多选模式（长按触发），并选中指定项 */
    fun enterSelection(file: CloudFile) {
        uiState = uiState.copy(selectionMode = true, selectedIds = setOf(file.id))
    }

    /** 切换某一项的选中状态（仅文件） */
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
    /** 是否处于多网盘聚合首页 */
    val atHome: Boolean = true,
    /** 首页展示的已启用存储 */
    val accounts: List<StorageAccount> = emptyList(),
    /** 当前所在存储 */
    val accountId: String? = null,
    val accountName: String? = null,
    /** 当前目录，null 表示存储根目录 */
    val currentDir: CloudFile? = null,
    val files: List<CloudFile> = emptyList(),
    val canGoBack: Boolean = false,
    val query: String = "",
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    /** 顶部显示的路径名 */
    val pathLabel: String
        get() = when {
            atHome -> "我的网盘"
            currentDir != null -> currentDir.name
            else -> accountName ?: "/"
        }

    /** 按搜索词过滤后的可见条目（首页搜索结果为网络返回，不再二次过滤） */
    val visibleFiles: List<CloudFile>
        get() = when {
            atHome -> files
            query.isBlank() -> files
            else -> files.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }

    /** 可播放音乐数量（全部，未过滤） */
    val musicCount: Int get() = files.count { it.isMusic() }

    /** 可见音乐数量 */
    val visibleMusicCount: Int get() = visibleFiles.count { it.isMusic() }

    /** 全选框状态：true=全选，false=部分或未选 */
    val allSelected: Boolean
        get() = visibleMusicCount > 0 && selectedIds.size == visibleMusicCount

    /** 首页且未输入搜索词时，展示存储卡片列表而非文件列表 */
    val showAccountCards: Boolean
        get() = atHome && query.isBlank()
}
