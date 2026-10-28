package com.cloudtune.player.ui.setup

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import com.cloudtune.player.data.tianyi.TianyiQrLogin
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** 把二维码位图保存到系统相册（相册中的图片可用天翼云盘 App 的「从相册选择」扫码） */
private fun saveBitmapToGallery(context: android.content.Context, bmp: android.graphics.Bitmap): Boolean =
    try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "cloudtune_login_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(uri).use { os: OutputStream? ->
            if (os == null) return false
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
        }
        true
    } catch (_: Exception) { false }

/** 用 ZXing 生成二维码位图 */
private fun qrBitmap(content: String, px: Int = 720): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 1,
    )
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints)
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    for (x in 0 until px) {
        for (y in 0 until px) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}

/** 扫码登录结果：优先 token 会话（不绑定 IP）；否则回退 Cookie */
data class QrLoginResult(
    val sessionKey: String? = null,
    val sessionSecret: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val cookie: String? = null,
)

/**
 * 天翼云盘「扫码登录」页（参照 OpenList 189pc 扫码登录）。
 *
 * 原生展示二维码（不依赖 WebView），用手机天翼云盘 App 扫码并在手机上确认；
 * 本页自动轮询，登录成功后优先换 token 会话，失败再回退抓 COOKIE_LOGIN_USER，
 * 通过 [onLoggedIn] 回填。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TianyiQrLoginScreen(
    onBack: () -> Unit,
    onLoggedIn: (QrLoginResult) -> Unit,
) {
    // 每次重新生成二维码用的 key（点击刷新时 +1，触发 LaunchedEffect 重跑）
    var round by remember { mutableIntStateOf(0) }
    var phase by remember(round) { mutableStateOf("正在生成二维码…") }
    var qr by remember(round) { mutableStateOf<Bitmap?>(null) }
    var fatal by remember(round) { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("扫码登录天翼云盘") },
                actions = {
                    IconButton(onClick = { round++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新二维码")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "用手机天翼云盘 App 扫码",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = qr
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "登录二维码",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        fatal ?: phase,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                fatal ?: phase,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fatal != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            if (qr != null && fatal == null) {
                Spacer(Modifier.height(12.dp))
                val context = LocalContext.current
                OutlinedButton(
                    onClick = {
                        val ok = saveBitmapToGallery(context, qr!!)
                        Toast.makeText(
                            context,
                            if (ok) "二维码已保存到相册，可用天翼云盘 App 从相册扫码" else "保存失败，请直接对准屏幕扫码",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                ) { Text("保存二维码到相册") }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "步骤：1. 打开手机「天翼云盘」App → 右上角「扫一扫」；2. 对准本二维码（若相机对屏幕识别不稳，" +
                    "可先点上方「保存二维码到相册」，再从云盘 App 的扫一扫里选择相册图片）；" +
                    "3. 在手机上确认登录。本页检测到成功后会自动返回并填入登录会话（不绑定 IP）。" +
                    "二维码约 2 分钟有效，过期可点右上角刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fatal != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { round++ }) { Text("重新生成") }
            }
        }
    }

    // 每一轮：生成二维码 → 轮询扫码状态 → 成功则抓取 Cookie
    androidx.compose.runtime.LaunchedEffect(round) {
        val login = TianyiQrLogin()
        val session = withContext(Dispatchers.IO) { login.start() }
        if (session == null) {
            fatal = "无法获取登录参数，请检查网络后重试"
            return@LaunchedEffect
        }
        val bmp = withContext(Dispatchers.Default) { qrBitmap(session.uuid) }
        qr = bmp
        fatal = null
        phase = "等待扫码…"

        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline) {
            val result = withContext(Dispatchers.IO) { login.poll(session) }
            when (result) {
                is TianyiQrLogin.Poll.Waiting -> phase = "等待扫码…"
                is TianyiQrLogin.Poll.Scanned -> phase = "已扫码，请在手机上确认登录"
                is TianyiQrLogin.Poll.Expired -> {
                    fatal = "二维码已过期，请点右上角刷新"
                    return@LaunchedEffect
                }
                is TianyiQrLogin.Poll.Fail -> {
                    fatal = "登录失败：${result.message}"
                    return@LaunchedEffect
                }
                is TianyiQrLogin.Poll.Success -> {
                    // 优先换 token 会话（不绑定 IP，切换网络仍有效）
                    phase = "登录成功，正在获取会话…"
                    val token = withContext(Dispatchers.IO) {
                        login.exchangeSession(result.redirectUrl)
                    }
                    if (token != null) {
                        onLoggedIn(
                            QrLoginResult(
                                sessionKey = token.sessionKey,
                                sessionSecret = token.sessionSecret,
                                accessToken = token.accessToken,
                                refreshToken = token.refreshToken,
                            )
                        )
                        return@LaunchedEffect
                    }
                    // 回退：抓 Cookie
                    phase = "正在获取 Cookie…"
                    val cookie = withContext(Dispatchers.IO) {
                        login.finishLogin(result.redirectUrl, session.lt, session.reqId)
                    }
                    if (cookie != null) {
                        onLoggedIn(QrLoginResult(cookie = cookie))
                        return@LaunchedEffect
                    } else {
                        val diag = withContext(Dispatchers.IO) { login.cookieDiagnostics() }
                        fatal = "登录已确认，但未取到有效会话。已捕获：$diag，请重试"
                        return@LaunchedEffect
                    }
                }
            }
            delay(2000)
        }
        fatal = "二维码已过期，请点右上角刷新"
    }
}
