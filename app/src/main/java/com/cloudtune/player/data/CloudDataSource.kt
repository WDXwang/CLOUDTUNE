package com.cloudtune.player.data

import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StreamSource

/**
 * 云端文件数据源的统一抽象。不同后端（WebDAV / 天翼直连）各自实现。
 */
interface CloudDataSource {

    /** 连通性 / 凭据校验，返回成功或失败原因 */
    suspend fun test(): Result<Unit>

    /**
     * 列出某文件夹下的内容（文件夹 + 文件）。
     * @param dir 为 null 表示配置中的根目录。
     */
    suspend fun list(dir: CloudFile?): List<CloudFile>

    /**
     * 把一个音乐文件解析为可播放的 [StreamSource]（现取现用，直链可能含时效）。
     */
    suspend fun resolveStream(file: CloudFile): StreamSource

    /** 释放底层连接资源 */
    fun close()
}

/**
 * 依据 [ServerConfig] 创建对应的 [CloudDataSource]。
 */
fun interface CloudDataSourceFactory {
    fun create(config: ServerConfig): CloudDataSource
}
