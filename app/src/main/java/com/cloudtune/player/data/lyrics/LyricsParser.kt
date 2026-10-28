package com.cloudtune.player.data.lyrics

/** 一行带时间戳的歌词。 */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * LRC 歌词解析：支持 `[mm:ss.xx]` / `[mm:ss:xxx]` 时间戳，及同一行多个时间戳。
 */
object LyricsParser {

    private val TIME_TAG = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(lrc: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        lrc.lineSequence().forEach { rawLine ->
            val matches = TIME_TAG.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val lastIdx = rawLine.lastIndexOf(']')
            val text = rawLine.substring(lastIdx + 1).trim()
            if (text.isEmpty()) return@forEach
            matches.forEach { m ->
                val mm = m.groupValues[1].toIntOrNull() ?: 0
                val ss = m.groupValues[2].toIntOrNull() ?: 0
                val fracMs = when (m.groupValues[3].length) {
                    1 -> (m.groupValues[3].toIntOrNull() ?: 0) * 100
                    2 -> (m.groupValues[3].toIntOrNull() ?: 0) * 10
                    3 -> m.groupValues[3].toIntOrNull() ?: 0
                    else -> 0
                }
                result += LyricLine(
                    timeMs = mm * 60_000L + ss * 1_000L + fracMs,
                    text = text,
                )
            }
        }
        return result.sortedBy { it.timeMs }
    }
}
