package com.cloudtune.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** 进程内唯一的均衡器设置 DataStore。 */
private val Context.eqDataStore by preferencesDataStore(name = "cloudtune_eq")

/**
 * 均衡器设置持久化：开关、当前预置、各频段增益（毫贝）。
 */
class EqualizerStore(private val context: Context) {

    /** 是否启用均衡器。 */
    suspend fun loadEnabled(): Boolean =
        context.eqDataStore.data.first()[KEY_ENABLED] ?: false

    suspend fun saveEnabled(on: Boolean) {
        context.eqDataStore.edit { it[KEY_ENABLED] = on }
    }

    /** 当前预置序号。 */
    suspend fun loadPreset(): Int =
        context.eqDataStore.data.first()[KEY_PRESET] ?: 0

    suspend fun savePreset(index: Int) {
        context.eqDataStore.edit { it[KEY_PRESET] = index }
    }

    /** 各频段增益（毫贝，长度=频段数）。 */
    suspend fun loadGains(): List<Int> {
        val raw = context.eqDataStore.data.first()[KEY_GAINS] ?: return emptyList()
        return raw.split(',').mapNotNull { it.toIntOrNull() }
    }

    suspend fun saveGains(gains: List<Int>) {
        context.eqDataStore.edit { it[KEY_GAINS] = gains.joinToString(",") }
    }

    companion object {
        private val KEY_ENABLED = booleanPreferencesKey("eq_enabled")
        private val KEY_PRESET = intPreferencesKey("eq_preset")
        private val KEY_GAINS = stringPreferencesKey("eq_gains")
    }
}
