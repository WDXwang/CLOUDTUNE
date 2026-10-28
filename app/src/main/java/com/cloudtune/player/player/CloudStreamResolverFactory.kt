package com.cloudtune.player.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.cloudtune.player.data.MusicRepository
import okhttp3.OkHttpClient
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * 组装播放用的 [DataSource.Factory]，三层结构（从内到外 / 从上游到下游）：
 *
 *   1. OkHttpDataSource.Factory   —— 真正发 HTTP 请求的网络层（设置 User-Agent）
 *   2. ResolvingDataSource.Factory —— 把 cloudtune://track 占位 DataSpec 换成仓库解析出的真实直链
 *   3. CacheDataSource.Factory    —— 在外层包一层 SimpleCache，实现边播边缓存
 *
 * SimpleCache 要求“同一缓存目录进程内只能打开一次”，因此用 @Volatile + 双检锁做进程级单例。
 */
@UnstableApi
object CloudStreamResolverFactory {

    /** 进程级唯一的 SimpleCache（双检锁惰性初始化） */
    @Volatile
    private var simpleCache: SimpleCache? = null

    private fun cache(context: Context): SimpleCache =
        simpleCache ?: synchronized(this) {
            simpleCache ?: buildCache(context).also { simpleCache = it }
        }

    private fun buildCache(context: Context): SimpleCache {
        val appContext = context.applicationContext
        val cacheDir = File(appContext.cacheDir, "media")
        // 缓存上限 512MB，LRU 淘汰
        val evictor = LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024)
        val databaseProvider = StandaloneDatabaseProvider(appContext)
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    /**
     * 构建给 ExoPlayer 使用的数据源工厂。
     */
    fun buildDataSourceFactory(context: Context, repository: MusicRepository): DataSource.Factory {
        // 第一层：网络层 OkHttp 数据源
        val okHttpFactory = OkHttpDataSource.Factory(OkHttpClient())
            .setUserAgent("CloudTune")

        // 第一层补充：DefaultDataSource 同时支持 content://、file://、asset 与网络，
        // 本地音乐（content://）与云盘（https://）都由它统一读取
        val baseFactory = DefaultDataSource.Factory(context, okHttpFactory)

        // 第二层：解析层 —— 播放开始前把自定义 URI 换成真实可下载直链
        val resolvingFactory = ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            // 解析出占位 URI 里内嵌的 CloudFile
            val file = CloudMedia.decode(dataSpec.uri)
            if (file == null) {
                // 不是我们的 scheme，原样透传给上游
                dataSpec
            } else {
                // 直链有效期短，现取现用；ResolvingDataSource 在加载线程同步调用，
                // 这里 runBlocking 桥接协程
                val src = runBlocking { repository.resolveStream(file) }

                // 替换为真实直链并带上鉴权所需请求头（setHttpRequestHeaders 会整体替换请求头）
                dataSpec.buildUpon()
                    .setUri(Uri.parse(src.url))
                    .setHttpRequestHeaders(src.headers)
                    .build()
            }
        }

        // 第三层：缓存层 —— 包在最外层，读到的块先落 SimpleCache 再给解码器
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(resolvingFactory)
    }
}
