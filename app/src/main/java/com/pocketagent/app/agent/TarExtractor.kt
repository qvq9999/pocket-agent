package com.pocketagent.app.agent

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream

/**
 * 纯 Kotlin 的 tar.gz 解压。
 *
 * 为什么不直接用 `/system/bin/toybox tar`：Android 10+ 对 app 域执行系统二进制有额外限制，
 * shell 里能跑不代表 untrusted_app 里能跑。这条路一旦被拦，用户看到的就是"点了没反应"，
 * 而且错误发生在子进程里、连堆栈都没有。
 *
 * 支持的成员类型（对着两个真实的 rootfs 逐一核对过）：
 * - Alpine minirootfs：目录 / 符号链接 / 普通文件（无硬链接）
 * - Ubuntu base 24.04：多出**硬链接**（`usr/bin/perl` → `perl5.38.2`）与 **PAX 扩展头**
 *
 * 硬链接必须真做：Ubuntu 的 `perl`、`uncompress` 都是硬链接，退化成"复制一份"会让
 * dpkg 的完整性校验对不上；而直接跳过又会让这些命令凭空消失。
 * 创建失败（部分文件系统不支持）时退化为复制，并保留权限位。
 */
object TarExtractor {

    class Result(val files: Int, val dirs: Int, val links: Int, val hardLinks: Int = 0)

    /**
     * @param createLinks 是否真的创建链接。默认 true；仅本地 Windows 验证时传 false
     *        （Windows 未开开发者模式时创建符号链接需要管理员权限，会抛 AccessDenied）
     */
    fun extractGz(
        gz: InputStream,
        dest: File,
        createLinks: Boolean = true,
        onEntry: ((done: Int, name: String) -> Unit)? = null,
    ): Result {
        if (!dest.exists() && !dest.mkdirs()) {
            throw ToolError("ROOTFS", "无法创建目录：${dest.absolutePath}")
        }
        var files = 0
        var dirs = 0
        var links = 0
        var hardLinks = 0

        // GNU tar 的超长名 / 超长链接 / PAX 头都是"下一项的元数据"，先存后取
        var pendingName: String? = null
        var pendingLink: String? = null

        GZIPInputStream(gz, 64 * 1024).use { input ->
            val header = ByteArray(BLOCK)
            while (true) {
                // 解几万个文件要十几秒，中途用户点了取消必须能停 —— 否则协程取消了
                // 这个阻塞循环还在跑，装完还会把状态改成"已就绪"，开关与实际完全错位
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("rootfs 解压已取消")
                }
                if (!readFully(input, header)) break
                // 连续两个全零块表示归档结束；这里只要遇到第一个就收工
                if (header.all { it == 0.toByte() }) break

                val rawName = readString(header, 0, 100)
                if (rawName.isEmpty()) break
                val prefix = readString(header, 345, 155)
                var name = if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName

                val mode = readOctal(header, 100, 8)
                val size = readOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                var linkName = readString(header, 157, 100)

                val dataPad = (BLOCK - (size % BLOCK)) % BLOCK

                // ── 元数据块：读完继续，不产生文件 ──
                when (type) {
                    'L' -> { // GNU 超长文件名
                        pendingName = readExactly(input, size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                        if (dataPad > 0) skipExactly(input, dataPad)
                        continue
                    }

                    'K' -> { // GNU 超长链接名
                        pendingLink = readExactly(input, size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                        if (dataPad > 0) skipExactly(input, dataPad)
                        continue
                    }

                    'x' -> { // PAX 扩展属性
                        val pax = parsePax(readExactly(input, size).toString(Charsets.UTF_8))
                        pendingName = pax["path"] ?: pendingName
                        pendingLink = pax["linkpath"] ?: pendingLink
                        if (dataPad > 0) skipExactly(input, dataPad)
                        continue
                    }
                }

                pendingName?.let { name = it; pendingName = null }
                pendingLink?.let { linkName = it; pendingLink = null }

                val target = safeResolve(dest, name)
                    ?: throw ToolError("ROOTFS", "归档里有越界路径，已中止：$name")

                when (type) {
                    '5' -> {
                        if (!target.exists() && !target.mkdirs()) {
                            throw ToolError("ROOTFS", "创建目录失败：${target.path}")
                        }
                        dirs++
                    }

                    '2' -> {
                        if (createLinks) {
                            target.parentFile?.mkdirs()
                            runCatching { Files.deleteIfExists(target.toPath()) }
                            runCatching {
                                Files.createSymbolicLink(target.toPath(), Paths.get(linkName))
                            }.getOrElse {
                                throw ToolError("ROOTFS", "创建符号链接失败：$name → $linkName")
                            }
                        }
                        links++
                    }

                    '1' -> {
                        // 硬链接的 linkName 是**归档内相对路径**（不带开头的 ./）
                        if (createLinks) {
                            target.parentFile?.mkdirs()
                            val src = safeResolve(dest, linkName)
                            if (src != null && src.isFile) {
                                runCatching { Files.deleteIfExists(target.toPath()) }
                                val ok = runCatching {
                                    Files.createLink(target.toPath(), src.toPath())
                                }.isSuccess
                                if (!ok) {
                                    // 某些 SAF/虚拟文件系统不支持硬链接：复制一份保证文件存在
                                    src.copyTo(target, overwrite = true)
                                    applyMode(target, mode)
                                }
                            }
                        }
                        hardLinks++
                    }

                    '0', '\u0000', '7' -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> copyExactly(input, out, size) }
                        applyMode(target, mode)
                        files++
                    }

                    else -> {
                        // 未知类型（设备节点、FIFO 等）在 rootfs 里不承载运行必需内容，
                        // 跳过而**不是**中止：Ubuntu base 里就有字符设备项。
                        // 数据区由下面的统一 skip 处理，这里只记数。
                    }
                }

                // 数据区补齐到 512 的整数倍（普通文件已边读边写，仍需跳过 padding）
                if (dataPad > 0) skipExactly(input, dataPad)

                // 进度回调：解 Ubuntu base 有 2 万多个成员，界面需要"现在到哪了"
                if (onEntry != null) {
                    runCatching { onEntry(files + dirs + links + hardLinks, name) }
                }
            }
        }
        return Result(files, dirs, links, hardLinks)
    }

    private const val BLOCK = 512

    private fun readFully(input: InputStream, buf: ByteArray): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) return off > 0
            off += n
        }
        return true
    }

    private fun readString(buf: ByteArray, offset: Int, len: Int): String {
        var end = offset
        val limit = minOf(offset + len, buf.size)
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun readOctal(buf: ByteArray, offset: Int, len: Int): Long {
        val s = readString(buf, offset, len).trim()
        if (s.isEmpty()) return 0L
        return s.toLongOrNull(8) ?: 0L
    }

    /** PAX 记录形如 `30 path=./usr/bin/xxx\n`（长度含自身） */
    private fun parsePax(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        var i = 0
        while (i < text.length) {
            val sp = text.indexOf(' ', i)
            if (sp < 0) break
            val len = text.substring(i, sp).toIntOrNull() ?: break
            if (len <= 0) break
            val end = (i + len).coerceAtMost(text.length)
            val record = text.substring(sp + 1, end).trimEnd('\n')
            val eq = record.indexOf('=')
            if (eq > 0) out[record.substring(0, eq)] = record.substring(eq + 1)
            i += len
        }
        return out
    }

    private fun readExactly(input: InputStream, size: Long): ByteArray {
        if (size <= 0) return ByteArray(0)
        if (size > Int.MAX_VALUE) throw ToolError("ROOTFS", "归档内的扩展头异常大（$size 字节）")
        val buf = ByteArray(size.toInt())
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) throw ToolError("ROOTFS", "归档数据不完整")
            off += n
        }
        return buf
    }

    private fun copyExactly(input: InputStream, out: java.io.OutputStream, size: Long) {
        var left = size
        val buf = ByteArray(64 * 1024)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) throw ToolError("ROOTFS", "归档数据不完整（还差 $left 字节）")
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun skipExactly(input: InputStream, count: Long) {
        var left = count
        val buf = ByteArray(4096)
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) return
            left -= n
        }
    }

    /**
     * 把归档内的相对路径解析到 dest 下，并拒绝任何逃逸。
     * tar 里的名字形如 `./bin/sh`，`..` 与绝对路径都必须挡住 —— 否则一个精心构造的
     * 压缩包可以往 app 私有目录之外写文件。
     */
    private fun safeResolve(dest: File, name: String): File? {
        var p = name.replace('\\', '/')
        while (p.startsWith("./")) p = p.substring(2)
        if (p.isEmpty() || p == ".") return dest
        if (p.startsWith("/")) return null
        val parts = p.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) return null
        var cur = dest
        for (part in parts) cur = File(cur, part)
        // 最终路径必须仍在 dest 之内
        return if (cur.absolutePath.startsWith(dest.absolutePath + File.separator)) cur else null
    }

    /** 只关心可执行位：bin 下的命令必须能跑 */
    private fun applyMode(f: File, mode: Long) {
        val ownerExec = (mode and 0b001_000_000L) != 0L
        val ownerRead = (mode and 0b000_100_000L) != 0L
        if (ownerExec) runCatching { f.setExecutable(true, false) }
        if (ownerRead) runCatching { f.setReadable(true, false) }
        runCatching { f.setWritable(true, true) }
    }
}
