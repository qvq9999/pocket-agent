package com.pocketagent.app.net

import android.content.Context
import com.pocketagent.app.data.UpdatePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * 安装包下载源选择：**国内镜像优先，官方直连兜底**。
 *
 * 为什么必须做这件事：APK 托管在 GitHub Release，国内直连
 * `release-assets.githubusercontent.com` 经常是几十 KB/s 甚至直接超时，
 * 用户点了「立即更新」看着进度条不动，会以为软件坏了。
 *
 * 三条约定：
 * 1. **只对 GitHub Release 地址套镜像**。别的域名（自建 CDN、对象存储）
 *    套前缀会拼出一个不存在的 URL，所以先判断再改装。
 * 2. **并发探测，取最快的**。串行试错在最坏情况下要等 4×1.8 秒，
 *    用户会以为卡死。探测是 `Range: bytes=0-0` 只读一个字节，代价可以忽略。
 * 3. **探测全挂就回直连**，不报错。挂代理、漫游、镜像站临时抽风的情况下，
 *    直连可能反而是唯一通的。
 */
object UpdateMirrors {

    /**
     * 前缀式加速站：`前缀 + 原始 URL` 就是镜像地址。
     *
     * 这类公益站存活期不固定，所以顺序可以随时调整 —— **但不要把某个站写死成唯一源**，
     * 它挂了的时候用户就彻底下不了。新增一个只需往这里加一行。
     */
    private val PREFIXES = listOf(
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://ghfast.top/",
        "https://hub.gitmirror.com/",
    )

    private const val UA = "PocketAgent/1.0 (Android)"

    private val GITHUB_RELEASE = Regex("^https://github\\.com/[^/]+/[^/]+/releases/download/")

    fun isGithubRelease(url: String): Boolean = GITHUB_RELEASE.containsMatchIn(url)

    /**
     * 候选地址（按尝试顺序）：上次成功的镜像 → 其余镜像 → 官方直连。
     *
     * 官方放最后是刻意的：它在国内大概率最慢，先试它等于先浪费一次几十秒的超时。
     */
    fun candidates(origin: String, preferred: String = ""): List<String> {
        if (!isGithubRelease(origin)) return listOf(origin)
        val all = PREFIXES.map { it + origin }
        val ordered = if (preferred.isNotEmpty()) {
            val hit = all.firstOrNull { it == preferred + origin }
            if (hit != null) listOf(hit) + all.filter { it != hit } else all
        } else all
        return ordered + origin
    }

    /**
     * 挑一个当前最快的源。探测全失败时返回 [origin]。
     *
     * 每个探测独立计时（1.8s），互不等待：某个站连不上不会拖住其他站的判定。
     */
    suspend fun pick(ctx: Context, origin: String): String {
        if (!isGithubRelease(origin)) return origin
        val preferred = UpdatePrefs.mirrorPrefix(ctx)
        val mirrors = candidates(origin, preferred).dropLast(1)
        if (mirrors.isEmpty()) return origin

        val timed = coroutineScope {
            mirrors.map { url -> async { url to probe(url) } }.awaitAll()
        }
        val best = timed.filter { it.second != null }.minByOrNull { it.second!! }?.first
            ?: return origin

        // 记下这次用的站，下次优先它 —— 但下次仍会重新探测，毕竟它会挂
        UpdatePrefs.setMirrorPrefix(ctx, PREFIXES.firstOrNull { best.startsWith(it) } ?: "")
        return best
    }

    /** 探测耗时（毫秒）；不可用返回 null */
    private suspend fun probe(url: String): Long? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(1_800) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 1_500
                    readTimeout = 1_500
                    instanceFollowRedirects = true
                    setRequestProperty("Range", "bytes=0-0")
                    setRequestProperty("User-Agent", UA)
                    // 不要 gzip：压缩后 contentLength 与文件字节数不等，续传会算错
                    setRequestProperty("Accept-Encoding", "identity")
                }
                val t0 = System.nanoTime()
                if (conn.responseCode !in 200..299) return@withTimeoutOrNull null
                // 必须真读到字节：能建连但 body 立刻断的站，只测状态码会误判成可用
                conn.inputStream.use { it.read() }
                (System.nanoTime() - t0) / 1_000_000
            } catch (e: Throwable) {
                null
            } finally {
                runCatching { conn?.disconnect() }
            }
        }
    }
}
