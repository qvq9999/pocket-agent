package com.pocketagent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

import androidx.compose.animation.core.spring
import androidx.activity.compose.BackHandler

import androidx.compose.animation.core.RepeatMode

import androidx.compose.animation.core.LinearEasing

import androidx.compose.animation.core.infiniteRepeatable

import androidx.compose.animation.core.rememberInfiniteTransition

import androidx.compose.animation.core.animateFloat

import androidx.compose.ui.graphics.Brush

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitFirstDown


import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent

import androidx.compose.foundation.gestures.awaitEachGesture

import androidx.compose.ui.geometry.Offset

import androidx.compose.foundation.Canvas

import com.pocketagent.app.data.ThinkingLevel
import kotlin.math.roundToInt

import androidx.compose.ui.geometry.CornerRadius

import com.pocketagent.app.data.Catalog


import androidx.compose.ui.input.pointer.pointerInput

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.pocketagent.app.data.VisionDisplay
import com.pocketagent.app.data.visionDisplay
import com.pocketagent.app.agent.ToolPolicy
import kotlinx.serialization.json.jsonObject

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketagent.app.AppViewModel
import com.pocketagent.app.FileRef
import com.pocketagent.app.ChatMsg
import com.pocketagent.app.TransUi
import com.pocketagent.app.data.ModelProfile
import com.pocketagent.app.data.PermissionMode
import com.pocketagent.app.data.groupedByRecent
import com.pocketagent.app.data.ThinkingStyle
import com.pocketagent.app.net.ThinkingRules

private enum class Pop { NONE, MODEL, THINK, PERMIT, ATTACH }

/** WindowInsets.isImeVisible 仍带实验标记，但它比自建 ViewTreeObserver 稳，直接用 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    vm: AppViewModel,
    /** 抽屉的所有权在 AppRoot：那里统一管返回栈，ChatScreen 只负责"请求打开" */
    onOpenDrawer: () -> Unit,
    onOpenModels: () -> Unit,
    /** 顶栏「选工作区」chip 用；侧边栏不再提供重复入口 */
    onPickWorkspace: () -> Unit,
) {
    val persisted by vm.state.collectAsStateWithLifecycle()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val model = vm.selectedModel()
    val permission = when (persisted.permission) {
        "readonly" -> PermissionMode.READONLY
        "allow" -> PermissionMode.ALLOW
        else -> PermissionMode.CONFIRM
    }

    var input by remember { mutableStateOf("") }
    // 长按菜单：记录被长按的消息与它当时的窗口矩形（菜单靠这个定位）
    var menuMsg by remember { mutableStateOf<ChatMsg?>(null) }
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    // 待确认删除的消息。删除会改变上下文，走二次确认而不是点一下就没了
    var pendingDelete by remember { mutableStateOf<ChatMsg?>(null) }
    /**
     * 文本选择的清除计数。SelectionContainer 的选择状态在它内部，外部没法直接清，
     * 只能换 key 让它重建 —— 点空白处就 +1。
     * 只在「点击」时加，滚动不加：用户是想滚动看上面的内容，不该把选择弄丢。
     */
    var selEpoch by remember { mutableStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    fun copyMessage(m: ChatMsg) {
        val text = m.text
        if (text.isBlank()) {
            vm.notice = "这条消息是空的，没有可复制的内容"
            return
        }
        // 剪贴板在部分定制系统上可能被限制，失败必须明确告诉用户而不是静默
        val ok = runCatching {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("message", text))
        }.isSuccess
        vm.notice = if (ok) "已复制（${text.length} 字）" else "复制失败：剪贴板不可用"
    }
    // 正在编辑的历史消息 id（编辑后重发）。null = 正常新消息
    var editTarget by remember { mutableStateOf<Long?>(null) }
    var pop by remember { mutableStateOf(Pop.NONE) }
    var lastPop by remember { mutableStateOf(Pop.MODEL) }
    var lastNotice by remember { mutableStateOf("") }

    // 抽屉的开合手势统一在 AppRoot 做：那里同时有开和关两个状态，
    // 手势放在同一处才能"跟手拖一半就停、松手看速度吸附"。这里只留按钮入口。

    LaunchedEffect(pop) { if (pop != Pop.NONE) lastPop = pop }
    // 弹层开着时，返回键先收弹层（后注册的 BackHandler 优先，抽屉不参与）
    BackHandler(enabled = pop != Pop.NONE) { pop = Pop.NONE }

    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.addReference(it) } }

    // 系统相册：PickMultipleVisualMedia 在 Android 13+ 走系统照片选择器（不需要读存储权限），
    // 低版本自动退回 OPEN_DOCUMENT，行为一致
    val galleryPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris -> uris.forEach { vm.addReference(it) } }

    // 相机：必须落一个我们能写的 content:// —— 直接给 file:// 会 FileUriExposedException
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraUri?.let { vm.addReference(it) } }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 键盘状态：弹出层要跟着键盘抬高（否则被输入法盖住），聊天列表要在键盘弹起后重新贴底
    val imeVisible = WindowInsets.isImeVisible
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    /**
     * 输入法收起后主动失焦。
     *
     * 不这样做的话：按返回键/下拉收起键盘后，输入框仍然是 focused 状态 ——
     * 光标继续闪，有些系统还会在切回前台、转屏、或点一下别处又弹回来，
     * 看着像"键盘关不掉"。
     *
     * 只在**可见 → 不可见**这个边沿触发：首帧 ime 本来就是 false，
     * 不加这个标记的话一进页面就会把焦点清掉（之后再点输入框才恢复）。
     */
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible) focus.clearFocus()
        imeWasVisible = imeVisible
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(pal().pageBg)
    ) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = "Pocket Agent",
                onMenu = onOpenDrawer,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 新会话：只清空当前消息流，不改模型和工作区
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(pal().surfaceAlt)
                                .clickable { vm.newConversation() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(G.PLUS, 16.dp, pal().ink, 2.3f)
                        }
                        Spacer(Modifier.width(7.dp))
                        // 工作区跟着对话走：这里读的是**本对话**的绑定，不是全局设置。
                        // 未绑定时显示"选工作区"（空状态），失效时提示重新授权。
                        WorkspaceChip(
                            name = when {
                                vm.workspaceBroken -> "需重新授权"
                                vm.workspace == null -> "选工作区"
                                else -> vm.workspace?.name.orEmpty()
                            },
                            broken = vm.workspaceBroken,
                            onClick = onPickWorkspace,
                            onClear = if (vm.workspace != null) ({ vm.detachWorkspace() }) else null,
                        )
                    }
                },
            )

            Box(
                Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { selEpoch++ })
                    }
            ) {
                if (vm.messages.isEmpty()) {
                    // 引导阶段**每次都由现状推导**（有没有模型/目录/发过消息），
                    // 所以用户点「添加模型」跳去设置页、配好返回，卡片会自动变成第二步 ——
                    // 不需要任何"返回时通知一下"的胶水代码。
                    val stageNow = vm.onboardStage(persisted)
                    // "先随便看看"只是本次收起，不写"跳过"：下次进来如果他还没配好，还能再看到
                    var lookAround by remember(stageNow) { mutableStateOf(false) }
                    if (stageNow != AppViewModel.OnboardStage.None && !lookAround) {
                        val hasModel = vm.chatModels().isNotEmpty()
                        val hasWs = vm.workspace != null && !vm.workspaceBroken
                        // 垂直居中，与原来"你好"空态的位置一致：
                        // 卡片摆在眼球落点上，而不是贴着顶栏往下掉
                        Box(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            OnboardCard(
                                stage = stageNow,
                                primaryText = when (stageNow) {
                                    // 条件已满足说明在"重看"，只翻说明，不让人重新配一遍
                                    AppViewModel.OnboardStage.Welcome ->
                                        if (hasModel) "下一步" else "添加模型"
                                    AppViewModel.OnboardStage.PickWorkspace ->
                                        if (hasWs) "下一步" else "选择目录"
                                    else -> ""
                                },
                                onPrimary = {
                                    when {
                                        stageNow == AppViewModel.OnboardStage.Welcome && !hasModel ->
                                            onOpenModels()
                                        stageNow == AppViewModel.OnboardStage.PickWorkspace && !hasWs ->
                                            onPickWorkspace()
                                        else -> vm.advanceOnboard()
                                    }
                                },
                                onSample = { input = it },
                                onSkip = { vm.dismissOnboard() },
                                onLookAround = { lookAround = true },
                            )
                        }
                    } else {
                        EmptyState(
                            modelName = model?.displayName,
                            hasWorkspace = vm.workspace != null && !vm.workspaceBroken,
                            onPick = { s -> input = s },
                        )
                    }
                } else {
                    // 一段会话一份列表状态。LazyListState 是按位置记忆的，沿用到另一段会话
                    // 就会从上一段的位置起步、再一路滚回末尾 —— 历史一长就是肉眼可见的抽搐。
                    // 用 sessionEpoch 当 key：切换会话时整块重建，滚动状态从零开始。
                    key(vm.sessionEpoch) {
                        // 初始位置直接给到最后一条：第一帧就已经在底部，
                        // 不需要"先渲染顶部再滚下来"的中间过程。配合下面的瞬时 scrollToItem，
                        // 切会话时不会再看到页面自己动一下。
                        val listState = rememberLazyListState(
                            initialFirstVisibleItemIndex = vm.messages.lastIndex.coerceAtLeast(0),
                            initialFirstVisibleItemScrollOffset = 0,
                        )
                        val scope = rememberCoroutineScope()

                        /**
                         * 工具卡 / 思考块的展开态，**统一放在屏幕层**。
                         *
                         * 原来这两处各自在卡片内部 `remember`：卡片一旦被 LazyColumn 回收
                         * （滚出屏幕再滚回来）、或者流式期间这一轮的消息列表结构变化，
                         * 那里的 remember 就归零 —— 用户刚点开的卡片自己又合上了，
                         * 看起来就是"点了没反应 / 有时打不开"。
                         *
                         * 放在这里之后：状态跟着**消息 id**走，与卡片实例无关；
                         * 而且它在 `key(vm.sessionEpoch)` 作用域内，切会话自然清空，
                         * 不会把上一段会话的展开状态带到新会话里。
                         */
                        val cardOpen = remember { mutableStateMapOf<Long, Boolean>() }
                        val reasoningOpen = remember { mutableStateMapOf<Long, Boolean>() }

                        /**
                         * 是否停在最底部。
                         *
                         * 用 `canScrollForward` 而不是"最后一项完整可见"：
                         * 后者在"最后一条比视口还高"时永远是 false（长回复极常见），
                         * 于是贴底之后立刻被判定成"不在底部"，跟随就被自己关掉了。
                         */
                        val atBottom by remember(listState) {
                            derivedStateOf {
                                listState.layoutInfo.totalItemsCount > 0 && !listState.canScrollForward
                            }
                        }

                        /**
                         * 是否继续跟随。
                         *
                         * 这里只记**用户意图**，而且只能由用户的行为改变：
                         * · 用户拖拽列表 → 立刻停止跟随（他就想看历史）；
                         * · 用户滚回底部 / 点浮标 / 发新消息 → 重新跟随。
                         *
                         * 关键：**程序滚动不参与判定**。之前是"滚动一停就用 atBottom 覆盖"，
                         * 而我们自己的贴底滚完时列表可能还在测量，读到的 atBottom 是 false，
                         * 于是 autoFollow 被永久关掉 —— 表现为"跟半句就停"，而且之后每一轮
                         * 都不再跟随（连自己新发的消息也不跟）。
                         */
                        var autoFollow by remember(listState) { mutableStateOf(true) }

                        val dragging by listState.interactionSource.collectIsDraggedAsState()
                        LaunchedEffect(dragging) {
                            if (dragging) autoFollow = false
                        }

                        // 只会"恢复"，不会"关闭"：滚到底就重新跟随
                        LaunchedEffect(listState) {
                            snapshotFlow { listState.isScrollInProgress }.collect { moving ->
                                if (!moving && !listState.canScrollForward &&
                                    listState.layoutInfo.totalItemsCount > 0
                                ) {
                                    autoFollow = true
                                }
                            }
                        }

                        // 首屏定位：启动后恢复当前会话、或从抽屉切到另一段会话，一律先落在最底部。
                        // 刻意不依赖 atBottom —— 首帧测量完成时最后一条通常比视口高，
                        // 靠它判断会静默不滚动。
                        LaunchedEffect(listState) {
                            if (vm.messages.isNotEmpty()) listState.scrollToVeryBottom()
                        }

                        var seenCount by remember { mutableStateOf(vm.messages.size) }

                        LaunchedEffect(listState) {
                            // 触发源必须覆盖"这一轮里**任何**会长高的东西"：
                            // 新增消息、正文逐字、思考逐字、工具卡出现/展开、失败原因变长。
                            // 之前只盯「最后一条消息」的四个字段，于是"增长发生在别的消息上"
                            // （比如最后一条是工具结果，正文在它前面继续流）就一次都不触发。
                            // 现在改成：统计最后一条 user 之后**整轮**的字符与结构化增量。
                            snapshotFlow {
                                val msgs = vm.messages
                                var chars = 0
                                var extras = 0
                                var i = msgs.lastIndex
                                while (i >= 0 && msgs[i].role != "user") {
                                    val m = msgs[i]
                                    chars += m.text.length + m.reasoning.length + m.detail.length
                                    extras += m.calls.size * 1000 +
                                        (m.tool?.diff?.size ?: 0) * 100 +
                                        (m.tool?.summary?.length ?: 0)
                                    i--
                                }
                                // 最后一条的角色：决定"新消息"要不要强行贴底
                                ScrollSig(msgs.size, chars, extras, msgs.lastOrNull()?.role.orEmpty())
                            }.collect { sig ->
                                val appended = sig.count != seenCount
                                seenCount = sig.count
                                if (sig.count == 0) return@collect
                                when {
                                    // 自己发出去的消息：一定贴底并恢复跟随，用户不会想停在上一条
                                    appended && sig.lastRole == "user" -> {
                                        autoFollow = true
                                        listState.scrollToVeryBottom()
                                    }
                                    // 回复/工具卡追加：用户在看历史时就不要拽他
                                    appended -> if (autoFollow) listState.scrollToVeryBottom()
                                    // 逐字增长
                                    autoFollow -> listState.scrollToVeryBottom()
                                }
                            }
                        }

                        // 视口高度一变（键盘弹起/收起、通知条出现都会改变列表高度），
                        // 如果**变矮之前**是贴底的，就重新贴底。
                        //
                        // 为什么不用 imeVisible 触发：insets 是逐帧动画，等它回调时视口可能已经
                        // 缩过了，那一刻再问"在不在底部"答案一定是"不在"，条件自证其反。
                        LaunchedEffect(listState) {
                            var prevViewport = 0
                            var prevAtBottom = true
                            snapshotFlow {
                                val info = listState.layoutInfo
                                Triple(
                                    info.viewportEndOffset - info.viewportStartOffset,
                                    !listState.canScrollForward,
                                    info.totalItemsCount,
                                )
                            }.collect { snap ->
                                val h = snap.first
                                val atEnd = snap.second
                                val total = snap.third
                                if (prevViewport > 0 && h < prevViewport && prevAtBottom && total > 0) {
                                    // 视口变矮是外部原因（键盘），不是用户滚动 → 跟住真正的底部
                                    autoFollow = true
                                    listState.scrollToVeryBottom()
                                }
                                prevViewport = h
                                prevAtBottom = atEnd
                            }
                        }

                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    // 浮标出现时把底部留白让出来：它贴在右下角，正好压着
                                    // 最后一条的用量行和失败原因，那两行是用户最想看的
                                    bottom = if (atBottom) 10.dp else 58.dp,
                                ),
                            ) {
                                // 按「轮」聚合：一次回复 = 一个气泡（正文 + 工具调用 + 用量）。
                                // 之前是一条消息一行，一次工具调用就多一张卡，用户问一句冒出四五个气泡。
                                val rows = buildChatRows(vm.messages)
                                val produced = collectProduced(vm.messages)
                                items(rows, key = { it.key }) { row ->
                                    when (row) {
                                        is ChatRow.User -> {
                                            val m = row.m
                                            val trans = vm.translations[m.id]
                                            val showing = m.id in vm.showingTrans && trans is TransUi.Done
                                            val shownText =
                                                if (showing) (trans as TransUi.Done).text else m.text
                                            UserBubble(
                                                text = shownText,
                                                at = m.at,
                                                refs = m.refs,
                                                translated = trans is TransUi.Done,
                                                showingTranslated = showing,
                                                onToggleTranslated = { vm.toggleTranslation(m.id) },
                                                onMenu = { r ->
                                                    menuAnchor = r
                                                    menuMsg = m
                                                },
                                                selEpoch = selEpoch,
                                            )
                                        }

                                        // 上下文压缩后插在列表最前面的那段摘要
                                        is ChatRow.Summary -> SummaryRow(row.m.text)

                                        is ChatRow.Turn -> TurnBubble(
                                            vm = vm,
                                            items = row.items,
                                            selEpoch = selEpoch,
                                            cardOpen = cardOpen,
                                            reasoningOpen = reasoningOpen,
                                            onMenu = { msg, r ->
                                                menuAnchor = r
                                                menuMsg = msg
                                            },
                                            onUndo = { vm.undoTool(it) },
                                        )
                                    }
                                }

                                // 产出文件跟在最后一条回复后面，随内容一起滚走（不占固定底栏）
                                if (produced.isNotEmpty()) {
                                    // key 带 sessionEpoch：切换会话时这一项必须整体重建，
                                    // 否则会沿用上一段会话的组合状态
                                    item(key = "produced-${vm.sessionEpoch}") {
                                        ProducedFilesCard(produced) { vm.openProducedFile(it) }
                                    }
                                }

                                // 首个任务真改过文件之后的一次性提示。
                                // 放在列表末尾、紧接在结果后面：用户刚看到"改好了"，
                                // 此刻他最需要知道的就是"改坏了怎么办"。
                                if (vm.undoHint) {
                                    item { UndoHintRow { vm.dismissUndoHint() } }
                                }
                            }

                            // 「回到底部」：只有真的翻上去了才出现，贴在列表右下角，
                            // 不挡正文、不挡输入区，和输入区用同一套胶囊 + 描边样式。
                            // 这里不用 AnimatedVisibility —— 它在 Column 作用域里会解析到
                            // ColumnScope 那个重载，塞进 Box 里用不了；淡入自己算更省事。
                            val jumpAlpha by animateFloatAsState(
                                targetValue = if (atBottom) 0f else 1f,
                                animationSpec = tween(if (atBottom) 110 else 150),
                                label = "jumpToBottom",
                            )
                            if (jumpAlpha > 0.01f) {
                                JumpToBottomPill(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 14.dp, bottom = 22.dp)
                                        .graphicsLayer {
                                            alpha = jumpAlpha
                                            // 从 0.9 轻微放大进入：像浮标"冒出来"，不打断阅读
                                            val s = 0.9f + 0.1f * jumpAlpha
                                            scaleX = s
                                            scaleY = s
                                        },
                                ) {
                                    scope.launch {
                                        // 直接落到真正的底部：长历史里 animateScrollToItem 要走好几秒，
                                        // 而且它同样只把"最后一条的顶部"对齐，未必到底
                                        listState.scrollToVeryBottom()
                                        // 点它=用户明确想跟最新内容，顺手把跟随打开。
                                        // 只滚一次的话，回复继续往下长时人又被甩在后面。
                                        autoFollow = true
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 通知条放在输入区正上方：既不挡消息，也不会像浮层那样贴到顶栏下面
            val noticeText = vm.notice
            LaunchedEffect(noticeText) { if (noticeText != null) lastNotice = noticeText }
            AnimatedVisibility(
                visible = noticeText != null,
                enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                NoticeBar(lastNotice) { vm.notice = null }
            }

            // 上次任务被打断：给"继续 / 忽略"两个明确出口，别让它一直转圈或静默消失
            if (vm.interrupted && !vm.streaming) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.WARN, 14.dp, pal().warn, 1.9f)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "上次任务在运行中被中断",
                        style = ty().micro,
                        color = pal().ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "继续",
                        style = ty().micro,
                        color = pal().accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { vm.resumeInterrupted() }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    Text(
                        "忽略",
                        style = ty().micro,
                        color = pal().muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { vm.dismissInterrupted() }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            // 编辑历史消息时的提示条：不说清楚，用户会以为自己在发新消息
            if (editTarget != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.INFO, 13.dp, pal().accent, 1.9f)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "正在编辑历史消息 · 发送后会从这条起重新回答",
                        style = ty().micro,
                        color = pal().muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "取消",
                        style = ty().micro,
                        color = pal().accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                editTarget = null
                                input = ""
                            }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Composer(
                modifier = Modifier.navigationBarsPadding().imePadding(),
                modelLabel = model?.displayName ?: "未选模型",
                thinkingLabel = thinkingLabel(model, vm),
                thinkingEnabled = (model?.thinkingLevels?.isNotEmpty() == true),
                thinkingColors = thinkChipColors(
                    model,
                    model?.let { vm.currentThinkingLevel(it) },
                ),
                permission = permission,
                hasModel = model != null,
                workspaceName = vm.workspace?.name.orEmpty(),
                // 底部状态要能区分"没有工具"和"只有文件工具不可用但终端能跑"
                shellReady = vm.linuxStatus is AppViewModel.LinuxStatus.Ready &&
                    persisted.shellEnabled,
                streaming = vm.streaming,
                input = input,
                onInput = { input = it },
                onSend = {
                    val target = editTarget
                    if (target != null) {
                        // 编辑历史消息：改写那一条并从它之后重跑，不新增用户消息
                        vm.editAndResend(target, input)
                        editTarget = null
                    } else {
                        vm.send(input)
                    }
                    input = ""
                    // 发完就收键盘：接下来是等回复/读回复，输入法留着只会挡住半屏内容
                    focus.clearFocus()
                    keyboard?.hide()
                },
                onStop = { vm.stopStreaming() },
                onPop = { pop = it },
                refs = vm.refs.toList(),
                activeSkills = vm.skillCatalog().filter { it.id in persisted.activeSkills },
                onRemoveSkill = { vm.toggleSkill(it) },
                onAttach = { pop = if (pop == Pop.ATTACH) Pop.NONE else Pop.ATTACH },
                onRemoveRef = { vm.removeReference(it) },
                quoteLabel = vm.quote?.let { q ->
                    val who = if (q.role == "user") "我" else q.modelLabel.ifBlank { "AI" }
                    "$who：${q.text.replace('\n', ' ').take(40)}"
                },
                onClearQuote = { vm.clearQuote() },
            )
        }

        // ── 锚定在输入区上方的就地弹出层 ──
        if (pop != Pop.NONE) {
            Box(Modifier.fillMaxSize().clickable { pop = Pop.NONE })
        }
        AnimatedVisibility(
            visible = pop != Pop.NONE,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.96f),
            exit = fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.98f),
            modifier = Modifier
                // 附件菜单是从输入框左边的加号长出来的，所以贴左；其余三个弹层仍在右下
                .align(if (lastPop == Pop.ATTACH) Alignment.BottomStart else Alignment.BottomEnd)
                // 卡片贴右下角 14dp / 122dp（122dp ≈ 输入区高度）。
                // 键盘弹起时输入区整块被 imePadding 抬上去了，弹层也要加上同样的位移，
                // 否则它会停在原位、正好被输入法盖住 —— 这是「输入法开着时点模型胶囊看不到弹窗」的根因。
                .padding(
                    start = if (lastPop == Pop.ATTACH) 12.dp else 0.dp,
                    end = if (lastPop == Pop.ATTACH) 0.dp else 14.dp,
                    bottom = 122.dp + if (imeVisible) imeBottom else navBottom,
                ),
        ) {
            // 238dp：322dp 的默认宽度会盖住半个屏幕，而这个菜单只有三行短标签
            PopoverSurface(width = if (lastPop == Pop.ATTACH) 264.dp else 292.dp) {
                when (lastPop) {
                    Pop.MODEL -> ModelPopover(
                        vm = vm,
                        onGoModels = { pop = Pop.NONE; onOpenModels() },
                        onClose = { pop = Pop.NONE },
                    )
                    Pop.THINK -> ThinkPopover(vm) { pop = Pop.NONE }
                    Pop.PERMIT -> PermitPopover(vm) { pop = Pop.NONE }
                    Pop.ATTACH -> AttachMenu(
                        // 停用的技能不进菜单：开关关着却还能在这儿选，两边就对不上
                        skills = vm.skillCatalog().filter {
                            it.id !in persisted.disabledSkills &&
                                com.pocketagent.app.data.SkillStore
                                    .skillDir(ctx, it.id).isDirectory
                        },
                        activeSkills = persisted.activeSkills.toSet(),
                        onToggleSkill = { vm.toggleSkill(it) },
                        vision = model?.visionEffective == true,
                        // 按模型表给出提示：表说"仅文本"就提前警告，表里没有就说"未知"
                        declaredTextOnly = model?.visionDisplay() == VisionDisplay.DECLARED_TEXT_ONLY,
                        unverified = model?.visionDisplay() == VisionDisplay.UNKNOWN,
                        onCamera = {
                            pop = Pop.NONE
                            // 失败原因必须原样报出来：之前这里统一写"临时目录创建失败"，
                            // 把 FileProvider 配置错误也盖成了"建目录失败"，白查一轮
                            var why: String? = null
                            val uri = runCatching {
                                val dir = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
                                // 只留最近 8 张：拍失败的、取消的也会留文件，不清就一路涨
                                dir.listFiles()?.sortedByDescending { it.lastModified() }
                                    ?.drop(8)?.forEach { it.delete() }
                                val f = java.io.File(dir, "shot-${System.currentTimeMillis()}.jpg")
                                androidx.core.content.FileProvider.getUriForFile(
                                    // 用拼接而不是插值：这里的 authority 必须和清单里的
                                    // "${applicationId}.fileprovider" 逐字相等，拼错了只会得到
                                    // "Couldn't find meta-data for provider with authority ..."
                                    ctx, ctx.packageName + ".fileprovider", f,
                                )
                            }.onFailure { why = it.message ?: it::class.java.simpleName }.getOrNull()
                            if (uri == null) {
                                vm.notice = "打不开相机：" + (why ?: "拿不到临时文件地址")
                            } else {
                                // 设备上可能根本没有相机应用（部分定制系统），launch 会抛
                                // ActivityNotFoundException —— 不接住就是直接崩
                                cameraUri = uri
                                runCatching { cameraPicker.launch(uri) }
                                    .onFailure { vm.notice = "这台设备没有可用的相机应用" }
                            }
                        },
                        onGallery = { pop = Pop.NONE; galleryPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                        )) },
                        onFile = { pop = Pop.NONE; filePicker.launch(arrayOf("*/*")) },
                    )
                    Pop.NONE -> Unit
                }
            }
        }

        // ── 消息长按菜单：最后组合，保证盖在弹层与列表之上 ──
        val mm = menuMsg
        if (mm != null) {
            val t = vm.translations[mm.id]
            val items = buildList {
                add(MsgAction("复制") { copyMessage(mm); menuMsg = null })
                add(MsgAction("引用") { vm.quoteMessage(mm); menuMsg = null })
                when (t) {
                    is TransUi.Loading -> add(
                        MsgAction("翻译成中文", hint = "翻译中", enabled = false) {}
                    )
                    is TransUi.Done -> add(
                        MsgAction(if (mm.id in vm.showingTrans) "查看原文" else "查看译文") {
                            vm.toggleTranslation(mm.id); menuMsg = null
                        }
                    )
                    else -> add(
                        MsgAction("翻译成中文") { vm.toggleTranslation(mm.id); menuMsg = null }
                    )
                }
                if (mm.role == "user") {
                    add(
                        MsgAction("编辑后重发") {
                            menuMsg = null
                            input = mm.text
                            editTarget = mm.id
                        }
                    )
                }
                // 只给"最后一条回复"重新生成：中间那条重跑会把它之后的对话全丢掉，容易误触
                val lastAssistantId = vm.messages.lastOrNull { it.role == "assistant" && it.calls.isEmpty() }?.id
                if (mm.role == "assistant" && mm.id == lastAssistantId && !vm.streaming) {
                    add(MsgAction("重新生成") { menuMsg = null; vm.regenerate() })
                }
                // 工具卡片不给删除：它的正文已经作为 tool 消息进了上下文，
                // 单独删会留下孤立的 tool_call 配对，下一轮请求直接 400
                if (mm.role != "tool") {
                    add(
                        MsgAction("删除", danger = true) {
                            menuMsg = null
                            pendingDelete = mm
                        }
                    )
                }
            }
            MessageMenu(
                anchor = menuAnchor,
                items = items,
                onDismiss = { menuMsg = null },
            )
        }

        // 删除确认。放在菜单之后组合，保证它盖在菜单那一层之上
        pendingDelete?.let { target ->
            ConfirmDialog(
                title = "删除这条消息？",
                message = if (target.role == "user") {
                    "你发送的这条消息会被删除，之后的对话里模型也看不到它了。"
                } else {
                    "这条回复会被删除。如果它是带工具调用的那一轮，相关的工具记录会一起删掉。"
                },
                onConfirm = {
                    vm.deleteMessage(target.id)
                    pendingDelete = null
                },
                onCancel = { pendingDelete = null },
            )
        }
    }
}

/**
 * 「早期对话已压缩」折叠条。
 *
 * 摘要本身是给模型用的，但用户必须知道上面那段去哪了 —— 静默丢历史是最让人不信任的行为之一。
 * 默认收起，点开能看到模型实际记住的内容。
 */
@Composable
private fun SummaryRow(text: String) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(pal().surfaceAlt)
                .clickable { open = !open }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(G.CUBE, 13.dp, pal().muted, 1.9f)
            Spacer(Modifier.width(6.dp))
            Text(
                if (open) "早期对话已压缩为摘要 · 收起" else "早期对话已压缩为摘要 · 展开",
                fontSize = 11.5.sp,
                color = pal().muted,
            )
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = pal().ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pal().surfaceAlt)
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun WorkspaceChip(
    name: String,
    broken: Boolean = false,
    onClick: () -> Unit,
    /** 已绑定时给一个"解除绑定"的小叉；未绑定则不显示 */
    onClear: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(pal().surfaceAlt)
            .clickable(onClick = onClick)
            .padding(start = 11.dp, end = if (onClear != null) 5.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(if (broken) G.WARN else G.FOLDER, 14.dp, if (broken) pal().warn else pal().muted, 1.9f)
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            fontSize = 13.sp,
            color = if (broken) pal().warn else pal().ink,
            maxLines = 1,
        )
        if (onClear != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(G.CLOSE, 11.dp, pal().muted, 2.1f)
            }
        }
    }
}

@Composable
private fun EmptyState(modelName: String?, hasWorkspace: Boolean, onPick: (String) -> Unit) {
    // 示例必须**当前状态下真的跑得通**：没选目录时给"这个目录里有什么"，
    // 点下去只会撞一个"没有工作区"的错误 —— 引导亲手把用户送进死路是最糟的。
    val suggestions = if (hasWorkspace) {
        listOf(
            "这个目录里有什么？",
            "帮我把下载目录按月份归档",
            "找出所有引用旧域名的文件",
        )
    } else {
        listOf(
            "帮我写一段自我介绍",
            "把这段话改得更简洁",
            "解释一下什么是向量数据库",
        )
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "你好",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = pal().accent,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (modelName == null) "先接一个模型，就能开始了" else "当前模型：$modelName",
            fontSize = 14.sp,
            color = pal().hint,
        )
        Spacer(Modifier.height(22.dp))
        suggestions.forEach { s ->
            Row(
                Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pal().card)
                    .border(1.dp, pal().line, RoundedCornerShape(50))
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(s, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "v${com.pocketagent.app.BuildConfig.VERSION_NAME} · 改文件前会先给你看改动，不满意可以撤销",
            style = ty().micro,
        )
    }
}

/**
 * 一次性提示：改完可以撤销。
 *
 * 不做弹窗、不做浮层：它只是对刚发生的事补一句说明，
 * 抢走注意力反而打断"看到结果"这件事本身。给一个「知道了」就够了。
 */
@Composable
private fun UndoHintRow(onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(pal().accent)
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "不满意的话，点消息上的「⋯」可以撤销这次改动。",
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = pal().muted,
            )
            Text("这条提示只出现一次。", fontSize = 10.5.sp, color = pal().faint)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "知道了",
            fontSize = 11.5.sp,
            color = pal().hint,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UserBubble(
    text: String,
    /** 发出时刻；0 = 旧消息，不显示 */
    at: Long = 0,
    refs: List<String> = emptyList(),
    translated: Boolean = false,
    showingTranslated: Boolean = false,
    onToggleTranslated: () -> Unit = {},
    onMenu: (Rect) -> Unit = {},
    selEpoch: Int = 0,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        // 气泡本体用 Box：正文照常纵向排，右下角那个「⋯」是**叠上去的角标**。
        // 不能再让它当 Column 的最后一个 child —— 那样它会跟着文字流走，
        // 文字窄的时候整块气泡被它撑宽、它就落在文字末尾，看起来"飘在中间"。
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                .background(pal().userBubble),
        ) {
            Column(
                // bottom 留出角标那条通道，正文不会压到「⋯」上
                Modifier.padding(start = 15.dp, top = 11.dp, end = 15.dp, bottom = 26.dp),
            ) {
                refs.forEach { name ->
                    Row(
                        Modifier.padding(bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(G.FOLDER, 12.dp, pal().hint, 1.9f)
                        Spacer(Modifier.width(5.dp))
                        Text(name, fontSize = 11.sp, color = pal().hint, maxLines = 1)
                    }
                }
                // 长按 = 系统文本选择（可以拖手柄选任意片段再复制）
                key(selEpoch) {
                    SelectionContainer { Text(text, style = ty().body) }
                }
                if (translated) {
                    TransTag(showingTranslated, onToggleTranslated)
                }
                // 时间贴右下角：这是"这条什么时候发的"，不是正文的一部分
                val clock = clockOf(at)
                if (clock.isNotBlank()) {
                    Text(
                        clock,
                        fontSize = 10.sp,
                        color = pal().faint,
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    )
                }
            }
            MoreButton(
                onMenu,
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 3.dp),
            )
        }
    }
}

/** 译文开关小标签。不占菜单，直接在消息底部点一下就切回原文 */
@Composable
private fun TransTag(showingTranslated: Boolean, onToggle: () -> Unit) {
    Spacer(Modifier.height(6.dp))
    Text(
        if (showingTranslated) "已翻译 · 查看原文" else "原文 · 查看译文",
        fontSize = 10.5.sp,
        color = pal().hint,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onToggle() }
            .padding(horizontal = 3.dp, vertical = 2.dp),
    )
}

/**
 * 气泡右下角的「⋯」角标（由气泡 Box 叠在底角上，见 UserBubble / AssistantRow）。
 * 长按已经让给系统文本选择（可以拖手柄选任意片段），
 * 所以整条消息的操作统一收进这个按钮，一个入口不打架。
 */
@Composable
private fun MoreButton(onMenu: (Rect) -> Unit, modifier: Modifier = Modifier) {
    var rect by remember { mutableStateOf(Rect.Zero) }
    // 三个点用 Canvas 画，不用 "···" 文本：文本自带字间距和末位留白，
    // 点在按钮里会偏一边，靠 padding 怎么调都贴不准角。
    val dot = pal().faint
    Box(
        modifier
            .onGloballyPositioned { rect = it.boundsInWindow() }
            .clip(RoundedCornerShape(9.dp))
            .clickable { onMenu(rect) }
            .padding(horizontal = 5.dp, vertical = 6.dp),
    ) {
        Canvas(Modifier.size(width = 20.dp, height = 8.dp)) {
            val r = 1.5.dp.toPx()
            val gap = 5.dp.toPx()
            val cy = size.height / 2f
            val x0 = (size.width - gap * 2f) / 2f
            drawCircle(dot, r, Offset(x0, cy))
            drawCircle(dot, r, Offset(x0 + gap, cy))
            drawCircle(dot, r, Offset(x0 + gap * 2f, cy))
        }
    }
}

/* ─────────── 一轮回复 = 一个气泡 ─────────── */

/**
 * 列表行。
 *
 * 之前是"一条消息一行"：一次工具调用就多一张卡、模型再说话又多一个气泡，
 * 于是用户问一句、屏幕上冒出四五个气泡，看着像聊了好几轮。
 * 现在按**轮**聚合：一条 user 之后直到下一条 user/summary 之前的所有
 * assistant / tool 消息都属于同一次回复，渲染进同一个气泡。
 */
private sealed interface ChatRow {
    val key: Long

    class User(val m: ChatMsg) : ChatRow {
        override val key: Long get() = m.id
    }

    class Summary(val m: ChatMsg) : ChatRow {
        override val key: Long get() = m.id
    }

    class Turn(val items: List<ChatMsg>) : ChatRow {
        override val key: Long get() = items.first().id
    }
}

private fun buildChatRows(msgs: List<ChatMsg>): List<ChatRow> {
    val out = ArrayList<ChatRow>(msgs.size)
    var buf: ArrayList<ChatMsg>? = null
    fun flush() {
        val b = buf ?: return
        if (b.isNotEmpty()) out.add(ChatRow.Turn(b))
        buf = null
    }
    for (m in msgs) {
        when (m.role) {
            "user" -> {
                flush()
                out.add(ChatRow.User(m))
            }
            "summary" -> {
                flush()
                out.add(ChatRow.Summary(m))
            }
            else -> {
                if (buf == null) buf = ArrayList()
                buf!!.add(m)
            }
        }
    }
    flush()
    return out
}

/**
 * 一次回复的整体气泡：思考 → 正文 → 工具调用 → 用量，全都长在同一个灰底里。
 *
 * 工具卡片用 inline 形态（白底、不缩进）：既看得出"它属于这一次回复"，
 * 又不会和气泡底色糊成一片。
 */
@Composable
private fun TurnBubble(
    vm: AppViewModel,
    items: List<ChatMsg>,
    selEpoch: Int,
    /** 工具卡展开态（按消息 id）。由屏幕层托管，见调用处的说明 */
    cardOpen: MutableMap<Long, Boolean>,
    /** 思考块展开态（按消息 id）。**只有用户点过的**才在里面 —— 没点过的走"流式中展开"的默认规则 */
    reasoningOpen: MutableMap<Long, Boolean>,
    onMenu: (ChatMsg, Rect) -> Unit,
    onUndo: (Long) -> Unit,
) {
    // 头像取"第一个带厂商信息的消息"：工具消息本身不带 slug，
    // 直接取 items.first() 会在"模型先调工具、后说话"的那一段渲染出空白头像
    val lead = items.firstOrNull { it.vendorSlug.isNotBlank() || it.vendorFallback.isNotBlank() }
        ?: items.first()
    val lastId = vm.messages.lastOrNull()?.id
    val target = items.lastOrNull { it.tool == null && !it.failed } ?: items.last()
    val usage = items.lastOrNull { it.usage != null }?.usage
    // 这一轮的结束时间与总耗时：收尾时由 VM 打在最后一条上（用户消息只有时间）
    val doneAt = items.lastOrNull { it.at > 0 }?.at ?: 0L
    val spentMs = items.lastOrNull { it.elapsedMs > 0 }?.elapsedMs ?: 0L
    // 底部一行：时间 · 用时 · 消耗 · tok/s · 缓存。没有的项直接不出现，
    // 不留空占位（旧会话没有时间戳，就只显示用量）
    val meta = buildList {
        clockOf(doneAt).takeIf { it.isNotBlank() }?.let { add(it) }
        if (spentMs > 0) add("用时 " + durText(spentMs))
        usage?.let { add(usageText(it)) }
    }.joinToString(" · ")

    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        VendorMark(
            slug = lead.vendorSlug.ifBlank { null },
            fallbackText = lead.vendorFallback,
            fallbackColor = Color(lead.vendorColor),
            dim = 26.dp,
            glyphDim = 15.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val label = items.firstOrNull { it.modelLabel.isNotBlank() }?.modelLabel
            if (label != null) {
                Text(
                    label,
                    style = ty().modelName,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp))
                    .background(pal().aiBubble),
            ) {
                Column(
                    Modifier.padding(
                        start = 13.dp,
                        top = 11.dp,
                        end = 13.dp,
                        bottom = if (meta.isNotBlank()) 11.dp else 26.dp,
                    ),
                ) {
                    items.forEachIndexed { idx, m ->
                        val tool = m.tool
                        when {
                            m.role == "tool" && tool != null -> {
                                // 工具卡自带 8dp 上边距，这里不要再叠加
                                val opened = cardOpen[m.id] == true
                                ToolCallRow(
                                    tool = tool,
                                    inline = true,
                                    expanded = opened,
                                    onToggle = { cardOpen[m.id] = !opened },
                                ) { onUndo(m.id) }
                            }

                            m.failed -> {
                                if (idx > 0) Spacer(Modifier.height(10.dp))
                                Text(
                                    m.text.ifBlank { "请求失败" },
                                    fontSize = 14.5.sp,
                                    lineHeight = 22.sp,
                                    color = pal().bad,
                                )
                                if (m.detail.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(pal().badSoft)
                                            .padding(9.dp)
                                    ) {
                                        Text(m.detail, style = ty().monoId, color = pal().bad)
                                    }
                                }
                            }

                            else -> {
                                val trans = vm.translations[m.id]
                                val showing = m.id in vm.showingTrans && trans is TransUi.Done
                                val shownText = if (showing) (trans as TransUi.Done).text else m.text
                                if (idx > 0) Spacer(Modifier.height(10.dp))
                                if (!showing && m.reasoning.isNotBlank()) {
                                    // 流式中自动展开（用户这时候就想看"它在想什么"）；
                                    // 一旦用户手动点过，就完全听他的 —— 否则会出现
                                    // "我明明关掉了，下一段思考又自己弹开"。
                                    val thinkingNow =
                                        vm.streaming && m.id == lastId && shownText.isBlank()
                                    val rOpen = reasoningOpen[m.id] ?: thinkingNow
                                    ReasoningBlock(
                                        text = m.reasoning,
                                        streaming = thinkingNow,
                                        expanded = rOpen,
                                        onToggle = { reasoningOpen[m.id] = !rOpen },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                if (shownText.isNotBlank()) {
                                    key(selEpoch) {
                                        SelectionContainer { MarkdownText(shownText, style = ty().body) }
                                    }
                                } else if (vm.streaming && m.id == lastId) {
                                    Box(Modifier.padding(vertical = 3.dp)) { LoadingDots(pal().muted) }
                                }
                                if (trans is TransUi.Done) {
                                    TransTag(showing) { vm.toggleTranslation(m.id) }
                                }
                            }
                        }
                    }
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            meta,
                            style = ty().micro,
                            lineHeight = 15.sp,
                            // 右边留出「⋯」角标的位置，否则长行会压到它上面
                            modifier = Modifier.padding(end = 38.dp),
                        )
                    }
                }
                MoreButton(
                    { r -> onMenu(target, r) },
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 3.dp),
                )
            }
        }
    }
}

/* ─────────── 本对话产出的文件 ─────────── */

private data class ProducedFile(val rel: String, val name: String)

/**
 * 滚动触发签名。
 *
 * [count] 条目数、[chars] 这一轮所有消息的可见字符总数、[extras] 工具卡的结构化增量；
 * [lastRole] 用来区分"新消息"是用户发的（必须贴底）还是模型/工具的（跟随态才贴）。
 */
private data class ScrollSig(
    val count: Int,
    val chars: Int,
    val extras: Int,
    val lastRole: String,
)

/**
 * 从工具调用的参数里挑出"真的写出了文件"的那些。
 *
 * 不看工具自己的 summary（那是给模型看的一句话），只看参数里的目标路径 ——
 * 这样即使模型把话说错、文件也确实写了，快捷方式照样出得来。
 * 去重按路径：同一个文件被改三次，只留一条。
 */
/**
 * 本轮产出。
 *
 * 只统计**最后一条 user 消息之后**的工具结果：底部那张卡是"这次任务做出了什么"，
 * 不是"这段会话历史上产出过什么"。以前是全会话累计，于是继续做下一个任务时，
 * 上一轮的文件还挂在那儿 —— 既占地方，又让人分不清哪些是刚生成的。
 * 旧文件在消息流里仍然看得到（工具卡 + 产出记录都在），只是不再常驻底部。
 */
private fun collectProduced(msgs: List<ChatMsg>): List<ProducedFile> {
    val fileTools = setOf(
        ToolPolicy.WRITE_FILE, ToolPolicy.APPEND_FILE, ToolPolicy.REPLACE_IN_FILE,
        ToolPolicy.DOCX_FILL, ToolPolicy.XLSX_SET, ToolPolicy.COPY_PATH, ToolPolicy.MOVE_PATH,
    )
    // 按**文件名**去重，后出现的覆盖前面的：同一个文件被"写一次 + 脚本再写一次"很常见，
    // 路径可能一个是 `a.pptx` 一个是 `out/a.pptx`。按路径去重会把它当成两个文件，
    // 用户看到的就是"重复的产出"。反过来，同名不同目录的情况极少，折叠成一条可以接受。
    val byName = LinkedHashMap<String, ProducedFile>()
    fun put(rel: String) {
        if (rel.isBlank()) return
        val name = rel.substringAfterLast('/')
        if (name.isBlank()) return
        val key = name.lowercase()
        byName.remove(key)
        byName[key] = ProducedFile(rel, name)
    }

    val lastUser = msgs.indexOfLast { it.role == "user" }
    val scope = if (lastUser >= 0) msgs.subList(lastUser + 1, msgs.size) else msgs
    for (m in scope) {
        val t = m.tool ?: continue
        // ① 工具自己回报的产出（skill_run 把 /workspace/out 拷回工作区的那些）。
        //    这条以前完全没有 —— 脚本生成的 PPT 就"看不见"，只因为它的路径不在参数里
        t.artifacts.forEach { put(it) }
        // ② 参数里能推出来的写入目标（write_file / docx_fill / copy_path …）
        if (!t.ok || t.name !in fileTools) continue
        val o = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(t.args).jsonObject
        }.getOrNull() ?: continue
        fun str(k: String): String? =
            (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val rel = str("dest") ?: str("path") ?: str("to") ?: continue
        put(rel)
    }
    return byName.values.toList()
}

/** 类型方块：小尺寸下只靠底色 + 三个字母区分，比画一个通用文件图标更好认 */
@Composable
private fun FileExtIcon(ext: String, dim: Int = 30) {
    val (bg, fg) = when (ext.lowercase()) {
        "docx", "doc" -> pal().accentSoft to pal().accentInk
        "xlsx", "xls", "csv" -> pal().okSoft to pal().ok
        "pdf" -> pal().badSoft to pal().bad
        "md", "txt", "log", "json" -> pal().surfaceAlt to pal().muted
        "png", "jpg", "jpeg", "webp" -> pal().mediaSoft to pal().media
        else -> pal().surfaceAlt to pal().muted
    }
    Box(
        Modifier
            .size(dim.dp)
            .clip(RoundedCornerShape((dim / 3).dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            ext.take(4).uppercase(),
            fontSize = (dim * 0.26f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            color = fg,
        )
    }
}

/**
 * 产出文件快捷方式。放在消息流的最后一项 —— 跟着内容滚走，不占固定底栏。
 * 点一下交给系统「打开方式」，我们不做内置预览。
 */
@Composable
private fun ProducedFilesCard(files: List<ProducedFile>, onOpen: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        // 与 AI 气泡左缘对齐：26dp 头像 + 8dp 间距
        Spacer(Modifier.width(34.dp))
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(pal().card)
                .border(1.dp, pal().line, RoundedCornerShape(14.dp))
                .padding(10.dp),
        ) {
            if (files.size == 1) {
                val f = files.first()
                val ext = f.name.substringAfterLast('.', "")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(f.rel) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FileExtIcon(ext, 34)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            f.name,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pal().ink,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            "点击选择打开方式",
                            style = ty().micro,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(pal().accentSoft),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.CHEV_RIGHT, 14.dp, pal().accent, 2.2f) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph(G.FOLDER, 12.dp, pal().muted, 2f)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "本对话产出",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        color = pal().muted,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${files.size} 个文件", style = ty().micro)
                }
                Spacer(Modifier.height(7.dp))
                files.take(6).forEach { f ->
                    val ext = f.name.substringAfterLast('.', "")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpen(f.rel) }
                            .padding(vertical = 5.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FileExtIcon(ext, 26)
                        Spacer(Modifier.width(9.dp))
                        Text(
                            f.name,
                            fontSize = 12.5.sp,
                            color = pal().ink,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        Glyph(G.CHEV_RIGHT, 12.dp, pal().faint, 2.2f)
                    }
                }
                if (files.size > 6) {
                    Text(
                        "还有 ${files.size - 6} 个文件",
                        style = ty().micro,
                        modifier = Modifier.padding(start = 2.dp, top = 3.dp),
                    )
                }
            }
        }
    }
}

// ───────────────── 输入区 ─────────────────

@Composable
private fun Composer(
    modifier: Modifier = Modifier,
    modelLabel: String,
    thinkingLabel: String,
    thinkingEnabled: Boolean,
    thinkingColors: Pair<Color, Color>,
    permission: PermissionMode,
    hasModel: Boolean,
    /** 空 = 没选工作区。没工作区时只有终端与记忆工具可用，底部要如实说明 */
    workspaceName: String = "",
    /** Linux 环境就绪且开关打开：即使没选工作区，agent 也能跑命令 */
    shellReady: Boolean = false,
    streaming: Boolean,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPop: (Pop) -> Unit,
    refs: List<FileRef> = emptyList(),
    /** 当前对话已启用的技能：在输入区上方给一行小胶囊，随时能看到、能摘掉 */
    activeSkills: List<com.pocketagent.app.data.SkillMeta> = emptyList(),
    onRemoveSkill: (String) -> Unit = {},
    onAttach: () -> Unit = {},
    onRemoveRef: (Long) -> Unit = {},
    /** 非空表示当前引用了一条消息，内容为"发送者：前 40 字" */
    quoteLabel: String? = null,
    onClearQuote: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 10.dp)) {

        // 已启用的技能：贴在输入区上方一行胶囊。
        // 之前只在「+」上点了个小圆点，用户根本不知道"我现在是在按某份手册做事"；
        // 胶囊把这件事摊开，且能点 × 就地摘掉，不用再进菜单。
        if (activeSkills.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                activeSkills.forEach { sk ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(pal().accentSoft)
                            .padding(start = 9.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(G.SPARKLE, 11.dp, pal().accentInk, 2f)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            sk.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pal().accentInk,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(3.dp))
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .clickable { onRemoveSkill(sk.id) },
                            contentAlignment = Alignment.Center,
                        ) { Glyph(G.CLOSE, 9.dp, pal().accentInk, 2.4f) }
                    }
                }
            }
        }

        // 三等分：宽度一致、左缘对齐，文案长短不再让它们一宽一窄地抖。
        // 之前是"按文字撑开 + 横向滚动"，模型名一长整行就在滑，三颗永远对不齐。
        Row(
            Modifier.fillMaxWidth().padding(bottom = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PillChip(
                text = modelLabel,
                bg = if (hasModel) pal().modelChipBg else pal().surfaceAlt,
                fg = if (hasModel) pal().modelChipInk else pal().muted,
                leadingDot = if (hasModel) pal().ok else pal().faint,
                modifier = Modifier.weight(1f),
                onClick = { onPop(Pop.MODEL) },
            )
            PillChip(
                // "思考·关" → "思考 关"：同样的信息，少一个全角点，长档位少挤 12dp
                text = "思考 $thinkingLabel",
                bg = if (thinkingEnabled) thinkingColors.first else pal().surfaceAlt,
                fg = if (thinkingEnabled) thinkingColors.second else pal().muted,
                leadingGlyph = G.SPARKLE,
                enabled = thinkingEnabled,
                modifier = Modifier.weight(1f),
                onClick = { if (thinkingEnabled) onPop(Pop.THINK) },
            )
            val (bg, fg) = when (permission) {
                PermissionMode.READONLY -> pal().surfaceAlt to pal().ink
                PermissionMode.CONFIRM -> pal().permChipBg to pal().permChipInk
                PermissionMode.ALLOW -> pal().warnSoft to pal().warn
            }
            PillChip(
                // 权限档位同样精简："全部允许"→"允许"，盾牌图标已经把语义兜住了
                text = when (permission) {
                    PermissionMode.READONLY -> "只读"
                    PermissionMode.CONFIRM -> "需确认"
                    PermissionMode.ALLOW -> "允许"
                    else -> permission.label
                },
                bg = bg,
                fg = fg,
                leadingGlyph = G.SHIELD,
                modifier = Modifier.weight(1f),
                onClick = { onPop(Pop.PERMIT) },
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                // 层次靠 1dp 灰描边 + 底色差：elevation 阴影会在动画里被裁成黑硬边
                .clip(RoundedCornerShape(26.dp))
                .background(pal().card)
                .border(1.dp, pal().line, RoundedCornerShape(26.dp))
                .padding(horizontal = 9.dp, vertical = 8.dp),
        ) {
            // 引用的消息：贴在输入框最上方，带一个明确的移除按钮
            if (quoteLabel != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 7.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(pal().accentSoft)
                        .padding(start = 9.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "引用",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = pal().accentInk,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        quoteLabel,
                        fontSize = 11.5.sp,
                        color = pal().ink,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable { onClearQuote() },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.CLOSE, 11.dp, pal().muted, 2.2f) }
                }
            }

            // 引用文件以胶囊形式嵌在输入框内部
            if (refs.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    refs.take(4).forEach { r ->
                        // 图片给缩略图，文件仍是名字胶囊：缩略图本身就是"我选了哪张"的答案
                        if (r.isImage) ImageRefChip(r) { onRemoveRef(r.id) }
                        else RefChip(
                            name = r.name,
                            hasContent = r.content != null,
                            onRemove = { onRemoveRef(r.id) },
                        )
                    }
                    if (refs.size > 4) {
                        Text("+${refs.size - 4}", style = ty().caption)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
            // 不再在加号上点蓝点：启用技能后输入区上方已经有胶囊，重复标记只会让人
            // 以为加号本身变成了另一个入口
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(pal().surfaceAlt)
                    .clickable(onClick = onAttach),
                contentAlignment = Alignment.Center,
            ) { Glyph(G.PLUS, 17.dp, pal().ink, 2.2f) }

            Spacer(Modifier.width(9.dp))

            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    textStyle = ty().body,
                    cursorBrush = SolidColor(pal().accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    modifier = Modifier.fillMaxWidth(),
                    // 占位文字必须和正文共用**同一个 TextStyle**。
                    // 之前它只给了 fontSize = 14.5.sp，没有 ty().body 的 23sp 行高：
                    // 空输入时光标按 23sp 的行盒从顶端画，占位文字却按默认行高（约 17sp）居中，
                    // 两者基线差 6sp —— 表现就是"没输入时光标位置不准"；
                    // 一旦有字，光标贴着字形，反而对上了。
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty()) {
                                Text("继续问，或让它改文件", style = ty().body, color = pal().hint)
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.width(9.dp))

            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (hasModel) pal().accent else pal().surfaceAlt)
                    .clickable { if (streaming) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                // 禁用态不能沿用 onAccent：深色主题下 onAccent 是深墨蓝，落在近黑底上看不见
                val sendFg = if (hasModel) pal().onAccent else pal().muted
                if (streaming) Glyph(G.STOP, 14.dp, sendFg, 2.2f)
                else Glyph(G.ARROW_UP, 16.dp, sendFg, 2.4f)
            }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (workspaceName.isBlank()) "未选工作区" else "工作区 · $workspaceName",
                style = ty().micro,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 130.dp),
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(1.dp, 9.dp).background(pal().line))
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    !hasModel -> "先添加模型"
                    // 没选工作区但有终端：文件工具不发，run_shell 照常可用。
                    // 这里写"工具不可用"会让用户以为 agent 什么都不会做，实际它能跑命令。
                    workspaceName.isBlank() && shellReady -> "终端可用"
                    workspaceName.isBlank() -> "工具不可用"
                    permission == PermissionMode.READONLY -> "只读，不能改文件"
                    else -> "可读写文件"
                },
                style = ty().micro,
            )
        }
    }
}

/**
 * 思考档位的四段色阶：灰 → 青 → 蓝 → 琥珀。
 *
 * 单一灰色表达不出"我现在开的是最大档"，而档位直接影响响应速度和费用，
 * 需要一眼能分出强弱。四段是上限：再多颜色就记不住了。
 */
private data class ThinkTier(val bg: Color, val bgDark: Color, val fg: Color, val fgDark: Color)

private val THINK_TIERS = listOf(
    // 关 / 最小
    ThinkTier(Color(0xFFF1F3F6), Color(0xFF141414), Color(0xFF5F6368), Color(0xFFA3A3A3)),
    // 低
    ThinkTier(Color(0xFFE2F3EF), Color(0xFF122A26), Color(0xFF1B6B5F), Color(0xFF7FD1C1)),
    // 中
    ThinkTier(Color(0xFFE8F0FE), Color(0xFF1E2C3D), Color(0xFF12508F), Color(0xFFAECBFA)),
    // 高 / 最高
    ThinkTier(Color(0xFFFDF1DE), Color(0xFF2E2415), Color(0xFF8A5200), Color(0xFFE0B15C)),
)

/**
 * 当前档位落在哪一段。
 * 两档模型（如小米的开/关）里"开"不该顶到最重的琥珀色，按"中"处理；
 * 三档以上按索引等比映射，七档的 Agnes 也不会出现六个不同颜色的混乱。
 */
private fun thinkSlot(levelCount: Int, index: Int): Int = when {
    levelCount <= 1 -> 0
    levelCount == 2 -> if (index == 0) 0 else 2
    else -> (index * 3) / (levelCount - 1)
}

@Composable
private fun thinkChipColors(model: ModelProfile?, levelId: String?): Pair<Color, Color> {
    if (model == null || model.thinkingLevels.isEmpty()) {
        return pal().surfaceAlt to pal().muted
    }
    val idx = model.thinkingLevels.indexOfFirst { it.id == levelId }.coerceAtLeast(0)
    val t = THINK_TIERS[thinkSlot(model.thinkingLevels.size, idx)]
    return (if (pal().isDark) t.bgDark else t.bg) to (if (pal().isDark) t.fgDark else t.fg)
}

private fun thinkingLabel(model: ModelProfile?, vm: AppViewModel): String {
    if (model == null || model.thinkingLevels.isEmpty()) return "—"
    val id = vm.currentThinkingLevel(model)
    return model.thinkingLevels.firstOrNull { it.id == id }?.label ?: "—"
}

// ───────────────── 弹出层 ─────────────────

@Composable
private fun ModelPopover(vm: AppViewModel, onGoModels: () -> Unit, onClose: () -> Unit) {
    val all = vm.chatModelsWithProvider()
    val current = vm.state.value.selectedModelId
    Column {
        PopoverHead(
            if (all.isEmpty()) "还没有添加任何模型"
            else "切换模型 · 右侧是思考档位数"
        )
        val ctx = androidx.compose.ui.platform.LocalContext.current
        all.take(6).forEach { (p, m) ->
            val sel = m.id == current
            val def = remember(p.catalogId) { Catalog.find(ctx, p.catalogId) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (sel) pal().accentSoft else Color.Transparent)
                    .clickable { vm.select(m.id); onClose() }
                    .padding(start = 8.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PopoverRadio(sel)
                Spacer(Modifier.width(7.dp))
                // 厂商图标：一眼分清是哪家，比看一串 URL 快得多
                VendorMark(
                    slug = def?.icon?.ifBlank { null },
                    // 自定义厂商在目录里查不到，退回名字首字，别留一个空方块
                    fallbackText = def?.fallbackText?.takeIf { it.isNotBlank() }
                        ?: p.displayName.take(1).uppercase(),
                    fallbackColor = parseHex(def?.fallbackColor ?: "#5F6368"),
                    dim = 26.dp,
                    glyphDim = 15.dp,
                    round = false,
                    showBorder = false,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.displayName,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = pal().ink,
                        maxLines = 1,
                    )
                    Text(
                        m.modelId,
                        style = ty().monoId,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(6.dp))
                val tiers = m.thinkingLevels.size
                // 选中行本身是 accentSoft 底，档位标签再用 accentSoft 就"化"进背景里，
                // 看起来像边框被抹掉了。选中态改成白卡片 + 强调色细描边，跟行底拉开层次。
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) pal().card else pal().surfaceAlt)
                        .border(
                            1.dp,
                            if (sel) pal().accent.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        if (tiers == 0) "无档位" else "思考 $tiers 档",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sel) pal().accentInk else pal().muted,
                    )
                }
            }
        }
        if (all.isEmpty()) {
            // 空态不能只给一句话：用户点开这个弹窗就是为了选模型，必须给一条直达路径
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(pal().accent)
                    .clickable(onClick = onGoModels)
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "去添加模型",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal().onAccent,
                )
            }
        } else if (all.size > 6) {
            Text(
                "还有 ${all.size - 6} 个模型，去「模型」页查看全部",
                style = ty().micro,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp),
            )
        }
        Text(
            "切换后思考档位与工具能力按新模型重置",
            fontSize = 10.sp,
            color = pal().faint,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ThinkPopover(vm: AppViewModel, onClose: () -> Unit) {
    val model = vm.selectedModel() ?: return
    val levels = model.thinkingLevels
    if (levels.isEmpty()) return
    val currentId = vm.currentThinkingLevel(model)

    Column {
        PopoverHead("思考程度 · ${model.displayName} 支持 ${levels.size} 档")

        levels.forEachIndexed { idx, lvl ->
            val sel = lvl.id == currentId
            val slot = THINK_TIERS[thinkSlot(levels.size, idx)]
            val dot = if (pal().isDark) slot.fgDark else slot.fg
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (sel) pal().accentSoft else Color.Transparent)
                    .clickable {
                        vm.setThinking(model.id, lvl.id)
                        onClose()
                    }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PopoverRadio(sel)
                Spacer(Modifier.width(10.dp))
                // 档位色点：同一套色阶，列表里也能一眼分出强弱
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(dot)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    lvl.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal().ink,
                )
                Spacer(Modifier.weight(1f))
                Text(lvl.param, style = ty().monoId)
            }
        }

        Text(
            "档位由模型能力决定，不支持的档位不会出现；切换后立即生效",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = pal().faint,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}



@Composable
private fun PermitPopover(vm: AppViewModel, onClose: () -> Unit) {
    val cur = vm.permission()
    val items = listOf(
        Triple(PermissionMode.READONLY, "只读", "只能读与搜。写、删、移动工具直接从工具表移除"),
        Triple(PermissionMode.CONFIRM, "需确认", "写入与删除每次都弹确认并展示 diff"),
        Triple(PermissionMode.ALLOW, "全部允许", "写与删自动执行。仍先拍快照，可在对话里逐次撤销"),
    )
    Column {
        PopoverHead("访问权限 · 仅作用于当前会话")
        items.forEach { (mode, title, desc) ->
            val sel = mode == cur
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (sel) pal().accentSoft else Color.Transparent)
                    .clickable { vm.setPermission(mode); onClose() }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.padding(top = 1.dp)) { PopoverRadio(sel) }
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
                    Text(desc, fontSize = 10.5.sp, lineHeight = 15.sp, color = pal().hint)
                }
            }
        }
        Text(
            "越界路径、备份失败、文件被外部改动，这三种情况即使「全部允许」也硬拦截",
            fontSize = 10.sp,
            lineHeight = 15.sp,
            color = pal().faint,
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

/**
 * 「+」的菜单：相机 / 相册 / 文件。
 *
 * 按**来源**分类而不是按文件类型分 —— 用户想的是"我现在要拍 / 要选 / 要找个文件"，
 * 而不是 image/jpeg 还是 text/markdown。三项的顺序也按这个心智走：最常用的拍照在最上。
 */
@Composable
private fun AttachMenu(
    skills: List<com.pocketagent.app.data.SkillMeta>,
    activeSkills: Set<String>,
    onToggleSkill: (String) -> Unit,
    vision: Boolean,
    /** 模型表声明它只吃文本 */
    declaredTextOnly: Boolean,
    /** 模型表里没有这个模型 */
    unverified: Boolean,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
) {
    Column {
        AttachRow(G.CAMERA, pal().accent, "相机", "拍完直接引用", onCamera)
        AttachRow(G.IMAGE, pal().media, "相册", "可多选 · 最多 4 张", onGallery)
        AttachRow(G.DOC, pal().ok, "文件", "文本 / 代码 / 文档", onFile)

        // ── 技能 ──
        // 放在同一个「+」里：附件和技能都是"给这一轮补充上下文"，
        // 分成两个入口只会让用户猜哪个在哪儿
        if (skills.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Hairline()
            // 默认折叠：技能只是"可选的工作方式"，不该一打开附件菜单就占掉半屏。
            // 折叠时只留一行标题 + 已启用数量，点开才铺列表。
            var showSkills by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { showSkills = !showSkills }
                    .padding(start = 9.dp, end = 6.dp, top = 9.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(G.SPARKLE, 13.dp, pal().muted, 2f)
                Spacer(Modifier.width(6.dp))
                Text(
                    "技能",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    color = pal().ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        activeSkills.isEmpty() -> skills.size.toString() + " 个 · 点开选择"
                        else -> "已启用 " + activeSkills.size + " 个"
                    },
                    style = ty().micro,
                )
                Spacer(Modifier.width(4.dp))
                Glyph(
                    G.CHEV_DOWN, 12.dp, pal().faint, 2.2f,
                    Modifier.graphicsLayer { rotationZ = if (showSkills) 0f else -90f },
                )
            }
            if (!showSkills) return@Column
            // 小屏兜底：展开后技能多了也不该把弹层顶出屏幕上沿，超过 220dp 内部滚
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                skills.forEach { sk ->
                    SkillRow(
                        meta = sk,
                        active = sk.id in activeSkills,
                        onClick = { onToggleSkill(sk.id) },
                    )
                }
            }
        }
        // 只写在菜单里**确定**的事：关着会拦、开着会发、未验证则如实说不确定。
        // 不再按模型名猜"看着像纯文本" —— 猜错了比不提示更容易误导。
        PopoverFoot(
            when {
                !vision -> "该模型已设为「图片·已关闭」，选图后发送会被拦下；可在模型编辑页改回「已开启」"
                declaredTextOnly -> "模型表显示它只支持文本：发图很可能被拒，报错就进模型编辑页设为「已关闭」"
                unverified -> "模型表里没有这个模型，图片能力未知。图片会以 base64 发出（长边 ≤1024，最多 4 张）"
                else -> "图片会以 base64 附带在这条消息里发送（长边 ≤1024，最多 4 张）"
            }
        )
    }
}

/**
 * 技能清单里的一行。
 *
 * 右侧给对勾而不是开关：它不是一个"设置项"，而是"这一轮要不要照这份手册做"，
 * 点一下即切换，不需要二次确认（随时能点回去）。
 */
@Composable
private fun SkillRow(
    meta: com.pocketagent.app.data.SkillMeta,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) pal().accentSoft.copy(alpha = 0.45f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (active) pal().accent.copy(alpha = 0.18f)
                    else pal().surfaceAlt
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                meta.name.take(1),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) pal().accent else pal().muted,
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meta.name,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal().ink,
                    maxLines = 1,
                )
                if (meta.hasScripts) {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "含脚本",
                        fontSize = 9.sp,
                        color = pal().faint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(pal().surfaceAlt)
                            .padding(horizontal = 3.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                meta.summary,
                style = ty().micro,
                // 单行：技能条目只做"是什么"的提示，细节进详情页看，别在这里占两行高度
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        if (active) Glyph(G.CHECK, 14.dp, pal().accent, 2.4f)
        else Glyph(G.PLUS, 13.dp, pal().faint, 2.2f)
    }
}

@Composable
private fun AttachRow(
    icon: G,
    tint: Color,
    title: String,
    desc: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = if (pal().isDark) 0.20f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, 17.dp, tint, 2f)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
            Text(desc, fontSize = 10.5.sp, color = pal().hint)
        }
    }
}

/**
 * 图片引用：42dp 缩略图 + 右上角删除。
 *
 * 用缩略图而不是文件名，是因为"IMG_2043.jpg"对用户没有任何信息量 ——
 * 他自己也分不清哪张是哪张，只有缩略图能让他确认"我选对了"。
 */
@Composable
private fun ImageRefChip(ref: FileRef, onRemove: () -> Unit) {
    val bmp = remember(ref.id) { ref.imageUrl?.let { decodeDataUrl(it) } }
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(pal().surfaceAlt),
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = ref.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Glyph(G.IMAGE, 16.dp, pal().hint, 2f)
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(3.dp)
                .size(15.dp)
                .clip(CircleShape)
                .background(Color(0x8F14181E))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.CLOSE, 9.dp, Color.White, 2.6f) }
    }
}

/** data URL → Bitmap，只给 42dp 缩略图用；解不出来就退回图标，不抛异常 */
private fun decodeDataUrl(url: String): android.graphics.Bitmap? {
    val b64 = url.substringAfter("base64,", "")
    if (b64.isEmpty()) return null
    val bytes = runCatching { android.util.Base64.decode(b64, android.util.Base64.DEFAULT) }
        .getOrNull() ?: return null
    return runCatching {
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/**
 * 侧边栏面板本体。由 AppRoot 渲染（不是 ChatScreen），
 * 这样进出二级页时它只是被整体平移，不会被销毁重建 —— 搜索词、滚动位置都还在。
 *
 * 定位是**会话切换器**，不是导航：底下只剩一行「设置」通到全部功能页。
 * 导出 / 清空只作用于当前这段会话，所以收在搜索行尾部的「⋯」里，
 * 既不占常驻行高，也不会跑到设置页去（作用域不同）。
 */
@Composable
internal fun DrawerPanel(
    modelCount: Int,
    /** 抽屉是否展开：只用来在收起时把「⋯」菜单一起清掉 */
    drawerOpen: Boolean,
    sessions: List<com.pocketagent.app.data.Session>,
    currentSessionId: String,
    collapsedGroups: Set<com.pocketagent.app.data.SessionGroup>,
    onToggleGroup: (com.pocketagent.app.data.SessionGroup) -> Unit,
    onNewChat: () -> Unit,
    onOpenSession: (String) -> Unit,
    onRequestDelete: (String, String) -> Unit,
    onClose: () -> Unit,
    onRequestClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    /** 「⋯」菜单：只有导出和清空这两件事，作用域都是"当前这段会话" */
    var menuOpen by remember { mutableStateOf(false) }
    val lineColor = pal().line
    val density = androidx.compose.ui.platform.LocalDensity.current

    /**
     * 搜索行底边到抽屉根的高度。
     *
     * 弹层要贴在搜索框正下方。这个位置 = 状态栏 + 品牌区 + 新会话 + 搜索 四段之和，
     * 写死一个 dp 会随字体缩放和状态栏高度漂；量一次最稳。
     */
    var searchBottomPx by remember { mutableStateOf(0f) }

    // 抽屉收起时顺手关掉菜单：否则下次拉出来，上次没收的弹层还挂在那儿
    androidx.compose.runtime.LaunchedEffect(drawerOpen) {
        if (!drawerOpen) menuOpen = false
    }

    Box(
        Modifier
            .width(302.dp)
            .fillMaxHeight()
            .background(pal().card)
            // 原来用 12dp 投影做层次，会触发"黑边闪"。抽屉是贴屏幕左侧的整块面板，
            // 只需要在右缘画一条 1dp 灰线把它和遮罩分开（其余三边都在屏幕外）
            .drawBehind {
                drawLine(
                    color = lineColor,
                    start = Offset(size.width - 0.5f, 0f),
                    end = Offset(size.width - 0.5f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // 这块 spacer 让白底一直铺到状态栏底下，同时把内容推到状态栏下方
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // ── 品牌区：让抽屉顶部有"这是哪个应用"的锚点 ──
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 与桌面图标同一份矢量：界面内标识必须和用户看到的 App 图标是同一个
                Image(
                    painter = androidx.compose.ui.res.painterResource(com.pocketagent.app.R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Pocket Agent", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
                    Text(
                        if (modelCount > 0) "$modelCount 个模型可用" else "还没有模型 · 去设置里添加",
                        fontSize = 11.5.sp,
                        color = pal().muted,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                SquareIcon(G.CLOSE, onClose)
            }

            // 新会话：抽屉里最该被一眼看到的主操作
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pal().accent)
                    .clickable(onClick = onNewChat),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Glyph(G.PLUS, 15.dp, pal().onAccent, 2.4f)
                Spacer(Modifier.width(7.dp))
                Text(
                    "新会话",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pal().onAccent,
                )
            }

            Spacer(Modifier.height(10.dp))

            // 搜索：真输入框。尾部那个「⋯」是当前会话的操作入口（导出 / 清空）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pal().surfaceAlt)
                    .onGloballyPositioned { c ->
                        searchBottomPx = c.positionInParent().y + c.size.height
                    }
                    .padding(start = 13.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(G.SEARCH, 15.dp, pal().hint, 2.1f)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("搜索会话", fontSize = 13.5.sp, color = pal().hint)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.5.sp,
                            color = pal().ink,
                        ),
                        cursorBrush = SolidColor(pal().accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(pal().line.copy(alpha = 0.55f))
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph(G.CLOSE, 10.dp, pal().muted, 2.2f)
                    }
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = !menuOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    // 开着的时候用强调色：让"菜单是从这儿弹出来的"有明确指向，
                    // 否则点完弹层盖住了列表，用户找不到是哪个按钮开的
                    Glyph(G.MORE, 15.dp, if (menuOpen) pal().accent else pal().hint, 2f)
                }
            }

            // 搜索词同时匹配标题与正文，避免"记得聊过但忘了标题"时搜不到
            val q = query.trim()
            // 「普通会话」与「工作区」各自成组，组内独立按最近收发倒序。
            // 排序不依赖存储里数组的顺序：即便某次落盘顺序有偏差，看到的也永远是最新在最上面
            val groups = remember(sessions, q) {
                val base = if (q.isBlank()) sessions
                else sessions.filter { s ->
                    s.title.contains(q, true) || s.messages.any { it.text.contains(q, true) }
                }
                base.groupedByRecent()
            }

            Spacer(Modifier.height(6.dp))

            if (groups.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        if (q.isBlank()) "还没有历史会话" else "没有匹配「$q」的会话",
                        style = ty().caption,
                    )
                }
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    groups.forEach { (group, rows) ->
                        // 搜索时把列表当成"平铺的命中结果"：一律展开，也不提供折叠
                        val collapsible = q.isBlank()
                        val collapsed = collapsible && group in collapsedGroups
                        item(key = "h-${group.name}") {
                            SessionGroupHeader(
                                label = group.label,
                                hint = rows.singleWorkspaceName(),
                                count = rows.size,
                                collapsed = collapsed,
                                collapsible = collapsible,
                                onToggle = { onToggleGroup(group) },
                            )
                        }
                        if (!collapsed) {
                            items(rows, key = { it.id }) { s ->
                                SessionRow(
                                    title = s.title.ifBlank { "新会话" },
                                    time = sessionTime(s.updatedAt),
                                    active = s.id == currentSessionId,
                                    onClick = { onOpenSession(s.id) },
                                    onLongClick = { onRequestDelete(s.id, s.title.ifBlank { "新会话" }) },
                                )
                            }
                        }
                    }
                }
            }

            Hairline(Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            // 唯一常驻入口：设置。抽屉不再承载导航，功能页一律从设置里钻进去。
            // 用中性灰而不是强调色 —— 它是"退路"，不该跟上面的新会话按钮抢注意力。
            DrawerRow(G.SETTINGS, "设置", "", pal().muted, onClick = onOpenSettings)

            // 底部 spacer：白底覆盖到手势条/三键导航栏底下，内容不被小白条压住
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(6.dp))
        }

        if (menuOpen) {
            // 空白处点一下只收菜单，不关抽屉 —— 和 scrim 的既有约定保持一致
            // （只有 X 和返回手势能关抽屉）
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { menuOpen = false },
                    )
            )
            // 宽度收到 190dp：322dp 的默认弹层会顶出 302dp 的抽屉
            PopoverSurface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(start = 14.dp, end = 14.dp, top = with(density) { searchBottomPx.toDp() } + 4.dp),
                width = 190.dp,
            ) {
                DrawerActionItem(
                    icon = G.ARROW_DOWN,
                    tint = pal().accent,
                    title = "导出会话",
                    trailing = "Markdown",
                ) {
                    menuOpen = false
                    onExport()
                }
                DrawerActionItem(
                    icon = G.TRASH,
                    tint = pal().bad,
                    title = "清空当前会话",
                    danger = true,
                ) {
                    menuOpen = false
                    onRequestClear()
                }
            }
        }
    }
}

/**
 * 弹层里的一个动作行。
 *
 * 不复用 [PopoverItem]：那个是"单选"语义（左边一个圆点），这里是一次性动作，
 * 用图标块 + 文字才对得上用户在抽屉里看到的那套行样式。
 */
@Composable
private fun DrawerActionItem(
    icon: G,
    tint: Color,
    title: String,
    trailing: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = if (pal().isDark) 0.20f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, 13.dp, tint, 2f)
        }
        Spacer(Modifier.width(9.dp))
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (danger) pal().bad else pal().ink,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, fontSize = 10.5.sp, color = pal().faint)
        }
    }
}

/**
 * 分组头：「普通会话」/「工作区」。
 *
 * 两组共用同一个实现，所以折叠的点击范围、动画时长、计数样式天然一致 ——
 * 用户不需要为两个分类记住两套交互。收起后列表项直接从 LazyColumn 里移除，
 * 而不是画一遍再透明掉：省重组，也不会出现"收起来了还占着高度"。
 */
@Composable
private fun SessionGroupHeader(
    label: String,
    hint: String?,
    count: Int,
    collapsed: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
) {
    // 箭头从"朝下"转到"朝右"，两组用同一个时长，手感一致
    val angle by animateFloatAsState(if (collapsed) -90f else 0f, tween(160), label = "groupChevron")
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(10.dp))
            // 搜索时列表是"平铺结果"，此时不给折叠交互 ——
            // 让标题看着能点、点了却没反应，比不能点更让人困惑
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(start = 5.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (collapsible) {
            Glyph(
                G.CHEV_DOWN, 13.dp, pal().muted, 2.2f,
                Modifier.graphicsLayer { rotationZ = angle },
            )
        } else {
            // 占住同样的宽度，标题的起始位置不会因为有没有箭头而跳动
            Spacer(Modifier.width(13.dp))
        }
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            color = pal().muted,
        )
        if (hint != null) {
            // 工作区名跟着标题走：一眼知道这组会话是在哪个目录里聊的
            Spacer(Modifier.width(6.dp))
            Text(
                hint,
                fontSize = 10.5.sp,
                color = pal().faint,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        // 计数在收起时也保留：否则收起来就不知道藏了几条
        Text("$count", fontSize = 10.5.sp, color = pal().faint)
    }
}

/**
 * 组内会话都指向同一个工作区时，把这个目录名放到组标题后面。
 * 多个工作区时名字对不上任何一个，返回 null（硬写一个只会误导）。
 */
private fun List<com.pocketagent.app.data.Session>.singleWorkspaceName(): String? =
    map { it.workspaceName.trim() }.filter { it.isNotEmpty() }.distinct().singleOrNull()

/** 历史会话的一行。长按由 AppRoot 弹二次确认（删除是破坏性操作，不能一次点掉） */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    title: String,
    time: String,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) pal().accentSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                color = pal().ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (time.isNotBlank()) {
                Text(time, style = ty().caption, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

/** 会话时间的口语化写法：今天只说"几分钟前"，跨年才补年份 */private fun sessionTime(ts: Long): String {
    if (ts <= 0L) return ""
    val diff = System.currentTimeMillis() - ts
    if (diff < 60_000L) return "刚刚"
    if (diff < 3_600_000L) return "${diff / 60_000L} 分钟前"
    if (diff < 86_400_000L) return "${diff / 3_600_000L} 小时前"
    if (diff < 172_800_000L) return "昨天"
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
    val now = java.util.Calendar.getInstance()
    val md = "${c.get(java.util.Calendar.MONTH) + 1} 月 ${c.get(java.util.Calendar.DAY_OF_MONTH)} 日"
    return if (c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)) md
    else "${c.get(java.util.Calendar.YEAR)} 年 $md"
}

@Composable
private fun RefChip(name: String, hasContent: Boolean, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(pal().surfaceAlt)
            .padding(start = 8.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.FOLDER, 12.dp, pal().hint, 1.9f)
        Spacer(Modifier.width(5.dp))
        Text(
            name + if (hasContent) "" else "（仅文件名）",
            fontSize = 11.sp,
            color = if (hasContent) pal().ink else pal().hint,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 132.dp),
        )
        Spacer(Modifier.width(3.dp))
        Box(
            Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.CLOSE, 9.dp, pal().hint, 2.2f) }
    }
}

/** "1,284 → 312 tok · 缓存 68% · 42.3 tok/s"，缺项自动省略 */
/**
 * 用量行：只给"消耗了多少"，不再拆输入/输出。
 *
 * 拆开写对用户没有决策价值 —— 他要的是"这一轮花了多少"。输入/输出仍然躺在
 * usage 里（账单页按用途/模型统计要用），只是不占用这一行。
 * `≈` 表示厂商没上报 usage、这两个数是我们按字符估的，必须标出来。
 */
private fun usageText(u: com.pocketagent.app.data.Usage): String {
    val sb = StringBuilder()
    if (u.estimated) sb.append("≈ ")
    sb.append("消耗 ").append(
        com.pocketagent.app.data.UsageStats.compact(u.totalTokens.toLong())
    ).append(" tok")
    u.speed?.let { sb.append(" · ").append(String.format("%.1f", it)).append(" tok/s") }
    // 缓存命中率固定占一位：厂商没下发 cached_tokens 时写"—"，
    // 而不是把这一项藏起来（否则用户会以为没统计数据）
    sb.append(" · 缓存 ").append(u.cacheRate?.let { "${(it * 100).toInt()}%" } ?: "—")
    return sb.toString()
}

private fun fmtInt(n: Int): String = java.text.NumberFormat.getIntegerInstance().format(n)

/** 消息时间戳 → HH:mm（按本地时区）。0 视为"旧存档没有时间" */
private fun clockOf(ms: Long): String {
    if (ms <= 0L) return ""
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(
        java.util.Locale.CHINA, "%02d:%02d",
        c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE),
    )
}

/** 毫秒 → "42 秒 / 1 分 12 秒 / 1 时 05 分" */
private fun durText(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s} 秒"
        s < 3600 -> "${s / 60} 分 ${s % 60} 秒"
        else -> "${s / 3600} 时 ${String.format(java.util.Locale.CHINA, "%02d", (s % 3600) / 60)} 分"
    }
}

@Composable
private fun DrawerRow(
    icon: G,
    title: String,
    trailing: String,
    tint: Color = Color.Unspecified,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = if (tint == Color.Unspecified) pal().muted else tint
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(c.copy(alpha = if (pal().isDark) 0.20f else 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(icon, 15.dp, c, 2f)
        }
        Spacer(Modifier.width(11.dp))
        Text(
            title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (danger) pal().bad else pal().ink,
        )
        Spacer(Modifier.weight(1f))
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                fontSize = 11.5.sp,
                color = pal().muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

/**
 * 真正滚到最底部。
 *
 * 只调 scrollToItem(lastIndex) 是不够的：它保证的只是"最后一条的**顶部**贴到视口顶部"。
 * 当最后一条比屏幕还高时（长回复很常见），页面会停在这条消息的中间 ——
 * 表现就是"明明在会话底部，键盘弹起后却没到底；点『回到底部』也回不去，手动再滑一下才行"。
 *
 * 所以在定位到最后一条之后，再用一个足够大的 scrollBy 把剩下的距离补完：
 * scrollBy 会被滚动范围截断，不会越界，正好停在真正的底部。
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToVeryBottom() {
    // 首帧时列表还没测量过，layoutInfo.totalItemsCount 是 0。
    // 以前这里直接 return，于是「启动后进入当前会话」「切换会话」这两种情况
    // 一次都没滚成 —— 列表停在最后一条的**顶部**（长回复看着就像停在中间），
    // 要等用户自己滑一下才到底。这里改成等第一次测量完成再动手。
    // 加超时兜底：消息为空时 LazyColumn 根本没被组合，别把协程一直挂着。
    if (layoutInfo.totalItemsCount == 0) {
        withTimeoutOrNull(2_000) {
            snapshotFlow { layoutInfo.totalItemsCount }.first { it > 0 }
        }
    }
    val total = layoutInfo.totalItemsCount
    if (total == 0) return
    // 两步各做一次，最多两轮：内容在同一帧继续变高（逐字、工具卡展开）时，
    // 第一次 scrollBy 只能滚到"当时算出来的底部"，差的那一截会让用户以为没跟到底
    repeat(2) {
        scrollToItem(layoutInfo.totalItemsCount - 1)
        scrollBy(400_000f)
        if (!canScrollForward) return
    }
}

/**
 * 「回到底部」浮标。
 *
 * 只在用户真的往上翻走（最后一条离开视口）时出现 —— 常驻会变成噪音，
 * 而且它压着的正是聊天气泡，平时不该占那块位置。
 * 样式对齐输入区那套「胶囊 + 1dp 描边 + 浅投影」，同一页面不出现第二种按钮语言。
 */
@Composable
private fun JumpToBottomPill(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(pal().card)
            .border(1.dp, pal().line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(start = 11.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(G.ARROW_DOWN, 13.dp, pal().accent, 2.2f)
        Spacer(Modifier.width(5.dp))
        Text(
            "回到底部",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = pal().accent,
        )
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit) {
    // 跟随主题的信息浮层：用 accentSoft 而不是中性灰 ——
    // 中性灰会和下方三个控件胶囊同色，看起来像「第四条胶囊」，分不出层级。
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(pal().accentSoft)
            .border(1.dp, pal().accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .clickable(onClick = onDismiss)
            .padding(start = 12.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 图标用主题强调色，让提示条有语义锚点又不会跳出整体配色
        Glyph(G.INFO, 15.dp, pal().accent, 2f)
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = pal().ink,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        // 独立胶囊：文字换行到两行时，「知道了」不会跟正文挤在同一行末尾
        Text(
            "知道了",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = pal().onAccent,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(pal().accent)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
