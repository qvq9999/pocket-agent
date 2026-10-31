package com.pocketagent.app.net

import com.pocketagent.app.data.ThinkingLevel
import com.pocketagent.app.data.ThinkingStyle

/**
 * 思考档位规则：按模型 ID 推断厂商参数形态。
 *
 * 为什么不能做成一张全局表：各家参数根本不是一回事 ——
 * OpenAI 四档枚举、Agnes 七档枚举、小米 thinking.type 布尔、Gemini 两档、Anthropic 是 token 预算。
 * UI 只认 levels 数组，数组几个就显示几个。
 */
object ThinkingRules {

    data class Spec(val style: ThinkingStyle, val levels: List<ThinkingLevel>, val defaultId: String)

    private val NONE = Spec(ThinkingStyle.NONE, emptyList(), "")

    // Agnes 3.0 Flash：reasoning_effort 七档
    private val AGNES_30 = Spec(
        ThinkingStyle.EFFORT,
        listOf(
            ThinkingLevel("none", "关", "none"),
            ThinkingLevel("minimal", "最小", "minimal"),
            ThinkingLevel("low", "低", "low"),
            ThinkingLevel("medium", "中", "medium"),
            ThinkingLevel("high", "高", "high"),
            ThinkingLevel("xhigh", "极高", "xhigh"),
            ThinkingLevel("max", "最大", "max"),
        ),
        "medium",
    )

    // GPT-5：reasoning_effort 四档
    private val GPT5 = Spec(
        ThinkingStyle.EFFORT,
        listOf(
            ThinkingLevel("minimal", "最小", "minimal"),
            ThinkingLevel("low", "低", "low"),
            ThinkingLevel("medium", "中", "medium"),
            ThinkingLevel("high", "高", "high"),
        ),
        "medium",
    )

    // Gemini 3：thinking_level 两档，没有"关"
    private val GEMINI3 = Spec(
        ThinkingStyle.TIER,
        listOf(
            ThinkingLevel("low", "低", "low"),
            ThinkingLevel("high", "高", "high"),
        ),
        "low",
    )

    private val BOOL_ON = Spec(
        ThinkingStyle.BOOL,
        listOf(
            ThinkingLevel("off", "关", "disabled"),
            ThinkingLevel("on", "开", "enabled"),
        ),
        "on",
    )

    private val CLAUDE_BUDGET = Spec(
        ThinkingStyle.BUDGET,
        listOf(
            ThinkingLevel("2k", "轻", "2048"),
            ThinkingLevel("8k", "中", "8192"),
            ThinkingLevel("16k", "重", "16384"),
        ),
        "8k",
    )

    fun forModel(modelId: String): Spec {
        val id = modelId.lowercase()
        return when {
            id.contains("agnes-3") -> AGNES_30
            id.contains("agnes-2.5") -> BOOL_ON
            id.startsWith("gpt-5") -> GPT5
            id.contains("mimo") -> BOOL_ON
            id.contains("gemini-3") -> GEMINI3
            id.contains("claude") -> CLAUDE_BUDGET
            else -> NONE
        }
    }

    fun styleLabel(style: ThinkingStyle): String = when (style) {
        ThinkingStyle.EFFORT -> "推理强度"
        ThinkingStyle.BOOL -> "思考开关"
        ThinkingStyle.TIER -> "思考档位"
        ThinkingStyle.BUDGET -> "思考预算"
        ThinkingStyle.NONE -> "不支持"
    }
}
