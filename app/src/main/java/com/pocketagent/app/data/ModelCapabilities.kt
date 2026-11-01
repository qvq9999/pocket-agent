package com.pocketagent.app.data

/**
 * 模型能力表：用**模型名分词匹配**声明一个模型支持哪些输入与能力。
 *
 * 语义与 rikkahub-agent-pure 的 `ai/registry/ModelRegistry.kt` 同一套：
 *  - 把 modelId 拆成「字母段 / 数字段 / 单个符号」的 token 序列；
 *  - 规则是一串 token（支持 `a|b` 表示任选其一、也支持正则），按**有序子序列**匹配；
 *  - 命中越多 token 分越高，取最高分；同分则合并（并集）。
 *
 * 为什么用表而不是"自动识别"：OpenAI 兼容的 `/models` 只返回 id/object/owned_by，
 * 没有能力字段，除了 OpenRouter / Gemini 这类自带元数据的端点，**接口层面无法识别**。
 * 所以这里的每一条都是"声明"，不是"探测"——界面必须如实标注来源，不能写成"已检测"。
 *
 * 匹配不到就是不认识：能力留空（未知），不要默认成"支持"或"不支持"。
 */
object ModelCapabilities {

    /** 一条规则。能力缺省 false；[image] false 表示"表里声明它只吃文本"，与"不认识"不同 */
    data class Rule(
        val name: String,
        val matcher: Matcher,
        val image: Boolean = false,
        val tools: Boolean = false,
        val reasoning: Boolean = false,
    )

    /** 查询结果。[matched] 为 null 表示模型表里没有这个模型，一切未知 */
    data class Result(
        val image: Boolean?,
        val tools: Boolean?,
        val reasoning: Boolean?,
        val matched: List<String>,
    ) {
        val known: Boolean get() = matched.isNotEmpty()
    }

    private val UNKNOWN = Result(null, null, null, emptyList())

    fun of(modelId: String): Result {
        if (modelId.isBlank()) return UNKNOWN
        val tokens = tokenize(modelId)
        var best: Int? = null
        val hits = mutableListOf<Rule>()
        for (r in RULES) {
            val score = r.matcher.score(modelId, tokens) ?: continue
            when {
                best == null || score > best!! -> {
                    best = score
                    hits.clear()
                    hits.add(r)
                }
                score == best -> hits.add(r)
            }
        }
        if (hits.isEmpty()) return UNKNOWN
        return Result(
            // 同分合并：任一条声明支持就算支持（与 rikkahub 的 resolveModalities 一致）
            image = hits.any { it.image },
            tools = hits.any { it.tools },
            reasoning = hits.any { it.reasoning },
            matched = hits.map { it.name },
        )
    }
}

// ───────────── token 匹配（移植自 rikkahub 的 ModelDsl） ─────────────

sealed interface Matcher {
    fun score(modelId: String, tokens: List<String>): Int?
}

/** 有序子序列匹配：token 按顺序出现即可，中间允许夹杂别的 token。返回命中条数 */
private class Tokens(private val specs: List<Spec>) : Matcher {
    override fun score(modelId: String, tokens: List<String>): Int? {
        if (specs.isEmpty()) return null
        var i = 0
        for (t in tokens) {
            if (specs[i].matches(t)) {
                i += 1
                if (i == specs.size) return specs.size
            }
        }
        return null
    }
}

/** 反向匹配：这串 token 出现则整条规则不成立 */
private class NotTokens(specs: List<Spec>) : Matcher {
    private val inner = Tokens(specs)
    override fun score(modelId: String, tokens: List<String>): Int? =
        if (inner.score(modelId, tokens) == null) 0 else null
}

private class AndAll(private val all: List<Matcher>) : Matcher {
    override fun score(modelId: String, tokens: List<String>): Int? {
        var total = 0
        for (m in all) total += m.score(modelId, tokens) ?: return null
        return total
    }
}

private class ExactId(private val id: String) : Matcher {
    override fun score(modelId: String, tokens: List<String>): Int? =
        if (modelId.equals(id, true)) 1000 + tokens.size else null
}

sealed interface Spec {
    fun matches(token: String): Boolean
}

/** `"4|4o"` 这种写法：任选其一 */
private class Alt(private val options: Set<String>) : Spec {
    override fun matches(token: String): Boolean = options.contains(token)
}

private class RegexSpec(private val re: Regex) : Spec {
    override fun matches(token: String): Boolean = re.matches(token)
}

/** 分词：字母段、数字段、其余单字符各成一个 token（与 rikkahub 完全一致） */
private fun tokenize(modelId: String): List<String> {
    val out = mutableListOf<String>()
    val s = modelId.lowercase()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c.isLetter() -> {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                out.add(s.substring(st, i))
            }
            c.isDigit() -> {
                val st = i
                while (i < s.length && s[i].isDigit()) i++
                out.add(s.substring(st, i))
            }
            else -> {
                out.add(c.toString())
                i++
            }
        }
    }
    return out
}

private fun rule(
    name: String,
    vararg specs: String,
    image: Boolean = false,
    tools: Boolean = true,
    reasoning: Boolean = false,
): ModelCapabilities.Rule =
    ModelCapabilities.Rule(name, Tokens(specs.map { s ->
        Alt(s.split('|').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet())
    }), image = image, tools = tools, reasoning = reasoning)

/**
 * 规则表。顺序无关（按分数取胜），只影响同分时的合并。
 *
 * 取值原则：只写各家公开文档明确写过的东西；拿不准的一律不写（留成未知）。
 * 与 rikkahub 的表对齐，但只保留我们目录里可能出现的厂商。
 */
private val RULES: List<ModelCapabilities.Rule> = listOf(
    // ── OpenAI ──
    rule("GPT-4o", "gpt", "4", "o", image = true),
    rule("GPT-4.1", "gpt", "4", "1", image = true),
    rule("GPT-4 Turbo", "gpt", "4", "turbo", image = true),
    rule("GPT-4 Vision", "gpt", "4", "vision", image = true),
    rule("GPT-4", "gpt", "4"),
    rule("GPT-5", "gpt", "5", image = true, reasoning = true),
    rule("GPT-6", "gpt", "6", image = true, reasoning = true),
    ModelCapabilities.Rule(
        name = "o 系列",
        matcher = AndAll(listOf(
            Tokens(listOf(Alt(setOf("o")), RegexSpec(Regex("^\\d+$")))),
        )),
        image = true, tools = true, reasoning = true,
    ),
    rule("GPT-3.5", "gpt", "3", tools = true),
    // ── Anthropic ──
    rule("Claude 3 Haiku", "claude", "3", "haiku", image = true),
    rule("Claude 3.5 Sonnet", "claude", "3", "5", image = true, reasoning = true),
    rule("Claude 3.7 Sonnet", "claude", "3", "7", image = true, reasoning = true),
    rule("Claude 4", "claude", "4", image = true, reasoning = true),
    rule("Claude", "claude", image = true, reasoning = true),
    // ── Google ──
    rule("Gemini 1.x", "gemini", "1", image = true),
    rule("Gemini 2.0 Flash", "gemini", "2", "0", image = true),
    rule("Gemini 2.5", "gemini", "2", "5", image = true, reasoning = true),
    rule("Gemini 3", "gemini", "3", image = true, reasoning = true),
    rule("Gemini", "gemini", image = true),
    // ── DeepSeek（官方 chat/reasoner 是两套：chat 不吃图、reasoner 无强度参数）──
    rule("DeepSeek Chat", "deepseek", "chat", tools = true),
    rule("DeepSeek V3", "deepseek", "v", "3", tools = true),
    rule("DeepSeek Reasoner", "deepseek", "reasoner", reasoning = true),
    rule("DeepSeek R1", "deepseek", "r", "1", reasoning = true),
    rule("DeepSeek 多模态", "deepseek", "flash", image = true, reasoning = true),
    rule("DeepSeek", "deepseek", tools = true),
    // ── 通义千问 ──
    rule("Qwen VL", "qwen", "vl", image = true),
    rule("Qwen VL", "qwen", "2", "5", "vl", image = true),
    rule("Qwen 3.x", "qwen", "3", "5", image = true, reasoning = true),
    rule("Qwen 3.x", "qwen", "3", "6", image = true, reasoning = true),
    rule("Qwen 3.x", "qwen", "3", "7", image = true, reasoning = true),
    rule("Qwen Max", "qwen", "max", tools = true),
    rule("Qwen Plus", "qwen", "plus", tools = true),
    rule("Qwen Turbo", "qwen", "turbo", tools = true),
    rule("Qwen", "qwen", tools = true),
    // ── 智谱 GLM ──
    rule("GLM-4V", "glm", "4", "v", image = true),
    rule("GLM-4.5V", "glm", "4", "5", "v", image = true),
    rule("GLM-4.5", "glm", "4", "5", reasoning = true),
    rule("GLM-4.6", "glm", "4", "6", reasoning = true),
    rule("GLM", "glm", tools = true),
    // ── 月之暗面 ──
    rule("Kimi K2", "kimi", "k", "2", reasoning = true),
    rule("Kimi VL", "kimi", "vl", image = true),
    rule("Moonshot Vision", "moonshot", "vision", image = true),
    rule("Kimi", "kimi", tools = true),
    rule("Moonshot", "moonshot", tools = true),
    // ── 豆包 / 火山 ──
    rule("Doubao 1.6+", "doubao", "1", "6", image = true, reasoning = true),
    rule("Doubao 1.8+", "doubao", "1", "8", image = true, reasoning = true),
    rule("Doubao 2.x", "doubao", "2", image = true, reasoning = true),
    rule("Doubao", "doubao", tools = true),
    // ── MiniMax ──
    rule("MiniMax VL", "minimax", "vl", image = true),
    rule("MiniMax M1", "minimax", "m", "1", reasoning = true),
    rule("MiniMax", "minimax", tools = true),
    rule("abab", "abab", tools = true),
    // ── 腾讯混元 ──
    rule("混元 Vision", "hunyuan", "vision", image = true),
    rule("混元 T1", "hunyuan", "t", "1", reasoning = true),
    rule("混元", "hunyuan", tools = true),
    // ── 百度文心 ──
    rule("文心 VL", "ernie", "vl", image = true),
    rule("文心 4.5 VL", "ernie", "4", "5", "vl", image = true),
    rule("文心", "ernie", tools = true),
    // ── 阶跃 ──
    rule("Step 1V", "step", "1", "v", image = true),
    rule("Step R1", "step", "r", "1", reasoning = true),
    rule("Step", "step", tools = true),
    // ── 零一万物 ──
    rule("Yi VL", "yi", "vl", image = true),
    rule("Yi", "yi", tools = true),
    // ── 小米 MiMo ──
    rule("MiMo", "mimo", "v", image = true, reasoning = true),
    rule("MiMo", "mimo", image = true, reasoning = true),
    // ── xAI / Mistral / Meta / 本地 ──
    rule("Grok 4", "grok", "4", image = true, reasoning = true),
    rule("Grok Vision", "grok", "vision", image = true),
    rule("Grok", "grok", tools = true),
    rule("Pixtral", "pixtral", image = true),
    rule("Mistral", "mistral", tools = true),
    rule("Llama Vision", "llama", "3", "2", "vision", image = true),
    rule("LLaVA", "llava", image = true),
    rule("InternVL", "internvl", image = true),
    rule("Gemma 3", "gemma", "3", image = true),
    rule("Llama", "llama", tools = true),
)

// ───────────── 展示状态 ─────────────

/**
 * 「图片」这项能力的展示状态。
 *
 * 手动设置优先于模型表：用户说"我确认它收图"比任何表都可信。
 * 表里查不到就是 [UNKNOWN] —— 界面上必须写成"未知"，不许写成"支持"。
 */
enum class VisionDisplay {
    /** 表里声明支持图片输入 */
    DECLARED_SUPPORTED,

    /** 表里声明只支持文本 */
    DECLARED_TEXT_ONLY,

    /** 表里没有这个模型，未知 */
    UNKNOWN,

    /** 用户手动设为开 */
    FORCED_ON,

    /** 用户手动设为关 */
    FORCED_OFF,
}

/** 结合模型表与手动设置，得出「图片」的展示状态 */
fun ModelProfile.visionDisplay(): VisionDisplay {
    if (visionTouched) return if (vision) VisionDisplay.FORCED_ON else VisionDisplay.FORCED_OFF
    return when (ModelCapabilities.of(modelId).image) {
        true -> VisionDisplay.DECLARED_SUPPORTED
        false -> VisionDisplay.DECLARED_TEXT_ONLY
        null -> VisionDisplay.UNKNOWN
    }
}

/** 模型表命中的规则名，用于在界面上交代"依据是什么" */
fun ModelProfile.capabilitySource(): String =
    ModelCapabilities.of(modelId).matched.joinToString(" + ").ifBlank { "未收录" }

// ───────────── 上下文窗口 ─────────────
//
// 为什么需要这一组：OpenAI 兼容接口的 /models 只返回 id/object/owned_by，**拿不到**窗口大小
// （这个项目里曾经写死 32k，于是所有模型都被当成 32k 用，长任务必然在半路丢手册）。
// 现在的取值顺序是：用户手填 > 按模型名声明的表 > 保守默认值。

/** 手填与声明的上限：再大没有任何厂商支持，纯属手滑 */
const val MAX_CONTEXT_WINDOW = 1_000_000

/**
 * 查不到任何信息时的默认值。
 *
 * 取 200k 是产品决定：现在的模型（Claude / GPT-4o 之后 / o 系列 / GLM / Kimi 等）
 * 主流窗口都在 128k～256k，31k 的老默认值会让长任务在中途被迫压缩，
 * 用户感受到的是"聊到一半它把前面的忘了"。
 *
 * 代价要说清楚：窗口报大了，网关会直接 400。所以这条默认值能成立，
 * 靠的是 `AppViewModel.agentTurn` 里"400 → 缩短历史重试"的自愈 —— 真的撞上
 * 小窗口模型时会退回到能装下的长度，而不是把这一轮打死。
 * 已知窗口的模型优先走 [declaredContextWindow]，不会用到这个值。
 */
const val DEFAULT_CONTEXT_WINDOW = 200_000

/**
 * 按模型名声明上下文窗口（token）。取值只写各家公开文档明确写过的；
 * 拿不准返回 null，交给默认值 —— 宁可保守，也不要给出一个假的大窗口让请求必炸。
 *
 * 注意：这仍然是**声明**，不是探测。网关可能把窗口改小，所以真正发请求时
 * 还要有 "400 → 缩短历史重试" 的自愈（AppViewModel.agentTurn 里那段）。
 */
fun declaredContextWindow(modelId: String): Int? {
    if (modelId.isBlank()) return null
    val s = modelId.lowercase()
    // o1/o3/o4 系列：名字就是 "o3-mini" 这种，前面可能是 "openai/"
    val oSeries = Regex("(^|[/\\-_:])o[1-9]").containsMatchIn(s)
    return when {
        s.contains("gemini") -> 1_000_000
        s.contains("claude") -> 200_000
        s.contains("gpt-5") || s.contains("gpt5") || s.contains("gpt-6") -> 400_000
        s.contains("gpt-4.1") -> 1_000_000
        s.contains("gpt-4o") || s.contains("gpt-4-turbo") -> 128_000
        oSeries -> 200_000
        s.contains("deepseek") -> 128_000
        s.contains("qwen") -> 131_072
        s.contains("kimi") || s.contains("moonshot") -> 256_000
        s.contains("glm") -> 200_000
        s.contains("doubao") -> 256_000
        s.contains("grok") -> 256_000
        s.contains("llama") || s.contains("mistral") || s.contains("mixtral") -> 128_000
        s.contains("gpt-4") -> 128_000
        else -> null
    }
}

/** 实际生效的窗口：手动设置 > 模型表声明 > 默认 200k。所有预算都读这一个函数 */
fun ModelProfile.effectiveContextWindow(): Int =
    (contextWindow?.takeIf { it in 4_000..MAX_CONTEXT_WINDOW }
        ?: declaredContextWindow(modelId)
        ?: DEFAULT_CONTEXT_WINDOW)
        .coerceIn(4_000, MAX_CONTEXT_WINDOW)
