package com.cloudtune.player.data.pan123

import com.cloudtune.player.core.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ThreadLocalRandom

/**
 * 123 云盘登录：账号 + 密码换 JWT token（实验性，非官方接口）。
 *
 * 要点（与个人盘 API 不是同一站点）：
 * - 登录域：`https://user.123pan.cn/api/user/sign_in`
 * - 个人盘域：`https://yun.123pan.cn/b/api/...`
 * - 成功判定是 `code == 200`（其它业务接口是 `code == 0`）
 * - `loginuuid` 是设备标识，长期复用，参与风控但不参与签名
 */
object Pan123Auth {

    /** 登录结果：token + 本次使用的设备标识。 */
    data class Session(val token: String, val loginUuid: String)

    /**
     * 登录并返回会话。
     * @param loginuuid 已有设备标识；为空时随机生成一个新的
     * @throws IllegalStateException 账号密码错误或平台返回业务失败时，携带平台 message。
     */
    suspend fun login(
        passport: String,
        password: String,
        client: OkHttpClient,
        loginuuid: String = "",
    ): Session = withContext(Dispatchers.IO) {
        val account = passport.trim()
        if (account.isBlank() || password.isBlank()) error("请填写 123 云盘账号和密码")

        val uuid = loginuuid.trim().ifBlank { randomLoginUuid() }

        val body = JSONObject().apply {
            put("passport", account)
            put("password", password)
            put("remember", false)
        }.toString()

        val req = Request.Builder()
            .url(SIGN_IN_URL)
            .header("platform", PLATFORM_WEB)
            .header("app-version", APP_VERSION_SIGN_IN)
            .header("loginuuid", uuid)
            .header("Origin", SIGN_IN_ORIGIN)
            .header("Referer", SIGN_IN_REFERER)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", UA)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: error("123 云盘登录失败：HTTP ${resp.code}，返回内容不是 JSON")
            val code = json.optInt("code", -1)
            val token = json.optJSONObject("data")?.optString("token").orEmpty()
            if (code == 200 && token.isNotBlank()) {
                Session(token = token, loginUuid = uuid)
            } else {
                val message = json.optString("message").ifBlank { "HTTP ${resp.code} / code=$code" }
                error("123 云盘登录失败：$message")
            }
        }
    }

    /** 生成 32 位十六进制设备标识。 */
    fun randomLoginUuid(): String {
        val rnd = ThreadLocalRandom.current()
        return buildString(32) {
            repeat(32) { append("0123456789abcdef"[rnd.nextInt(16)]) }
        }
    }

    /**
     * 如果配置里已有 token，则构造一个可用的数据源；否则先登录。
     * 供「测试连接」与保存流程复用。
     */
    fun resolveLoginUuid(config: ServerConfig): String =
        config.pan123LoginUuid.trim().ifBlank { randomLoginUuid() }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private const val SIGN_IN_URL = "https://user.123pan.cn/api/user/sign_in"
    private const val SIGN_IN_ORIGIN = "https://user.123pan.cn"
    private const val SIGN_IN_REFERER = "https://user.123pan.cn/"
    private const val PLATFORM_WEB = "web"

    /** 登录接口专用的 app-version（网页端个人盘接口是 3，两者不可混用）。 */
    private const val APP_VERSION_SIGN_IN = "132"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
}
