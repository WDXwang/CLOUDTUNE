package com.cloudtune.player.core.model

import kotlinx.serialization.Serializable

/**
 * 云端后端类型。
 * - [WEBDAV]：连接自建 AList（或任意 WebDAV 服务），默认且推荐，风险隔离在服务端。
 * - [TIANYI_DIRECT]：App 内直连天翼云盘非官方 Web API（实验性，使用粘贴 Cookie / 扫码会话登录）。
 * - [PAN123]：123 云盘（实验性，账号密码换取 token）。
 * - [QUARK]：夸克网盘（实验性，粘贴浏览器 Cookie）。
 * - [GUANGYA]：光鸭云盘（实验性，账号密码登录换取令牌）。
 */
@Serializable
enum class BackendType { WEBDAV, TIANYI_DIRECT, PAN123, QUARK, GUANGYA }

/** 后端类型的展示名称。 */
val BackendType.displayName: String
    get() = when (this) {
        BackendType.WEBDAV -> "WebDAV / AList"
        BackendType.TIANYI_DIRECT -> "天翼云盘"
        BackendType.PAN123 -> "123 云盘"
        BackendType.QUARK -> "夸克网盘"
        BackendType.GUANGYA -> "光鸭云盘"
    }

/**
 * 服务器 / 账号配置。不同后端只使用其中一部分字段。
 *
 * WebDAV 字段：
 * @param baseUrl 服务器根地址，例如 http://192.168.1.10:5244
 * @param davPath WebDAV 路径，AList 默认为 /dav
 * @param username Basic Auth 用户名（AList 后台用户名）
 * @param password Basic Auth 密码（AList 后台密码）
 * @param rootPath 起始浏览目录（相对 davPath），例如 / 或 /音乐
 *
 * 天翼直连字段：
 * @param tianyiCookie 从浏览器登录 cloud.189.cn 后复制的 Cookie 字符串（legacy，绑定 IP）
 * @param tianyiSessionKey 扫码登录后换到的签名会话 key（推荐，不绑定 IP）
 * @param tianyiSessionSecret 签名会话 secret
 * @param tianyiAccessToken 会话刷新用 token
 * @param tianyiRefreshToken token 续期用 refreshToken
 * @param tianyiRootFolderId 个人云根目录文件夹 ID，默认 -11
 *
 * 123 云盘字段：
 * @param pan123Passport 登录账号（仅用于展示 / 重新登录）
 * @param pan123Token 登录后换到的 JWT（Bearer）
 * @param pan123RootFolderId 起始目录 fileId，根目录为 0
 * @param pan123LoginUuid 请求签名用的设备标识（32 位十六进制，长期复用）
 *
 * 夸克网盘字段：
 * @param quarkCookie 从 pc.quark.cn 登录后复制的 Cookie
 * @param quarkRootFid 起始目录 fid，根目录为 0
 *
 * 光鸭云盘字段：
 * @param guangYaAccount 登录账号（手机号 / 邮箱 / 用户名）
 * @param guangYaAccessToken 登录换到的 access token
 * @param guangYaRefreshToken 令牌续期用 refresh token
 * @param guangYaDeviceId 业务接口 did 头所需的设备 id（32 位十六进制）
 * @param guangYaDeviceSign 账号接口 X-Device-Sign 所需设备签名（wdi10. 开头）
 * @param guangYaRootFolderId 起始目录 id，根目录为空串
 */
@Serializable
data class ServerConfig(
    val backend: BackendType = BackendType.WEBDAV,
    val baseUrl: String = "",
    val davPath: String = "/dav",
    val username: String = "",
    val password: String = "",
    val rootPath: String = "/",
    val tianyiCookie: String = "",
    val tianyiRootFolderId: String = "-11",
    val tianyiSessionKey: String = "",
    val tianyiSessionSecret: String = "",
    val tianyiAccessToken: String = "",
    val tianyiRefreshToken: String = "",
    val pan123Passport: String = "",
    val pan123Token: String = "",
    val pan123RootFolderId: String = "0",
    val pan123LoginUuid: String = "",
    val quarkCookie: String = "",
    val quarkRootFid: String = "0",
    val guangYaAccount: String = "",
    val guangYaAccessToken: String = "",
    val guangYaRefreshToken: String = "",
    val guangYaDeviceId: String = "",
    val guangYaDeviceSign: String = "",
    val guangYaRootFolderId: String = "",
)

/**
 * 一条「存储」记录：用户可以添加多个网盘 / WebDAV，统一在文件页聚合展示。
 *
 * @param id 本地生成的唯一标识（UUID），用于把目录项 / 收藏路由回对应数据源
 * @param name 用户自定义显示名，例如「家里的 AList」「天翼主号」
 * @param enabled 停用后不在聚合首页出现，也不再参与跨盘搜索
 */
@Serializable
data class StorageAccount(
    val id: String,
    val name: String,
    val config: ServerConfig,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
)

/**
 * 云端文件 / 文件夹的统一抽象。
 *
 * id 的含义随后端不同：
 * - WebDAV：相对 davPath 的完整 href 路径，例如 /音乐/专辑/01.mp3
 * - 天翼直连：文件（夹）数字 ID
 * - 123 云盘 / 夸克网盘：平台侧 fileId / fid
 *
 * [accountId] / [accountName] 记录该项属于哪一条存储（多网盘聚合后用于把播放请求、
 * 收藏路由回正确的数据源）；本地文件与历史遗留数据可能为 null。
 */
@Serializable
data class CloudFile(
    val id: String,
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String? = null,
    val isLocal: Boolean = false,
    val accountId: String? = null,
    val accountName: String? = null,
) {
    /** 是否为可播放的音乐文件（按扩展名判断） */
    fun isMusic(): Boolean = !isDir && com.cloudtune.player.core.util.MusicUtils.isMusicFile(name)

    /** 是否为可播放的视频文件（按扩展名判断） */
    fun isVideo(): Boolean = !isDir && com.cloudtune.player.core.util.VideoUtils.isVideoFile(name)
}

/**
 * 一首可播放的音轨（界面与播放器使用）。
 */
@Serializable
data class MusicTrack(
    val file: CloudFile,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
)

/**
 * 已解析的播放源：真实可请求的 URL 与所需请求头。
 *
 * 直链通常有较短有效期，因此应“播放前现取现用”，不要长期缓存。
 */
data class StreamSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
)
