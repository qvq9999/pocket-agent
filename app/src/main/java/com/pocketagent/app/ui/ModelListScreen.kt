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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.data.Catalog
import com.pocketagent.app.data.PermissionMode
import com.pocketagent.app.data.ProviderInstance
import com.pocketagent.app.data.VisionDisplay
import com.pocketagent.app.data.capabilitySource
import com.pocketagent.app.data.visionDisplay
import com.pocketagent.app.agent.ToolPolicy
import com.pocketagent.app.TestUi
import androidx.compose.foundation.layout.size

@Composable
fun ModelListScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    val providers = persisted.providers
    val selectedId = persisted.selectedModelId

    Column(Modifier.fillMaxSize().background(pal().pageBg).navigationBarsPadding()) {
        TopBar(
            title = "模型",
            onBack = onBack,
            trailing = { SquareIcon(G.PLUS, onAdd) },
        )

        if (providers.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 只陈述"还没有模型"是不够的：用户此刻真正想知道的是
                // "为什么非要填 Key、这 Key 会不会被人拿走"
                Text("还没有模型", fontSize = 15.sp, color = pal().ink)
                Spacer(Modifier.height(7.dp))
                Text(
                    "用你自己的 API Key 接一个，之后一直用它。\nKey 只保存在这台手机里。",
                    style = ty().micro,
                    lineHeight = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                GhostButton("添加模型", onClick = onAdd)
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(providers, key = { it.id }) { p ->
                ProviderCard(
                    vm = vm,
                    provider = p,
                    selectedModelId = selectedId,
                    // 工具数量必须是"裁剪后"的：只读模式下写/删工具会被移除，
                    // 显示写死的 allTools.size 就是在骗人
                    permission = when (persisted.permission) {
                        "readonly" -> PermissionMode.READONLY
                        "allow" -> PermissionMode.ALLOW
                        else -> PermissionMode.CONFIRM
                    },
                    onEdit = { onEdit(p.id) },
                )
            }
            item {
                Text(
                    "在对话输入区点模型 chip 即可切换。带「仅媒体」的厂商不会出现在切换列表里。",
                    style = ty().micro,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/** 能力小胶囊：能不能做只靠底色区分，右侧一律给文字，不靠颜色猜 */
@Composable
private fun CapPill(text: String, on: Boolean, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) pal().accentSoft else pal().surfaceAlt)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (on) pal().accentInk else pal().faint,
        )
    }
}

@Composable
private fun ProviderCard(
    vm: AppViewModel,
    provider: ProviderInstance,
    selectedModelId: String,
    permission: PermissionMode,
    onEdit: () -> Unit,
) {
    val def = Catalog.find(
        androidx.compose.ui.platform.LocalContext.current,
        provider.catalogId,
    )
    var confirmRemove by remember { mutableStateOf(false) }

    OutlineCard(padding = 13.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VendorMark(
                    slug = def?.icon?.ifBlank { null },
                    // 自定义厂商在目录里查不到，退回名字首字 —— 否则是一个空方块
                    fallbackText = def?.fallbackText?.takeIf { it.isNotBlank() }
                        ?: provider.displayName.take(1).uppercase(),
                    fallbackColor = parseHex(def?.fallbackColor ?: "#5F6368"),
                    dim = 34.dp,
                    glyphDim = 19.dp,
                    round = false,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(provider.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
                    Text(provider.baseUrl, style = ty().monoId, maxLines = 1)
                }
                val (label, bg, fg) = when {
                    provider.models.isEmpty() -> Triple("仅媒体", pal().mediaSoft, pal().media)
                    else -> Triple("可用", pal().okSoft, pal().ok)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(bg)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg)
                }
            }

            // 一个厂商同时只有一个模型处于启用状态（updateProvider 里做了归一化），
            // 所以卡片上只展示这一个 —— 以前把前 3 个模型都摊成 chip，看着像"配了一堆"，
            // 其实同一时间只有一个在跑。要换模型走「编辑」页的单选列表。
            val current = provider.models.firstOrNull { it.enabled } ?: provider.models.firstOrNull()
            if (current != null) {
                val inUse = current.id == selectedModelId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (inUse) pal().modelChipBg else pal().surfaceAlt)
                        .clickable { vm.select(current.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("当前模型", fontSize = 10.5.sp, color = pal().hint)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        current.modelId,
                        style = ty().monoId,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (inUse) {
                        // 行底本身就是 modelChipBg，标签再用同一个色就"化"没了，
                        // 所以给白底 + 强调色细描边（和模型弹窗里的档位标签一致）
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pal().card)
                                .border(
                                    1.dp,
                                    pal().accent.copy(alpha = 0.35f),
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "对话中",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = pal().modelChipInk,
                            )
                        }
                    } else {
                        Text(
                            // 「切换」容易被读成"切换到别的模型"，实际动作是"用这个"
                            "设为当前",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pal().accent,
                        )
                    }
                }

                // ── 这个模型能干什么 ──
                // 依据是「模型名匹配表」（与 rikkahub 同款的声明式表，见 data/ModelCapabilities.kt），
                // 不是端点实测 —— 官方 OpenAI 兼容端点根本不返回能力字段，界面必须交代来源。
                // 表里没有这个模型就显示「未知」，不替用户猜。
                val tools = if (current.toolCalling) ToolPolicy.visibleIn(permission) else emptySet()
                val vision = current.visionDisplay()
                var capDetail by remember(current.id) { mutableStateOf<String?>(null) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CapPill("文本", true)
                    CapPill(
                        text = when (vision) {
                            VisionDisplay.DECLARED_SUPPORTED -> "图片·支持"
                            VisionDisplay.DECLARED_TEXT_ONLY -> "图片·仅文本"
                            VisionDisplay.UNKNOWN -> "图片·未知"
                            VisionDisplay.FORCED_ON -> "图片·已开启"
                            VisionDisplay.FORCED_OFF -> "图片·已关闭"
                        },
                        on = vision == VisionDisplay.DECLARED_SUPPORTED ||
                            vision == VisionDisplay.FORCED_ON,
                        onClick = { capDetail = if (capDetail == "vision") null else "vision" },
                    )
                    CapPill(
                        text = if (current.toolCalling) "工具 ${tools.size}" else "无工具",
                        on = current.toolCalling,
                        onClick = if (current.toolCalling) ({
                            capDetail = if (capDetail == "tools") null else "tools"
                        }) else null,
                    )
                }

                if (capDetail == "vision") {
                    Column(
                        Modifier.padding(top = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            "输入：JPEG / PNG / WebP → 统一转 JPEG，长边 1024；单条最多 4 张，随消息以 image_url(base64) 发出",
                            style = ty().micro,
                            lineHeight = 16.sp,
                        )
                        Text(
                            when (vision) {
                                VisionDisplay.DECLARED_SUPPORTED ->
                                    "依据：模型名匹配表命中「${current.capabilitySource()}」→ 声明支持图片输入"
                                VisionDisplay.DECLARED_TEXT_ONLY ->
                                    "依据：模型名匹配表命中「${current.capabilitySource()}」→ 声明只支持文本，发图大概率被拒"
                                VisionDisplay.UNKNOWN ->
                                    "依据：模型名匹配表里没有这个模型 → 图片能力未知，能否发图只能实测"
                                VisionDisplay.FORCED_ON ->
                                    "依据：你手动设为「已开启」，优先于模型表"
                                VisionDisplay.FORCED_OFF ->
                                    "依据：你手动设为「已关闭」，选图后会拦下"
                            },
                            style = ty().micro,
                            lineHeight = 16.sp,
                            color = when (vision) {
                                VisionDisplay.UNKNOWN, VisionDisplay.DECLARED_TEXT_ONLY -> pal().warn
                                else -> pal().muted
                            },
                        )
                        Text(
                            "说明：这是按模型名声明的能力表，不是端点自报的能力；端点若拒绝会返回 400",
                            style = ty().micro,
                            lineHeight = 16.sp,
                        )
                    }
                }

                if (capDetail == "tools" && tools.isNotEmpty()) {
                    Text(
                        tools.sortedBy { ToolPolicy.labelOf(it) }
                            .joinToString("、") { ToolPolicy.labelOf(it) },
                        style = ty().micro,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "以上是本次实际下发给模型的工具；模型端会不会调用，取决于端点自己的实现",
                        style = ty().micro,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    if (permission == PermissionMode.READONLY) {
                        Text(
                            "只读模式：写、删、执行命令类工具已从工具表移除",
                            style = ty().micro,
                            color = pal().warn,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }

            if (provider.mediaModels.isNotEmpty()) {
                Text(
                    "媒体模型 ${provider.mediaModels.size} 个，不参与对话",
                    style = ty().micro,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ── 连通性测试结果 ──
            val test = vm.tests[provider.id]
            if (test != null) {
                val (icon, tint, text) = when (test) {
                    is TestUi.Testing -> Triple(null, pal().muted, "正在测试连通性…")
                    is TestUi.Ok -> Triple(G.CHECK, pal().ok, "${test.ms} ms · ${test.summary}")
                    is TestUi.Fail -> Triple(G.WARN, pal().bad, test.reason)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 9.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (test) {
                                is TestUi.Ok -> pal().okSoft
                                is TestUi.Fail -> pal().badSoft
                                else -> pal().surfaceAlt
                            }
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Glyph(icon, 13.dp, tint, 2.2f)
                    } else {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = pal().muted,
                            strokeWidth = 1.6.dp,
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = if (test is TestUi.Fail) pal().bad else pal().ink,
                    )
                }
            }

            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { vm.testProvider(provider.id) }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.CHECK, 13.dp, pal().ok, 2.2f)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (vm.tests[provider.id] is TestUi.Testing) "测试中" else "测试",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pal().ok,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.SLIDERS, 13.dp, pal().accent, 2f)
                    Spacer(Modifier.width(5.dp))
                    Text("编辑", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = pal().accent)
                }
                Spacer(Modifier.weight(1f))
                // 和左边「测试 / 编辑」同一套形状（图标 + 文字 + 9dp 圆角点击区），
                // 只是换成危险色：移除是会连 API Key 一起删掉的操作，不该长得像普通文字链接
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { confirmRemove = true }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.TRASH, 13.dp, pal().bad, 1.9f)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "移除",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pal().bad,
                    )
                }
            }

            // 删除厂商会连 API Key 一起清掉，必须二次确认（Popup 不占布局，放在卡片里就行）
            if (confirmRemove) {
                ConfirmDialog(
                    title = "移除这个厂商？",
                    message = "「${provider.displayName}」及其 ${provider.models.size} 个模型配置会被删除，" +
                        "已保存的 API Key 也会一并清除，无法恢复。",
                    confirmText = "移除",
                    onConfirm = {
                        vm.removeProvider(provider.id)
                        confirmRemove = false
                    },
                    onCancel = { confirmRemove = false },
                )
            }
        }
    }
}
