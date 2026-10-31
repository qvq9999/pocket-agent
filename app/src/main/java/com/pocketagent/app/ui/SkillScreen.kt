package com.pocketagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.data.SkillMeta
import com.pocketagent.app.data.SkillStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill 管理。
 *
 * 四个技能都是**内置**的（资源打在 APK 里，首次启动解压，不需要联网下载），
 * 所以这一页的重点不是"安装"，而是"我现在装了哪些、它们有多大、哪些开着"。
 *
 * 一个必须写清楚的边界：脚本型技能（ppt-master）的手册能用，但它里面的 Python 脚本
 * 本机跑不了 —— 页面上的徽标和详情页的依赖说明都要如实讲，不能让人以为"装了就能跑脚本"。
 */
@Composable
fun SkillScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    // catalog 是 object 里的缓存，装/删之后要重新取一次；postKey 就是它的版本号
    var postKey by remember { mutableIntStateOf(0) }
    val skills = remember(postKey, persisted.disabledSkills, persisted.activeSkills) { vm.skillCatalog() }
    var ghDialog by remember { mutableStateOf(false) }

    // 本地 zip：zip 的 mime 各 ROM 报得不一样，有的报 application/zip，有的报 octet-stream，
    // 干脆放开 */*，由解包器自己判断"有没有 SKILL.md"
    val zipPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.installSkillFromUri(it) } }

    val installing = vm.skillInstall
    val disabled = persisted.disabledSkills.toSet()
    val active = persisted.activeSkills.toSet()

    Box(Modifier.fillMaxSize().background(pal().pageBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "Skill 管理", onBack = onBack)

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Text(
                        "Skill 是一份操作手册（可能附带脚本）。启用后，AI 遇到对应任务会先读手册再动手。" +
                            "也可以从 GitHub 或本地 zip 装自己的技能。",
                        style = ty().micro,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }

                // 所有技能同一套行。之前把 ppt-master 单拎出来做成大卡，
                // 结果是"同一个列表里两种卡片"——它只是体积大，不是另一个种类
                item { SectionLabel("已安装", trailing = skills.size.toString() + " 个") }
                skills.forEach { sk ->
                    item(key = sk.id) {
                        SkillListRow(
                            meta = sk,
                            enabled = sk.id !in disabled,
                            active = sk.id in active,
                            gone = !SkillStore.skillDir(vm.getApplication(), sk.id).isDirectory,
                            onToggle = { on -> vm.setSkillEnabled(sk.id, on) },
                            onClick = { onOpenDetail(sk.id) },
                        )
                    }
                }

                // 安装入口。当前版本四个技能都在包里，这里如实说明，并保留后续扩展的位置
                item { SectionLabel("安装") }
                item {
                    OutlineCard(padding = 0.dp) {
                        Column {
                            SettingRow(
                                title = "从 GitHub 安装",
                                value = if (installing != null) installing!! else "填地址",
                                icon = G.CUBE,
                                iconTint = pal().accent,
                                onClick = { if (installing == null) ghDialog = true },
                            )
                            Hairline()
                            SettingRow(
                                title = "从本地文件安装",
                                value = if (installing != null) "安装中…" else "选 .zip",
                                icon = G.FOLDER,
                                iconTint = pal().ok,
                                onClick = {
                                    if (installing == null) zipPicker.launch(arrayOf("*/*"))
                                },
                            )
                            Hairline()
                            SettingRow(
                                title = "重装内置技能",
                                value = "恢复全部",
                                icon = G.REFRESH,
                                iconTint = pal().accent,
                                onClick = { vm.reinstallSkills() },
                            )
                        }
                    }
                }

                if (installing != null) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(pal().accentSoft)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = pal().accent,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                installing!!,
                                style = ty().micro,
                                color = pal().accentInk,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                item {
                    Text(
                        "脚本型技能会在本机 Linux 沙盒里执行代码，每次运行都要你确认；" +
                            "没装 Linux 环境时只能读手册（指令模式）。" +
                            "技能手册来自第三方：PPT Master 为 MIT（© Hugo He），" +
                            "Theme Factory / Brand Guidelines / Doc Co-Authoring 为 Apache-2.0（Anthropic）。" +
                            "自定义安装的技能请自行确认来源，它们同样能拿到工作区文件的副本。",
                        style = ty().micro,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
                    )
                }
            }
        }

        if (ghDialog) {
            GithubInstallDialog(
                onDismiss = { ghDialog = false },
                onInstall = { url ->
                    ghDialog = false
                    vm.installSkillFromGithub(url)
                    postKey++
                },
            )
        }
    }
}

/**
 * GitHub 安装弹窗。
 *
 * 骨架与设置页的 Key 弹窗一致（18dp 圆角卡 + 42dp 胶囊按钮）：这个 App 里"输入一次就提交"
 * 的动作统一长这样，用户不用重新认一遍。
 */
@Composable
private fun GithubInstallDialog(onDismiss: () -> Unit, onInstall: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(pal().card)
                .border(1.dp, pal().line, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Text("从 GitHub 安装", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
            Text(
                "填仓库地址，或直接指到技能目录，例如 " +
                    "github.com/anthropics/skills/tree/main/skills/doc-coauthoring。" +
                    "下的是仓库 zip，装哪个技能由包里的 SKILL.md 决定。",
                style = ty().micro,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, pal().line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text("owner/repo 或 github.com/…", fontSize = 13.sp, color = pal().hint)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = pal().ink,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(pal().accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(pal().surfaceAlt)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Text("取消", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = pal().muted) }
                Box(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(if (text.isBlank()) pal().line else pal().accent)
                        .clickable(enabled = text.isNotBlank()) { onInstall(text) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "安装",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (text.isBlank()) pal().muted else pal().onAccent,
                    )
                }
            }
        }
    }
}

/** 普通一行：首字母块 + 名称 + 来源/大小/模式 + 开关 + 箭头 */
@Composable
private fun SkillListRow(
    meta: SkillMeta,
    enabled: Boolean,
    active: Boolean,
    /** 解压出来的副本不在（内置技能被删过一次）—— 显示"已删除"而不是假装可用 */
    gone: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkillBadge(meta, 30)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meta.name,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) pal().ink else pal().faint,
                    maxLines = 1,
                )
                if (active) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "使用中",
                        fontSize = 9.5.sp,
                        color = pal().accentInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(pal().accentSoft)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                meta.source.substringAfterLast('/').ifBlank { meta.source } +
                    " · " + sizeText(meta.sizeBytes) + " · " +
                    when {
                        gone -> "已删除 · 重装可恢复"
                        meta.hasScripts -> "含脚本"
                        else -> "指令模式"
                    },
                style = ty().micro,
                maxLines = 1,
                color = if (gone) pal().bad else pal().muted,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = pal().card,
                checkedTrackColor = pal().accent,
                uncheckedThumbColor = pal().card,
                uncheckedTrackColor = pal().line,
                uncheckedBorderColor = pal().line,
            ),
        )
        Spacer(Modifier.width(2.dp))
        Glyph(G.CHEV_RIGHT, 13.dp, pal().faint, 2.2f)
    }
}

/** 详情页：手册摘要 + 文件树 + 许可证 + 动作 */
@Composable
fun SkillDetailScreen(
    vm: AppViewModel,
    id: String,
    onBack: () -> Unit,
) {
    val meta = remember(id) { SkillStore.find(vm.getApplication(), id) }
    val disabled = vm.state.collectAsStateWithLifecycle().value.disabledSkills.toSet()
    var confirmDelete by remember { mutableStateOf(false) }

    val preview by produceState("", id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (!SkillStore.ensure(vm.getApplication())) return@runCatching "（资源包尚未解压完成）"
                SkillStore.readText(vm.getApplication(), id)
                    .lineSequence().take(20).joinToString("\n")
            }.getOrElse { "（读不到手册：" + (it.message ?: "未知原因") + "）" }
        }
    }
    val files by produceState(emptyList<String>(), id) {
        value = withContext(Dispatchers.IO) {
            runCatching { SkillStore.tree(vm.getApplication(), id, 24) }.getOrDefault(emptyList())
        }
    }

    Box(Modifier.fillMaxSize().background(pal().pageBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = meta?.name ?: id, subtitle = meta?.version?.let { "v" + it }, onBack = onBack)

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Text(
                        meta?.summary.orEmpty(),
                        style = ty().bodySmall,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                item { SectionLabel("信息") }
                item {
                    OutlineCard(padding = 0.dp) {
                        Column {
                            SettingRow("来源", meta?.source.orEmpty(), onClick = {}, clickable = false, icon = G.INFO)
                            Hairline()
                            SettingRow("作者", meta?.author.orEmpty().ifBlank { "—" }, onClick = {}, clickable = false, icon = G.INFO)
                            Hairline()
                            SettingRow("许可证", meta?.license.orEmpty().ifBlank { "见技能目录" }, onClick = {}, clickable = false, icon = G.SHIELD)
                            Hairline()
                            SettingRow("体积", sizeText(meta?.sizeBytes ?: 0L), onClick = {}, clickable = false, icon = G.FOLDER)
                            Hairline()
                            SettingRow(
                                "运行条件",
                                when {
                                    meta?.hasScripts != true -> "指令模式 · 无额外依赖"
                                    vm.pyStatus is AppViewModel.PyStatus.Ready ->
                                        "指令模式 + 脚本（python3 已就绪）"
                                    else -> "指令模式可用 · 脚本需在「扩展」里装 Python"
                                },
                                onClick = {},
                                clickable = false,
                                icon = G.SLIDERS,
                                iconTint = if (meta?.hasScripts != true ||
                                    vm.pyStatus is AppViewModel.PyStatus.Ready
                                ) pal().ok else pal().warn,
                            )
                        }
                    }
                }

                item { SectionLabel("手册摘要", trailing = "前 20 行") }
                item {
                    OutlineCard {
                        Text(
                            preview,
                            style = ty().monoId,
                            lineHeight = 17.sp,
                        )
                    }
                }

                if (files.isNotEmpty()) {
                    item { SectionLabel("文件", trailing = files.size.toString() + " 个（两层）") }
                    item {
                        OutlineCard {
                            Text(files.joinToString("\n"), style = ty().monoId, lineHeight = 17.sp)
                        }
                    }
                }

                item { SectionLabel("操作") }
                item {
                    OutlineCard(padding = 0.dp) {
                        Column {
                            if (meta?.hasScripts == true) {
                                // 不在这一页装：Python 是**全局运行时**，挂在某个技能下面
                                // 会让人以为"装了 A 的技能，B 就有 Python"。入口统一在扩展页
                                SettingRow(
                                    "Python 运行环境",
                                    when (val py = vm.pyStatus) {
                                        is AppViewModel.PyStatus.Ready -> "已就绪 · 在「扩展」里可重装"
                                        is AppViewModel.PyStatus.Working -> py.stage
                                        is AppViewModel.PyStatus.Failed -> "安装失败 · 去「扩展」重试"
                                        else -> "未安装 · 去「设置 → 扩展」安装"
                                    },
                                    onClick = {
                                        vm.notice = "Python 运行环境在「设置 → 扩展 → Python 运行环境」里安装"
                                    },
                                    icon = G.CUBE,
                                    iconTint = if (vm.pyStatus is AppViewModel.PyStatus.Ready) pal().ok
                                    else pal().warn,
                                )
                                Hairline()
                            }
                            // 「重装」只对内置技能有意义：它重新解压的是 assets 里那份。
                            // 用户自己装的副本不在那个包里，显示出来只会让人点了没反应
                            if (meta?.custom != true) {
                                SettingRow(
                                    "重装",
                                    "重新解压内置包",
                                    onClick = { vm.reinstallSkills() },
                                    icon = G.REFRESH,
                                    iconTint = pal().accent,
                                )
                                Hairline()
                            }
                            SettingRow(
                                "停用",
                                if (id in disabled) "当前已停用" else "从清单中暂时关闭",
                                onClick = { vm.setSkillEnabled(id, id in disabled) },
                                icon = G.SLIDERS,
                            )
                            Hairline()
                            SettingRow(
                                "删除",
                                "只删本机副本，不影响工作区文件",
                                onClick = { confirmDelete = true },
                                icon = G.TRASH,
                                danger = true,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (confirmDelete) {
            ConfirmDialog(
                title = "删除这个技能？",
                message = "只删本机解压出来的副本，不会动你的工作区文件。之后可以在「Skill 管理 → 重装」里恢复。",
                confirmText = "删除",
                onConfirm = {
                    vm.deleteSkill(id)
                    confirmDelete = false
                    onBack()
                },
                onCancel = { confirmDelete = false },
            )
        }
    }
}

// ───────────── 小零件 ─────────────

@Composable
private fun SkillBadge(meta: SkillMeta, dim: Int) {
    Box(
        Modifier
            .size(dim.dp)
            .clip(RoundedCornerShape((dim / 3).dp))
            .background(pal().accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            meta.name.take(1),
            fontSize = (dim * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = pal().accentInk,
        )
    }
}

@Composable
private fun MicroTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, pal().line, RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

private fun sizeText(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> (bytes / 1024).toString() + " KB"
    else -> String.format("%.1f MB", bytes / 1048576.0)
}
