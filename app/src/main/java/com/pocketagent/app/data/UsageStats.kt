package com.pocketagent.app.data

/**
 * 用量账单的聚合。
 *
 * 借鉴 rikkahub-agent-pure 的三条口径（它踩过的坑，我们没必要再踩一遍）：
 *
 * 1. **缺失保持缺失**。厂商没上报缓存字段时，缓存命中率是 `null`（UI 显示"—"），
 *    不是 0%。把"不知道"画成"0%"，用户会以为缓存完全没命中，进而去调无关的参数。
 * 2. **分子分母同源**。平均速度只统计**真正上报了耗时**的那些调用，
 *    分子用它们的输出 token、分母用它们的耗时；没测到的调用既不能拉高也不能稀释。
 * 3. **成本在写入时冻结**（本项目暂不接价格表，先把 token 维度做准；加价格时不能回改历史）。
 *
 * 纯函数，不依赖 Android，方便单独验证。
 */
object UsageStats {

    /** 一次调用的聚合结果 */
    data class Bucket(
        val key: String = "",
        val calls: Int = 0,
        val prompt: Long = 0,
        val completion: Long = 0,
        /** 只累加"真的上报了缓存"的那些行 */
        val cached: Long = 0,
        val cachePrompt: Long = 0,
        val cacheReported: Boolean = false,
        /** 只累加"真的上报了耗时"的那些行 */
        val measuredOutput: Long = 0,
        val genMs: Long = 0,
    ) {
        val total: Long get() = prompt + completion

        /** 缓存命中率（整数百分比）。没有任何一行上报过 → null，不是 0 */
        val cacheRate: Int?
            get() = if (cacheReported && cachePrompt > 0L) (cached * 100L / cachePrompt).toInt() else null

        /** 输出速度 tok/s。没有可用的耗时数据 → null */
        val tokensPerSecond: Double?
            get() = if (genMs > 0L && measuredOutput > 0L) measuredOutput * 1000.0 / genMs else null
    }

    /** 一个时间窗口内的全部视图 */
    data class View(
        val total: Bucket = Bucket(),
        /** 按日期倒序（key 为 yyyy-MM-dd） */
        val byDay: List<Bucket> = emptyList(),
        /** token 从大到小 */
        val byModel: List<Bucket> = emptyList(),
        /** token 从大到小 */
        val byPurpose: List<Bucket> = emptyList(),
        /** 最近 N 条明细，新的在前 */
        val recent: List<UsageEntry> = emptyList(),
    ) {
        fun shareOf(b: Bucket): Float =
            if (total.total <= 0L) 0f else b.total.toFloat() / total.total
    }

    private fun acc(rows: List<UsageEntry>): Bucket {
        var calls = 0
        var prompt = 0L
        var completion = 0L
        var cached = 0L
        var cachePrompt = 0L
        var cacheReported = false
        var measuredOutput = 0L
        var genMs = 0L
        for (r in rows) {
            calls++
            prompt += r.prompt.toLong()
            completion += r.completion.toLong()
            if (r.cacheReported) {
                cacheReported = true
                cached += r.cached.toLong()
                cachePrompt += r.prompt.toLong()
            }
            if (r.latencyMs > 0L) {
                genMs += r.latencyMs
                measuredOutput += r.completion.toLong()
            }
        }
        return Bucket(
            key = "",
            calls = calls,
            prompt = prompt,
            completion = completion,
            cached = cached,
            cachePrompt = cachePrompt,
            cacheReported = cacheReported,
            measuredOutput = measuredOutput,
            genMs = genMs,
        )
    }

    private fun bucketize(
        rows: List<UsageEntry>,
        keyOf: (UsageEntry) -> String,
    ): List<Bucket> {
        val groups = LinkedHashMap<String, MutableList<UsageEntry>>()
        for (r in rows) groups.getOrPut(keyOf(r)) { ArrayList() }.add(r)
        return groups.map { (k, rs) -> acc(rs).copy(key = k) }
            // token 多的排前面；并列时按调用次数，再按 key 稳定排序
            .sortedWith(
                compareByDescending<Bucket> { it.total }
                    .thenByDescending { it.calls }
                    .thenBy { it.key }
            )
    }

    /** 日期键 yyyy-MM-dd，按设备本地时区 */
    fun dayKeyOf(at: Long): String =
        java.time.Instant.ofEpochMilli(at)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString()

    /**
     * 生成统计视图。
     *
     * @param entries 账单原始行（任意顺序）
     * @param recentLimit 明细列表最多返回多少条
     */
    fun build(entries: List<UsageEntry>, recentLimit: Int = 60): View {
        if (entries.isEmpty()) return View()
        val byDay = bucketize(entries) { dayKeyOf(it.at) }.sortedByDescending { it.key }
        val byModel = bucketize(entries) { it.model.ifBlank { "未知模型" } }
        val byPurpose = bucketize(entries) { purposeLabel(it.purpose) }
        val recent = entries.sortedByDescending { it.at }.take(recentLimit)
        return View(
            total = acc(entries),
            byDay = byDay,
            byModel = byModel,
            byPurpose = byPurpose,
            recent = recent,
        )
    }

    /** 某个日期区间内的行（含当天） */
    fun since(entries: List<UsageEntry>, days: Int, nowMs: Long = System.currentTimeMillis()): List<UsageEntry> {
        val from = nowMs - days * 86_400_000L
        return entries.filter { it.at >= from }
    }

    fun purposeLabel(p: String): String = when (p) {
        "MAIN" -> "对话"
        "TOOL_LOOP" -> "工具循环"
        "COMPACTION" -> "上下文压缩"
        "TRANSLATION" -> "翻译"
        "TITLE" -> "生成标题"
        else -> "其它"
    }

    /** 数字紧凑显示：1234 → 1.2k */
    fun compact(n: Long): String = when {
        n < 1000 -> n.toString()
        n < 1_000_000 -> String.format(java.util.Locale.CHINA, "%.1fk", n / 1000.0)
        else -> String.format(java.util.Locale.CHINA, "%.2fM", n / 1_000_000.0)
    }
}
