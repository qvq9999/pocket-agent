package com.pocketagent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pocketagent.app.agent.DiffLine
import com.pocketagent.app.agent.ToolInfo
import com.pocketagent.app.agent.ToolPolicy

/** 工具 → 图标。读的用文件夹、写的用加号、删的用垃圾桶，让人一眼看出"它动了什么" */
private fun glyphOf(name: String): G = when (name) {
    ToolPolicy.LIST_DIR -> G.FOLDER
    ToolPolicy.READ_FILE -> G.FOLDER
    ToolPolicy.STAT -> G.INFO
    ToolPolicy.WRITE_FILE -> G.PLUS
    ToolPolicy.REPLACE_IN_FILE -> G.SLIDERS
    ToolPolicy.MOVE_PATH -> G.CHEV_RIGHT
    ToolPolicy.DELETE_PATH -> G.TRASH
    else -> G.CUBE
}

/**
 * 对话里的工具卡片。
 *
 * 折叠态只占一行：图标 + 动作 + 状态 + 变更量。默认就该是一行 ——
 * 一个任务里可能有十几次工具调用，每条都展开会把回复冲得看不见。
 * 展开才给完整的 diff / 参数 / 撤销入口。
 */
@Composable
fun ToolCallRow(
    tool: ToolInfo,
    inline: Boolean = false,
    /**
     * 展开态由调用方托管。
     *
     * 原来用 `remember { mutableStateOf(false) }` 存在卡片内部：卡片一旦被列表回收
     * （滚出屏幕、或者这次回复还在流式、列表项被重建），状态就回到"收起"，
     * 用户刚点开的又自己关回去 —— 表现就是"点了没反应 / 有时打不开"。
     */
    expanded: Boolean,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
) {
    val angle by animateFloatAsState(if (expanded) 0f else -90f, tween(150), label = "toolChevron")

    val okColor = when {
        tool.undone -> pal().hint
        tool.ok -> pal().ok
        else -> pal().bad
    }
    val statusText = when {
        tool.undone -> "已撤销"
        tool.ok -> ""
        else -> "失败"
    }

    // 卡片本体。两处复用：
    //  · 独立成条（inline = false）：与 AI 气泡左缘对齐（26dp 头像 + 8dp 间距），底色 surfaceAlt；
    //  · 内联进 AI 气泡（inline = true）：不再缩进，底色换白 —— 它正长在 aiBubble 的灰底上，
    //    同色会糊成一片，也就分不清"这一条卡片属于哪次回复"。
    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (inline) pal().card else pal().surfaceAlt)
                .border(1.dp, pal().line, RoundedCornerShape(12.dp)),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // 折叠时整行可点：比只点那个小箭头友好得多
                    .clickable(onClick = onToggle)
                    .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(glyphOf(tool.name), 13.dp, if (tool.ok) pal().muted else pal().bad, 1.9f)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ToolPolicy.labelOf(tool.name),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (tool.ok) pal().ink else pal().bad,
                            maxLines = 1,
                        )
                        if (tool.note.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                tool.note,
                                fontSize = 10.5.sp,
                                color = if (tool.undone) pal().hint else pal().faint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // 失败原因：折叠时给 3 行预览（2 行会把"可用：ppt-"这种关键信息切掉），
                    // 展开后由下面的正文区显示全文 —— 两处同时渲染就是同一个错误说两遍
                    if (tool.summary.isNotBlank() && !tool.ok && !expanded) {
                        Text(
                            tool.summary,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = pal().bad,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (statusText.isNotBlank()) {
                    Text(statusText, fontSize = 10.5.sp, color = okColor, maxLines = 1)
                    Spacer(Modifier.width(5.dp))
                } else if (tool.ok) {
                    Glyph(G.CHECK, 11.dp, pal().ok, 2.6f)
                    Spacer(Modifier.width(5.dp))
                }
                Glyph(
                    G.CHEV_DOWN, 12.dp, pal().faint, 2.2f,
                    Modifier.graphicsLayer { rotationZ = angle },
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(150)) + fadeIn(tween(120)),
                exit = shrinkVertically(tween(130)),
            ) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    val detail = tool.summary.ifBlank { tool.preview.take(160) }
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = if (tool.ok) pal().hint else pal().bad,
                        )
                    }
                    if (tool.diff.isNotEmpty()) {
                        Spacer(Modifier.height(7.dp))
                        DiffBlock(tool.diff)
                    }
                    if (tool.args.isNotBlank() && !tool.ok) {
                        Spacer(Modifier.height(7.dp))
                        Text("参数", fontSize = 10.sp, color = pal().faint)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tool.args.take(400),
                            style = ty().monoId,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildString {
                                if (tool.ms > 0) append("${tool.ms} ms")
                                if (tool.undoId.isNotBlank()) {
                                    if (isNotEmpty()) append(" · ")
                                    append(if (tool.undone) "已恢复原状" else "改动已存入快照")
                                }
                            },
                            style = ty().micro,
                        )
                        Spacer(Modifier.weight(1f))
                        if (tool.undoId.isNotBlank() && !tool.undone) {
                            Text(
                                "撤销",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = pal().accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .clickable(onClick = onUndo)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (inline) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) { body() }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Spacer(Modifier.width(34.dp))
            body()
        }
    }
}

/** 统一的 diff 展示：审批弹层与工具卡片共用同一套配色与排版 */
@Composable
fun DiffBlock(lines: List<DiffLine>, maxLines: Int = 40) {
    val shown = lines.take(maxLines)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, pal().lineSoft, RoundedCornerShape(9.dp))
            .background(pal().card)
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        shown.forEach { line ->
            val (bg, fg, mark) = when (line.kind) {
                DiffLine.ADD -> Triple(pal().diffAddBg, pal().diffAddInk, "+ ")
                DiffLine.DEL -> Triple(pal().diffDelBg, pal().diffDelInk, "− ")
                else -> Triple(pal().card, pal().hint, "  ")
            }
            Text(
                mark + line.text,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = ty().mono,
                color = fg,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }
        if (lines.size > shown.size) {
            Text(
                "…（还有 ${lines.size - shown.size} 行）",
                fontSize = 10.5.sp,
                color = pal().faint,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 写操作的审批弹层。
 *
 * 刻意不提供「始终允许」以外的快捷勾选：删除与覆盖是**不可逆**的
 *（快照只能救一次，用户清缓存就没了），所以每一次都要看见 diff 才能点。
 * 「本会话允许」是给批量改同一个文件时的折中，且只对**这一类工具**生效。
 */
@Composable
fun ToolApprovalDialog(
    title: String,
    target: String,
    diff: List<DiffLine>,
    note: String,
    danger: Boolean,
    onDeny: () -> Unit,
    onAllowOnce: () -> Unit,
    onAllowAlways: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDeny,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pal().scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDeny,
                ),
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(130)) + scaleIn(tween(170), initialScale = 0.94f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Column(
                    Modifier
                        .width(322.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(pal().card)
                        .border(1.dp, pal().line, RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph(if (danger) G.WARN else G.SHIELD, 15.dp, if (danger) pal().bad else pal().accent, 2f)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pal().ink,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        target,
                        style = ty().monoId,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (note.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(note, fontSize = 11.sp, color = pal().hint)
                    }
                    if (diff.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                            DiffBlock(diff, maxLines = 200)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ApproveButton("拒绝", primary = false, danger = false, onClick = onDeny)
                        Spacer(Modifier.width(8.dp))
                        ApproveButton(
                            if (danger) "确认删除" else "允许一次",
                            primary = true,
                            danger = danger,
                            onClick = onAllowOnce,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onAllowAlways)
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            // 文案必须和真实行为一致：删除只在本会话内放行，不会写进永久清单。
                            // 说成"永久允许"却每次重启又问，比干脆不给这个选项更糟。
                            if (danger) "本会话内允许删除，不再逐次询问（重启后重新确认）"
                            else "永久允许这类操作，可在设置里撤销",
                            fontSize = 11.sp,
                            color = pal().hint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApproveButton(text: String, primary: Boolean, danger: Boolean, onClick: () -> Unit) {
    val bg = when {
        primary && danger -> pal().bad
        primary -> pal().accent
        else -> pal().surfaceAlt
    }
    val fg = when {
        primary -> pal().pageBg
        else -> pal().ink
    }
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}
