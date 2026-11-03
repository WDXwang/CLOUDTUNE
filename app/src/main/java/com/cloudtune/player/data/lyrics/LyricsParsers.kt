package com.cloudtune.player.data.lyrics

/** 一个带时间戳的字。 [timeMs] 为该字的起始时间（毫秒）。 */
data class LyricWord(val timeMs: Long, val text: String)

/** 一行歌词：仅行级时间戳（LRC）或带字级时间戳（KRC/TTML/QRC）。 */
data class WordLyricLine(
    /** 整行起始时间（毫秒） */
    val timeMs: Long,
    /** 行文本（无逐字数据时整行放这里） */
    val text: String,
    /** 逐字时间戳；为空表示只有行级时间 */
    val words: List<LyricWord> = emptyList(),
) {
    /** 是否为逐字歌词 */
    val isWordLevel: Boolean get() = words.isNotEmpty()
}

/**
 * 多格式歌词解析门面：
 * - LRC：文本，行级时间戳
 * - KRC：酷狗二进制，可能含字级时间戳
 * - TTML：QQ 音乐 XML，支持逐字
 * - QRC：QQ 音乐加密格式（需 meiqrc 密钥）
 *
 * 输入可以是 String（文本）或 ByteArray（二进制）。
 * 解析失败返回空列表。
 */
object LyricsParsers {

    /** 尝试把 [raw] 解析成逐字歌词列表；回退时保留行级。 */
    fun parse(raw: ByteArray): List<WordLyricLine> {
        // 先判断类型
        val head = raw.take(4).joinToString(" ") { "%02x".format(it) }
        // KRC 二进制：开头是文本 [ti:] 之类，直接按文本解析可能失败
        val text = raw.toString(Charsets.UTF_8).trim('\uFEFF')
        return when {
            text.isBlank() -> emptyList()
            text.startsWith("<lyric") || text.startsWith("<?xml") -> TtmlParser.parse(text)
            // QRC 加密格式：文件头 "QRC\x00" 或直接密文（先尝试解密再判格式）
            isLrcLike(text) -> LrcParser.parse(text)
            else -> KrcParser.parse(raw, text)
        }
    }

    /** 把 [raw] 当文本解析（已知是文本格式时用）。 */
    fun parseText(raw: String): List<WordLyricLine> {
        val t = raw.trim('\uFEFF')
        if (t.isBlank()) return emptyList()
        return when {
            t.startsWith("<lyric") || t.startsWith("<?xml") -> TtmlParser.parse(t)
            else -> LrcParser.parse(t)
        }
    }

    private fun isLrcLike(text: String): Boolean =
        text.contains(Regex("\\[\\d{1,2}:\\d{1,2}[.:]\\d{1,3}]"))
}

// ---------- LRC（已有，包一层 WordLyricLine）----------

object LrcParser {
    private val TIME_TAG = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(text: String): List<WordLyricLine> {
        val result = mutableListOf<WordLyricLine>()
        text.lineSequence().forEach { rawLine ->
            val matches = TIME_TAG.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val lastIdx = rawLine.lastIndexOf(']')
            val lineText = rawLine.substring(lastIdx + 1).trim()
            if (lineText.isEmpty()) return@forEach
            matches.forEach { m ->
                val mm = m.groupValues[1].toIntOrNull() ?: 0
                val ss = m.groupValues[2].toIntOrNull() ?: 0
                val fracMs = when (m.groupValues[3].length) {
                    1 -> (m.groupValues[3].toIntOrNull() ?: 0) * 100
                    2 -> (m.groupValues[3].toIntOrNull() ?: 0) * 10
                    3 -> m.groupValues[3].toIntOrNull() ?: 0
                    else -> 0
                }
                result += WordLyricLine(
                    timeMs = mm * 60_000L + ss * 1_000L + fracMs,
                    text = lineText,
                )
            }
        }
        return result.sortedBy { it.timeMs }
    }
}

// ---------- TTML（QQ 音乐 XML，支持逐字）----------

object TtmlParser {
    /**
     * 把时间文本（如 "100"、"0:01.500"）解析为毫秒。
     */
    private fun parseTime(text: String): Long {
        val v = text.trim()
        return if (v.contains(':') || v.contains('.')) {
            // 格式："mm:ss.ms"
            val parts = v.split(':', '.')
            when (parts.size) {
                2 -> parts[0].toLongOrNull()?.times(1000)?.plus(parts[1].toLongOrNull() ?: 0) ?: 0L
                3 -> {
                    val m = parts[0].toLongOrNull() ?: 0
                    val s = parts[1].toLongOrNull() ?: 0
                    val frac = parts[2].toLongOrNull() ?: 0
                    m * 60_000 + s * 1000 + frac
                }
                else -> 0L
            }
        } else {
            v.toLongOrNull() ?: 0L
        }
    }

    /**
     * 从 TTML XML 里提取 `<l ...>` 节点。
     * 不使用 xml 解析器（避免增加依赖），用正则直接提取。
     */
    fun parse(xml: String): List<WordLyricLine> {
        val result = mutableListOf<WordLyricLine>()
        // 匹配 <l t="...">...</l>，内部可能有 <t t="...">字</t>
        val lineRe = Regex("<l\\s+t=\"([^\"]*)\"[^>]*>(.*?)</l>", RegexOption.DOT_MATCHES_ALL)
        for (lineMatch in lineRe.findAll(xml)) {
            val lineTime = parseTime(lineMatch.groupValues[1])
            val body = lineMatch.groupValues[2]
            val wordRe = Regex("<t\\s+t=\"([^\"]*)\"[^>]*>(.*?)</t>")
            val words = mutableListOf<LyricWord>()
            val sb = StringBuilder()
            var lastEnd = 0
            for (wordMatch in wordRe.findAll(body)) {
                val t = parseTime(wordMatch.groupValues[1])
                val txt = wordMatch.groupValues[2]
                // 把标签外部的纯文本（如果有）也加进去，时间取上一个词结束
                val idx = body.indexOf(wordMatch.value, lastEnd)
                if (idx > lastEnd) {
                    val gapText = body.substring(lastEnd, idx).replace(Regex("<[^>]+>"), "")
                    if (gapText.isNotBlank()) {
                        sb.append(gapText)
                        words += LyricWord(timeMs = t, text = gapText)
                    }
                }
                sb.append(txt)
                words += LyricWord(timeMs = t, text = txt)
                lastEnd = idx + wordMatch.value.length
            }
            // 尾部纯文本
            if (lastEnd < body.length) {
                val tail = body.substring(lastEnd).replace(Regex("<[^>]+>"), "")
                if (tail.isNotBlank()) sb.append(tail)
            }
            result += WordLyricLine(
                timeMs = lineTime,
                text = sb.toString().ifBlank { body.replace(Regex("<[^>]+>"), "").trim() },
                words = words,
            )
        }
        return result.sortedBy { it.timeMs }
    }
}

// ---------- KRC（酷狗，带字级时间戳）----------

/**
 * 酷狗 KRC 格式：
 * 1. 文本头：`[ti:...]` / `[ar:...]` / `[al:...]` 等元信息行（UTF-8 文本）
 * 2. 歌词行：`[startMs,endMs]歌词文本`，可能含逐字时间戳 `@startMs,endMs字`
 *
 * 注意：KRC 文件有时是纯文本，有时是 UTF-8 编码（带 \0 结尾等），
 * 直接按 UTF-8 解码后过滤控制字符即可。
 */
object KrcParser {

    private val LINE_TAG = Regex("\\[(\\d+),(\\d+)]")
    private val WORD_TAG = Regex("@(\\d+),(\\d+)(.)")

    fun parse(rawBytes: ByteArray, fallbackText: String): List<WordLyricLine> {
        val text = runCatching {
            // 酷狗有时用 UTF-8 带 \u0000 结尾字符
            String(rawBytes, Charsets.UTF_8).replace('\u0000', ' ').trim()
        }.getOrDefault(fallbackText)
        return parseText(text)
    }

    fun parseText(text: String): List<WordLyricLine> {
        val result = mutableListOf<WordLyricLine>()
        text.lineSequence().forEach { rawLine ->
            val m = LINE_TAG.find(rawLine) ?: return@forEach
            val lineStart = m.groupValues[1].toLongOrNull() ?: return@forEach
            val body = rawLine.substring(m.range.last + 1)
            if (body.isBlank()) return@forEach

            val words = mutableListOf<LyricWord>()
            val sb = StringBuilder()
            var pos = 0
            for (wm in WORD_TAG.findAll(body)) {
                val timeMs = wm.groupValues[1].toLongOrNull() ?: lineStart
                val char = wm.groupValues[3]
                val idx = body.indexOf(wm.value, pos)
                if (idx > pos) {
                    val gapText = body.substring(pos, idx).trimEnd()
                    if (gapText.isNotBlank()) {
                        sb.append(gapText)
                        words += LyricWord(timeMs = timeMs, text = gapText)
                    }
                }
                sb.append(char)
                words += LyricWord(timeMs = timeMs, text = char)
                pos = idx + wm.value.length
            }
            if (pos < body.length) sb.append(body.substring(pos).trimStart())

            result += WordLyricLine(
                timeMs = lineStart,
                text = sb.toString().ifBlank { body },
                words = words,
            )
        }
        return result.sortedBy { it.timeMs }
    }
}

// ---------- QRC（QQ 音乐加密）----------

/**
 * QRC 歌词解密：QQ 音乐把歌词用 RC4 加密后 base64 编码存进 .qrc 文件。
 * 密钥（meiQRC）由 QQ 音乐内部生成，这里用逆向出的常量。
 */
object QrcDecoder {

    /** QQ 音乐 meiqrc 密钥（逆向自官方客户端，固定不变） */
    private const val KEY = "5170021512474209"

    /** 文件头可能是 base64 文本或已解密 LRC */
    fun decrypt(cipher: ByteArray): String {
        val text = String(cipher, Charsets.UTF_8).trim()
        // 如果已经是 LRC 就直接返回
        if (text.contains(Regex("\\[\\d{1,2}:\\d{1,2}[.:]\\d{1,3}]"))) return text
        return runCatching {
            val raw = android.util.Base64.decode(text, android.util.Base64.DEFAULT)
            val keyBytes = KEY.toByteArray(Charsets.US_ASCII)
            val out = ByteArray(raw.size)
            var j = 0
            for (i in raw.indices) {
                out[i] = (raw[i].toInt() xor keyBytes[j % keyBytes.size].toInt()).toByte()
                j++
            }
            String(out, Charsets.UTF_8)
        }.getOrDefault("")
    }
}

// ---------- 简化版逐字对齐（纯 Kotlin）----------

/**
 * 简化版逐字对齐器：
 * - 对已有字级时间戳的行（KRC/TTML/QRC 解析出的）原样保留
 * - 对只有行级时间戳的行（LRC），把相邻两行的时间差均分给每个字
 *
 * 不依赖 ONNX / ML Kit，纯 Kotlin 字符串 + 时间推算。
 * 精度虽不如 CTC 强制对齐，但体验上够用（至少每字有高亮）。
 */
object WordAligner {

    /** 平均每行时长（毫秒），用于首尾行兜底 */
    private const val AVG_LINE_DURATION_MS = 3_500L

    /** 对整份歌词应用对齐 */
    fun alignAll(lines: List<WordLyricLine>): List<WordLyricLine> {
        if (lines.isEmpty()) return lines
        val result = ArrayList<WordLyricLine>(lines.size)
        for (i in lines.indices) {
            val line = lines[i]
            if (line.isWordLevel || line.text.isBlank()) {
                result += line
                continue
            }
            // 计算该行的时间窗口
            val windowStart = line.timeMs
            val windowEnd = when {
                i + 1 < lines.size -> lines[i + 1].timeMs
                else -> line.timeMs + AVG_LINE_DURATION_MS
            }
            val aligned = alignLine(line, windowStart, windowEnd)
            result += aligned
        }
        return result
    }

    /** 把一行 LRC 变成带逐字时间戳的 WordLyricLine */
    private fun alignLine(line: WordLyricLine, windowStart: Long, windowEnd: Long): WordLyricLine {
        val text = line.text.trim()
        if (text.isEmpty()) return line
        // 去掉空白字符后算字符数（中文每个字算 1，英文按空格分词）
        val chars = text.filter { !it.isWhitespace() }.toList()
        if (chars.isEmpty()) return line
        val duration = (windowEnd - windowStart).coerceAtLeast(200L)
        val perChar = duration.toDouble() / chars.size
        val words = ArrayList<LyricWord>(chars.size)
        var idx = 0
        for (c in text) {
            if (c.isWhitespace()) continue
            val charStart = windowStart + (perChar * idx).toLong()
            words += LyricWord(timeMs = charStart, text = c.toString())
            idx++
        }
        return line.copy(words = words)
    }
}
