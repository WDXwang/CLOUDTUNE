package com.cloudtune.player.core.util

import java.util.Locale

/**
 * 音乐文件相关的工具：扩展名识别、时长 / 文件大小格式化。
 */
object MusicUtils {

    /** 支持的音乐文件扩展名（含无损格式） */
    val MUSIC_EXTENSIONS: Set<String> = setOf(
        "mp3", "flac", "wav", "ape", "m4a", "ogg", "oga",
        "aac", "wma", "opus", "aiff", "aif", "alac", "dsf", "dff"
    )

    /** 取文件扩展名（不含点，小写），无扩展名返回空串 */
    fun extension(name: String): String {
        val idx = name.lastIndexOf('.')
        return if (idx >= 0 && idx < name.length - 1) {
            name.substring(idx + 1).lowercase(Locale.US)
        } else ""
    }

    /** 判断文件名是否为音乐文件 */
    fun isMusicFile(name: String): Boolean = extension(name) in MUSIC_EXTENSIONS

    /** 解析歌手时常用的「歌手 - 歌名」分隔符（含全角 / 外文破折号）。 */
    private val ARTIST_SEPARATORS = listOf(" - ", " – ", " — ", "－", "–", "—")

    /**
     * 从文件名解析歌手名：识别「歌手 - 歌名.mp3」这类命名。
     *
     * 云端接口拿不到 ID3 元数据，只能依赖文件名约定；
     * 不匹配任何分隔模式时返回 null（调用方通常会跳过该曲目）。
     */
    fun parseArtist(fileName: String): String? {
        val base = baseName(fileName)
        for (sep in ARTIST_SEPARATORS) {
            val idx = base.indexOf(sep)
            if (idx > 0) {
                val artist = base.substring(0, idx).trim()
                val rest = base.substring(idx + sep.length).trim()
                if (artist.isNotEmpty() && rest.isNotEmpty()) return artist
            }
        }
        return null
    }

    /**
     * 从文件名解析歌名（与 [parseArtist] 配套）：能分出歌手时取右半部分，
     * 否则返回去扩展名后的完整文件名。
     */
    fun parseTitle(fileName: String): String {
        val base = baseName(fileName)
        for (sep in ARTIST_SEPARATORS) {
            val idx = base.indexOf(sep)
            if (idx > 0) {
                val artist = base.substring(0, idx).trim()
                val rest = base.substring(idx + sep.length).trim()
                if (artist.isNotEmpty() && rest.isNotEmpty()) return rest
            }
        }
        return base
    }

    /** 去掉扩展名后的文件名，用作默认标题 */
    fun baseName(name: String): String {
        val idx = name.lastIndexOf('.')
        return if (idx > 0) name.substring(0, idx) else name
    }

    /** 毫秒 -> mm:ss 或 h:mm:ss */
    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /** 字节数 -> 人类可读大小 */
    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024.0
            i++
        }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }
}
