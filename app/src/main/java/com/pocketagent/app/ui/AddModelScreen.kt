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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.DiscoveryUi
import com.pocketagent.app.data.DiscoveredModel
import com.pocketagent.app.data.FailureKind
import com.pocketagent.app.data.ProviderDef
import com.pocketagent.app.net.UrlNormalizer

/**
 * 自定义厂商的固定 id。目录里没有它，所以厂商标走 fallbackText/falbackColor。
 *
 * 关键约定：它是**空壳** —— 不预填端点、不预置模型、不落库任何东西。
 * 认不出模型就什么都没有，认出来了才在最后一步写 ProviderInstance，
 * 和目录里的厂商走同一条路。
 */
const val CUSTOM_PROVIDER_ID = "custom"

/** 添加模型页那张「自定义模型」卡片用的 def：只有名字和图标回退，其余留给用户填 */
fun customProviderDef(): ProviderDef = ProviderDef(
    id = CUSTOM_PROVIDER_ID,
    name = "自定义模型",
    fallbackText = "自",
    fallbackColor = "#5F6368",
    region = "custom",
    baseUrl = "",
)

// ───────────────── 08 厂商预设 ─────────────────

@Composable
fun PresetScreen(
    vm: AppViewModel,
    onPick: (ProviderDef) -> Unit,
    onBack: () -> Unit,
) {
    val providers = vm.catalog?.providers.orEmpty()
    val groups = listOf(
        "cn" to "国内",
        "intl" to "海外",
        "local" to "本地 / 局域网",
    )

    Column(Modifier.fillMaxSize().background(pal().pageBg).navigationBarsPadding()) {
        TopBar(title = "添加模型", onBack = onBack)

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Box(Modifier.padding(bottom = 10.dp)) {
                    InfoBox("选一个厂商，端点已按官方文档预填。填上 API Key 后点「识别模型」，会自动拉取该账号可用的模型。")
                }
            }
            // 自定义：独立一张卡，放在厂商分组之上。
            // 它和下面那些行的区别是实质性的 —— 不预设端点、也不走 /models 识别，
            // 所以不该混在"国内/海外"里当第 N 个厂商
            item {
                OutlineCard(padding = 0.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(customProviderDef()) }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(pal().accentSoft),
                            contentAlignment = Alignment.Center,
                        ) { Glyph(G.SLIDERS, 18.dp, pal().accent, 2f) }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "自定义模型",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = pal().ink,
                            )
                            Text(
                                "填端点和 Key，识别后从列表里挑，和上面的厂商一样",
                                style = ty().micro,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        Glyph(G.CHEV_RIGHT, 14.dp, pal().faint, 2.3f)
                    }
                }
            }

            groups.forEach { (key, label) ->
                val list = providers.filter { it.region == key }
                if (list.isNotEmpty()) {
                    item { SectionLabel(label) }
                    items(list) { def ->
                        VendorRow(def) { onPick(def) }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun VendorRow(def: ProviderDef, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VendorMark(
            slug = def.icon.ifBlank { null },
            fallbackText = def.fallbackText,
            fallbackColor = parseHex(def.fallbackColor),
            dim = 34.dp,
            glyphDim = 19.dp,
            round = false,
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(def.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
            Text(def.baseUrl, style = ty().monoId, maxLines = 1)
        }
        Glyph(G.CHEV_RIGHT, 14.dp, pal().line, 2.3f)
    }
}

// ───────────────── 09 填 Key + 10 选择模型 ─────────────────

@Composable
fun AddModelScreen(
    def: ProviderDef,
    vm: AppViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(def.baseUrl) }
    var showKey by remember { mutableStateOf(true) }
    val discovery = vm.discovery
    // 目录里的厂商都按官方要求必须有 Key；自定义端点可能是本地服务，允许空 Key
    val canDiscover = baseUrl.isNotBlank() &&
        (apiKey.isNotBlank() || def.id == CUSTOM_PROVIDER_ID)

    if (discovery is DiscoveryUi.Done) {
        PickContent(
            def = def,
            models = discovery.models,
            notes = discovery.notes,
            onBack = { vm.resetDiscovery() },
            onSave = { picked ->
                // 自定义厂商在目录里没有名字，用端点主机名当显示名 —— 否则建两个
                // 自定义厂商，模型列表里会出现两个一模一样的"自定义模型"
                val saved = if (def.id == CUSTOM_PROVIDER_ID) {
                    val host = runCatching { android.net.Uri.parse(baseUrl).host }
                        .getOrNull().orEmpty()
                    def.copy(
                        name = if (host.isNotBlank()) "自定义 · " + host else "自定义模型",
                        fallbackText = if (host.isNotBlank()) host.take(1).uppercase() else "自",
                    )
                } else def
                vm.saveProvider(saved, baseUrl, apiKey, picked, onSaved)
            },
        )
        return
    }

    Column(Modifier.fillMaxSize().background(pal().pageBg).navigationBarsPadding().imePadding()) {
        TopBar(
            title = def.name,
            subtitle = if (def.baseUrl.isBlank()) "OpenAI 兼容协议 · 先填端点和 Key"
            else "OpenAI 兼容协议 · 端点已预填",
            onBack = onBack,
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 4.dp)) {

            FieldLabel("API Key", "从控制台复制")
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, if (apiKey.isBlank()) pal().line else pal().accent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (apiKey.isEmpty()) {
                        Text("sk-...", style = ty().monoId, color = pal().faint)
                    }
                    BasicTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it.trim() },
                        textStyle = ty().monoCode,
                        singleLine = true,
                        cursorBrush = SolidColor(pal().accent),
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        // 用 Text 而不是 Password：Password 会让输入法进入安全输入模式，
                        // 多数中文输入法会因此禁掉剪贴板建议与粘贴
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
                            if (!pasted.isNullOrBlank()) apiKey = pasted.trim()
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(2.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { showKey = !showKey },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (showKey) "隐" else "显", fontSize = 11.sp, color = pal().muted)
                }
            }

            Spacer(Modifier.height(14.dp))

            FieldLabel(
                "Base URL",
                when {
                    def.baseUrl.isBlank() -> "必填 · 写到 /v1 这一级"
                    baseUrl == def.baseUrl -> "已预填"
                    else -> "已修改"
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, pal().line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    textStyle = ty().monoCode,
                    singleLine = true,
                    cursorBrush = SolidColor(pal().accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (def.altEndpoints.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val alt = def.altEndpoints.first()
                InfoBox(
                    "如果你用的是「${alt.label}」，端点要换成 ${alt.baseUrl}。" +
                        (if (alt.note.isNotBlank()) " ${alt.note}" else ""),
                    Tone.INFO,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "改用 ${alt.label} 端点",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal().accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { baseUrl = alt.baseUrl }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            when (discovery) {
                is DiscoveryUi.Loading -> {
                    Row(
                        Modifier.fillMaxWidth().height(46.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = pal().accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("正在拉取模型列表…", fontSize = 13.sp, color = pal().muted)
                    }
                }
                is DiscoveryUi.Failed -> {
                    InfoBox(
                        "${discovery.message}" +
                            (if (discovery.detail.isNotBlank()) "\n${discovery.detail}" else ""),
                        if (discovery.kind == FailureKind.AUTH || discovery.kind == FailureKind.BAD_REQUEST)
                            Tone.BAD else Tone.WARN,
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("重新识别", enabled = canDiscover) {
                        vm.startDiscovery(baseUrl, apiKey, def.modelsPath)
                    }
                }
                else -> {
                    PrimaryButton("识别模型", enabled = canDiscover) {
                        vm.startDiscovery(baseUrl, apiKey, def.modelsPath)
                    }
                }
            }

            Spacer(Modifier.height(9.dp))
            Text(
                "点按后请求 GET ${UrlNormalizer.modelsUrl(baseUrl, def.modelsPath)}，只读接口，不消耗 token。",
                style = ty().micro,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel("识别失败会告诉你是哪一类")
            OutlineCard(padding = 11.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ErrorLine("401", "Key 无效或已过期", pal().bad)
                    ErrorLine("400", "base URL 里重复了 /v1（拼成 /v1/v1/…）", pal().bad)
                    ErrorLine("404", "端点写错，或该厂商没有 /models", pal().bad)
                    ErrorLine("超时", "网络或代理问题；局域网地址需先授予「附近设备」权限", pal().warn)
                    ErrorLine("200 空", "这个端点没有 /models，换一个支持列模型的端点", pal().muted)
                }
            }
        }
    }
}

@Composable
private fun ErrorLine(code: String, text: String, color: Color) {
    Row {
        Text(code, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(52.dp))
        Text(text, fontSize = 11.5.sp, lineHeight = 17.sp, color = pal().muted)
    }
}

@Composable
internal fun FieldLabel(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ty().label)
        Spacer(Modifier.weight(1f))
        Text(trailing, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = pal().accent)
    }
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun PickContent(
    def: ProviderDef,
    models: List<DiscoveredModel>,
    notes: List<String>,
    onBack: () -> Unit,
    onSave: (List<DiscoveredModel>) -> Unit,
) {
    val chat = models.filter { it.isChat }
    val media = models.filterNot { it.isChat }

    val initiallyPicked = remember {
        chat.filter { it.note.isBlank() }.take(2).map { it.modelId }.toSet()
    }
    var picked by remember { mutableStateOf(initiallyPicked) }

    Column(Modifier.fillMaxSize().background(pal().pageBg).navigationBarsPadding()) {
        TopBar(
            title = "选择模型",
            subtitle = "${def.name} · 识别到 ${models.size} 个",
            onBack = onBack,
        )

        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            if (notes.isNotEmpty()) {
                item {
                    Box(Modifier.padding(bottom = 10.dp)) {
                        InfoBox("端点已自动规范化：${notes.joinToString("；")}", Tone.INFO)
                    }
                }
            }
            if (chat.isEmpty()) {
                item {
                    Box(Modifier.padding(bottom = 12.dp)) {
                        InfoBox(
                            "端点连通，但没有识别到对话模型。可以保留为媒体服务，仅用于生成图片/视频。",
                            Tone.WARN,
                        )
                    }
                }
            }

            item { SectionLabel("可用于对话", "${chat.size} 个") }
            items(chat) { m ->
                ModelRow(
                    m = m,
                    checked = picked.contains(m.modelId),
                    enabled = m.note.isBlank(),
                    onToggle = {
                        picked = if (picked.contains(m.modelId)) picked - m.modelId
                        else picked + m.modelId
                    },
                )
            }

            if (media.isNotEmpty()) {
                item { SectionLabel("其他模态", "${media.size} 个") }
                items(media) { m -> ModelRow(m = m, checked = false, enabled = false, onToggle = {}) }
            }

            item {
                Text(
                    "勾选的模型会加入模型列表，之后在输入区点模型 chip 随时切换。",
                    style = ty().micro,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (chat.isEmpty()) {
                PrimaryButton("没有可保存的对话模型", enabled = false) {}
                Spacer(Modifier.height(9.dp))
                GhostButton("仅保留为媒体服务") {
                    onSave(media)
                }
            } else {
                PrimaryButton("保存 ${picked.size} 个模型", enabled = picked.isNotEmpty()) {
                    onSave(models.filter { picked.contains(it.modelId) })
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    m: DiscoveredModel,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { BoxCheck(checked, enabled) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.modelId,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) pal().ink else pal().hint,
                    fontFamily = ty().mono,
                )
                if (m.recommended) {
                    Spacer(Modifier.width(7.dp))
                    Tag("推荐", pal().accentSoft, pal().modelChipInk)
                }
                if (m.note.isNotBlank()) {
                    Spacer(Modifier.width(7.dp))
                    Tag("已下线", pal().surfaceAlt, pal().hint)
                }
                if (!m.isChat) {
                    Spacer(Modifier.width(7.dp))
                    Tag(modalityLabel(m.modality), pal().mediaSoft, pal().media)
                }
            }
            val meta = when {
                m.note.isNotBlank() -> m.note
                !m.isChat -> modalityDesc(m.modality)
                else -> "点击勾选后可加入对话模型列表"
            }
            Text(meta, style = ty().micro, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

private fun modalityLabel(kind: String) = when (kind) {
    "image" -> "图像"
    "video" -> "视频"
    "audio" -> "音频"
    else -> "其他"
}

private fun modalityDesc(kind: String) = when (kind) {
    "image" -> "图像生成 / 编辑，不参与对话"
    "video" -> "视频生成，不参与对话"
    "audio" -> "语音转写，不参与对话"
    else -> "非对话模型"
}

fun parseHex(hex: String): Color = runCatching {
    val clean = hex.removePrefix("#")
    val v = clean.toLong(16)
    if (clean.length == 6) Color(0xFF000000L or v) else Color(v)
}.getOrDefault(Color(0xFF5F6368))
