package com.pocketagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 状态栏高度占位（视觉稿里的 44dp 头部） */
val StatusBarSpace = 44.dp

@Composable
fun TopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()          // 内容避让状态栏，背景由外层页面铺满
            .height(if (subtitle == null) 56.dp else 62.dp)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            onBack != null -> SquareIcon(G.BACK, onBack)
            onMenu != null -> SquareIcon(G.MENU, onMenu)
        }
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = ty().barTitle, maxLines = 1)
            if (subtitle != null) {
                Text(subtitle, style = ty().micro, maxLines = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SquareIcon(kind: G, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(kind, 20.dp, pal().ink, 1.9f)
    }
}

/**
 * 顶栏 / 输入区的小胶囊。
 *
 * 输入区那三颗是**三等分**排的（调用方传 weight(1f)），所以宽度天然一致、左缘永远对齐，
 * 文案长短不会再让它们一宽一窄地抖。
 *
 * 文字不做省略号：先扣掉固定件（左右内边距、前导点或图标、尾部箭头）得到文字预算，
 * 再按 11.5 → 11 → 10.5 → 10 → 9.5 逐档试字号，取第一个放得下的。
 * 极端长的模型名（> 20 字）才退到省略号 —— 那是兜底，不是常态。
 */
@Composable
fun PillChip(
    text: String,
    bg: Color,
    fg: Color,
    leadingGlyph: G? = null,
    leadingDot: Color? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier
            .height(29.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        // 固定件宽度：内边距 9+7、前导点 5+5 或图标 13+5、尾部箭头 11+1
        val fixed = 16.dp + (if (leadingDot != null) 10.dp else 0.dp) +
            (if (leadingGlyph != null) 18.dp else 0.dp) + 12.dp
        val budget = (maxWidth - fixed).coerceAtLeast(24.dp)

        Row(
            Modifier.fillMaxSize().padding(start = 9.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingDot != null) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(leadingDot))
                Spacer(Modifier.width(5.dp))
            }
            if (leadingGlyph != null) {
                Glyph(leadingGlyph, 13.dp, fg, 1.9f)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text,
                fontSize = fittedChipFont(text, budget),
                fontWeight = FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(1.dp))
            Glyph(G.CHEV_DOWN, 11.dp, fg.copy(alpha = if (enabled) 0.75f else 0.4f), 2.6f)
        }
    }
}

/** 给定宽度下能放下的最大字号；全放不下就用最小档，交给省略号兜底 */
@Composable
private fun fittedChipFont(text: String, budget: Dp): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, budget, density) {
        val w = with(density) { budget.toPx() }
        val ladder = listOf(11.5f, 11f, 10.5f, 10f, 9.5f)
        val hit = ladder.firstOrNull { size ->
            measurer.measure(
                text = AnnotatedString(text),
                style = TextStyle(fontSize = size.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                softWrap = false,
            ).size.width <= w
        }
        (hit ?: ladder.last()).sp
    }
}

@Composable
fun SectionLabel(text: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            color = pal().muted,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, fontSize = 10.5.sp, color = pal().faint)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(pal().lineSoft))
}

@Composable
fun OutlineCard(
    modifier: Modifier = Modifier,
    padding: Dp = 13.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(pal().card)
            .border(1.dp, pal().line, RoundedCornerShape(15.dp))
            .padding(padding),
    ) { content() }
}

@Composable
fun BoxCheck(checked: Boolean, enabled: Boolean = true) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        Modifier
            .size(18.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> pal().surface
                    checked -> pal().accent
                    else -> pal().card
                }
            )
            .border(
                1.8.dp,
                when {
                    !enabled -> pal().line
                    checked -> pal().accent
                    else -> pal().line
                },
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked && enabled) Glyph(G.CHECK, 11.dp, pal().onAccent, 3.2f)
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (enabled) pal().accent else pal().lineSoft)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) pal().onAccent else pal().faint,
        )
    }
}

@Composable
fun GhostButton(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(pal().card)
            .border(1.dp, if (danger) Color(0xFFEFD5D3) else pal().line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) pal().bad else pal().ink,
        )
    }
}

@Composable
fun InfoBox(text: String, tone: Tone = Tone.INFO) {
    val bg = when (tone) {
        Tone.INFO -> pal().infoBg
        Tone.WARN -> pal().warnSoft
        Tone.BAD -> pal().badSoft
    }
    val lineColor = when (tone) {
        Tone.INFO -> pal().infoLine
        Tone.WARN -> pal().warnLine
        Tone.BAD -> pal().bad.copy(alpha = 0.35f)
    }
    val ink = when (tone) {
        Tone.INFO -> pal().infoInk
        Tone.WARN -> pal().warn
        Tone.BAD -> pal().bad
    }
    val glyph = when (tone) {
        Tone.INFO -> G.INFO
        Tone.WARN -> G.WARN
        Tone.BAD -> G.WARN
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, lineColor, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Glyph(glyph, 13.dp, ink, 2f)
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 11.sp, lineHeight = 17.sp, color = ink)
    }
}

enum class Tone { INFO, WARN, BAD }
