package com.pocketagent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 轻量 Markdown 渲染。
 *
 * 刻意不引第三方库：本机拉 Maven 依赖经常超时，而聊天场景真正需要的只是
 * 「标题 / 段落 / 列表 / 引用 / 分割线 / 行内代码 / 代码块 / 粗斜体 / 链接」这几样。
 * 解析是纯函数，渲染才依赖 Compose，方便单独验证。
 */

// ───────────── 块级模型 ─────────────

private sealed interface Md {
    data class H(val level: Int, val text: String) : Md
    data class P(val text: String) : Md
    data class Code(val lang: String, val code: String) : Md
    data class Li(val ordered: Boolean, val num: Int, val text: String) : Md
    data class Quote(val text: String) : Md
    data object Rule : Md
}

private val RE_BULLET = Regex("""^\s{0,6}([-*+])\s+(.*)$""")
private val RE_ORDER = Regex("""^\s{0,6}(\d{1,3})[.)]\s+(.*)$""")
private val RE_RULE = Regex("""^\s{0,3}([-*_])\s*(\1\s*){2,}$""")

private fun bulletOf(line: String): Triple<Boolean, Int, String>? {
    RE_ORDER.find(line)?.let { m ->
        return Triple(true, m.groupValues[1].toIntOrNull() ?: 1, m.groupValues[2])
    }
    RE_BULLET.find(line)?.let { m ->
        return Triple(false, 0, m.groupValues[2])
    }
    return null
}

/**
 * 流式输出时尾部经常只有半截语法（比如刚打出一个 ``` 或 **），
 * 所有解析都必须容忍未闭合，最坏情况是当作普通文字显示，绝不能丢内容。
 */
private fun parseMarkdown(src: String): List<Md> {
    val out = ArrayList<Md>()
    val lines = src.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val para = StringBuilder()

    fun flush() {
        val t = para.toString().trimEnd()
        if (t.isNotEmpty()) out += Md.P(t)
        para.setLength(0)
    }

    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val trimmed = raw.trimEnd()

        // 代码块：未闭合时一路吃到结尾（流式过程中最常见）
        if (trimmed.trimStart().startsWith("```")) {
            flush()
            val lang = trimmed.trimStart().removePrefix("```").trim()
            val body = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                body.append(lines[i]).append('\n')
                i++
            }
            if (i < lines.size) i++ // 跳过收尾的 ```
            val codeText = body.toString().trimEnd('\n')
            // 空的 ``` 块（模型偶尔会输出来占位）不渲染，否则会留一个空卡片
            if (codeText.isNotBlank() || lang.isNotBlank()) out += Md.Code(lang, codeText)
            continue
        }

        if (trimmed.isBlank()) {
            flush()
            i++
            continue
        }

        val t = trimmed.trimStart()

        // 标题：必须有空格分隔，否则 #hashtag 会被误判
        if (t.startsWith("#")) {
            val level = t.takeWhile { it == '#' }.length
            if (level in 1..6 && (t.length == level || t[level] == ' ')) {
                flush()
                out += Md.H(level, t.substring(level).trim())
                i++
                continue
            }
        }

        if (RE_RULE.matches(trimmed)) {
            flush()
            out += Md.Rule
            i++
            continue
        }

        val li = bulletOf(trimmed)
        if (li != null) {
            flush()
            out += Md.Li(li.first, li.second, li.third.trim())
            i++
            continue
        }

        if (t.startsWith(">")) {
            flush()
            out += Md.Quote(t.removePrefix(">").trim())
            i++
            continue
        }

        para.append(trimmed).append('\n')
        i++
    }
    flush()
    return out
}

// ───────────── 行内样式 ─────────────

private fun inline(
    src: String,
    bold: SpanStyle,
    italic: SpanStyle,
    code: SpanStyle,
    link: SpanStyle,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            // 行内代码：优先级最高，内部不再解析其它语法
            c == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(code) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(c); i++
                }
            }

            src.startsWith("**", i) || src.startsWith("__", i) -> {
                val mark = src.substring(i, i + 2)
                val end = src.indexOf(mark, i + 2)
                if (end > i + 2) {
                    withStyle(bold) { append(src.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(c); i++
                }
            }

            c == '*' || c == '_' -> {
                val end = src.indexOf(c, i + 1)
                if (end > i + 1) {
                    withStyle(italic) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(c); i++
                }
            }

            c == '[' -> {
                val mid = src.indexOf("](", i)
                val end = if (mid > 0) src.indexOf(')', mid + 2) else -1
                if (mid > 0 && end > mid) {
                    val label = src.substring(i + 1, mid)
                    val url = src.substring(mid + 2, end)
                    // 只对 http(s) 做可点链接，其它协议（javascript: 等）一律当普通文字
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(style = link),
                            )
                        ) { append(label) }
                        i = end + 1
                    } else {
                        append(label)
                        i = end + 1
                    }
                } else {
                    append(c); i++
                }
            }

            else -> {
                append(c); i++
            }
        }
    }
}

// ───────────── 渲染 ─────────────

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ty().body,
) {
    val p = pal()
    // 每次文本变化（流式时每 60ms）才重新解析，滚动/重组不重复解析
    val blocks = remember(text) { parseMarkdown(text) }

    val bold = SpanStyle(fontWeight = FontWeight.SemiBold)
    val italic = SpanStyle(fontStyle = FontStyle.Italic)
    val codeSpan = SpanStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = style.fontSize * 0.92f,
        background = p.line,
        color = p.accentInk,
    )
    val linkSpan = SpanStyle(color = p.accent, textDecoration = TextDecoration.Underline)

    fun rich(s: String) = inline(s, bold, italic, codeSpan, linkSpan)

    Column(modifier) {
        blocks.forEachIndexed { idx, block ->
            val prev = blocks.getOrNull(idx - 1)
            val gap = when {
                idx == 0 -> 0.dp
                block is Md.Li && prev is Md.Li -> 5.dp
                block is Md.H -> 15.dp
                block is Md.Code -> 10.dp
                block is Md.Rule -> 14.dp
                prev is Md.H -> 6.dp
                prev is Md.Code -> 10.dp
                else -> 9.dp
            }
            if (gap > 0.dp) Spacer(Modifier.height(gap))

            when (block) {
                is Md.H -> Text(
                    rich(block.text),
                    fontSize = when (block.level) {
                        1 -> 19.sp
                        2 -> 17.sp
                        else -> 15.5.sp
                    },
                    lineHeight = when (block.level) {
                        1 -> 26.sp
                        2 -> 24.sp
                        else -> 22.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = p.ink,
                )

                is Md.P -> Text(rich(block.text), style = style)

                is Md.Li -> Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (block.ordered) "${block.num}." else "•",
                        style = style,
                        color = p.muted,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(rich(block.text), style = style, modifier = Modifier.weight(1f))
                }

                is Md.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(1.dp))
                            .background(p.line)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(rich(block.text), style = style, color = p.muted, modifier = Modifier.weight(1f))
                }

                is Md.Code -> CodeBlock(block.lang, block.code)

                Md.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(p.line)
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val p = pal()
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1400)
            copied = false
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(11.dp))
            .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                lang.ifBlank { "code" }.uppercase(),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = p.faint,
            )
            Text(
                if (copied) "已复制" else "复制",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (copied) p.ok else p.muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("code", code))
                        copied = true
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Text(
                code,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
                color = p.ink,
            )
        }
    }
}
