package com.cloudtune.player.data.source

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.ScriptableObject
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 脚本初始化后得到的音源元信息。 */
data class ScriptMeta(
    val name: String,
    val author: String,
    val version: String,
    val sources: List<String>,
    val actions: List<String>,
)

/**
 * 洛雪音乐自定义源脚本执行引擎（基于 Mozilla Rhino）。
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
            val bridge = LxBridge(client)
            runScript(script, bridge)
            val data = bridge.initJson
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
            val bridge = LxBridge(client)
            val ctx = ContextFactory().enterContext()
            try {
                ctx.optimizationLevel = -1
                ctx.setClassShutter(ALLOW_BRIDGE_ONLY)
                val scope = ctx.initStandardObjects()
                ScriptableObject.putProperty(scope, "__bridge", RhinoContext.javaToJS(bridge, scope))
                ctx.evaluateString(scope, PRELUDE, "lx-prelude.js", 1, null)
                ctx.evaluateString(scope, script, "source.js", 1, null)

                val jsAction = Json.encodeToString(String.serializer(), action)
                val jsSource = Json.encodeToString(String.serializer(), sourceKey)
                val jsInfo = Json.encodeToString(String.serializer(), infoJson)
                val result = ctx.evaluateString(
                    scope,
                    "__lx_call($jsAction, $jsSource, $jsInfo)",
                    "lx-call.js",
                    1,
                    null,
                )
                val text = RhinoContext.toString(result)
                val element = runCatching { json.parseToJsonElement(text) }.getOrNull()
                val err = (element as? JsonObject)?.get("__error")?.jsonPrimitive?.contentOrNull
                if (err != null) error(err)
                text
            } finally {
                RhinoContext.exit()
            }
        }
    }

    /** 创建 Rhino 作用域并执行前置 shim + 目标脚本。 */
    private fun runScript(script: String, bridge: LxBridge) {
        val ctx = ContextFactory().enterContext()
        try {
            ctx.optimizationLevel = -1
            // 只放行 LxBridge（JS 侧 __bridge），其余 Java 类一律禁止访问
            ctx.setClassShutter(ALLOW_BRIDGE_ONLY)
            val scope = ctx.initStandardObjects()
            ScriptableObject.putProperty(scope, "__bridge", RhinoContext.javaToJS(bridge, scope))
            ctx.evaluateString(scope, PRELUDE, "lx-prelude.js", 1, null)
            ctx.evaluateString(scope, script, "source.js", 1, null)
        } finally {
            RhinoContext.exit()
        }
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

    /** JS 侧 `__bridge` 对象：只暴露轻量网络 / 摘要 / 编码能力。 */
    class LxBridge(private val client: OkHttpClient) {

        @Volatile
        var initJson: String? = null
            private set

        fun send(name: String, dataJson: String) {
            if (name == "inited") initJson = dataJson
        }

        fun http(url: String, optionsJson: String): String {
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

        fun md5(value: String): String = digest("MD5", value)

        fun sha1(value: String): String = digest("SHA-1", value)

        fun base64Encode(value: String): String =
            Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        fun base64Decode(value: String): String = runCatching {
            String(Base64.decode(value, Base64.NO_WRAP), Charsets.UTF_8)
        }.getOrDefault("")

        fun log(message: String) {
            Log.d("LxSource", message)
        }

        private fun digest(algorithm: String, value: String): String {
            val bytes = MessageDigest.getInstance(algorithm).digest(value.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
        }
    }

    private companion object {

        /**
         * 白名单 ClassShutter：只允许 JS 侧的 `__bridge`（即 LxBridge），
         * 第三方脚本无法通过 `Java.type(...)` 访问其他任何 Java 类。
         */
        val ALLOW_BRIDGE_ONLY = ClassShutter { className ->
            className == LxBridge::class.java.name
        }
        /** 洛雪自定义源协议的最小兼容层（含 Promise / globalThis / lx.request 同步桥）。 */
        val PRELUDE = """
            var globalThis = this;
            var __nextId = 1;
            var __handlers = {};

            function LxPromise(executor) {
              var self = this;
              this._state = 0;
              this._value = null;
              this._handlers = [];
              function resolve(v) {
                if (self._state) return;
                if (v && typeof v.then === 'function') { v.then(resolve, reject); return; }
                self._state = 1; self._value = v; self._flush();
              }
              function reject(e) {
                if (self._state) return;
                self._state = 2; self._value = e; self._flush();
              }
              try { executor(resolve, reject); } catch (e) { reject(e); }
            }
            LxPromise.prototype._flush = function () {
              var hs = this._handlers; this._handlers = [];
              for (var i = 0; i < hs.length; i++) this._run(hs[i]);
            };
            LxPromise.prototype._run = function (h) {
              var self = this;
              if (self._state === 1) {
                if (h.onF) { try { h.resolve(h.onF(self._value)); } catch (e) { h.reject(e); } }
                else h.resolve(self._value);
              } else if (self._state === 2) {
                if (h.onR) { try { h.resolve(h.onR(self._value)); } catch (e) { h.reject(e); } }
                else h.reject(self._value);
              } else {
                self._handlers.push(h);
              }
            };
            LxPromise.prototype['then'] = function (onF, onR) {
              var self = this;
              return new LxPromise(function (res, rej) {
                self._handlers.push({ onF: onF, onR: onR, resolve: res, reject: rej });
                if (self._state) self._flush();
              });
            };
            LxPromise.prototype['catch'] = function (onR) { return this['then'](null, onR); };
            LxPromise.resolve = function (v) { return new LxPromise(function (r) { r(v); }); };
            LxPromise.reject = function (e) { return new LxPromise(function (_, j) { j(e); }); };
            LxPromise.all = function (arr) {
              return new LxPromise(function (resolve, reject) {
                var n = arr.length, out = [];
                if (!n) return resolve(out);
                for (var i = 0; i < arr.length; i++) {
                  (function (idx) {
                    LxPromise.resolve(arr[idx])['then'](function (v) {
                      out[idx] = v;
                      if (--n === 0) resolve(out);
                    }, reject);
                  })(i);
                }
              });
            };
            globalThis.Promise = LxPromise;

            var lx = {
              EVENT_NAMES: { inited: 'inited', request: 'request', updateAlert: 'updateAlert' },
              version: '2.0.0',
              send: function (name, data) {
                __bridge.send(name, JSON.stringify(data === undefined ? null : data));
              },
              on: function (name, handler) { __handlers[name] = handler; },
              request: function (url, options, callback) {
                options = options || {};
                var env = JSON.parse(__bridge.http(url, JSON.stringify(options)));
                // 兼容洛雪：既提供 status，也提供 statusCode
                env.statusCode = env.status;
                var ok = env.status >= 200 && env.status < 300;
                var err = ok ? null : new Error(env.error || ('HTTP ' + env.status));
                if (typeof callback === 'function') callback(err, env);
                return new LxPromise(function (resolve, reject) {
                  if (ok) resolve(env); else reject(err);
                });
              },
              utils: {
                crypto: {
                  md5: function (s) { return __bridge.md5(String(s)); },
                  sha1: function (s) { return __bridge.sha1(String(s)); }
                },
                str2b64: function (s) { return __bridge.base64Encode(String(s)); },
                b642str: function (s) { return __bridge.base64Decode(String(s)); }
              }
            };
            globalThis.lx = lx;
            var atob = function (s) { return __bridge.base64Decode(String(s)); };
            var btoa = function (s) { return __bridge.base64Encode(String(s)); };
            var setTimeout = function (fn) { if (typeof fn === 'function') fn(); return 0; };
            var clearTimeout = function (id) { };

            function __lx_call(action, source, infoJson) {
              try {
                var handler = __handlers['request'];
                if (typeof handler !== 'function') {
                  return JSON.stringify({ __error: '音源脚本未实现 request 处理器' });
                }
                var info = {};
                try { info = JSON.parse(infoJson || '{}'); } catch (e) { info = {}; }
                var result = handler({ source: source, action: action, info: info });
                if (result && typeof result['then'] === 'function') {
                  var value, failed = null, done = false;
                  result['then'](function (v) { value = v; done = true; },
                                function (e) { failed = e; done = true; });
                  if (failed) return JSON.stringify({ __error: String(failed) });
                  return JSON.stringify(value === undefined ? null : value);
                }
                return JSON.stringify(result === undefined ? null : result);
              } catch (e) {
                return JSON.stringify({ __error: String(e) });
              }
            }
        """.trimIndent()
    }
}
