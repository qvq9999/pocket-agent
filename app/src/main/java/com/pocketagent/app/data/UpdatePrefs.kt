package com.pocketagent.app.data

import android.content.Context

/**
 * 更新相关的本地状态。
 *
 * 单独一份 SharedPreferences，**不进 `state.json`**：
 * 这些是"这台机器上的更新进度"，不属于对话数据。塞进 `PersistedState` 会让
 * `Store.CURRENT_VERSION` 跟着升一档，还要为它写一遍迁移，纯属自找麻烦。
 * 写法沿用 [SearchSettings]（同样是"与会话无关的本机设置"）。
 */
object UpdatePrefs {

    private const val FILE = "pa_update"

    /** 上次成功检查的时间戳（毫秒）。节流只认它 */
    private const val K_LAST_CHECK = "lastCheckAt"

    /** 上次检查是否成功。设置页据此显示「检查失败」而不是假装「已是最新」 */
    private const val K_LAST_OK = "lastCheckOk"
    private const val K_LAST_ERROR = "lastError"

    /** 已经为用户自动弹过的 versionCode。同一版本只自动弹一次 */
    private const val K_OFFERED = "offeredCode"

    /**
     * 用户点过「取消」的 versionCode。
     *
     * 这是整个更新流程里最容易被省掉、也最容易惹人的一条：
     * 没有它，24 小时一轮会变成"每天弹一次同一个版本"。
     * 之后只在设置页显示「新版本 x.y.z」，不再自动弹。
     */
    private const val K_SKIPPED = "skippedCode"

    /** 上次下载成功/测速最快的镜像前缀（空串 = 官方直连） */
    private const val K_MIRROR = "mirrorPrefix"

    /** 已下载但还没安装的 versionCode，用于「继续下载」 */
    private const val K_PENDING = "pendingCode"

    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun lastCheckAt(ctx: Context): Long = sp(ctx).getLong(K_LAST_CHECK, 0L)

    fun lastCheckOk(ctx: Context): Boolean = sp(ctx).getBoolean(K_LAST_OK, false)

    fun lastError(ctx: Context): String = sp(ctx).getString(K_LAST_ERROR, "").orEmpty()

    fun markChecked(ctx: Context, ok: Boolean, error: String = "") {
        sp(ctx).edit()
            .putLong(K_LAST_CHECK, System.currentTimeMillis())
            .putBoolean(K_LAST_OK, ok)
            .putString(K_LAST_ERROR, error.take(200))
            .apply()
    }

    fun offeredCode(ctx: Context): Int = sp(ctx).getInt(K_OFFERED, 0)

    fun markOffered(ctx: Context, code: Int) {
        sp(ctx).edit().putInt(K_OFFERED, code).apply()
    }

    fun skippedCode(ctx: Context): Int = sp(ctx).getInt(K_SKIPPED, 0)

    fun skip(ctx: Context, code: Int) {
        sp(ctx).edit().putInt(K_SKIPPED, code).apply()
    }

    /** 用户手动点了「检查更新」并真的命中了新版：把跳过记录清掉，让他能重新被提醒 */
    fun clearSkip(ctx: Context) {
        sp(ctx).edit().remove(K_SKIPPED).apply()
    }

    fun mirrorPrefix(ctx: Context): String = sp(ctx).getString(K_MIRROR, "").orEmpty()

    fun setMirrorPrefix(ctx: Context, prefix: String) {
        sp(ctx).edit().putString(K_MIRROR, prefix).apply()
    }

    fun pendingCode(ctx: Context): Int = sp(ctx).getInt(K_PENDING, 0)

    fun setPending(ctx: Context, code: Int) {
        sp(ctx).edit().putInt(K_PENDING, code).apply()
    }

    fun clearPending(ctx: Context) {
        sp(ctx).edit().remove(K_PENDING).apply()
    }
}
