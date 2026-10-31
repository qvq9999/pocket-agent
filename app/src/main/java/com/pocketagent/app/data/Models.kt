package com.pocketagent.app.data

import com.pocketagent.app.agent.ToolCall
import com.pocketagent.app.agent.ToolInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ───────────── 厂商目录（只读，来自 assets/providers.json） ─────────────

@Serializable
data class AltEndpoint(
    val label: String = "",
    val baseUrl: String = "",
    val keyHint: String = "",
    val note: String = "",
)

@Serializable
data class ProviderDef(
    val id: String,
    val name: String,
    val icon: String = "",
    val fallbackText: String = "",
    val fallbackColor: String = "#5F6368",
    val region: String = "cn",
    val baseUrl: String,
    val modelsPath: String = "/models",
    val docsUrl: String = "",
    val requiresEdit: Boolean = false,
    val altEndpoints: List<AltEndpoint> = emptyList(),
)

@Serializable
data class CatalogFile(
    val version: Int = 1,
    val updated: String = "",
    val providers: List<ProviderDef> = emptyList(),
)

// ───────────── 思考档位 ─────────────

@Serializable
enum class ThinkingStyle {
    /** OpenAI 风格枚举：reasoning_effort */
    @SerialName("effort") EFFORT,

    /** 布尔开关：thinking.type = enabled/disabled */
    @SerialName("bool") BOOL,

    /** 分档枚举：thinking_level = low/high */
    @SerialName("tier") TIER,

    /** token 预算：thinking.budget_tokens */
    @SerialName("budget") BUDGET,

    /** 该模型不支持 */
    @SerialName("none") NONE,
}

@Serializable
data class ThinkingLevel(
    val id: String,
    val label: String,
    /** 真正发给厂商的值，如 "high" / "enabled" / "8192" */
    val param: String,
)

// ───────────── 已保存的模型配置 ─────────────

@Serializable
data class ModelProfile(
    val id: String,
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val contextWindow: Int? = null,
    val toolCalling: Boolean = true,
    val vision: Boolean = false,
    /**
     * 用户是否**手动**设过视觉。没设过（false）就按模型名自动判断 ——
     * 视觉模型开箱即用，不用先去设置里找个开关；手动设过就以手动为准。
     */
    val visionTouched: Boolean = false,
    val thinkingStyle: ThinkingStyle = ThinkingStyle.NONE,
    val thinkingLevels: List<ThinkingLevel> = emptyList(),
    val thinkingDefault: String = "",
    val priceIn: Double? = null,
    val priceOut: Double? = null,
) {
    /**
     * 实际生效的视觉能力：**默认开启**，只手动态关了才关。
     *
     * 为什么默认开而不是默认关：现在的模型绝大多数都能收图，默认关的结果是
     * "原生视觉模型也得先去设置里手动打开"（用户实测就是这么被拦住的）。
     * 反过来，默认开最坏的结果是：把图发给一个真不支持的模型 → 服务端 400 →
     * 我们原样报出错误并提示改成「关」，代价只有一次失败的请求。
     *
     * 所以所有"能不能发图"的判断都读这个，不要直接读 [vision]。
     */
    val visionEffective: Boolean
        get() = if (visionTouched) vision else true
}

/** 非对话模型（图像/视频/音频），不进切换列表 */
@Serializable
data class MediaModel(
    val modelId: String,
    val modality: String,
)

@Serializable
data class ProviderInstance(
    val id: String,
    val catalogId: String,
    val displayName: String,
    val baseUrl: String,
    /** Keystore 加密后的密文（base64），磁盘上永不出现明文 key */
    val keyCipher: String,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val models: List<ModelProfile> = emptyList(),
    val mediaModels: List<MediaModel> = emptyList(),
)

/** 单次回复的资源消耗。厂商返回的 usage 优先，缺失时由本地估算并标记 estimated */
@Serializable
data class Usage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    /**
     * 厂商是否**真的**下发了缓存字段。
     *
     * 必须区分"上报了 0"和"压根没上报"：后者算出 0% 命中率是假信息，
     * 用户会以为是缓存失效去调无关参数。账单里所有缓存统计都以此为准。
     */
    val cacheReported: Boolean = false,
    val seconds: Double = 0.0,
    val estimated: Boolean = false,
) {
    val totalTokens: Int get() = promptTokens + completionTokens

    /** 缓存命中率：只有厂商下发了 cached_tokens 才有意义，否则返回 null（不编造） */
    val cacheRate: Double?
        get() = if (promptTokens > 0 && cachedTokens > 0) cachedTokens.toDouble() / promptTokens else null

    /** 输出速度 tok/s：按首字到结束的时长算 */
    val speed: Double?
        get() = if (seconds >= 0.15 && completionTokens > 0) completionTokens / seconds else null
}

/** 落盘的一条消息。字段与运行时 ChatMsg 一一对应，颜色用 Long 存，避免解析颜色字符串 */
@Serializable
data class StoredMsg(
    val id: Long,
    val role: String,
    val text: String = "",
    val apiText: String = "",
    val refs: List<String> = emptyList(),
    val usage: Usage? = null,
    val modelLabel: String = "",
    val vendorSlug: String = "",
    val vendorFallback: String = "",
    val vendorColor: Long = 0xFF5F6368,
    val failed: Boolean = false,
    val detail: String = "",
    /** 这条 assistant 消息发起的工具调用（重建请求时原样回放，顺序不能丢） */
    val calls: List<ToolCall> = emptyList(),
    /** role = "tool" 时这一条对应的工具执行结果 */
    val tool: ToolInfo? = null,
    /**
     * role = "summary" 时：这条摘要覆盖到哪条消息（按 id）。
     * 小于等于这个 id 的原始消息不再进请求体，但**仍然留在会话里**，用户往上翻还能看到全文。
     */
    val coveredUpTo: Long = 0,
    /** 模型的思考/推理内容。只用于展示，不进请求历史 */
    val reasoning: String = "",

    /** 落定时间（epoch ms）；0 = 旧存档没有这个字段，界面不显示时间 */
    val at: Long = 0,

    /** 这一轮的墙钟耗时（毫秒），只写在每轮最后一条上 */
    val elapsedMs: Long = 0,
)

/** 一段会话。标题取首条用户消息，抽屉列表按 updatedAt 倒序 */
@Serializable
data class Session(
    val id: String,
    val title: String = "",
    val createdAt: Long = 0L,
    /** 最近一次**收发消息**的时间，不是最近一次被打开/保存的时间 */
    val updatedAt: Long = 0L,
    /**
     * 这段会话绑定的工作区。空 = 普通会话。
     *
     * 一旦绑定就不再改写（见 AppViewModel.snapshotSession）：否则用户换一个工作区，
     * 所有旧会话都会被重新贴上新的目录名，历史归属就乱了。
     */
    val workspaceUri: String = "",
    val workspaceName: String = "",
    val messages: List<StoredMsg> = emptyList(),
) {
    /** 是否属于工作区会话。派生属性，不参与序列化 */
    val inWorkspace: Boolean get() = workspaceUri.isNotBlank()
}

/** 抽屉里的两个会话分类。顺序即展示顺序 */
enum class SessionGroup(val label: String) {
    /** 没绑工作区的普通对话 */
    PLAIN("普通会话"),

    /** 绑定了工作区的会话 */
    WORKSPACE("工作区"),
}

fun Session.group(): SessionGroup = if (inWorkspace) SessionGroup.WORKSPACE else SessionGroup.PLAIN

/**
 * 按类型分组，**各组内部独立**按「最近收发消息」倒序。
 *
 * 空组不返回：抽屉里不该出现一个只有标题、里面什么都没有的分组头。
 */
fun List<Session>.groupedByRecent(): List<Pair<SessionGroup, List<Session>>> =
    SessionGroup.entries
        .map { g -> g to filter { it.group() == g }.sortedByRecent() }
        .filter { (_, items) -> items.isNotEmpty() }

/**
 * 会话排序：最近收发过消息的排最前。
 *
 * 只比 [Session.updatedAt]，后两级纯粹是兜底 —— 同一毫秒保存的两段会话如果不给
 * 确定次序，列表顺序就会随每次重排漂移，看起来像"自己乱跳"。
 */
val SessionOrder: Comparator<Session> = compareByDescending<Session> { it.updatedAt }
    .thenByDescending { it.createdAt }
    .thenByDescending { it.id }

/** 抽屉与会话存储共用的排序入口，避免两处各写一份规则而对不上 */
fun List<Session>.sortedByRecent(): List<Session> = sortedWith(SessionOrder)

@Serializable
data class PersistedState(
    val version: Int = 1,
    val providers: List<ProviderInstance> = emptyList(),
    val selectedModelId: String = "",    val workspaceUri: String = "",
    val workspaceName: String = "",
    val workspaceWritable: Boolean = false,
    val permission: String = "confirm",
    val theme: String = "system",
    /**
     * 是否启用可选终端能力（proot + Ubuntu rootfs）。默认关：它要下载并解压 rootfs，
     * 属于重型能力。关掉开关只是不再把 run_shell 发给模型，已装环境保留，
     * 真正的删除由扩展页的「删除 Linux 环境」负责。
     */
    val shellEnabled: Boolean = false,
    /**
     * 是否有正在跑的一轮 agent。进程被杀时来不及清，下次启动就能据此判定"上次被打断了"。
     * 这不是"是否在跑"的实时状态（内存里那条才是），只是给开机自检用的一颗脏标记。
     */
    val runActive: Boolean = false,
    /**
     * 旧版「按天 token 汇总」。
     *
     * v5 起**不再写入**：账单明细（[usageEntries]）是唯一数据源。
     * 这个字段只为兼容旧存档而保留，迁移时它的内容会被折成 purpose=LEGACY 的账单行。
     */
    val usageDaily: Map<String, Long> = emptyMap(),
    /** 用量账单明细（一次模型往返一行）。唯一的数据源，只保留最近若干条 */
    val usageEntries: List<UsageEntry> = emptyList(),
    /**
     * 用户点过「永久允许」的工具名。
     * 跨会话、跨重启生效，可在设置里撤销 —— 否则用户每次重启都要重新点一遍同意。
     */
    val toolAllowList: List<String> = emptyList(),
    /** 历史会话（不含空会话）。按 updatedAt 倒序存放 */
    val sessions: List<Session> = emptyList(),
    /** 当前正在编辑的会话 id；空表示"还没落盘的新会话" */
    val currentSessionId: String = "",
    /**
     * 当前对话启用的内置技能 id。空 = 不启用任何技能。
     *
     * 只存 id 不存内容：技能本体在 assets 里，enable 只是一个开关。
     * 新开会话会清空 —— 技能是"这段对话的工作方式"，不该悄悄带到下一段。
     */
    val activeSkills: List<String> = emptyList(),
    /**
     * 被用户停用或删除的内置技能 id。
     *
     * 内置资源在 assets 里删不掉，"删除"只能删解压出来的副本并记在这里；
     * 重装 = 把它从这份名单里移出去，再重新解压。
     */
    val disabledSkills: List<String> = emptyList(),
    /** 新手引导的三个时间戳。见 [OnboardState] */
    val onboard: OnboardState = OnboardState(),
)

/**
 * 新手引导的状态。
 *
 * 刻意**不存"走到第几步"**：该显示哪一步完全由现状推导
 * （有没有模型、有没有目录、发过消息没有）。存下来的 step 会和现实脱节 ——
 * 用户从引导跳去加模型、回来却因为 step 没更新又看到"先添加模型"，
 * 那种"界面不相信自己刚做过的事"是最伤体验的。
 *
 * 这里只存三个**不可推导**的事实：跳过、完成、那条撤销提示给过没有。
 */
@Serializable
data class OnboardState(
    /** 文案版本。将来改文案想再引导一次，把它 +1（已完成的用户仍然静默） */
    val version: Int = 1,
    /** 点过「跳过」的时间。永久生效：半路打断的教程比没有更烦 */
    val dismissedAt: Long = 0,
    /** 发出第一条消息的时间。有它就不再出现引导 */
    val completedAt: Long = 0,
    /** 「不满意可以撤销」那条提示是否给过（一生只给一次） */
    val undoHintShown: Boolean = false,
)

/**
 * 一次模型往返的账单行。
 *
 * 粒度刻意选「一次往返」而不是「一轮对话」：一轮对话可能调多次模型
 * （工具循环、上下文压缩、翻译），按轮记录会把它们糊成一个数，
 * 于是"这次花了多少"永远说不清，也没法知道钱花在哪个环节。
 */
@Serializable
data class UsageEntry(
    val id: Long = 0,
    val at: Long = 0,
    /** MAIN / TOOL_LOOP / COMPACTION / TRANSLATION / TITLE */
    val purpose: String = "MAIN",
    /** 厂商显示名 */
    val provider: String = "",
    val model: String = "",
    val prompt: Int = 0,
    val completion: Int = 0,
    val cached: Int = 0,
    /** 厂商是否真的上报了缓存字段。false 时 cached=0 不能读成"未命中" */
    val cacheReported: Boolean = false,
    /** 厂商没给 usage、由本地按字符估算 */
    val estimated: Boolean = false,
    /** 本次往返的耗时（毫秒），用于算真实输出速度 */
    val latencyMs: Long = 0,
)

/** 访问权限三档 */
enum class PermissionMode(val label: String) {
    READONLY("只读"),
    CONFIRM("需确认"),
    ALLOW("全部允许"),
}

// ───────────── 运行时（不落盘的临时态） ─────────────

data class DiscoveredModel(
    val modelId: String,
    val displayName: String,
    val isChat: Boolean,
    val modality: String = "",
    val note: String = "",
    val recommended: Boolean = false,
)

sealed interface DiscoveryOutcome {
    data class Success(val models: List<DiscoveredModel>) : DiscoveryOutcome
    data class Failure(val kind: FailureKind, val message: String, val detail: String = "") : DiscoveryOutcome
}

enum class FailureKind { AUTH, NOT_FOUND, BAD_REQUEST, RATE_LIMIT, SERVER, TIMEOUT, NETWORK, TLS, EMPTY, UNKNOWN }
