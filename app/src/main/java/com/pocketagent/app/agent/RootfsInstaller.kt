package com.pocketagent.app.agent

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * rootfs 下载与安装。
 *
 * 做法照 RikkaHub 的 `RootfsInstaller`：**从 URL 下载，解到 staging，成功后整体 rename**。
 * 三条细节都是有代价换来的，别省：
 *
 * 1. **先写 `.part` 再改名**。直接写目标文件时，中途断网会留下一个"看起来存在但残缺"的包，
 *    下次启动拿它去解压，报的是莫名其妙的 tar 错误，而不是"没下载完"。
 * 2. **解到 staging 再 rename**。半解压的 rootfs 里有 `usr/bin/apt-get`，
 *    会被 `rootfsReady()` 判成"已就绪"，之后每次 apt 都失败且找不到原因。
 * 3. **失败清理 staging**。一次失败留下 500MB 垃圾，用户除了手动"清除数据"没别的办法。
 *
 * 为什么是 Ubuntu 而不是 Alpine：Alpine 的 LibreOffice 包里**没有 `libvclplug_svplo.so`**
 * （headless 渲染后端），所以 `soffice --version` 正常但任何转换都静默 exit 1。
 * Ubuntu 的包自带该插件，这也是 RikkaHub 默认 rootfs 选 Ubuntu base 的原因。
 */
class RootfsInstaller(private val ctx: android.content.Context) {

    enum class Stage { DOWNLOADING, EXTRACTING, INSTALLED }

    data class Progress(
        val stage: Stage,
        val bytesRead: Long = 0,
        val totalBytes: Long = 0,
        val entries: Int = 0,
        val currentEntry: String = "",
    )

    /**
     * 多源安装：按顺序试，一个失败就换下一个。
     *
     * 为什么需要这个：国内直连 `cdimage.ubuntu.com` 经常在中途被掐断（报
     * `Connection closed` / `unexpected end of stream`），而镜像站是同内容的完整副本。
     * 只有一个源时，用户只能反复重试同一个坏连接。
     *
     * @param urls 候选地址，按优先级排列；必须是 `.tar.gz`
     * @param target 最终 rootfs 目录（如 `files/linux`）
     */
    fun install(urls: List<String>, target: File, onProgress: (Progress) -> Unit = {}) {
        require(urls.isNotEmpty()) { "至少需要一个 rootfs 下载地址" }
        var lastError: Throwable? = null
        urls.forEachIndexed { index, url ->
            try {
                installOne(url, target, onProgress, resetPartial = index > 0)
                return
            } catch (e: InterruptedException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw ToolError(
            "ROOTFS",
            "所有下载源都失败了（${urls.size} 个）。最后一次：${lastError?.message ?: "未知原因"}",
        )
    }

    /**
     * @param resetPartial 换源时必须清掉半截文件：两个镜像虽然内容相同，
     *        但同步版本可能不一致，把 A 的下半段拼到 B 的上半段会得到一个能解压、
     *        内容却错乱的文件 —— 这种损坏要到运行命令时才暴露。
     */
    private fun installOne(
        url: String,
        target: File,
        onProgress: (Progress) -> Unit,
        resetPartial: Boolean,
    ) {

        val parent = target.parentFile ?: throw ToolError("ROOTFS", "目标目录不合法")
        val staging = File(parent, target.name + "-staging")
        val archive = File(ctx.cacheDir, "rootfs.tar.gz.part")
        val ready = File(ctx.cacheDir, "rootfs.tar.gz")

        if (resetPartial) {
            archive.delete()
            ready.delete()
        }

        var completed = false
        try {
            staging.deleteRecursively()
            staging.mkdirs()

            // 上次在"解压阶段"被取消：压缩包是完整的，直接复用 —— 不然用户要重下 30MB。
            // 长度门槛是防半截文件：download() 成功后会 rename 成 ready，所以 ready 只可能完整。
            val reusable = ready.isFile && ready.length() > MIN_ARCHIVE_BYTES
            if (!reusable) {
                // 同源最多试两次：断流多半是偶发，第二次会带 Range 从断点续传，
                // 不必因为一次抖动就丢掉已下的十几兆。
                var attempt = 0
                while (true) {
                    try {
                        download(url, archive, ready, onProgress)
                        break
                    } catch (e: InterruptedException) {
                        throw e
                    } catch (e: Throwable) {
                        attempt++
                        if (attempt >= 2) throw e
                    }
                }
            } else {
                onProgress(Progress(Stage.EXTRACTING))
            }

            var lastReport = 0L
            ready.inputStream().use { input ->
                TarExtractor.extractGz(input, staging) { done, name ->
                    // 逐条回调太密（几万个成员会让 UI 每毫秒重组一次），按 500 条节流
                    if (done - lastReport >= 500) {
                        lastReport = done.toLong()
                        onProgress(Progress(Stage.EXTRACTING, entries = done, currentEntry = name))
                    }
                }
            }

            // 原子替换：旧目录先删（升级 rootfs 时），再 rename 新的
            target.deleteRecursively()
            if (!staging.renameTo(target)) {
                throw ToolError("ROOTFS", "无法把解压结果移动到 ${target.name}")
            }
            completed = true
            onProgress(Progress(Stage.INSTALLED))
        } finally {
            staging.deleteRecursively()
            if (completed) {
                archive.delete()
                ready.delete()
            }
            // 未完成时刻意**不清**：.part 留着下次续传，ready 留着下次直接解压。
            // 这里的取舍是"下次少下一个 30MB" vs "缓存目录多占 30MB"，前者更值。
        }
    }

    /**
     * 下载，带断点续传（30MB 在弱网下并不小，切后台断掉不必从头再来）。
     *
     * 用 `RandomAccessFile` 而不是 `outputStream()`：后者一打开就把文件截成 0，
     * 续传会变成"从 0 覆盖前 N 字节"，得到一个长度对、内容错的文件 —— 这种损坏
     * 到解压时才炸，且报错完全指不到这里。
     */
    private fun download(url: String, part: File, ready: File, onProgress: (Progress) -> Unit) {
        var have = if (part.isFile) part.length() else 0L
        if (have > 0) onProgress(Progress(Stage.DOWNLOADING, have, 0))

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            // 60s 对慢网太紧：8MB 的包在 200KB/s 下要 40 秒，中间再抖一下就被判超时
            readTimeout = 120_000
            instanceFollowRedirects = true
            // 有些 CDN 对没有 UA 的请求会直接断连
            setRequestProperty("User-Agent", "PocketAgent/1.0 (Android)")
            // 不要 gzip：一压缩 contentLength 就不等于文件字节数，进度条和续传都会算错
            setRequestProperty("Accept-Encoding", "identity")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }

        try {
            val code = conn.responseCode
            if (code == 416 && have > 0) {
                // Range 起点已经等于文件长度：.part 上次其实下完了，直接用
                conn.disconnect()
                if (!part.renameTo(ready)) {
                    part.copyTo(ready, overwrite = true)
                    part.delete()
                }
                return
            }
            if (code !in 200..299) throw ToolError("ROOTFS", "下载失败：HTTP $code")
            // 服务器不认 Range（回 200 而不是 206）就整体重下
            if (code == 200 && have > 0) {
                have = 0
                part.delete()
            }
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong + have else 0L

            java.io.RandomAccessFile(part, "rw").use { raf ->
                raf.seek(have)
                raf.setLength(have)
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var read = have
                    var lastReport = have
                    while (true) {
                        // 同上：30MB 在弱网下要几分钟，取消必须立刻生效
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("rootfs 下载已取消")
                        }
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        read += n
                        if (read - lastReport >= 512 * 1024) {
                            lastReport = read
                            onProgress(Progress(Stage.DOWNLOADING, read, total))
                        }
                    }
                    onProgress(Progress(Stage.DOWNLOADING, read, total))
                    // 关键校验：连接被中途掐断时 read() 往往返回 -1（而不是抛异常），
                    // 不比对长度就会把一个残缺包 rename 成 ready，解压时才炸，
                    // 报的还是"归档数据不完整"，完全指不到是下载断流。
                    if (total > 0 && read < total) {
                        throw ToolError(
                            "ROOTFS",
                            "下载中断：已收到 ${read / 1024}KB / ${total / 1024}KB，下次会从这个位置续传",
                        )
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (!part.renameTo(ready)) {
            part.copyTo(ready, overwrite = true)
            part.delete()
        }
    }

    companion object {
        /** 压缩包小于这个值就当作没下完（Ubuntu base 约 29MB） */
        private const val MIN_ARCHIVE_BYTES = 10L * 1024 * 1024

        /** Ubuntu base 24.04 arm64（官方源）。RikkaHub 用的就是这一份 */
        const val UBUNTU_BASE =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.3-base-arm64.tar.gz"

        /** 国内镜像（同一份官方内容）。放前面：直连官方经常被掐断 */
        const val UBUNTU_BASE_CN =
            "https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.3-base-arm64.tar.gz"

        /**
         * 默认候选顺序：**镜像优先**。
         * 实测国内直连 cdimage.ubuntu.com 会 `Connection closed`，而镜像站能跑满带宽。
         */
        val DEFAULT_SOURCES = listOf(UBUNTU_BASE_CN, UBUNTU_BASE)
    }
}
