package com.cloudtune.player.ui.video

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.core.util.MusicUtils
import com.cloudtune.player.core.util.VideoUtils
import com.cloudtune.player.player.CloudMedia
import com.cloudtune.player.player.CloudStreamResolverFactory
import com.cloudtune.player.ui.components.NovaProgress
import com.cloudtune.player.ui.theme.NovaTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 全屏视频播放 Activity。
 *
 * - 独立持有一个 [ExoPlayer]，与音乐后台播放互不影响。
 * - 云端视频复用 [CloudStreamResolverFactory]（cloudtune://track 占位 URI，播放前现取直链+请求头）；
 *   本地视频直接用 content:// URI。
 * - 倍速：[Player.setPlaybackParameters]。
 * - 字幕：外挂 srt/vtt 通过 SAF 选择，配 [MediaItem.SubtitleConfiguration]；内嵌字幕可切换/关闭。
 * - 画中画：onUserLeaveHint 进入，回前台恢复控件。
 */
class VideoActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private var targetFile: CloudFile? = null
    private val sidecarSubs = mutableListOf<MediaItem.SubtitleConfiguration>()

    var errorMessage by mutableStateOf<String?>(null)
        private set
    var inPip by mutableStateOf(false)
        private set

    /** true = 横屏全屏（沉浸式）；false = 竖屏（系统栏可见、播放栏常驻）。 */
    private var isFullscreen by mutableStateOf(false)

    /** 全屏下返回键先退出全屏，而不是直接关闭 Activity。 */
    private val fullscreenBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            changeFullscreen(false)
        }
    }

    private val dlna by lazy { (application as CloudTuneApp).container.dlnaController }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val EXTRA_FILE_B64 = "extra_file_b64"

        /** 从列表点击进入视频播放。 */
        fun start(context: Context, file: CloudFile) {
            val payload = Json { ignoreUnknownKeys = true }
                .encodeToString(CloudFile.serializer(), file)
            val b64 = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            context.startActivity(
                Intent(context, VideoActivity::class.java).putExtra(EXTRA_FILE_B64, b64)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onBackPressedDispatcher.addCallback(this, fullscreenBackCallback)
        // 默认竖屏模式：系统状态栏/导航栏可见，播放栏常驻。
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val b64 = intent.getStringExtra(EXTRA_FILE_B64)
        targetFile = b64?.let {
            runCatching {
                json.decodeFromString(
                    CloudFile.serializer(),
                    String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8)
                )
            }.getOrNull()
        }

        if (targetFile == null) {
            Toast.makeText(this, "无法打开该视频", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initPlayer()

        setContent {
            NovaTheme(darkTheme = true) {
                val exo = player
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (exo != null) {
                        PlayerSurface(exo = exo)
                        if (!inPip) {
                            ControlsOverlay(
                                exo = exo,
                                error = errorMessage,
                                onPickSubtitle = { pickSubtitleLauncher.launch(arrayOf("*/*")) },
                                onEnterPip = { enterPip() },
                                onClose = { finish() },
                                isFullscreen = isFullscreen,
                                onToggleFullscreen = { changeFullscreen(!isFullscreen) },
                            )
                        }
                    } else {
                        Text(
                            errorMessage ?: "初始化播放器…",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }

    /** SAF 选择外挂字幕文件后回调：识别 mimeType 并追加到 sidecar。 */
    private val pickSubtitleLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            val name = queryDisplayName(uri) ?: "subtitle.srt"
            val mime = VideoUtils.subtitleMime(name)
            if (mime == null) {
                Toast.makeText(this, "不支持的字幕格式（仅 srt/vtt）", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            sidecarSubs.add(
                MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(mime)
                    .setLanguage("zh")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            )
            reloadWithSubtitles()
            Toast.makeText(this, "已加载字幕：$name", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "字幕加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun initPlayer() {
        val file = targetFile ?: return
        runCatching {
            val container = (application as CloudTuneApp).container
            val dsFactory = CloudStreamResolverFactory.buildDataSourceFactory(this, container.repository)
            val exo = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
                .build()
                .apply {
                    playWhenReady = true
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                            errorMessage = "播放失败：${error.errorCodeName}"
                        }
                    })
                    setMediaItem(buildMediaItem(file))
                    prepare()
                }
            player = exo
        }.onFailure {
            errorMessage = "初始化播放器失败：${it.message ?: "未知错误"}"
        }
    }

    private fun buildMediaItem(file: CloudFile): MediaItem {
        val base = if (file.isLocal) {
            MediaItem.Builder()
                .setMediaId(file.id)
                .setUri(Uri.parse(file.id))
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(MusicUtils.baseName(file.name)).build()
                )
                .build()
        } else {
            CloudMedia.mediaItem(file)
        }
        if (sidecarSubs.isEmpty()) return base
        return base.buildUpon().setSubtitleConfigurations(sidecarSubs.toList()).build()
    }

    private fun reloadWithSubtitles() {
        val exo = player ?: return
        val file = targetFile ?: return
        runCatching {
            val pos = exo.currentPosition
            exo.setMediaItem(buildMediaItem(file))
            exo.prepare()
            exo.seekTo(pos)
        }
    }

    /**
     * 切换 竖屏模式 ↔ 横屏全屏模式。
     * 全屏：锁横屏 + 隐藏系统栏（沉浸式）；竖屏：锁竖屏 + 恢复系统栏。
     */
    private fun changeFullscreen(fullscreen: Boolean) {
        if (isFullscreen == fullscreen) return
        isFullscreen = fullscreen
        fullscreenBackCallback.isEnabled = fullscreen
        requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        applySystemBars(fullscreen)
    }

    /** 按模式显示/隐藏系统栏；不重建 Activity（Manifest 已声明 configChanges）。 */
    private fun applySystemBars(fullscreen: Boolean) {
        runCatching {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (fullscreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 旋转后部分机型会把系统栏重新显示出来，转屏后再校正一次。
        window.decorView.post { applySystemBars(isFullscreen) }
    }

    override fun onResume() {
        super.onResume()
        if (!inPip) applySystemBars(isFullscreen)
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val exo = player ?: return
        runCatching {
            if (!exo.isPlaying) return
            val builder = PictureInPictureParams.Builder()
            val vw = exo.videoSize.width
            val vh = exo.videoSize.height
            if (vw > 0 && vh > 0) builder.setAspectRatio(Rational(vw, vh))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(true)
            enterPictureInPictureMode(builder.build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        // 画中画里不显示任何覆盖控件；退出画中画后按当前模式恢复系统栏。
        if (!isInPictureInPictureMode) applySystemBars(isFullscreen)
    }

    override fun onStop() {
        super.onStop()
        if (!inPip) player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { player?.release() }
        player = null
    }

    // ---------------- Compose 视图 ----------------

    @Composable
    private fun PlayerSurface(exo: ExoPlayer) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // 释放：当 player 实例变化（一般不会）时由 Activity onDestroy 统一 release
        DisposableEffect(exo) { onDispose { } }
    }

    private val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    @Composable
    private fun ControlsOverlay(
        exo: ExoPlayer,
        error: String?,
        onPickSubtitle: () -> Unit,
        onEnterPip: () -> Unit,
        onClose: () -> Unit,
        isFullscreen: Boolean,
        onToggleFullscreen: () -> Unit,
    ) {
        var positionMs by remember { mutableLongStateOf(0L) }
        var durationMs by remember { mutableLongStateOf(0L) }
        var isPlaying by remember { mutableStateOf(exo.isPlaying) }
        var speed by remember { mutableStateOf(1.0f) }
        var speedMenu by remember { mutableStateOf(false) }
        var subMenu by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var dragFraction by remember { mutableFloatStateOf(0f) }
        var castPanel by remember { mutableStateOf(false) }

        // 播放栏可见性 + 自动隐藏计时令牌：每次交互自增以重置 5 秒计时
        var controlsVisible by remember { mutableStateOf(true) }
        var autoHideToken by remember { mutableIntStateOf(0) }

        /** 记录一次用户操作：保持播放栏可见并重置自动隐藏计时。 */
        fun markInteraction() {
            autoHideToken++
            controlsVisible = true
        }

        val devices by dlna.devices.collectAsStateWithLifecycle()
        val discovering by dlna.discovering.collectAsStateWithLifecycle()
        val casting by dlna.castingDevice.collectAsStateWithLifecycle()

        // 打开投屏面板时触发一轮 SSDP 发现
        LaunchedEffect(castPanel) {
            if (castPanel) dlna.startDiscovery()
        }

        // 轮询进度
        LaunchedEffect(exo) {
            while (true) {
                positionMs = exo.currentPosition
                durationMs = exo.duration.coerceAtLeast(0L)
                isPlaying = exo.isPlaying
                delay(500)
            }
        }

        // 全屏：5 秒无操作自动隐藏播放栏；竖屏：播放栏常驻
        LaunchedEffect(autoHideToken, isFullscreen) {
            if (!isFullscreen) {
                controlsVisible = true
                return@LaunchedEffect
            }
            delay(5000)
            if (!dragging && !speedMenu && !subMenu && !castPanel) controlsVisible = false
        }

        val progress = if (dragging) dragFraction
        else if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        else 0f

        Box(Modifier.fillMaxSize()) {
            // 画面点击层：位于播放栏之下，点画面中央切换播放栏显隐（不会挡住控件）
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            if (isFullscreen) {
                                controlsVisible = !controlsVisible
                                if (controlsVisible) autoHideToken++
                            }
                        }
                    }
            ) {
                // 仅作为画面点击区域，不含内容
            }

            // 顶部返回：全屏时退出全屏，竖屏时关闭页面（随播放栏一起显隐）
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0x66000000))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        markInteraction()
                        if (isFullscreen) onToggleFullscreen() else onClose()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                }
            }

            // 底部播放栏
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .pointerInput(Unit) { detectTapGestures { markInteraction() } }
                        .padding(16.dp)
                ) {
                    error?.let {
                        Text(it, color = Color(0xFFFF6B6B), modifier = Modifier.padding(bottom = 8.dp))
                    }
                    // 进度条 + 时间 + 全屏切换
                    NovaProgress(
                        progress = progress,
                        onSeek = { dragFraction = it; dragging = true },
                        onSeekFinished = { f ->
                            if (durationMs > 0) exo.seekTo((f * durationMs).toLong())
                            dragging = false
                            markInteraction()
                        },
                        enabled = durationMs > 0L,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            MusicUtils.formatDuration(if (dragging) (dragFraction * durationMs).toLong() else positionMs),
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(MusicUtils.formatDuration(durationMs), color = Color.White)
                            // 竖屏 / 横屏全屏切换
                            IconButton(onClick = {
                                markInteraction()
                                onToggleFullscreen()
                            }) {
                                Icon(
                                    if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = if (isFullscreen) "退出全屏" else "全屏播放",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 控制行
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            markInteraction()
                            exo.seekTo(exo.currentPosition - 10000)
                        }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "后退10秒", tint = Color.White)
                        }
                        IconButton(onClick = {
                            markInteraction()
                            if (exo.isPlaying) exo.pause() else exo.play()
                        }) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "播放/暂停",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        IconButton(onClick = {
                            markInteraction()
                            exo.seekTo(exo.currentPosition + 10000)
                        }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "快进10秒", tint = Color.White)
                        }

                        // 倍速
                        Box {
                            IconButton(onClick = {
                                markInteraction()
                                speedMenu = true
                            }) {
                                Icon(Icons.Filled.Speed, contentDescription = "倍速", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = speedMenu,
                                onDismissRequest = { speedMenu = false; markInteraction() }
                            ) {
                                speeds.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(if (s == 1f) "正常 1.0x" else "${s}x") },
                                        onClick = {
                                            speed = s
                                            exo.playbackParameters = PlaybackParameters(s)
                                            speedMenu = false
                                            markInteraction()
                                        }
                                    )
                                }
                            }
                        }

                        // 字幕
                        Box {
                            IconButton(onClick = {
                                markInteraction()
                                subMenu = true
                            }) {
                                Icon(Icons.Filled.ClosedCaption, contentDescription = "字幕", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = subMenu,
                                onDismissRequest = { subMenu = false; markInteraction() }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("关闭字幕") },
                                    onClick = {
                                        applySubtitleSelection(off = true, groupIndex = -1)
                                        subMenu = false
                                        markInteraction()
                                    }
                                )
                                // 内嵌/外挂文本轨
                                val textGroups = remember(exo.currentTracks) { collectTextGroupCount(exo) }
                                for (i in 0 until textGroups) {
                                    DropdownMenuItem(
                                        text = { Text("字幕轨道 ${i + 1}") },
                                        onClick = {
                                            applySubtitleSelection(off = false, groupIndex = i)
                                            subMenu = false
                                            markInteraction()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("选择外挂字幕文件…") },
                                    onClick = { subMenu = false; markInteraction(); onPickSubtitle() }
                                )
                            }
                        }

                        // 投屏
                        IconButton(onClick = {
                            markInteraction()
                            castPanel = true
                        }) {
                            Icon(
                                Icons.Filled.Tv,
                                contentDescription = "投屏",
                                tint = if (casting != null) Color(0xFF5B93FF) else Color.White
                            )
                        }

                        // PiP
                        IconButton(onClick = {
                            markInteraction()
                            onEnterPip()
                        }) {
                            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "画中画", tint = Color.White)
                        }
                    }
                }
            }
        }

        // 投屏设备选择对话框
        if (castPanel) {
            AlertDialog(
                onDismissRequest = { castPanel = false; markInteraction() },
                confirmButton = {},
                title = { Text(if (casting != null) "正在投屏：${casting!!.friendlyName}" else "投屏到设备") },
                text = {
                    Column {
                        if (discovering) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LinearProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("正在搜索同局域网设备…", color = Color.White)
                            }
                        } else if (devices.isEmpty()) {
                            Text("未发现设备。请确认电视与手机在同一 Wi-Fi，且电视已开启 DLNA/投屏。", color = Color.White)
                        } else {
                            devices.forEach { d ->
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        val file = targetFile ?: return@TextButton
                                        lifecycleScope.launch {
                                            val ok = dlna.castTo(d, file)
                                            if (ok) {
                                                exo.pause()
                                                Toast.makeText(this@VideoActivity, "已投到 ${d.friendlyName}", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(this@VideoActivity, "投屏失败，请重试", Toast.LENGTH_SHORT).show()
                                            }
                                            castPanel = false
                                        }
                                    }
                                ) { Text(d.friendlyName, color = Color.White) }
                            }
                        }
                        if (casting != null) {
                            androidx.compose.material3.TextButton(onClick = {
                                lifecycleScope.launch {
                                    dlna.stop()
                                    Toast.makeText(this@VideoActivity, "已断开投屏", Toast.LENGTH_SHORT).show()
                                    castPanel = false
                                }
                            }) { Text("断开投屏并回到本机", color = Color(0xFFFF6B6B)) }
                        }
                    }
                }
            )
        }
    }

    /** 统计当前可用文本（字幕）轨道组数。 */
    private fun collectTextGroupCount(exo: ExoPlayer): Int {
        return runCatching {
            val tracks = exo.currentTracks
            var n = 0
            for (i in 0 until tracks.groups.size) {
                if (tracks.groups[i].type == C.TRACK_TYPE_TEXT) n++
            }
            n
        }.getOrDefault(0)
    }

    /** off=true 关闭字幕；否则启用并选中第 groupIndex 个文本轨。 */
    private fun applySubtitleSelection(off: Boolean, groupIndex: Int) {
        val exo = player ?: return
        runCatching {
            val builder = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, off)
            if (!off && groupIndex >= 0) {
                val tracks = exo.currentTracks
                var seen = -1
                for (i in 0 until tracks.groups.size) {
                    if (tracks.groups[i].type == C.TRACK_TYPE_TEXT) {
                        seen++
                        if (seen == groupIndex) {
                            builder.addOverride(TrackSelectionOverride(tracks.groups[i].mediaTrackGroup, 0))
                            break
                        }
                    }
                }
            }
            exo.trackSelectionParameters = builder.build()
        }
    }
}
