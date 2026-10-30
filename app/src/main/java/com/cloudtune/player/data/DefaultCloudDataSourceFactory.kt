package com.cloudtune.player.data

import com.cloudtune.player.core.model.BackendType
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.data.pan123.Pan123CloudDataSource
import com.cloudtune.player.data.quark.QuarkCloudDataSource
import com.cloudtune.player.data.tianyi.TianyiCloudDataSource
import com.cloudtune.player.data.webdav.WebDavCloudDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 默认数据源工厂：按 [ServerConfig.backend] 分发到各网盘实现。
 * 每次 create 都使用统一超时 / 重定向策略构建一个新的 [OkHttpClient]。
 */
class DefaultCloudDataSourceFactory : CloudDataSourceFactory {

    override fun create(config: ServerConfig): CloudDataSource {
        val client = buildClient()
        return when (config.backend) {
            BackendType.WEBDAV -> WebDavCloudDataSource(config, client)
            BackendType.TIANYI_DIRECT -> TianyiCloudDataSource(config, client)
            BackendType.PAN123 -> Pan123CloudDataSource(config, client)
            BackendType.QUARK -> QuarkCloudDataSource(config, client)
        }
    }

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
