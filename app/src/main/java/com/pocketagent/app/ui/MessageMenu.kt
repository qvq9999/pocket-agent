package com.pocketagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** 一条消息菜单项 */
data class MsgAction(
    val label: String,
    /** 右侧补充说明（如"翻译中…"），为空则不显示 */
    val hint: String = "",
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * 消息长按 / 右键菜单。
 *
 * 锚点用「消息块的窗口矩形」而不是手指落点：长消息被按在中段时，
 * 按手指定位菜单只会在消息中段弹出、还可能顶出屏幕；按块定位则永远贴着这条消息。
 * 上方放不下会翻到下方，水平方向夹紧在屏幕内，靠边按也不会被裁掉。
 */
@Composable
fun MessageMenu(
    anchor: Rect,
    items: List<MsgAction>,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    Popup(
        popupPositionProvider = MessageMenuPosition(
            anchor = anchor,
            gap = with(density) { 8.dp.roundToPx() },
            margin = with(density) { 12.dp.roundToPx() },
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Column(
            Modifier
                .widthIn(min = 132.dp, max = 220.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(pal().card)
                .border(1.dp, pal().line, RoundedCornerShape(14.dp))
                .padding(5.dp),
        ) {
            items.forEach { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(enabled = item.enabled, onClick = item.onClick)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(
                        item.label,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            !item.enabled -> pal().faint
                            item.danger -> pal().bad
                            else -> pal().ink
                        },
                    )
                    if (item.hint.isNotBlank()) {
                        Spacer(Modifier.weight(1f))
                        Text(item.hint, fontSize = 11.sp, color = pal().faint)
                    }
                }
            }
        }
    }
}

private class MessageMenuPosition(
    private val anchor: Rect,
    private val gap: Int,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height

        // 水平：以消息块中心对齐，再夹进屏幕（左右各留 margin）
        val rawX = anchor.center.x.toInt() - w / 2
        val maxX = (windowSize.width - w - margin).coerceAtLeast(margin)
        val x = rawX.coerceIn(margin, maxX)

        // 垂直：优先贴消息块上方；上方不够就翻到下方；都不够就夹紧到下边界
        val above = anchor.top.toInt() - gap - h
        val below = anchor.bottom.toInt() + gap
        val maxY = (windowSize.height - h - margin).coerceAtLeast(margin)
        val y = when {
            above >= margin -> above
            below <= maxY -> below
            else -> maxY
        }
        return IntOffset(x, y)
    }
}
