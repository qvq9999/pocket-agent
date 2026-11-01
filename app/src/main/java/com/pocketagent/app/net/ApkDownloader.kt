package com.pocketagent.app.net

import android.content.Context
import android.os.StatFs
import com.pocketagent.app.data.UpdateManifest
import com.pocketagent.app.data.UpdatePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 安装包下载。
 *
 * 下载器的写法照抄 [com.pocketagent.app.agent.RootfsInstaller] 里那套（代码不共用：
 * 那边跟 tar 解压耦合在一起），三个细节是有代价换来的：
 *
 * 1. **先写 `.part`，校验通过再改名**。直接写目标文件时，中途断网会留下一个
 *    "存在但残缺"的包 —— 交给系统安装器，用户看到的是"解析软件包出现问题"，
 *    没人猜得到是没下完。
 * 2. **用 `RandomAccessFile` 而不是 `outputStream()`**。后者一打开就把文件截成 0，
 *    所谓"续传"会变成从 0 覆盖前 N 字节，得到一个长度正确、内容错乱的文件。
 * 3. **比对最终长度**。连接被掐断时 `read()` 往往返回 -1 而不是抛异常，
 *    不比对长度就会把残包当成品。
 */
class ApkDownloader(private val ctx: Context) {

    data class Progress(val bytes: Long, val total: Long, val bytesPerSec: Long) {
        val percent: Int
            get() = if (total <= 0) 0 else ((bytes * 100) / total).coerceIn(0, 100).toInt()
    }

    sealed interface Outcome {
        data class Ok(val file: File) : Outcome

        /** 用户主动取消：保留 `.part`，下次继续 */
        data object Cancelled : Outcome

        data class Failed(val reason: String) : Outcome
    }

    private val dir: File get() = File(ctx.cacheDir, "update").apply { mkdirs() }

    /** 完整包的落盘位置。已存在且校验通过时可直接复用，省一次下载 */
    fun apkFile(code: Int): File = File(dir, "pocketagent-$code.apk")

    private fun partFile(code: Int): File = File(dir, "pocketagent-$code.apk.part")

    /** 有可续传的半成品或已完成的包 */
    fun pendingFile(code: Int): File? =
        listOf(apkFile(code), partFile(code)).firstOrNull { it.isFile && it.length() > 0 }

    /**
     * 下载并校验。
     *
     * 源的选择交给 [UpdateMirrors]：它并发探测镜像并给出最快的那个；
     * 这里再按"首选 → 其余 → 直连"的顺序兜底，任一源下完并通过校验就收工。
     */
    suspend fun download(
        manifest: UpdateManifest,
        onProgress: (Progress) -> Unit,
    ): Outcome = withContext(Dispatchers.IO) {
        val need = if (manifest.sizeBytes > 0) manifest.sizeBytes else 4L * 1024 * 1024
        val room = runCatching { StatFs(ctx.cacheDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (room < need * 12 / 10) {
            // 留 20% 余量：System 解包安装时还要在 /data 里再展开一份
            return@withContext Outcome.Failed("存储空间不足，至少需要 ${need / 1024 / 1024 + 1} MB 可用空间")
        }

        val best = UpdateMirrors.pick(ctx, manifest.apkUrl)
        val ordered = listOf(best) + UpdateMirrors.candidates(manifest.apkUrl, UpdatePrefs.mirrorPrefix(ctx))
            .filter { it != best }

        var lastError = "未知原因"
        for (url in ordered) {
            val part = partFile(manifest.versionCode)
            try {
                fetchOne(url, part, manifest.sizeBytes, onProgress)
                // —— 校验：长度 + 摘要。两步都要，缺一不可 ——
                if (manifest.sizeBytes > 0 && part.length() != manifest.sizeBytes) {
                    throw IOException("下载不完整（${part.length()}/${manifest.sizeBytes} 字节）")
                }
                val want = manifest.sha256.lowercase()
                if (want.length == 64) {
                    val got = sha256Of(part)
                    if (got != want) {
                        part.delete()
                        throw IOException("安装包校验不通过，已删除，请重试")
                    }
                }
                val target = apkFile(manifest.versionCode)
                target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                UpdatePrefs.clearPending(ctx)
                return@withContext Outcome.Ok(target)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户点了取消：.part 留着，下次从断点接上
                throw e
            } catch (e: InterruptedException) {
                return@withContext Outcome.Cancelled
            } catch (e: Throwable) {
                lastError = e.message ?: e.javaClass.simpleName
                // 换下一个源时从零开始：不同镜像的分片内容可能不一致，接着旧 .part 写会得到坏包
                part.delete()
            }
        }
        Outcome.Failed("下载失败：$lastError")
    }

    private suspend fun fetchOne(
        url: String,
        part: File,
        expectTotal: Long,
        onProgress: (Progress) -> Unit,
    ) {
        var have = if (part.isFile) part.length() else 0L
        if (have > 0) onProgress(Progress(have, expectTotal, 0))
        // 已有的大小已经等于目标长度：上一轮其实下完了，直接交给校验
        if (expectTotal > 0 && have >= expectTotal) return

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PocketAgent/1.0 (Android)")
            setRequestProperty("Accept-Encoding", "identity")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            if (code == 416) {
                // Range 起点已等于文件长度：当作已完成
                conn.disconnect()
                return
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            if (code == 200 && have > 0) {
                // 服务器不认 Range（回 200 全量），只能从头写
                have = 0
                part.delete()
            }
            val total = when {
                conn.contentLengthLong > 0 -> conn.contentLengthLong + have
                else -> expectTotal
            }

            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(have)
                raf.setLength(have)
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var read = have
                    var lastReport = have
                    val t0 = System.nanoTime()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        read += n
                        // 每 256KB 上报一次：再密就是在拿重组换进度条
                        if (read - lastReport >= 256 * 1024) {
                            lastReport = read
                            val sec = (System.nanoTime() - t0) / 1_000_000_000.0
                            val speed = if (sec > 0.2) ((read - have) / sec).toLong() else 0L
                            onProgress(Progress(read, total, speed))
                        }
                    }
                    val sec = (System.nanoTime() - t0) / 1_000_000_000.0
                    val speed = if (sec > 0.2) ((read - have) / sec).toLong() else 0L
                    onProgress(Progress(read, total, speed))
                    // 断流时 read() 常返回 -1 而不是抛异常，长度是唯一可靠的判据
                    if (total > 0 && read < total) {
                        throw IOException("连接中断（$read/$total 字节）")
                    }
                }
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        val out = StringBuilder(64)
        for (b in md.digest()) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private companion object {
        const val HEX = "0123456789abcdef"
    }
}
