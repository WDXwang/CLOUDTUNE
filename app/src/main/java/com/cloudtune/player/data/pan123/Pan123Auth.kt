package com.cloudtune.player.data.pan123

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 123 云盘登录：账号 + 密码换 JWT token（实验性，非官方接口）。
 *
 * 成功后返回 token，由调用方写入 [com.cloudtune.player.core.model.ServerConfig.pan123Token]。
 */
object Pan123Auth {

    /**
     * 登录并返回 Bearer token。
     * @throws IllegalStateException 账号密码错误或平台返回业务失败时，携带平台 message。
     */
    suspend fun login(passport: String, password: String, client: OkHttpClient): String =
        withContext(Dispatchers.IO) {
            if (passport.isBlank() || password.isBlank()) error("请填写 123 云盘账号和密码")

            val body = JSONObject().apply {
                put("passport", passport.trim())
                put("password", password)
                put("remember", true)
            }.toString()

            val req = Request.Builder()
                .url(SIGN_IN_URL)
                .header("platform", "android")
                .header("app-version", "3")
                .header("Accept", "application/json")
                .header("User-Agent", UA)
                .post(body.toRequestBody(JSON_MEDIA))
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrElse {
                    if (!resp.isSuccessful) error("123 云盘登录失败：HTTP ${resp.code}")
                    error("123 云盘登录失败：返回非 JSON 数据")
                }

                val token = json.optJSONObject("data")?.optString("token").orEmpty()
                if (token.isBlank()) {
                    val message = json.optString("message").ifBlank { "HTTP ${resp.code}" }
                    error("123 云盘登录失败：$message")
                }
                token
            }
        }

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private const val SIGN_IN_URL = "https://www.123pan.com/api/user/sign_in"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
}
