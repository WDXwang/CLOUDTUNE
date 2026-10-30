package com.cloudtune.player.core.util

import java.util.Locale

/**
 * 视频文件识别工具：按扩展名判断常见视频格式。
 */
object VideoUtils {

    /** 常见视频扩展名（含 HLS m3u8 流媒体索引） */
    val VIDEO_EXTENSIONS: Set<String> = setOf(
        "mp4", "mkv", "webm", "mov", "avi", "flv", "ts", "3gp", "3g2",
        "m4v", "mpg", "mpeg", "wmv", "rmvb", "rm", "asf", "m2ts", "mts",
        "m3u8",
    )

    /** 外挂字幕扩展名 */
    val SUBTITLE_EXTENSIONS: Set<String> = setOf("srt", "vtt")

    /** 判断文件名是否为视频文件 */
    fun isVideoFile(name: String): Boolean {
        val idx = name.lastIndexOf('.')
        val ext = if (idx >= 0 && idx < name.length - 1) {
            name.substring(idx + 1).lowercase(Locale.US)
        } else ""
        return ext in VIDEO_EXTENSIONS
    }

    /** 根据字幕扩展名返回 Media3 需要的 mimeType；不认识返回 null */
    fun subtitleMime(name: String): String? {
        val idx = name.lastIndexOf('.')
        val ext = if (idx >= 0 && idx < name.length - 1) {
            name.substring(idx + 1).lowercase(Locale.US)
        } else ""
        return when (ext) {
            "srt" -> "application/x-subrip"
            "vtt" -> "text/vtt"
            else -> null
        }
    }
}
