package com.pocketagent.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketagent.app.AppViewModel.OnboardStage

/**
 * 新手引导卡。
 *
 * 为什么是"卡片贴流程"而不是"五页教程"：
 * 这个 app 的首次门槛只有三个动作 —— 接模型、选目录、发第一个任务。
 * 把这三件事做成随时能看见的一张卡，用户在哪一步就看见哪一句；
 * 全屏 onboarding 看完就忘，还得让他自己去找"刚才说的那个按钮在哪"。
 *
 * 三条交互规矩：
 * 1. **按钮跳真页面**（模型页 / 系统目录选择器），不在卡片里做假表单；
 * 2. 回来时卡片由 AppViewModel.onboardStage 重新推导，所以"加完模型就自动变第二步"；
 * 3. 每张卡都能跳过，且跳过是永久的 —— 半路打断的教程比没有更烦。
 */
@Composable
fun OnboardCard(
    stage: OnboardStage,
    /**
     * 主按钮文案。首次流程是「添加模型」「选择目录」；
     * 重看时（条件已满足）变成「下一步」—— 只翻说明，不逼用户再配一遍。
     */
    primaryText: String,
    onPrimary: () -> Unit,
    onSample: (String) -> Unit,
    onSkip: () -> Unit,
    /** "先随便看看 / 先聊两句"：本次收起卡片，不写"跳过"，下次进来还能看到 */
    onLookAround: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stage == OnboardStage.None) return
    OutlineCard(modifier, padding = 0.dp) {
        Box {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 15.dp)) {
                StepDots(step = stage.stepOf())
                Spacer(Modifier.height(13.dp))
                Text(
                    stage.title(),
                    fontSize = 20.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal().ink,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    stage.body(),
                    fontSize = 13.sp,
                    lineHeight = 20.5.sp,
                    color = pal().muted,
                )

                when (stage) {
                    OnboardStage.Welcome -> {
                        Spacer(Modifier.height(14.dp))
                        OnboardButton(primaryText, onPrimary)
                        Spacer(Modifier.height(10.dp))
                        SecondaryLink("或者 先随便看看", onLookAround)
                        Spacer(Modifier.height(11.dp))
                        Text(
                            "用你自己的 API Key，只存在这台手机里，不上传。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = pal().faint,
                        )
                    }
                    OnboardStage.PickWorkspace -> {
                        Spacer(Modifier.height(14.dp))
                        OnboardButton(primaryText, onPrimary)
                        Spacer(Modifier.height(10.dp))
                        SecondaryLink("或者 先聊两句，不选也行", onLookAround)
                    }
                    OnboardStage.TryIt -> {
                        Spacer(Modifier.height(12.dp))
                        val samples = listOf(
                            "把这里带日期的 Word 改个名",
                            "总结这个目录里的笔记",
                        )
                        samples.forEachIndexed { i, s ->
                            SampleChip(s) { onSample(s) }
                            if (i < samples.lastIndex) Spacer(Modifier.height(7.dp))
                        }
                        Spacer(Modifier.height(11.dp))
                        Text(
                            "也能直接把文件发进来，我会先看清内容再动手。",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = pal().faint,
                        )
                    }
                    OnboardStage.None -> Unit
                }
            }

            // 跳过固定在右上角：它不属于任何一步的内容，越靠边越不像"主流程的一部分"
            // 颜色用 hint 而不是 faint：faint 在白底上只有 2.6:1，实机上几乎看不见，
            // 那就等于没给退路
            Text(
                "跳过",
                fontSize = 11.5.sp,
                color = pal().hint,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/** 三个点表示进度：完成的用浅强调色，当前的拉长成小胶囊 —— 不用 1/3 这种数字 */
@Composable
private fun StepDots(step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val current = i == step
            val done = i < step
            Box(
                Modifier
                    .height(5.dp)
                    .width(if (current) 14.dp else 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            current -> pal().accent
                            done -> pal().accent.copy(alpha = 0.45f)
                            else -> pal().line
                        }
                    )
            )
            if (i < 2) Spacer(Modifier.width(5.dp))
        }
    }
}

/**
 * 主按钮。
 *
 * 不用现有的 PrimaryButton：那个是 `fillMaxWidth` 46dp 的大按钮，
 * 在卡片里会把下面的"或者…"挤到第二屏；这里要的是贴内容的胶囊。
 * 配色仍取同一套 accent / onAccent，不引入新语义。
 */
@Composable
private fun OnboardButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(pal().accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = pal().onAccent)
    }
}

/** "或者 先随便看看"：这是一条**退路**，不是等价按钮，所以给文字链接的份量 */
@Composable
private fun SecondaryLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        color = pal().hint,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
    )
}

/** 示例问题：点了直接填进输入框，用户不用自己敲 —— 这是"教会它怎么用"最有效的一步 */
@Composable
private fun SampleChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(pal().surface)
            .border(1.dp, pal().line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = pal().ink)
    }
}

private fun OnboardStage.stepOf(): Int = when (this) {
    OnboardStage.Welcome -> 0
    OnboardStage.PickWorkspace -> 1
    OnboardStage.TryIt -> 2
    OnboardStage.None -> 0
}

private fun OnboardStage.title(): String = when (this) {
    OnboardStage.Welcome -> "你好，我是\nPocket Agent"
    OnboardStage.PickWorkspace -> "挑一个文件夹\n给我看"
    OnboardStage.TryIt -> "现在可以让它动手了"
    OnboardStage.None -> ""
}

private fun OnboardStage.body(): String = when (this) {
    OnboardStage.Welcome ->
        "我能读你指定的文件夹、改 Word 和 Excel、帮你跑命令。先接一个模型，就能开始。"
    OnboardStage.PickWorkspace ->
        "我只能看到你选的那个目录。别的文件碰不到，改之前会先把改动给你看。"
    OnboardStage.TryIt ->
        "用一句话说你要的结果，不用讲步骤。试试："
    OnboardStage.None -> ""
}
