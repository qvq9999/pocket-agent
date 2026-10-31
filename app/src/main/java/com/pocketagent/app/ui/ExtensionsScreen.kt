package com.pocketagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.agent.LinuxCleaner

/**
 * 扩展（可选能力包）。
 *
 * 独立成页而不是塞进设置：能力包要下载几十兆、装进私有目录，
 * 属于"用户需要知道自己在占什么空间"的操作。塞在设置第十几行里，
 * 用户只会看到一个开关，看不到它装了什么、占多少、怎么删。
 *
 * 现在只有一项：**Linux 环境**（Ubuntu base + proot）。装好它终端能力就可用 ——
 * 环境与开关是同一件事，所以只有一张卡、一个开关。
 *
 * 关于 LibreOffice：实测在本机 proot 下"装得上、转不动"
 * （`bind()` 单实例 IPC 的 unix socket 返回 EACCES，Xvfb 也在同一处失败），
 * 而 RikkaHub 本身也没做这件事（全仓库搜 libreoffice/soffice 命中 0），
 * 所以这部分代码已整体移除，不再让用户白下 134MB。
 */
@Composable
fun ExtensionsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    val linux = vm.linuxStatus
    // 清理中也算"忙"：这时删除/清理两个入口都得禁掉，否则会一边删缓存一边删 rootfs
    val busy = linux is AppViewModel.LinuxStatus.Working || vm.cleaningLinux
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmClean by remember { mutableStateOf(false) }
    // 进页面探一次 python3：已装/未装决定卡片怎么写。探测本身 200ms，不算负担
    androidx.compose.runtime.LaunchedEffect(linux) {
        vm.refreshPythonStatus()
        // 占用与可释放同步重量一次。挂在同一个 key 上：装完、删完状况一变就自动刷新
        vm.refreshLinuxUsage()
    }
    val py = vm.pyStatus
    // 遍历 rootfs 是两万多个文件的事：既不能每帧算，也不能在主线程算 —— 由 VM 在 IO 上扫好后给结果
    val usage = vm.linuxUsage
    val sizeText = usage?.let { LinuxCleaner.sizeText(it.rootfsBytes) }.orEmpty()

    Box(Modifier.fillMaxSize().background(pal().pageBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "扩展", onBack = onBack)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionLabel("终端能力")

                OutlineCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(pal().surfaceAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Glyph(
                                    G.CUBE,
                                    15.dp,
                                    if (linux is AppViewModel.LinuxStatus.Failed) pal().warn else pal().accent,
                                    2.0f,
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Linux 环境",
                                    style = ty().body,
                                    fontWeight = FontWeight.Medium,
                                    color = pal().ink,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    when (linux) {
                                        is AppViewModel.LinuxStatus.Working -> linux.stage
                                        is AppViewModel.LinuxStatus.Failed -> linux.message
                                        AppViewModel.LinuxStatus.Ready ->
                                            if (persisted.shellEnabled) "已就绪 · Ubuntu 24.04"
                                            else "已就绪 · 终端已关闭"
                                        else -> "未安装 · 下载约 30MB，解开约 100MB"
                                    },
                                    style = ty().micro,
                                    color = if (linux is AppViewModel.LinuxStatus.Failed) pal().warn else pal().muted,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            // 安装中也保持可点：关掉就是取消下载（已下的部分会保留）
                            Switch(
                                checked = persisted.shellEnabled,
                                onCheckedChange = { on ->
                                    if (on) vm.setShellEnabled(true) else vm.setShellEnabled(false)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = pal().card,
                                    checkedTrackColor = pal().accent,
                                    uncheckedThumbColor = pal().card,
                                    uncheckedTrackColor = pal().line,
                                    uncheckedBorderColor = pal().line,
                                ),
                            )
                        }

                        // 进度条：字节数来自 HTTP 响应，不是按时间估的
                        if (busy) {
                            val p = vm.rootfsProgress
                            val frac = p?.frac ?: -1f
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(pal().lineSoft),
                            ) {
                                // 总数未知时给一段固定比例的短条：静止的 0% 会让人以为卡死了
                                Box(
                                    Modifier
                                        .fillMaxWidth(if (frac > 0f) frac else 0.28f)
                                        .height(5.dp)
                                        .background(pal().accent),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                // 解压阶段 frac<0：tar 是流式读，拿不到可用的字节进度，
                                // 这时候显示下载阶段残留的 "29.4/29.4 MB" 反而让人以为完成了
                                if ((p?.frac ?: -1f) < 0f) p?.stage ?: "准备中…"
                                else progressText(
                                    stage = p?.stage ?: "准备中…",
                                    bytes = p?.bytes ?: 0L,
                                    totalBytes = p?.totalBytes ?: 0L,
                                    startedAt = p?.startedAt ?: 0L,
                                ),
                                style = ty().micro,
                                color = pal().muted,
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Hairline()
                        Spacer(Modifier.height(10.dp))

                        Text(
                            "Ubuntu 24.04 最小系统，给 agent 一个能跑 bash / grep / sed / awk / tar / apt 的环境，" +
                                "工作目录是 /workspace（可自由写文件）。",
                            style = ty().micro,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "它看不到你的工作区 —— 改文件仍然走那些带审批和撤销的工具，" +
                                "否则一条 rm -rf 就能绕过全部保护。",
                            style = ty().micro,
                            color = pal().hint,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "装在应用私有目录，卸载应用会一并删除。关掉开关只是停用工具，环境保留。",
                            style = ty().micro,
                            color = pal().hint,
                        )

                        if (linux is AppViewModel.LinuxStatus.Ready) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (sizeText.isBlank()) "占用 计算中…" else "占用 $sizeText",
                                style = ty().micro,
                                color = pal().hint,
                            )
                            // 工作区在 rootfs 之外、也不参与清理，但它算在系统的"应用数据"里。
                            // 不点明的话，用户清完再看系统设置会以为清理没生效
                            val ws = usage?.workspaceBytes ?: 0L
                            if (ws >= 10L * 1024 * 1024) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "/workspace 工作区另占 ${LinuxCleaner.sizeText(ws)}，是你的文件，不参与清理",
                                    style = ty().micro,
                                    color = pal().hint,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Hairline()
                            Spacer(Modifier.height(6.dp))
                            CleanRow(
                                label = if (vm.cleaningLinux) "正在清理…" else "清理缓存与临时文件",
                                trailing = when {
                                    vm.cleaningLinux -> ""
                                    usage == null -> "计算中…"
                                    usage.reclaimable > 0L ->
                                        "可释放 ${LinuxCleaner.sizeText(usage.reclaimable)}"
                                    else -> "已干净"
                                },
                                enabled = !busy && (usage?.reclaimable ?: 0L) > 0L,
                                onClick = { confirmClean = true },
                            )
                            Spacer(Modifier.height(2.dp))
                            Hairline()
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !busy) { confirmRemove = true }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Glyph(G.TRASH, 15.dp, pal().bad, 1.9f)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    // 这段只可能在"已就绪"分支里出现，装/删不会同时进行，
                                    // 唯一会忙住的只有清理
                                    if (vm.cleaningLinux) "正在处理…" else "删除 Linux 环境",
                                    style = ty().body,
                                    color = pal().bad,
                                )
                            }
                        }

                        // 环境没装、但有上次中断留下的 30MB 压缩包：这时也要能清，
                        // 否则用户看着系统里那几十兆完全找不到入口
                        val stale = usage?.staleBytes ?: 0L
                        val idle = linux is AppViewModel.LinuxStatus.Absent ||
                            linux is AppViewModel.LinuxStatus.Failed
                        if (idle && stale > 0L) {
                            Spacer(Modifier.height(10.dp))
                            Hairline()
                            Spacer(Modifier.height(6.dp))
                            CleanRow(
                                label = if (vm.cleaningLinux) "正在清理…" else "清理安装残留",
                                trailing = "可释放 ${LinuxCleaner.sizeText(stale)}",
                                enabled = !busy,
                                onClick = { confirmClean = true },
                            )
                        }

                        if (linux is AppViewModel.LinuxStatus.Failed) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "再点一次开关会重新下载；已下载的部分会续传，不会从头开始。",
                                style = ty().micro,
                                color = pal().hint,
                            )
                        }
                    }
                }

                if (busy) {
                    Text(
                        "下载期间请保持应用在前台或后台运行，不要清掉后台任务。",
                        style = ty().micro,
                        color = pal().hint,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }

                Spacer(Modifier.height(6.dp))
                SectionLabel("脚本运行")

                OutlineCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(pal().surfaceAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Glyph(
                                    G.DOC,
                                    15.dp,
                                    if (py is AppViewModel.PyStatus.Failed) pal().warn else pal().accent,
                                    2.0f,
                                )
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Python 运行环境",
                                    style = ty().body,
                                    fontWeight = FontWeight.Medium,
                                    color = pal().ink,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    when (py) {
                                        is AppViewModel.PyStatus.Working -> py.stage
                                        is AppViewModel.PyStatus.Failed -> py.message
                                        is AppViewModel.PyStatus.Ready -> "已就绪 · python3 + 核心依赖"
                                        else -> when {
                                            linux !is AppViewModel.LinuxStatus.Ready ->
                                                "需要先安装上面的 Linux 环境"
                                            else -> "未安装 · 下载约 60MB"
                                        }
                                    },
                                    style = ty().micro,
                                    color = if (py is AppViewModel.PyStatus.Failed) pal().warn else pal().muted,
                                )
                            }
                            Spacer(Modifier.width(10.dp))

                            // 和 Linux 那张卡同一个位置放操作：正在装时给"取消"，
                            // 其余时候给"安装/重装"。开关在这里没有意义 ——
                            // python 只能装或删，不能像工具那样"关掉"
                            val ready = py is AppViewModel.PyStatus.Ready
                            val working = py is AppViewModel.PyStatus.Working
                            val canInstall = linux is AppViewModel.LinuxStatus.Ready
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        when {
                                            working -> pal().surfaceAlt
                                            !canInstall -> pal().surfaceAlt
                                            else -> pal().accentSoft
                                        }
                                    )
                                    .clickable(enabled = working || canInstall) {
                                        if (working) vm.cancelPythonEnv() else vm.installPythonEnv()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    when {
                                        working -> "取消"
                                        ready -> "重装"
                                        else -> "安装"
                                    },
                                    fontSize = 11.5f.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        working -> pal().muted
                                        !canInstall -> pal().faint
                                        else -> pal().accent
                                    },
                                )
                            }
                        }

                        // 进度条与 Linux 那张完全同款：apt/pip 没有字节进度，
                        // 所以给的是不确定态（固定比例短条），靠阶段文案说明在干什么
                        if (py is AppViewModel.PyStatus.Working) {
                            val p = vm.pyProgress
                            Spacer(Modifier.height(12.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(pal().lineSoft),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.28f)
                                        .height(5.dp)
                                        .background(pal().accent),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                buildString {
                                    append(p?.stage ?: "准备中…")
                                    val since = p?.startedAt ?: 0L
                                    if (since > 0L) {
                                        val sec = (android.os.SystemClock.elapsedRealtime() - since) / 1000
                                        append(" · 已用 ")
                                        if (sec >= 60) append("${sec / 60} 分 ${sec % 60} 秒")
                                        else append("$sec 秒")
                                    }
                                },
                                style = ty().micro,
                                color = pal().muted,
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Hairline()
                        Spacer(Modifier.height(10.dp))

                        Text(
                            "装的是 python3 + pip，加四个生成文档要用的包：" +
                                "PyYAML、python-pptx、XlsxWriter、Pillow。技能手册里的脚本靠它跑。",
                            style = ty().micro,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "只跑脚本时才用得到它；没有 Python 也能用技能 —— 手册本身是可读的，只是" +
                                "脚本步骤要改用原生工具。",
                            style = ty().micro,
                            color = pal().hint,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "可选依赖（PDF 转换、配音等，含 AGPL 的 PyMuPDF）没有预装：" +
                                "它们体积大、只服务少数支线，失败还会拖垮整个安装。",
                            style = ty().micro,
                            color = pal().hint,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "更多能力（PDF 读取、表格加行、插入图片）计划做进原生解析层，不需要额外下载。",
                    style = ty().micro,
                    color = pal().hint,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = "删除 Linux 环境？",
            message = "会删除整套 Ubuntu 环境" + (if (sizeText.isBlank()) "" else "（约 $sizeText）") + "，" +
                "终端能力也会随之关闭。以后需要时重新下载即可。",
            confirmText = "删除",
            onConfirm = {
                confirmRemove = false
                vm.removeLinux()
            },
            onCancel = { confirmRemove = false },
        )
    }

    if (confirmClean) {
        // 逐项报体积而不是只说"清理一下"：用户得能判断这次值不值得点，
        // 也能看出空间到底花在 npm 式的"缓存"上还是"我装的东西"上
        val detail = usage?.items
            ?.joinToString("\n") { "· ${it.label}（${LinuxCleaner.sizeText(it.bytes)}）" }
            .orEmpty()
        ConfirmDialog(
            title = "清理无用文件？",
            message = "将删除以下内容，共约 ${LinuxCleaner.sizeText(usage?.reclaimable ?: 0L)}：\n\n" +
                detail + "\n\n已安装的软件包和 /workspace 里的文件不会动。",
            confirmText = "清理",
            onConfirm = {
                confirmClean = false
                vm.cleanLinux()
            },
            onCancel = { confirmClean = false },
        )
    }
}

/**
 * "可清理"那一行。
 *
 * 与"删除 Linux 环境"同款布局：整行可点、左侧图标 + 文案、右侧一句话结论。
 * 图标沿用项目里的 G.TRASH（清理 = 删掉缓存，语义最近），两个入口靠**颜色与文案**区分：
 * 清理是常规维护（accent、文案带"可释放 xx"），删除环境是不可逆（bad）。
 */
@Composable
private fun CleanRow(
    label: String,
    trailing: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.TRASH, 15.dp, if (enabled) pal().accent else pal().faint, 1.9f)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = ty().body,
            color = if (enabled) pal().ink else pal().muted,
        )
        if (trailing.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))
            Text(
                trailing,
                style = ty().micro,
                color = if (enabled) pal().accent else pal().faint,
                maxLines = 1,
            )
        }
    }
}

/** 「12.3 / 29.4 MB · 1.8 MB/s · 预计还需 40 秒」；字节未知时只显示阶段名 */
private fun progressText(stage: String, bytes: Long, totalBytes: Long, startedAt: Long): String {
    if (totalBytes <= 0 || startedAt <= 0L) return stage
    val elapsed = ((android.os.SystemClock.elapsedRealtime() - startedAt) / 1000f).coerceAtLeast(1f)
    val doneMb = bytes / 1048576f
    val totalMb = totalBytes / 1048576f
    val speed = doneMb / elapsed
    val sb = StringBuilder()
    sb.append("%.1f / %.0f MB".format(doneMb, totalMb))
    if (speed > 0.05f) {
        sb.append(" · %.1f MB/s".format(speed))
        if (doneMb < totalMb) {
            val eta = ((totalMb - doneMb) / speed).toInt().coerceAtLeast(0)
            sb.append(" · 预计还需 ")
            if (eta >= 60) sb.append("${eta / 60} 分 ${eta % 60} 秒") else sb.append("$eta 秒")
        }
    }
    return sb.toString()
}
