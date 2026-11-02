package com.cloudtune.player.data.source

import android.util.Base64
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** 脚本初始化后得到的音源元信息。 */
data class ScriptMeta(
    val name: String,
    val author: String,
    val version: String,
    val sources: List<String>,
    val actions: List<String>,
)

/**
 * 洛雪音乐自定义源脚本执行引擎（基于 QuickJS，完整支持 ES2020：async/await、
 * 解构、可选链、原生 Promise）。
 *
 * 兼容 `globalThis.lx` 协议：脚本可调用 `lx.send / lx.on / lx.request`，
 * 我们额外支持扩展 action `leaderboard`（榜单）与 `search`（搜索），
 * 具体由脚本自行实现，脚本未实现时界面上会给出提示。
 */
class LxScriptEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 读取脚本元信息（执行一次脚本，捕获 inited 事件）。 */
    suspend fun inspect(script: String): Result<ScriptMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val inited = AtomicReference<String?>(null)
            val failed = AtomicReference<String?>(null)
            val completed = withTimeoutOrNull(EVAL_TIMEOUT_MS) {
                quickJs {
                    defineBridge(
                        onInited = { inited.set(it) },
                        onDone = {},
                        onError = { failed.set(it) },
                    )
                    evaluate<Any?>(buildString {
                        append(PRELUDE)
                        append('\n')
                        append(script)
                    })
                    flushUntil { inited.get() != null || failed.get() != null }
                }
                true
            }
            if (completed == null) error("音源脚本初始化超时")
            failed.get()?.let { error(it) }
            val data = inited.get()
                ?: error("脚本未返回初始化信息（未调用 lx.send(inited, ...)）")
            parseMeta(data)
        }
    }

    /**
     * 调用脚本的 request 处理器。
     *
     * @param action `musicUrl` / `lyric` / `pic` / `leaderboard` / `search`
     * @return 脚本返回值的 JSON 文本
     */
    suspend fun call(
        script: String,
        action: String,
        sourceKey: String,
        infoJson: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val done = AtomicReference<String?>(null)
            val failed = AtomicReference<String?>(null)
            val completed = withTimeoutOrNull(EVAL_TIMEOUT_MS) {
                quickJs {
                    defineBridge(
                        onInited = {},
                        onDone = { done.set(it) },
                        onError = { failed.set(it) },
                    )
                    val jsAction = Json.encodeToString(String.serializer(), action)
                    val jsSource = Json.encodeToString(String.serializer(), sourceKey)
                    val jsInfo = Json.encodeToString(String.serializer(), infoJson)
                    evaluate<Any?>(buildString {
                        append(PRELUDE)
                        append('\n')
                        append(script)
                        append("\n__lx_call(")
                        append(jsAction).append(", ")
                        append(jsSource).append(", ")
                        append(jsInfo).append(");")
                    })
                    flushUntil { done.get() != null || failed.get() != null }
                }
                true
            }
            if (completed == null) error("音源脚本执行超时")
            failed.get()?.let { error(it) }
            val raw = done.get() ?: error("音源脚本未返回结果")
            val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            val err = (element as? JsonObject)?.get("__error")?.jsonPrimitive?.contentOrNull
            if (err != null) error(err)
            raw
        }
    }

    /** 等待脚本内部的异步任务（Promise 微任务）全部落定。 */
    private suspend fun QuickJs.flushUntil(predicate: () -> Boolean) {
        var tries = 0
        while (!predicate() && tries < FLUSH_TRIES) {
            delay(FLUSH_INTERVAL_MS)
            // 每次求值都会推进 QuickJS 的微任务队列
            evaluate<Any?>("void 0")
            tries++
        }
    }

    /** 向 JS 注入同步桥 `__bridge`：只暴露网络 / 摘要 / 编码能力。 */
    private suspend fun QuickJs.defineBridge(
        onInited: (String) -> Unit,
        onDone: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        define("__bridge") {
            function("send") { args ->
                val name = args.getOrNull(0)?.toString().orEmpty()
                val data = args.getOrNull(1)?.toString().orEmpty()
                if (name == "inited") onInited(data)
                null
            }
            function("done") { args -> onDone(args.getOrNull(0)?.toString().orEmpty()); null }
            function("fail") { args -> onError(args.getOrNull(0)?.toString().orEmpty()); null }
            function("http") { args ->
                http(args.getOrNull(0)?.toString().orEmpty(), args.getOrNull(1)?.toString().orEmpty())
            }
            function("md5") { args -> digest("MD5", args.getOrNull(0)?.toString().orEmpty()) }
            function("sha1") { args -> digest("SHA-1", args.getOrNull(0)?.toString().orEmpty()) }
            function("base64Encode") { args ->
                Base64.encodeToString(
                    args.getOrNull(0)?.toString().orEmpty().toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
            }
            function("base64Decode") { args ->
                runCatching {
                    String(
                        Base64.decode(args.getOrNull(0)?.toString().orEmpty(), Base64.NO_WRAP),
                        Charsets.UTF_8,
                    )
                }.getOrDefault("")
            }
            function("log") { args ->
                Log.d("LxSource", args.joinToString(" "))
                null
            }
        }
    }

    private fun http(url: String, optionsJson: String): String {
        return runCatching {
            val options = runCatching { Json.parseToJsonElement(optionsJson).jsonObject }.getOrNull()
            val bodyText = (options?.get("body") as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            val method = options?.get("method")?.jsonPrimitive?.contentOrNull
                ?.uppercase(Locale.US)
                ?: if (bodyText != null) "POST" else "GET"
            val headers = options?.get("headers")?.jsonObject

            val builder = Request.Builder().url(url)
            headers?.forEach { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { builder.header(k, it) }
            }
            if (method != "GET" && method != "HEAD") {
                builder.method(method, (bodyText ?: "").toRequestBody("application/json".toMediaTypeOrNull()))
            }
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val headerJson = headersOf(resp)
                buildString {
                    append("{\"status\":").append(resp.code)
                    append(",\"body\":").append(Json.encodeToString(String.serializer(), body))
                    append(",\"headers\":").append(headerJson)
                    append("}")
                }
            }
        }.getOrElse { e ->
            Log.w("LxSource", "request failed: $url", e)
            "{\"status\":0,\"body\":\"\",\"headers\":{},\"error\":${
                Json.encodeToString(String.serializer(), e.message ?: "网络请求失败")
            }}"
        }
    }

    private fun headersOf(resp: okhttp3.Response): String {
        val items = resp.headers.names().joinToString(",") { key ->
            val value = resp.headers.values(key).joinToString(",")
            "${Json.encodeToString(String.serializer(), key)}:${
                Json.encodeToString(String.serializer(), value)
            }"
        }
        return "{$items}"
    }

    private fun digest(algorithm: String, value: String): String {
        val bytes = MessageDigest.getInstance(algorithm).digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    private fun parseMeta(raw: String): ScriptMeta {
        val root = json.parseToJsonElement(raw).jsonObject
        val info = (root["sourceInfo"] as? JsonObject)
            ?: error("音源脚本缺少 sourceInfo")
        val name = info["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: "未命名音源"
        val author = info["author"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val version = info["version"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val sourceKeys = info["sources"]?.jsonObject?.keys?.toList().orEmpty()
        val actions = info["sources"]?.jsonObject?.values
            ?.mapNotNull { (it as? JsonObject)?.get("actions") as? JsonArray }
            ?.flatMap { arr -> arr.mapNotNull { it.jsonPrimitive.contentOrNull } }
            ?.distinct()
            .orEmpty()
        return ScriptMeta(name, author, version, sourceKeys, actions)
    }

    private companion object {
        const val EVAL_TIMEOUT_MS = 20_000L
        const val FLUSH_TRIES = 40
        const val FLUSH_INTERVAL_MS = 100L

        /** 洛雪自定义源协议兼容层（原生 Promise，无需自实现 Promise）。 */
        val PRELUDE = """
            var __handlers = {};
            var __lx = {
              EVENT_NAMES: { inited: 'inited', request: 'request', updateAlert: 'updateAlert' },
              version: '2.0.0',
              send: function (name, data) {
                __bridge.send(name, JSON.stringify(data === undefined ? null : data));
              },
              on: function (name, handler) { __handlers[name] = handler; },
              request: function (url, options, callback) {
                options = options || {};
                var env;
                try {
                  env = JSON.parse(__bridge.http(url, JSON.stringify(options)));
                } catch (e) {
                  env = { status: 0, body: '', headers: {}, error: '网络请求失败' };
                }
                env.statusCode = env.status;
                var ok = env.status >= 200 && env.status < 300;
                var error = ok ? null : new Error(env.error || ('HTTP ' + env.status));
                if (typeof callback === 'function') callback(error, env);
                var promise = ok ? Promise.resolve(env) : Promise.reject(error);
                // 标记为已处理，避免调用方只用 callback 时产生 unhandled rejection
                promise.catch(function () {});
                return promise;
              },
              utils: {
                crypto: {
                  md5: function (s) { return __bridge.md5(String(s)); },
                  sha1: function (s) { return __bridge.sha1(String(s)); }
                },
                str2b64: function (s) { return __bridge.base64Encode(String(s)); },
                b642str: function (s) { return __bridge.base64Decode(String(s)); },
                buffer: {
                  bufToString: function (b) { return String(b); },
                  strToBuf: function (s) { return String(s); }
                }
              }
            };
            globalThis.lx = __lx;
            globalThis.atob = function (s) { return __bridge.base64Decode(String(s)); };
            globalThis.btoa = function (s) { return __bridge.base64Encode(String(s)); };
            // 我们的网络桥是同步的，所以 >0 的延时定时器永不触发（仅保留超时保护语义）
            globalThis.setTimeout = function (fn, ms) {
              if (typeof fn === 'function' && (!ms || ms <= 0)) Promise.resolve().then(fn);
              return 0;
            };
            globalThis.clearTimeout = function () {};
            globalThis.setInterval = function () { return 0; };
            globalThis.clearInterval = function () {};

            function __lx_call(action, source, infoJson) {
              try {
                var handler = __handlers['request'];
                if (typeof handler !== 'function') {
                  __bridge.done(JSON.stringify({ __error: '音源脚本未实现 request 处理器' }));
                  return;
                }
                var info = {};
                try { info = JSON.parse(infoJson || '{}'); } catch (e) { info = {}; }
                Promise.resolve(handler({ source: source, action: action, info: info })).then(
                  function (value) {
                    __bridge.done(JSON.stringify(value === undefined ? null : value));
                  },
                  function (e) {
                    __bridge.done(JSON.stringify({ __error: String(e && e.message ? e.message : e) }));
                  }
                );
              } catch (e) {
                __bridge.done(JSON.stringify({ __error: String(e && e.message ? e.message : e) }));
              }
            }
        """.trimIndent()
    }
}
