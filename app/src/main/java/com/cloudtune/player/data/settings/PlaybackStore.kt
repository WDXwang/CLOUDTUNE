package com.cloudtune.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** 进程内唯一的播放偏好 DataStore。 */
private val Context.playbackDataStore by preferencesDataStore(name = "cloudtune_playback")

/**
 * 播放偏好持久化（倍速、软件音量）。
 */
class PlaybackStore(private val context: Context) {

    /** 上次保存的播放倍速，默认 1.0。 */
    suspend fun loadSpeed(): Float =
        context.playbackDataStore.data.first()[KEY_SPEED] ?: 1f

    suspend fun saveSpeed(speed: Float) {
        context.playbackDataStore.edit { it[KEY_SPEED] = speed }
    }

    /** 上次保存的软件音量（0.0~2.0，1.0 为正常，>1.0 为增益放大），默认 1.0。 */
    suspend fun loadVolume(): Float =
        context.playbackDataStore.data.first()[KEY_VOLUME] ?: 1f

    suspend fun saveVolume(volume: Float) {
        context.playbackDataStore.edit { it[KEY_VOLUME] = volume.coerceIn(0f, 2f) }
    }

    companion object {
        private val KEY_SPEED = floatPreferencesKey("playback_speed")
        private val KEY_VOLUME = floatPreferencesKey("playback_volume")
    }
}
