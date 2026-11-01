package com.pocketagent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 使用说明。
 *
 * 原来是 13 个分组、60 多条步骤全部平铺，要滚十几屏才看得完 ——
 * 想找"怎么生成 PPT"得先滑过 8 个不相关的分组，找的成本远大于读的成本。
 *
 * 现在改成**折叠目录**：默认只展开第一节，其余收起只留标题与步数，
 * 13 个主题一屏半内全部可见，点哪节开哪节。
 * 内容同时从 60 多条压到 43 条：砍掉跨节重复的叙述（技能在"生成 Word"里又讲一遍、
 * 工作区在三个地方各说一次），但**每条提示都保留** —— 那些都是容易踩坑的地方。
 *
 * 内容口径与实现严格对齐（写之前逐条核过代码）：抽屉只做会话切换、
 * 设置页的四个分组（常用 / 对话 / 模型与能力 / 关于）、技能入口实际叫
 * 「Skill 管理」、「永久允许的工具」在「对话 → 默认访问权限」那张卡里、
 * 检查更新的节流规则等，都按当前版本的实际行为写。**改动功能时顺手改这里**，
 * 否则它比没有更糟。
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    // 展开的是"哪几节的标题"。默认只开第一节：新人第一眼有内容，
    // 老手一眼看到目录 —— 两种读者都不用先滚一遍。
    var expanded by remember { mutableStateOf(setOf(sections.first().title)) }

    Box(Modifier.fillMaxSize().background(pal().pageBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "使用说明", onBack = onBack)

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Text(
                        "点开要看的那一节。第一次用建议先看「开始对话」。",
                        style = ty().micro,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }

                sections.forEachIndexed { i, sec ->
                    item(key = sec.title) {
                        HelpSectionCard(
                            sec = sec,
                            // 第一节标"首启必看"：13 个标题并排时，得有一个"从这开始"的落点
                            first = i == 0,
                            open = sec.title in expanded,
                            onToggle = {
                                expanded = if (sec.title in expanded) {
                                    expanded - sec.title
                                } else {
                                    expanded + sec.title
                                }
                            },
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** 一节：标题行永远可见，点开才铺开步骤 */
@Composable
private fun HelpSectionCard(
    sec: HelpSection,
    first: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
) {
    // 与会话列表的分组箭头同一套：旋转而不是换图标，回退时才有"倒回去"的因果感
    val angle by animateFloatAsState(
        targetValue = if (open) 0f else -90f,
        animationSpec = tween(160),
        label = "helpChevron",
    )

    Column(Modifier.padding(top = 8.dp)) {
        OutlineCard(padding = 0.dp) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onToggle)
                        .padding(start = 14.dp, end = 12.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        sec.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = pal().ink,
                    )
                    if (first) {
                        Spacer(Modifier.width(7.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pal().accentSoft)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text("首启必看", fontSize = 9.5.sp, color = pal().accentInk)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${sec.steps.size} 步", style = ty().micro)
                    Spacer(Modifier.width(6.dp))
                    Glyph(
                        G.CHEV_DOWN, 13.dp, pal().faint, 2.2f,
                        modifier = Modifier.rotate(angle),
                    )
                }

                AnimatedVisibility(
                    visible = open,
                    enter = fadeIn(tween(120)) + expandVertically(tween(180), expandFrom = Alignment.Top),
                    exit = fadeOut(tween(90)) + shrinkVertically(tween(150), shrinkTowards = Alignment.Top),
                ) {
                    Column {
                        Hairline()
                        sec.steps.forEachIndexed { i, step ->
                            if (i > 0) Hairline()
                            HelpStepRow(index = i + 1, text = step)
                        }
                        if (sec.tip != null) {
                            Hairline()
                            HelpTipRow(sec.tip)
                        }
                    }
                }
            }
        }
    }
}

/** 一条操作步骤：序号 + 一句话。序号用固定 18dp 圆，缩进才不会随位数变化 */
@Composable
private fun HelpStepRow(index: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(pal().accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                index.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = pal().accentInk,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = pal().ink,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 分组末尾的一句提醒（容易踩坑的点），语气比正文轻一档 */
@Composable
private fun HelpTipRow(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Glyph(G.INFO, 13.dp, pal().warn, 2f)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = pal().muted,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class HelpSection(val title: String, val steps: List<String>, val tip: String? = null)

/**
 * 全部说明内容。
 *
 * 每条都写成"在哪儿 → 做什么 → 会发生什么"，不写"支持 XX 功能"这种没有操作的话 ——
 * 用户来看说明是要解决问题，不是看功能清单。
 *
 * 顺序 = 用户真实的使用顺序：先会聊天，再会调，然后才是各种能力与设置。
 */
private val sections: List<HelpSection> = listOf(
    HelpSection(
        title = "开始对话",
        steps = listOf(
            "左上角 ☰ 拉出侧边栏 → 「新会话」。侧边栏只做会话切换，所有功能入口都在「设置」里。",
            "顶栏第二行是当前工作区，点它就能换目录；没选或授权失效时会变成琥珀色提示。",
            "输入框写需求直接发送。空输入时右侧按钮是灰描边、点不动；有文字才变蓝。",
            "长按任意消息可「复制 / 引用 / 翻译成中文」；生成中按右下角方块可随时停止。",
        ),
        tip = "一次回复整体是一个气泡：思考过程、正文、工具调用、token 用量都在里面，点卡片展开看细节。",
    ),
    HelpSection(
        title = "顶栏与会话菜单",
        steps = listOf(
            "右上角 ⋯ 是这段会话的动作：新对话、切换会话、解除工作区绑定、删除这段会话。",
            "「解除绑定」只断开关联、「删除会话」只删聊天记录，两个都不会动工作区里的文件。",
        ),
        tip = "删除会话不可撤销，重要的对话先用 ⋯ 里的导出存一份 Markdown。",
    ),
    HelpSection(
        title = "输入框上的控制条",
        steps = listOf(
            "三颗胶囊从左到右：模型、思考档位、访问权限。模型不支持思考时，点开也会告诉你原因。",
            "权限三档：只读 / 需确认 / 允许。只有「允许」是琥珀色 —— 它意味着写文件不再询问你。",
            "输入框右下角那行小字是「工作区 · 读写状态」，点它同样能换工作区。",
        ),
        tip = "三颗胶囊宽度固定：档位名变长只会省略号，不会让整条控制条左右跳动。",
    ),
    HelpSection(
        title = "技能（Skill）",
        steps = listOf(
            "入口在 设置 → 模型与能力 → 「Skill 管理」。内置三个：主题工厂、品牌规范、文档协作。",
            "要用哪个就在输入框左侧「+」里勾选 —— 只有勾上的才生效，新开一段会话会清空。",
            "模型先拿到名字和用途，真正动手前会自己读完整手册，再按步骤走。",
            "也能装自己的：从 GitHub 仓库地址装，或选本地的 .zip 包。装进来的是纯文本手册。",
        ),
        tip = "手册里凡是要求跑 python 脚本、起本地服务的步骤会被跳过，改用内置工具做同类的事，模型会告诉你哪一步是降级完成的。",
    ),
    HelpSection(
        title = "生成 Word 与 PPT",
        steps = listOf(
            "直接说「帮我做一份 Word / PPT」：不联网下载、不跑脚本、一次调用出成品。",
            "Word 支持标题层级、正文、列表、引用、代码块、表格与分页；PPT 支持封面、要点、双栏、纯标题和表格页（16:9）。",
            "产出的是真文档：手机上用 WPS 或 Office 能打开，也能让 AI 接着改文字和表格。",
        ),
        tip = "不支持图片、图表、母版和动画。也不要让它用「写入文件」去改 .docx / .xlsx / .pptx —— 那会写出打不开的文件，工具层已经直接拦住了。",
    ),
    HelpSection(
        title = "引用文件与图片",
        steps = listOf(
            "点输入框左侧「+」，三种来源：相机（拍完直接引用）、相册（可多选，一次最多 4 张）、文件。",
            "文本文件会内联进这一条消息（上限 200KB）；图片会先按 EXIF 转正、长边压到 1024 再发出去。",
            "图片以缩略图挂在输入框里，右上角 × 单独删除；文件仍是名字胶囊。",
        ),
        tip = "当前模型被设为「视觉·关」时，发图会被拦下并提示先改设置，避免白花一次请求。",
    ),
    HelpSection(
        title = "模型与视觉",
        steps = listOf(
            "设置 → 模型与能力 → 模型 → 添加：先选厂商（端点已按官方文档预填），再填 API Key。",
            "点「识别模型」会自动拉取该账号可用的模型列表，不用手抄模型 id。",
            "视觉是三态：自动（默认开）、开、关。某个模型发图报错时，点成「关」就能继续用它的文本能力。",
            "上下文窗口留空就是「自动」：按模型名识别，识别不出来按 200k 算。",
        ),
        tip = "窗口报得比模型真实上限大时网关会拒绝，这时会自动缩短历史重试，不用手动改。本地模型（Ollama、LM Studio）在「添加」里单独分组。",
    ),
    HelpSection(
        title = "工作区与产出文件",
        steps = listOf(
            "工作区就是 AI 的活动范围，通过系统目录授权选择，随时可以在顶栏更换。",
            "AI 写入、修改或生成的文件，会出现在这段对话末尾的「本对话产出」卡里。",
            "点文件名 → 交给系统「打开方式」。文件被外部删掉后再点，会提示「文件已经不在了」。",
        ),
        tip = "「本对话产出」只统计最后一条用户消息之后的文件，不是整段会话的历史累计。",
    ),
    HelpSection(
        title = "工具与权限",
        steps = listOf(
            "只读：写、删、执行命令类工具直接从工具表移除，模型看不到它们。",
            "需确认：每次写或删都弹一次确认并展示 diff；可以点「永久允许」把它加进白名单。",
            "允许：写删自动执行，但仍会先拍快照，随时能在工具卡里点「撤销」回到原状。",
            "设置 → 对话 →「永久允许的工具」可查看和撤销白名单，也能一次全部清除。",
        ),
        tip = "三种情况即使「允许」也会硬拦截：越界路径、备份失败、文件被外部改动过。删除不提供永久允许，只在本会话内放行。",
    ),
    HelpSection(
        title = "联网搜索",
        steps = listOf(
            "默认开启。模型判断需要最新信息时会自己搜，对话里出现一张「联网搜索」工具卡。",
            "设置 → 对话 →「联网搜索」：开关、你自己的 AnySearch Key、去哪儿申请都在这一组里。",
            "不填 key 也能用，走匿名额度（每天 300 次）；填了自己的 key 提到 1500 次/天，key 用系统密钥库加密，只存本机。",
        ),
        tip = "搜索结果可能过时。涉及日期、价格、「今天」这类说法，让模型先核对来源时间再下结论。",
    ),
    HelpSection(
        title = "扩展（Linux 环境）",
        steps = listOf(
            "设置 → 模型与能力 →「扩展」→ 安装 Linux 环境（下载约 30MB，解开约 100MB）。装好后模型可以执行 bash 命令。",
            "同一页的开关是「允许执行命令」：关掉后这个工具不会下发给模型，已装环境仍然保留。",
            "这一页还能看占用、清缓存、彻底删除 —— 三件事是分开的，删环境不会顺手把你的对话弄没。",
        ),
        tip = "Linux 环境有自己的文件系统，看不到你的工作区，不能用它替你改文件；它也不能装 LibreOffice 转文档，那部分已经从软件里移除了。",
    ),
    HelpSection(
        title = "外观、用量与会话管理",
        steps = listOf(
            "设置 → 常用 → 主题：跟随系统 / 浅色 / 深色，行内直接点，点一项立即生效。",
            "设置 → 常用 → 用量账单：今日、近 7 天、累计 token，按模型占比，以及按用途的明细。",
            "设置 → 关于 →「使用引导」可以随时重看三步引导。",
        ),
        tip = "「清空账单」只影响统计，不动对话记录；删会话只删聊天，不动工作区文件。侧边栏按今天/昨天/更早分组，搜索框同时匹配标题与正文。",
    ),
    HelpSection(
        title = "检查更新",
        steps = listOf(
            "设置 → 关于 → 版本，点这一行就会去查。有新版本会弹窗，写明新版本号与本次更新日志。",
            "冷启动与回到前台时也会自动查一次，24 小时内最多一次；正在跑任务时不弹，等这一轮结束再说。",
            "点「立即更新」后自动走国内镜像下载（安装包约 2MB），校验通过就交给系统安装器。",
            "同一个版本点过「取消」之后就不再自动弹，只在设置页那行显示「发现新版本」。",
        ),
        tip = "系统可能拦一下，要求先允许「安装未知应用」—— 去设置里打开后返回来会自动接着装，不用重新点。装好之后旧的安装包会自动清掉，不占空间。",
    ),
)
