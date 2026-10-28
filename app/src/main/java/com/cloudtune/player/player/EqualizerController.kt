package com.cloudtune.player.player

import android.media.audiofx.Equalizer
import com.cloudtune.player.data.settings.EqualizerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 均衡器界面状态。
 */
data class EqUiState(
    val enabled: Boolean = false,
    val bandCount: Int = 5,
    /** 各频段增益（毫贝） */
    val gains: List<Int> = emptyList(),
    /** 各频段中心频率标签，如 "60Hz"、"1kHz" */
    val bandLabels: List<String> = emptyList(),
    val minGain: Int = -1500,
    val maxGain: Int = 1500,
)

/**
 * 均衡器控制器：把 Android [Equalizer] 绑定到后台播放器的音频会话，
 * 提供开关 / 预置 / 频段增益调节，并持久化。
 *
 * 音频会话由 PlaybackService 在创建 ExoPlayer 时通过
 * [android.media.AudioManager.generateAudioSessionId] 固定分配，
 * 因此 UI 层与后台服务都能用同一个 sessionId 控制同一个 Equalizer。
 */
class EqualizerController(private val store: EqualizerStore) {

    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var equalizer: Equalizer? = null

    private val _state = MutableStateFlow(EqUiState())
    val state: StateFlow<EqUiState> = _state.asStateFlow()

    /**
     * 绑定到指定音频会话（由 PlaybackService 调用），并应用已保存设置。
     */
    fun attach(sessionId: Int) {
        release()
        val eq = runCatching { Equalizer(0, sessionId) }.getOrNull() ?: return
        equalizer = eq

        val bandCount = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange // [min, max] 毫贝（Short）
        val min = range[0].toInt()
        val max = range[1].toInt()

        // 频段标签
        val labels = (0 until bandCount).map { formatHz(eq.getCenterFreq(it.toShort()).toInt()) }

        // 读取已保存增益；长度不符（设备频段数变化）则归零
        var gains = savedGains
        if (gains.size != bandCount) gains = List(bandCount) { 0 }

        val enabled = savedEnabled

        // 应用
        eq.enabled = enabled
        gains.forEachIndexed { band, g ->
            runCatching { eq.setBandLevel(band.toShort(), g.toShort()) }
        }

        _state.value = EqUiState(
            enabled = enabled,
            bandCount = bandCount,
            gains = gains,
            bandLabels = labels,
            minGain = min,
            maxGain = max,
        )
    }

    /** 释放底层 Equalizer（由 PlaybackService 在销毁时调用）。 */
    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }

    fun setEnabled(on: Boolean) {
        equalizer?.enabled = on
        _state.value = _state.value.copy(enabled = on)
        scope.launch { store.saveEnabled(on) }
    }

    fun setBandGain(band: Int, gain: Int) {
        val s = _state.value
        if (band !in s.gains.indices) return
        val clamped = gain.coerceIn(s.minGain, s.maxGain)
        runCatching { equalizer?.setBandLevel(band.toShort(), clamped.toShort()) }
        val newGains = s.gains.toMutableList().apply { this[band] = clamped }
        _state.value = s.copy(gains = newGains)
        scope.launch { store.saveGains(newGains) }
    }

    // ---------- 内部 ----------

    // 进程内缓存已保存值，attach 时同步读取
    private var savedEnabled = false
    private var savedGains: List<Int> = emptyList()

    /** 从存储加载（构造后调用一次）。 */
    suspend fun loadFromStore() {
        savedEnabled = store.loadEnabled()
        savedGains = store.loadGains()
    }

    private fun formatHz(freq: Int): String = when {
        freq >= 1000 -> "%.1fk".format(freq / 1000f)
        else -> "${freq}H"
    }
}
