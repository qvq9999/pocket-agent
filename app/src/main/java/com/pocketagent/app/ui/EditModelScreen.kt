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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.EditDiscoverUi
import com.pocketagent.app.data.Catalog
import com.pocketagent.app.data.DEFAULT_CONTEXT_WINDOW
import com.pocketagent.app.data.ModelProfile
import com.pocketagent.app.data.VisionDisplay
import com.pocketagent.app.data.declaredContextWindow
import com.pocketagent.app.data.effectiveContextWindow
import com.pocketagent.app.data.visionDisplay

/**
 * 编辑已保存的厂商配置。
 *
 * 只覆盖"会变的东西"：显示名、端点、Key、以及启用哪些模型。
 * 模型的能力描述（思考档位、是否支持工具）不在这里改 —— 那是识别结果的产物，
 * 手改容易和厂商实际能力对不上。
 */
@Composable
fun EditModelScreen(
    vm: AppViewModel,
    providerId: String,
    onBack: () -> Unit,
) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    val provider = persisted.providers.firstOrNull { it.id == providerId }

    if (provider == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }

    val def = Catalog.find(androidx.compose.ui.platform.LocalContext.current, provider.catalogId)

    var name by remember(providerId) { mutableStateOf(provider.displayName) }
    var baseUrl by remember(providerId) { mutableStateOf(provider.baseUrl) }
    // 回填明文：用户必须能看见并修改自己保存的 Key，"只给掩码"等于不可编辑
    var key by remember(providerId) { mutableStateOf(vm.plainKeyOf(providerId)) }
    var showKey by remember { mutableStateOf(false) }

    // 模型的启用状态：用副本编辑，点保存才写回，避免边改边生效
    val models = remember(providerId) {
        mutableStateListOf<ModelProfile>().also { it.addAll(provider.models) }
    }
    var syncNote by remember(providerId) { mutableStateOf<String?>(null) }

    // 拉回厂商模型列表后合并：已保存的复用原配置（保留思考档位与启用状态），
    // 新出现的模型按模型名推导思考档位后加入，默认启用。
    androidx.compose.runtime.LaunchedEffect(vm.editDiscover) {
        val done = vm.editDiscover
        if (done is EditDiscoverUi.Done) {
            val existing = models.associateBy { it.modelId }
            val rebuilt = done.models.filter { it.isChat }.map { dm ->
                existing[dm.modelId] ?: run {
                    val spec = com.pocketagent.app.net.ThinkingRules.forModel(dm.modelId)
                    ModelProfile(
                        id = "mdl-" + java.util.UUID.randomUUID().toString().take(8),
                        providerId = providerId,
                        modelId = dm.modelId,
                        displayName = dm.modelId,
                        // 新拉到的模型不自动启用：这里是单选，自动全开等于没选
                        enabled = false,
                        thinkingStyle = spec.style,
                        thinkingLevels = spec.levels,
                        thinkingDefault = spec.defaultId,
                    )
                }
            }.toMutableList()

            // 保证恰好一个启用：原模型还在就沿用，否则选第一个
            if (rebuilt.isNotEmpty() && rebuilt.none { it.enabled }) {
                rebuilt[0] = rebuilt[0].copy(enabled = true)
            }
            models.clear()
            models.addAll(rebuilt)
            syncNote = if (rebuilt.isEmpty()) {
                "厂商没有返回可对话的模型"
            } else {
                "已同步 ${rebuilt.size} 个模型，选一个当前使用"
            }
            vm.resetEditDiscover()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(pal().pageBg)
            .navigationBarsPadding()
            .imePadding()
    ) {
        TopBar(
            title = "编辑模型",
            subtitle = def?.name ?: provider.catalogId,
            onBack = onBack,
            trailing = {
                Text(
                    "保存",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal().accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            if (key.isBlank()) {
                                vm.notice = "Key 不能为空"
                            } else {
                                vm.updateProvider(
                                    providerId = providerId,
                                    displayName = name,
                                    baseUrl = baseUrl,
                                    newKey = key,
                                    models = models.toList(),
                                    onSaved = onBack,
                                )
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp, bottom = 24.dp)
        ) {

            // ── 基本信息 ──
            SectionLabel("基本信息", modifier = Modifier.padding(top = 2.dp))

            OutlineCard(padding = 14.dp) {
                Column {
                    FieldLabel("显示名称", "仅本地显示，随便起")
                    InputBox {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it },
                            textStyle = ty().body,
                            singleLine = true,
                            cursorBrush = SolidColor(pal().accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    FieldLabel("Base URL", if (baseUrl == def?.baseUrl) "与预设一致" else "已修改")
                    InputBox {
                        BasicTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            textStyle = ty().monoCode,
                            singleLine = true,
                            cursorBrush = SolidColor(pal().accent),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        "保存时会自动补齐 /v1、折叠重复的 /v1/v1",
                        style = ty().micro,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // ── 凭证 ──
            SectionLabel("凭证", modifier = Modifier.padding(top = 16.dp))

            OutlineCard(padding = 14.dp) {
                Column {
                    FieldLabel("API Key", if (showKey) "已显示明文" else "默认隐藏")
                    InputBox(active = true) {
                        Box(Modifier.weight(1f)) {
                            if (key.isEmpty()) {
                                Text("未填写", style = ty().monoId, color = pal().faint)
                            }
                            BasicTextField(
                                value = key,
                                onValueChange = { key = it.trim() },
                                textStyle = ty().monoCode,
                                singleLine = true,
                                cursorBrush = SolidColor(pal().accent),
                                visualTransformation = if (showKey)
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Done,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        Text(
                            "粘贴",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pal().accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                        as android.content.ClipboardManager
                                    val pasted = cm.primaryClip?.takeIf { it.itemCount > 0 }
                                        ?.getItemAt(0)?.coerceToText(ctx)?.toString()
                                    if (!pasted.isNullOrBlank()) key = pasted.trim()
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).clickable { showKey = !showKey },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (showKey) "隐" else "显", fontSize = 11.sp, color = pal().muted)
                        }
                    }
                    Text(
                        "Key 用 Android Keystore 加密后存盘，磁盘上只有密文。改完可回列表点「测试」验证连通性。",
                        style = ty().micro,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // ── 模型 ──
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "可用模型",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    color = pal().muted,
                )
                Spacer(Modifier.weight(1f))
                val loading = vm.editDiscover is EditDiscoverUi.Loading
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !loading) { vm.discoverForEdit(providerId) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (loading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            color = pal().accent,
                            strokeWidth = 1.5.dp,
                        )
                    } else {
                        Glyph(G.REFRESH, 12.dp, pal().accent, 2.2f)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (loading) "获取中" else "重新获取",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pal().accent,
                    )
                }
            }

            OutlineCard(padding = 0.dp) {
                Column {
                    if (models.isEmpty()) {
                        Text(
                            "这个厂商没有对话模型。若端点或 Key 已修正，可点右上角「重新获取」。",
                            style = ty().micro,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                    models.forEachIndexed { idx, m ->
                        if (idx > 0) Hairline()
                        ModelChoiceRow(
                            model = m,
                            onSelect = {
                                // 单选：选中一个就把其余全部关掉
                                models.indices.forEach { i ->
                                    models[i] = models[i].copy(enabled = models[i].id == m.id)
                                }
                            },
                            // 视觉三态：未验证 → 已开启 → 已关闭 → 未验证。
                            // 只影响这一行发不发图，和"单选启用"互不干扰，所以单独一个点击区
                            onSetVision = { manual ->
                                models[idx] = m.copy(
                                    visionTouched = manual != null,
                                    vision = manual ?: false,
                                )
                            },
                        )
                    }
                    if (provider.mediaModels.isNotEmpty()) {
                        Hairline()
                        Text(
                            "另有 ${provider.mediaModels.size} 个媒体模型（图像/视频），不参与对话",
                            style = ty().micro,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            // ── 上下文窗口 ──
            // 只在"当前启用"的那个模型上改：单选语义下，实际发给 API 的也只有它。
            // 留空 = 自动（按模型名识别，识别不出按 200k），不会因为不知道填多少而没法用。
            val activeIdx = models.indexOfFirst { it.enabled }
            if (activeIdx >= 0) {
                val activeModel = models[activeIdx]
                SectionLabel("上下文窗口", modifier = Modifier.padding(top = 16.dp))
                OutlineCard(padding = 14.dp) {
                    Column {
                        FieldLabel(
                            "这个模型能装多少 token 的历史",
                            if (activeModel.contextWindow == null) {
                                "自动识别：" + activeModel.effectiveContextWindow()
                            } else {
                                "手动指定"
                            },
                        )
                        var ctxText by remember(activeModel.id) {
                            mutableStateOf(
                                (activeModel.contextWindow ?: activeModel.effectiveContextWindow()).toString()
                            )
                        }
                        InputBox {
                            BasicTextField(
                                value = ctxText,
                                onValueChange = { raw ->
                                    ctxText = raw.filter { it.isDigit() }.take(7)
                                    val v = ctxText.toIntOrNull()
                                    models[activeIdx] = models[activeIdx].copy(
                                        contextWindow = v?.takeIf { it >= 4_000 },
                                    )
                                },
                                textStyle = ty().monoCode,
                                singleLine = true,
                                cursorBrush = SolidColor(pal().accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Text("token", fontSize = 11.sp, color = pal().muted)
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                "32k" to 32_000,
                                "128k" to 128_000,
                                "200k" to 200_000,
                                "1M" to 1_000_000,
                            ).forEach { (label, v) ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, pal().line, RoundedCornerShape(8.dp))
                                        .clickable {
                                            ctxText = v.toString()
                                            models[activeIdx] =
                                                models[activeIdx].copy(contextWindow = v)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Text(label, fontSize = 11.sp, color = pal().muted)
                                }
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, pal().line, RoundedCornerShape(8.dp))
                                    .clickable {
                                        ctxText = (
                                            declaredContextWindow(activeModel.modelId)
                                                ?: DEFAULT_CONTEXT_WINDOW
                                            ).toString()
                                        models[activeIdx] =
                                            models[activeIdx].copy(contextWindow = null)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("自动", fontSize = 11.sp, color = pal().muted)
                            }
                        }
                        Text(
                            "实际历史预算 = 这个数 − 约 14k（留给输出和系统提示），用到 70% 时自动压缩。" +
                                "填得比模型真实窗口还大也不会更聪明：网关会拒绝，现在会自动缩短历史重试。",
                            style = ty().micro,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            val failed = vm.editDiscover as? EditDiscoverUi.Failed
            if (failed != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal().badSoft)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.WARN, 13.dp, pal().bad, 2.2f)
                    Spacer(Modifier.width(7.dp))
                    Text(failed.message, fontSize = 11.sp, color = pal().bad)
                }
            } else if (syncNote != null) {
                Text(
                    syncNote!!,
                    fontSize = 11.sp,
                    color = pal().ok,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                "「图片」默认值来自模型名匹配表（与 RikkaHub 同款的声明式表）：支持 / 仅文本 / 未知。" +
                    "点一下可改成「已开启」（你确认它能收图）或「已关闭」，手动值优先于表。" +
                    "表是声明、不是端点实测 —— 发图报 400 就改成「已关闭」。",
                style = ty().micro,
                modifier = Modifier.padding(top = 10.dp),
            )

            Text(
                "同一厂商同一时间只启用一个模型：选中的那个才会出现在输入区的切换列表里，其余保留配置但不显示。点「重新获取」可从厂商同步最新模型列表。",
                style = ty().micro,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun InputBox(
    active: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (active) pal().accent else pal().line,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ModelChoiceRow(
    model: ModelProfile,
    onSelect: () -> Unit,
    /** null = 自动；true/false = 用户强制开/关 */
    onSetVision: (Boolean?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                model.modelId,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (model.enabled) pal().ink else pal().faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tiers = model.thinkingLevels.size
            val desc = buildString {
                append(if (tiers == 0) "无思考档位" else "思考 $tiers 档")
                // 模型表声明支持、或用户手动设为开，才在描述里写出来
                when (model.visionDisplay()) {
                    VisionDisplay.DECLARED_SUPPORTED, VisionDisplay.FORCED_ON ->
                        append(" · 图片输入")
                    else -> Unit
                }
                if (!model.toolCalling) append(" · 无工具")
            }
            Text(desc, style = ty().micro, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.width(8.dp))
        // 三态标签。默认值来自「模型名匹配表」（与 rikkahub 同款的声明式表）：
        // 命中且声明支持 → 图片·支持；命中但只声明文本 → 图片·仅文本；没命中 → 图片·未知。
        // 点一下可改为「已开启 / 已关闭」，手动值优先于表。
        val auto = !model.visionTouched
        val display = model.visionDisplay()
        val label = when (display) {
            VisionDisplay.DECLARED_SUPPORTED -> "图片·支持"
            VisionDisplay.DECLARED_TEXT_ONLY -> "图片·仅文本"
            VisionDisplay.UNKNOWN -> "图片·未知"
            VisionDisplay.FORCED_ON -> "图片·已开启"
            VisionDisplay.FORCED_OFF -> "图片·已关闭"
        }
        val on = display == VisionDisplay.DECLARED_SUPPORTED || display == VisionDisplay.FORCED_ON
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (on) pal().accentSoft else Color.Transparent)
                .border(
                    1.dp,
                    when {
                        auto -> pal().line
                        on -> pal().accent.copy(alpha = 0.45f)
                        else -> pal().line
                    },
                    RoundedCornerShape(8.dp),
                )
                .clickable {
                    // 循环：按表 → 已开启 → 已关闭 → 按表
                    onSetVision(
                        when {
                            auto -> true
                            on -> false
                            else -> null
                        }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    on -> pal().accentInk
                    auto -> pal().muted
                    else -> pal().faint
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        // 单选圆点：比开关更能表达"只能选一个"
        Box(
            Modifier
                .size(19.dp)
                .clip(CircleShape)
                .border(2.dp, if (model.enabled) pal().accent else pal().line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (model.enabled) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(pal().accent))
            }
        }
    }
}


