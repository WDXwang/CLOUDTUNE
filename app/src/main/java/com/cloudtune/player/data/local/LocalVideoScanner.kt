package com.cloudtune.player.data.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.util.VideoUtils

/**
 * 本地视频扫描器：通过 [MediaStore.Video] 查询设备上的视频文件，
 * 统一包装成 [CloudFile]（isLocal=true，id 为 content:// URI）。
 *
 * 调用方需先取得视频读取权限（READ_MEDIA_VIDEO / READ_EXTERNAL_STORAGE）。
 * 任何异常（如权限被拒）都返回空列表，绝不抛出。
 */
class LocalVideoScanner(private val context: Context) {

    /** 扫描全部本地视频，按显示名排序。失败返回空列表。 */
    fun scan(): List<CloudFile> = runCatching {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.RELATIVE_PATH,
        )

        val out = ArrayList<CloudFile>()
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Video.Media.SIZE} > 0",
            null,
            "${MediaStore.Video.Media.DISPLAY_NAME} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val displayName = c.getString(nameCol) ?: continue
                if (!VideoUtils.isVideoFile(displayName)) continue
                val contentUri = ContentUris.withAppendedId(collection, id)
                out.add(
                    CloudFile(
                        id = contentUri.toString(),
                        name = displayName,
                        path = c.getString(pathCol) ?: "/",
                        isDir = false,
                        size = c.getLong(sizeCol),
                        mimeType = "video/*",
                        isLocal = true,
                    )
                )
            }
        }
        out
    }.getOrDefault(emptyList())
}
