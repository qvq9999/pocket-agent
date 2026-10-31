package com.pocketagent.app.agent

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

/**
 * Linux 环境的垃圾清理。
 *
 * ── 为什么会越用越大 ──
 * 一个 Ubuntu base 本身只有 100MB 出头，真正让它膨胀的是四处：
 * 1. `/var/cache/apt/archives`：每个 `.deb` 装完**不会自动删**，装一套 python3-pip
 *    就留下上百兆；重装/升级还会再存一份别的版本，旧的照样躺着。
 * 2. `/root/.cache`：pip 把下载过的 wheel 和 HTTP 缓存全留着，体积往往是装完之后的两三倍。
 * 3. `/tmp`、`/var/tmp`、`/var/log`、`/var/backups`：agent 每次跑命令的中间产物和日志。
 * 4. app 侧还有一处用户根本看不见的：取消安装后，`cache/rootfs.tar.gz(.part)` 和
 *    `files/linux-staging` 会留下 30MB 级的半成品。它们不在 rootfs 里，
 *    所以扩展页那个"占用"数字从来没把它们算进去 —— 用户会觉得"删了也没变小"。
 *
 * ── 为什么不用 apt-get clean / autoremove ──
 * 那两条要起 proot 跑整个 apt（几十秒、要拿锁、要软件源索引），而且 `autoremove`
 * 会**改已装的软件包集合**：随手卸掉某个技能依赖的包，报错会出现在几天后某次脚本执行里，
 * 根本追不回来。这里只删"删了必然无害"的纯文件 —— 缓存、临时文件、日志、安装残留。
 * 已安装的软件包和 `/workspace` 里用户自己的文件一律不碰。
 *
 * ── 符号链接必须不跟随 ──
 * rootfs 里 `/bin`、`/lib` 都是指向 `/usr/...` 的相对符号链接。用 `File.walkTopDown()`
 * （它按 `isDirectory()` 判断，会跟进去）不仅会把 `usr/bin` 重复计一遍，
 * 还可能顺着链接跑到 rootfs 外面去删东西。所以这里统一用 NIO 的 `walkFileTree`
 * —— 它默认 NOFOLLOW：符号链接当成一个文件，只删链接本身。
 */
class LinuxCleaner(private val ctx: Context) {

    /**
     * 一类可回收内容。
     *
     * 展示什么就清什么：`bytes == 0` 的项不进列表，避免"说好清 200MB、实际只少 80MB"。
     */
    data class Item(val id: String, val label: String, val bytes: Long)

    data class Usage(
        /** rootfs 本身（不含符号链接的重复计数） */
        val rootfsBytes: Long,
        /** /workspace 工作区；属于用户的文件，不参与清理，只用来解释"为什么清理后总占用没降多少" */
        val workspaceBytes: Long,
        val items: List<Item>,
    ) {
        val reclaimable: Long get() = items.sumOf { it.bytes }

        /** 只算安装残留：环境删掉后它还在，单独给"清理"入口用 */
        val staleBytes: Long get() = items.firstOrNull { it.id == ID_STALE }?.bytes ?: 0L
    }

    private val rootfs: File get() = File(ctx.filesDir, ProotRuntime.ROOTFS_DIR)

    private val workspace: File get() = File(ctx.filesDir, ProotRuntime.WORKSPACE_DIR)

    /** 扫一遍：既给"占用"也给"可释放"，两处数字必须同源，否则用户会发现它们互相矛盾 */
    fun usage(): Usage = Usage(
        rootfsBytes = LinuxDisk.bytesUnder(rootfs),
        workspaceBytes = LinuxDisk.bytesUnder(workspace),
        items = buildList {
            add(Item(ID_APT, "apt 包缓存", LinuxDisk.bytesUnder(File(rootfs, "var/cache/apt/archives"))))
            add(Item(ID_PY, "Python 与字体缓存", LinuxDisk.bytesUnder(File(rootfs, "root/.cache"))))
            add(Item(ID_TMP, "临时文件", bytesUnder(File(rootfs, "tmp"), File(rootfs, "var/tmp"))))
            add(Item(ID_LOG, "系统日志与备份", bytesUnder(File(rootfs, "var/log"), File(rootfs, "var/backups"))))
            add(Item(ID_STALE, "安装残留", bytesUnder(*staleTargets().toTypedArray())))
        }.filter { it.bytes > 0L },
    )

    /**
     * 真正删除并返回实际释放的字节数。
     *
     * 返回值是"删前可清理 - 删后可清理"，不是把删前的数字直接报出去：
     * 有些文件删不掉（被占用、权限、包管理器正在写），差额能诚实地反映出来。
     */
    fun clean(): Long {
        val before = usage()
        if (before.reclaimable <= 0L) return 0L
        before.items.forEach { item ->
            when (item.id) {
                // archives/partial 必须保留目录：apt 拿它当下载暂存区，目录不存在会直接报错
                ID_APT -> LinuxDisk.clearContents(File(rootfs, "var/cache/apt/archives"), keep = setOf("partial"))
                ID_PY -> LinuxDisk.clearContents(File(rootfs, "root/.cache"))
                ID_TMP -> listOf("tmp", "var/tmp").forEach { LinuxDisk.clearContents(File(rootfs, it)) }
                ID_LOG -> listOf("var/log", "var/backups").forEach { LinuxDisk.clearContents(File(rootfs, it)) }
                ID_STALE -> staleTargets().forEach { LinuxDisk.delete(it) }
            }
        }
        return (before.reclaimable - usage().reclaimable).coerceAtLeast(0L)
    }

    /**
     * 安装残留：都不是 rootfs 里的东西，而是安装流程留下的半成品。
     *
     * 路径从 [ProotRuntime] 的常量拼，不写字面量：哪天目录改了名，
     * 这里不会静默变成"永远清 0 字节"。
     */
    private fun staleTargets(): List<File> = listOf(
        // RootfsInstaller 的解压暂存目录：正常流程会自己删，进程被杀才会留下
        File(ctx.filesDir, ProotRuntime.ROOTFS_DIR + "-staging"),
        // 取消/失败后刻意保留的压缩包，用于下次续传或直接复用
        File(ctx.cacheDir, "rootfs.tar.gz"),
        File(ctx.cacheDir, "rootfs.tar.gz.part"),
    )

    private fun bytesUnder(vararg dirs: File): Long = dirs.sumOf { LinuxDisk.bytesUnder(it) }

    companion object {
        const val ID_APT = "apt"
        const val ID_PY = "py"
        const val ID_TMP = "tmp"
        const val ID_LOG = "log"
        const val ID_STALE = "stale"

        /**
         * 人读的体积文案。
         *
         * 用 US Locale 固定小数点：跟随系统区域时，某些语言会把 "1.2 MB" 写成 "1,2 MB"，
         * 而中文语境下那个逗号看起来像千分位。
         */
        fun sizeText(bytes: Long): String = when {
            bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / 1073741824f)
            bytes >= 100L * 1024 * 1024 -> String.format(Locale.US, "%.0f MB", bytes / 1048576f)
            bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1048576f)
            bytes >= 1024L -> "${bytes / 1024} KB"
            bytes > 0L -> "<1 KB"
            else -> "0 KB"
        }
    }
}

/**
 * 私有目录里的文件系统操作：只做三件事 —— 量体积、清空目录、删掉整棵树。
 *
 * 独立成对象是因为 [ProotRuntime] 量"占用"和 [LinuxCleaner] 量"可释放"必须是同一套口径，
 * 各写一份迟早会对不上（旧实现用 `walkTopDown` 就把符号链接进去的 `/usr/bin` 算了两遍）。
 */
internal object LinuxDisk {

    /** 目录（或单个文件）占多少字节；符号链接不计入，因为它删掉也省不出空间 */
    fun bytesUnder(target: File): Long {
        val path = target.toPath()
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            return if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                runCatching { Files.size(path) }.getOrDefault(0L)
            } else {
                0L
            }
        }
        var total = 0L
        runCatching {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (!attrs.isSymbolicLink && attrs.isRegularFile) total += attrs.size()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                    FileVisitResult.CONTINUE
            })
        }
        return total
    }

    /** 清空目录内容，保留目录本身；[keep] 里的子项递归清空而不删除（如 apt 的 partial） */
    fun clearContents(dir: File, keep: Set<String> = emptySet()) {
        if (!Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) return
        dir.listFiles()?.forEach { child ->
            if (child.name in keep) clearContents(child) else delete(child)
        }
    }

    /** 删除文件或整棵目录。符号链接只删链接本身，绝不跟进去 */
    fun delete(target: File) {
        val path = target.toPath()
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            runCatching { Files.deleteIfExists(path) }
            return
        }
        runCatching {
            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    runCatching { Files.deleteIfExists(file) }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    runCatching { Files.deleteIfExists(file) }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    runCatching { Files.deleteIfExists(dir) }
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }
}
