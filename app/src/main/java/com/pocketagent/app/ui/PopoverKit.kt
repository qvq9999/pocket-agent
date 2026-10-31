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

@Composable
fun PopoverItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    desc: String? = null,
    trailing: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) pal().accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = if (desc == null) Alignment.CenterVertically else Alignment.Top,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.padding(top = if (desc == null) 0.dp else 1.dp)) {
            PopoverRadio(selected)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
            if (desc != null) {
                Text(desc, fontSize = 10.5.sp, lineHeight = 15.sp, color = pal().hint)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = ty().monoId)
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
