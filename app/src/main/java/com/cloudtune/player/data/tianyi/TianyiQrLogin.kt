package com.cloudtune.player.data.tianyi

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadLocalRandom

/**
 * 天翼云盘「扫码登录」，流程参照 OpenList（原 AList）189pc 驱动：
 *
 * 1. [initBaseParams]  GET cloud.189.cn/api/portal/unifyLoginForPC.action → 跟随重定向到登录框，
 *    从重定向地址解析 lt / reqId（缺 paramId 时再 POST appConf.do 获取）；
 * 2. [requestUuid]      POST open.e.189.cn/api/logbox/oauth2/getUUID.do → 拿到 uuid / encryuuid / paramId；
 *    二维码内容即 uuid（用手机天翼云盘 App 扫描）；
 * 3. [pollState]        POST open.e.189.cn/api/logbox/oauth2/qrcodeLoginState.do 轮询扫码状态；
 * 4. 状态为成功(0)拿到 redirectUrl，[finishLogin] 用同一个 CookieJar 跟随该重定向，
 *    服务端在跳转链中把 COOKIE_LOGIN_USER 种到 .cloud.189.cn，最后读出该域 Cookie 字符串。
 *
 * 全程在手机端 OkHttp 完成（不依赖 WebView），因此 Cookie 绑定的是手机自身 IP，
 * 与后续播放同网，规避了之前 WebView 白屏和沙箱 IP 不一致问题。
 */
class TianyiQrLogin(
    private val client: OkHttpClient = defaultClient(),
) {
    /** 一次扫码会话的参数 */
    data class Session(
        val uuid: String,
        val encryuuid: String,
        val paramId: String,
        val lt: String,
        val reqId: String,
        val referer: String,
    )

    /** 轮询结果 */
    sealed class Poll {
        object Waiting : Poll()        // 等待扫描
        object Scanned : Poll()       // 已扫描，等待手机确认
        object Expired : Poll()       // 二维码过期
        data class Success(val redirectUrl: String) : Poll()
        data class Fail(val message: String) : Poll()
    }

    private val jar = MemoryCookieJar()

    // 设备指纹：OpenList 的 loginHeaders 会带 user-finger，
    // 源码注释明确「缺少 user-finger 和 Referer 会被判定为陌生设备」，
    // 一次登录会话内固定同一个值，随 appConf/getUUID/轮询请求一起发送。
    private val userFinger: String =
        (1_000_000_000L + Math.abs(ThreadLocalRandom.current().nextLong() % 9_000_000_000L)).toString()

    private val http: OkHttpClient = client.newBuilder()
        .cookieJar(jar)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** 手动走跳转链用：不自动跟随，逐跳处理 */
    private val noRedirectClient: OkHttpClient = client.newBuilder()
        .cookieJar(jar)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** logbox 公共请求头（与 OpenList loginHeaders 对齐） */
    private fun loginHeaders(lt: String, reqId: String, referer: String): Map<String, String> = mapOf(
        "REQID" to reqId,
        "lt" to lt,
        "user-finger" to userFinger,
        "Referer" to referer,
    )

    private fun Request.Builder.applyHeaders(lt: String, reqId: String, referer: String) = apply {
        header("User-Agent", UA)
        loginHeaders(lt, reqId, referer).forEach { (k, v) -> header(k, v) }
    }

    // ---- 第 1 步：初始化登录参数 ----
    private fun initBaseParams(): Session? {
        val ts = System.currentTimeMillis()
        val url = "https://cloud.189.cn/api/portal/unifyLoginForPC.action".toHttpUrl().newBuilder()
            .addQueryParameter("appId", APP_ID)
            .addQueryParameter("clientType", CLIENT_TYPE)
            .addQueryParameter("returnURL", RETURN_URL)
            .addQueryParameter("timeStamp", ts.toString())
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            // 重定向后的最终地址（登录框），从中解析 lt / reqId
            val finalUrl = resp.request.url
            var lt = finalUrl.queryParameter("lt").orEmpty()
            var reqId = finalUrl.queryParameter("reqId").orEmpty()
            var referer = finalUrl.toString()
            var paramId = finalUrl.queryParameter("paramId").orEmpty()

            if (lt.isEmpty() || reqId.isEmpty()) {
                // 老页面：参数写在页内
                val body = resp.body?.string().orEmpty()
                lt = Regex("""lt\s*=\s*"(.+?)"""").find(body)?.groupValues?.get(1).orEmpty()
                reqId = Regex("""reqId\s*=\s*"(.+?)"""").find(body)?.groupValues?.get(1).orEmpty()
                paramId = Regex("""paramId\s*=\s*"(.+?)"""").find(body)?.groupValues?.get(1).orEmpty()
                referer = "https://open.e.189.cn/api/logbox/separate/web/index.html" +
                    "?appId=$APP_ID&lt=$lt&reqId=$reqId"
            }
            if (lt.isEmpty() || reqId.isEmpty()) return null

            // 重定向地址上没有 paramId，再问一次 appConf.do
            if (paramId.isEmpty()) {
                paramId = requestParamId(lt, reqId, referer) ?: return null
            }
            // uuid 下一步获取，这里先占位
            return Session(
                uuid = "", encryuuid = "", paramId = paramId,
                lt = lt, reqId = reqId, referer = referer,
            )
        }
    }

    private fun requestParamId(lt: String, reqId: String, referer: String): String? {
        val form = FormBody.Builder()
            .add("version", "2.0")
            .add("appKey", APP_ID)
            .build()
        val req = Request.Builder()
            .url("https://open.e.189.cn/api/logbox/oauth2/appConf.do")
            .applyHeaders(lt, reqId, referer)
            .post(form)
            .build()
        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            return json.optJSONObject("data")?.optString("paramId")?.takeIf { it.isNotEmpty() }
        }
    }

    // ---- 第 2 步：获取二维码 UUID ----
    /** 开始一次扫码，返回可展示的会话（二维码内容为 [Session.uuid]） */
    fun start(): Session? {
        val base = initBaseParams() ?: return null
        val form = FormBody.Builder().add("appId", APP_ID).build()
        val req = Request.Builder()
            .url("https://open.e.189.cn/api/logbox/oauth2/getUUID.do")
            .applyHeaders(base.lt, base.reqId, base.referer)
            .post(form)
            .build()
        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            val uuid = json.optString("uuid")
            if (uuid.isEmpty()) return null
            return base.copy(
                uuid = uuid,
                encryuuid = json.optString("encryuuid"),
            )
        }
    }

    // ---- 第 3 步：轮询扫码状态 ----
    fun poll(session: Session): Poll {
        val now = System.currentTimeMillis()
        val form = FormBody.Builder()
            .add("appId", APP_ID)
            .add("clientType", QR_CLIENT_TYPE)
            .add("returnUrl", RETURN_URL)
            .add("paramId", session.paramId)
            .add("uuid", session.uuid)
            .add("encryuuid", session.encryuuid)
            .add("cb_SaveName", "3")
            .add("isOauth2", "false")
            .add("state", "")
            .add("date", dateStr())
            .add("timeStamp", now.toString())
            .build()
        val req = Request.Builder()
            .url("https://open.e.189.cn/api/logbox/oauth2/qrcodeLoginState.do")
            .applyHeaders(session.lt, session.reqId, session.referer)
            .post(form)
            .build()
        http.newCall(req).execute().use { resp ->
            val json = JSONObject(resp.body?.string().orEmpty())
            return when (val status = json.optInt("status", -999)) {
                0 -> Poll.Success(json.optString("redirectUrl"))
                -11002 -> Poll.Scanned
                -11001 -> Poll.Expired
                -106 -> Poll.Waiting
                else -> Poll.Fail("status=$status ${json.optString("msg")}")
            }
        }
    }

    // ---- 第 4b 步：用 redirectUrl 换 token 会话（OpenList 189pc 方式）----
    /**
     * 用扫码成功后的 redirectUrl 换 token 会话（POST getSessionForPC.action）。
     *
     * 返回的 [TianyiSession] 通过 HMAC-SHA1 签名访问 api.cloud.189.cn，**不绑定 IP**，
     * 切换网络后依然可用（这是相对于 Cookie 直连的关键优势）。
     */
    fun exchangeSession(redirectUrl: String): TianyiSession? {
        val url = "https://api.cloud.189.cn/getSessionForPC.action".toHttpUrl().newBuilder()
            .apply {
                TianyiAuth.clientSuffix().forEach { (k, v) -> addQueryParameter(k, v) }
                TianyiAuth.deviceParams().forEach { (k, v) -> addQueryParameter(k, v) }
                addQueryParameter("redirectURL", redirectUrl)
            }
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("X-Request-ID", TianyiAuth.uuid())
            .post(FormBody.Builder().build())
            .build()
        return runCatching {
            http.newCall(req).execute().use { resp ->
                val json = JSONObject(resp.body?.string().orEmpty())
                val session = TianyiSession(
                    accessToken = json.optString("accessToken"),
                    refreshToken = json.optString("refreshToken"),
                    sessionKey = json.optString("sessionKey"),
                    sessionSecret = json.optString("sessionSecret"),
                )
                if (session.valid) session else null
            }
        }.getOrNull()
    }

    // ---- 第 4 步：跟随重定向 + SSO 换 Cookie，抓取 COOKIE_LOGIN_USER ----
    /**
     * 登录成功后按浏览器方式抓取 COOKIE_LOGIN_USER。
     *
     * 第一步：跟随授权重定向（oauth2Transfer，带 lt/reqId/Referer），在 e.189.cn 建立 SSO 会话
     * （SSON / OPENINFO / GUID / LT 等）。第二步：用该 SSO 会话访问 cloud.189.cn 的 SSO 换 Cookie
     * 入口 loginUrl.action，它会自动跳回 cloud.189.cn/web/main 并在 cloud.189.cn 种下 COOKIE_LOGIN_USER。
     */
    fun finishLogin(redirectUrl: String, lt: String, reqId: String): String? {        var current = redirectUrl
        // 手动走跳转链，避免 OkHttp 自动跟随时丢失跳转链上的 Set-Cookie / 请求头
        repeat(15) {
            val req = Request.Builder()
                .url(current)
                .header("User-Agent", UA)
                .header("lt", lt)
                .header("REQID", reqId)
                .header("Referer", current)
                .get()
                .build()
            noRedirectClient.newCall(req).execute().use { resp ->
                val code = resp.code
                if (code in 300..399) {
                    val loc = resp.header("Location")
                    val next = resp.request.url.resolve(loc ?: "")?.toString()
                        ?: return readCloudCookie(lt, reqId)
                    current = next
                } else {
                    // 已落到非 3xx（e.189.cn 的 SSO 登录页等），用 SSO 会话去换 cloud Cookie
                    return readCloudCookie(lt, reqId)
                }
            }
        }
        return readCloudCookie(lt, reqId)
    }

    /**
     * 用 e.189.cn 的 SSO 会话访问 cloud.189.cn 的 SSO 入口 loginUrl.action，
     * 让 cloud.189.cn 把 SSO 换成自己的会话 Cookie（COOKIE_LOGIN_USER + JSESSIONID）。
     */
    private fun readCloudCookie(lt: String, reqId: String): String? {
        val entry = "https://cloud.189.cn/api/portal/loginUrl.action" +
            "?redirectURL=https%3A%2F%2Fcloud.189.cn%2Fmain.action"
        var current = entry
        repeat(12) {
            val req = Request.Builder()
                .url(current)
                .header("User-Agent", UA)
                .header("lt", lt)
                .header("REQID", reqId)
                .header("Referer", current)
                .get()
                .build()
            noRedirectClient.newCall(req).execute().use { resp ->
                if (resp.code in 300..399) {
                    val loc = resp.header("Location")
                    val next = resp.request.url.resolve(loc ?: "")?.toString()
                        ?: return readJar()
                    current = next
                } else {
                    return readJar()
                }
            }
        }
        return readJar()
    }

    private fun readJar(): String? {
        val cookie = jar.cookieHeaderFor("cloud.189.cn")
        return if (cookie.contains("COOKIE_LOGIN_USER=")) cookie else null
    }

    /** 诊断：列出当前 CookieJar 里与 cloud.189.cn 相关的 Cookie 名，便于排错 */
    fun cookieDiagnostics(): String =
        jar.allNames().takeIf { it.isNotEmpty() }?.joinToString(", ") { it } ?: "（无）"

    private fun dateStr(): String {
        // 与官方一致的格式：YYYY-MM-DDHH:mm:ssSSS（无分隔，本地时区）
        val sdf = java.text.SimpleDateFormat("yyyy-MM-ddHH:mm:ss.SSS", java.util.Locale.US)
        return sdf.format(java.util.Date())
    }

    companion object {
        private const val APP_ID = "9317140619"
        private const val CLIENT_TYPE = "10020"
        private const val QR_CLIENT_TYPE = "1"
        private const val RETURN_URL =
            "https://m.cloud.189.cn/zhuanti/2020/loginErrorPc/index.html"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * 简单内存 CookieJar：扁平保存，按 OkHttp 的 [Cookie.matches] 过滤请求；
 * 提供按域读取整行 Cookie 的能力（用于回填）。
 */
private class MemoryCookieJar : CookieJar {
    private val all = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { c ->
            all.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            all.add(c)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return all.filter { it.expiresAt >= now && it.matches(url) }
    }

    @Synchronized
    fun cookieHeaderFor(host: String): String {
        val now = System.currentTimeMillis()
        return all.filter { it.expiresAt >= now && host.endsWith(it.domain.trimStart('.')) }
            .distinctBy { it.name }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    @Synchronized
    fun allNames(): List<String> {
        val now = System.currentTimeMillis()
        return all.filter { it.expiresAt >= now }
            .map { "${it.name}@${it.domain}" }
            .distinct()
    }
}
