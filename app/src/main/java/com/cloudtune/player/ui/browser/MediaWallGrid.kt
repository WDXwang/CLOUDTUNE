package com.cloudtune.player.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.util.MusicUtils
import kotlin.math.abs

/**
 * 「电影墙」网格展示：视频文件以海报卡片（2:3）呈现，文件夹 / 音乐以紧凑卡片混排。
 *
 * 云端接口不提供视频缩略图，因此海报用「按文件名散列取渐变色 + 影片图标」生成，
 * 同一文件每次颜色一致，视觉上形成可辨认的封面墙。
 *
 * @param files 已过滤后的可见条目
 * @param onFolderClick 点击文件夹
 * @param onVideoClick 点击视频（进入视频播放）
 * @param onMusicClick 点击音乐（带该音乐在可见音乐中的下标）
 */
@Composable
fun MediaWallGrid(
    files: List<CloudFile>,
    onFolderClick: (CloudFile) -> Unit,
    onVideoClick: (CloudFile) -> Unit,
    onMusicClick: (musicIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            items = files,
            key = { _, f -> "${f.accountId ?: "local"}:${f.id}" },
        ) { index, file ->
            when {
                file.isDir -> FolderTile(file = file, onClick = { onFolderClick(file) })
                file.isVideo() -> VideoPosterCard(file = file, onClick = { onVideoClick(file) })
                file.isMusic() -> {
                    val musicIdx = files.subList(0, index + 1).count { it.isMusic() } - 1
                    MusicTile(file = file, onClick = { onMusicClick(musicIdx) })
                }
            }
        }
    }
}

/** 视频海报卡片：渐变封面 + 影片图标 + 播放按钮 + 底部标题 */
@Composable
private fun VideoPosterCard(file: CloudFile, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(Brush.linearGradient(posterGradient(file.name))),
        ) {
            // 中央影片水印图标
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(56.dp)
                    .align(Alignment.Center),
            )
            // 中央播放按钮
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "播放",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(44.dp)
                    .align(Alignment.Center),
            )
            // 底部渐变遮罩 + 标题 / 大小
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    MusicUtils.baseName(file.name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val size = MusicUtils.formatSize(file.size)
                if (size.isNotEmpty()) {
                    Text(
                        size,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

/** 文件夹紧凑卡片 */
@Composable
private fun FolderTile(file: CloudFile, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                file.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 音乐紧凑卡片 */
@Composable
private fun MusicTile(file: CloudFile, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                MusicUtils.baseName(file.name),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 海报渐变色调色板：按文件名散列取值，保证同一文件颜色稳定。 */
private val POSTER_GRADIENTS: List<List<Color>> = listOf(
    listOf(Color(0xFF0F2027), Color(0xFF2C5364)),
    listOf(Color(0xFF42275A), Color(0xFF734B6D)),
    listOf(Color(0xFF134E5E), Color(0xFF71B280)),
    listOf(Color(0xFF232526), Color(0xFF414345)),
    listOf(Color(0xFF603813), Color(0xFFB29F94)),
    listOf(Color(0xFF1A2980), Color(0xFF26D0CE)),
    listOf(Color(0xFF5614B0), Color(0xFF9D50BB)),
    listOf(Color(0xFF870000), Color(0xFF3A1C1C)),
    listOf(Color(0xFF00223E), Color(0xFF0E5A77)),
    listOf(Color(0xFF360033), Color(0xFF0B8793)),
)

/** 按文件名散列选取渐变，保证同一文件每次颜色一致。 */
private fun posterGradient(name: String): List<Color> =
    POSTER_GRADIENTS[abs(name.hashCode()) % POSTER_GRADIENTS.size]
