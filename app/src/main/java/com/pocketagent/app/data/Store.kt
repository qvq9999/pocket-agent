package com.pocketagent.app.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

/**
 * 极简持久化：一个 JSON 文件 + 原子替换。
 * 刻意不引入 Room/DataStore —— v0 只需要存「厂商实例 + 模型」，把依赖面压到最小。
 *
 * ── 升级不丢数据的四道保险 ──
 * 1. `encodeDefaults = true`：默认值也写盘。否则 `version` 这类字段永远不会出现，
 *    将来无法判断文件格式版本，迁移无从下手。
 * 2. `coerceInputValues = true`：未知枚举值 / 非法字段转成默认值，而不是抛异常。
 * 3. 解析失败绝不静默覆盖：坏文件另存为 `state.corrupt-<ts>.json` 留证，
 *    再从 `state.backup.json` 回滚。
 * 4. 逐条抢救：整个文件解析不了时，逐条解析 `providers` 数组，
 *    坏一条不影响其余，能救几个是几个。
 */
class Store(context: Context) {

    companion object {
        /**
         * 每次改 PersistedState 结构时 +1，并在 migrate() 里补迁移逻辑。
         * v3：新增 sessions / currentSessionId（有默认值，旧文件可直接解析）
         * v4：消息新增 calls / tool（工具调用与执行结果），同样有默认值，无需改写历史数据
         * v5：新增 usageEntries（账单明细）与 toolAllowList（永久允许的工具），均有默认值
         * v6：新增 onboard（新手引导状态）。**老存档一律静默标记为已完成** ——
         *     升级上来的人已经自己摸熟了，再给他看"你好，我是…"是打扰
         */
        const val CURRENT_VERSION = 6

        /** 账单明细保留条数上限。JSON 全量重写，条数不设限会让存档无限膨胀 */
        const val MAX_USAGE_ENTRIES = 1000
    }

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "state.json")
    private val backupFile = File(appContext.filesDir, "state.backup.json")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // 非法枚举/类型不抛异常，转成默认值
        coerceInputValues = true
        // 默认值也写盘：保证 version 等字段真实存在于文件里
        encodeDefaults = true
        prettyPrint = true
    }

    private val writeLock = Mutex()

    /** load 完成前不允许落盘，否则启动竞态会用空状态覆盖用户的真实配置 */
    private val loadDone = CompletableDeferred<Unit>()

    private val _state = MutableStateFlow(PersistedState())
    val state: StateFlow<PersistedState> = _state.asStateFlow()

    /**
     * 本次加载的异常说明（正常为 null）。
     * UI 读取后应提示用户，而不是让"配置莫名消失"变成黑箱。
     */
    @Volatile
    var loadReport: String? = null
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            val raw = runCatching { if (file.exists()) file.readText() else null }.getOrNull()

            // ① 正常解析
            val parsed = raw?.let { decode(it) }

            var needsPersist = false

            val restored: PersistedState = when {
                parsed != null -> parsed

                raw != null -> {
                    // ② 主文件坏了：先留证
                    quarantine(raw)

                    // 优先做精确修复：只剔除坏厂商，其余设置全部保留
                    val repaired = repair(raw)
                    if (repaired != null) {
                        loadReport = "有 ${repaired.dropped} 个厂商记录损坏已跳过，其余设置与用量已保留"
                        needsPersist = true
                        repaired.state
                    } else {
                        // ③ 整体不可修复：回滚上一份备份
                        val fromBackup = runCatching {
                            if (backupFile.exists()) decode(backupFile.readText()) else null
                        }.getOrNull()

                        if (fromBackup != null) {
                            loadReport = "配置文件损坏，已从上一份备份恢复模型与设置"
                            needsPersist = true
                            fromBackup
                        } else {
                            // ④ 兜底：只抢救厂商列表
                            val salvaged = salvage(raw)
                            if (salvaged.isNotEmpty()) {
                                loadReport = "配置文件损坏，已抢救出 ${salvaged.size} 个厂商，" +
                                    "主题与用量已重置，原文件保留为 state.corrupt-*.json"
                                needsPersist = true
                                PersistedState(providers = salvaged)
                            } else {
                                loadReport = "配置文件已损坏且无法恢复，原文件已保留为 state.corrupt-*.json"
                                PersistedState()
                            }
                        }
                    }
                }

                // 真的第一次装：直接给当前版本号，别走 migrate ——
                // 否则"升级上来的老用户"和"新装的用户"在 migrate 里长得一模一样，
                // 会被一起标记成"引导已完成"，新用户就永远见不到引导了
                else -> PersistedState(version = CURRENT_VERSION)
            }

            val migrated = migrate(restored)
            _state.value = migrated
            // 版本升级或做过修复后立刻回写：否则坏文件一直留在盘上，
            // 每次启动都要重新修复一遍、重复弹同一条提示
            if (needsPersist || restored.version != CURRENT_VERSION) {
                runCatching { writeToDisk(migrated) }
            }
        } finally {
            loadDone.complete(Unit)
        }
    }

    /** 解析失败时把原始内容另存留证，绝不直接丢掉 —— 用户还能靠它手工找回 key 与端点 */
    private fun quarantine(raw: String) {
        runCatching {
            File(file.parentFile, "state.corrupt-${System.currentTimeMillis()}.json").writeText(raw)
        }
    }

    private fun decode(text: String): PersistedState? =
        runCatching { json.decodeFromString(PersistedState.serializer(), text) }.getOrNull()

    /**
     * 精确修复：整文件解析失败时，只从 providers 数组里剔除坏条目，
     * 其余字段（主题、权限、用量统计、工作区）原样保留后重新解析。
     *
     * 之前这里是"只抢救 providers"，等于一个厂商记录损坏就把用户所有设置清空 ——
     * 这个代价太大，不能这么干。
     */
    private fun repair(text: String): Repair? {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val arr = root["providers"] as? JsonArray ?: return null

        val good = arr.filter { el ->
            runCatching {
                json.decodeFromJsonElement(ProviderInstance.serializer(), el as JsonObject)
            }.isSuccess
        }
        if (good.size == arr.size) return null

        val repairedObj = JsonObject(root.toMutableMap().apply { put("providers", JsonArray(good)) })
        val st = runCatching {
            json.decodeFromJsonElement(PersistedState.serializer(), repairedObj)
        }.getOrNull() ?: return null
        return Repair(st, arr.size - good.size)
    }

    private class Repair(val state: PersistedState, val dropped: Int)

    /** 最后的兜底：连 repair 都失败时，只逐条解析 providers */
    private fun salvage(text: String): List<ProviderInstance> = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        val arr: JsonArray? = (root["providers"] as? JsonArray)
        arr?.mapNotNull { el ->
            runCatching {
                json.decodeFromJsonElement(
                    ProviderInstance.serializer(),
                    el as JsonObject,
                )
            }.getOrNull()
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /**
     * 结构迁移。字段新增一律给默认值，所以多数版本只需要挪 version。
     *
     * v4 → v5 例外：要把旧的「按天 token 汇总」折成账单行。
     * 不折的话，用户升级后账单清零，等于把历史抹掉了；
     * 而两份数据并存又迟早对不上（清空时清哪个？累计算谁的？），所以只折一次、之后单一数据源。
     * 判定条件是「明细为空」——迁移中途崩溃后重跑不会把同一天算两遍。
     */
    private fun migrate(s: PersistedState): PersistedState {
        var cur = s
        if (cur.version < CURRENT_VERSION) {
            if (cur.usageDaily.isNotEmpty() && cur.usageEntries.isEmpty()) {
                val legacy = cur.usageDaily.entries.mapNotNull { (day, tokens) ->
                    val at = noonOf(day) ?: return@mapNotNull null
                    UsageEntry(
                        // 负数 id：一看就知道不是正常记录，UI 可以据此区别对待
                        id = -(day.hashCode().toLong()),
                        at = at,
                        purpose = "LEGACY",
                        model = "升级前记录",
                        // 旧口径是"输入+输出总数"，无法再拆；放输出侧，且不带缓存/耗时，
                        // 于是它不会污染命中率与速度这两项统计
                        completion = tokens.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                    )
                }
                cur = cur.copy(usageEntries = legacy.sortedBy { it.at }, usageDaily = emptyMap())
            }
            // v6：老存档当"已完成"。这条必须在 version 提升之前判断 ——
            // 判据是"升级上来的老文件"，新装的用户没有 state.json，走的是默认值，不受影响
            if (cur.onboard.completedAt == 0L && cur.onboard.dismissedAt == 0L) {
                cur = cur.copy(onboard = cur.onboard.copy(completedAt = System.currentTimeMillis()))
            }
            cur = cur.copy(version = CURRENT_VERSION)
        }
        return cur
    }

    /** "yyyy-MM-dd" → 当天中午的时间戳。解析失败返回 null：宁可丢这一行，也不写错日期 */
    private fun noonOf(day: String): Long? = runCatching {
        java.time.LocalDate.parse(day)
            .atTime(12, 0)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private suspend fun persist(next: PersistedState) = withContext(Dispatchers.IO) {
        // 等 load 完成再写，避免启动瞬间用默认状态覆盖磁盘上的真实配置
        loadDone.await()
        writeLock.withLock { writeToDisk(next) }
    }

    /**
     * 事务式更新：**在锁内重新读一次当前状态**再改。
     *
     * 会话相关的写入由多个协程并发触发（保存上一段 + 切换游标 + 删除），
     * 如果各自用调用前捕获的 `cur` 去 persist，后落盘的那个会覆盖前一个的成果。
     * 返回同一个对象表示"什么都没变"，直接跳过，省一次整文件写盘。
     */
    private suspend fun update(block: (PersistedState) -> PersistedState) = withContext(Dispatchers.IO) {
        loadDone.await()
        writeLock.withLock {
            val cur = _state.value
            val next = block(cur)
            if (next !== cur) writeToDisk(next)
        }
    }

    /** 真正落盘。调用方需保证已持锁，或在 load 阶段（无并发写）调用 */
    private fun writeToDisk(next: PersistedState) {
        _state.value = next
        val tmp = File(file.parentFile, "state.json.tmp")
        val text = json.encodeToString(PersistedState.serializer(), next)
        if (runCatching { tmp.writeText(text) }.isFailure) return

        // 覆盖前先把当前文件存成备份：新文件若在下次启动解析失败，还能回滚
        runCatching { if (file.exists()) file.copyTo(backupFile, overwrite = true) }
        if (!tmp.renameTo(file)) {
            runCatching { file.writeText(text) }
            tmp.delete()
        }
    }

    fun newId(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

    suspend fun addProvider(provider: ProviderInstance) {
        val cur = _state.value
        persist(cur.copy(providers = cur.providers + provider))
    }

    suspend fun removeProvider(providerId: String) {
        val cur = _state.value
        val next = cur.providers.filterNot { it.id == providerId }
        val stillHasDefault = next.any { p -> p.models.any { it.id == cur.selectedModelId } }
        persist(
            cur.copy(
                providers = next,
                selectedModelId = if (stillHasDefault) cur.selectedModelId else "",
            )
        )
    }

    suspend fun updateProvider(provider: ProviderInstance) {
        val cur = _state.value
        persist(cur.copy(providers = cur.providers.map { if (it.id == provider.id) provider else it }))
    }

    suspend fun selectModel(modelProfileId: String) {
        val cur = _state.value
        persist(cur.copy(selectedModelId = modelProfileId))
    }

    suspend fun setWorkspace(uri: String, name: String, writable: Boolean) {
        persist(_state.value.copy(
            workspaceUri = uri,
            workspaceName = name,
            workspaceWritable = writable,
        ))
    }

    suspend fun setPermission(mode: PermissionMode) {
        persist(_state.value.copy(permission = mode.name.lowercase()))
    }

    suspend fun setTheme(mode: String) {
        persist(_state.value.copy(theme = mode))
    }

    suspend fun setShellEnabled(on: Boolean) {
        persist(_state.value.copy(shellEnabled = on))
    }

    /** 当前对话启用的技能 id 列表 */
    suspend fun setActiveSkills(ids: List<String>) {
        persist(_state.value.copy(activeSkills = ids.distinct()))
    }

    /** 停用/删除的内置技能 id。删除时要连 activeSkills 一起清掉，否则会残留一个空开关 */
    suspend fun setDisabledSkills(ids: List<String>) {
        val clean = ids.distinct()
        persist(
            _state.value.copy(
                disabledSkills = clean,
                activeSkills = _state.value.activeSkills.filter { it !in clean },
            )
        )
    }

    // ─────────── 新手引导 ───────────

    /** 用户点了「跳过」。永久生效，不再出现任何一张引导卡 */
    suspend fun dismissOnboard() = update { cur ->
        cur.copy(onboard = cur.onboard.copy(dismissedAt = System.currentTimeMillis()))
    }

    /**
     * 发出第一条消息 → 引导视为完成。
     * 重复调用无副作用（只在第一次写时间戳），否则每发一条都要写盘。
     */
    suspend fun completeOnboard() = update { cur ->
        if (cur.onboard.completedAt > 0L) cur
        else cur.copy(onboard = cur.onboard.copy(completedAt = System.currentTimeMillis()))
    }

    /** 「可以撤销」那条一次性提示已经给过 */
    suspend fun markUndoHintShown() = update { cur ->
        if (cur.onboard.undoHintShown) cur
        else cur.copy(onboard = cur.onboard.copy(undoHintShown = true))
    }

    /**
     * 重看引导：把"跳过/完成"清零，等于没开始过。
     * 只清这两个时间戳，模型、目录、账单都不动。
     */
    suspend fun resetOnboard() = update { cur ->
        cur.copy(onboard = cur.onboard.copy(dismissedAt = 0, completedAt = 0))
    }

    /** 一轮 agent 开始/结束时打标记。异常退出会留下 true，下次启动据此提示"上次被中断" */
    suspend fun setRunActive(on: Boolean) {
        if (_state.value.runActive == on) return
        update { cur -> cur.copy(runActive = on) }
    }

    suspend fun clearUsage() {
        persist(_state.value.copy(usageDaily = emptyMap(), usageEntries = emptyList()))
    }

    /**
     * 记一条账单。
     *
     * v5 起**只写 usageEntries**，不再写 usageDaily ——
     * 两份数据并存就必然有一天对不上（谁是权威？清空时清哪个？），
     * 所以直接把明细作为唯一数据源，旧的天级汇总在 migrate 里折成 LEGACY 行。
     *
     * 走 [update] 而不是 persist：账单写入与消息保存是并发协程，
     * 各自拿旧状态落盘会互相覆盖。
     */
    suspend fun recordUsage(entry: UsageEntry) = update { cur ->
        cur.copy(usageEntries = (cur.usageEntries + entry).takeLast(MAX_USAGE_ENTRIES))
    }

    /** 记住"这个工具以后别再问我了"。同名重复添加无副作用 */
    suspend fun allowTool(name: String) = update { cur ->
        if (name.isBlank() || name in cur.toolAllowList) cur
        else cur.copy(toolAllowList = cur.toolAllowList + name)
    }

    suspend fun revokeTool(name: String) = update { cur ->
        if (name !in cur.toolAllowList) cur
        else cur.copy(toolAllowList = cur.toolAllowList - name)
    }

    suspend fun clearAllowList() = update { cur ->
        if (cur.toolAllowList.isEmpty()) cur else cur.copy(toolAllowList = emptyList())
    }

    /** 加载报告消费一次后清空，避免重复弹提示 */
    fun consumeLoadReport(): String? {
        val r = loadReport
        loadReport = null
        return r
    }

    // ─────────── 会话 ───────────

    /**
     * 写入/更新一段会话，默认同时把它设为当前会话。
     *
     * 走 [update] 而不是拿调用方捕获的旧状态：保存会话和切换会话是两个并发协程，
     * 如果各自基于自己那份 `cur` 落盘，后写的那个会把前一个的改动整体覆盖掉
     * （表现为"刚聊完的会话历史没存上"）。
     *
     * [makeCurrent] 只在真正开始聊这段会话时为 true；切走时保存上一段要把游标留给新会话。
     */
    suspend fun saveSession(session: Session, makeCurrent: Boolean = true) = update { cur ->
        val others = cur.sessions.filterNot { it.id == session.id }
        cur.copy(
            sessions = (others + session).sortedByRecent(),
            currentSessionId = if (makeCurrent) session.id else cur.currentSessionId,
        )
    }

    suspend fun setCurrentSession(id: String) = update { cur ->
        if (cur.currentSessionId == id) cur else cur.copy(currentSessionId = id)
    }

    /** 删掉一段会话；删的正好是当前会话时把游标一起清空 */
    suspend fun deleteSession(id: String) = update { cur ->
        if (cur.sessions.none { it.id == id }) cur
        else cur.copy(
            sessions = cur.sessions.filterNot { it.id == id },
            currentSessionId = if (cur.currentSessionId == id) "" else cur.currentSessionId,
        )
    }

    // 派生查询
    fun allModels(): List<Pair<ProviderInstance, ModelProfile>> =
        _state.value.providers
            .filter { it.enabled }
            .flatMap { p -> p.models.filter { it.enabled }.map { p to it } }

    fun chatModels(): List<ModelProfile> = allModels().map { it.second }

    fun selected(): Pair<ProviderInstance, ModelProfile>? {
        val id = _state.value.selectedModelId
        return allModels().firstOrNull { it.second.id == id } ?: allModels().firstOrNull()
    }

    fun providerOf(modelProfileId: String): ProviderInstance? =
        _state.value.providers.firstOrNull { p -> p.models.any { it.id == modelProfileId } }
}
