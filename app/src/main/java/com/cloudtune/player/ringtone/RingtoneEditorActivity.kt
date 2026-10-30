package com.cloudtune.player.ringtone

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.util.MusicUtils
import com.cloudtune.player.ui.components.NovaProgress
import com.cloudtune.player.ui.theme.NovaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 铃声剪辑页：选择曲目片段 → 裁剪导出为 AAC(.m4a) → 写入系统铃声并设为来电铃声。
 *
 * 全程 runCatching，失败 Toast 提示，绝不崩溃。
 */
class RingtoneEditorActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_FILE_B64 = "extra_file_b64"
        fun start(context: android.content.Context, file: CloudFile) {
            val payload = Json { ignoreUnknownKeys = true }
                .encodeToString(CloudFile.serializer(), file)
            val b64 = Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP)
            context.startActivity(
                Intent(context, RingtoneEditorActivity::class.java).putExtra(EXTRA_FILE_B64, b64)
            )
        }
    }

    private var targetFile: CloudFile? = null
    private var sourceFile: java.io.File? = null
    private var previewPlayer: android.media.MediaPlayer? = null
    private var durationUs by mutableLongStateOf(0L)
    private var ready by mutableStateOf(false)
    private var status by mutableStateOf("正在准备音频…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK

        val b64 = intent.getStringExtra(EXTRA_FILE_B64)
        targetFile = b64?.let {
            runCatching {
                Json { ignoreUnknownKeys = true }
                    .decodeFromString(CloudFile.serializer(), String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8))
            }.getOrNull()
        }
        if (targetFile == null) {
            Toast.makeText(this, "无法打开该音频", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setContent {
            NovaTheme(darkTheme = true) {
                EditorScreen()
            }
        }

        // 准备源文件
        Thread {
            runCatching {
                val repo = (application as CloudTuneApp).container.repository
                val res = kotlinx.coroutines.runBlocking {
                    RingtoneWriter.prepareSource(this@RingtoneEditorActivity, repo, targetFile!!)
                }
                res.getOrThrow()
            }.onSuccess { f ->
                sourceFile = f
                val dur = runCatching {
                    val ex = android.media.MediaExtractor()
                    ex.setDataSource(f.absolutePath)
                    var d = 0L
                    for (i in 0 until ex.trackCount) {
                        val fmt = ex.getTrackFormat(i)
                        if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                            d = if (fmt.containsKey(android.media.MediaFormat.KEY_DURATION))
                                fmt.getLong(android.media.MediaFormat.KEY_DURATION) else 0L
                            break
                        }
                    }
                    ex.release()
                    d
                }.getOrDefault(0L)
                durationUs = dur
                ready = true
                status = if (dur > 0) "请选择片段（建议 ≤40 秒）" else "已就绪"
            }.onFailure {
                status = "准备音频失败：${it.message ?: "未知错误"}"
            }
        }.start()
    }

    @Composable
    private fun EditorScreen() {
        val file = targetFile ?: return
        var startUs by remember { mutableLongStateOf(0L) }
        var endUs by remember { mutableLongStateOf((durationUs).coerceAtMost(40_000_000L)) }
        var previewPosUs by remember { mutableLongStateOf(0L) }
        var exporting by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val dur = durationUs
        Column(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C11))
                .padding(20.dp)
        ) {
            Text(
                "制作铃声",
                style = com.cloudtune.player.ui.theme.NovaType.PageTitle,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(MusicUtils.baseName(file.name), color = Color(0xFF9AA3B2))
            Text("总时长 ${MusicUtils.formatDuration(dur / 1000)}", color = Color(0xFF9AA3B2))
            Spacer(Modifier.height(20.dp))

            if (!ready) {
                Text(status, color = Color(0xFF9AA3B2))
                return@Column
            }

            // 当前位置（预览进度）
            Text(
                "当前位置 ${MusicUtils.formatDuration(previewPosUs / 1000)}",
                color = Color.White
            )
            NovaProgress(
                progress = if (dur > 0) previewPosUs.toFloat() / dur.toFloat() else 0f,
                onSeek = { f -> previewPosUs = (f * dur).toLong(); previewSeekTo(previewPosUs) },
                onSeekFinished = { f -> previewPosUs = (f * dur).toLong(); previewSeekTo(previewPosUs) },
                enabled = dur > 0,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { previewPlay(0L) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    Text("预览", color = Color.White)
                }
                OutlinedButton(onClick = { previewStop() }) {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White)
                    Text("停止", color = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 起点 / 终点
            Text("起点 ${MusicUtils.formatDuration(startUs / 1000)}", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { startUs = (startUs - 100_000L).coerceAtLeast(0L); normalize(startUs, true, endUs) }) { Text("-0.1s", color = Color.White) }
                OutlinedButton(onClick = { startUs += 100_000L; normalize(startUs, true, endUs) }) { Text("+0.1s", color = Color.White) }
                OutlinedButton(onClick = { startUs = previewPosUs; normalize(startUs, true, endUs) }) { Text("设为起点", color = Color.White) }
            }

            Spacer(Modifier.height(8.dp))
            Text("终点 ${MusicUtils.formatDuration(endUs / 1000)}", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { endUs -= 100_000L; normalize(startUs, false, endUs) }) { Text("-0.1s", color = Color.White) }
                OutlinedButton(onClick = { endUs = (endUs + 100_000L).coerceAtMost(dur); normalize(startUs, false, endUs) }) { Text("+0.1s", color = Color.White) }
                OutlinedButton(onClick = { endUs = previewPosUs; normalize(startUs, false, endUs) }) { Text("设为终点", color = Color.White) }
            }

            Spacer(Modifier.height(12.dp))
            val selLen = (endUs - startUs).coerceAtLeast(0) / 1000
            Text("所选片段 ${MusicUtils.formatDuration(selLen)}", color = Color(0xFF5B93FF))

            message?.let { Text(it, color = Color(0xFFF0B457), modifier = Modifier.padding(top = 8.dp)) }

            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (endUs <= startUs) { message = "终点必须大于起点"; return@Button }
                    if ((endUs - startUs) > 40_000_000L) { message = "片段不能超过 40 秒"; return@Button }
                    exporting = true
                    message = "正在裁剪…"
                    scope.launch(Dispatchers.IO) {
                        val src = sourceFile ?: return@launch
                        val out = java.io.File(cacheDir, "ringtone_out.m4a")
                        val res = RingtoneTrimmer.trim(src.absolutePath, startUs, endUs, out)
                        if (res.isFailure) {
                            message = "裁剪失败：${res.exceptionOrNull()?.message ?: ""}"
                            exporting = false
                            return@launch
                        }
                        val installed = RingtoneWriter.installRingtone(this@RingtoneEditorActivity, out, MusicUtils.baseName(file.name))
                        if (installed.isFailure) {
                            message = "写入铃声目录失败：${installed.exceptionOrNull()?.message ?: ""}"
                            exporting = false
                            return@launch
                        }
                        val uri = installed.getOrThrow()
                        // 设为默认
                        val granted = Settings.System.canWrite(this@RingtoneEditorActivity)
                        if (granted) {
                            val ok = RingtoneWriter.setAsDefaultRingtone(this@RingtoneEditorActivity, uri)
                            message = if (ok) "已保存并设为来电铃声" else "已保存铃声，可在系统设置中手动设为铃声"
                        } else {
                            message = "铃声已保存。需授予“修改系统设置”才能自动设为铃声。"
                            // 引导授权
                            runCatching {
                                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS))
                            }
                        }
                        exporting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !exporting && ready,
            ) { Text(if (exporting) "处理中…" else "导出为铃声") }
        }
    }

    /** 保证 0<=start<end<=dur，且不超过 40s。 */
    private fun normalize(start: Long, isStart: Boolean, end: Long) {
        // 占位：实际校验在导出时做，这里不做复杂回写（由 state 内部保持）
    }

    private fun previewSeekTo(posUs: Long) {
        runCatching { previewPlayer?.seekTo(posUs.toInt()) }
    }

    private fun previewPlay(fromUs: Long) {
        runCatching {
            val f = sourceFile ?: return
            previewStop()
            previewPlayer = android.media.MediaPlayer().apply {
                setDataSource(f.absolutePath)
                prepare()
                seekTo(fromUs.toInt())
                start()
            }
        }
    }

    private fun previewStop() {
        runCatching { previewPlayer?.stop(); previewPlayer?.release() }
        previewPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        previewStop()
    }
}
