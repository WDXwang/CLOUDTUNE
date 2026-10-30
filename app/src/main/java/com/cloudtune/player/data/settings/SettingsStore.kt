package com.cloudtune.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.core.model.displayName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 进程内唯一的 DataStore 实例（通过顶层属性委托）。 */
private val Context.settingsDataStore by preferencesDataStore(name = "cloudtune_settings")

/**
 * 多存储配置本地持久化：使用 DataStore Preferences。
 *
 * 账号列表（[StorageAccount]）整体序列化为一个 JSON 数组字符串存为单条偏好。
 * 兼容旧版本：旧版只保存单个 [ServerConfig]（KEY_CONFIG），读取时自动迁移为一条存储，
 * 用户下次保存账号列表时即完成落盘。
 */
class SettingsStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true // 模型字段后续可能增减，老数据容错
        encodeDefaults = true
    }

    private val accountsSerializer = ListSerializer(StorageAccount.serializer())

    /** 全部已添加的存储（含已停用的）。首次启动为空列表。 */
    val accounts: Flow<List<StorageAccount>> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[KEY_ACCOUNTS]
        if (raw != null) {
            runCatching { json.decodeFromString(accountsSerializer, raw) }.getOrElse { emptyList() }
        } else {
            // 旧版本单配置 → 迁移为一条存储（不立即写回，避免在 map 里做写操作）
            prefs[KEY_CONFIG]?.let { legacy ->
                runCatching {
                    val config = json.decodeFromString(ServerConfig.serializer(), legacy)
                    listOf(
                        StorageAccount(
                            id = LEGACY_ACCOUNT_ID,
                            name = config.backend.displayName,
                            config = config,
                            createdAt = 0L,
                        )
                    )
                }.getOrElse { emptyList() }
            } ?: emptyList()
        }
    }

    /** 兼容旧调用：当前列表中的第一条配置。 */
    val config: Flow<ServerConfig?> = accounts.map { list -> list.firstOrNull()?.config }

    /** 覆盖保存全部存储。 */
    suspend fun saveAccounts(list: List<StorageAccount>) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_ACCOUNTS] = json.encodeToString(accountsSerializer, list)
            // 迁移完成，移除旧键
            prefs.remove(KEY_CONFIG)
        }
    }

    /** 清除全部存储（退出登录）。 */
    suspend fun clearAccounts() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(KEY_ACCOUNTS)
            prefs.remove(KEY_CONFIG)
        }
    }

    companion object {
        private val KEY_ACCOUNTS = stringPreferencesKey("storage_accounts_json")
        private val KEY_CONFIG = stringPreferencesKey("server_config_json")
        private const val LEGACY_ACCOUNT_ID = "legacy-account"
    }
}
