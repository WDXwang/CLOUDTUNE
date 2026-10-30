package com.cloudtune.player.ui.setup

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cloudtune.player.CloudTuneApp
import com.cloudtune.player.core.model.BackendType
import com.cloudtune.player.core.model.ServerConfig
import com.cloudtune.player.core.model.StorageAccount
import com.cloudtune.player.core.model.displayName
import com.cloudtune.player.data.MusicRepository
import com.cloudtune.player.data.guangya.GuangYaAuth
import com.cloudtune.player.data.pan123.Pan123Auth
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 「添加网盘」页 ViewModel：收集表单、测试连接、登录（123 云盘 / 光鸭云盘短信验证码）、保存为新存储。
 *
 * 手机 Web 登录页会把抓到的 Cookie 写入本页的 SavedStateHandle，此处自动回填到表单。
 */
class SetupViewModel(
    app: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val repository: MusicRepository =
        (getApplication<Application>() as CloudTuneApp).container.repository

    init {
        // 监听手机 Web 登录页回填的 Cookie
        viewModelScope.launch {
            savedStateHandle.getStateFlow("tianyi_web_cookie", "")
                .collectLatest { cookie ->
                    if (cookie.isNotBlank()) {
                        applyWebLoginCookie(cookie)
                        savedStateHandle["tianyi_web_cookie"] = ""
                    }
                }
        }
    }

    /** 显示名称（聚合首页卡片标题），留空时使用后端名称 */
    var accountName by mutableStateOf("")

    /** 当前选择的后端 */
    var backend by mutableStateOf(BackendType.WEBDAV)
        private set

    // ---- WebDAV 表单字段 ----
    var baseUrl by mutableStateOf("")
    var davPath by mutableStateOf("/dav")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var rootPath by mutableStateOf("/")

    // ---- 天翼直连表单字段 ----
    var tianyiCookie by mutableStateOf("")
    var tianyiRootFolderId by mutableStateOf("-11")
    var tianyiSessionKey by mutableStateOf("")
    var tianyiSessionSecret by mutableStateOf("")
    var tianyiAccessToken by mutableStateOf("")
    var tianyiRefreshToken by mutableStateOf("")

    // ---- 123 云盘表单字段 ----
    var pan123Passport by mutableStateOf("")
    var pan123Password by mutableStateOf("")
    var pan123Token by mutableStateOf("")
    var pan123LoginUuid by mutableStateOf("")
    var pan123RootFolderId by mutableStateOf("0")
    var pan123LoggingIn by mutableStateOf(false)
        private set
    var pan123LoginMessage by mutableStateOf<String?>(null)
        private set

    // ---- 光鸭云盘表单字段 ----
    /** 手机号（11 位数字或 +86 开头） */
    var guangYaAccount by mutableStateOf("")
    /** 短信验证码 */
    var guangYaCode by mutableStateOf("")
    var guangYaAccessToken by mutableStateOf("")
    var guangYaRefreshToken by mutableStateOf("")
    var guangYaDeviceId by mutableStateOf("")
    var guangYaDeviceSign by mutableStateOf("")
    var guangYaRootFolderId by mutableStateOf("")
    var guangYaLoggingIn by mutableStateOf(false)
        private set
    var guangYaSendingCode by mutableStateOf(false)
        private set
    var guangYaLoginMessage by mutableStateOf<String?>(null)
        private set
    /** 发送验证码后拿到的验证会话，登录时必须与之一致 */
    private var guangYaChallenge: GuangYaAuth.SmsChallenge? = null

    // ---- 夸克网盘表单字段 ----
    var quarkCookie by mutableStateOf("")
    var quarkRootFid by mutableStateOf("0")

    /** 当前是否处于「token 会话」登录态（扫码登录，不绑定 IP） */
    val hasTokenSession: Boolean
        get() = tianyiSessionKey.isNotEmpty() && tianyiSessionSecret.isNotEmpty()

    /** 清除 token 会话（例如用户改用手动 Cookie 时） */
    fun clearTokenSession() {
        tianyiSessionKey = ""
        tianyiSessionSecret = ""
        tianyiAccessToken = ""
        tianyiRefreshToken = ""
    }

    // ---- 流程状态 ----
    var testing by mutableStateOf(false)
        private set
    /** 测试结果文案；null 表示尚未测试 */
    var testMessage by mutableStateOf<String?>(null)
        private set
    var testOk by mutableStateOf(false)
        private set

    var saving by mutableStateOf(false)
        private set
    /** 保存成功后置为 true，界面据此跳转主界面 */
    var saved by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    /** 切换后端类型，并清空上一次测试结果 */
    fun selectBackend(type: BackendType) {
        backend = type
        testMessage = null
    }

    /** 手机 Web 登录成功后，把抓到的 Cookie 回填到天翼直连表单 */
    fun applyWebLoginCookie(cookie: String) {
        if (cookie.isNotBlank()) {
            tianyiCookie = cookie
            testMessage = null
        }
    }

    /** 扫码登录成功后，回填 token 会话（不绑定 IP，优先于 Cookie） */
    fun applyWebLoginSession(
        sessionKey: String,
        sessionSecret: String,
        accessToken: String,
        refreshToken: String,
    ) {
        tianyiSessionKey = sessionKey
        tianyiSessionSecret = sessionSecret
        tianyiAccessToken = accessToken
        tianyiRefreshToken = refreshToken
        // token 会话已生效，可清掉旧 Cookie，避免歧义
        tianyiCookie = ""
        testMessage = null
    }

    /** 123 云盘账号密码登录，换到 Bearer token 后回填表单。 */
    fun loginPan123() {
        if (pan123LoggingIn) return
        if (pan123Passport.isBlank() || pan123Password.isBlank()) {
            pan123LoginMessage = "请先填写账号与密码"
            return
        }
        viewModelScope.launch {
            pan123LoggingIn = true
            pan123LoginMessage = null
            runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                try {
                    Pan123Auth.login(pan123Passport.trim(), pan123Password, client)
                } finally {
                    runCatching { client.dispatcher.executorService.shutdown() }
                }
            }.onSuccess { session ->
                pan123Token = session.token
                pan123LoginUuid = session.loginUuid
                pan123LoginMessage = "登录成功，可以测试连接"
            }.onFailure { e ->
                pan123LoginMessage = "登录失败：${e.message ?: "未知错误"}"
            }
            pan123LoggingIn = false
        }
    }

    /** 发送光鸭云盘短信验证码。 */
    fun sendGuangYaSmsCode() {
        if (guangYaSendingCode) return
        if (guangYaAccount.isBlank()) {
            guangYaLoginMessage = "请先填写手机号"
            return
        }
        viewModelScope.launch {
            guangYaSendingCode = true
            guangYaLoginMessage = null
            runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                try {
                    val device = GuangYaAuth.deviceOf(guangYaDeviceId, guangYaDeviceSign)
                    GuangYaAuth.sendSmsCode(guangYaAccount, client, device).also { challenge ->
                        guangYaChallenge = challenge
                        guangYaDeviceId = device.deviceId
                        guangYaDeviceSign = device.deviceSign
                    }
                } finally {
                    runCatching { client.dispatcher.executorService.shutdown() }
                }
            }.onSuccess {
                guangYaLoginMessage = "验证码已发送，请查看短信"
            }.onFailure { e ->
                guangYaChallenge = null
                guangYaLoginMessage = "发送失败：${e.message ?: "未知错误"}"
            }
            guangYaSendingCode = false
        }
    }

    /** 光鸭云盘短信验证码登录，换到 access token 后回填表单。 */
    fun loginGuangYaBySms() {
        if (guangYaLoggingIn) return
        val challenge = guangYaChallenge
        if (challenge == null) {
            guangYaLoginMessage = "请先获取验证码"
            return
        }
        if (guangYaCode.isBlank()) {
            guangYaLoginMessage = "请填写短信验证码"
            return
        }
        viewModelScope.launch {
            guangYaLoggingIn = true
            guangYaLoginMessage = null
            runCatching {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                try {
                    val device = GuangYaAuth.deviceOf(guangYaDeviceId, guangYaDeviceSign)
                    GuangYaAuth.loginBySms(guangYaAccount, guangYaCode, challenge, client, device)
                } finally {
                    runCatching { client.dispatcher.executorService.shutdown() }
                }
            }.onSuccess { session ->
                guangYaAccessToken = session.accessToken
                guangYaRefreshToken = session.refreshToken
                guangYaDeviceId = session.deviceId
                guangYaDeviceSign = session.deviceSign
                guangYaLoginMessage = "登录成功，可以测试连接"
            }.onFailure { e ->
                guangYaLoginMessage = "登录失败：${e.message ?: "未知错误"}"
            }
            guangYaLoggingIn = false
        }
    }

    /** 根据当前表单构造配置对象 */
    private fun buildConfig(): ServerConfig = when (backend) {
        BackendType.WEBDAV -> ServerConfig(
            backend = BackendType.WEBDAV,
            baseUrl = baseUrl.trim(),
            davPath = davPath.trim().ifBlank { "/dav" },
            username = username.trim(),
            password = password,
            rootPath = rootPath.trim().ifBlank { "/" },
        )

        BackendType.TIANYI_DIRECT -> ServerConfig(
            backend = BackendType.TIANYI_DIRECT,
            tianyiCookie = tianyiCookie.trim(),
            tianyiRootFolderId = tianyiRootFolderId.trim().ifBlank { "-11" },
            tianyiSessionKey = tianyiSessionKey,
            tianyiSessionSecret = tianyiSessionSecret,
            tianyiAccessToken = tianyiAccessToken,
            tianyiRefreshToken = tianyiRefreshToken,
        )

        BackendType.PAN123 -> ServerConfig(
            backend = BackendType.PAN123,
            pan123Passport = pan123Passport.trim(),
            pan123Token = pan123Token,
            pan123LoginUuid = pan123LoginUuid,
            pan123RootFolderId = pan123RootFolderId.trim().ifBlank { "0" },
        )

        BackendType.QUARK -> ServerConfig(
            backend = BackendType.QUARK,
            quarkCookie = quarkCookie.trim(),
            quarkRootFid = quarkRootFid.trim().ifBlank { "0" },
        )

        BackendType.GUANGYA -> ServerConfig(
            backend = BackendType.GUANGYA,
            guangYaAccount = guangYaAccount.trim(),
            guangYaAccessToken = guangYaAccessToken,
            guangYaRefreshToken = guangYaRefreshToken,
            guangYaDeviceId = guangYaDeviceId,
            guangYaDeviceSign = guangYaDeviceSign,
            guangYaRootFolderId = guangYaRootFolderId.trim(),
        )
    }

    /** 用当前表单做连通性测试（不影响已保存的配置） */
    fun testConnection() {
        if (testing) return
        viewModelScope.launch {
            testing = true
            testMessage = null
            runCatching { repository.testConnection(buildConfig()) }
                .onSuccess { result ->
                    result.onSuccess {
                        testOk = true
                        testMessage = "连接成功，可以保存"
                    }.onFailure { e ->
                        testOk = false
                        testMessage = "连接失败：${e.message ?: "未知错误"}"
                    }
                }
                .onFailure { e ->
                    testOk = false
                    testMessage = "连接失败：${e.message ?: "未知错误"}"
                }
            testing = false
        }
    }

    /** 保存为新存储；成功后置 [saved]，界面跳转 */
    fun save() {
        if (saving) return
        viewModelScope.launch {
            saving = true
            saveError = null
            val name = accountName.trim().ifBlank { backend.displayName }
            val account = StorageAccount(
                id = UUID.randomUUID().toString(),
                name = name,
                config = buildConfig(),
                enabled = true,
                createdAt = System.currentTimeMillis(),
            )
            runCatching { repository.addAccount(account) }
                .onSuccess {
                    saved = true
                }
                .onFailure { e -> saveError = "保存失败：${e.message ?: "未知错误"}" }
            saving = false
        }
    }
}
