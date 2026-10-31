package com.pocketagent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.agent.ToolPolicy
import com.pocketagent.app.data.PermissionMode
import com.pocketagent.app.data.UsageStats

/**
 * 设置。**唯一的功能入口集合** —— 抽屉只做会话切换，进功能页一律从这儿走。
 *
 * 结构自上而下就是"越常用越靠上"：
 *   常用（主题 / 用量账单，就地弹窗或跳页） → 功能（模型 / 记忆 / 扩展，点行进二级页）
 *   → 对话（工具能做什么） → 联网搜索 → 关于。
 *
 * 主题和用量账单被放在最上面：这两项是"看一眼、换一下"的高频操作，
 * 排在三层导航下面要滚一屏才够得着，不值。
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSkills: () -> Unit,
    /** 重看引导：由 AppRoot 实现"重置 + 关抽屉 + 回会话页"，一步到位看到第一张卡 */
    onReplayOnboard: () -> Unit,
) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    // 记忆条数要真实：不刷新的话「记忆」那行会一直停在"只存本机"
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshMemory() }
    // 展开哪一组选项（null = 全部收起）。剩下的内联展开只有「永久允许的工具」一处 ——
    // 它展开的是"清单"，不是"单选"，弹窗反而无法在弹窗里逐条撤销。
    var expanded by remember { mutableStateOf<String?>(null) }
    // 主题 / 访问权限改成弹窗：单选这件事用弹窗比内联展开更明确（点一项即生效、点外面就取消），
    // 也不用把设置页越拉越长 —— 内联展开会把下面所有分组整体往下推一屏。
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPermDialog by remember { mutableStateOf(false) }
    // 联网搜索：默认开。值不进 VM 的 state（那是会话数据），这里就地读、就地改
    var searchOn by remember { mutableStateOf(vm.searchEnabled()) }
    var keyMasked by remember { mutableStateOf(vm.searchKeyMasked()) }
    var showKeyDialog by remember { mutableStateOf(false) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun openUrl(url: String) {
        // 没有浏览器时 startActivity 会抛 ActivityNotFoundException，不能让它冒到主线程
        runCatching {
            ctx.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    val modelCount = vm.chatModels().size
    val noteCount = vm.memoryDocs.size
    val extLabel = when (val s = vm.linuxStatus) {
        is AppViewModel.LinuxStatus.Ready -> "Linux 环境 · 已装"
        is AppViewModel.LinuxStatus.Working -> "安装中"
        is AppViewModel.LinuxStatus.Failed -> "安装失败"
        AppViewModel.LinuxStatus.Absent -> "未安装"
    }
    // 只算"今天"这一格：口径与账单页的「今日」一致（按自然日切，不是滚动 24 小时）
    val todayKey = UsageStats.dayKeyOf(System.currentTimeMillis())
    val todayTok = remember(persisted.usageEntries, todayKey) {
        persisted.usageEntries
            .filter { UsageStats.dayKeyOf(it.at) == todayKey }
            .sumOf { (it.prompt + it.completion).toLong() }
    }

    Box(Modifier.fillMaxSize().background(pal().pageBg)) {

        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "设置", onBack = onBack)

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

                // ── 常用：主题 + 用量账单 ──
                // 放在第一屏最上面。主题是"顺手换一下"，用量是"看一眼今天花了多少"，
                // 都属于高频轻操作，不该排在模型/记忆/扩展这三层导航后面。
                item { SectionLabel("常用") }
                item {
                    OutlineCard(padding = 0.dp) {
                        Column {
                            SettingRow(
                                title = "主题",
                                value = themeLabelOf(persisted.theme),
                                icon = G.THEME,
                                iconTint = pal().accent,
                                onClick = { showThemeDialog = true },
                            )
                            Hairline()
                            SettingRow(
                                title = "用量账单",
                                value = if (todayTok > 0) "今日 ${UsageStats.compact(todayTok)} tok"
                                else "还没有用量",
                                icon = G.INFO,
                                iconTint = pal().accent,
                                onClick = onOpenUsage,
                            )
                        }
                    }
                }

                // ── 功能：三个二级页的入口 ──
                item { SectionLabel("功能") }
                item {
                    OutlineCard(padding = 0.dp) {
                        Column {
                            SettingRow(
                                title = "模型",
                                value = if (modelCount > 0) "$modelCount 个" else "还没有",
                                icon = G.CUBE,
                                iconTint = pal().accent,
                                onClick = onOpenModels,
                            )
                            Hairline()
                            SettingRow(
                                title = "记忆",
                                value = if (noteCount > 0) "$noteCount 篇笔记" else "只存本机",
                                icon = G.SPARKLE,
                                iconTint = pal().media,
                                onClick = onOpenMemory,
                            )
                            Hairline()
                            SettingRow(
                                title = "扩展",
                                value = extLabel,
                                icon = G.SLIDERS,
                                iconTint = pal().ok,
                                onClick = onOpenExtensions,
                            )
                            Hairline()
                            // 技能：装了几个、这段对话开了几个，"N 个 · M 启用"一眼能看完
                            // 数字必须是真的：装了 N 个（解压副本在才算装）、开关打开 M 个。
                            // 之前用 activeSkills.size 当"启用数"，那是"当前对话启用"，
                            // 和这一行的开关不是一回事，看起来就像坏了
                            val installedSkills = vm.skillCatalog().count {
                                com.pocketagent.app.data.SkillStore
                                    .skillDir(vm.getApplication(), it.id).isDirectory
                            }
                            val enabledSkills = vm.skillCatalog().count {
                                it.id !in persisted.disabledSkills &&
                                    com.pocketagent.app.data.SkillStore
                                        .skillDir(vm.getApplication(), it.id).isDirectory
                            }
                            SettingRow(
                                title = "Skill 管理",
                                value = "$installedSkills 个 · $enabledSkills 启用",
                                icon = G.SPARKLE,
                                iconTint = pal().accent,
                                onClick = onOpenSkills,
                            )
                        }
                    }
                }

                // ── 对话：工具能做多远 ──
                item { SectionLabel("对话") }
                item {
                    OutlineCard(padding = 0.dp) {
                        Column {
                            SettingRow(
                                icon = G.SHIELD,
                                iconTint = pal().warn,
                                title = "默认访问权限",
                                value = permissionLabelOf(persisted.permission),
                                onClick = { showPermDialog = true },
                            )
                            Hairline()
                            SettingRow(
                                icon = G.SLIDERS,
                                iconTint = pal().media,
                                title = "永久允许的工具",
                                value = if (persisted.toolAllowList.isEmpty()) "无"
                                else "${persisted.toolAllowList.size} 个",
                                onClick = {
                                    if (persisted.toolAllowList.isEmpty()) {
                                        vm.notice = "还没有永久允许的工具。写操作弹确认时点「永久允许」会记在这里；" +
                                            "「删除」不在此列 —— 它只在本会话内放行。"
                                    } else {
                                        expanded = if (expanded == "allow") null else "allow"
                                    }
                                },
                                // 清单为空时它只是个"说明"动作，展不开，就不该给可展开的箭头
                                expandable = persisted.toolAllowList.isNotEmpty(),
                                expanded = expanded == "allow" && persisted.toolAllowList.isNotEmpty(),
                            )
                            AnimatedVisibility(
                                visible = expanded == "allow" && persisted.toolAllowList.isNotEmpty(),
                                enter = fadeIn(tween(120)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(90)) + shrinkVertically(tween(150)),
                            ) {
                                Column {
                                    Hairline()
                                    persisted.toolAllowList.forEach { name ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { vm.revokeToolAllow(name) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                ToolPolicy.labelOf(name),
                                                style = ty().body,
                                                color = pal().ink,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text("撤销", style = ty().micro, color = pal().bad)
                                        }
                                        Hairline()
                                    }
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { vm.clearToolAllowList() }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("全部清除", style = ty().body, color = pal().bad)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 联网搜索 ──
                // 默认开启：用户不需要先"启用"才能让模型查东西；填 key 只是把额度提上去
                item { SectionLabel("联网搜索") }
                item {
                    OutlineCard(padding = 0.dp) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(pal().accent.copy(alpha = if (pal().isDark) 0.16f else 0.10f)),
                                    contentAlignment = Alignment.Center,
                                ) { Glyph(G.SEARCH, 15.dp, pal().accent, 2f) }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "联网搜索",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = pal().ink,
                                    )
                                    Text(
                                        if (searchOn) "模型可自行决定要不要搜 · ${vm.searchQuotaText()}"
                                        else "已关闭：模型只能用已有知识回答",
                                        style = ty().micro,
                                        modifier = Modifier.padding(top = 1.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                androidx.compose.material3.Switch(
                                    checked = searchOn,
                                    onCheckedChange = {
                                        searchOn = it
                                        vm.setSearchEnabled(it)
                                    },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = pal().card,
                                        checkedTrackColor = pal().accent,
                                        uncheckedThumbColor = pal().card,
                                        uncheckedTrackColor = pal().line,
                                        uncheckedBorderColor = pal().line,
                                    ),
                                )
                            }
                            Hairline()
                            SettingRow(
                                title = "AnySearch Key",
                                value = if (keyMasked.isBlank()) "未填 · 走匿名额度" else keyMasked,
                                icon = G.SHIELD,
                                iconTint = pal().media,
                                onClick = { showKeyDialog = true },
                            )
                            Hairline()
                            SettingRow(
                                title = "获取 Key",
                                value = "anysearch.com",
                                icon = G.INFO,
                                iconTint = pal().muted,
                                onClick = { openUrl("https://www.anysearch.com") },
                            )
                        }
                    }
                }
                item {
                    Text(
                        "搜索由 AnySearch 提供：不填 key 也能用（匿名额度，本地限流每天 300 次）；" +
                            "填自己的 key 后提到 1500 次/天。key 用系统密钥库加密后只存本机。",
                        style = ty().micro,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                // ── 关于 ──
                item { SectionLabel("关于") }
                item {
                    OutlineCard(padding = 0.dp) {
                        Column {
                            // 使用说明排在最前：它是"关于"里唯一需要主动去读的一项，
                            // 版本号和重看引导都是看一眼就走
                            SettingRow(
                                title = "使用说明",
                                value = "",
                                desc = "各功能模块的操作步骤，从发第一条消息到打开产出文件",
                                icon = G.DOC,
                                iconTint = pal().accent,
                                onClick = onOpenHelp,
                            )
                            Hairline()
                            // 重看入口：只重置"跳过/完成"两个时间戳，模型与目录都不动 ——
                            // 重看说明不该让人重新配置一遍。点完直接回会话页，卡片就在那儿
                            SettingRow(
                                title = "使用引导",
                                value = vm.onboardStatus(persisted),
                                icon = G.SPARKLE,
                                iconTint = pal().accent,
                                onClick = onReplayOnboard,
                            )
                            Hairline()
                            // 版本固定放最后：它只是"看一眼"，不该夹在两个可点项中间
                            SettingRow(
                                "版本",
                                com.pocketagent.app.BuildConfig.VERSION_NAME,
                                onClick = {},
                                clickable = false,
                                icon = G.INFO,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (showKeyDialog) {
            SearchKeyDialog(
                initial = "",
                hasKey = keyMasked.isNotBlank(),
                onDismiss = { showKeyDialog = false },
                onSave = { plain ->
                    vm.setSearchKey(plain)
                    keyMasked = vm.searchKeyMasked()
                    searchOn = vm.searchEnabled()
                    showKeyDialog = false
                },
            )
        }

        if (showThemeDialog) {
            SettingChoiceDialog(
                title = "主题",
                subtitle = "跟随系统会随手机的深色开关自动切换。",
                selected = themeKeyOf(persisted.theme),
                options = listOf(
                    ChoiceOption("system", "跟随系统", "随系统深色开关自动切换"),
                    ChoiceOption("light", "浅色", "始终使用亮色界面"),
                    ChoiceOption("dark", "深色", "纯黑背景，OLED 屏在这些区域不发光"),
                ),
                onPick = { key ->
                    vm.setTheme(key)
                    showThemeDialog = false
                },
                onDismiss = { showThemeDialog = false },
            )
        }

        if (showPermDialog) {
            SettingChoiceDialog(
                title = "默认访问权限",
                subtitle = "越界路径、备份失败、文件被外部改动，这三种情况即使「全部允许」也硬拦截。",
                selected = permKeyOf(persisted.permission),
                options = listOf(
                    ChoiceOption("readonly", "只读", "只能读与搜，写删工具从工具表移除"),
                    ChoiceOption("confirm", "需确认", "写入与删除每次弹确认并展示 diff"),
                    ChoiceOption("allow", "全部允许", "写删自动执行，仍先拍快照，可在对话里撤销"),
                ),
                onPick = { key ->
                    vm.setPermission(
                        when (key) {
                            "readonly" -> PermissionMode.READONLY
                            "allow" -> PermissionMode.ALLOW
                            else -> PermissionMode.CONFIRM
                        }
                    )
                    showPermDialog = false
                },
                onDismiss = { showPermDialog = false },
            )
        }
    }
}

/** 弹窗里的一项：key 是落盘值，label/desc 是给人看的 */
private data class ChoiceOption(val key: String, val label: String, val desc: String)

/**
 * 单选弹窗。骨架与 [SearchKeyDialog] 完全一致（18dp 圆角卡、18dp 内边距、42dp 胶囊按钮），
 * 只把输入框换成选项列表 —— 设置页里两种弹窗长得一样，才不会显得是两套东西。
 *
 * 点选项**即生效并关闭**：单选不该要求"选完再点保存"，那是把一步动作拆成两步。
 * 选中态用「强调色底 + 对勾」双保险，深色主题下低对比度也分得清。
 */
@Composable
private fun SettingChoiceDialog(
    title: String,
    subtitle: String,
    options: List<ChoiceOption>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(pal().card)
                .border(1.dp, pal().line, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
            Text(subtitle, style = ty().micro, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, pal().line, RoundedCornerShape(14.dp)),
            ) {
                options.forEachIndexed { i, o ->
                    if (i > 0) Hairline()
                    val on = o.key == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (on) pal().accentSoft.copy(alpha = 0.45f) else Color.Transparent
                            )
                            .clickable { onPick(o.key) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .border(
                                    2.dp,
                                    if (on) pal().accent else pal().line,
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(pal().accent)
                                )
                            }
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                o.label,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = pal().ink,
                            )
                            Text(o.desc, style = ty().micro, modifier = Modifier.padding(top = 1.dp))
                        }
                        if (on) {
                            Spacer(Modifier.width(8.dp))
                            Glyph(G.CHECK, 14.dp, pal().accent, 2.4f)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(pal().surfaceAlt)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("取消", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = pal().muted)
            }
        }
    }
}

/**
 * AnySearch Key 的输入框。
 *
 * 用独立的小弹窗而不是新页面：这是一次性粘贴动作，跳页会打断"我在设置里做一件事"的连续性。
 * 输入框默认留空（不回显已存的 key），避免"看起来像明文存着"；留空保存 = 清除。
 */
@Composable
private fun SearchKeyDialog(
    initial: String,
    hasKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(pal().card)
                .border(1.dp, pal().line, RoundedCornerShape(18.dp))
                .padding(18.dp),
        ) {
            Text("AnySearch Key", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
            Text(
                if (hasKey) "已经配置过。粘贴新 key 会覆盖，留空保存则清除。"
                else "粘贴你的 key（形如 as_sk_…）。留空保存 = 继续用匿名额度。",
                style = ty().micro,
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
                    Text("as_sk_…", fontSize = 13.5.sp, color = pal().hint)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.5.sp,
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
                        .background(pal().accent)
                        .clickable { onSave(text) },
                    contentAlignment = Alignment.Center,
                ) { Text("保存", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = pal().onAccent) }
            }
        }
    }
}

private fun themeLabelOf(mode: String): String = when (themeKeyOf(mode)) {
    "dark" -> "深色"
    "light" -> "浅色"
    else -> "跟随系统"
}

/**
 * 主题值的归一化。
 *
 * 落盘可能是空串或历史值，弹窗要按 key 比对选中项 —— 不归一化的话，
 * 空串会让三个选项全都不高亮，看起来像"没选中任何一项"。
 */
private fun themeKeyOf(mode: String): String = when (mode) {
    "dark" -> "dark"
    "light" -> "light"
    else -> "system"
}

/** 权限档位归一化：存储里空串的含义就是「需确认」 */
private fun permKeyOf(mode: String): String = when (mode) {
    "readonly" -> "readonly"
    "allow" -> "allow"
    else -> "confirm"
}

private fun permissionLabelOf(mode: String): String = when (permKeyOf(mode)) {
    "readonly" -> "只读"
    "allow" -> "全部允许"
    else -> "需确认"
}

/**
 * 设置项一行。可在本包内复用（用量账单页的「清空账单」也用这一行），
 * 所以不是 private —— 两处各写一套会把行高、箭头角度、配色分出两个版本。
 */
@Composable
internal fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    clickable: Boolean = true,
    modifier: Modifier = Modifier,
    icon: G? = null,
    iconTint: Color? = null,
    danger: Boolean = false,
    /** 这一行是"可展开卡片"而不是"点进去做一件事" */
    expandable: Boolean = false,
    /** 与 [expandable] 配合的当前状态；收起 = 箭头朝右，展开 = 朝下 */
    expanded: Boolean = false,
    /**
     * 标题下的一句说明。为空时只显示标题（绝大多数行都是这样）；
     * 有值时标题列占满剩余宽度，右侧的 [value] 仍然靠右对齐。
     */
    desc: String? = null,
) {
    // 与会话记录列表的分组箭头完全同一套：同一个图标、同一套角度、同一个 160ms。
    // 用旋转而不是换图标（CHEV_RIGHT ↔ CHEV_DOWN）：换图标是硬切，旋转能让人
    // 看出"是同一个东西倒过去了"，状态变化才有因果感。
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(160),
        label = "settingChevron",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            val tint = iconTint ?: pal().muted
            // 图标底用 tint 的低透明度版本：既有颜色识别，又不会让整页花掉
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = if (pal().isDark) 0.16f else 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(icon, 15.dp, tint, 2f)
            }
            Spacer(Modifier.width(11.dp))
        }
        if (desc == null) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (danger) pal().bad else pal().ink,
            )
            Spacer(Modifier.weight(1f))
        } else {
            // 有副标题时列自己吃掉剩余宽度：再叠一个 weight(1f) 的 Spacer 会把
            // 副标题挤成一半宽，长句就折成三四行
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (danger) pal().bad else pal().ink,
                )
                Text(
                    desc,
                    style = ty().micro,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (value.isNotBlank()) {
            Text(
                value,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = pal().muted,
            )
        }
        if (clickable) {
            Spacer(Modifier.width(5.dp))
            if (expandable) {
                Glyph(
                    G.CHEV_DOWN, 13.dp, pal().faint, 2.2f,
                    Modifier.graphicsLayer { rotationZ = chevronAngle },
                )
            } else {
                // 动作行（点进去做事/弹确认）保持"朝右"的既有语义，不做旋转
                Glyph(G.CHEV_RIGHT, 13.dp, pal().faint, 2.2f)
            }
        }
    }
}
