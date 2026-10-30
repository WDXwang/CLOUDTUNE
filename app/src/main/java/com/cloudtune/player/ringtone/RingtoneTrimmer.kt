package com.cloudtune.player.ringtone

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * 音频裁剪 + 重编码为 AAC(.m4a)。
 *
 * 用 MediaExtractor 读源，MediaCodec 解码→重编码为 AAC，MediaMuxer 封装进 mp4。
 * 正确处理时间戳偏移：跳过 startUs 之前、到 endUs 停止。整段 runCatching，失败抛异常由调用方兜底。
 */
object RingtoneTrimmer {

    /**
     * 把 [srcPath] 本地文件的 [startUs, endUs] 区间重编码到 [outFile]。
     */
    fun trim(srcPath: String, startUs: Long, endUs: Long, outFile: File): Result<Unit> = runCatching {
        check(outFile.parentFile?.exists() == true) { "输出目录不存在" }

        val extractor = MediaExtractor()
        extractor.setDataSource(srcPath)

        var audioTrack = -1
        var sampleRate = 44100
        var channels = 2
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrack = i
                sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                break
            }
        }
        check(audioTrack >= 0) { "未找到音频轨" }

        val inputFormat = extractor.getTrackFormat(audioTrack)
        val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        extractor.selectTrack(audioTrack)
        if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
        val encFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val decoder = MediaCodec.createDecoderByType(inputMime)
        decoder.configure(inputFormat, null, null, 0)

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var muxTrackIndex = -1

        decoder.start()
        encoder.start()

        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()

        var decoderInputEos = false
        var decoderOutputEos = false

        while (!decoderOutputEos) {
            // 1) 喂源 → 解码器输入
            if (!decoderInputEos) {
                val inIndex = decoder.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val buf = decoder.getInputBuffer(inIndex)!!
                    buf.clear()
                    val sampleTime = extractor.sampleTime
                    if (sampleTime < 0 || sampleTime >= endUs) {
                        decoder.queueInputBuffer(
                            inIndex, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        decoderInputEos = true
                    } else {
                        val sz = extractor.readSampleData(buf, 0)
                        if (sz < 0) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            decoderInputEos = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sz, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            // 2) 解码器输出(PCM) → 编码器输入
            val outIndex = decoder.dequeueOutputBuffer(decInfo, 5000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // 解码器输出格式变化不影响 AAC 编码（编码器格式已固定）
                }
                outIndex >= 0 -> {
                    val pcms = decoder.getOutputBuffer(outIndex)!!
                    val ptsUs = decInfo.presentationTimeUs
                    val shouldDrop = ptsUs < startUs
                    decoder.releaseOutputBuffer(outIndex, false)

                    if (!shouldDrop && decInfo.size > 0) {
                        // 写入编码器
                        var encIn = encoder.dequeueInputBuffer(5000)
                        while (encIn < 0) encIn = encoder.dequeueInputBuffer(5000)
                        val ebuf = encoder.getInputBuffer(encIn)!!
                        ebuf.clear()
                        pcms.position(0)
                        val limit = decInfo.size.coerceAtMost(ebuf.capacity())
                        ebuf.put(pcms)
                        val flags =
                            if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        encoder.queueInputBuffer(encIn, 0, limit, ptsUs - startUs, flags)
                    }
                    if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decoderOutputEos = true
                    }
                }
            }

            // 3) 编码器输出(AAC) → muxer
            var encOut = encoder.dequeueOutputBuffer(encInfo, 5000)
            while (encOut >= 0 || encOut == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (encOut == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        muxTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    encOut = encoder.dequeueOutputBuffer(encInfo, 0)
                    continue
                }
                val ebuf = encoder.getOutputBuffer(encOut)!!
                if (muxerStarted && encInfo.size > 0) {
                    ebuf.position(encInfo.offset)
                    ebuf.limit(encInfo.offset + encInfo.size)
                    muxer.writeSampleData(muxTrackIndex, ebuf, encInfo)
                }
                encoder.releaseOutputBuffer(encOut, false)
                encOut = encoder.dequeueOutputBuffer(encInfo, 0)
            }
        }

        runCatching { muxer.stop() }
        muxer.release()
        runCatching { decoder.stop(); decoder.release() }
        runCatching { encoder.stop(); encoder.release() }
        runCatching { extractor.release() }
        Unit
    }
}
