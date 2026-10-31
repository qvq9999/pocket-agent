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
            "左上角 ☰ 拉出侧边栏 → 点「新会话」。侧边栏只做会话切换，功能都在底部的「设置」里。",
            "第一次使用先在顶栏点「选工作区」，授权一个文件夹 —— AI 只能在这个目录里读写，不会碰别处。",
            "在输入框写需求直接发送。模型会自己判断要不要调用工具；没选工作区时，文件类工具不会出现。",
            "一次回复整体是一个气泡：思考过程、正文、工具调用、token 用量都在里面。点工具卡可展开看具体改了什么。",
        ),
        tip = "长按任意消息可「复制 / 引用 / 翻译成中文」；「重新生成」只出现在最后一条回复上。",
    ),
    HelpSection(
        title = "输入框上的三颗胶囊",
        steps = listOf(
            "左（模型）：列出已启用的模型，点一下切换。同一厂商同时只启用一个模型。",
            "中（思考）：显示当前模型支持的思考档位。模型不支持时它是灰的，点不动。",
            "右（权限）：只读 / 需确认 / 允许，点开可临时切换。它和设置里的「默认访问权限」是同一个值，改一处两边同步。",
            "右侧圆形按钮发送；发送后按钮变为停止，可中途打断。",
        ),
    ),
    HelpSection(
        title = "引用文件与图片",
        steps = listOf(
            "点输入框左侧「+」，三种来源：相机（拍完直接引用）、相册（可多选，一次最多 4 张）、文件（文本/代码/文档）。",
            "文本文件会内联进这一条消息（上限 200KB）；二进制文件只带文件名。",
            "图片会先按 EXIF 转正、长边压到 1024，再作为视觉输入发出去 —— 这样模型才真的看得见图，而不是收到一段 base64。",
            "图片以缩略图挂在输入框里，右上角 × 单独删除；文件仍是名字胶囊。",
        ),
        tip = "发图前若当前模型被设为「视觉·关」，会被拦下并提示先改设置，避免白花一次请求。",
    ),
    HelpSection(
        title = "模型与视觉",
        steps = listOf(
            "设置 → 模型 → 选厂商 → 「添加」，按引导填 Base URL、API Key、模型 id。",
            "每张厂商卡显示当前模型的能力：文本 / 视觉 / 工具 N。点「工具 N」可展开它真正能调用的工具名。",
            "视觉是三态：自动（默认开）、开、关。某个模型发图报错时，点成「关」就能继续用它的文本能力。",
            "「重新获取」从厂商同步最新模型列表，本地已配置的项不受影响。",
        ),
    ),
    HelpSection(
        title = "工作区与产出文件",
        steps = listOf(
            "工作区就是 AI 的活动范围，通过系统目录授权选择，可以随时在顶栏更换。",
            "AI 写入或修改过的文件，会出现在这段对话末尾的「本对话产出」卡里 —— 单文件是整行，多文件是列表。",
            "点文件名 → 交给系统的「打开方式」，选 WPS / Word / 阅读器即可查看。我们没有内置预览器，也不会把文件传到别处。",
            "文件被外部删掉后再点，会提示「文件已经不在了」，不会静默失败。",
        ),
        tip = "产出列表是整段对话的累计（按路径去重），不是只有最后一轮。",
    ),
    HelpSection(
        title = "工具与权限",
        steps = listOf(
            "只读：只能读文件和搜索，写、删、执行命令类工具直接从工具表移除，模型看不到它们。",
            "需确认：每次写或删都弹一次确认并展示 diff；可以点「永久允许」把它加进白名单。",
            "全部允许：写删自动执行，但仍会先拍快照，随时能在工具卡里点「撤销」回到原状。",
            "设置 → 对话 →「永久允许的工具」可查看和撤销白名单，「全部清除」一次清空。",
        ),
        tip = "三种情况即使「全部允许」也会硬拦截：越界路径、备份失败、文件被外部改动过。删除不提供永久允许，只在本会话内放行。",
    ),
    HelpSection(
        title = "记忆",
        steps = listOf(
            "让 AI「记住」的信息会写进本机记忆，跨对话保留，下次对话它会自动检索。",
            "设置 → 记忆 可查看全部笔记、逐条删除。",
        ),
        tip = "记忆只存在 app 私有目录，不进你的工作区，也不会被文件类工具读到。",
    ),
    HelpSection(
        title = "联网搜索",
        steps = listOf(
            "默认开启。模型判断需要最新信息时会自己搜，对话里出现一张「联网搜索」工具卡，展开是标题 + 链接。",
            "设置 → 联网搜索 可以整体关掉；关掉后连工具都不会下发给模型。",
            "填 AnySearch 的 key 可把每日额度从本地匿名档提到更高，key 用系统密钥库加密后只存本机。",
        ),
        tip = "搜索结果可能过时。涉及日期、价格、「今天」这类说法，让模型先核对来源时间再下结论。",
    ),
    HelpSection(
        title = "扩展（Linux 环境）",
        steps = listOf(
            "设置 → 扩展 → 安装 Linux 环境（首次约 30MB，解开约 100MB）。装好后模型可以执行 bash 命令。",
            "同一页右侧的开关是「允许执行命令」：关掉后这个工具不会下发给模型；安装过程中关掉等于取消下载，已下的部分会保留。",
        ),
        tip = "Linux 环境有自己的文件系统，看不到你的工作区，也不能用它替你改文件。命令开关是全局的，对所有对话生效。",
    ),
    HelpSection(
        title = "外观与用量",
        steps = listOf(
            "设置 → 常用 → 主题：跟随系统 / 浅色 / 深色，点一项立即生效。",
            "设置 → 常用 → 用量账单：今日、近 7 天、累计 token，按模型占比，以及按用途的明细。",
            "账单页最底部的「危险区」可以清空账单，清空只影响统计，不动对话记录。",
        ),
    ),
    HelpSection(
        title = "会话管理",
        steps = listOf(
            "侧边栏顶部搜索框同时匹配标题与正文，记得聊过但忘了标题也搜得到。",
            "搜索框右端的「⋯」是当前这段会话的操作：导出会话（Markdown）、清空当前会话。",
            "会话按「普通会话 / 工作区」分组，组内按最近收发排序；长按某条会话可删除。",
        ),
        tip = "「清空当前会话」会删掉全部消息并把这段会话从历史中移除，和长按删除是同一个动作，执行前有二次确认。",
    ),
)
