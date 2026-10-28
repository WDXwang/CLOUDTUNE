package com.cloudtune.player.player

import android.net.Uri
import android.util.Base64
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.util.MusicUtils
import kotlinx.serialization.json.Json

/**
 * 把云端文件 [CloudFile] 编码进 ExoPlayer 可识别的自定义 URI：
 *
 *   cloudtune://track?data=<Base64(NO_WRAP)(JSON(CloudFile))>
 *
 * 真实直链并不直接写进 URI，而是交给 [CloudStreamResolverFactory] 在播放开始前
 * 通过 ResolvingDataSource 现取现解析，避免把短期有效的直链长期固化在 MediaItem 里。
 */
object CloudMedia {

    const val SCHEME = "cloudtune"
    const val AUTHORITY = "track"
    private const val QUERY_DATA = "data"

    // 容错解析：后端字段后续可能增减，未知字段忽略即可
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 由 [file] 构造 MediaItem：内嵌自定义 URI 作为播放地址，mediaId 用文件 id。 */
    fun mediaItem(file: CloudFile): MediaItem {
        // 1. CloudFile -> JSON 字符串
        val jsonStr = json.encodeToString(CloudFile.serializer(), file)
        // 2. Base64(NO_WRAP) 编码后放进 query 参数，避免换行破坏 URI
        val encoded = Base64.encodeToString(jsonStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val uri = Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendQueryParameter(QUERY_DATA, encoded)
            .build()

        // 标题默认用“去扩展名的文件名”
        val title = MusicUtils.baseName(file.name)
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .build()

        return MediaItem.Builder()
            .setMediaId(file.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /** 反向解析：从自定义 URI 还原 [CloudFile]，任何异常都返回 null（容错）。 */
    fun decode(uri: Uri): CloudFile? = runCatching {
        // 仅处理本 scheme 的 URI
        if (uri.scheme != SCHEME || uri.host != AUTHORITY) return@runCatching null
        val encoded = uri.getQueryParameter(QUERY_DATA) ?: return@runCatching null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val jsonStr = String(bytes, Charsets.UTF_8)
        json.decodeFromString(CloudFile.serializer(), jsonStr)
    }.getOrNull()
}
