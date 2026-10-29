package com.cloudtune.player.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.data.db.HistoryEntity
import com.cloudtune.player.player.PlayerControllerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 历史记录 UI 项。 */
data class HistoryItem(
    val file: CloudFile,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val playedAt: Long,
    val playCount: Int,
)

/**
 * 历史播放记录 ViewModel：订阅 history 表，解码 fileJson 还原 CloudFile。
 */
class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as CloudTuneApp).container
    private val playerManager: PlayerControllerManager = container.playerManager
    private val json = Json { ignoreUnknownKeys = true }

    val history: StateFlow<List<HistoryItem>> = container.historyDao.observeAll()
        .map { list -> list.mapNotNull { it.toItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun HistoryEntity.toItem(): HistoryItem? = runCatching {
        val file = json.decodeFromString(CloudFile.serializer(), fileJson)
        HistoryItem(
            file = file,
            title = title.ifBlank { file.name },
            artist = artist,
            durationMs = durationMs,
            playedAt = playedAt,
            playCount = playCount,
        )
    }.getOrNull()

    /** 播放历史列表中的第 [index] 项。 */
    fun playAt(items: List<HistoryItem>, index: Int) {
        if (index !in items.indices) return
        viewModelScope.launch {
            playerManager.playFiles(items.map { it.file }, index)
        }
    }

    fun remove(id: String) {
        viewModelScope.launch { container.historyDao.deleteById(id) }
    }

    fun clear() {
        viewModelScope.launch { container.historyDao.clear() }
    }
}
