package com.cloudtune.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cloudtune.player.core.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** 进程内唯一的 DataStore 实例（通过顶层属性委托）。 */
private val Context.settingsDataStore by preferencesDataStore(name = "cloudtune_settings")

/**
 * 服务器配置本地持久化：使用 DataStore Preferences。
 * [ServerConfig] 通过 kotlinx.serialization 序列化为 JSON 字符串存为单条偏好。
 */
class SettingsStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true // 模型字段后续可能增减，老数据容错
        encodeDefaults = true
    }

    /** 当前已保存的配置；未登录 / 未配置时为 null。 */
    val config: Flow<ServerConfig?> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_CONFIG]?.let { raw ->
            runCatching { json.decodeFromString(ServerConfig.serializer(), raw) }.getOrNull()
        }
    }

    /** 保存 / 覆盖配置。 */
    suspend fun saveConfig(c: ServerConfig) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_CONFIG] = json.encodeToString(ServerConfig.serializer(), c)
        }
    }

    /** 清除配置（退出登录）。 */
    suspend fun clearConfig() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(KEY_CONFIG)
        }
    }

    companion object {
        private val KEY_CONFIG = stringPreferencesKey("server_config_json")
    }
}
