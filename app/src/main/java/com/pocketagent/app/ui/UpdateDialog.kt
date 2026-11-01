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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.pocketagent.app.BuildConfig
import com.pocketagent.app.data.UpdateManifest
import com.pocketagent.app.net.ApkDownloader

/**
 * 版本更新弹窗。
 *
 * 视觉沿用 [ConfirmDialog] 那套（300dp 卡片 / 18dp 圆角 / 1dp 描边 / 右对齐按钮），
 * 只有主按钮换成 accent —— 更新不是破坏性操作，不该用删除红。
 *
 * 用独立的 `Popup` 而不是往页面里塞一层：它可能从会话页、设置页、任何一级页面弹出来，
 * 只有独立 window 能保证"盖满全屏、不被抽屉或滚动容器裁掉"。
 */
@Composable
fun UpdateDialog(
    ui: UpdateUi,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onGoSettings: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    // 下载中与强制更新不许点外部/返回键关掉：前者会让进度凭空消失，后者等于放行
    val dismissible = ui !is UpdateUi.Downloading &&
        !(ui is UpdateUi.Offer && ui.forced)

    fun close() { if (dismissible) onLater() }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = { close() },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = dismissible,
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
                    onClick = { close() },
                ),
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(130)) + scaleIn(tween(170), initialScale = 0.94f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Column(
                    Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(pal().card)
                        .border(1.dp, pal().line, RoundedCornerShape(18.dp))
                        // 吃掉卡片内的点击，避免穿透到遮罩
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 14.dp),
                ) {
                    when (ui) {
                        is UpdateUi.Offer -> OfferBody(ui, onUpdate, onLater)
                        is UpdateUi.Downloading -> DownloadingBody(ui, onLater)
                        is UpdateUi.Failed -> FailedBody(ui, onUpdate, onLater)
                        is UpdateUi.NeedPermission -> PermissionBody(ui, onGoSettings, onLater)
                        UpdateUi.UpToDate -> UpToDateBody(onLater)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
}

/** 「当前 0.8.1 → 新版本 0.8.2」。旧版本压灰、新版本用 accent，视线自然落在后半段 */
@Composable
private fun VersionLine(newName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("当前 ${BuildConfig.VERSION_NAME}", fontSize = 12.5.sp, color = pal().hint)
        Text("　→　", fontSize = 12.5.sp, color = pal().faint)
        Text(
            "新版本 $newName",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = pal().accent,
        )
    }
}

/**
 * 更新日志区。
 *
 * 高度封顶 + 内部滚动：备注由远端控制，条目多少、某条多长都不由我们决定，
 * 一旦让它自然伸展，弹窗会被撑到超出屏幕，按钮就点不到了。
 */
@Composable
private fun NotesBlock(manifest: UpdateManifest) {
    val lines = manifest.notes.ifEmpty {
        listOf("本次更新包含若干修复与优化。")
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 132.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(pal().surface)
            .border(1.dp, pal().lineSoft, RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text("更新日志", fontSize = 11.sp, color = pal().hint)
        Spacer(Modifier.height(6.dp))
        lines.forEach { line ->
            Row(Modifier.padding(bottom = 6.dp)) {
                // 圆点从行首往下挪 6dp：正好压在第一行文字的视觉中线上
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(pal().faint),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    line,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = pal().muted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ButtonRow(
    cancelText: String? = null,
    onCancel: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cancelText != null) {
            DialogButton(cancelText, primary = false, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
        }
        DialogButton(
            confirmText,
            primary = true,
            onClick = onConfirm,
            primaryColor = pal().accent,
            enabled = confirmEnabled,
        )
    }
}

@Composable
private fun OfferBody(ui: UpdateUi.Offer, onUpdate: () -> Unit, onLater: () -> Unit) {
    DialogTitle(if (ui.forced) "需要更新才能继续" else "发现新版本")
    Spacer(Modifier.height(7.dp))
    VersionLine(ui.manifest.versionName)
    Spacer(Modifier.height(12.dp))
    NotesBlock(ui.manifest)
    if (ui.forced) {
        Spacer(Modifier.height(10.dp))
        InfoBox("当前版本已不再支持，更新后才能正常使用。", Tone.WARN)
    }
    Spacer(Modifier.height(16.dp))
    ButtonRow(
        cancelText = if (ui.forced) null else "取消",
        onCancel = onLater,
        confirmText = if (ui.forced) "去更新" else "立即更新",
        onConfirm = onUpdate,
    )
}

@Composable
private fun DownloadingBody(ui: UpdateUi.Downloading, onLater: () -> Unit) {
    val p = ui.progress
    DialogTitle("正在下载 ${ui.manifest.versionName}")
    Spacer(Modifier.height(10.dp))
    // 进度条：底色用 line，进度用 accent。不画圆角端帽，2MB 的包走过去很快，端帽反而闪
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(pal().line),
    ) {
        val frac = if (p.total > 0) (p.bytes.toFloat() / p.total).coerceIn(0f, 1f) else 0f
        Box(
            Modifier
                .fillMaxWidth(frac)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(pal().accent),
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        buildString {
            append(mb(p.bytes)).append(" / ").append(if (p.total > 0) mb(p.total) else "—")
            if (p.bytesPerSec > 0) append(" · ").append(speed(p.bytesPerSec))
            append(" · ").append(p.percent).append("%")
        },
        fontSize = 11.5.sp,
        color = pal().hint,
    )
    Spacer(Modifier.height(14.dp))
    ButtonRow(
        cancelText = "取消",
        onCancel = onLater,
        confirmText = "${p.percent}%",
        onConfirm = {},
        confirmEnabled = false,
    )
}

@Composable
private fun FailedBody(ui: UpdateUi.Failed, onUpdate: () -> Unit, onLater: () -> Unit) {
    DialogTitle("更新没有完成")
    Spacer(Modifier.height(7.dp))
    Text(ui.reason, fontSize = 12.5.sp, lineHeight = 19.sp, color = pal().hint)
    Spacer(Modifier.height(16.dp))
    ButtonRow(
        cancelText = "取消",
        onCancel = onLater,
        confirmText = "重试",
        onConfirm = onUpdate,
        confirmEnabled = ui.manifest != null,
    )
}

@Composable
private fun PermissionBody(ui: UpdateUi.NeedPermission, onGoSettings: () -> Unit, onLater: () -> Unit) {
    DialogTitle("还需要一步")
    Spacer(Modifier.height(7.dp))
    Text(
        "系统默认禁止安装来路不明的应用。开启「允许此来源安装应用」后返回，会接着装 ${ui.manifest.versionName}。",
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
        color = pal().hint,
    )
    Spacer(Modifier.height(16.dp))
    ButtonRow(
        cancelText = "暂不更新",
        onCancel = onLater,
        confirmText = "去设置",
        onConfirm = onGoSettings,
    )
}

@Composable
private fun UpToDateBody(onLater: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(pal().okSoft),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.CHECK, 14.dp, pal().ok, 2f) }
        Spacer(Modifier.width(10.dp))
        DialogTitle("已是最新版本")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "当前 ${BuildConfig.VERSION_NAME}，没有可用的更新。",
        fontSize = 12.5.sp,
        lineHeight = 19.sp,
        color = pal().hint,
    )
    Spacer(Modifier.height(16.dp))
    ButtonRow(cancelText = null, onCancel = onLater, confirmText = "知道了", onConfirm = onLater)
}

// 显式指定 Locale.US：String.format 用默认 locale 时，某些语言会把小数点写成逗号
private fun mb(bytes: Long): String =
    if (bytes < 1024 * 1024) "${bytes / 1024} KB"
    else String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)

private fun speed(bytesPerSec: Long): String =
    if (bytesPerSec < 1024 * 1024) "${bytesPerSec / 1024} KB/s"
    else String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / 1024.0 / 1024.0)
