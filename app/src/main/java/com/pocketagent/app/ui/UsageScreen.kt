package com.pocketagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.data.UsageStats
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * 用量账单。
 *
 * 从设置首屏独立成页：它天然是高密度报表（三个时间窗 + 三项指标 + 趋势 + 模型占比 + 用途），
 * 摆在设置首屏会把"功能入口"顶到折叠线以下 —— 设置要当入口集合，就不能同时是报表页。
 *
 * 「清空账单」跟着它作用的数据一起搬进来：这是破坏性操作，放在数据旁边比放在设置首屏更不容易误触，
 * 也符合"点开账单才可能想清空账单"的因果顺序。
 */
@Composable
fun UsageScreen(vm: AppViewModel, onBack: () -> Unit) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    // 明细是唯一数据源，这里按条目聚合一次（条目上限 1000，聚合是微秒级）
    val bill = remember(persisted.usageEntries) { UsageStats.build(persisted.usageEntries) }
    val today = UsageStats.dayKeyOf(System.currentTimeMillis())
    val todayBucket = bill.byDay.firstOrNull { it.key == today } ?: UsageStats.Bucket()
    val last7Tokens = remember(persisted.usageEntries) {
        UsageStats.since(persisted.usageEntries, 7).sumOf { (it.prompt + it.completion).toLong() }
    }
    val dailyMap = remember(bill) { bill.byDay.associate { it.key to it.total } }

    Box(Modifier.fillMaxSize().background(pal().pageBg)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "用量账单", onBack = onBack)

            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

                item {
                    SectionLabel(
                        "用量账单",
                        if (bill.total.calls > 0) "累计 ${UsageStats.compact(bill.total.total)} tok" else null,
                    )
                }

                // 这块信息密度天然很高（三个时间窗 + 三项指标 + 趋势 + 模型占比 + 用途），
                // 所以刻意分四层给层级：主数字 → 指标格 → 分节标题 → 明细。
                item {
                    OutlineCard {
                        Column {
                            // ① 主数字：今日最常用来看，给最大字号；7 天/累计降一级，
                            //    三等分宽度不变，视线从左到右自然读出"今天 → 一周 → 全部"。
                            Row(verticalAlignment = Alignment.Top) {
                                UsageFigure("今日", todayBucket.total, primary = true, Modifier.weight(1f))
                                FigureDivider()
                                UsageFigure("近 7 天", last7Tokens, primary = false, Modifier.weight(1f))
                                FigureDivider()
                                UsageFigure("累计", bill.total.total, primary = false, Modifier.weight(1f))
                            }

                            // ② 三项指标各自成格。缺失项写 "—"（厂商没上报），不写成 0 —— 0 是假信息。
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MetricCell(
                                    label = "调用",
                                    value = if (bill.total.calls > 0) "${bill.total.calls} 次" else "—",
                                    modifier = Modifier.weight(1f),
                                )
                                MetricCell(
                                    label = "输出速度",
                                    value = bill.total.tokensPerSecond
                                        ?.let { String.format(Locale.CHINA, "%.0f tok/s", it) } ?: "—",
                                    modifier = Modifier.weight(1f),
                                )
                                MetricCell(
                                    label = "缓存命中",
                                    value = bill.total.cacheRate?.let { "$it%" } ?: "—",
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            // ③ 趋势
                            val peak = bill.byDay.maxByOrNull { it.total }
                            UsageHeading(
                                "近 7 天趋势",
                                when {
                                    peak == null || peak.total <= 0L -> null
                                    // 峰值就是今天时不再重复那个数字 —— 顶上刚写过"今日 350.6k"
                                    peak.key == today -> "今日最高"
                                    else -> "峰值 ${UsageStats.compact(peak.total)} · ${peak.key.takeLast(5)}"
                                },
                            )
                            Last7Days(dailyMap, today)

                            // ④ 主要消耗
                            if (bill.byModel.isNotEmpty()) {
                                UsageHeading("主要消耗", "${bill.byModel.size} 个模型")
                                bill.byModel.take(3).forEachIndexed { i, b ->
                                    ModelUsageBar(b.key, b.total, bill.shareOf(b))
                                    if (i < minOf(2, bill.byModel.size - 1)) Spacer(Modifier.height(9.dp))
                                }
                            }

                            // ⑤ 按用途：两行以内的短列表，比一行长字符串好扫
                            if (bill.byPurpose.size > 1) {
                                UsageHeading("按用途", null)
                                bill.byPurpose.forEach { b ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            b.key,
                                            style = ty().micro,
                                            color = pal().ink,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(UsageStats.compact(b.total), style = ty().micro)
                                    }
                                }
                            }

                            // ⑥ 脚注：最弱的一层，只在想深究时看
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "一次模型往返记一行（含工具循环与上下文压缩）。" +
                                    "厂商未返回 usage 时按字符估算，记为 ≈；「—」表示该项厂商没有上报。",
                                style = ty().micro,
                                color = pal().faint,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                }

                item { SectionLabel("危险区") }
                item {
                    OutlineCard(padding = 0.dp) {
                        SettingRow(
                            title = "清空账单",
                            value = "",
                            icon = G.TRASH,
                            iconTint = pal().bad,
                            danger = true,
                            onClick = { confirmClear = true },
                        )
                    }
                }
                item {
                    Text(
                        "清空后今日、近 7 天与累计都会归零，无法恢复。",
                        style = ty().micro,
                        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp),
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        if (confirmClear) {
            ConfirmDialog(
                title = "清空账单？",
                message = "所有账单明细会被删除，今日、近 7 天与累计都会归零。清空后无法恢复。",
                confirmText = "清空",
                onConfirm = { vm.clearUsage(); confirmClear = false },
                onCancel = { confirmClear = false },
            )
        }
    }
}

/**
 * 一个模型的消耗占比：名字 + 百分比 + 一条按占比伸缩的横条。
 * 条长最短留 2%，否则占比极低的模型看起来像"没有数据"。
 */
@Composable
private fun ModelUsageBar(name: String, tokens: Long, share: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = ty().micro,
                color = pal().ink,
                maxLines = 1,
                // 长模型名（如 org/model-instruct-2507-q4）以前会顶掉右侧数字，
                // 现在省略中间之外的部分，把百分比保住 —— 两个都重要时先保结论
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                UsageStats.compact(tokens),
                style = ty().micro,
                color = pal().muted,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${(share * 100).toInt()}%",
                style = ty().micro,
                fontWeight = FontWeight.SemiBold,
                color = pal().ink,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(pal().surfaceAlt),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(pal().accent),
            )
        }
    }
}

/**
 * 三列大数字里的一列。
 *
 * [primary] 只给"今日"用：字号拉到 25sp、颜色用正文色；其余两列降字号并转次色。
 * 三个数字等大是错的 —— 用户 90% 的时间只看今天，三个一样大等于让他每次重新找。
 *
 * 数字用 [figureText]：小数值保留千分位（"1,024"），超过 10 万才压成 "1.2M"。
 * 三列并排时 7 位数字会把 1/3 宽度撑破，宁可压缩也不让数字换行或溢出。
 */
@Composable
private fun UsageFigure(
    label: String,
    tokens: Long,
    primary: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(vertical = 1.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = 0.2.sp,
            color = if (primary) pal().muted else pal().faint,
        )
        Spacer(Modifier.height(if (primary) 5.dp else 6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                figureText(tokens),
                fontSize = if (primary) 25.sp else 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (primary) pal().ink else pal().ink.copy(alpha = 0.82f),
                maxLines = 1,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "tok",
                fontSize = if (primary) 10.5.sp else 9.5.sp,
                color = pal().faint,
                modifier = Modifier.padding(bottom = if (primary) 4.dp else 2.dp),
            )
        }
    }
}

/** 三列之间的分隔线：细、短、垂直居中，只做视觉分栏，不抢注意力 */
@Composable
private fun FigureDivider() {
    Box(
        Modifier
            .padding(top = 18.dp)
            .width(1.dp)
            .height(28.dp)
            .background(pal().lineSoft),
    )
}

/**
 * 一项指标（调用次数 / 输出速度 / 缓存命中）。
 *
 * 值在上、标签在下：先看到数字再看到它是什么，扫读时最快。
 * 值缺失传 "—"，由调用方决定 —— 这里不把 null 变 0。
 */
@Composable
private fun MetricCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(pal().surfaceAlt)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = pal().ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = pal().faint, maxLines = 1)
    }
}

/** 卡片内的分节标题。字号比页面级 SectionLabel 小一号，避免两级标题打架 */
@Composable
private fun UsageHeading(text: String, trailing: String?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            color = pal().muted,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, fontSize = 10.sp, color = pal().faint, maxLines = 1)
        }
    }
}

/**
 * 数字显示口径：小于 10 万给千分位精确值，再大才压缩。
 * 三列并排时精度和宽度必须取舍 —— 7 位数字会撑破 1/3 宽度。
 */
private fun figureText(n: Long): String =
    if (n < 100_000) java.text.NumberFormat.getIntegerInstance(Locale.CHINA).format(n)
    else UsageStats.compact(n)

/** 近 7 日柱状图。全为 0 时画一条细底线，避免空图误导 */
@Composable
private fun Last7Days(daily: Map<String, Long>, todayKey: String) {
    val days = (6 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
    val values = days.map { d -> daily[d.toString()] ?: 0L }
    val max = values.maxOrNull() ?: 0L

    if (max <= 0) {
        Box(
            Modifier.fillMaxWidth().height(58.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "近 7 天还没有用量记录。发一条消息后这里会出现柱状图。",
                style = ty().micro,
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(54.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEachIndexed { i, d ->
                val v = values[i]
                val isToday = d.toString() == todayKey
                // 当天为 0 就完全不画柱子：之前给 8% 保底高度，会把零值画成一条细线，
                // 看起来像分隔线而不是"这天没有用量"
                val ratio = if (v <= 0L || max <= 0) 0f else (v.toFloat() / max).coerceIn(0.08f, 1f)
                Column(
                    Modifier.height(54.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height((ratio * 33f).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isToday) pal().accent else pal().surfaceAlt)
                    )
                    Spacer(Modifier.height(5.dp))
                    // 今天这一格的星期字加色加粗：柱子颜色已经区分了，但小字更容易一眼定位
                    Text(
                        d.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.CHINA),
                        fontSize = 10.sp,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isToday) pal().accent else pal().faint,
                        maxLines = 1,
                    )
                }
            }
        }
        // 基线：零值那天会把"柱子"整根省掉，没有这条线时插图看着像浮空的
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal().lineSoft))
    }
}
