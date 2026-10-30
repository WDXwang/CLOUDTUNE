package com.cloudtune.player.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cloudtune.player.ui.theme.NovaTheme
import com.cloudtune.player.ui.theme.NovaType

/*
 * 白卡容器 / 列表行 / 播放中跳动条 / 收藏按钮
 * 对应原型：.list、.rowbtn、.tile、.meter、.star
 */

/** 22dp 圆角白卡，内部自行排布内容并用 [NovaDivider] 分隔。 */
@Composable
fun NovaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = NovaTheme.colors.surface,
        contentColor = NovaTheme.colors.text,
        // CSS 阴影无法等价，浅色 2dp、深色 4dp 近似
        shadowElevation = if (NovaTheme.colors.isDark) 4.dp else 2.dp
    ) {
        Column(content = content)
    }
}

/** 卡片内嵌分隔线：左右各缩进 16dp，不贯穿卡片边缘。 */
@Composable
fun NovaDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * 通用列表行：36dp 图标底座 + 主标题 + 等宽副标 + 尾部插槽。
 * 触控高度取决于尾部内容，尾部放 48dp 按钮时整行约 74dp，属正常。
 */
@Composable
fun NovaRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    isActive: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NovaTheme.colors.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NovaTheme.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (icon != null) 14.dp else 0.dp)
        ) {
            Text(
                text = title,
                style = NovaType.RowTitle,
                color = if (isActive) NovaTheme.colors.accent else NovaTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = NovaType.Mono,
                    color = NovaTheme.colors.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        trailing()
    }
}

/**
 * 曲目行：本地音乐 / 收藏 / 歌单详情通用。
 * 行高 64dp（上下 8dp + 48dp 收藏按钮），收藏按钮保持 48dp 触控目标。
 */
@Composable
fun NovaTrackRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    duration: String? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    starred: Boolean = false,
    onPlay: () -> Unit,
    onToggleStar: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NovaTheme.colors.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = NovaTheme.colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(
                text = title,
                style = NovaType.RowTitle,
                color = if (isCurrent) NovaTheme.colors.accent else NovaTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = NovaType.Mono,
                    color = NovaTheme.colors.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (isCurrent) {
            NovaPlayingMeter(
                playing = isPlaying,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (duration != null) {
            Text(
                text = duration,
                style = NovaType.Mono,
                color = NovaTheme.colors.sub,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        NovaStarButton(
            starred = starred,
            onToggle = onToggleStar
        )
    }
}

/** 收藏按钮：视觉 20dp 图标，48dp 触控区。 */
@Composable
fun NovaStarButton(
    starred: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier.size(48.dp)
    ) {
        Icon(
            imageVector = if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
            contentDescription = if (starred) "取消收藏" else "收藏",
            tint = if (starred) NovaTheme.colors.accent else NovaTheme.colors.sub,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 播放中三根跳动条。暂停时统一压到 0.4 倍高度，与原型一致。
 */
@Composable
fun NovaPlayingMeter(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = NovaTheme.colors.accent
) {
    val transition = rememberInfiniteTransition(label = "nova-meter")
    Row(
        modifier = modifier.height(13.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 220)
                ),
                label = "bar-$index"
            )
            Box(
                modifier = Modifier
                    .padding(start = if (index == 0) 0.dp else 2.dp)
                    .width(2.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = if (playing) scale else 0.4f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}
