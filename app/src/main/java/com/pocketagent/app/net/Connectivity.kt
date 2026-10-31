package com.pocketagent.app.net

import android.os.SystemClock
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 连通性探测。
 *
 * 分两级，先便宜后准确：
 * 1) `GET /models` —— 不消耗 token，能同时验证「端点通不通」和「Key 有没有效」。
 * 2) 只有厂商没实现 `/models`（404/405/501）时，才退化成一次 `max_tokens=1` 的对话请求，
 *    这一步会真的花掉一个 token，但能额外验证「模型 ID 是否正确」。
 *
 * 反过来做（直接发对话）虽然一次到位，但每次点测都要花钱；而大部分厂商都实现了 /models。
 */
object Connectivity {

    sealed interface Result {
        val ms: Long

        data class Ok(override val ms: Long, val summary: String) : Result
        data class Fail(override val ms: Long, val reason: String, val detail: String = "") : Result
    }

    private class Resp(val code: Int, val body: String) {
        val snippet: String
            get() = body.replace(Regex("\\s+"), " ").trim().take(180)
    }

    fun test(baseUrl: String, apiKey: String, modelId: String): Result {
        val t0 = SystemClock.elapsedRealtime()
        fun since() = SystemClock.elapsedRealtime() - t0

        val base = UrlNormalizer.normalize(baseUrl).url

        // ── 第一级：/models ──
        val models = request("GET", base.trimEnd('/') + "/models", apiKey, null)
        when {
            models.code in 200..299 -> {
                val count = Regex("\"id\"\\s*:").findAll(models.body).count()
                return Result.Ok(
                    since(),
                    if (count > 0) "端点与 Key 有效 · 返回 $count 个模型" else "端点与 Key 有效",
                )
            }
            models.code == 401 || models.code == 403 ->
                return Result.Fail(since(), "Key 无效或已过期（HTTP ${models.code}）", models.snippet)
            models.code == 429 ->
                return Result.Fail(since(), "被限流（HTTP 429），连通性正常", models.snippet)
            // 没实现 /models：继续第二级
            models.code == 404 || models.code == 405 || models.code == 501 || models.code == 0 -> Unit
            else -> return Result.Fail(since(), "端点异常（HTTP ${models.code}）", models.snippet)
        }

        if (modelId.isBlank()) {
            return Result.Fail(since(), "端点没提供 /models，且没有可用模型可测", models.snippet)
        }

        // ── 第二级：最小对话请求 ──
        val payload = """
            {"model":"$modelId","messages":[{"role":"user","content":"ping"}],"max_tokens":1,"stream":false}
        """.trimIndent()
        val chat = request("POST", UrlNormalizer.chatUrl(base), apiKey, payload)

        return when {
            chat.code in 200..299 ->
                Result.Ok(since(), "对话接口连通 · $modelId 可用")
            chat.code == 401 || chat.code == 403 ->
                Result.Fail(since(), "Key 无效或已过期（HTTP ${chat.code}）", chat.snippet)
            chat.code == 404 ->
                Result.Fail(since(), "模型 ID 或端点不存在（HTTP 404）", chat.snippet)
            chat.code == 429 ->
                Result.Fail(since(), "被限流（HTTP 429），连通性正常", chat.snippet)
            chat.code == 400 ->
                Result.Fail(since(), "请求被拒（HTTP 400），可能模型 ID 不对", chat.snippet)
            chat.code in 500..599 ->
                Result.Fail(since(), "厂商服务异常（HTTP ${chat.code}）", chat.snippet)
            else ->
                Result.Fail(since(), "失败（HTTP ${chat.code}）", chat.snippet)
        }
    }

    private fun request(method: String, url: String, apiKey: String, body: String?): Resp {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                setRequestProperty("User-Agent", "PocketAgent/0.1")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = runCatching {
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            }.getOrElse { "" }
            Resp(code, text)
        } catch (e: Exception) {
            // 连接层失败（DNS/超时/TLS）统一记 0，由调用方按"端点不可达"处理
            Resp(0, e.message.orEmpty())
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
