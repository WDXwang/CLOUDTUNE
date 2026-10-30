package com.cloudtune.player.ui.local

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.player.PlayerControllerManager
import kotlinx.coroutines.launch

/**
 * 本地音乐页 ViewModel：扫描设备本地音乐，支持搜索、多选、全选。
 */
class LocalViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (getApplication<Application>() as CloudTuneApp).container
    private val repository = container.repository
    private val player: PlayerControllerManager = container.playerManager

    var uiState by mutableStateOf(LocalUiState())
        private set

    /** 所需的音频权限（按系统版本选择）。 */
    val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** 权限授予后扫描本地音乐与视频。 */
    fun load() {
        uiState = uiState.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching {
                // 音乐 + 本地视频合并展示；视频扫描失败（如未授权）返回空列表，不影响音乐
                val music = repository.listLocalMusic()
                val videos = com.cloudtune.player.data.local.LocalVideoScanner(getApplication()).scan()
                (music + videos).sortedWith(
                    compareByDescending<CloudFile> { it.isVideo() }.thenBy { it.name }
                )
            }.onSuccess { files ->
                uiState = uiState.copy(loading = false, files = files)
            }.onFailure {
                uiState = uiState.copy(loading = false, error = it.message ?: "扫描失败")
            }
        }
    }

    fun setQuery(q: String) { uiState = uiState.copy(query = q) }

    fun enterSelection(file: CloudFile) {
        uiState = uiState.copy(selectionMode = true, selectedIds = setOf(file.id))
    }

    fun toggleSelect(file: CloudFile) {
        val s = uiState.selectedIds.toMutableSet()
        if (!s.add(file.id)) s.remove(file.id)
        uiState = uiState.copy(selectedIds = s)
    }

    fun selectAll() {
        val music = visibleMusic()
        uiState = if (uiState.selectedIds.size == music.size && music.isNotEmpty()) {
            uiState.copy(selectedIds = emptySet())
        } else {
            uiState.copy(selectedIds = music.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        uiState = uiState.copy(selectionMode = false, selectedIds = emptySet())
    }

    private fun visibleMusic(): List<CloudFile> = uiState.visibleFiles.filter { it.isMusic() }

    fun playMusic(index: Int) {
        val music = visibleMusic()
        if (index !in music.indices) return
        viewModelScope.launch { player.playFiles(music, index) }
    }

    fun playAll() {
        val music = visibleMusic()
        if (music.isEmpty()) return
        viewModelScope.launch { player.playFiles(music, 0) }
    }

    fun playSelected() {
        val selected = uiState.selectedIds
        val music = visibleMusic().filter { it.id in selected }
        if (music.isEmpty()) return
        viewModelScope.launch {
            player.playFiles(music, 0)
            clearSelection()
        }
    }
}

data class LocalUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val files: List<CloudFile> = emptyList(),
    val query: String = "",
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    val visibleFiles: List<CloudFile>
        get() = if (query.isBlank()) files
        else files.filter { it.name.contains(query.trim(), ignoreCase = true) }

    val allSelected: Boolean
        get() = visibleFiles.isNotEmpty() && selectedIds.size == visibleFiles.size
}
