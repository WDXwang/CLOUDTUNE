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
import kotlinx.serialization.json.JsonPrimitive
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
    val sourceActions: Map<String, List<String>> = emptyMap(),
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
            val evalError = AtomicReference<String?>(null)
            val completed = withTimeoutOrNull(EVAL_TIMEOUT_MS) {
                quickJs {
                    defineBridge(
                        onInited = { inited.set(it) },
                        onDone = {},
                        onError = { failed.set(it) },
                    )
                    runCatching { evaluate<Any?>(buildProgram(script)) }
                        .onFailure { evalError.set(it.message ?: it.toString()) }
                    flushUntil { inited.get() != null || failed.get() != null }
                }
                true
            }
            if (completed == null) error("音源脚本初始化超时")
            failed.get()?.let { error(it) }
            // 脚本可能先发 inited 再抛错（例如日志输出异常），此时仍视为导入成功
            val data = inited.get()
                ?: error(evalError.get() ?: "脚本未返回初始化信息（未调用 lx.send(inited, ...)）")
            parseMeta(data, script)
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
            val evalError = AtomicReference<String?>(null)
            val completed = withTimeoutOrNull(EVAL_TIMEOUT_MS) {
                quickJs {
                    defineBridge(
                        onInited = {},
                        onDone = { done.set(it) },
                        onError = { failed.set(it) },
                    )
                    val tail = buildString {
                        append("\n__lx_call(")
                        append(quote(action)).append(", ")
                        append(quote(sourceKey)).append(", ")
                        append(quote(infoJson)).append(");")
                    }
                    runCatching { evaluate<Any?>(buildProgram(script, tail)) }
                        .onFailure { evalError.set(it.message ?: it.toString()) }
                    flushUntil { done.get() != null || failed.get() != null }
                }
                true
            }
            if (completed == null) error("音源脚本执行超时")
            failed.get()?.let { error(it) }
            val raw = done.get()
                ?: error(evalError.get() ?: "音源脚本未返回结果")
            val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            val err = (element as? JsonObject)?.get("__error")?.jsonPrimitive?.contentOrNull
            if (err != null) error(err)
            raw
        }
    }

    /**
     * 拼装一次求值的完整源码：脚本信息 → 协议兼容层 → 脚本正文 → 尾调用。
     *
     * 脚本信息先注入，兼容层里的 `lx.currentScriptInfo` 才能在脚本读取时可用
     * （部分脚本靠它从头部注释解析 Cookie 等配置）。
     */
    private fun buildProgram(script: String, tail: String = ""): String = buildString {
        append("globalThis.__scriptInfo = ").append(scriptInfoJson(script)).append(";\n")
        append(PRELUDE)
        append('\n')
        append(script)
        append(tail)
    }

    /** 等待脚本内部的异步任务（Promise 微任务 / 定时器）落定。 */
    private suspend fun QuickJs.flushUntil(predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + FLUSH_TIMEOUT_MS
        while (!predicate()) {
            if (System.currentTimeMillis() >= deadline) break
            runCatching { evaluate<Any?>("__runTimers(); void 0") }
            if (predicate()) break
            delay(FLUSH_INTERVAL_MS)
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
            val bodyText = (options?.get("body") as? JsonPrimitive)?.contentOrNull
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
                    append(",\"statusCode\":").append(resp.code)
                    append(",\"body\":").append(quote(body))
                    append(",\"headers\":").append(headerJson)
                    append("}")
                }
            }
        }.getOrElse { e ->
            Log.w("LxSource", "request failed: $url", e)
            "{\"status\":0,\"statusCode\":0,\"body\":\"\",\"headers\":{},\"error\":${
                quote(e.message ?: "网络请求失败")
            }}"
        }
    }

    private fun headersOf(resp: okhttp3.Response): String {
        val items = resp.headers.names().joinToString(",") { key ->
            val value = resp.headers.values(key).joinToString(",")
            "${quote(key)}:${quote(value)}"
        }
        return "{$items}"
    }

    private fun digest(algorithm: String, value: String): String {
        val bytes = MessageDigest.getInstance(algorithm).digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
    }

    /**
     * 解析 inited 事件数据。洛雪协议结构为：
     * `{ status: true, openDevTools: false, sources: { [sourceKey]: { name, type, actions, qualitys } } }`
     */
    private fun parseMeta(raw: String, script: String): ScriptMeta {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: error("音源脚本初始化返回的数据不是合法 JSON")
        val status = (root["status"] as? JsonPrimitive)?.contentOrNull
        if (status == "false") {
            val message = root.str("message", "msg")
            error(message.ifBlank { "音源脚本初始化失败（status: false）" })
        }
        val legacy = root["sourceInfo"] as? JsonObject
        val sources = (root["sources"] as? JsonObject)
            ?: (legacy?.get("sources") as? JsonObject)
            ?: error("音源脚本未注册任何音源（inited 事件缺少 sources）")

        val header = headerMeta(script)
        val firstSourceName = sources.values.firstNotNullOfOrNull { value ->
            (value as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        val name = root.str("name").ifBlank {
            legacy?.str("name").orEmpty()
        }.ifBlank {
            header["name"].orEmpty()
        }.ifBlank {
            firstSourceName ?: "未命名音源"
        }
        val author = header["author"].orEmpty().ifBlank { legacy?.str("author").orEmpty() }
        val version = header["version"].orEmpty().ifBlank { legacy?.str("version").orEmpty() }

        val perSourceActions = sources.entries.associate { (key, value) ->
            key to ((value as? JsonObject)?.get("actions") as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty()
        }
        val actions = perSourceActions.values.flatten().distinct()

        return ScriptMeta(
            name = name,
            author = author,
            version = version,
            sources = sources.keys.toList(),
            actions = actions,
            sourceActions = perSourceActions,
        )
    }

    /** 从脚本头部注释（`@name` / `@author` / `@version`）读取元信息。 */
    private fun headerMeta(script: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        HEADER_TAG.findAll(script).forEach { match ->
            val key = match.groupValues[1].lowercase(Locale.US)
            if (key !in result) result[key] = match.groupValues[2].trim()
        }
        return result
    }

    /** `globalThis.__scriptInfo` 的内容，供 `lx.currentScriptInfo` 使用。 */
    private fun scriptInfoJson(script: String): String {
        val header = headerMeta(script)
        return buildString {
            append("{\"name\":").append(quote(header["name"].orEmpty()))
            append(",\"author\":").append(quote(header["author"].orEmpty()))
            append(",\"version\":").append(quote(header["version"].orEmpty()))
            append(",\"rawScript\":").append(quote(script))
            append("}")
        }
    }

    private fun JsonObject.str(vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private fun quote(value: String): String = Json.encodeToString(String.serializer(), value)

    private companion object {
        const val EVAL_TIMEOUT_MS = 20_000L
        const val FLUSH_TIMEOUT_MS = 19_000L
        const val FLUSH_INTERVAL_MS = 60L

        val HEADER_TAG = Regex("@(name|version|author)\\s+([^\\r\\n*]+)")

        /** 洛雪自定义源协议兼容层（原生 Promise / 原生定时器队列）。 */
        val PRELUDE = """
            var __handlers = {};
            var __timers = [];
            var __timerId = 1;

            function __fmt(value) {
              if (typeof value === 'string') return value;
              if (value === null || value === undefined) return String(value);
              try { return JSON.stringify(value); } catch (e) { return String(value); }
            }

            globalThis.console = {
              log: function () { __bridge.log(Array.prototype.map.call(arguments, __fmt).join(' ')); },
              info: function () { __bridge.log(Array.prototype.map.call(arguments, __fmt).join(' ')); },
              warn: function () { __bridge.log('[warn] ' + Array.prototype.map.call(arguments, __fmt).join(' ')); },
              error: function () { __bridge.log('[error] ' + Array.prototype.map.call(arguments, __fmt).join(' ')); },
              debug: function () { __bridge.log(Array.prototype.map.call(arguments, __fmt).join(' ')); }
            };

            var __lx = {
              EVENT_NAMES: { inited: 'inited', request: 'request', updateAlert: 'updateAlert' },
              version: '2.0.0',
              env: { platform: 'android', os: 'android' },
              currentScriptInfo: globalThis.__scriptInfo || null,
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
                  env = { status: 0, statusCode: 0, body: '', headers: {}, error: '网络请求失败' };
                }
                var status = env.status === undefined ? env.statusCode : env.status;
                env.status = status;
                env.statusCode = status;
                var ok = status >= 200 && status < 300;
                var error = ok ? null : new Error(env.error || ('HTTP ' + status));
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
                  bufToString: function (b) {
                    return typeof b === 'string' ? b : String(b);
                  },
                  strToBuf: function (s) { return String(s); }
                }
              }
            };
            globalThis.lx = __lx;
            globalThis.atob = function (s) { return __bridge.base64Decode(String(s)); };
            globalThis.btoa = function (s) { return __bridge.base64Encode(String(s)); };
            globalThis.queueMicrotask = function (fn) { Promise.resolve().then(fn); };

            // 定时器队列：由宿主在等待循环里驱动 __runTimers()，因此真正的延时有效
            globalThis.setTimeout = function (fn, ms) {
              var id = __timerId++;
              __timers.push({
                id: id,
                due: Date.now() + (typeof ms === 'number' && ms > 0 ? ms : 0),
                fn: fn,
                args: Array.prototype.slice.call(arguments, 2)
              });
              return id;
            };
            globalThis.clearTimeout = function (id) {
              for (var i = 0; i < __timers.length; i++) {
                if (__timers[i].id === id) { __timers.splice(i, 1); return; }
              }
            };
            globalThis.setInterval = function (fn, ms) { return globalThis.setTimeout(fn, ms); };
            globalThis.clearInterval = globalThis.clearTimeout;

            function __runTimers() {
              var now = Date.now();
              var due = [];
              var rest = [];
              for (var i = 0; i < __timers.length; i++) {
                (now >= __timers[i].due ? due : rest).push(__timers[i]);
              }
              if (due.length === 0) return;
              __timers = rest;
              for (var j = 0; j < due.length; j++) {
                try {
                  if (typeof due[j].fn === 'function') due[j].fn.apply(null, due[j].args);
                } catch (e) { }
              }
            }

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
