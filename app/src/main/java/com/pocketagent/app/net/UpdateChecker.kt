package com.pocketagent.app.net

import android.content.Context
import com.pocketagent.app.BuildConfig
import com.pocketagent.app.data.UpdateManifest
import com.pocketagent.app.data.UpdatePrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本检查。
 *
 * 清单地址用 `releases/latest/download/version.json`，这是 GitHub 的固定跳转路径：
 * 每次发版只要把 `version.json` 传成附件，**客户端不用改任何东西就能拿到最新清单**。
 * 不要换成 `raw.githubusercontent.com` —— 它有约 5 分钟 CDN 缓存，
 * 发版后立刻检查会拿到旧内容，表现为"明明发了新版却提示已是最新"。
 */
object UpdateChecker {

    /** 仓库全名。改仓库时要同步这里，否则检查会 404 */
    const val REPO = "qvq9999/pocket-agent"

    private const val MANIFEST_URL =
        "https://github.com/qvq9999/pocket-agent/releases/latest/download/version.json"

    private const val UA = "PocketAgent/1.0 (Android)"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    sealed interface Result {
        /** 有可用更新 */
        data class Available(val manifest: UpdateManifest) : Result

        /** 已是最新，或清单本身不完整（拿不到可下载的包就当没有更新） */
        data object UpToDate : Result

        data class Failed(val reason: String) : Result
    }

    /**
     * 检查更新。
     *
     * @param manual 用户手动点的检查。为 true 时会绕开 HTTP 缓存，并把失败原因带回去；
     *               自动检查则静默失败（用户没问，不该被"检查更新失败"弹窗打断）。
     */
    suspend fun check(ctx: Context, manual: Boolean): Result {
        val body = fetchFirst(ctx, manual) ?: return Result.Failed(
            if (manual) "网络不可用，未能连接更新服务器" else "offline"
        )

        val manifest = runCatching { json.decodeFromString<UpdateManifest>(body) }.getOrNull()
            ?: return if (manual) Result.Failed("版本清单格式不正确") else Result.UpToDate

        UpdatePrefs.markChecked(ctx, ok = true)

        // —— 语义校验：清单是远端可控的，不能拿什么就信什么 ——
        // 1. 版本号回退（发错包、或 latest 指到了旧 tag）：一律当没更新，不能让用户"被降级"
        if (manifest.versionCode <= 0 || !manifest.isNewerThan(BuildConfig.VERSION_CODE)) {
            return Result.UpToDate
        }
        // 2. 没有版本名或下载地址的清单无法展示、也无法安装
        if (manifest.versionName.isBlank() || manifest.apkUrl.isBlank()) {
            return Result.UpToDate
        }
        return Result.Available(manifest)
    }

    /**
     * 并发请求所有候选源，**第一个返回有效内容的胜出**，其余立刻取消。
     *
     * 为什么不是顺序试：镜像挂掉时 connectTimeout 要等满 8 秒，四个源串起来是半分钟，
     * 而用户只是想知道"有没有新版"。并发的话最坏也只等一个超时。
     */
    private suspend fun fetchFirst(ctx: Context, manual: Boolean): String? = withContext(Dispatchers.IO) {
        val preferred = UpdatePrefs.mirrorPrefix(ctx)
        val urls = UpdateMirrors.candidates(MANIFEST_URL, preferred)
        val parent = Job()
        val scope = CoroutineScope(Dispatchers.IO + parent)
        val done = CompletableDeferred<String>()
        try {
            urls.forEach { url ->
                scope.launch {
                    val text = runCatching { httpGet(url, manual) }.getOrNull()
                    if (text != null && !done.isCompleted) done.complete(text)
                }
            }
            // 15 秒：镜像首次要回源，实测 gh-proxy 冷启动 9 秒出头，
            // 卡在 12 秒会因为一点点波动就判失败，用户看到的是"网络不可用"
            withTimeoutOrNull(15_000) { done.await() }
        } finally {
            // 有一个成功就掐掉剩下的连接，别让它们在后台继续占着带宽
            parent.cancel()
        }
    }

    private fun httpGet(url: String, noCache: Boolean): String {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                // 12 秒：镜像经代理回源可到 9 秒以上，8 秒会把能成功的请求判成超时
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "identity")
                if (noCache) {
                    // 手动检查必须拿到当下的状态：CDN 缓存里可能是上一次的
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                }
            }
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            // 防御：清单只有几百字节，几 MB 的响应只可能是镜像返回了一个错误页
            if (text.length > 256 * 1024) throw IOException("清单过大")
            return text
        } finally {
            runCatching { conn?.disconnect() }
        }
    }
}
