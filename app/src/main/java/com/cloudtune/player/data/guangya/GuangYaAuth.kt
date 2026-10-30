package com.cloudtune.player.data.guangya

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ThreadLocalRandom

/**
 * ============================================================
 *  实验性 · 非官方接口，风险自担
 * ------------------------------------------------------------
 *  光鸭云盘登录：账号 + 密码换 access / refresh token。
 *
 *  - 登录域：https://account.guangyapan.com
 *  - 流程：先初始化人机验证拿 captcha_token，再调用 /v1/auth/signin
 *  - 账号接口需要一整套 X-Device-* 头，其中 X-Device-Id / X-Device-Sign 需 URL 编码
 * ============================================================
 */
object GuangYaAuth {

    /** 登录结果：令牌 + 设备标识（设备标识建议长期复用）。 */
    data class Session(
        val accessToken: String,
        val refreshToken: String,
        val deviceId: String,
        val deviceSign: String,
        val expiresInSeconds: Long,
    )

    /** 设备标识。 */
    data class Device(val deviceId: String, val deviceSign: String)

    /** 用已有设备标识构造，缺失时随机生成一个。 */
    fun deviceOf(deviceId: String, deviceSign: String): Device {
        val id = deviceId.trim().ifBlank { newDeviceId() }
        val sign = deviceSign.trim().ifBlank { newDeviceSign(id) }
        return Device(id, sign)
    }

    /**
     * 账号密码登录。
     * @throws IllegalStateException 账号密码错误、需要短信验证或风控拦截时，抛出可读文案。
     */
    suspend fun login(
        account: String,
        password: String,
        client: OkHttpClient,
        device: Device,
    ): Session = withContext(Dispatchers.IO) {
        val normalized = normalizeAccount(account)
        if (normalized.isBlank() || password.isBlank()) error("请填写光鸭云盘账号和密码")

        val meta = metaFor(normalized)
        var captchaToken = initCaptcha(client, device, ACTION_SIGNIN, meta)

        var attempt = 0
        while (true) {
            val json = doSignIn(client, device, normalized, password, captchaToken)
            val code = json.optString("code").ifBlank { json.optString("error") }
            if (code == "captcha_required" || code == "captcha_invalid") {
                if (attempt >= 2) error("光鸭安全验证未通过，请稍后重试")
                attempt++
                captchaToken = initCaptcha(client, device, ACTION_SIGNIN, meta)
                continue
            }
            if (!isSuccess(json)) error(mapSigninError(json))
            return@withContext parseSession(json, device)
        }
        @Suppress("UNREACHABLE_CODE")
        error("光鸭登录失败")
    }

    /** 短信验证码登录的中间态：验证会话 ID + 本次 captcha 令牌。 */
    data class SmsChallenge(val verificationId: String, val captchaToken: String)

    /**
     * 发送短信验证码。
     * @throws IllegalStateException 需要人机验证、发送过于频繁等，抛出可读文案。
     */
    suspend fun sendSmsCode(
        account: String,
        client: OkHttpClient,
        device: Device,
    ): SmsChallenge = withContext(Dispatchers.IO) {
        val phone = normalizeAccount(account)
        if (!phone.startsWith("+")) error("请填写手机号（11 位数字或 +86 开头）")
        val captchaToken = initCaptcha(
            client,
            device,
            ACTION_VERIFICATION,
            JSONObject().put("phone_number", phone),
        )
        val body = JSONObject().apply {
            put("phone_number", phone)
            put("target", "ANY")
            put("client_id", CLIENT_ID)
        }.toString()
        val builder = accountRequest(VERIFICATION, body, device)
        if (captchaToken.isNotBlank()) builder.header("X-Captcha-Token", captchaToken)
        val json = execute(client, builder.build())
        if (!isSuccess(json)) error(mapSmsError(json))
        val data = json.optJSONObject("data") ?: json
        val id = firstNonBlank(data.optString("verification_id"), data.optString("verificationId"))
        if (id.isBlank()) error("光鸭未返回验证会话，请稍后重试")
        SmsChallenge(id, captchaToken)
    }

    /** 校验短信验证码并换取 access token。 */
    suspend fun loginBySms(
        account: String,
        code: String,
        challenge: SmsChallenge,
        client: OkHttpClient,
        device: Device,
    ): Session = withContext(Dispatchers.IO) {
        val phone = normalizeAccount(account)
        if (code.isBlank()) error("请填写短信验证码")
        val verifyBody = JSONObject().apply {
            put("verification_id", challenge.verificationId)
            put("verification_code", code.trim())
            put("client_id", CLIENT_ID)
        }.toString()
        val verifyJson = execute(client, accountRequest(VERIFY, verifyBody, device).build())
        if (!isSuccess(verifyJson)) error(mapSmsError(verifyJson))
        val vData = verifyJson.optJSONObject("data") ?: verifyJson
        val vToken = firstNonBlank(
            vData.optString("verification_token"),
            vData.optString("verificationToken"),
        )
        if (vToken.isBlank()) error("验证码校验失败，请重新获取")

        val signinBody = JSONObject().apply {
            put("verification_code", code.trim())
            put("verification_token", vToken)
            put("username", phone)
            put("client_id", CLIENT_ID)
        }.toString()
        val builder = accountRequest(SIGNIN, signinBody, device)
        if (challenge.captchaToken.isNotBlank()) builder.header("X-Captcha-Token", challenge.captchaToken)
        val json = execute(client, builder.build())
        if (!isSuccess(json)) error(mapSmsError(json))
        parseSession(json, device)
    }

    /** 用 refresh token 续期；失败返回 null。 */
    suspend fun refresh(
        refreshToken: String,
        client: OkHttpClient,
        device: Device,
    ): Session? = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank()) return@withContext null
        val body = JSONObject().apply {
            put("client_id", CLIENT_ID)
            put("grant_type", "refresh_token")
            put("refresh_token", refreshToken)
        }.toString()
        val req = accountRequest(AUTH_TOKEN, body, device)
            .header("X-Action", "401")
            .build()
        val json = runCatching { execute(client, req) }.getOrNull() ?: return@withContext null
        if (!isSuccess(json)) return@withContext null
        val session = runCatching { parseSession(json, device) }.getOrNull()
        session?.copy(refreshToken = session.refreshToken.ifBlank { refreshToken })
    }

    /** 账号归一化：手机号补 "+86 " 前缀；邮箱 / 用户名原样保留。 */
    fun normalizeAccount(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty()) return s
        val digits = s.filter { it.isDigit() }
        return when {
            s.startsWith("+86") -> "+86 " + digits.removePrefix("86")
            s.length == 11 && s.all { it.isDigit() } -> "+86 $s"
            else -> s
        }
    }

    // ---------- 内部实现 ----------

    private fun metaFor(account: String): JSONObject = when {
        account.startsWith("+") -> JSONObject().put("phone_number", account)
        account.contains("@") -> JSONObject().put("email", account)
        else -> JSONObject().put("username", account)
    }

    private fun initCaptcha(
        client: OkHttpClient,
        device: Device,
        action: String,
        meta: JSONObject,
    ): String {
        val body = JSONObject().apply {
            put("client_id", CLIENT_ID)
            put("action", action)
            put("device_id", device.deviceId)
            put("captcha_token", "")
            put("meta", meta)
        }.toString()
        val json = runCatching { execute(client, accountRequest(CAPTCHA_INIT, body, device).build()) }
            .getOrDefault(JSONObject())
        return json.optString("captcha_token")
    }

    private fun doSignIn(
        client: OkHttpClient,
        device: Device,
        account: String,
        password: String,
        captchaToken: String,
    ): JSONObject {
        val body = JSONObject().apply {
            put("client_id", CLIENT_ID)
            put("username", account)
            put("password", password)
        }.toString()
        val builder = accountRequest(SIGNIN, body, device)
        if (captchaToken.isNotBlank()) builder.header("X-Captcha-Token", captchaToken)
        return runCatching { execute(client, builder.build()) }.getOrElse { e ->
            error("光鸭登录失败：${e.message ?: "网络异常"}")
        }
    }

    private fun parseSession(json: JSONObject, device: Device): Session {
        val data = json.optJSONObject("data") ?: json
        val access = firstNonBlank(data.optString("access_token"), data.optString("accessToken"))
        if (access.isBlank()) error("光鸭登录失败，请检查账号密码")
        val refresh = firstNonBlank(data.optString("refresh_token"), data.optString("refreshToken"))
        val expiresIn = when (val v = data.opt("expires_in")) {
            is Number -> v.toLong()
            null -> 0L
            else -> v.toString().toLongOrNull() ?: 0L
        }
        return Session(access, refresh, device.deviceId, device.deviceSign, expiresIn)
    }

    private fun isSuccess(json: JSONObject): Boolean {
        if (!json.optBoolean("success", true)) return false
        if (json.optString("error").isNotBlank()) return false
        val code = json.optString("code")
        return code.isBlank() || code == "0"
    }

    private fun mapSigninError(json: JSONObject): String {
        val code = json.optString("code").ifBlank { json.optString("error") }
        val serverMsg = firstNonBlank(
            json.optString("msg"), json.optString("message"), json.optString("error_description")
        )
        return when (code) {
            "too_many_requests" -> "光鸭登录尝试过于频繁，请稍后重试"
            "invalid_password", "invalid_account_or_password", "invalid_credentials",
            "unauthenticated", "user_not_found" -> "光鸭账号或密码不正确，请检查后重试"
            "password_required", "password_not_set" -> "该光鸭账号尚未设置密码，请改用网页登录"
            "verification_required", "verification_code_required" -> "光鸭要求短信验证，请改用网页登录"
            else -> serverMsg.ifBlank { "光鸭登录失败，请检查账号密码" }
        }
    }

    private fun mapSmsError(json: JSONObject): String {
        val code = json.optString("code").ifBlank { json.optString("error") }
        val serverMsg = firstNonBlank(
            json.optString("msg"), json.optString("message"), json.optString("error_description")
        )
        return when (code) {
            "too_many_requests" -> "验证码发送过于频繁，请稍后再试"
            "invalid_verification_code", "verification_code_invalid", "invalid_code" ->
                "验证码错误或已失效，请重新获取"
            "verification_expired", "verification_code_expired" -> "验证码已过期，请重新获取"
            "captcha_required", "captcha_invalid" -> "光鸭要求人机验证，请改用开发者 token 登录"
            "verification_required" -> "请先获取短信验证码"
            else -> serverMsg.ifBlank { "光鸭登录失败（${code.ifBlank { "未知错误" }}）" }
        }
    }

    private fun accountRequest(url: String, body: String, device: Device): Request.Builder {
        val deviceId = deviceIdFromSign(device.deviceSign, device.deviceId)
        return Request.Builder()
            .url(url)
            .header("X-Client-Id", CLIENT_ID)
            .header("X-Client-Version", CLIENT_VERSION)
            .header("X-Device-Id", URLEncoder.encode(deviceId, "UTF-8"))
            .header("X-Device-Model", DEVICE_MODEL)
            .header("X-Device-Name", DEVICE_NAME)
            .header("X-Device-Sign", URLEncoder.encode(device.deviceSign, "UTF-8"))
            .header("X-Os-Version", OS_VERSION)
            .header("X-Platform-Version", PLATFORM_VERSION)
            .header("X-Protocol-Version", PROTOCOL_VERSION)
            .header("X-Sdk-Version", SDK_VERSION)
            .header("Accept", "application/json, text/plain, */*")
            .header("User-Agent", UA)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA))
    }

    private fun execute(client: OkHttpClient, req: Request): JSONObject {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) error("光鸭接口返回为空（HTTP ${resp.code}）")
            return runCatching { JSONObject(text) }
                .getOrElse { error("光鸭接口返回非 JSON 数据（HTTP ${resp.code}）") }
        }
    }

    private fun deviceIdFromSign(deviceSign: String, fallback: String): String {
        val idx = deviceSign.indexOf('.')
        if (idx < 0) return fallback
        val rest = deviceSign.substring(idx + 1)
        return if (rest.length >= 32) rest.substring(0, 32) else fallback
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    private fun newDeviceId(): String {
        val rnd = ThreadLocalRandom.current()
        return buildString(32) {
            repeat(32) { append("0123456789abcdef"[rnd.nextInt(16)]) }
        }
    }

    private fun newDeviceSign(deviceId: String): String {
        val rnd = ThreadLocalRandom.current()
        val tail = buildString(32) {
            repeat(32) { append("0123456789abcdef"[rnd.nextInt(16)]) }
        }
        return "wdi10.$deviceId$tail"
    }

    private val JSON_MEDIA = "application/json;charset=UTF-8".toMediaType()

    private const val CLIENT_ID = "aMe-8VSlkrbQXpUR"
    private const val ACCOUNT_BASE = "https://account.guangyapan.com"

    private const val CAPTCHA_INIT = "$ACCOUNT_BASE/v1/shield/captcha/init"
    private const val SIGNIN = "$ACCOUNT_BASE/v1/auth/signin"
    private const val VERIFICATION = "$ACCOUNT_BASE/v1/auth/verification"
    private const val VERIFY = "$ACCOUNT_BASE/v1/auth/verification/verify"
    private const val AUTH_TOKEN = "$ACCOUNT_BASE/v1/auth/token"
    private const val ACTION_SIGNIN = "POST:/v1/auth/signin"
    private const val ACTION_VERIFICATION = "POST:/v1/auth/verification"

    private const val CLIENT_VERSION = "0.0.1"
    private const val SDK_VERSION = "9.1.3"
    private const val PROTOCOL_VERSION = "301"
    private const val DEVICE_MODEL = "chrome%2F131.0.0.0"
    private const val DEVICE_NAME = "PC-Chrome"
    private const val OS_VERSION = "Win32"
    private const val PLATFORM_VERSION = "1"

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
}
