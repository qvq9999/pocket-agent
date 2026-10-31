package com.pocketagent.app.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * AnySearch 检索客户端。
 *
 * 为什么选它（2026-10 实测）：每天 1000 次免费额度、匿名也能调、返回结构干净
 * （`data.results[].{title,url,snippet,content}`），壁钟延迟 1.7–3.8s。
 * 已知短板：**时效性不稳**——查"今天收盘"会返回历史上的同一天（查询里的日期字面命中），
 * 所以工具结果的说明里必须让模型核对来源日期，别把旧文当今天。
 *
 * 这个类只做一件事：把一次搜索变成 [Hit] 列表，或者一句人能看懂的错误。
 * 不重试、不限流 —— 那是调用方（ToolRunner）的职责，放在这儿会两层重试互相打架。
 */
object AnySearch {

    /** 端点固定，不给用户改：换后端是改代码的事，不是改配置的事 */
    private const val ENDPOINT = "https://api.anysearch.com/v1/search"

    data class Hit(val title: String, val url: String, val snippet: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param key 为空则走匿名额度（实测可用，只是限额更低）
     * @return 成功时给命中列表；失败时给"可以原样念给用户听"的错误文本
     */
    fun search(
        query: String,
        maxResults: Int,
        key: String?,
        connectTimeoutMs: Int = 8_000,
        readTimeoutMs: Int = 12_000,
    ): Result<List<Hit>> {
        if (query.isBlank()) return Result.failure(IllegalArgumentException("搜索词是空的"))

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                if (!key.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $key")
                }
            }
            val body = """{"query":${JsonPrimitive(query)},"max_results":$maxResults}"""
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            when {
                code == 401 || code == 403 ->
                    Result.failure(IOException("AnySearch 密钥无效或额度已用尽（HTTP $code）"))
                code == 429 ->
                    Result.failure(IOException("AnySearch 调用太频繁（HTTP 429），稍后再试"))
                code !in 200..299 ->
                    Result.failure(IOException("AnySearch 返回 HTTP $code：${text.take(160)}"))
                else -> parse(text)
            }
        } catch (e: IOException) {
            Result.failure(IOException("连不上 AnySearch：${e.message ?: "网络异常"}"))
        } catch (e: Exception) {
            Result.failure(IOException("AnySearch 请求失败：${e.message ?: e::class.java.simpleName}"))
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(text: String): Result<List<Hit>> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return Result.failure(IOException("AnySearch 返回的不是 JSON"))
        val code = (root["code"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
        val msg = (root["message"] as? JsonPrimitive)?.content.orEmpty()
        if (code != 0) return Result.failure(IOException("AnySearch 拒绝了这次搜索：$msg"))

        val results = runCatching {
            root["data"]?.jsonObject?.get("results")?.jsonArray ?: return Result.success(emptyList())
        }.getOrNull() ?: return Result.success(emptyList())

        val hits = results.mapNotNull { el ->
            val o: JsonObject = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val title = (o["title"] as? JsonPrimitive)?.content.orEmpty().trim()
            val url = (o["url"] as? JsonPrimitive)?.content.orEmpty().trim()
            if (url.isBlank()) return@mapNotNull null
            val snippet = (o["snippet"] as? JsonPrimitive)?.content.orEmpty()
                .replace('\n', ' ')
                .trim()
                .take(300)
            Hit(title = title.ifBlank { url }, url = url, snippet = snippet)
        }
        return Result.success(hits)
    }
}
