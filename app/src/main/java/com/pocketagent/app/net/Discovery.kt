package com.pocketagent.app.net

import com.pocketagent.app.data.DiscoveredModel
import com.pocketagent.app.data.DiscoveryOutcome
import com.pocketagent.app.data.FailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLException

/**
 * 拉取 GET {base}/models，归一化三种响应结构，再按模态分类。
 * 超时 8s，仅对网络类错误重试 1 次（500ms 退避 + 抖动）。
 */
class Discovery {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun run(
        baseUrlRaw: String,
        apiKey: String,
        modelsPath: String = "/models",
    ): DiscoveryOutcome = withContext(Dispatchers.IO) {
        val normalized = UrlNormalizer.normalize(baseUrlRaw)
        if (normalized.url.isEmpty()) {
            return@withContext DiscoveryOutcome.Failure(FailureKind.BAD_REQUEST, "Base URL 不能为空")
        }
        val url = UrlNormalizer.modelsUrl(normalized.url, modelsPath)
        val key = apiKey.trim()

        if (key.isEmpty()) {
            return@withContext DiscoveryOutcome.Failure(FailureKind.AUTH, "API Key 不能为空")
        }

        var attempt = 0
        var lastFailure: DiscoveryOutcome.Failure? = null

        while (attempt < 2) {
            attempt++
            when (val r = requestOnce(url, key)) {
                is DiscoveryOutcome.Success -> return@withContext r
                is DiscoveryOutcome.Failure -> {
                    lastFailure = r
                    val retriable = r.kind == FailureKind.TIMEOUT ||
                        r.kind == FailureKind.NETWORK ||
                        r.kind == FailureKind.SERVER
                    if (!retriable || attempt >= 2) return@withContext r
                    delay(500L + (0..250).random().toLong())
                }
            }
        }
        return@withContext lastFailure
            ?: DiscoveryOutcome.Failure(FailureKind.UNKNOWN, "识别失败")
    }

    private fun requestOnce(url: String, key: String): DiscoveryOutcome {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PocketAgent/0.1")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val body = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }.getOrElse { "" }
                val snippet = body.replace(Regex("\\s+"), " ").take(180)
                return when (code) {
                    401, 403 -> DiscoveryOutcome.Failure(
                        FailureKind.AUTH, "Key 无效或已过期", "HTTP $code · $snippet"
                    )
                    404 -> DiscoveryOutcome.Failure(
                        FailureKind.NOT_FOUND, "端点写错，或该厂商没有 /models", "HTTP 404 · $snippet"
                    )
                    400 -> {
                        val hint = if (url.contains("/v1/v1")) "base URL 里重复了 /v1" else "参数不被接受"
                        DiscoveryOutcome.Failure(FailureKind.BAD_REQUEST, hint, "HTTP 400 · $snippet")
                    }
                    429 -> DiscoveryOutcome.Failure(
                        FailureKind.RATE_LIMIT, "请求太频繁，稍后再试", "HTTP 429"
                    )
                    in 500..599 -> DiscoveryOutcome.Failure(
                        FailureKind.SERVER, "厂商服务暂时不可用", "HTTP $code"
                    )
                    else -> DiscoveryOutcome.Failure(
                        FailureKind.UNKNOWN, "识别失败（HTTP $code）", snippet
                    )
                }
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            parse(text)
        } catch (e: SocketTimeoutException) {
            DiscoveryOutcome.Failure(FailureKind.TIMEOUT, "连接超时，检查网络或代理")
        } catch (e: SSLException) {
            DiscoveryOutcome.Failure(FailureKind.TLS, "该地址的证书不被信任", e.message.orEmpty())
        } catch (e: IOException) {
            val msg = e.message.orEmpty()
            if (msg.contains("ECONNREFUSED", true) || msg.contains("Connection refused", true)) {
                DiscoveryOutcome.Failure(FailureKind.NETWORK, "连接被拒绝，服务可能没启动")
            } else if (msg.contains("EPERM", true)) {
                DiscoveryOutcome.Failure(
                    FailureKind.NETWORK, "局域网访问被系统拦截，需授予「附近设备」权限", msg
                )
            } else {
                DiscoveryOutcome.Failure(FailureKind.NETWORK, "网络错误：$msg")
            }
        } finally {
            conn?.disconnect()
        }
    }

    // ─────────── 三种响应结构归一化 ───────────

    private fun parse(text: String): DiscoveryOutcome {
        val root: JsonElement = runCatching { json.parseToJsonElement(text) }
            .getOrElse {
                return DiscoveryOutcome.Failure(FailureKind.UNKNOWN, "返回内容不是合法 JSON")
            }

        val items: List<JsonElement> = when {
            root is JsonArray -> root.toList()
            root is JsonObject && root["data"] is JsonArray -> root["data"]!!.jsonArray.toList()
            root is JsonObject && root["models"] is JsonArray -> root["models"]!!.jsonArray.toList()
            else -> emptyList()
        }

        if (items.isEmpty()) {
            return DiscoveryOutcome.Failure(
                FailureKind.EMPTY, "该厂商不下发模型列表", "返回 200 但没有 data / models 字段"
            )
        }

        val models = items.mapNotNull { it.toDiscovered() }.distinctBy { it.modelId }
            .sortedBy { it.modelId }

        return if (models.isEmpty()) {
            DiscoveryOutcome.Failure(FailureKind.EMPTY, "厂商返回了列表，但没有可解析的模型 ID")
        } else {
            DiscoveryOutcome.Success(models)
        }
    }

    private fun JsonElement.toDiscovered(): DiscoveredModel? {
        val obj = this as? JsonObject ?: return null

        var id = obj.str("id")
        if (id.isNullOrBlank()) id = obj.str("name")?.removePrefix("models/")
        if (id.isNullOrBlank()) return null

        val display = obj.str("display_name") ?: obj.str("displayName") ?: ""

        // 第 1 步：响应里的显式字段
        val explicit = listOf("type", "modality", "mode", "kind")
            .mapNotNull { k -> obj.str(k) }
            .joinToString(" ")
            .lowercase()

        val capText = runCatching { obj["capabilities"]?.toString().orEmpty().lowercase() }
            .getOrDefault("")

        val marker = "$explicit $capText"

        val mediaKind = when {
            marker.contains("video") -> "video"
            marker.contains("image") -> "image"
            marker.contains("audio") || marker.contains("speech") || marker.contains("asr") -> "audio"
            marker.contains("embedding") || marker.contains("rerank") -> "other"
            else -> ""
        }

        if (mediaKind.isNotEmpty()) {
            return DiscoveredModel(id, display.ifBlank { id }, isChat = false, modality = mediaKind)
        }

        // 第 2 步：ID 启发式
        val lower = id.lowercase()
        val mediaByPattern = when {
            lower.contains("video") || lower.contains("sora") -> "video"
            lower.contains("image") || Regex("(^|[-_/])img").containsMatchIn(lower) -> "image"
            lower.contains("tts") || lower.contains("whisper") || lower.contains("asr") -> "audio"
            lower.contains("embed") || lower.contains("rerank") -> "other"
            lower.contains("moderation") -> "other"
            else -> ""
        }
        if (mediaByPattern.isNotEmpty()) {
            return DiscoveredModel(id, display.ifBlank { id }, isChat = false, modality = mediaByPattern)
        }

        // 第 3、4 步：默认按对话处理（误判为对话的代价 < 误判为媒体的代价）
        val note = deprecationNote(lower)
        val recommended = lower.contains("3.0-flash") || lower.endsWith("-pro") || lower.contains("gpt-5")
        return DiscoveredModel(
            modelId = id,
            displayName = display.ifBlank { id },
            isChat = true,
            note = note,
            recommended = recommended && note.isEmpty(),
        )
    }

    /** 已下线模型提示。/models 不会告诉你这个，只能手工维护，随厂商公告更新。 */
    private fun deprecationNote(lowerId: String): String = when (lowerId) {
        "mimo-v2.5-pro", "mimo-v2.5" -> "已于 2026-10-21 下线"
        else -> ""
    }
}

/** JsonNull / 非字符串一律返回 null，避免 jsonPrimitive 抛异常 */
private fun JsonObject.str(key: String): String? {
    val el = this[key] as? kotlinx.serialization.json.JsonPrimitive ?: return null
    return runCatching { el.content }.getOrNull()?.takeIf { it.isNotBlank() }
}
