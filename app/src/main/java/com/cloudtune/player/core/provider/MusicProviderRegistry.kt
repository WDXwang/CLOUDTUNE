package com.cloudtune.player.core.provider

/**
 * Provider 注册表 — 集中管理全部已注册的 MusicProvider 实现。
 *
 * 类似 MeloX 的 MusicProviderRegistry：按 source 索引，支持查询某能力是否可用，
 * 以及遍历所有 Provider 做跨源聚合（如 UnifiedMusicService）。
 */
class MusicProviderRegistry {

    private val bySource = mutableMapOf<MusicSource, MusicProvider>()
    private val all = mutableListOf<MusicProvider>()

    fun register(provider: MusicProvider) {
        bySource[provider.source] = provider
        all.add(provider)
    }

    fun all(): List<MusicProvider> = all.toList()

    fun bySource(source: MusicSource): MusicProvider? = bySource[source]

    /** 查询所有支持某能力的 Provider */
    fun withCapability(capability: MusicCapability): List<MusicProvider> =
        all.filter { it.supports(capability) }

    /** 跨源聚合搜索：并行请求所有支持 Search 的 Provider，按来源去重 */
    suspend fun aggregateSearch(query: String): List<ProviderTrack> {
        val searchers = all.filterIsInstance<SearchCapability>()
        if (searchers.isEmpty()) return emptyList()
        val results = searchers.flatMap { src ->
            runCatching { src.searchSongs(query, pageSize = 10) }.getOrDefault(emptyList())
        }
        return results.distinctBy { "${it.source}:${it.id}" }
    }
}
