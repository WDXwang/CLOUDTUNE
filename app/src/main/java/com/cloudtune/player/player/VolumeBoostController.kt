package com.cloudtune.player.player

import android.media.audiofx.LoudnessEnhancer

/**
 * 音量增益控制器：把 Android [LoudnessEnhancer] 绑定到后台播放器的音频会话，
 * 实现「超过 100% 音量」的软件增益。
 *
 * 为什么不直接用 MediaController/ExoPlayer 的 volume > 1.0：
 * MediaController 跨进程对 volume 取值范围有校验，设置 >1 会抛异常导致闪退。
 * 因此 0~100% 用播放器自身 volume（0..1），100% 以上的增益统一交给本类，
 * 稳定且不崩。
 */
class VolumeBoostController {

    private var enhancer: LoudnessEnhancer? = null

    /** 绑定到音频会话（由 PlaybackService 与均衡器一起调用）。 */
    fun attach(sessionId: Int) {
        release()
        enhancer = runCatching {
            LoudnessEnhancer(sessionId).apply {
                enabled = true
                setTargetGain(0)
            }
        }.getOrNull()
    }

    /**
     * 设置增益（毫贝，1000 mB = 10 dB）。
     * 由整体音量换算：volume>1 时 gain = (volume-1)*1000。
     */
    fun setGain(gainMilliB: Int) {
        runCatching { enhancer?.setTargetGain(gainMilliB.coerceAtLeast(0)) }
    }

    fun release() {
        runCatching { enhancer?.release() }
        enhancer = null
    }
}
