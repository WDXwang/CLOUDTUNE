package com.cloudtune.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cloudtune.player.ui.theme.NovaTheme

/*
 * 播放进度条（可点击 + 可拖拽 seek）
 * 对应原型：.wave / .wave-knob（34dp 触控带、6dp 轨道、16dp 滑块）
 *
 * @param progress 0f..1f 的真实播放进度
 * @param onSeek 拖动/点击过程中持续回调（可用于实时预览或直接 seek）
 * @param onSeekFinished 手势结束时回调，接播放器 seekTo
 */

@Composable
fun NovaProgress(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onSeekFinished: (Float) -> Unit = {},
    enabled: Boolean = true,
    contentDescription: String = "播放进度"
) {
    val palette = NovaTheme.colors
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    val shown = if (dragging) dragValue else progress.coerceIn(0f, 1f)
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val f = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                dragValue = f
                                onSeek(f)
                                onSeekFinished(f)
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    dragging = true
                                    dragValue = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    onSeek(dragValue)
                                },
                                onDragEnd = {
                                    dragging = false
                                    onSeekFinished(dragValue)
                                },
                                onDragCancel = { dragging = false },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    dragValue = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    onSeek(dragValue)
                                }
                            )
                        }
                } else {
                    Modifier
                }
            )
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
            }
    ) {
        val trackH = 6.dp.toPx()
        val knobR = 8.dp.toPx()
        val centerY = size.height / 2f
        val top = centerY - trackH / 2f

        // 未播轨道
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, top),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f)
        )
        // 已播轨道
        if (shown > 0f) {
            drawRoundRect(
                color = palette.accent,
                topLeft = Offset(0f, top),
                size = Size(size.width * shown, trackH),
                cornerRadius = CornerRadius(trackH / 2f)
            )
        }
        // 滑块
        drawCircle(
            color = palette.accent,
            radius = knobR,
            center = Offset(size.width * shown, centerY)
        )
    }
}
