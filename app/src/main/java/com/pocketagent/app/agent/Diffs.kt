package com.pocketagent.app.agent

/**
 * 行级 diff。用来在审批弹层里告诉用户"这一改到底动了什么"，
 * 而不是让他对着一句"我要写入 notes.md"点同意。
 *
 * 实现刻意很朴素：先削掉公共前后缀（99% 的编辑都落在中间一小段），
 * 中间部分再用 LCS 对齐。中间过长时退化成"删一块 + 加一块"——
 * 这不是精度问题，是不想在手机上做几十万格的 DP。
 */
object Diffs {

    private const val LCS_LIMIT = 220

    /**
     * 超过这个字符数就不做逐行 diff。
     * 不是为了省 CPU，是为了**不把几百 KB 的文本 split 成几万个字符串对象**——
     * 手机上一次 GC 停顿足够让审批卡片卡住。大文件只给"行数变化"，用户仍能看到影响面。
     */
    private const val MAX_DIFF_CHARS = 400_000

    fun compute(oldText: String, newText: String, maxOut: Int = 300): List<DiffLine> {
        if (oldText == newText) return emptyList()
        if (oldText.length > MAX_DIFF_CHARS || newText.length > MAX_DIFF_CHARS) {
            return listOf(
                DiffLine(
                    DiffLine.SAME,
                    "文件较大（${oldText.length} → ${newText.length} 字符），未生成逐行 diff",
                )
            )
        }
        // 新建 / 清空：没有"旧内容"可对齐，直接全增 / 全删，
        // 否则 LCS 会先吐一行空的删除行（旧内容是单个空串），看着像莫名其妙删了一行
        if (oldText.isEmpty()) return plain(newText, DiffLine.ADD, maxOut)
        if (newText.isEmpty()) return plain(oldText, DiffLine.DEL, maxOut)

        val a = oldText.split('\n')
        val b = newText.split('\n')

        var head = 0
        while (head < a.size && head < b.size && a[head] == b[head]) head++
        var tail = 0
        while (tail < a.size - head && tail < b.size - head &&
            a[a.size - 1 - tail] == b[b.size - 1 - tail]
        ) tail++

        val out = ArrayList<DiffLine>()
        // 上下文：只保留变更前后各 3 行，太多会把审批卡片撑成一篇文档
        val ctxStart = (head - 3).coerceAtLeast(0)
        for (i in ctxStart until head) out += DiffLine(DiffLine.SAME, a[i])
        if (ctxStart > 0) out.add(0, DiffLine(DiffLine.SAME, "…"))

        val midA = a.subList(head, a.size - tail)
        val midB = b.subList(head, b.size - tail)

        if (midA.size <= LCS_LIMIT && midB.size <= LCS_LIMIT) {
            out += lcs(midA, midB)
        } else {
            midA.forEach { out += DiffLine(DiffLine.DEL, it) }
            midB.forEach { out += DiffLine(DiffLine.ADD, it) }
        }

        val ctxEnd = (a.size - tail + 3).coerceAtMost(a.size)
        for (i in (a.size - tail) until ctxEnd) out += DiffLine(DiffLine.SAME, a[i])
        if (ctxEnd < a.size) out += DiffLine(DiffLine.SAME, "…")

        if (out.size <= maxOut) return out
        return cap(out, maxOut)
    }

    /**
     * 全文新增 / 全文删除。边切边截断，不先 split 出完整列表 ——
     * 删一个 5 万行的文件不该先生成 5 万个 DiffLine 再扔掉。
     */
    private fun plain(text: String, kind: Int, maxOut: Int): List<DiffLine> {
        val out = ArrayList<DiffLine>(maxOut + 1)
        var start = 0
        var count = 0
        while (count < maxOut) {
            val idx = text.indexOf('\n', start)
            if (idx < 0) {
                out += DiffLine(kind, text.substring(start))
                count++
                start = text.length
                break
            }
            out += DiffLine(kind, text.substring(start, idx))
            count++
            start = idx + 1
        }
        if (start < text.length) out += DiffLine(DiffLine.SAME, "…（还有更多行未显示）")
        return out
    }

    private fun cap(lines: List<DiffLine>, maxOut: Int): List<DiffLine> {
        if (lines.size <= maxOut) return lines
        val cut = lines.take(maxOut).toMutableList()
        cut += DiffLine(DiffLine.SAME, "…（还有 ${lines.size - maxOut} 行未显示）")
        return cut
    }

    private fun lcs(a: List<String>, b: List<String>): List<DiffLine> {
        val n = a.size
        val m = b.size
        if (n == 0) return b.map { DiffLine(DiffLine.ADD, it) }
        if (m == 0) return a.map { DiffLine(DiffLine.DEL, it) }

        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1
                else maxOf(dp[i + 1][j], dp[i][j + 1])
            }
        }
        val out = ArrayList<DiffLine>(n + m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { out += DiffLine(DiffLine.SAME, a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { out += DiffLine(DiffLine.DEL, a[i]); i++ }
                else -> { out += DiffLine(DiffLine.ADD, b[j]); j++ }
            }
        }
        while (i < n) { out += DiffLine(DiffLine.DEL, a[i]); i++ }
        while (j < m) { out += DiffLine(DiffLine.ADD, b[j]); j++ }
        return out
    }

    /** 新增/删除行数，用于「+3 −1」这种紧凑摘要 */
    fun stat(lines: List<DiffLine>): Pair<Int, Int> {
        var add = 0
        var del = 0
        lines.forEach {
            if (it.kind == DiffLine.ADD) add++
            else if (it.kind == DiffLine.DEL) del++
        }
        return add to del
    }
}
