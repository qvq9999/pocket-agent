package com.pocketagent.app.data

import kotlinx.serialization.Serializable

/**
 * 远端版本清单（`version.json`，随 Release 一起上传）。
 *
 * **判定"有没有新版"只认 [versionCode]**：它是单调递增的整数，
 * 而 `versionName` 是展示用的字符串 —— 拿 `"0.10.0" < "0.9.0"` 去比字符串
 * 迟早会把用户锁在旧版本上。所以这里两个字段各有分工，不能互相替代。
 *
 * 所有字段都给默认值：清单是远端可控的，将来删字段/加字段时，
 * 旧客户端必须能解析出"没有更新"而不是直接崩掉（配合 `ignoreUnknownKeys`）。
 */
@Serializable
data class UpdateManifest(
    /** 清单格式版本，留给将来结构性变更 */
    val schema: Int = 1,
    val versionCode: Int = 0,
    val versionName: String = "",
    /**
     * 最低可支持版本。低于它的客户端**必须**更新，弹窗不给取消。
     *
     * 用在"服务端接口不兼容、旧版会报一堆莫名错误"的场景；
     * 平时留 0，别拿它当"催更新"的手段 —— 强制更新的代价是用户可能直接卸载。
     */
    val minSupported: Int = 0,
    val sizeBytes: Long = 0L,
    /** APK 的 SHA-256（小写十六进制）。为空时不允许自动安装，只能跳浏览器 */
    val sha256: String = "",
    val publishedAt: String = "",
    val apkUrl: String = "",
    /**
     * 更新日志，逐条短句。
     *
     * 用数组而不是一整段文本：UI 是按条渲染成列表的（对齐小节圆点），
     * 一段长文本要么被截断，要么把弹窗撑成一屏。
     */
    val notes: List<String> = emptyList(),
    /** 详细日志的网页地址；与 [notes] 二选一，都有时以 notes 为准 */
    val notesUrl: String = "",
) {

    /**
     * 是否可用来自动下载安装。
     *
     * 缺 sha256 或地址不是 https 时**降级**成"打开浏览器"：
     * 校验不了的安装包等于让用户盲装，而 http 明文地址可以被人替换。
     * 这两条不是洁癖，是"更新通道"本身的信任前提。
     */
    val canAutoInstall: Boolean
        get() = apkUrl.startsWith("https://") && sha256.length == 64

    fun isNewerThan(current: Int): Boolean = versionCode > current

    fun isForcedFor(current: Int): Boolean = minSupported > 0 && current < minSupported
}
