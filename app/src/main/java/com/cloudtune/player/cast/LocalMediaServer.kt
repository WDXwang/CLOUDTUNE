package com.cloudtune.player.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * 极简局域网 HTTP/1.1 服务：把手机本地 content:// 文件通过 http://<手机IP>:<port>/ 暴露给电视。
 *
 * 电视无法访问 content://，因此投屏本地文件时必须由本服务对外提供可下载直链。
 * - 单进程单连接循环，足够投屏单路播放。
 * - 支持简单 Range（bytes=start-），满足多数电视拖动/起播；不支持时退化为整文件。
 * - 失败不抛出，启停由 [DlnaController] 统一管理。
 */
class LocalMediaServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null
    private var targetUri: Uri? = null
    private var port: Int = 0

    /** 启动服务并登记要分享的文件，返回可供电视访问的 http URL；失败返回 null。 */
    @Synchronized
    fun start(uri: Uri): String? {
        stop()
        return runCatching {
            targetUri = uri
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress("0.0.0.0", 0))
            serverSocket = ss
            port = ss.localPort
            thread = Thread({ acceptLoop() }, "cast-http-server").apply { isDaemon = true; start() }
            val ip = getLocalIp() ?: return null
            "http://$ip:$port/v"
        }.getOrNull()
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        thread = null
        targetUri = null
        port = 0
    }

    private fun acceptLoop() {
        while (true) {
            val ss = serverSocket ?: break
            val client = runCatching { ss.accept() }.getOrNull() ?: break
            runCatching { handle(client) }
            runCatching { client.close() }
        }
    }

    private fun handle(socket: Socket) {
        val uri = targetUri ?: return
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val requestLine = reader.readLine() ?: return
        // 读掉请求头（本服务不关心）
        var rangeHeader: String? = null
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) rangeHeader = line.substringAfter(':').trim()
        }

        val resolver = context.contentResolver
        val type = resolver.getType(uri) ?: "video/*"
        val total = runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        }.getOrDefault(-1L)

        socket.getOutputStream().use { out ->
            var start = 0L
            var end = if (total > 0) total - 1 else -1L
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val range = rangeHeader.removePrefix("bytes=")
                val parts = range.split("-")
                start = parts[0].toLongOrNull() ?: 0L
                if (parts.size > 1 && parts[1].isNotEmpty()) end = parts[1].toLongOrNull() ?: end
            }
            val partial = rangeHeader != null && total > 0
            val code = if (partial) "206 Partial Content" else "200 OK"
            out.write(("HTTP/1.1 $code\r\n").toByteArray())
            out.write(("Content-Type: $type\r\n").toByteArray())
            out.write(("Accept-Ranges: bytes\r\n").toByteArray())
            if (total > 0) {
                out.write(("Content-Length: ${(end - start + 1).coerceAtLeast(0)}\r\n").toByteArray())
                if (partial) out.write(("Content-Range: bytes $start-$end/$total\r\n").toByteArray())
            }
            out.write(("Access-Control-Allow-Origin: *\r\n").toByteArray())
            out.write(("\r\n").toByteArray())
            out.flush()

            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fd = pfd.fileDescriptor
                val fis = java.io.FileInputStream(fd)
                fis.channel.use { chan ->
                    if (start > 0) chan.position(start)
                    val buf = ByteArray(64 * 1024)
                    var remaining = if (end >= start) end - start + 1 else Long.MAX_VALUE
                    while (remaining > 0) {
                        val n = chan.read(java.nio.ByteBuffer.wrap(buf))
                        if (n < 0) break
                        out.write(buf, 0, n)
                        remaining -= n
                        if (remaining <= 0) break
                    }
                    out.flush()
                }
            }
        }
    }

    private fun getLocalIp(): String? = runCatching {
        val nics = java.net.NetworkInterface.getNetworkInterfaces()
        for (nic in nics) {
            for (addr in nic.inetAddresses) {
                if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) {
                    return@runCatching addr.hostAddress
                }
            }
        }
        null
    }.getOrNull()
}
