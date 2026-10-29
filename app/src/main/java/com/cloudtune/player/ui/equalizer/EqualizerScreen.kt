package com.cloudtune.player.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cloudtune.player.player.EqualizerController

/**
 * 均衡器页：开关、预置选择、各频段增益滑杆。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    vm: EqualizerViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("均衡器") },
                actions = {
                    TextButton(onClick = { vm.reset() }) { Text("重置") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // 开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("启用均衡器", style = MaterialTheme.typography.titleMedium)
                Switch(checked = state.enabled, onCheckedChange = { vm.setEnabled(it) })
            }

            Spacer(Modifier.height(24.dp))

            // 音效预设
            Text("音效预设", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { vm.applyPreset(EqualizerController.Presets.LIVE) },
                    label = { Text("现场氛围") },
                )
                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { vm.applyPreset(EqualizerController.Presets.POP) },
                    label = { Text("流行") },
                )
                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { vm.applyPreset(EqualizerController.Presets.ROCK) },
                    label = { Text("摇滚") },
                )
                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { vm.applyPreset(EqualizerController.Presets.VOCAL) },
                    label = { Text("人声") },
                )
                androidx.compose.material3.FilterChip(
                    selected = false,
                    onClick = { vm.applyPreset(EqualizerController.Presets.FLAT) },
                    label = { Text("平坦") },
                )
            }

            Spacer(Modifier.height(24.dp))

            // 频段滑杆（纵向列表）
            Text("频段增益", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (state.gains.isEmpty()) {
                Text(
                    "当前设备不支持均衡器，或尚未开始播放。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.gains.indices.forEach { band ->
                    BandSliderRow(
                        label = state.bandLabels.getOrElse(band) { "${band + 1}" },
                        value = state.gains[band],
                        min = state.minGain,
                        max = state.maxGain,
                        enabled = state.enabled,
                        onChange = { vm.setBandGain(band, it) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "均衡器仅对本应用播放生效，并随播放会话自动恢复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单个频段行：标签 + 滑杆 + 增益值 */
@Composable
private fun BandSliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.Center,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${if (value > 0) "+" else ""}${value / 100} dB",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.End,
        )
    }
}
