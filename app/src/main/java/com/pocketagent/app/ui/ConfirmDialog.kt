package com.pocketagent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * 删除类操作的二次确认。
 *
 * 用 Popup 而不是往调用方布局里塞一层 Box：
 * 抽屉面板只有 302dp 宽，设置页/模型页又各有自己的滚动容器，
 * 只有独立 window 才能保证"无论在哪个页面、哪一层弹出来都盖满全屏"。
 *
 * 视觉沿用全局弹窗体：卡片 + 1dp 描边 + 大圆角，与选择类弹窗是同一套语言。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    cancelText: String = "取消",
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onCancel,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            // 遮罩自己处理点击，不用 popup 的"点外面关闭"（它会和卡片内的按钮抢事件）
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pal().scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(130)) + scaleIn(tween(170), initialScale = 0.94f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                // 层次只靠 1dp 灰描边：任何画到节点边界外的阴影都会在淡入淡出时被裁成黑硬边
                Column(
                    Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(pal().card)
                        .border(1.dp, pal().line, RoundedCornerShape(18.dp))
                        // 吃掉卡片区域的点击，避免穿透到遮罩把弹窗关掉
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 14.dp),
                ) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pal().ink,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        message,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = pal().hint,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DialogButton(cancelText, primary = false, onClick = onCancel)
                        Spacer(Modifier.width(8.dp))
                        DialogButton(confirmText, primary = true, onClick = onConfirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (primary) pal().bad else pal().surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            // 主按钮文字用 pageBg：亮色下是白字压深红，暗色下是黑字压浅红，两边都够对比
            color = if (primary) pal().pageBg else pal().ink,
        )
    }
}
