package com.pocketagent.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.pocketagent.app.agent.ToolPolicy
import com.pocketagent.app.data.ProviderDef
import com.pocketagent.app.ui.AddModelScreen
import com.pocketagent.app.ui.ChatScreen
import com.pocketagent.app.ui.ConfirmDialog
import com.pocketagent.app.ui.DrawerPanel
import com.pocketagent.app.ui.EditModelScreen
import com.pocketagent.app.ui.ExtensionsScreen
import com.pocketagent.app.ui.ModelListScreen
import com.pocketagent.app.ui.OnboardingScreen
import com.pocketagent.app.ui.PresetScreen
import com.pocketagent.app.ui.SettingsScreen
import com.pocketagent.app.ui.SkillDetailScreen
import com.pocketagent.app.ui.SkillScreen
import com.pocketagent.app.ui.ToolApprovalDialog
import com.pocketagent.app.ui.UpdateDialog
import com.pocketagent.app.ui.HelpScreen
import com.pocketagent.app.ui.UsageScreen
import com.pocketagent.app.ui.pal
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * 二级页（浮层）。null 才表示会话页 —— 会话页是常驻的根，不是这个栈里的一个状态。
 */
sealed interface Screen {
    data object Models : Screen
    data object Presets : Screen
    data object Settings : Screen
    data object Extensions : Screen

    /** 用量账单。从设置进来，清空账单也在这页里 */
    data object Usage : Screen

    /** 新手引导：独立全屏页，盖在会话页之上（不再是会话空态里的一张卡） */
    data object Onboard : Screen

    /** 使用说明。从设置 → 关于进来，只读，没有可保存的东西 */
    data object Help : Screen

    /** 技能管理（列表）与技能详情 */
    data object SkillManager : Screen
    data class SkillDetail(val id: String) : Screen

    data class Configure(val def: ProviderDef) : Screen
    data class EditProvider(val providerId: String) : Screen
}

/**
 * 层级：只在二级页之间判断"往里钻还是往回退"。
 *
 * 设置是唯一的一级页 —— 其余功能页都从它里面进，所以它们都比设置深一层。
 * 从会话页直接进模型页时 initial 是 null，走的是"滑入"，这里不参与判断。
 */
private fun Screen.depth(): Int = when (this) {
    Screen.Settings -> 1
    Screen.Models, Screen.Extensions, Screen.Usage, Screen.Help, Screen.Onboard,
    Screen.SkillManager -> 2
    Screen.Presets -> 3
    is Screen.SkillDetail -> 3
    is Screen.EditProvider -> 3
    is Screen.Configure -> 4
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    /**
     * 二级页栈。空 = 会话页。
     *
     * 原来是单个 `Screen?`，返回一律回会话页 —— 从设置进「模型」再退回来会掉到会话页，
     * 想接着改设置就得重新拉一次抽屉。改成栈之后"从哪来、退回哪"由栈自己表达。
     */
    var stack by remember { mutableStateOf<List<Screen>>(emptyList()) }
    /** 当前压在最上面的二级页；null = 会话页 */
    val screen = stack.lastOrNull()

    /** 往里钻：功能页一律从设置进，退回来还是设置 */
    fun push(s: Screen) { stack = stack + s }
    /** 从会话页直接进（抽屉里的设置、对话页的"去管理模型"） */
    fun openFromChat(s: Screen) { stack = listOf(s) }
    fun popUp() { if (stack.isNotEmpty()) stack = stack.dropLast(1) }
    /** 保存完/取消编辑后回模型列表：中间可能隔着 Presets、Configure 两页 */
    fun popToModels() { stack = stack.dropLastWhile { it !is Screen.Models } }

    /**
     * 回到引导页。栈底有 Onboard 时，"配完模型"这条支路要落回这里。
     *
     * 与 [popToModels] 的区别只在退到哪一层：从设置进模型页时栈里没有 Onboard，
     * 保存后就该停在列表；从引导进来时目标是走完引导，不该停在中间层。
     */
    fun popToOnboard() { stack = stack.dropLastWhile { it !is Screen.Onboard } }
    fun closeAllScreens() { stack = emptyList() }

    /**
     * 页面层级深度：0 = 只有会话页，1 = 至少压着一个二级页。
     *
     * 这个值只驱动会话页"后退一层"（缩小 + 左移 + 圆角 + 投影）。
     * 用 0↔1 的过渡值而不是布尔，是为了让推入/退出时会话页和二级页**同时**动 ——
     * 一个在缩、一个在滑，层次感才成立（rikkahub 的旧页 scale 0.7 就是这个意思）。
     */
    val depth by animateFloatAsState(
        targetValue = if (stack.isEmpty()) 0f else 1f,
        animationSpec = tween(PAGE_MS, easing = PageEasing),
        label = "pageDepth",
    )

    /** 待二次确认的破坏性操作；null 表示没有弹窗 */
    var pending by remember { mutableStateOf<PendingConfirm?>(null) }

    // ─────────── 侧边栏 ───────────
    // 三层结构，从下到上：会话页 → 抽屉 → 二级页浮层。
    // 关键约定：**进出二级页时绝不去动抽屉**。
    // 抽屉只是被浮层盖住/露出，状态（开合、搜索词、滚动位置）一路不变，
    // 所以返回时它"本来就在那儿"，不会滑、不会闪、也不会有残影。
    val drawerProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    /**
     * 跟手拖动时的临时进度。null = 没在拖，一切以动画的 value 为准。
     *
     * 为什么不直接每帧 Animatable.snapTo：snapTo 会重启动画通道，
     * 快速滑动时手指位移和面板位移对不上（手已到 80%，面板只走到 60%），观感是"拖不动"。
     */
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val drawerWidthPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }

    /**
     * 甩动判定阈值（dp/s）。对齐 Material3 DrawerState 的默认 velocityThreshold：
     * 之前用 350 太"重"，轻甩不认账，手感上也是"难触发"的一部分。
     */
    val velocityOpenDp = 125f

    /** 渲染只认这个：拖动中用拖动值，其余时间用动画值 */
    val drawerFraction = { dragFraction ?: drawerProgress.value }
    val drawerOpen = drawerFraction() > 0.01f

    /**
     * 吸附。**先 snapTo(from) 再清 dragFraction**：
     * 反过来的话会有一帧回落到动画的旧值（比如 0），表现为松手瞬间闪一下。
     */
    fun animateDrawer(target: Float, from: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            drawerProgress.snapTo(from)
            dragFraction = null
            drawerProgress.animateTo(
                target,
                tween(
                    durationMillis = if (target > from) 220 else 180,
                    easing = if (target > from) FastOutSlowInEasing else FastOutLinearInEasing,
                ),
            )
        }
    }

    fun openDrawer() {
        animateDrawer(1f, drawerFraction())
    }

    fun closeDrawer() {
        animateDrawer(0f, drawerFraction())
    }

    // 导出走 SAF：用户自己挑位置，不需要 FileProvider，也不会往公共目录乱写
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> if (uri != null) vm.exportTo(uri) }

    val workspacePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        // 取消选择时什么都不做：工作区入口在顶栏 chip，和抽屉无关
        if (uri != null) vm.attachWorkspace(uri)
    }

    // 冷启动时**不再**自动跳模型页。
    // 以前这里是：没模型就 screen = Screen.Presets —— 用户还没看清这是什么，
    // 就被系统页面接管了，很像"点错了"。现在改成会话页给一张引导卡，
    // 由用户自己点「添加模型」再进去，而且配好返回时会话页会自动变成第二步。

    // ─────────── 统一返回栈：抽屉 → 二级页 → 系统 ───────────
    BackHandler(enabled = drawerOpen || stack.isNotEmpty()) {
        if (stack.isNotEmpty()) {
            // 逐级退：功能页 → 设置 → 会话页。栈里第二层是谁，退回去就是谁
            popUp()
            return@BackHandler
        }
        // 抽屉开着时按返回，只关抽屉，绝不能直接退到桌面
        if (drawerOpen) closeDrawer()
    }

    Box(
        Modifier
            .fillMaxSize()
            // ── 抽屉手势（照 Material3 ModalNavigationDrawer 的手感）──
            // 起手位置不限制：Material3 把 anchoredDraggable 挂在整个满屏容器上，
            // 所以在屏幕任意位置横向拖都能拉出抽屉（RikkaHub 就是这种手感）。
            // 关闭态：右滑拉出；打开态：左滑收回，全程跟手，松手按速度 + 半程阈值吸附；
            // 竖向滚动不会被抢：没越过横向触摸阈值就整段交还给下面的列表。
            .pointerInput(screen == null) {
                // 二级页盖在上面时不接手势，否则触摸会穿透到下面的会话页
                if (screen != null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startFrom = drawerFraction()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    // 手指一落下就接管：正在惯性收敛的动画必须先停，否则两边抢 progress
                    settleJob?.cancel()

                    /**
                     * 方向裁决，必须自己算，不能用 awaitHorizontalTouchSlopOrCancellation：
                     * 那个 API 只看"横向位移是否过 slop"，斜着快速下滑时横向分量也会过阈值，
                     * 它会先于列表滚动抢到事件 —— 表现就是"滚对话却弹出侧边栏"。
                     *
                     * 这里的规则：横向要**明显占优**（超过纵向 1.5 倍）才接管；
                     * 一旦纵向占优、或列表先消费了事件，立刻整段放弃，让列表自己滚。
                     */
                    val slop = viewConfiguration.touchSlop
                    var pointer = down.id
                    var dxTotal = 0f
                    var dyTotal = 0f
                    var fraction = startFrom
                    var tookOver = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer } ?: break
                        // 列表/其它组件已经接管（消费）了事件，或手指抬起：都不要抢
                        if (change.isConsumed || !change.pressed) break

                        dxTotal += change.positionChange().x
                        dyTotal += change.positionChange().y
                        tracker.addPosition(change.uptimeMillis, change.position)

                        if (kotlin.math.abs(dyTotal) > slop &&
                            kotlin.math.abs(dyTotal) >= kotlin.math.abs(dxTotal)
                        ) {
                            // 竖向占优：这是滚动，不是拉抽屉
                            dragFraction = null
                            return@awaitEachGesture
                        }
                        if (kotlin.math.abs(dxTotal) > slop &&
                            kotlin.math.abs(dxTotal) > kotlin.math.abs(dyTotal) * 1.5f
                        ) {
                            change.consume()
                            fraction = (startFrom + dxTotal / drawerWidthPx).coerceIn(0f, 1f)
                            dragFraction = fraction
                            tookOver = true
                            break
                        }
                    }
                    if (!tookOver) {
                        dragFraction = null
                        return@awaitEachGesture
                    }

                    horizontalDrag(down.id) { change ->
                        // 必须先读位移再 consume：positionChange() 在事件已消费后返回 Zero，
                        // 顺序反了会让拖动从第二个事件起全部变成 0 —— 面板定在第一次的进度上，
                        // 松手才由动画一次补到全开，表现就是"一点都不能跟手"。
                        val dx = change.positionChange().x
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        fraction = (fraction + dx / drawerWidthPx).coerceIn(0f, 1f)
                        dragFraction = fraction
                    }

                    // 速度优先：甩一下就按方向收/开，哪怕没拖过一半。
                    // 阈值取 125dp/s，与 Material3 DrawerState 的默认 velocityThreshold 一致；
                    // 之前用 350dp/s 太"重"，轻甩不认账，手感上就是"难触发"。
                    val vxDp = with(density) { tracker.calculateVelocity().x.toDp().value }
                    val target = when {
                        vxDp > velocityOpenDp -> 1f
                        vxDp < -velocityOpenDp -> 0f
                        fraction >= 0.5f -> 1f
                        else -> 0f
                    }
                    animateDrawer(target, fraction)
                }
            }
    ) {
        // 会话页后退时，卡片四周露出来的那圈"背景"。
        // 不铺这一层的话，缩小后的会话页和根窗口是同一个色，看着只是内容变小了、
        // 不像一张被压下去的卡片。颜色取 scrim 的低透明度版本：亮色下是浅灰，
        // 深色下本来就是纯黑，多浅都不会脏。
        Box(
            Modifier
                .fillMaxSize()
                .background(pal().scrim.copy(alpha = 0.32f * depth))
        )

        // ── ① 会话页：常驻最底层，不参与页面转场 ──
        // 二级页只是盖在它上面的浮层，撤掉浮层会话页本来就在原地：
        // 不重建（滚动位置、输入草稿都还在）。
        //
        // 但它要"后退一层"：有二级页压着时缩小 7%、左移 4.5%、带 18dp 圆角和投影。
        // 这就是 rikkahub 里旧页 scale 0.7 的那个观感 —— 只不过他们能淡出，
        // 我们**只缩不淡**：一淡，底下的背景就透出来，正是之前"闪一下"的来源。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val sc = 1f - 0.07f * depth
                    scaleX = sc
                    scaleY = sc
                    translationX = -size.width * 0.045f * depth
                    shape = RoundedCornerShape(18.dp * depth)
                    clip = depth > 0.001f
                    // 投影跟着深度出现：卡片边缘和背景之间才有分离感
                    shadowElevation = 8.dp.toPx() * depth
                }
        ) {
            ChatScreen(
                vm = vm,
                onOpenDrawer = { openDrawer() },
                // 对话页弹层里的「去管理模型」：抽屉没开，所以二级页正常滑入
                onOpenModels = { openFromChat(Screen.Models) },
                onPickWorkspace = { workspacePicker.launch(null) },
                // 导出入口从抽屉的 ⋯ 挪到了顶栏 ⋯（抽屉那颗位置让给了筛选）
                onExport = {
                    exportPicker.launch("pocket-agent-${System.currentTimeMillis()}.md")
                },
            )
        }

        // ── ② 抽屉：常驻图层，只平移 ──
        DrawerScrim(progress = drawerFraction)

        DrawerHost(progress = drawerFraction) {
            DrawerPanel(
                modelCount = vm.chatModels().size,
                drawerOpen = drawerOpen,
                sessions = persisted.sessions,
                currentSessionId = persisted.currentSessionId,
                // 折叠状态存在 VM 里：关抽屉再打开、转屏回来，两组都还是原来的开合状态
                collapsedGroups = vm.collapsedGroups,
                onToggleGroup = { vm.toggleGroup(it) },
                onNewChat = { vm.newConversation(); closeDrawer() },
                onOpenSession = { id -> vm.openSession(id); closeDrawer() },
                onRequestDelete = { id, title ->
                    pending = PendingConfirm(
                        title = "删除这段会话？",
                        message = "「$title」及其全部消息都会被移除，删除后无法恢复。",
                        confirmText = "删除",
                    ) { vm.deleteSession(id) }
                },
                onClose = { closeDrawer() },
                onRequestClear = {
                    pending = PendingConfirm(
                        title = "清空当前会话？",
                        message = "当前对话的全部消息会被删除，这段会话也会从历史记录里消失。",
                        confirmText = "清空",
                    ) {
                        vm.clearConversation()
                        closeDrawer()
                    }
                },
                // 进二级页不动抽屉：它被浮层盖住，返回时原样露出
                onOpenSettings = { openFromChat(Screen.Settings) },
                // 导出走系统文件选择器，先收抽屉 —— 回来时不该还压着一层抽屉
                onExport = {
                    closeDrawer()
                    exportPicker.launch("pocket-agent-${System.currentTimeMillis()}.md")
                },
            )
        }

        // ── ③ 二级页浮层：盖在抽屉之上 ──
        // 动效对齐 rikkahub-agent-pure 的 NavDisplay（见其 RouteActivity 的 transitionSpec）：
        //   推入 = 新页整屏从右滑入，旧页左移半屏（视差）→ 于是"深处"的页面看着更慢；
        //   返回 = 当前页整屏右滑退出，底下那页按同样的速度回到原位。
        // 唯一不照搬的是 fadeIn/fadeOut：他们每页下面垫的是纯色背景，可以淡；
        // 我们下面是活的会话页，一淡就透底 —— 所以这里任何一帧都必须有一页**完全不透明**
        // 地盖住屏幕，靠位移和缩放表达层次，不靠透明度。
        // 设置页的滚动位置放在**这里**持有。
        // 设置页一旦 push 出二级页就会离开组合，它内部 remember 的 LazyListState 随之销毁，
        // 返回时列表从顶部重来 —— 用户刚翻到「对话」那组，回来又得重新滑一遍。
        val settingsList = androidx.compose.foundation.lazy.rememberLazyListState()

        // 首启自动进引导：**等配置真的读完**（vm.ready）再判断"有没有模型"，
        // 否则会拿默认空状态误判，每次启动都跳一次。同一个进程只尝试一次 ——
        // 用户按 ✕ 退出（"先不看"，不写 dismissedAt）之后不该马上又被弹回来。
        var autoOnboardTried by remember { mutableStateOf(false) }
        LaunchedEffect(vm.ready) {
            if (!vm.ready || autoOnboardTried) return@LaunchedEffect
            autoOnboardTried = true
            val s = vm.state.value
            if (s.onboard.dismissedAt > 0L || s.onboard.completedAt > 0L) return@LaunchedEffect
            if (vm.chatModels().isNotEmpty()) return@LaunchedEffect
            push(Screen.Onboard)
        }

        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val spec = tween<IntOffset>(PAGE_MS, easing = PageEasing)
                when {
                    // 会话页 → 二级页：只有新页在动（会话页由 depth 自己缩/退）
                    initialState == null ->
                        slideInHorizontally(spec) { it } togetherWith ExitTransition.None

                    // 二级页 → 会话页：二级页整屏右滑退出，会话页本来就在原地等着
                    targetState == null ->
                        EnterTransition.None togetherWith slideOutHorizontally(spec) { it }

                    // 往里钻：新页铺满滑入，旧页只退半屏（视差），退到一半就被新页盖住
                    (targetState?.depth() ?: 0) > (initialState?.depth() ?: 0) ->
                        slideInHorizontally(spec) { it } togetherWith
                            slideOutHorizontally(spec) { -it / 2 }

                    // 往回退：两页对开，速度一致，中间不会漏出会话页
                    else ->
                        slideInHorizontally(spec) { -it } togetherWith
                            slideOutHorizontally(spec) { it }
                }
            },
            label = "overlay",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            // target 为 null 时留一个透明的空层，尺寸仍然铺满，不改变测量结果。
            // zIndex 不能省：推入时两页重叠半屏，新页必须画在旧页之上，
            // 否则旧页会盖住新页、看起来像"卡住不动"。
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(if (target == screen) 1f else 0f)
            ) {
                when (target) {
                    null -> Unit

                    is Screen.EditProvider -> EditModelScreen(
                        vm = vm,
                        providerId = target.providerId,
                        onBack = { popUp() },
                    )

                    // 设置：唯一的功能入口集合。四个入口都往里 push，退回来还是设置
                    Screen.Settings -> SettingsScreen(
                        vm = vm,
                        onBack = { popUp() },
                        listState = settingsList,
                        onOpenModels = { push(Screen.Models) },
                        onOpenExtensions = { push(Screen.Extensions) },
                        onOpenUsage = { push(Screen.Usage) },
                        onOpenHelp = { push(Screen.Help) },
                        onOpenSkills = { push(Screen.SkillManager) },
                        // 抽屉是进入设置页的入口，它一直开着；重看引导要"一步看到卡片"，
                        // 所以这里顺手把抽屉收掉，省掉用户再按一次返回
                        onReplayOnboard = {
                            // 只重置引导状态并把引导页推上来：**不再清空当前会话**
                            // （以前那样做只是为了把卡片放进空会话）
                            vm.replayOnboard()
                            closeDrawer()
                            closeAllScreens()
                            push(Screen.Onboard)
                        },
                    )

                    Screen.Extensions -> ExtensionsScreen(
                        vm = vm,
                        onBack = { popUp() },
                    )

                    Screen.Help -> HelpScreen(onBack = { popUp() })

                    Screen.Onboard -> OnboardingScreen(
                        vm = vm,
                        onClose = { popUp() },
                        // 「添加模型」在引导页之上再 push 一层：配完返回，仍然回到引导页，
                        // 此时这一步已自动打勾（步骤按现状推导）
                        onOpenModels = { push(Screen.Models) },
                        onPickWorkspace = { workspacePicker.launch(null) },
                    )

                    Screen.SkillManager -> SkillScreen(
                        vm = vm,
                        onBack = { popUp() },
                        onOpenDetail = { push(Screen.SkillDetail(it)) },
                    )

                    is Screen.SkillDetail -> SkillDetailScreen(
                        vm = vm,
                        id = target.id,
                        onBack = { popUp() },
                    )

                    Screen.Usage -> UsageScreen(
                        vm = vm,
                        onBack = { popUp() },
                    )

                    Screen.Models -> ModelListScreen(
                        vm = vm,
                        onBack = { popUp() },
                        onAdd = { push(Screen.Presets) },
                        onEdit = { id -> push(Screen.EditProvider(id)) },
                    )

                    Screen.Presets -> PresetScreen(
                        vm = vm,
                        onPick = { def -> push(Screen.Configure(def)) },
                        onBack = { popUp() },
                    )


                    is Screen.Configure -> AddModelScreen(
                        def = target.def,
                        vm = vm,
                        onBack = { vm.resetDiscovery(); popUp() },
                        onSaved = {
                            // 从引导页进来的：配完直接回引导页，别停在模型列表。
                            // 停在列表的话用户得自己意识到"还要按一次返回"，
                            // 多数人这时候就切去聊天了，第 2 步（选工作区）永远走不到。
                            if (stack.any { it is Screen.Onboard }) popToOnboard() else popToModels()
                        },
                    )
                }
            }
        }

        // ── ④ 破坏性操作的二次确认：独立 window，盖在所有层之上 ──
        pending?.let { c ->
            ConfirmDialog(
                title = c.title,
                message = c.message,
                confirmText = c.confirmText,
                onConfirm = {
                    pending = null
                    c.action()
                },
                onCancel = { pending = null },
            )
        }
        // ── ⑤ 工具写操作审批：盖在最上层，且必须点选才能继续 ──
        // 放在 AppRoot 而不是对话页里：抽屉或二级页开着时，agent 也可能正在跑，
        // 弹层必须无视当前停在哪一页直接浮出来。
        vm.approval?.let { req ->
            val danger = req.toolName == ToolPolicy.DELETE_PATH ||
                req.toolName == ToolPolicy.MOVE_PATH
            ToolApprovalDialog(
                title = if (danger) "确认${req.title}？" else "${req.title}？",
                target = req.target,
                diff = req.diff,
                note = req.note,
                danger = danger,
                onDeny = { vm.answerApproval(ApprovalAnswer.DENY) },
                onAllowOnce = { vm.answerApproval(ApprovalAnswer.ONCE) },
                onAllowAlways = { vm.answerApproval(ApprovalAnswer.ALWAYS) },
            )
        }
        // ── ⑥ 版本更新：同样盖在所有层之上 ──
        // 只在一轮 agent 结束后才可能被置起（见 AppViewModel.offerUpdate 里的 runActive 判断），
        // 所以不会和上面的审批框抢焦点。
        vm.updateUi?.let { ui ->
            UpdateDialog(
                ui = ui,
                onUpdate = { vm.startUpdate() },
                onLater = { vm.dismissUpdate() },
                onGoSettings = { vm.openInstallPermission() },
            )
        }
    }
}

/** 一次待确认的删除动作。文案在这里定，执行体由调用方给 */
private data class PendingConfirm(
    val title: String,
    val message: String,
    val confirmText: String,
    val action: () -> Unit,
)

/**
 * 抽屉遮罩。进度以 lambda 传入，读 value 只发生在本函数自己的重组作用域里 ——
 * 否则动画每一帧都会把整个 AppRoot（连同当前页面）重组一遍。
 *
 * 点击遮罩**不关抽屉**（按用户要求：只有 X 号或返回手势能关），
 * 但必须吃掉这一下点击 —— 否则会穿透到底下的会话页，误触到消息/输入框。
 */
@Composable
private fun DrawerScrim(progress: () -> Float) {
    val p = progress()
    if (p <= 0.002f) return
    Box(
        Modifier
            .fillMaxSize()
            .background(pal().scrim.copy(alpha = pal().scrim.alpha * p))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { /* 吞掉点击，不做任何事 */ },
            )
    )
}

/** 抽屉容器：translationX 走 graphicsLayer 的延迟读取，动画期间不触发任何重组 */
@Composable
private fun DrawerHost(progress: () -> Float, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxHeight()
            .graphicsLayer { translationX = -(1f - progress()) * size.width }
    ) { content() }
}

/** 页面转场统一时长。rikkahub 用的是默认 spring，这里取等价观感但不回弹的 300ms */
private const val PAGE_MS = 300

/**
 * 强调减速曲线（Material 的 emphasized decelerate）：起步快、尾巴长，
 * 落位时有"滑到位"的实感，比 FastOutSlowIn 更不像机械匀速。
 */
private val PageEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
