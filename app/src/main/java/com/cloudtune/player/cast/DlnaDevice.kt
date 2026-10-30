package com.cloudtune.player.cast

/**
 * 一个可投屏的 DLNA MediaRenderer 设备。
 *
 * @param udn 设备唯一标识（USN 提取）
 * @param friendlyName 设备名（用于列表展示）
 * @param location 设备描述 XML 的 URL（用于取服务控制地址）
 * @param avTransportUrl AVTransport 服务控制地址（绝对 URL）
 * @param renderingControlUrl RenderingControl 服务控制地址（可选，音量用，本阶段可不用）
 */
data class DlnaDevice(
    val udn: String,
    val friendlyName: String,
    val location: String,
    val avTransportUrl: String,
    val renderingControlUrl: String? = null,
)
