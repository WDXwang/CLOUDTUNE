package com.cloudtune.player.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cloudtune.player.ui.theme.NovaTheme

/*
 * One UI 拨杆开关
 * 对应原型：.switch（50×30 轨道、22dp 滑块、选中态 accentSoft 无描边）
 *
 * 不使用 Material3 的 Switch：它的轨道/滑块比例与 One UI 不一致，
 * 且选中态是实心 primary，原型是 10%~16% 透明度的 accentSoft。
 */

@Composable
fun NovaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val palette = NovaTheme.colors
    val shape = RoundedCornerShape(15.dp)

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "switch-thumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.accentSoft else palette.surface2,
        label = "switch-track"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) palette.accent else palette.text.copy(alpha = 0.35f),
        label = "switch-thumb-color"
    )

    Box(
        modifier = modifier
            .size(width = 50.dp, height = 30.dp)
            .clip(shape)
            .background(trackColor)
            // 选中时去掉描边，未选中保留 1dp 细线
            .then(if (checked) Modifier else Modifier.border(1.dp, palette.lineStrong, shape))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            )
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}
