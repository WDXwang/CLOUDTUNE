package com.cloudtune.player.data.tianyi

import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 天翼云盘 token 会话（对应 OpenList 189pc 的 AppSessionResp / UserSessionResp）。
 *
 * 与 Cookie 不同，token 会话通过「HMAC-SHA1 签名 + 明文查询参数」调用
 * api.cloud.189.cn 接口，**不绑定登录 IP**——切换网络后会话依然有效。
 */
data class TianyiSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val sessionKey: String = "",
    val sessionSecret: String = "",
) {
    /** 是否具备可用的签名会话（必须同时有 SessionKey 与 SessionSecret） */
    val valid: Boolean get() = sessionKey.isNotEmpty() && sessionSecret.isNotEmpty()
}

/**
 * OpenList 189pc 的签名认证原语（对应 help.go / utils.go 的 clientSuffix、
 * signatureOfHmac、getHttpDateStr、deviceParams）。
 */
object TianyiAuth {
    private const val PC = "TELEPC"
    private const val VERSION = "7.2.4.0"
    private const val CHANNEL_ID = "web_cloud.189.cn"

    /** 每次请求携带的客户端后缀参数（rand 用 "_" 连接的随机数） */
    fun clientSuffix(): Map<String, String> {
        val r = ThreadLocalRandom.current()
        return mapOf(
            "clientType" to PC,
            "version" to VERSION,
            "channelId" to CHANNEL_ID,
            "rand" to "${r.nextLong(0, 100_000)}_${r.nextLong(0, 10_000_000_000L)}",
        )
    }

    /** 会话参数（CloudTune 未填写 clientSn/jgOpenId，仅 returnType=JSON） */
    fun deviceParams(): Map<String, String> = mapOf("returnType" to "JSON")

    /** HTTP 规范 GMT 时间，如 "Mon, 02 Jan 2006 15:04:05 GMT" */
    fun httpDate(): String {
        val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("GMT")
        return sdf.format(java.util.Date())
    }

    /**
     * HMAC-SHA1 签名（OpenList signatureOfHmac）。
     * 签名原文：SessionKey=<sk>&Operate=<method>&RequestURI=<path>&Date=<httpDate>[&params=...]
     * 结果为小写字节的 hex 大写。
     */
    fun signature(
        sessionSecret: String,
        sessionKey: String,
        operate: String,
        fullUrl: String,
        dateOfGmt: String,
        param: String = "",
    ): String {
        val path = try {
            java.net.URI(fullUrl).path.ifEmpty { "/" }
        } catch (_: Exception) {
            "/"
        }
        val data = StringBuilder()
            .append("SessionKey=").append(sessionKey)
            .append("&Operate=").append(operate)
            .append("&RequestURI=").append(path)
            .append("&Date=").append(dateOfGmt)
        if (param.isNotEmpty()) data.append("&params=").append(param)

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(sessionSecret.toByteArray(), "HmacSHA1"))
        val digest = mac.doFinal(data.toString().toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }

    fun uuid(): String = UUID.randomUUID().toString()
}
