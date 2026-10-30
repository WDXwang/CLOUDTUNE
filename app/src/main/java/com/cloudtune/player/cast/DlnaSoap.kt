package com.cloudtune.player.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * AVTransport SOAP 调用。
 *
 * 所有调用都 POST 到设备的 avTransportUrl，SOAPAction 头带 UPnP 命名空间动作。
 * 全程异常不抛出，返回是否成功。
 */
object DlnaSoap {

    private const val AVT_SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
    private const val INSTANCE_ID = "0"

    /** 把 [mediaUrl] 投到设备并 Play。[title] 用于 DIDL 元数据展示。 */
    suspend fun play(
        device: DlnaDevice,
        mediaUrl: String,
        title: String,
        mimeType: String? = null,
    ): Boolean {
        val meta = buildDidlLite(mediaUrl, title, mimeType)
        val setOk = callAction(
            device, "SetAVTransportURI",
            mapOf(
                "InstanceID" to INSTANCE_ID,
                "CurrentURI" to mediaUrl,
                "CurrentURIMetaData" to meta,
            )
        )
        if (!setOk) return false
        return callAction(
            device, "Play",
            mapOf("InstanceID" to INSTANCE_ID, "Speed" to "1")
        )
    }

    /** 暂停。 */
    suspend fun pause(device: DlnaDevice): Boolean =
        callAction(device, "Pause", mapOf("InstanceID" to INSTANCE_ID))

    /** 恢复播放。 */
    suspend fun resume(device: DlnaDevice): Boolean =
        callAction(device, "Play", mapOf("InstanceID" to INSTANCE_ID, "Speed" to "1"))

    /** 停止并断开。 */
    suspend fun stop(device: DlnaDevice): Boolean =
        callAction(device, "Stop", mapOf("InstanceID" to INSTANCE_ID))

    private suspend fun callAction(
        device: DlnaDevice,
        action: String,
        args: Map<String, String>,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildString {
                append("<?xml version=\"1.0\"?>\n")
                append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
                append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
                append("<s:Body>")
                append("<u:$action xmlns:u=\"$AVT_SERVICE_TYPE\">")
                args.forEach { (k, v) -> append("<$k>${escapeXml(v)}</$k>") }
                append("</u:$action>")
                append("</s:Body></s:Envelope>")
            }
            val conn = (URL(device.avTransportUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPAction", "\"$AVT_SERVICE_TYPE#$action\"")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        }.getOrDefault(false)
    }

    /** 构造 DIDL-Lite 元数据（部分电视要求 CurrentURIMetaData 非空）。 */
    private fun buildDidlLite(url: String, title: String, mimeType: String?): String {
        val encUrl = URLEncoder.encode(url, "UTF-8")
        return buildString {
            append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
            append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
            append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
            append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
            append("<dc:title>${escapeXml(title)}</dc:title>")
            append("<res protocolInfo=\"http-get:*:${mimeType ?: "video/mpeg"}:*\">$url</res>")
            append("<upnp:class>object.item.videoItem</upnp:class>")
            append("</item></DIDL-Lite>")
        }.let { URLEncoder.encode(it, "UTF-8") }
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
