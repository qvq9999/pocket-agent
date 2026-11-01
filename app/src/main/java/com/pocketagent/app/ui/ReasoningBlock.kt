package com.pocketagent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 三个点的呼吸动画。
 *
 * 用它替代原来的 `"…"` 文本：一个静态省略号分不清"在加载"和"模型只回了一个点"，
 * 而且它在气泡里占位忽大忽小，流式开始的那一帧会跳一下。
 *
 * 三个点各自延迟 160ms 起跳、`Reverse` 往返，形成波浪而不是同时明灭 ——
 * 同时明灭看起来像在闪屏报错。
 */
@Composable
fun LoadingDots(
    color: Color,
    dotSize: Dp = 5.dp,
    gap: Dp = 4.dp,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.22f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = i * 160,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(dotSize)
                    .alpha(alpha)
                    .background(color, CircleShape),
            )
        }
    }
}

/**
 * 思考过程卡片（对齐 RikkaHub 的 reasoning 卡）。
 *
 * 三条行为：
 * 1. **默认折叠**成一行 —— 思考内容往往几百字，全展开会把正文顶到屏幕外；
 * 2. **流式中自动展开**，并且跟着内容滚到底 —— 用户这时候最想看的就是"它在想什么"；
 * 3. 一轮结束后**自动收起**，但保留"思考过程 · N 字"这一行，随时可以点开回看。
 *
 * 内容用等宽感的小字号 + 低对比色：它是过程，不是结论，不应该比正文更抢眼。
 */
@Composable
fun ReasoningBlock(
    text: String,
    streaming: Boolean,
    /** 展开态由调用方托管：放在卡片内部会随列表项回收而丢失，看起来像"点不开" */
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return

    // 流式结束自动收起由调用方处理（它知道"用户是否手动点过"）；这里只管渲染

    val scroll = rememberScrollState()
    // 流式时让内容始终停在最新一行，相当于"实时日志"
    LaunchedEffect(text) {
        if (streaming && expanded) scroll.scrollTo(scroll.maxValue)
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(G.SPARKLE, 12.dp, pal().muted, 1.9f)
            Spacer(Modifier.width(5.dp))
            if (streaming) {
                Text("正在思考", fontSize = 11.5.sp, color = pal().muted)
                Spacer(Modifier.width(6.dp))
                LoadingDots(pal().muted, dotSize = 3.dp, gap = 3.dp)
            } else {
                Text(
                    "思考过程 · ${text.length} 字",
                    fontSize = 11.5.sp,
                    color = pal().muted,
                )
            }
            Spacer(Modifier.width(4.dp))
            Glyph(
                if (expanded) G.CHEV_DOWN else G.CHEV_RIGHT,
                11.dp,
                pal().faint,
                2f,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            // expandFrom 必须显式给 Top：默认是 Alignment.Bottom，内容从底边往上顶，
            // 点开时看着像"往上展开"、标题还往上跳。Top = 标题不动、正文往下展开。
            enter = fadeIn(tween(160)) + expandVertically(tween(180), expandFrom = Alignment.Top),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Top),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(pal().surfaceAlt)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text.trim(),
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = pal().muted,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(scroll),
                )
            }
        }
    }
}
