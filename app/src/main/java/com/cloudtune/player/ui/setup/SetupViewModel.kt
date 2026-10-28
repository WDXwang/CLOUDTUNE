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
import com.cloudtune.player.data.MusicRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 首次配置 / 服务器设置页 ViewModel。
 *
 * 负责：收集表单字段、测试连接、保存配置。
 * 表单状态直接用 mutableStateOf 暴露，便于 Compose 直接绑定。
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

    /** 当前选择的后端：WebDAV（推荐）/ 天翼直连（实验） */
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
    }

    /** 用当前表单做连通性测试（不影响已保存配置） */
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

    /** 保存配置；成功后置 [saved]，界面跳转 */
    fun save() {
        if (saving) return
        viewModelScope.launch {
            saving = true
            saveError = null
            runCatching { repository.saveConfig(buildConfig()) }
                .onSuccess { saved = true }
                .onFailure { e -> saveError = "保存失败：${e.message ?: "未知错误"}" }
            saving = false
        }
    }
}
