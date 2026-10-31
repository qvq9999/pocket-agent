package com.pocketagent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.agent.MemoryHabits
import com.pocketagent.app.agent.MemoryStore

/**
 * 记忆库。
 *
 * 分三段，对应三种性质完全不同的东西：
 *
 * 1. **使用习惯**（自动归纳）：app 自己在本地数出来的行为统计，只读、可单条删除；
 * 2. **记忆笔记**（模型写入）：用户说"记住…"或模型判断值得留存时写下的 md，可展开看全文、可删；
 * 3. **最近操作**（流水）：最近 7 天做过什么，用来回答"我上周让它干过啥"，会自动过期。
 *
 * 为什么值得单独一个入口，而不是塞进设置：记忆是**模型自己也在读写**的东西。
 * 看不见就等于黑箱 —— 用户会怀疑"它到底记了什么"，而看不见的内容一旦记错，
 * 只能在对话里反复纠正。这里给出可读、可删、可核对的界面。
 *
 * 删除的语义是硬删除：没有回收站、没有软标记，删完下一轮请求里就不再有它。
 */
@Composable
fun MemoryScreen(vm: AppViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshMemory() }
    var openName by remember { mutableStateOf<String?>(null) }
    var pendingNote by remember { mutableStateOf<MemoryStore.Doc?>(null) }
    var pendingEntry by remember { mutableStateOf<MemoryHabits.Entry?>(null) }
    var pendingClearAll by remember { mutableStateOf(false) }

    // 习惯与流水分开：它们在同一份数据里，但用户的心智是"我常做什么" vs "我上次做了什么"
    val habits = vm.habitEntries.filter { it.group == MemoryHabits.Group.HABIT }
    val recent = vm.habitEntries.filter { it.group == MemoryHabits.Group.ACTIVITY }
    val empty = habits.isEmpty() && recent.isEmpty() && vm.memoryDocs.isEmpty()

    Box(Modifier.fillMaxSize().background(pal().pageBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(
                title = "记忆",
                subtitle = "只存本机 · 可逐条删除",
                onBack = onBack,
            )

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // ── 开关：先给用户一根总闸 ──
                item {
                    Spacer(Modifier.height(6.dp))
                    OutlineCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "自动记录使用习惯",
                                    style = ty().body,
                                    fontWeight = FontWeight.Medium,
                                    color = pal().ink,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "在本机统计你用哪个目录、做哪类事情、常用哪些工具。" +
                                        "只记行为不记内容；关掉后停止采集，已有条目仍可查看与删除。",
                                    style = ty().micro,
                                    lineHeight = 16.sp,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(
                                checked = vm.memoryAuto,
                                onCheckedChange = { vm.setAutoRecord(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = pal().card,
                                    checkedTrackColor = pal().accent,
                                    uncheckedThumbColor = pal().card,
                                    uncheckedTrackColor = pal().line,
                                    uncheckedBorderColor = pal().line,
                                ),
                            )
                        }
                    }
                }

                if (empty) {
                    item { EmptyMemory() }
                }

                // ── 使用习惯 ──
                if (habits.isNotEmpty()) {
                    item { SectionLabel("使用习惯", "自动归纳 · ${habits.size} 条") }
                    items(habits, key = { it.id }) { e ->
                        HabitRow(e) { pendingEntry = e }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── 记忆笔记 ──
                if (vm.memoryDocs.isNotEmpty()) {
                    item { SectionLabel("记忆笔记", "${vm.memoryDocs.size} 篇") }
                    items(vm.memoryDocs, key = { it.name }) { doc ->
                        val isOpen = openName == doc.name
                        OutlineCard(padding = 0.dp) {
                            Column {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openName = if (isOpen) null else doc.name }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            doc.title,
                                            style = ty().body,
                                            fontWeight = FontWeight.Medium,
                                            color = pal().ink,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            "${sizeText(doc.size)} · ${dateText(doc.mtime)}",
                                            style = ty().micro,
                                        )
                                    }
                                    Text(
                                        if (isOpen) "收起" else "展开",
                                        fontSize = 11.5.sp,
                                        color = pal().muted,
                                    )
                                }
                                AnimatedVisibility(
                                    visible = isOpen,
                                    enter = fadeIn(tween(120)) + expandVertically(tween(180)),
                                    exit = fadeOut(tween(90)) + shrinkVertically(tween(150)),
                                ) {
                                    Column {
                                        Hairline()
                                        Text(
                                            vm.readMemory(doc.name).orEmpty().ifBlank { "（空）" },
                                            style = ty().monoCode,
                                            fontSize = 12.sp,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(14.dp),
                                        )
                                        Hairline()
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { pendingNote = doc }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text("删除这篇记忆", style = ty().micro, color = pal().bad)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // ── 最近操作 ──
                if (recent.isNotEmpty()) {
                    item { SectionLabel("最近操作", "保留 ${MemoryHabits.ACT_DAYS} 天 · ${recent.size} 条") }
                    items(recent, key = { it.id }) { e ->
                        OutlineCard(padding = 0.dp) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        e.title,
                                        style = ty().body,
                                        fontSize = 13.5.sp,
                                        color = pal().ink,
                                    )
                                    if (e.detail.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(e.detail, style = ty().micro, maxLines = 1)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "删除",
                                    style = ty().micro,
                                    color = pal().faint,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { pendingEntry = e }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        GhostButton("清空操作记录", danger = false) { vm.clearHabitActivity() }
                    }
                }

                // ── 清空全部 ──
                if (!empty) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        GhostButton("清空全部记忆", danger = true) { pendingClearAll = true }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    PrivacyNote()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // 删除一条自动记忆
    pendingEntry?.let { e ->
        val isActivity = e.group == MemoryHabits.Group.ACTIVITY
        ConfirmDialog(
            title = if (isActivity) "删掉这条操作记录？" else "删掉这条记忆？",
            message = if (isActivity) {
                "这条记录会从列表里移除。它只影响「最近操作」的展示，不影响其它记忆。"
            } else {
                "「${e.title}」会被永久删除，之后不会再被统计到，也不会被自动记回来。" +
                    "如果它由某篇笔记推导出来，笔记本身不受影响。"
            },
            onConfirm = {
                vm.deleteHabitEntry(e.id)
                pendingEntry = null
            },
            onCancel = { pendingEntry = null },
        )
    }

    // 删除一篇笔记
    pendingNote?.let { doc ->
        ConfirmDialog(
            title = "删除「${doc.title}」？",
            message = "这篇记忆会被永久删除，模型之后检索不到，也不会被它自己重新写回来。" +
                "删除后无法恢复。",
            onConfirm = {
                vm.deleteMemory(doc.name)
                if (openName == doc.name) openName = null
                pendingNote = null
            },
            onCancel = { pendingNote = null },
        )
    }

    // 清空全部：单独一条更重的文案，因为它连"删除墓碑"一起抹掉
    if (pendingClearAll) {
        ConfirmDialog(
            title = "清空全部记忆？",
            message = "笔记、使用习惯、最近操作全部删除，包括\"哪些被你删过\"的记录。" +
                "清空后本机不再留任何记忆数据，模型之后完全从零开始。此操作不可恢复。",
            confirmText = "全部清空",
            onConfirm = {
                vm.clearAllMemory()
                openName = null
                pendingClearAll = false
            },
            onCancel = { pendingClearAll = false },
        )
    }
}

@Composable
private fun HabitRow(e: MemoryHabits.Entry, onDelete: () -> Unit) {
    OutlineCard(padding = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.title, style = ty().body, fontSize = 13.5.sp, color = pal().ink)
                Spacer(Modifier.height(2.dp))
                Text(e.detail, style = ty().micro, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "删除",
                style = ty().micro,
                color = pal().faint,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EmptyMemory() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 26.dp)) {
        Text("还没有记忆", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "· 用几次文件或 Word，这里会自动归纳出\"常做什么、常用哪个目录\"；\n" +
                "· 对话里说一句「记住我的写作偏好是简洁、不要客套」，它会把要点存成笔记。",
            style = ty().micro,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun PrivacyNote() {
    Column(Modifier.padding(horizontal = 6.dp)) {
        Hairline()
        Spacer(Modifier.height(10.dp))
        Text(
            "采集范围：工具名、任务类型、工作区目录名、成功/失败、耗时。" +
                "不记录文件名、文件内容与聊天原文；目录名里出现手机号、证件号这类长数字时不记录。",
            style = ty().micro,
            color = pal().faint,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "存储与使用：全部存在 app 私有目录，没有云端备份。" +
                "每次对话会把上面这段\"使用习惯\"的极简摘要随请求发给模型服务商，" +
                "用来少问你几句；笔记只有在模型主动检索时才会被读取。",
            style = ty().micro,
            color = pal().faint,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "删除：直接抹掉文件，不进回收站；删过的条目会被记入\"不要再自动记\"的黑名单，" +
                "所以它不会自己长回来。已经发出去的请求无法收回，那是模型的上下文，不是你的记忆库。",
            style = ty().micro,
            color = pal().faint,
            lineHeight = 16.sp,
        )
    }
}

private fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(java.util.Locale.CHINA, "%.1f KB", bytes / 1024.0)
    else -> String.format(java.util.Locale.CHINA, "%.1f MB", bytes / 1048576.0)
}

private fun dateText(ms: Long): String = runCatching {
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(ms))
}.getOrDefault("")
