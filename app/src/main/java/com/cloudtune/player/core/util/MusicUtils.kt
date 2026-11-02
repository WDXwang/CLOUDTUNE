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
    private val ARTIST_SEPARATORS = listOf(" - ", " – ", " — ", "－", "–", "—", "～", "~")

    /** 音轨号前缀：`01. `、`01、`、`01 - `、`01_` 等，解析前先剥掉。 */
    private val TRACK_NO_PREFIX = Regex("""^\d{1,3}(\s*[.、]\s*|\s*[_\-–—～~]\s+)""")

    /** 常见通用目录名，作为歌手名没有意义（用父目录兜底时要跳过）。 */
    private val GENERIC_FOLDERS = setOf(
        "music", "musics", "音乐", "歌曲", "我的音乐", "audio", "音频",
        "download", "downloads", "下载", "cloudtune", "sdcard", "storage",
    )

    /** 去掉扩展名与音轨号前缀后的「干净」文件名。 */
    fun cleanBaseName(name: String): String {
        val base = baseName(name)
        val stripped = TRACK_NO_PREFIX.replace(base, "").trim()
        return stripped.ifEmpty { base.trim() }
    }

    /**
     * 从文件名解析歌手名：识别「歌手 - 歌名.mp3」这类命名（含无空格 `歌手-歌名`）。
     *
     * 云端接口拿不到 ID3 元数据，只能依赖文件名约定；
     * 不匹配任何模式时返回 null（调用方可用 [folderArtist] 按目录兜底）。
     */
    fun parseArtist(fileName: String): String? = splitArtistTitle(fileName)?.first

    /**
     * 从文件名解析歌名（与 [parseArtist] 配套）：能分出歌手时取右半部分，
     * 否则返回去扩展名（已剥掉音轨号前缀）后的完整文件名。
     */
    fun parseTitle(fileName: String): String =
        splitArtistTitle(fileName)?.second ?: cleanBaseName(fileName)

    /**
     * 文件名本身不含歌手时，用所在目录名兜底（取最靠近文件、且不是通用名 / 纯数字的目录）。
     *
     * @param fileName 文件名
     * @param path 文件所在路径（云端 path 或本地相对目录）
     */
    fun folderArtist(fileName: String, path: String): String? {
        if (path.isBlank()) return null
        val base = baseName(fileName)
        val segments = path.replace('\\', '/')
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (i in segments.indices.reversed()) {
            val name = segments[i]
            // 跳过与文件同名的段、带扩展名的文件段、通用目录、纯数字目录与 URL 段
            if (name.equals(base, ignoreCase = true) || name.equals(fileName, ignoreCase = true)) continue
            if (extension(name).isNotEmpty()) continue
            if (name.startsWith("http", ignoreCase = true)) continue
            if (name.lowercase(Locale.US) in GENERIC_FOLDERS) continue
            if (name.all { it.isDigit() }) continue
            return name
        }
        return null
    }

    /** 按分隔符把「歌手 + 歌名」切开，切不出返回 null。 */
    private fun splitArtistTitle(fileName: String): Pair<String, String>? {
        val base = cleanBaseName(fileName)
        if (base.isEmpty()) return null
        // 1) 常规带空格分隔符 / 全角破折号
        for (sep in ARTIST_SEPARATORS) {
            val idx = base.indexOf(sep)
            if (idx > 0) {
                val artist = base.substring(0, idx).trim()
                val title = base.substring(idx + sep.length).trim()
                if (isValidPair(artist, title)) return artist to title
            }
        }
        // 2) 无空格连字符（如「周杰伦-告白气球」），要求两侧都不是纯数字
        val idx = base.indexOf('-')
        if (idx > 0) {
            val artist = base.substring(0, idx).trim()
            val title = base.substring(idx + 1).trim()
            if (isValidPair(artist, title) &&
                !artist.all { it.isDigit() } && !title.all { it.isDigit() }
            ) {
                return artist to title
            }
        }
        return null
    }

    private fun isValidPair(artist: String, title: String): Boolean =
        artist.isNotEmpty() && title.isNotEmpty() && artist.length <= 40

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
