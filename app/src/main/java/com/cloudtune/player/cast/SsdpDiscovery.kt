package com.cloudtune.player.cast

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.URL

/**
 * SSDP 发现 + 设备描述 XML 解析。
 *
 * 纯自实现：UDP 多播 M-SEARCH 239.255.255.250:1900，目标 ST = MediaRenderer:1；
 * 收到响应后取 LOCATION，HTTP GET 设备描述 XML，解析 friendlyName 与 AVTransport 控制地址。
 * 不引入 Cling，避免 jetty/javax 依赖冲突。
 */
object SsdpDiscovery {

    private const val MULTICAST_ADDR = "239.255.255.250"
    private const val MULTICAST_PORT = 1900
    private const val ST_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"

    /** 一轮发现，返回去重后的设备列表（最多等待 [timeoutMs]）。全程异常不抛出。 */
    suspend fun discover(timeoutMs: Int = 4000): List<DlnaDevice> = withContext(Dispatchers.IO) {
        val locations = LinkedHashMap<String, String>() // location -> usn
        runCatching {
            val msg = buildString {
                append("M-SEARCH * HTTP/1.1\r\n")
                append("HOST: $MULTICAST_ADDR:$MULTICAST_PORT\r\n")
                append("MAN: \"ssdp:discover\"\r\n")
                append("ST: $ST_TARGET\r\n")
                append("MX: 2\r\n")
                append("\r\n")
            }
            val addr = InetAddress.getByName(MULTICAST_ADDR)
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val data = msg.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(data, data.size, addr, MULTICAST_PORT)
                // 连发两次，提高发现率
                socket.send(packet)
                socket.send(packet)

                val buf = ByteArray(4096)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val resp = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(resp)
                    } catch (_: Exception) {
                        break
                    }
                    val text = String(buf, 0, resp.length, Charsets.UTF_8)
                    val loc = headerValue(text, "LOCATION") ?: continue
                    val usn = headerValue(text, "USN") ?: loc
                    locations[loc] = usn
                }
            }
        }
        // 逐个取设备描述 XML
        locations.keys.mapNotNull { loc -> fetchDevice(loc) }
    }

    private fun headerValue(raw: String, name: String): String? =
        raw.lineSequence()
            .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()

    /** GET 设备描述 XML，解析 friendlyName + AVTransport 控制 URL。 */
    private fun fetchDevice(location: String): DlnaDevice? = runCatching {
        val conn = (URL(location).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 4000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CloudTune")
        }
        conn.inputStream.use { stream ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, "UTF-8")
            parseDeviceXml(parser, location)
        }
    }.getOrNull()

    private fun parseDeviceXml(parser: XmlPullParser, location: String): DlnaDevice? {
        var friendlyName = ""
        var udn = ""
        var avControlUrl: String? = null
        var rcControlUrl: String? = null

        // 服务扫描需要跨 service 节点记录 serviceId / controlURL
        var currentServiceId = ""
        var currentControlUrl = ""
        var inService = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "friendlyName" -> friendlyName = parser.nextText()
                    "UDN" -> udn = parser.nextText()
                    "service" -> { inService = true; currentServiceId = ""; currentControlUrl = "" }
                    "serviceId" -> if (inService) currentServiceId = parser.nextText()
                    "controlURL" -> if (inService) currentControlUrl = parser.nextText()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "service" -> {
                        if (currentServiceId.contains("AVTransport", ignoreCase = true)) {
                            avControlUrl = resolveUrl(location, currentControlUrl)
                        } else if (currentServiceId.contains("RenderingControl", ignoreCase = true)) {
                            rcControlUrl = resolveUrl(location, currentControlUrl)
                        }
                        inService = false
                    }
                }
            }
            event = parser.next()
        }

        if (avControlUrl == null) return null
        return DlnaDevice(
            udn = udn.ifEmpty { location },
            friendlyName = friendlyName.ifEmpty { location },
            location = location,
            avTransportUrl = avControlUrl,
            renderingControlUrl = rcControlUrl,
        )
    }

    /** 把描述 XML 里的相对 controlURL 解析为绝对 URL。 */
    private fun resolveUrl(base: String, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        return URL(URL(base), path).toString()
    }
}
