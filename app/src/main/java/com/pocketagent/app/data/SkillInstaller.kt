package com.pocketagent.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 自定义技能安装：从 GitHub 仓库地址，或本地 zip 文件。
 *
 * 两条路最后都收敛到 [installFromZip]：先把包解到临时目录，校验，再从里面**挑一个技能目录**
 * 搬进 `filesDir/skills_user/<id>/`。临时目录在失败时整体删掉，不会留下半个技能。
 *
 * 校验刻意严格（数量、体积、路径、SKILL.md 必须存在、frontmatter 必须可解析）：
 * 技能会被模型当成"操作手册"读，一个来路不明的包如果能塞进 `../../` 或 20 万个文件，
 * 后果比"装不上"严重得多。
 */
object SkillInstaller {

    private const val TAG = "SkillInstaller"

    /**
     * 单个技能最多多少个文件。
     *
     * 不能按"几十个"想：ppt-master 的 scripts + templates 真实有 1.3 万个文件，
     * 上限设小了会把正常技能误判成恶意包。3 万是"再多就不是技能了"的量级。
     */
    private const val MAX_ENTRIES = 30_000

    /** 单个技能解压后的总字节上限。ppt-master 是 37MB，留几倍余量 */
    private const val MAX_TOTAL = 200L * 1024 * 1024

    /** GitHub 仓库 zip 可能很大，下载上限 200MB，超过就放弃（这不是技能，是仓库） */
    private const val MAX_DOWNLOAD = 200L * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    /** 安装结果：成功给元信息，失败给一句人能读懂的原因 */
    sealed interface Outcome {
        data class Ok(val meta: SkillMeta, val overwritten: Boolean) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    // ───────────────────────── 本地 zip ─────────────────────────

    fun installFromUri(ctx: Context, uri: Uri): Outcome = runCatching {
        val name = runCatching { uri.lastPathSegment }.getOrNull().orEmpty()
        val tmp = File(ctx.cacheDir, "skill-local-${System.currentTimeMillis()}.zip")
        try {
            val copied = ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
            } ?: return Outcome.Failed("打不开这个文件，可能已被移动或没有读取权限")
            if (copied > MAX_DOWNLOAD) {
                return Outcome.Failed("这个文件超过 200MB，不像是一个技能包")
            }
            installFromZip(ctx, tmp, source = "本地文件", sourceLabel = name)
        } finally {
            runCatching { tmp.delete() }
        }
    }.getOrElse { e ->
        Log.w(TAG, "install from uri failed", e)
        Outcome.Failed(e.message ?: "读取 zip 失败")
    }

    // ───────────────────────── GitHub ─────────────────────────

    /**
     * 支持这几种写法（都指向"技能所在的目录"）：
     *  - `owner/repo`
     *  - `https://github.com/owner/repo`
     *  - `https://github.com/owner/repo/tree/<branch>/<path/to/skill>`
     *  - 任意直链 `.zip`（codeload / 自建）
     */
    fun installFromGithub(ctx: Context, rawUrl: String, onStep: (String) -> Unit = {}): Outcome {
        val url = rawUrl.trim()
        if (url.isBlank()) return Outcome.Failed("地址是空的")

        // 直链 zip：不解析，直接下
        if (url.endsWith(".zip", ignoreCase = true) && !url.contains("github.com/", true)) {
            onStep("下载 zip…")
            return downloadAndInstall(ctx, url, source = "直链 zip", sourceLabel = url, subPath = "", onStep = onStep)
        }

        val spec = parseGithub(url) ?: return Outcome.Failed(
            "认不出这个地址。可以填 owner/repo，或者 github.com/owner/repo/tree/main/skills/xxx",
        )

        onStep("下载仓库…")
        // 分支没给就依次试 main / master：GitHub 已经不强制 master，猜错一次很常见
        val branches = if (spec.branch.isNotBlank()) listOf(spec.branch) else listOf("main", "master")
        var lastErr = ""
        for (b in branches) {
            val zipUrl = "https://codeload.github.com/${spec.owner}/${spec.repo}/zip/refs/heads/$b"
            when (
                val r = downloadAndInstall(
                    ctx, zipUrl,
                    source = "GitHub",
                    sourceLabel = "github.com/${spec.owner}/${spec.repo}",
                    subPath = spec.path,
                    onStep = onStep,
                )
            ) {
                is Outcome.Ok -> return r
                is Outcome.Failed -> lastErr = r.reason
            }
        }
        return Outcome.Failed(lastErr.ifBlank { "下载失败，检查网络或仓库地址" })
    }

    private data class GhSpec(val owner: String, val repo: String, val branch: String, val path: String)

    private fun parseGithub(raw: String): GhSpec? {
        val s = raw.trim().removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").removeSuffix("/")
        if (!s.startsWith("github.com/")) {
            // 允许直接写 owner/repo
            val parts = s.split('/')
            if (parts.size == 2 && parts.all { it.isNotBlank() }) {
                return GhSpec(parts[0], parts[1].removeSuffix(".git"), "", "")
            }
            return null
        }
        val parts = s.removePrefix("github.com/").split('/')
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (parts.size == 2) return GhSpec(owner, repo, "", "")
        // /tree/<branch>/<path...>
        if (parts[2] != "tree" && parts[2] != "blob") return GhSpec(owner, repo, "", "")
        val branch = parts.getOrElse(3) { "" }
        val path = parts.drop(4).joinToString("/").let { if (parts[2] == "blob") it.substringBeforeLast('/') else it }
        return GhSpec(owner, repo, branch, path)
    }

    private fun downloadAndInstall(
        ctx: Context,
        url: String,
        source: String,
        sourceLabel: String,
        subPath: String,
        onStep: (String) -> Unit,
    ): Outcome {
        val tmpZip = File(ctx.cacheDir, "skill-dl-${System.currentTimeMillis()}.zip")
        return try {
            if (download(url, tmpZip, onStep) != true) return Outcome.Failed(
                "下载失败：连不上这个地址。GitHub 在国内常常超时，可以换本地 zip 安装",
            )
            onStep("解包…")
            installFromZip(ctx, tmpZip, source = source, sourceLabel = sourceLabel, subPath = subPath)
        } catch (e: Exception) {
            Log.w(TAG, "download/install failed: $url", e)
            Outcome.Failed(e.message ?: "安装失败")
        } finally {
            runCatching { tmpZip.delete() }
        }
    }

    /** 带重定向与体积上限的下载；返回 false 表示放弃 */
    private fun download(url: String, dest: File, onStep: (String) -> Unit): Boolean? {
        var conn: HttpURLConnection? = null
        return try {
            var current = url
            var hops = 0
            while (hops < 5) {
                conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "PocketAgent/1.0")
                    setRequestProperty("Accept", "*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: return false
                    current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                    hops++
                    conn.disconnect()
                    continue
                }
                if (code !in 200..299) return false

                val total = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                if (total > MAX_DOWNLOAD) return false
                var read = 0L
                val buf = ByteArray(64 * 1024)
                dest.outputStream().use { out ->
                    conn.inputStream.use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            read += n
                            if (read > MAX_DOWNLOAD) return false
                            out.write(buf, 0, n)
                        }
                    }
                }
                onStep("下载完成 ${read / 1024 / 1024} MB")
                return true
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "download error", e)
            false
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    // ───────────────────────── 解包 ─────────────────────────

    /**
     * 从 zip 文件里装一个技能。**两阶段**，不是一遍流式解压：
     *
     * 阶段 1 只读中央目录（ZipFile），把所有 SKILL.md 的位置列出来，据此决定"装哪一个"；
     * 阶段 2 只解压那个目录下的文件。
     *
     * 为什么必须这样：GitHub 的仓库 zip 会把整个仓库打进来（ppt-master 是 1.3 万个文件），
     * 一遍流式解压再挑，等于为了一个技能写几十 MB 垃圾；而且"文件数上限"会把正常的大仓库
     * 误判成恶意包。先看目录再解，两个问题一起解决。
     *
     * @param subPath 仓库内目标目录（/tree/main/skills/xxx 里的 skills/xxx）；空 = 自动判断
     */
    fun installFromZip(
        ctx: Context,
        zipFile: File,
        source: String,
        sourceLabel: String,
        subPath: String = "",
    ): Outcome {
        val chosen: String = try {
            java.util.zip.ZipFile(zipFile).use { zf ->
                // 所有 SKILL.md 的"所在目录"，相对 zip 根，用 / 结尾方便拼路径
                val dirs = zf.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { it.name.replace('\\', '/') }
                    .filter { it.equals("SKILL.md", true) || it.endsWith("/SKILL.md", true) }
                    .map { it.removeSuffix("SKILL.md").removeSuffix("/") }
                    .filter { it.count { c -> c == '/' } <= 4 }
                    .toList()
                if (dirs.isEmpty()) {
                    return Outcome.Failed("包里没有 SKILL.md，这不是一个技能（技能目录根部必须有 SKILL.md）")
                }
                val sub = subPath.trim('/')
                when {
                    // 指定了目录：允许带仓库外壳（repo-main/skills/xxx）
                    sub.isNotBlank() -> dirs.firstOrNull { it == sub }
                        ?: dirs.firstOrNull { it.endsWith("/" + sub) }
                        ?: return Outcome.Failed("包里没有 $sub 这个目录（找到的技能目录：" + dirs.take(4).joinToString("、") + "）")
                    dirs.size == 1 -> dirs.first()
                    // 没指定又有一堆：列出来让用户自己指，别替他猜
                    else -> return Outcome.Failed(
                        "这个包里有 " + dirs.size + " 个技能，请把地址填到具体目录，例如 " +
                            dirs.take(3).joinToString("、"),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "read zip failed", e)
            return Outcome.Failed("这不是一个有效的 zip：" + (e.message ?: "读不出来"))
        }

        val tmp = File(ctx.filesDir, "skills_install.tmp")
        tmp.deleteRecursively()
        if (!tmp.mkdirs()) return Outcome.Failed("无法创建临时目录，检查存储空间")
        val tmpPrefix = tmp.canonicalPath + File.separator
        val prefix = if (chosen.isBlank()) "" else "$chosen/"

        var entries = 0
        var total = 0L
        try {
            java.util.zip.ZipFile(zipFile).use { zf ->
                for (e in zf.entries()) {
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/')
                    if (prefix.isNotEmpty() && !name.startsWith(prefix)) continue
                    val rel = name.removePrefix(prefix)
                    if (rel.isBlank()) continue
                    entries++
                    if (entries > MAX_ENTRIES) return Outcome.Failed("这个技能超过 $MAX_ENTRIES 个文件，太大了")
                    if (rel.startsWith("/") || rel.contains("..")) {
                        return Outcome.Failed("包内有可疑路径：$rel")
                    }
                    total += e.size.coerceAtLeast(0)
                    if (total > MAX_TOTAL) return Outcome.Failed("解压后超过 80MB，这不是一个技能包")
                    val target = File(tmp, rel)
                    if (!target.canonicalPath.startsWith(tmpPrefix)) {
                        return Outcome.Failed("包内有越界路径：$rel")
                    }
                    target.parentFile?.mkdirs()
                    zf.getInputStream(e).use { input ->
                        target.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
                    }
                }
            }

            val entryFile = File(tmp, "SKILL.md")
            if (!entryFile.isFile) return Outcome.Failed("$chosen 里没有 SKILL.md")

            val head = runCatching {
                entryFile.bufferedReader().use { r ->
                    val sb = StringBuilder()
                    repeat(120) {
                        val line = r.readLine() ?: return@repeat
                        sb.append(line).append('\n')
                    }
                    sb.toString()
                }
            }.getOrDefault("")
            val fm = parseFrontmatter(head)

            val rawName = fm["name"].orEmpty().ifBlank {
                if (chosen.isBlank()) sourceLabel.substringAfterLast('/').substringBeforeLast('.')
                else chosen.substringAfterLast('/')
            }
            val id = sanitizeId(rawName)
            if (id.isBlank()) return Outcome.Failed("技能名不合法：$rawName")

            val exists = SkillStore.isUserSkill(ctx, id) || SkillStore.isBuiltin(ctx, id)
            val targetDir = File(SkillStore.userRoot(ctx), id)
            if (targetDir.exists()) targetDir.deleteRecursively()
            targetDir.mkdirs()
            tmp.copyRecursively(targetDir, overwrite = true)

            val meta = SkillMeta(
                id = id,
                name = fm["name"].orEmpty().ifBlank { id },
                summary = fm["summary"].orEmpty().ifBlank { shortSummary(fm["description"].orEmpty()) },
                description = fm["description"].orEmpty(),
                version = fm["version"].orEmpty(),
                license = fm["license"].orEmpty().ifBlank { detectLicense(targetDir) },
                author = fm["author"].orEmpty(),
                source = sourceLabel.ifBlank { source },
                hasScripts = hasScripts(targetDir),
                entry = "SKILL.md",
                sizeBytes = targetDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                custom = true,
            )
            File(targetDir, META).writeText(json.encodeToString(SkillMeta.serializer(), meta))
            SkillStore.invalidate()
            return Outcome.Ok(meta, overwritten = exists)
        } catch (e: Exception) {
            Log.w(TAG, "installFromZip failed", e)
            return Outcome.Failed(e.message ?: "解包失败")
        } finally {
            tmp.deleteRecursively()
        }
    }

    private const val META = ".meta.json"

    private fun sanitizeId(raw: String): String = raw.trim().lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-', '.', '_')
        .take(48)

    private fun parseFrontmatter(head: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val lines = head.lines()
        if (lines.firstOrNull()?.trim() != "---") return out
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.trim() == "---") break
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            val k = line.substring(0, idx).trim().lowercase()
            var v = line.substring(idx + 1).trim()
            v = v.removeSurrounding("\"").removeSurrounding("'")
            if (k in setOf("name", "description", "version", "license", "author", "summary")) {
                out[k] = v
            }
        }
        return out
    }

    /** 英文原描述截一句中文侧栏能放下的摘要（截断到第一个句号） */
    private fun shortSummary(desc: String): String {
        if (desc.isBlank()) return ""
        val first = desc.split('.', '。').firstOrNull()?.trim().orEmpty()
        return (if (first.isBlank()) desc else first).take(60)
    }

    private fun detectLicense(dir: File): String = runCatching {
        dir.listFiles()?.firstOrNull {
            it.name.startsWith("LICENSE", true) || it.name.startsWith("COPYING", true)
        }?.let { f ->
            val t = f.bufferedReader().use { r -> r.readText().take(1200) }
            when {
                t.contains("Apache License", true) -> "Apache-2.0"
                t.contains("MIT License", true) || t.contains("Permission is hereby granted, free of charge", true) -> "MIT"
                t.contains("GNU AFFERO", true) -> "AGPL-3.0"
                t.contains("GNU GENERAL PUBLIC", true) -> "GPL"
                t.contains("BSD", true) -> "BSD"
                else -> "见 LICENSE 文件"
            }
        }.orEmpty()
    }.getOrDefault("")

    /** 有没有脚本目录：决定界面上要不要提示"需要 Linux 环境" */
    private fun hasScripts(dir: File): Boolean = runCatching {
        dir.listFiles()?.any { it.name.equals("scripts", true) && it.isDirectory } == true
    }.getOrDefault(false)
}
