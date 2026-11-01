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
    /** 副标题颜色：会话页用它表达"需重新授权 / 未选工作区"这类要注意的状态 */
    subtitleColor: Color? = null,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    /** 副标题可点（会话页=换工作区），给了才加点击热区 */
    onSubtitleClick: (() -> Unit)? = null,
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
        // weight(1f) 独占剩余宽度：之前给的是 fill=false 再在后面跟一个 Spacer(weight)，
        // 两者按权重分同一块空间，长副标题会被提前截成"选择工作区 · 才能读写文…"
        Column(Modifier.padding(start = 4.dp).weight(1f)) {
            Text(
                title,
                style = ty().barTitle,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                val sub = @Composable {
                    Text(
                        subtitle,
                        style = ty().micro,
                        color = subtitleColor ?: ty().micro.color,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                if (onSubtitleClick == null) sub()
                else Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onSubtitleClick)
                        .padding(end = 6.dp, top = 1.dp, bottom = 1.dp),
                ) { sub() }
            }
        }
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
