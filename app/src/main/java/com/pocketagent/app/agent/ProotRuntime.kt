package com.pocketagent.app.agent

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * proot 能力包：在 app 私有目录里跑一个 Linux 用户空间。
 *
 * ── 为什么是 Ubuntu 而不是 Alpine（2026-10-31 真机换掉）──
 * Alpine 的 `libreoffice-writer` 里**没有 `libvclplug_svplo.so`**（headless 渲染后端），
 * 于是 `soffice --version` 正常、任何转换都静默 exit 1 且无任何输出。
 * Ubuntu 的包自带该插件，而 Ubuntu base 只比 Alpine 大一点（下 30MB）。
 * RikkaHub 的默认 rootfs 也是 Ubuntu base 24.04，这里与它保持一致。
 *
 * ── 三个关键前提（都在真机上验证过，不是推测）──
 * 1. **app 私有目录能执行二进制**（实测 toybox 拷过去可跑）。
 * 2. **proot 二进制必须放在 nativeLibraryDir**，因为那里 SELinux 上下文是 `apk_data_file`；
 *    放在 files/ 下映射 loader 会失败（实测报 "loader was not found or doesn't work"）。
 *    为此 gradle 必须开 `useLegacyPackaging = true`，否则 .so 不解压、目录是空的。
 * 3. **PROOT_LOADER / PROOT_TMP_DIR 两个环境变量缺一不可**，另外要 `PROOT_NO_SECCOMP=1`
 *    （Android 的 seccomp 会拦掉 proot 需要的部分系统调用）。
 */
class ProotRuntime(private val ctx: Context) {

    /** rootfs 位置：app 私有目录，卸载应用时系统一并删除 */
    private val rootfs: File = File(ctx.filesDir, ROOTFS_DIR)

    private val libDir: File = File(ctx.applicationInfo.nativeLibraryDir)

    private val prootExec: File get() = File(libDir, PROOT_EXEC)
    private val prootLoader: File get() = File(libDir, PROOT_LOADER)

    private val installer = RootfsInstaller(ctx)

    /** 二进制是否随包正确安装（nativeLibraryDir 里存在） */
    fun binariesReady(): Boolean = prootExec.isFile && prootLoader.isFile

    /**
     * rootfs 是否可用。
     *
     * 以 `usr/bin/apt-get` 为准而不是 `bin/sh`：Ubuntu 是 usrmerge，`/bin` 是指向 `/usr/bin`
     * 的符号链接，用 `bin/sh` 判断在符号链接还没建好时也会假阳性；
     * 而 apt 存在且 `usr/bin/env` 也在，才说明这套 rootfs 真的完整解开了。
     */
    fun rootfsReady(): Boolean =
        File(rootfs, "usr/bin/apt-get").isFile && File(rootfs, "usr/bin/env").isFile

    fun isReady(): Boolean = binariesReady() && rootfsReady()

    /** 一次命令执行的结果 */
    data class Result(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean,
    )

    /**
     * 下载并安装 rootfs。
     *
     * 走 URL 下载而不是打进 APK：Ubuntu base 压缩后 30MB、解开近 100MB，
     * 打进包会让每次更新都重传，而且我们只需要它做"可选能力"。
     * 缺点是没有网络的用户用不了终端能力 —— 这一点在 UI 上明说。
     */
    suspend fun installRootfs(
        // 多源：镜像优先，直连官方兜底。只给一个源时，一旦被掐断用户只能干等
        sources: List<String> = RootfsInstaller.DEFAULT_SOURCES,
        onProgress: (RootfsInstaller.Progress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        if (!binariesReady()) throw ToolError("ROOTFS", "proot 二进制缺失，安装包不完整")
        if (rootfsReady()) {
            // 已装过也补一遍配置：resolv.conf 可能被清过，缺了 apt 就报"Temporary failure
            // resolving"，看起来像断网，实际只是没有 DNS。这几步都是幂等的小写入。
            prepareRootfs()
            return@withContext true
        }

        try {
            // install() 里全是阻塞 IO（下载 / 解 tar），协程 cancel 打不断它。
            // runInterruptible 会把取消转成线程中断，配合循环里的 isInterrupted 检查才真能停。
            runInterruptible { installer.install(sources, rootfs, onProgress) }
        } catch (e: InterruptedException) {
            // 转成 CancellationException，VM 侧才能认出"这是用户取消"而不是"安装失败"
            throw kotlinx.coroutines.CancellationException("rootfs 安装已取消").also { it.initCause(e) }
        }

        // 装完修正几个 Android 上必须的配置，否则 apt 一定失败
        prepareRootfs()
        rootfsReady()
    }

    /**
     * rootfs 首次可用后必须做的三件事。
     *
     * 1. **DNS**：Android 没有 `/etc/resolv.conf`，rootfs 里那份是空的，
     *    apt 会以 "Temporary failure resolving" 失败，看起来像断网。
     * 2. **apt 沙箱用户**：apt 默认降权到 `_apt` 用户，proot 里 `--root-id` 把一切映射成 root，
     *    降权会失败（"Couldn't drop privileges"）。显式设成 root 才对。
     * 3. **清空 apt 缓存目录**：base 镜像里可能残留索引，先清掉避免用到过期版本。
     */
    private fun prepareRootfs() {
        runCatching { File(rootfs, "etc").mkdirs() }
        runCatching {
            File(rootfs, "etc/resolv.conf").writeText(
                "nameserver 223.5.5.5\nnameserver 119.29.29.29\nnameserver 8.8.8.8\n",
            )
        }
        runCatching {
            val dir = File(rootfs, "etc/apt/apt.conf.d").apply { mkdirs() }
            File(dir, "99-pocketagent").writeText(
                """
                APT::Sandbox::User "root";
                Acquire::Retries "2";
                """.trimIndent() + "\n",
            )
        }
        runCatching { File(rootfs, "var/cache/apt/archives/partial").mkdirs() }
    }

    /**
     * 彻底删除 rootfs（终端能力随之失效）。
     *
     * 走 `deleteRecursively` 而不是 `rm -rf`：path 里有中文/空格时 shell 引号很容易出错，
     * 而这是"删几百 MB"的操作，错一次代价很大。
     */
    fun removeRootfs(): Boolean = runCatching { rootfs.deleteRecursively() }.getOrDefault(false)

    /**
     * 在 rootfs 里执行一条 shell 命令。
     *
     * 启动方式对齐 RikkaHub 的 `ProotShellRunner`，三处差异都是有原因的：
     * 1. **`/usr/bin/env -i` 清空环境**。直接继承 Android 的环境变量会带进
     *    `ANDROID_*`、`EXTERNAL_STORAGE` 之类，某些工具会据此走 Android 分支；
     *    RikkaHub 用 `env -i` 从零构造，行为才可预测。
     * 2. **优先 bash**。Ubuntu 有 bash，`-l` 能加载标准 PATH；Alpine 没有时回退 `/bin/sh`。
     * 3. **绑定一个可写目录到 `/workspace`**。RikkaHub 绑的是整个 `filesDir`；
     *    这里绑 `filesDir/shellspace` —— shell 需要一个能写临时文件的地方，
     *    但不该让它够得着 `state.json` 和记忆文件（一次 rm 就没了配置）。
     *
     * 命令通过位置参数传入（`eval "$2"`），不做字符串拼接转义。
     *
     * 阻塞实现（非 suspend）：调用方本来就在 Dispatchers.IO 上，而工具计划里的 run 是同步签名。
     */
    fun exec(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onLine: ((String) -> Unit)? = null,
        /** 额外的宿主→沙盒绑定，格式 "宿主绝对路径:沙盒内路径"。技能目录靠它进来 */
        extraBinds: List<Pair<String, String>> = emptyList(),
        /** 额外环境变量（PYTHONIOENCODING 之类），传在 env -i 之后 */
        extraEnv: Map<String, String> = emptyMap(),
    ): Result {
        if (!isReady()) throw ToolError("SHELL_NOT_READY", "Linux 环境还没准备好，请先在「扩展」页安装")
        if (command.isBlank()) throw ToolError("BAD_ARGS", "命令不能为空")

        val shell = if (File(rootfs, "bin/bash").exists()) "/bin/bash" else "/bin/sh"

        // 可写工作区：shell 里可以随便造文件，但碰不到 app 的配置。
        // -w 指向一个不存在的目录会让 proot 直接退出，所以这里必须确认建成功
        val workspace = File(ctx.filesDir, WORKSPACE_DIR)
        if (!workspace.isDirectory && !workspace.mkdirs()) {
            throw ToolError("SHELL_NOT_READY", "无法创建 shell 工作区：${workspace.absolutePath}")
        }

        val cmd = mutableListOf(
            prootExec.absolutePath,
            "--root-id",
            "--link2symlink",
            "--kill-on-exit",
            "-r", rootfs.absolutePath,
            "-w", "/workspace",
            "-b", "${workspace.absolutePath}:/workspace",
        )
        // 技能目录：内置与用户两套分别绑到 /opt/skills、/opt/skills-user。
        // 不绑的话沙盒里根本看不见它们（沙盒只有 rootfs 和 /workspace 两处可见）。
        extraBinds.forEach { (host, guest) ->
            if (File(host).isDirectory) {
                // proot 的 -b 目标目录必须在 rootfs 里先存在，否则会直接退出
                val target = File(rootfs, guest.trimStart('/'))
                if (!target.isDirectory) target.mkdirs()
                cmd += "-b"
                cmd += "$host:$guest"
            }
        }
        // 内核伪文件系统：存在才绑（某些 ROM 没有 /sys）
        KERNEL_FS_MOUNTS.forEach { p ->
            if (File(p).exists()) {
                cmd += "-b"
                cmd += p
            }
        }
        cmd += listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TMPDIR=/tmp",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8",
            // 抑制各类 CLI 的交互行为（确认提示 / 分页器 / 颜色转义）
            "CI=true",
            "NO_COLOR=1",
            "PAGER=cat",
            "DEBIAN_FRONTEND=noninteractive",
            *extraEnv.map { (k, v) -> "$k=$v" }.toTypedArray(),
            shell, "-l", "-c",
            "cd -- \"\$1\" && eval \"\$2\"",
            "pocketagent",
            "/workspace",
            command,
        )

        val pb = ProcessBuilder(cmd).directory(ctx.filesDir).redirectErrorStream(true)
        pb.environment().apply {
            put("PROOT_LOADER", prootLoader.absolutePath)
            put("PROOT_TMP_DIR", ctx.cacheDir.absolutePath)
            put("TMPDIR", ctx.cacheDir.absolutePath)
            // PROOT_NO_SECCOMP 刻意**不设**：RikkaHub 只在"兼容模式"下才开它，
            // 默认走 seccomp 加速。有些设备开了它反而让 proot 的 syscall 拦截出错。
        }

        val p = pb.start()
        val text = StringBuilder()
        val reader = p.inputStream.bufferedReader()

        // **必须边读边等**。先 waitFor 再读是死锁：命令输出超过管道缓冲（几十 KB）就会写阻塞，
        // 进程永远不退出，只能等到超时 —— 表现是"明明几秒钟的活却卡满 30 秒"。
        // apt 的输出量必然触发，所以这里用一个线程持续抽干。
        val pump = Thread {
            runCatching {
                var line = reader.readLine()
                while (line != null) {
                    if (text.length < MAX_OUTPUT) text.append(line).append('\n')
                    runCatching { onLine?.invoke(line) }
                    line = reader.readLine()
                }
            }
        }
        pump.isDaemon = true
        pump.start()

        val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            p.destroyForcibly()
            pump.interrupt()
            return Result(-1, "命令超时（${timeoutMs / 1000} 秒）已终止", timedOut = true)
        }
        // 给读取线程一点时间把最后几行收干净，别把结论行丢了
        runCatching { pump.join(1_500) }

        val out = text.toString().trimEnd()
        return Result(
            exitCode = p.exitValue(),
            output = if (out.length >= MAX_OUTPUT) out + "\n…（输出过长已截断）" else out,
            timedOut = false,
        )
    }

    companion object {
        /** rootfs 目录名。扩展页的清理（LinuxCleaner）要按同一个名字找，所以对外可见 */
        internal const val ROOTFS_DIR = "linux"

        /** shell 的可写工作区：绑到 rootfs 内的 /workspace */
        internal const val WORKSPACE_DIR = "shellspace"

        /** 由宿主透传的内核伪文件系统（RikkaHub 的 KERNEL_FS_MOUNTS 同款） */
        private val KERNEL_FS_MOUNTS = listOf("/dev", "/proc", "/sys")

        /** 伪装成 .so 才能被系统解压到 nativeLibraryDir 并带上可执行权限 */
        private const val PROOT_EXEC = "libproot_exec.so"
        private const val PROOT_LOADER = "libproot_loader.so"

        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val MAX_OUTPUT = 24_000
    }
}
