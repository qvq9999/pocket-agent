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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.AppViewModel.OnboardStage

/**
 * 新手引导（独立页）。
 *
 * 以前它是会话空态区里的一张卡：和"你好 / 示例 / 版本号"挤在同一屏，内容一多就要滚，
 * 也不像一段流程。独立成页之后有三件事变得自然：
 *  1. 有**进度**（第 N 步 / 共 3 步），用户知道还有多久；
 *  2. 每步右上角能摆一张"三步状态"示意，做完的步骤当场打勾；
 *  3. 复习说明（设置 → 使用引导）不用再切走当前会话 —— 那只是为了把卡片放进空会话。
 *
 * 步骤仍按**现状推导**，不存"第几步"：用户点了「添加模型」跳走再回来，
 * 这一页会自动落在他该在的那一步，不需要任何"返回时通知一下"的胶水代码。
 */
@Composable
fun OnboardingScreen(
    vm: AppViewModel,
    onClose: () -> Unit,
    onOpenModels: () -> Unit,
    onPickWorkspace: () -> Unit,
) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    val hasModel = vm.chatModels().isNotEmpty()
    val hasWs = vm.workspace != null && !vm.workspaceBroken
    val sent = persisted.onboard.completedAt > 0L
    val replaying = vm.replayStage != null

    // 重看走"按步翻"，否则按现状推导
    val step = vm.replayStage ?: when {
        !hasModel -> OnboardStage.Welcome
        !hasWs -> OnboardStage.PickWorkspace
        else -> OnboardStage.TryIt
    }
    val index = when (step) {
        OnboardStage.Welcome -> 1
        OnboardStage.PickWorkspace -> 2
        else -> 3
    }

    val title = when (step) {
        OnboardStage.Welcome -> "先接一个模型"
        OnboardStage.PickWorkspace -> "选一个工作区"
        else -> "说第一句话"
    }
    val desc = when (step) {
        OnboardStage.Welcome ->
            "它不内置模型。填入你自己的 API Key，请求直接从这台手机发到厂商，中途不经过我们。"
        OnboardStage.PickWorkspace ->
            "AI 只能在这个目录里读写文件，别处碰不到。随时可以换。"
        else ->
            "它能读写工作区里的文件，也能直接生成 Word 和 PPT —— 不需要额外下载任何东西。"
    }

    // 主按钮：条件还没满足时是"去做那件事"，满足了就是"下一步 / 开始使用"
    val primaryText = when (step) {
        OnboardStage.Welcome -> if (hasModel || replaying && hasModel) "下一步" else "添加模型"
        OnboardStage.PickWorkspace -> if (hasWs || replaying && hasWs) "下一步" else "选择目录"
        else -> "开始使用"
    }
    val onPrimary: () -> Unit = {
        when (step) {
            OnboardStage.Welcome -> if (!hasModel) onOpenModels() else vm.advanceOnboard()
            OnboardStage.PickWorkspace -> if (!hasWs) onPickWorkspace() else vm.advanceOnboard()
            else -> {
                vm.finishOnboard()
                onClose()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal().pageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── 进度：三段横条 + 步数 ──
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(pal().surfaceAlt)
                        // ✕ 只是"先不看"：不写 dismissedAt，下次进来还在
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.CLOSE, 13.dp, pal().muted, 2.2f) }
                Spacer(Modifier.weight(1f))
                Text("第 $index 步 / 共 3 步", style = ty().micro)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < index) pal().accent else pal().lineSoft)
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text(
                title,
                fontSize = 23.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold,
                color = pal().ink,
            )
            Spacer(Modifier.height(8.dp))
            Text(desc, fontSize = 13.sp, lineHeight = 21.sp, color = pal().muted)

            // ── 三步状态示意：完成打勾，比"只给一个按钮"多一层全局感 ──
            Spacer(Modifier.height(18.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(pal().surface)
                    .border(1.dp, pal().line, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepLine(
                    glyph = G.CUBE,
                    tint = pal().accent,
                    title = "模型",
                    meta = if (hasModel) "${vm.chatModels().size} 个可用" else "还没添加",
                    done = hasModel,
                )
                StepLine(
                    glyph = G.FOLDER,
                    tint = pal().media,
                    title = "工作区",
                    meta = if (hasWs) "${vm.workspace?.name.orEmpty()} · 可读写" else "还没选",
                    done = hasWs,
                )
                StepLine(
                    glyph = G.DOC,
                    tint = pal().ok,
                    title = "发第一句",
                    meta = if (sent) "已经聊过了" else "还没开口",
                    done = sent,
                )
            }

            if (step == OnboardStage.TryIt) {
                Spacer(Modifier.height(14.dp))
                listOf(
                    "把这里带日期的 Word 改个名",
                    "总结这个目录里的笔记",
                    "帮我做一份三页的 PPT",
                ).forEach { s ->
                    SampleRow(s) {
                        vm.pendingInput = s
                        vm.finishOnboard()
                        onClose()
                    }
                    Spacer(Modifier.height(7.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "不做图片和图表；删除类操作始终会先问你。",
                    style = ty().micro,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── 底部：跳过 / 主按钮 ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, pal().line, RoundedCornerShape(12.dp))
                    .clickable {
                        vm.dismissOnboard()
                        onClose()
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text("跳过", fontSize = 13.5.sp, color = pal().muted) }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal().accent)
                    .clickable(onClick = onPrimary),
                contentAlignment = Alignment.Center,
            ) {
                Text(primaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal().onAccent)
            }
        }
    }
}

/** 三步状态行：图标 + 名称 + 现状 + ✓/待完成 */
@Composable
private fun StepLine(
    glyph: G,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    meta: String,
    done: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = if (pal().isDark) 0.18f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, 13.dp, tint, 1.9f) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = pal().ink)
            Text(meta, style = ty().micro)
        }
        if (done) {
            Box(
                Modifier.size(16.dp).clip(CircleShape).background(pal().okSoft),
                contentAlignment = Alignment.Center,
            ) { Glyph(G.CHECK, 10.dp, pal().ok, 2.6f) }
        } else {
            Text("待完成", fontSize = 10.sp, color = pal().faint)
        }
    }
}

/** 示例句子：点一下就把这句填进会话输入框（不直接发送，用户还能改） */
@Composable
private fun SampleRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .border(1.dp, pal().line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.5.sp, color = pal().ink, modifier = Modifier.weight(1f))
        Glyph(G.CHEV_RIGHT, 12.dp, pal().faint, 2.2f)
    }
}
