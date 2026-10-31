package com.pocketagent.app.data

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * 联网搜索的偏好与配额。
 *
 * 用 SharedPreferences 而不是 Store：
 *  - 这里只有开关、密文 key、当日计数三样东西，属于"临时态"，写进 PersistedState
 *    会让每次搜索都触发一次整份会话状态的序列化；
 *  - 当日计数每天清零，丢了也只是少一次保护，不影响功能。
 *
 * key 走 [KeyVault] 加密后落盘，明文只在内存里活一次请求的时间。
 */
object SearchSettings {

    private const val FILE = "search_prefs"
    private const val K_ENABLED = "enabled"
    private const val K_KEY = "key_cipher"
    private const val K_DAY = "quota_day"
    private const val K_COUNT = "quota_count"

    /** 匿名额度是全网共享的，本地必须自己踩刹车，别让一次 Agent 循环把它抽干 */
    private const val CAP_ANON = 300
    private const val CAP_KEYED = 1_500

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ── 开关 ──

    /** 默认开启：用户不需要先去某个页面"启用"才能让模型联网 */
    fun isEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(K_ENABLED, true)

    fun setEnabled(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean(K_ENABLED, on).apply()
    }

    // ── 密钥 ──

    fun hasKey(ctx: Context): Boolean = keyPlain(ctx).isNotBlank()

    /** 明文 key；没配或解不开都返回空串（解不开说明密钥库被重置，等于没配） */
    fun keyPlain(ctx: Context): String {
        val cipher = sp(ctx).getString(K_KEY, "").orEmpty()
        if (cipher.isBlank()) return ""
        return runCatching { KeyVault.decrypt(cipher) }.getOrDefault("")
    }

    fun setKey(ctx: Context, plain: String) {
        val trimmed = plain.trim()
        val cipher = if (trimmed.isEmpty()) "" else runCatching { KeyVault.encrypt(trimmed) }.getOrDefault("")
        sp(ctx).edit().putString(K_KEY, cipher).apply()
    }

    /** 只给界面看的掩码：as_sk_****36c2 */
    fun maskedKey(ctx: Context): String {
        val k = keyPlain(ctx)
        if (k.isBlank()) return ""
        return if (k.length <= 10) "已配置" else k.take(6) + "…" + k.takeLast(4)
    }

    // ── 当日配额 ──

    fun cap(ctx: Context): Int = if (hasKey(ctx)) CAP_KEYED else CAP_ANON

    fun usedToday(ctx: Context): Int {
        val today = LocalDate.now().toString()
        val p = sp(ctx)
        if (p.getString(K_DAY, "") != today) return 0
        return p.getInt(K_COUNT, 0)
    }

    fun remaining(ctx: Context): Int = (cap(ctx) - usedToday(ctx)).coerceAtLeast(0)

    /**
     * 占用一次额度。超限返回 false —— 调用方要把这件事如实告诉模型，
     * 让它对用户说"今天搜不了了"，而不是悄悄降级成"没有搜索结果"。
     */
    fun tryConsume(ctx: Context): Boolean {
        val today = LocalDate.now().toString()
        val p = sp(ctx)
        val used = if (p.getString(K_DAY, "") == today) p.getInt(K_COUNT, 0) else 0
        if (used >= cap(ctx)) return false
        p.edit().putString(K_DAY, today).putInt(K_COUNT, used + 1).apply()
        return true
    }
}
