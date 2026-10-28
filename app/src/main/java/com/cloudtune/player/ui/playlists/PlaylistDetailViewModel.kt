package com.cloudtune.player.ui.playlists

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.MusicTrack
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.player.PlayerControllerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 歌单详情页 ViewModel。
 */
class PlaylistDetailViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val repository: MusicRepository =
        (app as CloudTuneApp).container.repository
    private val player: PlayerControllerManager =
        (app as CloudTuneApp).container.playerManager

    val playlistId: Long = savedStateHandle["playlistId"] ?: -1L

    /** 歌单内歌曲 */
    val tracks: StateFlow<List<MusicTrack>> =
        repository.observePlaylistTracks(playlistId).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )

    var uiState by mutableStateOf(PlaylistDetailUiState())
        private set

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
        val music = visibleTracks()
        uiState = if (uiState.selectedIds.size == music.size && music.isNotEmpty()) {
            uiState.copy(selectedIds = emptySet())
        } else {
            uiState.copy(selectedIds = music.map { it.file.id }.toSet())
        }
    }

    fun clearSelection() {
        uiState = uiState.copy(selectionMode = false, selectedIds = emptySet())
    }

    /** 从歌单移除选中的歌曲 */
    fun removeSelected() {
        val selected = uiState.selectedIds
        viewModelScope.launch {
            selected.forEach { fileId -> repository.removeFromPlaylist(playlistId, fileId) }
            clearSelection()
        }
    }

    fun rename(name: String) {
        viewModelScope.launch { repository.renamePlaylist(playlistId, name) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            onDone()
        }
    }

    private fun visibleTracks(): List<MusicTrack> = uiState.visibleTracks(tracks.value)

    fun playMusic(index: Int) {
        val music = visibleTracks()
        if (index !in music.indices) return
        viewModelScope.launch { player.playFiles(music.map { it.file }, index) }
    }

    fun playAll() {
        val music = visibleTracks()
        if (music.isEmpty()) return
        viewModelScope.launch { player.playFiles(music.map { it.file }, 0) }
    }

    fun playSelected() {
        val selected = uiState.selectedIds
        val music = visibleTracks().filter { it.file.id in selected }
        if (music.isEmpty()) return
        viewModelScope.launch {
            player.playFiles(music.map { it.file }, 0)
            clearSelection()
        }
    }
}

data class PlaylistDetailUiState(
    val query: String = "",
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
) {
    fun visibleTracks(all: List<MusicTrack>): List<MusicTrack> =
        if (query.isBlank()) all
        else all.filter { it.title.contains(query.trim(), ignoreCase = true) }
}
