package com.pocketagent.app.net

/**
 * Base URL 规范化。
 *
 * 真实世界的坑：Agnes 官方文档同时写「Base URL: .../v1」和「POST /v1/chat/completions」，
 * 按字面拼会得到 /v1/v1/chat/completions；用户也经常把整个端点粘进来。
 * 这两件事都会产生 404/400，所以进请求前先规整。
 */
object UrlNormalizer {

    data class Result(val url: String, val changed: Boolean, val notes: List<String>)

    private val versionSeg = Regex("/v\\d+")

    fun normalize(rawInput: String): Result {
        var raw = rawInput.trim()
        if (raw.isEmpty()) return Result("", false, emptyList())

        val notes = mutableListOf<String>()

        // 1. 补协议头。局域网地址默认 http —— 给 Ollama / LM Studio 硬加 https 会直接连不上
        if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true)) {
            val looksLocal = Regex(
                "^(localhost|127\\.|0\\.0\\.0\\.0|10\\.|192\\.168\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.)"
            ).containsMatchIn(raw)
            raw = if (looksLocal) "http://$raw" else "https://$raw"
            notes += if (looksLocal) "已补全 http://（局域网地址）" else "已补全 https://"
        }

        // 2. 去尾部斜杠
        while (raw.endsWith("/")) raw = raw.dropLast(1)

        // 3. 截掉被整段粘进来的端点
        for (suffix in listOf("/chat/completions", "/completions", "/responses", "/models")) {
            if (raw.endsWith(suffix, true)) {
                raw = raw.dropLast(suffix.length)
                notes += "已截掉多余路径 $suffix"
            }
        }

        // 4. 折叠重复的版本段（/v1/v1 → /v1）
        var guard = 0
        while (guard++ < 5) {
            val m = Regex("(/v\\d+)\\1+").find(raw)
            if (m == null) break
            raw = raw.replaceRange(m.range, m.groupValues[1])
            notes += "已合并重复的 ${m.groupValues[1]}"
        }

        // 5. 没有任何版本段则补 /v1
        if (!versionSeg.containsMatchIn(raw)) {
            raw = "$raw/v1"
            notes += "已补全 /v1"
        }

        return Result(raw, notes.isNotEmpty(), notes)
    }

    /** 拼出 /models 的完整地址 */
    fun modelsUrl(baseUrl: String, modelsPath: String): String =
        baseUrl.trimEnd('/') + modelsPath

    /** 拼出 chat/completions 的完整地址 */
    fun chatUrl(baseUrl: String): String = baseUrl.trimEnd('/') + "/chat/completions"
}
