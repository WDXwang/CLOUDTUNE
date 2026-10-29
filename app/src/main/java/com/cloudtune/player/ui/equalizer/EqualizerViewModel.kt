package com.cloudtune.player.ui.equalizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.player.EqUiState
import com.cloudtune.player.player.EqualizerController
import kotlinx.coroutines.flow.StateFlow

/**
 * 均衡器页 ViewModel，直接转发 [EqualizerController]。
 */
class EqualizerViewModel(app: Application) : AndroidViewModel(app) {

    private val controller: EqualizerController =
        (app as CloudTuneApp).container.equalizerController

    val state: StateFlow<EqUiState> = controller.state

    fun setEnabled(on: Boolean) = controller.setEnabled(on)

    fun setBandGain(band: Int, gain: Int) = controller.setBandGain(band, gain)

    /** 应用内置预设（如现场氛围）。 */
    fun applyPreset(db: List<Int>) = controller.applyPreset(db)

    /** 一键归零（各频段 0，并启用）。 */
    fun reset() {
        val s = controller.state.value
        s.gains.indices.forEach { band ->
            controller.setBandGain(band, 0)
        }
        controller.setEnabled(true)
    }
}
