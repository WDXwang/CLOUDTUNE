package com.cloudtune.player.player

import android.os.CountDownTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 睡眠定时控制器：到点自动暂停播放。
 *
 * 状态：
 * - [enabled=false]   未启用
 * - [enabled=true]    倒计时中，[remainingMs] 为剩余毫秒
 */
class SleepTimer(private val player: PlayerControllerManager) {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    @Volatile
    private var timer: CountDownTimer? = null

    /**
     * 启动一个时长 [minutes] 的定时（分钟）。
     * 传入 0 或负数 = 取消。
     */
    fun start(minutes: Int) {
        cancelInternal()
        if (minutes <= 0) {
            _state.value = SleepState()
            return
        }
        val totalMs = minutes * 60_000L
        timer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _state.value = SleepState(enabled = true, remainingMs = millisUntilFinished)
            }
            override fun onFinish() {
                _state.value = SleepState()
                // 到点动作：停播 + 把音量慢慢拉到 0 再暂停（体验更自然）
                scope.launch {
                    try {
                        // 10 秒淡出
                        fadeOutThenPause(totalMs = 10_000L)
                    } catch (_: Exception) {
                        player.pause()
                    }
                }
            }
        }.start()
        _state.value = SleepState(enabled = true, remainingMs = totalMs)
    }

    /** 取消睡眠定时 */
    fun cancel() {
        cancelInternal()
        _state.value = SleepState()
    }

    /** 延长 [minutes] 分钟（在当前剩余时间上加时；未启用时等同 start） */
    fun addMinutes(minutes: Int) {
        if (minutes <= 0) return
        val base = if (_state.value.enabled) _state.value.remainingMs else 0L
        val newMs = base + minutes * 60_000L
        timer?.cancel()
        timer = object : CountDownTimer(newMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _state.value = SleepState(enabled = true, remainingMs = millisUntilFinished)
            }
            override fun onFinish() {
                _state.value = SleepState()
                scope.launch { fadeOutThenPause(totalMs = 10_000L) }
            }
        }.start()
        _state.value = SleepState(enabled = true, remainingMs = newMs)
    }

    private fun cancelInternal() {
        timer?.cancel()
        timer = null
    }

    /**
     * 淡出：10 秒内把音量从 100% 拉到 0 再暂停。
     * 直接操作 ExoPlayer 的 volume，不影响系统音量。
     */
    private suspend fun fadeOutThenPause(totalMs: Long) {
        val steps = 20
        val stepMs = totalMs / steps
        var volume = 1f
        repeat(steps) { i ->
            volume = 1f * (1f - (i + 1).toFloat() / steps)
            player.setVolume(volume)
            kotlinx.coroutines.delay(stepMs)
        }
        player.pause()
        player.setVolume(1f)
    }

    companion object {
        /** 预设时长（分钟） */
        val PRESETS = listOf(15, 30, 45, 60, 90, 120)
    }
}

data class SleepState(
    /** 是否启用睡眠定时 */
    val enabled: Boolean = false,
    /** 剩余毫秒；未启用时为 0 */
    val remainingMs: Long = 0L,
) {
    /** 剩余时间的中文显示，如 "还剩 23 分 10 秒" */
    fun display(): String {
        if (!enabled) return ""
        val total = remainingMs / 1000L
        val m = total / 60
        val s = total % 60
        return if (m >= 60) {
            val h = m / 60
            val rm = m % 60
            "还剩 ${h} 时 ${rm} 分"
        } else if (m > 0) {
            "还剩 ${m} 分 ${s} 秒"
        } else {
            "还剩 ${s} 秒"
        }
    }
}
