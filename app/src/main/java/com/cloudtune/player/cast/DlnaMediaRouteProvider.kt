package com.cloudtune.player.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouteDescriptor
import androidx.mediarouter.media.MediaRouteProvider
import androidx.mediarouter.media.MediaRouteProviderDescriptor
import androidx.mediarouter.media.MediaRouter

/**
 * 把 SSDP 发现的 DLNA 设备发布为 MediaRouter 路由。
 *
 * 这是一个极简自定义 MediaRouteProvider：只负责把设备列表作为 remote 路由发布出去，
 * 真正的投放/停止由 [DlnaController] 直接 SOAP 控制（不经过 MediaRouter 的 RemotePlayback），
 * 以避免对 Google Cast 通道的依赖。
 */
class DlnaMediaRouteProvider(context: Context) : MediaRouteProvider(context) {

    fun publish(devices: List<DlnaDevice>) {
        val builder = MediaRouteProviderDescriptor.Builder()
        devices.forEach { d ->
            val route = MediaRouteDescriptor.Builder(d.udn, d.friendlyName)
                .setDescription("DLNA 渲染设备")
                .setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE)
                .setDeviceType(MediaRouter.RouteInfo.DEVICE_TYPE_TV)
                .setEnabled(true)
                .build()
            builder.addRoute(route)
        }
        setDescriptor(builder.build())
    }
}
