package com.pocketagent.app.agent

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar

/**
 * 自动记忆：**在本机**把"用户怎么用这个 app"归纳成几条可读的条目。
 *
 * 三条设计红线（每一条都对应一种常见的产品失误）：
 *
 * 1. **不调模型**。归纳发生在每次工具调用之后，是纯本地的计数与去重。
 *    如果用 LLM 来"总结用户习惯"，一次总结就是一次请求：慢、要钱、
 *    而且结论不可解释 —— 用户看到"它觉得你喜欢简洁回复"，无从判断依据是什么。
 *    这里的每条习惯都能回答"依据是哪几次操作"。
 *
 * 2. **只记行为，不记内容**。记的是"用 docx_fill 填过 Word 表格 9 次"，
 *    不是"填的是张三的简历"。文件名、正文、聊天原文一律不落盘。
 *    目录名是唯一会出现的具体字符串（没有它，"常用工作区"这条就没有意义），
 *    并且遇到像手机号/证件号的目录名会直接跳过不记。
 *
 * 3. **删除是真的删除**。删掉一条 → 从 JSON 里移除；同时落一条"墓碑"，
 *    让后续的行为采集**不会把它重新记回来**。否则用户删了三次，
 *    第四次又被自动写回，删除按钮就成了摆设。
 *
 * 数据落在 `filesDir/memory/_profile.json`（app 私有），与笔记 md 同目录，
 * 「清空全部记忆」时整目录一起抹掉。
 */
class MemoryHabits(private val root: File) {

    /** 生产环境只有一个入口：app 私有目录下的 memory/ */
    constructor(ctx: Context) : this(File(ctx.filesDir, "memory"))

    // ─────────── 落盘结构 ───────────

    @Serializable
    data class Counter(val key: String, var n: Int = 0, var lastAt: Long = 0L)

    @Serializable
    data class Act(
        val at: Long,
        val kind: String,
        val tools: List<String> = emptyList(),
        val ok: Boolean = true,
        val ms: Long = 0L,
    )

    /**
     * 墓碑：用户删掉的东西。
     *
     * [key] 是稳定标识（`ws:简历` / `kind:word` / `note:写作偏好`），
     * [digest] 是笔记正文的归一化指纹，用来拦住"换个标题重写同一段话"。
     */
    @Serializable
    data class Tomb(val key: String, val at: Long, val digest: String = "")

    @Serializable
    data class State(
        val v: Int = 1,
        /** 自动记录总开关。关掉只是停止采集，已有条目仍可查看与删除 */
        val auto: Boolean = true,
        val workspaces: MutableList<Counter> = mutableListOf(),
        val kinds: MutableList<Counter> = mutableListOf(),
        val tools: MutableList<Counter> = mutableListOf(),
        val activity: MutableList<Act> = mutableListOf(),
        val tombs: MutableList<Tomb> = mutableListOf(),
    )

    /** UI 用的一条记忆。id 稳定，删除时按 id 定位 */
    data class Entry(
        val id: String,
        val group: Group,
        val title: String,
        val detail: String,
        val count: Int,
        val lastAt: Long,
    )

    enum class Group { HABIT, ACTIVITY }

    private val file = File(root, "_profile.json")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private var state: State = load()

    // ─────────── 读写 ───────────

    private fun load(): State {
        if (!file.exists()) return State()
        val text = runCatching { file.readText() }.getOrNull() ?: return State()
        return runCatching { json.decodeFromString(State.serializer(), text) }
            .getOrElse {
                // 解析失败不能把历史整个丢掉，也不能让 app 起不来：
                // 挪成 .bak 留证据，然后从空状态继续
                runCatching { file.renameTo(File(root, "_profile.corrupt.json")) }
                State()
            }
    }

    private fun persist() {
        runCatching {
            if (!root.exists()) root.mkdirs()
            val tmp = File(root, "_profile.tmp")
            tmp.writeText(json.encodeToString(State.serializer(), state))
            // 先写临时文件再改名：中途被杀不会留下半截 JSON
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    fun autoEnabled(): Boolean = state.auto

    @Synchronized
    fun setAuto(on: Boolean) {
        state = state.copy(auto = on)
        persist()
    }

    // ─────────── 采集 ───────────

    /**
     * 一轮对话结束后记一笔。
     *
     * 粒度刻意是"轮"不是"次调用"：一轮里 read 了 5 个文件，记成 5 次只会让统计失真，
     * 真正想回答的问题是"他常在这类事情上花时间"。
     *
     * @param workspace 工作区显示名（可能是空）
     * @param tools 本轮用到的工具（已去重）
     */
    @Synchronized
    fun recordTurn(workspace: String?, tools: Set<String>, ok: Boolean, ms: Long) {
        if (!state.auto) return
        val now = System.currentTimeMillis()
        val kind = kindOf(tools)

        val wsKey = workspace?.trim().orEmpty().take(40)
        if (wsKey.isNotEmpty() && !isSensitiveLabel(wsKey) && !isTombed("ws:$wsKey")) {
            bump(state.workspaces, wsKey, now)
            state.workspaces.sortWith(compareByDescending<Counter> { it.n }.thenByDescending { it.lastAt })
            while (state.workspaces.size > MAX_WS) state.workspaces.removeAt(state.workspaces.size - 1)
        }

        if (kind != null && !isTombed("kind:$kind")) {
            bump(state.kinds, kind, now)
            state.kinds.sortWith(compareByDescending<Counter> { it.n }.thenByDescending { it.lastAt })
            while (state.kinds.size > MAX_KINDS) state.kinds.removeAt(state.kinds.size - 1)

            for (t in tools.filter { it in TRACKED_TOOLS }) {
                if (isTombed("tool:$t")) continue
                bump(state.tools, t, now)
            }
            state.tools.sortWith(compareByDescending<Counter> { it.n }.thenByDescending { it.lastAt })
            while (state.tools.size > MAX_TOOLS) state.tools.removeAt(state.tools.size - 1)
        }

        if (kind != null && tools.isNotEmpty()) {
            state.activity.add(
                0,
                Act(
                    at = now,
                    kind = kind,
                    tools = tools.filter { it in TRACKED_TOOLS }.take(3),
                    ok = ok,
                    ms = ms,
                ),
            )
        }
        pruneActivity(now)
        persist()
    }

    /** 只留最近的：条数上限 + 天数上限，两个都要，否则要么爆要么忘 */
    private fun pruneActivity(now: Long) {
        val cutoff = now - ACT_DAYS * 24L * 3600 * 1000
        state.activity.removeAll { it.at < cutoff }
        while (state.activity.size > MAX_ACT) state.activity.removeAt(state.activity.size - 1)
    }

    private fun bump(list: MutableList<Counter>, key: String, now: Long) {
        val hit = list.firstOrNull { it.key == key }
        if (hit == null) list.add(Counter(key, 1, now))
        else {
            hit.n += 1
            hit.lastAt = now
        }
    }

    // ─────────── 读取（给 UI + 提示词） ───────────

    @Synchronized
    fun entries(): List<Entry> {
        val out = ArrayList<Entry>()
        for (c in state.workspaces) {
            out.add(
                Entry(
                    id = "ws:${c.key}",
                    group = Group.HABIT,
                    title = "常用工作区：${c.key}",
                    detail = "在这个目录里干过 ${c.n} 轮 · 最近 ${timeText(c.lastAt)}",
                    count = c.n,
                    lastAt = c.lastAt,
                )
            )
        }
        for (c in state.kinds) {
            out.add(
                Entry(
                    id = "kind:${c.key}",
                    group = Group.HABIT,
                    title = "常做：${kindLabel(c.key)}",
                    detail = "${c.n} 轮 · 最近 ${timeText(c.lastAt)}",
                    count = c.n,
                    lastAt = c.lastAt,
                )
            )
        }
        for (c in state.tools) {
            out.add(
                Entry(
                    id = "tool:${c.key}",
                    group = Group.HABIT,
                    title = "常用工具：${c.key}",
                    detail = "在 ${c.n} 轮里用到 · 最近 ${timeText(c.lastAt)}",
                    count = c.n,
                    lastAt = c.lastAt,
                )
            )
        }
        for ((idx, a) in state.activity.withIndex()) {
            out.add(
                Entry(
                    // 带上序号：同一毫秒完成两轮也不是不可能，LazyColumn 的 key 必须唯一
                    id = "act:${a.at}:$idx",
                    group = Group.ACTIVITY,
                    title = "${timeText(a.at)} · ${kindLabel(a.kind)}",
                    detail = buildString {
                        if (a.tools.isNotEmpty()) append(a.tools.joinToString("、"))
                        append(if (a.ok) " · 成功" else " · 有失败")
                        if (a.ms > 0) append(" · ").append(formatSec(a.ms))
                    },
                    count = 1,
                    lastAt = a.at,
                )
            )
        }
        return out
    }

    private fun formatSec(ms: Long): String {
        val s = ms / 1000.0
        return if (s < 10) String.format(java.util.Locale.CHINA, "%.1f 秒", s) else "${ms / 1000} 秒"
    }

    /**
     * 塞进 system prompt 的一段。**必须短**。
     *
     * 冷记忆的前提是"按需读取"，但"用户常做什么"这种元信息每次都用得上 ——
     * 前提是它足够短（这里硬性截到 360 字）。超了就砍条目，不够正文长。
     */
    @Synchronized
    fun promptBlock(): String {
        if (!state.auto) return ""
        if (state.workspaces.isEmpty() && state.kinds.isEmpty() && state.activity.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("## 使用习惯（本机自动归纳，只记行为不记内容；可能已过期）\n")
        if (state.workspaces.isNotEmpty()) {
            sb.append("- 常用工作区：")
                .append(state.workspaces.take(3).joinToString("、") { "${it.key}（${it.n} 轮）" })
                .append('\n')
        }
        if (state.kinds.isNotEmpty()) {
            sb.append("- 常做的事：")
                .append(state.kinds.take(3).joinToString("、") { "${kindLabel(it.key)}×${it.n}" })
                .append('\n')
        }
        if (state.tools.isNotEmpty()) {
            sb.append("- 常用工具：")
                .append(state.tools.take(4).joinToString("、") { it.key })
                .append('\n')
        }
        state.activity.firstOrNull()?.let { a ->
            sb.append("- 最近一次：").append(timeText(a.at)).append(' ')
                .append(kindLabel(a.kind))
                .append(if (a.ok) "（成功）" else "（有失败）")
                .append('\n')
        }
        sb.append("这些是行为统计，不含文件名与内容。用户明确说过的偏好以记忆笔记为准。")
        val text = sb.toString()
        return if (text.length <= PROMPT_MAX) text else text.take(PROMPT_MAX) + "\n…（已截断）"
    }

    /** 是否有任何自动记忆（UI 判断空态用） */
    @Synchronized
    fun isEmpty(): Boolean =
        state.workspaces.isEmpty() && state.kinds.isEmpty() &&
            state.tools.isEmpty() && state.activity.isEmpty()

    // ─────────── 删除 ───────────

    /**
     * 删一条。返回 true 表示确实删掉了东西。
     *
     * 三类 habit 条目删除后都会落墓碑，**除非**用户主动重开自动记录
     * （[setAuto] 传 true 时不动墓碑：重开开关不该等于"恢复我以为删干净的那些"）。
     */
    @Synchronized
    fun deleteEntry(id: String): Boolean {
        val now = System.currentTimeMillis()
        val removed = when {
            id.startsWith("ws:") -> {
                val k = id.removePrefix("ws:")
                state.workspaces.removeAll { it.key == k }
            }
            id.startsWith("kind:") -> {
                val k = id.removePrefix("kind:")
                state.kinds.removeAll { it.key == k }
            }
            id.startsWith("tool:") -> {
                val k = id.removePrefix("tool:")
                state.tools.removeAll { it.key == k }
            }
            id.startsWith("act:") -> {
                val at = id.removePrefix("act:").substringBefore(":").toLongOrNull()
                at != null && state.activity.removeAll { it.at == at }
            }
            else -> false
        }
        // 操作流水删掉就不落墓碑：它是一次性的历史，不是被推断出来的偏好
        if (removed && !id.startsWith("act:")) tomb(id, "", now)
        if (removed) persist()
        return removed
    }

    /** 清掉操作流水，保留习惯条目 */
    @Synchronized
    fun clearActivity() {
        state.activity.clear()
        persist()
    }

    /** 用户删了一篇笔记 → 落墓碑，防止模型下次换个说法又写回来 */
    @Synchronized
    fun rememberNoteDeletion(name: String, content: String) {
        tomb("note:${normalizeName(name)}", digest(content), System.currentTimeMillis())
        persist()
    }

    @Synchronized
    fun blockedNote(name: String, content: String): Boolean {
        val key = "note:${normalizeName(name)}"
        if (state.tombs.any { it.key == key }) return true
        val d = digest(content)
        return d.isNotEmpty() && state.tombs.any { it.digest == d }
    }

    private fun tomb(key: String, digest: String, now: Long) {
        state.tombs.removeAll { it.key == key }
        state.tombs.add(Tomb(key, now, digest))
        while (state.tombs.size > TOMB_MAX) state.tombs.removeAt(0)
    }

    private fun isTombed(key: String): Boolean = state.tombs.any { it.key == key }

    /** 清空全部：连墓碑一起抹，这样"从头再来"是真的从头 */
    @Synchronized
    fun wipe() {
        state = State(auto = state.auto)
        runCatching { file.delete() }
    }

    // ─────────── 小工具 ───────────

    private fun normalizeName(name: String): String =
        name.removeSuffix(".md").trim().lowercase().replace(Regex("\\s+"), " ")

    /** 正文指纹：去空白、取前 120 字。用来识别"同一段话换个标题重写" */
    private fun digest(content: String): String {
        val n = content.replace(Regex("\\s+"), "").lowercase()
        if (n.length < 12) return ""
        return n.take(120)
    }

    /**
     * 目录名里出现手机号/证件号这类长数字串时不记录。
     *
     * 这是最低限度的护栏：我们不去猜"这个目录是不是私人"，只处理**明显**的标识符。
     * 过度过滤会让"常用工作区"这条彻底失去信息量。
     */
    private fun isSensitiveLabel(s: String): Boolean {
        if (Regex("""\d{9,}""").containsMatchIn(s)) return true
        return Regex("身份证|护照|银行卡|密码|私钥", RegexOption.IGNORE_CASE).containsMatchIn(s)
    }

    private fun timeText(ms: Long): String {
        if (ms <= 0) return "未知时间"
        val now = Calendar.getInstance()
        val t = Calendar.getInstance().apply { timeInMillis = ms }
        val sameDay = now.get(Calendar.YEAR) == t.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
        val hm = String.format(java.util.Locale.CHINA, "%02d:%02d", t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
        if (sameDay) return "今天 $hm"
        val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = y.get(Calendar.YEAR) == t.get(Calendar.YEAR) &&
            y.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) return "昨天 $hm"
        return String.format(
            java.util.Locale.CHINA, "%02d-%02d %s",
            t.get(Calendar.MONTH) + 1, t.get(Calendar.DAY_OF_MONTH), hm,
        )
    }

    companion object {
        /** 操作流水最多留多少条 */
        const val MAX_ACT = 40

        /** 操作流水的保留天数 */
        const val ACT_DAYS = 7

        private const val MAX_WS = 6
        private const val MAX_KINDS = 8
        private const val MAX_TOOLS = 12
        private const val TOMB_MAX = 60

        /** 提示词里这段的上限 */
        private const val PROMPT_MAX = 360

        /** 参与"常用工具"统计的工具。记忆自身的读写不算使用习惯 */
        private val TRACKED_TOOLS = setOf(
            ToolPolicy.LIST_DIR, ToolPolicy.READ_FILE, ToolPolicy.STAT, ToolPolicy.GREP_FILES,
            ToolPolicy.DOCX_READ, ToolPolicy.XLSX_READ,
            ToolPolicy.WRITE_FILE, ToolPolicy.REPLACE_IN_FILE, ToolPolicy.APPEND_FILE,
            ToolPolicy.CREATE_DIR, ToolPolicy.DOCX_FILL, ToolPolicy.XLSX_SET,
            ToolPolicy.RUN_SHELL, ToolPolicy.COPY_PATH, ToolPolicy.MOVE_PATH, ToolPolicy.DELETE_PATH,
        )

        /** 任务类型的稳定 key → 展示名。key 用英文，改名不影响历史数据 */
        fun kindLabel(key: String): String = when (key) {
            "word" -> "Word 文档处理"
            "sheet" -> "表格处理"
            "shell" -> "命令行"
            "text" -> "文本编辑"
            "read" -> "查看文件"
            else -> key
        }

        private fun kindOf(tools: Set<String>): String? = when {
            tools.any { it == ToolPolicy.DOCX_FILL || it == ToolPolicy.DOCX_READ } -> "word"
            tools.any { it == ToolPolicy.XLSX_SET || it == ToolPolicy.XLSX_READ } -> "sheet"
            tools.any { it == ToolPolicy.RUN_SHELL } -> "shell"
            tools.any {
                it == ToolPolicy.WRITE_FILE || it == ToolPolicy.REPLACE_IN_FILE || it == ToolPolicy.APPEND_FILE
            } -> "text"
            tools.any { it in TRACKED_TOOLS } -> "read"
            else -> null
        }
    }
}
