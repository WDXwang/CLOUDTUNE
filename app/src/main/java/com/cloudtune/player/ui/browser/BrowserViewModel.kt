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
import com.cloudtune.player.data.source.BuiltinSources
import com.cloudtune.player.data.source.MusicSourceInfo
import com.cloudtune.player.data.source.SourceBoard
import com.cloudtune.player.data.source.SourceRepository
import com.cloudtune.player.data.source.SourceSong
import com.cloudtune.player.player.PlayerControllerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
    private val sourceRepository: SourceRepository = container.sourceRepository
    private val player: PlayerControllerManager = container.playerManager

    /** 当前展示的存储列表（来自持久化账号） */
    private var allAccounts: List<StorageAccount> = emptyList()

    /** 存储内浏览的目录栈；null 表示该存储的根目录 */
    private val dirStack = ArrayDeque<CloudFile?>()

    /** 当前所在存储；null 表示聚合首页 */
    private var currentAccountId: String? = null

    private var searchJob: Job? = null
    private var sourceLoadJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 汽水伪榜单：关键词即榜单名 */
    private val QISHUI_KEYWORDS = listOf("热歌", "新歌", "飙升")

    /** 判定汽水源的 key 片段（聚合脚本里汽水 key 常见取值，含 qsvip） */
    private val QISHUI_HINTS = listOf("汽水", "qishui", "qsvip", "qsd", "soda")

    /** 伪榜单 id 前缀 */
    private val PSEUDO_PREFIX = "pseudo:"

    /** 歌曲原始 JSON 中可能表示热度的字段 */
    private val HEAT_KEYS = listOf(
        "heat", "hot", "hotValue", "score", "popularity",
        "playCount", "play_count", "listenCount", "listen_count", "likeCount",
    )

    /** 每个榜单展示的歌曲数 */
    private val BOARD_SONG_LIMIT = 10

    /** 分类行里的「全部」选项 */
    private val CATEGORY_ALL = "全部"

    var uiState by mutableStateOf(BrowserUiState())
        private set

    /** 第三方音源排行榜与推荐状态 */
    var sourceUiState by mutableStateOf(SourceUiState())
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
        // 第三方音源列表变化（导入 / 删除 / 内置源异步导入完成）时自动刷新榜单与推荐
        viewModelScope.launch {
            sourceRepository.sources.collectLatest { loadSourceData() }
        }
    }

    /** 加载第三方音源的排行榜与推荐歌曲 */
    fun loadSourceData() {
        sourceLoadJob?.cancel()
        sourceLoadJob = viewModelScope.launch {
            sourceUiState = sourceUiState.copy(loading = true, error = null)
            runCatching {
                val sources = sourceRepository.sources.first()
                if (sources.isEmpty()) {
                    sourceUiState = sourceUiState.copy(loading = false, error = "未导入第三方音源")
                    return@runCatching
                }
                // 所有声明了 leaderboard 的音源都参与榜单（合并成一条榜单条带）
                val boardSources = sources.filter { it.supports("leaderboard") }
                val multiSource = boardSources.size > 1
                val boards = kotlinx.coroutines.coroutineScope {
                    boardSources.map { src ->
                        async {
                            sourceRepository.loadBoard(src, null).getOrNull()?.boards.orEmpty()
                                .map { board ->
                                    board.copy(
                                        id = encodeBoardId(src.id, board.id),
                                        // 多音源时在榜单名前加来源，避免重名混淆
                                        name = if (multiSource) "${sourceLabel(src.name)}·${board.name}" else board.name,
                                    )
                                }
                        }
                    }.awaitAll().flatten()
                }.toMutableList()

                // 汽水音乐没有公开榜单接口，用其搜索能力拼成伪榜单
                qishuiTarget(sources)?.let { (src, key) ->
                    boards += QISHUI_KEYWORDS.map { keyword ->
                        SourceBoard(
                            id = encodePseudoBoardId(src.id, keyword),
                            name = "汽水·${keyword}榜",
                        )
                    }
                }
                // 每个榜单归属一个分类（取名字里 "·" 前缀），用于分类过滤行
                val categoryMap = boards.associate { board ->
                    board.id to board.name.substringBefore("·", missingDelimiterValue = "其他")
                }.toMutableMap()
                // 单音源无前缀时归入其来源名
                if (boardSources.size == 1) {
                    val label = sourceLabel(boardSources.first().name)
                    boards.forEach { board -> if (categoryMap[board.id] == "其他") categoryMap[board.id] = label }
                }
                val categories = listOf(CATEGORY_ALL) +
                    boards.map { categoryMap[it.id].orEmpty() }.distinct().filter { it.isNotBlank() }
                val firstBoard = boards.firstOrNull()
                val boardSongs = firstBoard?.let { loadBoardSongs(it) }.orEmpty()
                // 推荐：找支持搜索的音源
                val searchSource = sources.firstOrNull { it.supports("musicSearch") || it.supports("search") }
                val recommendedSongs = searchSource?.let { src ->
                    val keywords = listOf("热歌", "新歌", "流行")
                    keywords.map { kw ->
                        sourceRepository.search(src, kw).getOrNull()?.songs.orEmpty().take(3)
                    }.flatten().distinctBy { it.id }.take(10)
                } ?: emptyList()

                sourceUiState = sourceUiState.copy(
                    loading = false,
                    sources = sources,
                    boards = boards,
                    boardCategories = categoryMap,
                    categories = categories,
                    currentBoard = firstBoard,
                    boardSongs = boardSongs,
                    boardError = if (boards.isNotEmpty() && boardSongs.isEmpty()) {
                        "该榜单暂无歌曲：音源接口可能不可用、超时或未返回数据"
                    } else {
                        null
                    },
                    recommendedSongs = recommendedSongs,
                    error = if (boards.isEmpty() && recommendedSongs.isEmpty()) "音源未返回排行榜与推荐数据" else null,
                )
            }.onFailure { e ->
                sourceUiState = sourceUiState.copy(loading = false, error = e.message ?: "加载音源数据失败")
            }
        }
    }

    /** 播放第三方音源的歌曲（按歌曲的 sourceKey 路由回对应音源） */
    fun playSourceSong(song: SourceSong) {
        val source = sourceForSong(song) ?: return
        viewModelScope.launch {
            val url = runCatching { sourceRepository.resolveUrl(source, song) }.getOrNull()?.getOrNull()
            if (url != null) {
                player.playUrl(url, song.name, song.artist)
            }
        }
    }

    /** 切换当前展示的榜单 */
    fun selectBoard(board: SourceBoard) {
        viewModelScope.launch {
            sourceUiState = sourceUiState.copy(loadingBoard = true, boardError = null)
            val songs = loadBoardSongs(board)
            sourceUiState = sourceUiState.copy(
                currentBoard = board,
                boardSongs = songs,
                loadingBoard = false,
                boardError = if (songs.isEmpty()) "该榜单暂无歌曲：音源接口可能不可用、超时或未返回数据" else null,
            )
        }
    }

    /** 切换榜单分类（null 或「全部」表示不过滤） */
    fun selectCategory(category: String?) {
        val next = category?.takeIf { it != CATEGORY_ALL }
        sourceUiState = sourceUiState.copy(selectedCategory = next)
    }

    /** 榜单 id 里带了音源 id（`sourceId|boardId`），解析后回查对应音源再拉歌曲 */
    private suspend fun loadBoardSongs(board: SourceBoard): List<SourceSong> {
        val (sourceId, boardId) = decodeBoardId(board.id)
        val source = sourceUiState.sources.firstOrNull { it.id == sourceId } ?: return emptyList()
        // 汽水伪榜单：用搜索关键词出歌，按热度降序（接口不带热度时保留原有顺序）
        if (boardId.startsWith(PSEUDO_PREFIX)) {
            val keyword = boardId.removePrefix(PSEUDO_PREFIX)
            // 显式用探测到的汽水子源 key 搜索，并把 key 写回歌曲，播放时路由回该子源
            val key = qishuiTarget(sourceUiState.sources)?.second
                ?: source.sourceFor("musicSearch") ?: return emptyList()
            val songs = sourceRepository.search(source, keyword, key).getOrNull()?.songs.orEmpty()
            return songs.asSequence()
                .distinctBy { it.id }
                .map { if (it.sourceKey.isBlank()) it.copy(sourceKey = key) else it }
                .sortedByDescending { heatOf(it) }
                .take(BOARD_SONG_LIMIT)
                .toList()
        }
        return sourceRepository.loadBoard(source, boardId).getOrNull()?.songs.orEmpty().take(BOARD_SONG_LIMIT)
    }

    /**
     * 探测（音源, 子源 key）：具备搜索能力、可作为汽水伪榜单数据来源的聚合源。
     * 优先 key 命中汽水暗示（qsvip / qishui / 汽水…），否则取第一个有搜索能力的非内置源。
     */
    private fun qishuiTarget(sources: List<MusicSourceInfo>): Pair<MusicSourceInfo, String>? {
        val candidates = sources.flatMap { src ->
            val keys = src.sourceActions.entries
                .filter { (_, acts) -> acts.contains("musicSearch") || acts.contains("search") }
                .map { it.key }
                .ifEmpty { src.sources }
            keys.map { src to it }
        }.filter { (src, _) -> !BuiltinSources.isBuiltin(src.name) }
        return candidates.firstOrNull { (_, key) -> QISHUI_HINTS.any { key.lowercase().contains(it) } }
            ?: candidates.firstOrNull()
    }

    /** 从歌曲原始 JSON 中取热度字段，用于伪榜单排序 */
    private fun heatOf(song: SourceSong): Long {
        if (song.raw.isBlank()) return 0L
        val obj = runCatching { json.parseToJsonElement(song.raw).jsonObject }.getOrNull() ?: return 0L
        for (key in HEAT_KEYS) {
            val value = runCatching { obj[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
                ?.toDoubleOrNull() ?: continue
            if (value > 0) return value.toLong()
        }
        return 0L
    }

    private fun sourceForSong(song: SourceSong): MusicSourceInfo? {
        val builtinName = BuiltinSources.nameForSourceKey(song.sourceKey)
        return sourceUiState.sources.firstOrNull { builtinName != null && it.name == builtinName }
            ?: sourceUiState.sources.firstOrNull { song.sourceKey.isNotBlank() && it.sources.contains(song.sourceKey) }
            ?: sourceUiState.sources.firstOrNull()
    }

    private fun encodeBoardId(sourceId: String, boardId: String): String = "$sourceId|$boardId"

    /** 伪榜单 id：`sourceId|pseudo:热歌` */
    private fun encodePseudoBoardId(sourceId: String, keyword: String): String =
        "$sourceId|$PSEUDO_PREFIX$keyword"

    /** 榜单前缀：网易云热歌榜 → 网易云；酷狗音乐榜 → 酷狗 */
    private fun sourceLabel(name: String): String =
        name.replace("音乐", "").replace("热歌榜", "").replace("榜", "")

    private fun decodeBoardId(raw: String): Pair<String, String> {
        val idx = raw.indexOf('|')
        return if (idx <= 0) "" to raw
        else raw.substring(0, idx) to raw.substring(idx + 1)
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

    /** 切换「列表 / 电影墙」展示模式（电影墙下视频以海报卡片网格展示） */
    fun toggleWallMode() {
        uiState = uiState.copy(wallMode = !uiState.wallMode)
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
    /** true = 以「电影墙」网格展示（视频海报卡片）；false = 传统列表 */
    val wallMode: Boolean = false,
) {
    /** 顶部显示的路径名 */
    val pathLabel: String
        get() = when {
            atHome -> "CloudTune"
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

    /** 当前目录视频数量（全部，未过滤） */
    val videoCount: Int get() = files.count { it.isVideo() }

    /** 可见音乐数量 */
    val visibleMusicCount: Int get() = visibleFiles.count { it.isMusic() }

    /** 全选框状态：true=全选，false=部分或未选 */
    val allSelected: Boolean
        get() = visibleMusicCount > 0 && selectedIds.size == visibleMusicCount

    /** 首页且未输入搜索词时，展示存储卡片列表而非文件列表 */
    val showAccountCards: Boolean
        get() = atHome && query.isBlank()
}

/** 第三方音源排行榜与推荐状态 */
data class SourceUiState(
    val loading: Boolean = false,
    val loadingBoard: Boolean = false,
    val error: String? = null,
    /** 当前榜单拉取失败 / 为空时的提示 */
    val boardError: String? = null,
    val sources: List<MusicSourceInfo> = emptyList(),
    val boards: List<SourceBoard> = emptyList(),
    /** 榜单 id → 分类（来源名，如 网易云 / 酷狗 / 汽水） */
    val boardCategories: Map<String, String> = emptyMap(),
    /** 分类行选项（首项固定「全部」） */
    val categories: List<String> = emptyList(),
    /** 当前选中的分类；null = 全部 */
    val selectedCategory: String? = null,
    val currentBoard: SourceBoard? = null,
    val boardSongs: List<SourceSong> = emptyList(),
    val recommendedSongs: List<SourceSong> = emptyList(),
)
