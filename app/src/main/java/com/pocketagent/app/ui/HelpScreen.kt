package com.pocketagent.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 使用说明。从「设置 → 关于 → 使用说明」进来，按功能模块列出操作步骤。
 *
 * 为什么单独成页而不是塞进设置：
 *  设置项一行只放得下一句副标题，而"怎么用"本身就是成段的；挤在设置里要么被截断，
 *  要么把设置页拉成三屏。独立成页还能随时返回，设置页的位置一点不变。
 *
 * 内容口径与实现严格对齐（写之前逐条核过代码）：抽屉结构、三颗胶囊、+ 的三种来源、
 * 视觉三态、四档工具能力、产出文件走系统「打开方式」等，都按当前版本的实际行为写。
 */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(pal().pageBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "使用说明", onBack = onBack)

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

                // 一句话交代这份说明的用法，省得用户在十来个分组里找"我该先看哪"
                item {
                    Text(
                        "按功能模块排列。第一次用建议从「开始对话」「引用文件与图片」「工具与权限」看起，" +
                            "其余按需查阅。",
                        style = ty().micro,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                sections.forEach { sec ->
                    item(key = "h-${sec.title}") { SectionLabel(sec.title) }
                    item(key = "c-${sec.title}") {
                        OutlineCard(padding = 0.dp) {
                            Column {
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

                item { Spacer(Modifier.height(24.dp)) }
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

/** 分组末尾的一句提醒（可能引发误解/容易踩坑的点），语气比正文轻一档 */
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
 */
private val sections: List<HelpSection> = listOf(
    HelpSection(
        title = "开始对话",
        steps = listOf(
            "左上角 ☰ 拉出侧边栏 → 「新会话」。侧边栏只做会话切换，功能都从底部的「设置」进。",
            "第一次使用先在顶栏点工作区（或直接点副标题）授权一个文件夹 —— AI 只能在这个目录里读写。",
            "在输入框写需求直接发送。空输入时右侧按钮是灰描边、点不动；有文字才变蓝。",
            "一次回复整体是一个气泡：思考过程、正文、工具调用、token 用量都在里面，点卡片可展开看细节。",
            "生成过程中气泡右下角不显示「⋯」，整轮结束后才出现 —— 期间也没有可用的操作。",
        ),
        tip = "长按任意消息可「复制 / 引用 / 翻译成中文」；生成中按右下角方块可随时停止。",
    ),
    HelpSection(
        title = "顶栏与会话菜单",
        steps = listOf(
            "顶栏第一行是这段会话的标题，第二行是当前工作区 —— 点它就能换目录。",
            "工作区没选或授权失效时，副标题会变成琥珀色提示，点一下即可重新授权。",
            "右上角 ⋯ 是这段会话的动作：新对话、切换会话、解除工作区绑定、删除这段会话。",
            "「解除工作区绑定」只断开关联，不动任何文件；删除会话也只删聊天记录。",
        ),
    ),
    HelpSection(
        title = "输入框上的控制条",
        steps = listOf(
            "左（模型）：点开列出可用模型，每行都写着它支持的思考档位；切到不支持思考的模型，副标题会提示会关掉档位。",
            "中（思考）：点开选档位，每一档都标了快慢与费用取向。模型不支持思考时，点开也能看到原因。",
            "右（权限）：只读 / 需确认 / 允许。只有「允许」是琥珀色 —— 它意味着写文件不再询问你。",
            "输入框右下角那行小字是「工作区 · 读写状态」，点它同样能换工作区。",
        ),
        tip = "三颗胶囊宽度固定：档位名变长只会省略号，不会让整条控制条左右跳动。",
    ),
    HelpSection(
        title = "生成 Word 与 PPT",
        steps = listOf(
            "直接说「帮我做一份 Word / PPT」。生成是原生做的：不联网下载、不跑脚本、一次调用出成品。",
            "Word 支持标题层级、正文、列表、引用、代码块、表格与分页；PPT 支持封面、要点、双栏、纯标题和表格页（16:9）。",
            "产出的是真文档：手机上用 WPS 能打开，也能让 AI 接着改（换文字、填表格、加段落需要重建）。",
            "不支持图片、图表、母版和动画。要这些时模型会如实说明，不会假装能画。",
        ),
        tip = "不要让它用「写入文件」去改 .docx / .xlsx / .pptx —— 那会写出一个打不开的文件，工具层已经直接拦住了。",
    ),
    HelpSection(
        title = "引用文件与图片",
        steps = listOf(
            "点输入框左侧「+」，三种来源：相机（拍完直接引用）、相册（可多选，一次最多 4 张）、文件（文本 / 代码 / 文档）。",
            "文本文件会内联进这一条消息（上限 200KB）；二进制文件只带文件名，由 AI 用专门的工具去读。",
            "图片先按 EXIF 转正、长边压到 1024 再发出去 —— 这样模型看到的是图像本身，不是一串 base64。",
            "图片以缩略图挂在输入框里，右上角 × 单独删除；文件仍是名字胶囊。",
        ),
        tip = "当前模型被设为「视觉·关」时，发图会被拦下并提示先改设置，避免白花一次请求。",
    ),
    HelpSection(
        title = "模型与视觉",
        steps = listOf(
            "设置 → 模型 → 选厂商 → 「添加」，按引导填 Base URL、API Key、模型 id。",
            "每张厂商卡显示能力：文本 / 视觉 / 工具 N。点「工具 N」可展开它真正能调用的工具名。",
            "视觉是三态：自动（默认开）、开、关。某个模型发图报错时，点成「关」就能继续用它的文本能力。",
            "「重新获取」从厂商同步最新模型列表，本地已配置的项不受影响。",
        ),
    ),
    HelpSection(
        title = "工作区与产出文件",
        steps = listOf(
            "工作区就是 AI 的活动范围，通过系统目录授权选择，可以随时在顶栏更换。",
            "AI 写入、修改或生成的文件，会出现在这段对话末尾的「本对话产出」卡里。",
            "点文件名 → 交给系统的「打开方式」，选 WPS / Word / 阅读器即可查看。没有内置预览器，也不会把文件传到别处。",
            "文件被外部删掉后再点，会提示「文件已经不在了」，不会静默失败。",
        ),
        tip = "「本对话产出」只统计最后一条用户消息之后的文件，不是整段会话的历史累计。",
    ),
    HelpSection(
        title = "工具与权限",
        steps = listOf(
            "只读：只能读文件和搜索；写、删、执行命令类工具直接从工具表移除，模型看不到它们。",
            "需确认：每次写或删都弹一次确认并展示 diff；可以点「永久允许」把它加进白名单。",
            "全部允许：写删自动执行，但仍会先拍快照，随时能在工具卡里点「撤销」回到原状。",
            "设置 → 对话 →「永久允许的工具」可查看和撤销白名单，「全部清除」一次清空。",
        ),
        tip = "三种情况即使「全部允许」也会硬拦截：越界路径、备份失败、文件被外部改动过。删除不提供永久允许，只在本会话内放行。",
    ),
    HelpSection(
        title = "联网搜索",
        steps = listOf(
            "默认开启。模型判断需要最新信息时会自己搜，对话里出现一张「联网搜索」工具卡，展开是标题 + 链接。",
            "设置 → 对话 →「联网搜索」那张卡里一次看全：开关、你自己的 AnySearch Key、去哪儿申请。",
            "不填 key 也能用，走匿名额度（每天 300 次）；填了自己的 key 提到 1500 次/天，key 用系统密钥库加密，只存本机。",
        ),
        tip = "搜索结果可能过时。涉及日期、价格、「今天」这类说法，让模型先核对来源时间再下结论。",
    ),
    HelpSection(
        title = "扩展（Linux 环境）",
        steps = listOf(
            "设置 → 扩展 → 安装 Linux 环境（首约 30MB，解开约 100MB）。装好后模型可以执行 bash 命令。",
            "同一页右侧的开关是「允许执行命令」：关掉后这个工具不会下发给模型。",
            "旧版本为技能脚本装过 Python 的，升级后会连同整个 Linux 环境一起清掉 —— 需要时重新装一次即可。",
        ),
        tip = "Linux 环境有自己的文件系统，看不到你的工作区，不能用它替你改文件；要用它会重新下载一次基础系统。",
    ),
    HelpSection(
        title = "外观、用量与会话管理",
        steps = listOf(
            "设置 → 常用 → 主题：跟随系统 / 浅色 / 深色，行内直接点，不再弹窗；点一项立即生效。",
            "设置 → 常用 → 用量账单：今日、近 7 天、累计 token，按模型占比，以及按用途的明细。",
            "侧边栏顶部搜索框同时匹配标题与正文；长按某条会话可删除。",
            "设置页最上面那张卡是当前配置：模型、上下文窗口、权限、今日用量一眼看完。",
        ),
        tip = "「清空账单」只影响统计，不动对话记录；删会话只删聊天，不动工作区文件。",
    ),
)

