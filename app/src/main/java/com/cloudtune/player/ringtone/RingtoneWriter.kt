package com.cloudtune.player.ringtone

import android.content.ContentValues
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 铃声导出与安装。
 *
 * - prepareSource：把待裁剪素材落地为本地缓存文件（云端先 resolveStream 再下载；本地直接 contentResolver 拷出）。
 * - installRingtone：按版本写入系统铃声目录并登记 MediaStore，返回可播放 Uri。
 * - setAsDefault：经 WRITE_SETTINGS 设为来电铃声。
 * 全部失败以 Result 抛出，由 UI 兜底。
 */
object RingtoneWriter {

    /** 把源素材准备成 MediaExtractor 可读的本地文件。 */
    suspend fun prepareSource(
        context: Context,
        repo: MusicRepository,
        file: CloudFile,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val cache = File(context.cacheDir, "ring_src_" + file.id.hashCode() + ".src")
            if (file.isLocal) {
                context.contentResolver.openFileDescriptor(Uri.parse(file.id), "r")?.use { pfd ->
                    pfd.fileDescriptor.let { fd ->
                        java.io.FileInputStream(fd).use { it.copyTo(cache.outputStream()) }
                    }
                } ?: error("无法打开本地文件")
            } else {
                val src = repo.resolveStream(file)
                val conn = (URL(src.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    src.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
                conn.inputStream.use { it.copyTo(cache.outputStream()) }
                conn.disconnect()
            }
            cache
        }
    }

    /** 把裁剪好的 m4a 文件写入系统铃声库，返回登记后的 Uri。 */
    suspend fun installRingtone(
        context: Context,
        m4a: File,
        displayName: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val name = "$displayName.m4a"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4a-latm")
                    put(MediaStore.Audio.Media.IS_RINGTONE, 1)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RINGTONES)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values
                ) ?: error("MediaStore 插入失败")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    m4a.inputStream().use { it.copyTo(out) }
                } ?: error("无法打开输出流")
                uri
            } else {
                // API 26-28：写入公共 Ringtones 目录，再扫描登记
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
                if (!dir.exists()) dir.mkdirs()
                val dst = File(dir, name)
                m4a.inputStream().use { it.copyTo(dst.outputStream()) }
                // MediaScanner 登记
                val scanPath = dst.absolutePath
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(scanPath), arrayOf("audio/mp4a-latm"), null
                )
                // 查询登记后的 Uri
                val proj = arrayOf(MediaStore.Audio.Media._ID)
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                    "${MediaStore.Audio.Media.DATA}=?", arrayOf(scanPath), null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        return@runCatching ContentUrisCompat.withId(c.getLong(0))
                    }
                }
                Uri.fromFile(dst)
            }
        }
    }

    /** 尝试设为默认来电铃声；需 WRITE_SETTINGS。返回是否成功。 */
    fun setAsDefaultRingtone(context: Context, uri: Uri): Boolean = runCatching {
        RingtoneManager.setActualDefaultRingtoneUri(
            context, RingtoneManager.TYPE_RINGTONE, uri
        )
        true
    }.getOrDefault(false)
}

/** 兼容 helper：由 id 构造 MediaStore content Uri。 */
private object ContentUrisCompat {
    fun withId(id: Long): Uri =
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon()
            .appendPath(id.toString()).build()
}
