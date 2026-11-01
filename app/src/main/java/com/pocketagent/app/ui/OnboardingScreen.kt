package com.pocketagent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            "模型已经接好了。最后一步：划一个目录给它 —— AI 只能在这里读写，别处碰不到，随时可以换。"
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

            // 推荐区块只在"接模型"这一步出现。
            // 重看时也显示：从设置点「使用引导」的人是在主动复习，
            // 想看一遍"当初该怎么弄到 Key"是完全合理的需求，不该因为已经配好就被藏起来。
            if (step == OnboardStage.Welcome) {
                Spacer(Modifier.height(16.dp))
                RecommendBlock(alreadyHasModel = hasModel)
                Spacer(Modifier.height(4.dp))
                Text(
                    // 不写"点下面的按钮"：重看时下面是「下一步」，不是「添加模型」
                    "拿到 Key 后，在 设置 → 模型与能力 → 模型 里选同一家厂商，端点已经填好了。",
                    style = ty().micro,
                )
            }

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

// ───────────── 推荐厂商 ─────────────

/**
 * 入门推荐的三家。
 *
 * 为什么不复用 providers.json 里的 docsUrl：那个字段指向"厂商文档首页"，
 * 而用户在这里真正需要的是"能马上注册 / 创建 Key 的那一页"。
 * MiMo 这条尤其要紧 —— 链接带 ref 参数，点进去邀请码已自动绑定，
 * 比"先复制 6 位码、再找输入框粘贴"少三步，转化差别很实在。
 *
 * 文案只讲"为什么选它"，不讲参数：端点、模型名都在 providers.json 里，
 * 用户拿到 Key 后走「添加模型」选同一家厂商就自动填好了。
 */
private data class Recommend(
    val name: String,
    val badge: String,
    val blurb: String,
    val keyUrl: String,
    /** true = 白送/免费（绿标），false = 常规卖点（蓝标） */
    val free: Boolean = false,
    /** 有邀请码时额外显示一行，可单独复制 */
    val inviteCode: String = "",
    /** 链接的主机名。整串 URL 太长且没人读，用户认的是域名 */
    val host: String = "",
)

private val RECOMMENDED = listOf(
    Recommend(
        name = "小米 MiMo",
        badge = "送 ¥10",
        blurb = "注册后邀请码自动填入，双方各得 10 元体验金，40 天有效。",
        keyUrl = "https://platform.xiaomimimo.com?ref=BL6U65",
        free = true,
        inviteCode = "BL6U65",
        host = "platform.xiaomimimo.com",
    ),
    Recommend(
        name = "Agnes",
        badge = "无限期免费",
        blurb = "文本、图像、视频全模态免费，不绑卡，限速约 20 次/分钟。",
        keyUrl = "https://platform.agnes-ai.com/",
        free = true,
        host = "platform.agnes-ai.com",
    ),
    Recommend(
        name = "DeepSeek",
        badge = "便宜好用",
        blurb = "国内直连不必翻墙，价格低，日常问答和长文本都够用。",
        keyUrl = "https://platform.deepseek.com/api_keys",
        host = "platform.deepseek.com",
    ),
)

/**
 * 推荐区块。
 *
 * 两个操作刻意分开：点整行 = 打开注册页（主路径），「复制地址」= 复制 URL，
 * 「复制」= 只复制邀请码。三种需求都要照顾到 —— 有人在手机上直接点开，
 * 也有人习惯把链接发到微信再在电脑上注册。
 */
@Composable
private fun RecommendBlock(alreadyHasModel: Boolean) {
    val ctx = LocalContext.current

    fun copyToClipboard(text: String, what: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val ok = cm != null && runCatching {
            cm.setPrimaryClip(ClipData.newPlainText("PocketAgent", text))
        }.isSuccess
        Toast.makeText(
            ctx,
            if (ok) "$what 已复制" else "复制失败：剪贴板不可用",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun openUrl(url: String) {
        // 没装浏览器时 startActivity 会抛 ActivityNotFoundException，不能让它冒到主线程
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(pal().surface)
            .border(1.dp, pal().line, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            // 已经配好模型的人是从设置进来复习的，再写"推荐从这三家开始"就莫名其妙
            if (alreadyHasModel) "想换个厂商？这三家可以领额度" else "推荐从这三家开始",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = pal().ink,
        )
        RECOMMENDED.forEach { r ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal().card)
                    .border(1.dp, pal().lineSoft, RoundedCornerShape(10.dp))
                    .clickable { openUrl(r.keyUrl) }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = pal().ink)
                    Spacer(Modifier.width(7.dp))
                    RecommendBadge(r.badge, r.free)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "去获取 ›",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = pal().accent,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(r.blurb, fontSize = 11.sp, lineHeight = 16.sp, color = pal().muted)

                if (r.inviteCode.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("邀请码", fontSize = 10.5.sp, color = pal().hint)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            r.inviteCode,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pal().ink,
                        )
                        Spacer(Modifier.width(8.dp))
                        // 独立的点击区域：点整行是"打开注册页"，这里只复制邀请码
                        Text(
                            "复制",
                            fontSize = 10.5.sp,
                            color = pal().accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { copyToClipboard(r.inviteCode, "邀请码") }
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.host,
                        fontSize = 10.sp,
                        color = pal().faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "复制地址",
                        fontSize = 10.5.sp,
                        color = pal().accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { copyToClipboard(r.keyUrl, "网址") }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendBadge(text: String, free: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (free) pal().okSoft else pal().accentSoft)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text,
            fontSize = 9.5.sp,
            color = if (free) pal().ok else pal().accentInk,
        )
    }
}
