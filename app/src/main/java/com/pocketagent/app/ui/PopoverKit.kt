package com.pocketagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局统一的弹出窗样式。
 *
 * 基准是对话页的「选择模型 / 思考程度 / 访问权限」三个弹出层：
 * 322dp 宽、16dp 圆角、1dp 灰色描边、6dp 内边距，结构固定为
 * （刻意不用阴影：elevation 阴影在淡入淡出动画里会被裁成硬边黑框，见下）
 * 「小标题 → 单选行 → 底部说明」。所有选择类弹窗都必须走这里，避免各页面各写一套。
 */
@Composable
fun PopoverSurface(
    modifier: Modifier = Modifier,
    /** 宽度可以收窄：抽屉只有 302dp，322dp 的弹层会顶出屏幕 */
    width: Dp = 322.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 卡片层次一律靠 1dp 灰描边 + 底色差，不用阴影：
    // 平台 elevation（以及任何画到节点边界外的投影）在父层做透明度动画时会被裁成硬边黑框，
    // 用户看到的是"弹窗外面闪一圈黑描边"。
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .background(pal().card)
            .border(1.dp, pal().line, RoundedCornerShape(16.dp))
            .padding(5.dp),
        content = content,
    )
}

@Composable
fun PopoverHead(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = pal().faint,
        modifier = Modifier.padding(start = 9.dp, top = 8.dp, bottom = 6.dp, end = 9.dp),
    )
}

@Composable
fun PopoverRadio(on: Boolean) {
    Box16(on)
}

@Composable
private fun Box16(on: Boolean) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (on) pal().accent else Color.Transparent)
            .border(2.dp, if (on) pal().accent else pal().line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(5.dp).clip(CircleShape).background(pal().onAccent)
            )
        }
    }
}

/**
 * 选择类弹层的**唯一**行样式。
 *
 * 三处（模型 / 思考 / 权限）以前各写一套：模型是"单选圈 + 图标 + 两行 + 徽标"、
 * 思考是"单选圈 + 彩点 + 单行 + 等宽参数"、权限是"单选圈 + 两行说明"，
 * 行高 40/34/44dp 各不相同 —— 同一个交互三种长相，是弹层看着割裂的主因。
 *
 * 现在统一成 44dp：`图标 18 · 标题 13 · 副标题 10.5 · 右侧 ✓`。
 * 选中态用**左侧强调竖条 + 浅底**而不是单选圈：这里表达的是"当前值"，
 * 不是一张待提交的表单，圆圈会暗示"还要再点一次确认"。
 */
@Composable
fun PopoverItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    /** 副标题要不要用警示色（"全部允许"这类后果） */
    warnSubtitle: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) pal().accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(pal().accent)
            )
            Spacer(Modifier.width(8.dp))
        }
        if (icon != null) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = pal().ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = if (warnSubtitle) pal().warn else pal().hint,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Glyph(G.CHECK, 14.dp, pal().accent, 2.4f)
        }
    }
}

@Composable
fun PopoverFoot(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        color = pal().faint,
        modifier = Modifier.padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 3.dp),
    )
}
