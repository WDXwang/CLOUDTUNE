package com.cloudtune.player.cast

import android.content.Context
import android.net.wifi.WifiManager
import com.cloudtune.player.core.model.CloudFile
import com.cloudtune.player.data.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DLNA 投屏控制器（单例，存于 AppContainer，跨页面存活）。
 *
 * 职责：
 * - SSDP 发现 MediaRenderer，维护设备列表与“扫描中”状态；
 * - 把当前媒体（云端/本地）转成电视可访问的 http URL 后 SOAP 投放；
 * - 本地文件走 [LocalMediaServer] 暴露局域网直链；
 * - 断开/停止，回收组播锁与本地 HTTP 服务。
 *
 * 所有失败都收敛为状态/布尔结果，不抛给 UI。
 */
class DlnaController(
    private val appContext: Context,
    private val repository: MusicRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _devices = MutableStateFlow<List<DlnaDevice>>(emptyList())
    val devices: StateFlow<List<DlnaDevice>> = _devices.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    private val _castingDevice = MutableStateFlow<DlnaDevice?>(null)
    val castingDevice: StateFlow<DlnaDevice?> = _castingDevice.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private val localServer = LocalMediaServer(appContext)
    private val routeProvider: DlnaMediaRouteProvider = DlnaMediaRouteProvider(appContext).also { p ->
        runCatching {
            androidx.mediarouter.media.MediaRouter.getInstance(appContext)
                .addProvider(p)
        }
    }

    /** 发起一轮 SSDP 发现。 */
    fun startDiscovery() {
        if (_discovering.value) return
        _discovering.value = true
        ensureMulticastLock()
        scope.launch {
            val found = runCatching { SsdpDiscovery.discover() }.getOrDefault(emptyList())
            _devices.value = found
            runCatching { routeProvider.publish(found) }
            _discovering.value = false
            releaseMulticastLock()
        }
    }

    private fun ensureMulticastLock() {
        runCatching {
            if (multicastLock != null) return@runCatching
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("cloudtune-dlna").apply {
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            multicastLock?.takeIf { it.isHeld }?.release()
            multicastLock = null
        }
    }

    /**
     * 把文件投到指定设备。成功后 [castingDevice] 更新。
     * @return 是否成功发起投放。
     */
    suspend fun castTo(device: DlnaDevice, file: CloudFile): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // 1) 解析出电视可访问的 http URL
            val mediaUrl = if (file.isLocal) {
                localServer.start(android.net.Uri.parse(file.id)) ?: return@withContext false
            } else {
                val src = repository.resolveStream(file)
                src.url
            }
            // 2) SOAP SetAVTransportURI + Play
            val ok = DlnaSoap.play(
                device = device,
                mediaUrl = mediaUrl,
                title = com.cloudtune.player.core.util.MusicUtils.baseName(file.name),
                mimeType = "video/*",
            )
            if (ok) _castingDevice.value = device
            ok
        }.getOrDefault(false)
    }

    /** 暂停投屏。 */
    suspend fun pause(): Boolean {
        val d = _castingDevice.value ?: return false
        return DlnaSoap.pause(d)
    }

    /** 停止投屏并断开。 */
    suspend fun stop() {
        val d = _castingDevice.value ?: return
        runCatching { DlnaSoap.stop(d) }
        _castingDevice.value = null
        localServer.stop()
    }

    /** 释放全部资源（应用销毁/退出登录时调用）。 */
    fun release() {
        scope.launch { runCatching { stop() } }
        localServer.stop()
        releaseMulticastLock()
    }
}
