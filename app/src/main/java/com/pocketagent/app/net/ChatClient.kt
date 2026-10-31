package com.pocketagent.app.net

import android.os.SystemClock
import com.pocketagent.app.agent.ToolCall
import com.pocketagent.app.agent.ToolCallBuilder
import com.pocketagent.app.data.FailureKind
import com.pocketagent.app.data.ThinkingStyle
import com.pocketagent.app.data.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLException

/**
 * OpenAI 兼容的流式对话（SSE）。
 * 用 Flow 而不是回调：回调在 IO 线程里改 Compose 状态是隐患，Flow + flowOn 让收集端固定在主线程。
 */
class ChatClient {

    /**
     * 一条要发给厂商的消息。
     *
     * 三种形态，靠字段区分（不带任何多余字段，避免严格校验的厂商 400）：
     *  - 普通对话：role + content
     *  - 带图对话：role=user + content 是 parts 数组（text + 若干 image_url）
     *  - 模型的工具请求：role=assistant + [toolCalls]（content 可能是空串）
     *  - 工具结果回灌：role=tool + [toolCallId] + content
     */
    data class Message(
        val role: String,
        val content: String,
        val toolCalls: List<ToolCall> = emptyList(),
        val toolCallId: String = "",
        /**
         * 图片（`data:image/jpeg;base64,...`）。非空时 content 发成 parts 数组，
         * 这是 OpenAI 兼容协议里唯一能让模型"真正看到图"的形态；
         * 直接塞字符串的话，模型收到的只是一段 base64 文本。
         */
        val images: List<String> = emptyList(),
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("role", role)
            when {
                role == "tool" -> {
                    put("content", content)
                    if (toolCallId.isNotBlank()) put("tool_call_id", toolCallId)
                }
                images.isNotEmpty() && role == "user" -> {
                    // parts 数组：文字在前、图在后。空文字也保留一个 text part，
                    // 极少数网关不接受"只有图片"的消息
                    put("content", buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", content.ifBlank { "请看这几张图。" })
                        })
                        images.forEach { url ->
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject { put("url", url) })
                            })
                        }
                    })
                }
                toolCalls.isNotEmpty() -> {
                    // 只发工具调用时 content 必须存在（部分厂商不给会 400），空串最通用
                    put("content", content)
                    put("tool_calls", buildJsonArray {
                        toolCalls.forEach { c ->
                            add(buildJsonObject {
                                put("id", c.id.ifBlank { "call_" + c.name })
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", c.name)
                                    put("arguments", c.arguments.ifBlank { "{}" })
                                })
                            })
                        }
                    })
                }
                else -> put("content", content)
            }
        }
    }

    sealed interface Event {
        data class Delta(val text: String) : Event

        /**
         * 推理/思考内容（独立于正文）。
         *
         * 各家字段名不一样：DeepSeek 与多数 OpenAI 兼容实现用 `reasoning_content`，
         * OpenRouter / 部分网关用 `reasoning`，两个都收。
         * 这类内容**不进正文、不进请求历史**，只用于界面展示"它在想什么"。
         */
        data class Reasoning(val text: String) : Event

        /**
         * 一轮结束。[calls] 非空表示模型要调工具：这一轮的正文可能很短甚至为空，
         * 绝不能因为"没文字"就判成失败 —— 那是正常的一次工具请求。
         */
        data class Done(
            val full: String,
            val usage: Usage,
            val calls: List<ToolCall> = emptyList(),
            val reasoning: String = "",
        ) : Event

        data class Failed(val kind: FailureKind, val message: String, val detail: String = "") : Event

        /**
         * 厂商不认识 tools 字段（400）。
         * 这不是错误而是能力差异：去掉 tools 重发一次，让对话继续，同时让 UI 提示一句。
         */
        data object ToolsUnsupported : Event
    }

    /**
     * 一次性补全（非流式）。翻译这类场景不需要逐字输出：
     * 走非流式省掉 SSE 解析，也不会让用户看到半句译文。
     */
    suspend fun complete(
        baseUrlRaw: String,
        apiKey: String,
        modelId: String,
        messages: List<Message>,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val base = UrlNormalizer.normalize(baseUrlRaw).url
            if (base.isEmpty()) error("Base URL 为空")
            val url = UrlNormalizer.chatUrl(base)

            val payload = buildJsonObject {
                put("model", modelId)
                put("stream", false)
                put("temperature", 0.2)
                put("messages", buildJsonArray {
                    messages.forEach { m -> add(m.toJson()) }
                })
            }.toString()

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("User-Agent", "PocketAgent/0.1")
            }
            try {
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val body = runCatching {
                        conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                    }.getOrDefault("")
                    val snippet = body.replace(Regex("\\s+"), " ").take(160)
                    error(if (code == 429) "请求太频繁（429）" else "HTTP $code · $snippet")
                }
                val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
                    ?: error("返回内容不是 JSON")
                val content = root["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")
                    ?.let { el -> if (el is JsonPrimitive && el.isString) el.content else null }
                if (content.isNullOrBlank()) error("模型返回了空内容")
                content.trim()
            } finally {
                conn.disconnect()
            }
        }
    }

    fun stream(
        baseUrlRaw: String,
        apiKey: String,
        modelId: String,
        thinkingStyle: ThinkingStyle,
        thinkingParam: String?,
        messages: List<Message>,
        tools: List<JsonObject> = emptyList(),
    ): Flow<Event> = flow {
        val base = UrlNormalizer.normalize(baseUrlRaw).url
        if (base.isEmpty()) {
            emit(Event.Failed(FailureKind.BAD_REQUEST, "Base URL 为空"))
            return@flow
        }
        val url = UrlNormalizer.chatUrl(base)

        // 必须在 buildPayload 之前声明：局部函数只能捕获它**前面**的变量
        var toolsSent = true

        fun buildPayload(includeUsage: Boolean): String = buildJsonObject {
            put("model", modelId)
            put("stream", true)
            // 让厂商在最后一个 chunk 带上 usage。严格校验字段的厂商可能 400，
            // 下面会在拿到 400 且报错提到 stream_options 时自动去掉重发一次
            if (includeUsage) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }
            put("messages", buildJsonArray {
                messages.forEach { m -> add(m.toJson()) }
            })
            if (tools.isNotEmpty() && toolsSent) {
                put("tools", buildJsonArray { tools.forEach { add(it) } })
                // auto：模型自己决定调不调；写死 required 会让它在纯聊天时也硬调一个工具
                put("tool_choice", "auto")
            }
            if (!thinkingParam.isNullOrBlank()) {
                when (thinkingStyle) {
                    ThinkingStyle.EFFORT -> put("reasoning_effort", thinkingParam)
                    ThinkingStyle.TIER -> put("thinking_level", thinkingParam)
                    ThinkingStyle.BOOL -> put("thinking", buildJsonObject { put("type", thinkingParam) })
                    ThinkingStyle.BUDGET -> put("thinking", buildJsonObject {
                        put("type", "enabled")
                        thinkingParam.toIntOrNull()?.let { put("budget_tokens", it) }
                    })
                    ThinkingStyle.NONE -> Unit
                }
            }
        }.toString()

        var includeUsage = true
        var conn: HttpURLConnection? = null
        val startedAt = SystemClock.elapsedRealtime()
        var firstTokenAt = 0L
        var providerUsage: Usage? = null

        try {
            while (true) {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 120000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("User-Agent", "PocketAgent/0.1")
                }
                conn.outputStream.use { it.write(buildPayload(includeUsage).toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code in 200..299) break

                val body = runCatching {
                    conn.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
                }.getOrElse { "" }

                // 兼容性降级：厂商不认识 stream_options，去掉后重试一次
                if (code == 400 && includeUsage && body.contains("stream_options", ignoreCase = true)) {
                    conn.disconnect()
                    includeUsage = false
                    continue
                }

                // 兼容性降级：厂商不支持工具调用（不认识 tools / tool_choice / tool 角色），
                // 去掉 tools 重发一次，按纯对话继续 —— 总比整轮失败好
                if (code == 400 && toolsSent && tools.isNotEmpty() &&
                    (body.contains("tool", ignoreCase = true) || body.contains("function", ignoreCase = true))
                ) {
                    conn.disconnect()
                    toolsSent = false
                    emit(Event.ToolsUnsupported)
                    continue
                }

                val snippet = body.replace(Regex("\\s+"), " ").take(200)
                val kind = when (code) {
                    401, 403 -> FailureKind.AUTH
                    404 -> FailureKind.NOT_FOUND
                    429 -> FailureKind.RATE_LIMIT
                    in 500..599 -> FailureKind.SERVER
                    else -> FailureKind.UNKNOWN
                }
                val msg = when (code) {
                    401, 403 -> "Key 无效或已过期"
                    404 -> "端点或模型 ID 不对"
                    429 -> "请求太频繁"
                    in 500..599 -> "厂商服务异常"
                    else -> "请求失败（HTTP $code）"
                }
                emit(Event.Failed(kind, msg, "HTTP $code · $snippet"))
                return@flow
            }

            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val sb = StringBuilder()
            val reasoning = StringBuilder()
            val callBuf = LinkedHashMap<Int, ToolCallBuilder>()
            val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank() || !line.startsWith("data:", true)) continue
                val data = line.substring(5).trim()
                if (data == "[DONE]") break

                val chunk = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
                    ?: continue

                // usage 可能出现在任意 chunk（多数厂商放在最后）
                chunk["usage"]?.let { u ->
                    (u as? JsonObject)?.let { obj ->
                        val prompt = obj.intOf("prompt_tokens")
                        val completion = obj.intOf("completion_tokens")
                        // 有没有这个字段，比它的值更重要：上报 0 和没上报是两件事
                        val details = obj["prompt_tokens_details"] as? JsonObject
                        val cached = details?.intOf("cached_tokens") ?: 0
                        if (prompt != null || completion != null) {
                            providerUsage = Usage(
                                promptTokens = prompt ?: 0,
                                completionTokens = completion ?: 0,
                                cachedTokens = cached,
                                cacheReported = details?.get("cached_tokens") != null,
                                seconds = ((SystemClock.elapsedRealtime() - startedAt) / 1000.0),
                                estimated = false,
                            )
                        }
                    }
                }

                val choices = runCatching { chunk["choices"]?.jsonArray }.getOrNull() ?: continue
                val delta = choices.firstOrNull()?.jsonObject?.get("delta") as? JsonObject ?: continue

                // tool_calls 是**分片下发**的：id/name 只在首片给，arguments 是逐段拼的 JSON 串。
                // 必须按 index 累积、拼完再 parse —— 拿半个 JSON 去解析必然失败。
                (delta["tool_calls"] as? kotlinx.serialization.json.JsonArray)?.forEach { el ->
                    val o = el as? JsonObject ?: return@forEach
                    val idx = (o["index"] as? JsonPrimitive)?.intOrNull ?: 0
                    val b = callBuf.getOrPut(idx) { ToolCallBuilder() }
                    val id = (o["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (!id.isNullOrBlank()) b.id = id
                    val fn = o["function"] as? JsonObject
                    if (fn != null) {
                        val nm = (fn["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        if (!nm.isNullOrBlank()) b.name = nm
                        val arg = (fn["arguments"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        if (!arg.isNullOrEmpty()) b.args.append(arg)
                    }
                }

                // 思考内容：两个字段名都要试。同样只认真字符串，避免 "null" 混进思考区。
                val think = runCatching {
                    val el = delta["reasoning_content"] ?: delta["reasoning"]
                    if (el is JsonPrimitive && el.isString) el.content else null
                }.getOrNull()
                if (!think.isNullOrEmpty()) {
                    if (firstTokenAt == 0L) firstTokenAt = SystemClock.elapsedRealtime()
                    reasoning.append(think)
                    emit(Event.Reasoning(think))
                }

                // 只接受真正的字符串内容。
                // 坑：kotlinx 里 JsonNull 也继承 JsonPrimitive，`.content` 会直接返回字符串 "null"，
                // 于是厂商下发 "content": null 的那些 chunk 会被拼进正文（表现为回复里莫名多出 "nullnull"）。
                val piece = runCatching {
                    val el = delta["content"]
                    if (el is JsonPrimitive && el.isString) el.content else null
                }.getOrNull()
                if (!piece.isNullOrEmpty()) {
                    if (firstTokenAt == 0L) firstTokenAt = SystemClock.elapsedRealtime()
                    sb.append(piece)
                    emit(Event.Delta(piece))
                }
            }

            val promptChars = messages.sumOf { it.content.length }
            val now = SystemClock.elapsedRealtime()
            val usage = providerUsage ?: estimateUsage(
                promptChars = promptChars,
                completionText = sb.toString(),
                startedAt = startedAt,
                firstTokenAt = firstTokenAt,
                now = now,
            )

            if (sb.isEmpty() && callBuf.isEmpty() && reasoning.isEmpty()) {
                emit(Event.Failed(FailureKind.UNKNOWN, "模型返回了空内容", "可能该模型不支持流式输出"))
            } else {
                // id 兜底：有些厂商流式下只给 name 不给 id，而我们回灌 tool 结果时必须带
                // 同一个 id，否则下一轮请求直接 400（tool_call_id 对不上）
                val calls = callBuf.toSortedMap().entries
                    .filter { it.value.name.isNotBlank() }
                    .map { (idx, b) ->
                        val c = b.toCall()
                        if (c.id.isBlank()) c.copy(id = "call_${c.name}_$idx") else c
                    }
                emit(Event.Done(sb.toString(), usage, calls, reasoning.toString()))
            }
        } catch (e: SocketTimeoutException) {
            emit(Event.Failed(FailureKind.TIMEOUT, "响应超时"))
        } catch (e: SSLException) {
            emit(Event.Failed(FailureKind.TLS, "证书校验失败", e.message.orEmpty()))
        } catch (e: Exception) {
            // 底层异常原文是 "Software caused connection abort" 这种，普通用户看不懂，
            // 也不知道该重试还是改配置。这里翻译成人话，原文留给 detail
            emit(Event.Failed(FailureKind.NETWORK, humanNetworkError(e), e.message.orEmpty()))
        } finally {
            conn?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 把 Socket / TLS 层的英文异常翻译成"用户知道下一步做什么"的一句话。
     *
     * 判断只看 message 里的特征串：不同厂商用 OkHttp / HttpURLConnection / cronet，
     * 抛出来的类名各不相同，但底层 errno 的措辞基本一致。
     */
    private fun humanNetworkError(e: Exception): String {
        val raw = (e.message ?: "").lowercase()
        val cls = e::class.java.simpleName.lowercase()
        return when {
            raw.contains("abort") || raw.contains("reset") || raw.contains("broken pipe") ->
                "连接被中断（网络切换或服务端断开了连接），可以重试一次"
            raw.contains("unable to resolve host") || raw.contains("unknownhost") ||
                raw.contains("nodename nor servname") ->
                "域名解析失败，检查网络或 Base URL 是否写错"
            raw.contains("refused") ->
                "连接被拒绝，端口或服务地址不对"
            raw.contains("network is unreachable") || raw.contains("no route to host") ->
                "网络不可达，检查是否断网或走了错误的代理"
            raw.contains("eperm") || raw.contains("permission denied") ->
                "系统拒绝了这次网络请求（可能缺少联网权限）"
            cls.contains("ssl") || cls.contains("tls") ->
                "安全连接失败，检查证书或代理设置"
            raw.isBlank() -> "网络请求失败（没有返回更多信息），可以重试一次"
            else -> "网络请求失败：" + raw.take(80)
        }
    }

    /** 厂商没给 usage 时的兜底估算，明确标记 estimated，UI 会加“≈” */
    private fun estimateUsage(
        promptChars: Int,
        completionText: String,
        startedAt: Long,
        firstTokenAt: Long,
        now: Long,
    ): Usage {
        val completion = TokenEstimator.estimate(completionText)
        // 输出速度按"首字之后"的时长算，避免把首字延迟算进吞吐
        val genMs = if (firstTokenAt > 0) (now - firstTokenAt) else (now - startedAt)
        return Usage(
            promptTokens = TokenEstimator.estimateChars(promptChars),
            completionTokens = completion,
            cachedTokens = 0,
            seconds = genMs / 1000.0,
            estimated = true,
        )
    }
}

private fun JsonObject.intOf(key: String): Int? =
    runCatching { (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull }.getOrNull()

/** 混合中英的粗估：ASCII 约 4 字符/token，CJK 约 1.1 字符/token */
object TokenEstimator {

    fun estimate(text: String): Int = estimateChars(text.length) + cjkBonus(text)

    private fun cjkBonus(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        for (c in text) if (c.code > 0x2E80) cjk++
        // CJK 比 ASCII 占更多 token，补一点差额
        return (cjk * 0.4).toInt()
    }

    fun estimateChars(chars: Int): Int = (chars / 4.0).toInt().coerceAtLeast(1)
}
