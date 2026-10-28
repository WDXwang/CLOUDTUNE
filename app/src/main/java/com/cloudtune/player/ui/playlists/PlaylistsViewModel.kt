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
}
