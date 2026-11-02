package com.cloudtune.player.ui.playlists

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.PlaylistInfo
import com.cloudtune.player.core.model.StorageAccount
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

    // ---------- 歌单搜索 ----------

    var searchQuery by mutableStateOf("")

    // ---------- 按歌手自动生成歌单 ----------

    /** 是否正在扫描并生成歌单 */
    var generating by mutableStateOf(false)
        private set

    /** 生成结果提示（扫描完成后弹出，null 表示无） */
    var generateMessage by mutableStateOf<String?>(null)
        private set

    fun clearGenerateMessage() { generateMessage = null }

    // ---------- 文件夹选择 ----------

    /** 可选的存储（已启用） */
    var pickerAccounts by mutableStateOf<List<StorageAccount>>(emptyList())
        private set

    /** 选择器是否打开 */
    var pickerOpen by mutableStateOf(false)
        private set

    /** 当前正在浏览的账号 id（null 表示账号列表） */
    var browseAccountId by mutableStateOf<String?>(null)
        private set

    /** 当前浏览路径（栈：从账号根开始的逐级目录） */
    var browseStack by mutableStateOf<List<CloudFile>>(emptyList())
        private set

    /** 当前目录下的子文件夹列表 */
    var currentFolders by mutableStateOf<List<CloudFile>>(emptyList())
        private set

    /** 是否正在加载当前目录 */
    var pickerLoading by mutableStateOf(false)
        private set

    /** 已勾选的文件夹（key = accountId + "\n" + path） */
    val selectedFolders = mutableMapOf<String, FolderRef>()

    /** 打开文件夹选择器 */
    fun openPicker() {
        viewModelScope.launch {
            val accounts = repository.accounts.first().filter { it.enabled }
            if (accounts.isEmpty()) {
                generateMessage = "还没有启用的网盘，请先添加存储"
                return@launch
            }
            pickerAccounts = accounts
            browseAccountId = null
            browseStack = emptyList()
            currentFolders = emptyList()
            pickerOpen = true
        }
    }

    fun closePicker() {
        pickerOpen = false
        browseAccountId = null
        browseStack = emptyList()
        currentFolders = emptyList()
    }

    /** 选择一个账号，进入其根目录浏览 */
    fun openAccount(account: StorageAccount) {
        browseAccountId = account.id
        browseStack = emptyList()
        loadFolders(null)
    }

    /** 进入某个子文件夹 */
    fun enterFolder(folder: CloudFile) {
        browseStack = browseStack + folder
        loadFolders(folder)
    }

    /** 返回上一级 */
    fun backOneLevel() {
        if (browseStack.isEmpty()) {
            browseAccountId = null
            currentFolders = emptyList()
        } else {
            val newStack = browseStack.dropLast(1)
            browseStack = newStack
            loadFolders(newStack.lastOrNull())
        }
    }

    private fun loadFolders(dir: CloudFile?) {
        val accountId = browseAccountId ?: return
        pickerLoading = true
        viewModelScope.launch {
            val folders = runCatching {
                repository.list(accountId, dir).filter { it.isDir }.sortedBy { it.name }
            }.getOrDefault(emptyList())
            currentFolders = folders
            pickerLoading = false
        }
    }

    /** 勾选 / 取消勾选当前目录下的某个文件夹 */
    fun toggleFolder(folder: CloudFile) {
        val accountId = browseAccountId ?: return
        val key = folderKey(accountId, folder.path)
        if (selectedFolders.containsKey(key)) {
            selectedFolders.remove(key)
        } else {
            selectedFolders[key] = FolderRef(accountId, folder.id, folder.path, folder.name)
        }
        // 触发重组
        currentFolders = currentFolders.toList()
    }

    fun clearSelection() {
        selectedFolders.clear()
        currentFolders = currentFolders.toList()
    }

    private fun folderKey(accountId: String, path: String) = "$accountId\n$path"

    /** 确认选择并生成歌单。folderRefs 为空时扫描全部已启用网盘。 */
    fun generateByArtist(folderRefs: List<FolderRef>) {
        if (generating) return
        generating = true
        generateMessage = null
        viewModelScope.launch {
            try {
                val byAccount = LinkedHashMap<String, MutableList<CloudFile>>()
                if (folderRefs.isEmpty()) {
                    val accounts = repository.accounts.first().filter { it.enabled }
                    if (accounts.isEmpty()) {
                        generateMessage = "还没有启用的网盘，请先添加存储"
                        return@launch
                    }
                    for (a in accounts) {
                        byAccount[a.id] = mutableListOf()
                    }
                } else {
                    folderRefs.groupBy { it.accountId }.forEach { (acc, refs) ->
                        byAccount[acc] = refs.map { CloudFile(id = it.fileId, name = it.name, path = it.path, isDir = true) }.toMutableList()
                    }
                }

                // 逐个网盘递归收集音乐；单个失败不影响其它网盘
                val all = ArrayList<CloudFile>()
                for ((accountId, roots) in byAccount) {
                    runCatching {
                        all.addAll(repository.scanMusicIn(accountId, roots))
                    }
                }
                if (all.isEmpty()) {
                    generateMessage = "所选位置没有扫描到任何音乐文件"
                    return@launch
                }
                // 按歌手分组：优先用文件名解析；文件名不含歌手时用所在目录兜底
                val byArtist = LinkedHashMap<String, MutableList<CloudFile>>()
                var skipped = 0
                var byFolder = 0
                for (f in all) {
                    var artist = com.cloudtune.player.core.util.MusicUtils.parseArtist(f.name)
                    if (artist == null) {
                        artist = com.cloudtune.player.core.util.MusicUtils.folderArtist(f.name, f.path)
                        if (artist != null) byFolder++
                    }
                    if (artist == null) {
                        skipped++
                    } else {
                        byArtist.getOrPut(artist) { mutableListOf() }.add(f)
                    }
                }
                if (byArtist.isEmpty()) {
                    generateMessage =
                        "共扫描到 ${all.size} 首音乐，但文件名和所在文件夹都无法识别歌手"
                    return@launch
                }
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
                    append("共扫描 ${all.size} 首音乐，已生成 $playlistCount 个歌手歌单，共 $trackCount 首")
                    if (byFolder > 0) append("；其中 $byFolder 首按所在文件夹归类")
                    if (skipped > 0) append("；$skipped 首无法识别歌手，已跳过")
                }
            } catch (e: Exception) {
                generateMessage = "生成失败：${e.message ?: "未知错误"}"
            } finally {
                generating = false
            }
        }
    }
}

/** 一个被勾选的文件夹引用。 */
data class FolderRef(
    val accountId: String,
    val fileId: String,
    val path: String,
    val name: String,
)
