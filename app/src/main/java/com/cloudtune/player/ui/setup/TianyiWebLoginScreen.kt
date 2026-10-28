package com.cloudtune.player.ui.setup

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 手机 Web 登录的目标地址：天翼云盘移动 H5 版（移动 UA 下官方登录页会跳到这里）。
 * 它在手机 WebView 里能正常渲染登录表单，登录后把 COOKIE_LOGIN_USER 种在 .cloud.189.cn 域。
 */
private const val TIANYI_LOGIN_URL = "https://h5.cloud.189.cn/"
/** 读取 Cookie 的来源域（登录 Cookie 种在 .cloud.189.cn，h5 与主域都读，合并去重） */
private val COOKIE_ORIGINS = listOf("https://cloud.189.cn", "https://h5.cloud.189.cn")

/** 从多个域读取 Cookie 并按名称合并去重（主域优先），返回 "k=v; k=v" 字符串。 */
private fun collectCookie(): String {
    val byName = LinkedHashMap<String, String>()
    COOKIE_ORIGINS.forEach { origin ->
        val raw = CookieManager.getInstance().getCookie(origin) ?: return@forEach
        raw.split(";").forEach { part ->
            val p = part.trim()
            if (p.isEmpty()) return@forEach
            val eq = p.indexOf('=')
            if (eq < 0) return@forEach
            val name = p.substring(0, eq)
            if (!byName.containsKey(name)) byName[name] = p
        }
    }
    return byName.values.joinToString("; ")
}

/**
 * 天翼云盘「手机 Web 端登录」页。
 *
 * 内嵌 WebView 打开云盘主界面（自动跳转统一登录），用户在 App 内登录。
 * 一旦检测到登录标志 Cookie（COOKIE_LOGIN_USER），自动把该域全部 Cookie 回填并交回设置页。
 *
 * WebView 配置要点（解决“无响应/白屏”）：
 * - 自定义移动端 Chrome UA，避免被天翼识别成 WebView 而拒绝渲染；
 * - 打开 JS / DOM 存储 / 多窗口，天翼登录用 window.open 弹窗需要 [WebChromeClient.onCreateWindow]；
 * - 允许第三方 Cookie；
 * - 通过进度回调把加载状态实时反馈到界面，避免看起来像卡死。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TianyiWebLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (String) -> Unit,
) {
    var statusText by remember { mutableStateOf("正在打开天翼云盘…") }
    // 保留 WebView 引用，供“刷新”按钮使用
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 防止重复回传
    var submitted by remember { mutableStateOf(false) }

    // 合并各域 Cookie；若已含登录标志则回传
    val trySubmit: (Boolean) -> Unit = { force ->
        val cookie = collectCookie()
        val loggedIn = cookie.contains("COOKIE_LOGIN_USER=")
        if (loggedIn && !submitted) {
            submitted = true
            statusText = "登录成功，正在保存 Cookie…"
            onLoggedIn(cookie)
        } else if (force && cookie.isNotBlank() && !submitted) {
            // 用户手动点“完成”，把当前 Cookie 交回（是否有效由后续测试连接判定）
            submitted = true
            onLoggedIn(cookie)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("手机 Web 登录") },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = { trySubmit(true) }) {
                        Icon(Icons.Filled.Done, contentDescription = "登录完成")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        // ---- 关键配置：让天翼网页能正常渲染与弹窗 ----
                        val s = settings
                        s.javaScriptEnabled = true
                        s.domStorageEnabled = true
                        s.databaseEnabled = true
                        s.loadWithOverviewMode = true
                        s.useWideViewPort = true
                        s.setSupportZoom(false)
                        s.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        // 用真实手机浏览器 UA，避免被识别为 WebView 而拒绝/白屏
                        s.userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; SM-G9910) AppleWebKit/537.36 " +
                                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                statusText = "正在加载…"
                            }

                            // 页面导航交给 WebView 自身处理（不拦截），保证登录跳转连贯
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                url: String?,
                            ): Boolean = false

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // 立即检测一次；登录写 Cookie 是异步的，稍后再补查一次
                                trySubmit(false)
                                view?.postDelayed({ trySubmit(false) }, 1200)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?,
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    statusText = "加载失败：${error?.description ?: "网络错误"}，可点右上角刷新"
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            // 实时反馈加载进度，避免页面看着像“无响应”
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (newProgress < 100) {
                                    statusText = "正在加载 $newProgress%…"
                                } else {
                                    statusText = "页面已加载，请在下方登录"
                                }
                            }

                            // 天翼登录页会用 window.open 弹窗；把弹窗内容加载回当前 WebView
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?,
                            ): Boolean {
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                transport.webView = view
                                resultMsg?.sendToTarget()
                                return true
                            }

                            override fun onCloseWindow(window: WebView?) {
                                window?.setVisibility(android.view.View.VISIBLE)
                            }
                        }

                        loadUrl(TIANYI_LOGIN_URL)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "提示：登录页跳转后需保持本页在前台。登录成功后会自动返回并填入 Cookie；" +
                        "若迟迟未自动跳回，可点右上角「刷新」重试，或「登录完成」手动确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
