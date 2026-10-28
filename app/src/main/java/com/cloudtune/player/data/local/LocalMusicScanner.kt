package com.cloudtune.player.data.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.util.MusicUtils

/**
 * 本地音乐扫描器：通过 [MediaStore.Audio] 查询设备上的音频文件，
 * 统一包装成 [CloudFile]（isLocal=true，id 为 content:// URI）。
 *
 * 调用方需先取得音频读取权限（READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE）。
 */
class LocalMusicScanner(private val context: Context) {

    /** 扫描全部本地音乐（排除铃声、通知音、闹钟），按标题排序。 */
    fun scan(): List<CloudFile> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )

        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 0"

        val out = ArrayList<CloudFile>()
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                val displayName = c.getString(nameCol) ?: continue
                // 再按扩展名兜底过滤，确保是音乐
                if (!MusicUtils.isMusicFile(displayName)) continue

                out.add(
                    CloudFile(
                        id = contentUri.toString(),
                        name = displayName,
                        path = c.getString(pathCol) ?: "/",
                        isDir = false,
                        size = c.getLong(sizeCol),
                        mimeType = null,
                        isLocal = true,
                    )
                )
            }
        }
        return out
    }
}
