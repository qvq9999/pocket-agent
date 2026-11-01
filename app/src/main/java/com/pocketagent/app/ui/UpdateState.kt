package com.pocketagent.app.ui

import com.pocketagent.app.data.UpdateManifest
import com.pocketagent.app.net.ApkDownloader

/**
 * 更新弹窗的状态。**一个实例就地切换内容** —— 不做"关掉旧弹窗、开个新弹窗"：
 * 那样会出现遮罩闪一下、层级错乱，而且下载中的进度会丢。
 *
 * 只有 [Offer] 与 [Failed] 会阻断操作；[Downloading] 允许用户退到后台去干别的
 * （下载不依赖界面存活），[UpToDate] 只在手动检查时出现。
 */
sealed interface UpdateUi {

    /** 有可用更新。forced = 低于 minSupported，不给取消 */
    data class Offer(val manifest: UpdateManifest, val forced: Boolean) : UpdateUi

    data class Downloading(
        val manifest: UpdateManifest,
        val progress: ApkDownloader.Progress,
    ) : UpdateUi

    /** 下载/校验失败，或安装被系统拒了。[manifest] 可能为空（检查阶段就失败了） */
    data class Failed(val manifest: UpdateManifest?, val reason: String) : UpdateUi

    /** 缺「安装未知来源应用」权限，等用户去系统设置开 */
    data class NeedPermission(val manifest: UpdateManifest) : UpdateUi

    /** 手动检查且没有新版本 */
    data object UpToDate : UpdateUi
}
