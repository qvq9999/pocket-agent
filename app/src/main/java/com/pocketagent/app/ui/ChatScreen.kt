package com.pocketagent.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.focus.onFocusChanged

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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
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

private enum class Pop { NONE, MODEL, THINK, PERMIT, ATTACH, MENU }

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
    /** 导出当前会话（Markdown），由 AppRoot 拉起系统文件选择器 */
    onExport: () -> Unit,
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
    // 引导页点的示例句子：那边够不到这里的 state，走一次性的"待填草稿"，
    // 消费完立刻清空，不会重复填
    LaunchedEffect(vm.pendingInput) {
        vm.pendingInput?.let {
            input = it
            vm.pendingInput = null
        }
    }
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
    /** 顶栏 ⋯ 里的"删除这段会话"要二次确认：删了不可恢复 */
    var confirmDeleteSession by remember { mutableStateOf(false) }
    /** 输入框右下角上下文百分比点开的明细 */
    var showCtxDetail by remember { mutableStateOf(false) }

    // ── 弹层锚点：贴在被点的那颗胶囊下面，而不是固定右下角 ──
    // composerH 是输入区**实测**高度（含 ime/nav 内边距），所以键盘弹起时弹层自然跟着上去；
    // chipX 是每颗胶囊的窗口横坐标，思考胶囊宽度会随档位名变化，只能量不能算。
    var composerH by remember { mutableStateOf(0) }
    var chipX by remember { mutableStateOf<Map<Pop, Int>>(emptyMap()) }
    var rootW by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val popWidth = if (lastPop == Pop.ATTACH) 264.dp else 248.dp
    val popStartDp = with(density) {
        val anchorPx = chipX[lastPop] ?: 12.dp.roundToPx()
        // 靠右的胶囊（权限）会让 248dp 弹层顶出屏幕，夹回可见范围内
        val maxPx = (rootW - popWidth.roundToPx() - 8.dp.roundToPx()).coerceAtLeast(0)
        anchorPx.coerceIn(0, maxPx).toDp()
    }
    val popBottomDp = with(density) { composerH.toDp() } + 6.dp

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

    // 键盘状态：弹出层要跟着键盘抬高（否则被输入法盖住），聊天列表要在键盘弹起后重新贴底
    val imeVisible = WindowInsets.isImeVisible

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
            .onGloballyPositioned { rootW = it.size.width }
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏两行：第一行是**这段会话的标题**（品牌名对用户没有信息量，挪进抽屉），
            // 第二行是工作区——它决定"能不能碰文件"，是最该常驻的一条状态，点一下就能换。
            run {
                val cur = persisted.sessions.firstOrNull { it.id == persisted.currentSessionId }
                val wsName = vm.workspace?.name.orEmpty()
                val permText = when (permission) {
                    PermissionMode.READONLY -> "只读"
                    PermissionMode.CONFIRM -> "需确认"
                    else -> "可读写"
                }
                TopBar(
                    title = cur?.title?.ifBlank { null } ?: "新会话",
                    subtitle = when {
                        vm.workspaceBroken -> "需重新授权"
                        wsName.isBlank() -> "选择工作区 · 才能读写文件"
                        else -> "$wsName · $permText"
                    },
                    subtitleColor = when {
                        vm.workspaceBroken -> pal().warn
                        wsName.isBlank() -> pal().warn
                        else -> null
                    },
                    onMenu = onOpenDrawer,
                    onSubtitleClick = onPickWorkspace,
                    trailing = {
                        // 顶栏只留一个入口：会话级动作都收进这里，避免再出现第二个"＋"
                        // （输入框左边那个 ＋ 是附件，同图标两种含义最容易点错）
                        SquareIcon(G.MORE, onClick = { pop = Pop.MENU })
                    },
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { selEpoch++ })
                    }
            ) {
                if (vm.messages.isEmpty()) {
                    // 引导已经独立成页（AppRoot 的 Screen.Onboard），会话页只留"你好 + 示例"。
                    // 以前这里挂一张引导卡：和示例 chip、版本号挤在同一屏，内容一多就要滚，
                    // 而且看起来像一块广告位，不像一段流程。
                    EmptyState(
                        modelName = model?.displayName,
                        hasWorkspace = vm.workspace != null && !vm.workspaceBroken,
                        onPick = { s -> input = s },
                    )
                } else {
                    // 一段会话一份列表状态。LazyListState 是按位置记忆的，沿用到另一段会话
                    // 就会从上一段的位置起步、再一路滚回末尾 —— 历史一长就是肉眼可见的抽搐。
                    // 用 sessionEpoch 当 key：切换会话时整块重建，滚动状态从零开始。
                    key(vm.sessionEpoch) {
                        /*
                         * 列表是**底部锚定**的（下面 LazyColumn 的 reverseLayout）：
                         * index 0 = 最新一条，贴着视口底边，更早的内容往上排
                         * （数据在 buildChatRows(...) 之后整体翻转）。
                         *
                         * 为什么非反过来不可：原来是"正着排 + 发现长高了再滚回底部"。
                         * 正文每 60ms、思考每 80ms 提交一次，内容在**这一帧**长高，
                         * 补滚动最快也要**下一帧**才落地 —— 于是每次提交都有一帧是错位的，
                         * 60fps 下就是每秒十几回"顶上去再拉回"，正是用户说的
                         * "生成时气泡上下抖"。锚定之后新增内容只往上顶，视口不动，
                         * 也就不存在"追"这个动作，抖动从根上没有了。
                         *
                         * 附带好处：初始位置天然就在底部，切会话不再需要"先渲染顶部
                         * 再滚下来"的补定位，键盘弹起把视口压矮也不需要重新贴底。
                         */
                        val listState = rememberLazyListState()
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
                         * 锚定布局下这就是"index 0 且贴着底边"，一句话判完：
                         * 不看 canScrollForward（方向被 reverseLayout 翻转过，容易判反），
                         * 也不看"最后一条是否完整可见"（长回复永远不完整）。
                         * 而且它只在**用户真的滚动**时才变，流式长高不会让它来回翻，
                         * 所以可以放心驱动浮标显隐和底部让位 padding。
                         */
                        val atBottom by remember(listState) {
                            derivedStateOf {
                                listState.layoutInfo.totalItemsCount > 0 &&
                                    listState.firstVisibleItemIndex == 0 &&
                                    listState.firstVisibleItemScrollOffset == 0
                            }
                        }

                        /**
                         * 是否跟随最新内容。
                         *
                         * 锚定布局下"跟随"**不需要任何滚动动作** —— 新长出来的内容自己
                         * 会往上顶，视口始终停在底部。这里只用来记用户意图：他往上翻看
                         * 历史时，新消息不要把他拽回底部。
                         */
                        var autoFollow by remember(listState) { mutableStateOf(true) }

                        val dragging by listState.interactionSource.collectIsDraggedAsState()
                        LaunchedEffect(dragging) {
                            if (dragging) autoFollow = false
                        }

                        // 用户自己翻回底部 = 重新跟随。atBottom 只在布局真的到底时为真，
                        // 不会被程序滚动误判（以前"滚动一停就读 atBottom"会把自己关死）
                        LaunchedEffect(atBottom) {
                            if (atBottom) autoFollow = true
                        }

                        /**
                         * 手指是否按在列表上（**只观察、不消费**事件）。
                         *
                         * 一次点按从 down 到 up 中间隔着几十毫秒，而这段时间里流式内容
                         * 还在往上顶；卡片跟着移动，up 就落到别的节点上 —— Compose 判定
                         * down/up 不是同一个可点击节点时直接取消这次点击，用户那边就是
                         * "点了没反应 / 打不开"。所以按住期间把正文/思考的提交冻住，
                         * 抬手再继续（见 vm.holdStream）。
                         */
                        var pointerDown by remember(listState) { mutableStateOf(false) }
                        LaunchedEffect(pointerDown) { vm.holdStream(pointerDown) }


                        // 新消息插在 index 0（也就是底部）时，把锚点放回 0。
                        //
                        // 只盯**条数**：逐字长高、思考增长、工具卡展开都不改条数，
                        // 所以这里一轮任务只会被叫醒几次，而不是每 60ms 一次 ——
                        // 原来那种"每次增量都补一次滚动"正是抖动的来源。
                        var seenCount by remember { mutableStateOf(vm.messages.size) }

                        LaunchedEffect(listState) {
                            snapshotFlow {
                                vm.messages.size to vm.messages.lastOrNull()?.role
                            }.collect { (count, role) ->
                                val appended = count != seenCount
                                seenCount = count
                                if (!appended || count == 0) return@collect
                                // 自己发出去的消息一定贴底并恢复跟随；
                                // 模型那侧的追加（新工具卡、新一轮草稿）只在跟随态才拽回去，
                                // 用户正翻历史时不要把他拖走
                                if (role == "user") autoFollow = true
                                if (autoFollow) listState.scrollToItem(0)
                            }
                        }


                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                // 底部锚定：index 0 在最下面，新内容往上顶（数据已翻转）
                                reverseLayout = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    // 只观察、不消费：Initial pass 先看一眼"有没有手指按着"，
                                    // 事件照常发给列表和卡片，点击/拖拽行为完全不受影响。
                                    .pointerInput(listState) {
                                        awaitEachGesture {
                                            // 不消费任何事件：只是"看一眼有没有手指按着"，
                                            // 点击与滚动照常交给列表和卡片。
                                            awaitFirstDown(requireUnconsumed = false)
                                            pointerDown = true
                                            while (true) {
                                                if (awaitPointerEvent().changes.none { it.pressed }) break
                                            }
                                            pointerDown = false
                                        }
                                    },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 10.dp,
                                    // **恒定**留白。以前是 if (autoFollow) 10 else 58：
                                    // 用户一拖拽 autoFollow 就翻成 false，内容高度当场变 48dp，
                                    // 列表于是又能多滚一截 —— 表现就是"滑到底部还能滚出一片空白"。
                                    // 浮标改为直接浮在内容上（它有实底 + 描边），不再靠让位留白。
                                    bottom = 10.dp,
                                ),
                            ) {
                                // 按「轮」聚合：一次回复 = 一个气泡（正文 + 工具调用 + 用量）。
                                // 之前是一条消息一行，一次工具调用就多一张卡，用户问一句冒出四五个气泡。
                                // 列表是底部锚定的（reverseLayout），所以这里要把「最新在末尾」
                                // 翻成「最新在 index 0」；每个 Turn 内部仍然是思考→正文→工具的阅读顺序。
                                val rows = buildChatRows(vm.messages).reversed()
                                val produced = collectProduced(vm.messages)

                                // 翻转之后，"最下面一项"要排在最前面写。
                                // 顺序（从底往上）：撤销提示 → 本对话产出 → 各轮消息。

                                // 首个任务真改过文件之后的一次性提示：用户刚看到"改好了"，
                                // 此刻他最需要知道的就是"改坏了怎么办"。
                                if (vm.undoHint) {
                                    item(key = "undoHint") { UndoHintRow { vm.dismissUndoHint() } }
                                }

                                // 产出文件跟在最后一条回复后面（不占固定底栏，随内容一起滚走）
                                if (produced.isNotEmpty()) {
                                    // key 带 sessionEpoch：切换会话时这一项必须整体重建，
                                    // 否则会沿用上一段会话的组合状态
                                    item(key = "produced-${vm.sessionEpoch}") {
                                        ProducedFilesCard(produced) { vm.openProducedFile(it) }
                                    }
                                }

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
                            }

                            // 「回到底部」：只在用户翻上去看历史（autoFollow 关）且确实
                            // 还没到底时出现。锚定布局下 atBottom 只随用户滚动变化，
                            // 流式长高不会让它来回翻，浮标也就不会跟着闪。
                            // 这里不用 AnimatedVisibility —— 它在 Column 作用域里会解析到
                            // ColumnScope 那个重载，塞进 Box 里用不了；淡入自己算更省事。
                            val pillWanted = !autoFollow && !atBottom
                            val jumpAlpha by animateFloatAsState(
                                targetValue = if (pillWanted) 1f else 0f,
                                animationSpec = tween(if (pillWanted) 150 else 110),
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
                                        // 锚定布局下"底部"就是 index 0，一步到位：
                                        // 不需要"先跳到最后一项顶部再补滚"的两段式
                                        listState.scrollToItem(0)
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
                modifier = Modifier.navigationBarsPadding(),
                modelLabel = model?.displayName ?: "未选模型",
                thinkingLabel = thinkingLabel(model, vm),
                thinkingEnabled = (model?.thinkingLevels?.isNotEmpty() == true),
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
                onOpenWorkspace = onPickWorkspace,
                onMeasured = { composerH = it },
                onChipPlaced = { p, x -> chipX = chipX + (p to x) },
                ctxPercent = vm.ctxPercent(),
                ctxReady = model != null,
                onOpenCtx = { showCtxDetail = true },
            )
        }

        // ── 就地弹出层 ──
        if (pop != Pop.NONE) {
            // 去掉水波纹：这层是透明的"点外面关闭"，它正好盖在下面那颗胶囊上。
            // 再点一次胶囊时事件命中这层，默认 indication 会在胶囊位置闪一块灰 ——
            // 用户看到的就是"点关闭按钮时按钮闪了一下"。
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember {
                            androidx.compose.foundation.interaction.MutableInteractionSource()
                        },
                        indication = null,
                    ) { pop = Pop.NONE }
            )
        }

        // 会话菜单从顶栏右下角展开：它是"这段会话"的动作集合，
        // 位置必须跟触发它的 ⋯ 在同一侧，否则点完要满屏找弹窗
        AnimatedVisibility(
            visible = pop == Pop.MENU,
            enter = fadeIn(tween(110)) + scaleIn(tween(150), initialScale = 0.96f),
            exit = fadeOut(tween(90)) + scaleOut(tween(110), targetScale = 0.98f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = statusTop + 50.dp, end = 8.dp),
        ) {
            PopoverSurface(width = 216.dp) {
                SessionMenu(
                    hasWorkspace = vm.workspace != null,
                    onNewChat = { pop = Pop.NONE; vm.newConversation() },
                    onOpenSessions = { pop = Pop.NONE; onOpenDrawer() },
                    onDetachWorkspace = { pop = Pop.NONE; vm.detachWorkspace() },
                    onExport = { pop = Pop.NONE; onExport() },
                    onDelete = { pop = Pop.NONE; confirmDeleteSession = true },
                )
            }
        }

        AnimatedVisibility(
            visible = pop != Pop.NONE && pop != Pop.MENU,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.96f),
            exit = fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.98f),
            modifier = Modifier
                // 三个控制胶囊各自在自己的位置下面展开：模型贴左缘、权限贴右缘、
                // 思考跟着它自己量到的横坐标走。以前一律钉在右下角 122dp，
                // 键盘一弹就飘到半屏之外，看着像"跟按钮没关系"。
                .align(Alignment.BottomStart)
                .padding(
                    start = popStartDp,
                    bottom = popBottomDp,
                ),
        ) {
            // 238dp：322dp 的默认宽度会盖住半个屏幕，而这个菜单只有三行短标签
            PopoverSurface(width = popWidth) {
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
                    // 会话菜单用的是另一块 AnimatedVisibility（贴顶栏），这里不渲染
                    Pop.MENU -> Unit
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

        // 顶栏 ⋯ →「删除这段会话」。只删会话本身，工作区里的文件一个不动
        if (confirmDeleteSession) {
            ConfirmDialog(
                title = "删除这段会话？",
                message = "这段会话和它的全部消息都会被移除，删除后无法恢复。" +
                    "工作区里的文件不受影响。",
                onConfirm = {
                    confirmDeleteSession = false
                    persisted.currentSessionId?.let { vm.deleteSession(it) }
                },
                onCancel = { confirmDeleteSession = false },
            )
        }

        if (showCtxDetail) {
            CtxDetailDialog(vm) { showCtxDetail = false }
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

/**
 * 控制条上的一颗胶囊。
 *
 * 宽度**定死**（[width]）：胶囊跟着文字长短伸缩时，整条控制条会随着档位名/权限名
 * 一变就左右挪一下 —— 用户在"思考 开 → 思考 最高"这种切换里看到的是一整排控件在抖。
 * 宁可截断：文字放不下就走省略号，位置永远不动。
 *
 * 默认**无底色**：整条控制条是"配置"，不该抢正文和发送键的注意力。
 * 只有传了 bg 才有底色（目前只有「允许」用），让颜色专门表示"这里要注意"。
 */
@Composable
private fun CtrlChip(
    text: String,
    width: Dp,
    modifier: Modifier = Modifier,
    glyph: G? = null,
    glyphTint: Color? = null,
    dot: Color? = null,
    bg: Color = Color.Transparent,
    fg: Color? = null,
    onClick: () -> Unit,
) {
    val ink = fg ?: pal().muted
    Row(
        modifier
            .width(width)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(5.dp))
        }
        if (glyph != null) {
            Glyph(glyph, 12.dp, glyphTint ?: ink, 1.9f)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(3.dp))
        Glyph(G.CHEV_DOWN, 10.dp, ink.copy(alpha = 0.55f), 2.6f)
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

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
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
            // 这个 Box 是**不能加 .clip()** 的：
            // 一整个 Turn（正文 + 几十张工具卡 + 思考块）都长在它里面，长会话里它能有
            // 近 3 万像素高。一旦挂 clip，Compose 会为它建一个巨大的 graphicsLayer，
            // 而命中测试用 layer 的裁剪范围做判定 —— 实测后果是气泡里所有子节点
            // （工具卡头、「思考过程」、⋯ 菜单）全都点不动，只有气泡本身收得到事件，
            // 看起来就是"点了没反应"。圆角改由 background 的形状自己画，不需要 layer；
            // 内容有 13dp 内边距，也不会溢出圆角。
            Box(
                Modifier
                    .background(pal().aiBubble, RoundedCornerShape(6.dp, 18.dp, 18.dp, 18.dp)),
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
                // 这一轮还在生成时不显示「⋯」：三个点会跟着气泡底边一路往上跳，
                // 而此刻能做的操作（复制 / 删除 / 重新生成）都还没意义 —— 等整轮结束再出现
                if (!(vm.streaming && items.lastOrNull()?.id == lastId)) {
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
}

/* ─────────── 本对话产出的文件 ─────────── */

private data class ProducedFile(val rel: String, val name: String)

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
        // ① 工具自己回报的产出（以磁盘上真实存在的文件为准，不是从参数猜的）。
        //    这条以前完全没有 —— 生成的文件就"看不见"，只因为它的路径不在参数里。
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
    permission: PermissionMode,
    hasModel: Boolean,
    /** 空 = 没选工作区。没工作区时只有终端与技能工具可用，底部要如实说明 */
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
    /** 点底部状态行换工作区：状态行已经收进卡片，不再单独占一行 */
    onOpenWorkspace: () -> Unit = {},
    /** 当前历史占用百分比（0..100）。ctxReady=false 时（没选模型）整个指示不出现 */
    ctxPercent: Int = 0,
    ctxReady: Boolean = false,
    /** 点百分比打开占用明细 */
    onOpenCtx: () -> Unit = {},
    /** 把输入区实测高度回报给弹层用的锚点 */
    onMeasured: (Int) -> Unit = {},
    /** 三颗控制胶囊各自的窗口横坐标，弹层按它对齐 */
    onChipPlaced: (Pop, Int) -> Unit = { _, _ -> },
) {
    /** 输入框自己是否持焦点。IME 内边距只跟它走，见下面的说明 */
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .onGloballyPositioned { onMeasured(it.size.height) }
            .fillMaxWidth()
            // 只在自己持焦点时吃 IME inset。
            // 无条件挂 imePadding 的后果：抽屉里点搜索框，输入法一样会弹，
            // 背景这张会话页也会跟着缩短、重排 —— 明明是另一块界面在用键盘。
            .then(if (focused) Modifier.imePadding() else Modifier)
            .padding(horizontal = 12.dp)
            .padding(top = 6.dp, bottom = 10.dp)
    ) {

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

        // ── 控制条：左边是"身份"（模型），右边是"行为"（思考、权限）──
        //
        // 以前三颗都是饱和底色（浅蓝 / 浅蓝 / 琥珀）等宽并排：三个彩色控件谁都不是重点，
        // 看着像临时拼在一起。现在默认全素色，**只有权限处于「允许」时才上色** ——
        // 那是唯一一个"写文件不再问你"的持续风险，值得一直提醒；其余状态都是中性灰。
        Row(
            Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlChip(
                text = modelLabel,
                width = 136.dp,
                dot = if (hasModel) pal().ok else pal().faint,
                onClick = { onPop(Pop.MODEL) },
                modifier = Modifier.onGloballyPositioned {
                    onChipPlaced(Pop.MODEL, it.positionInWindow().x.toInt())
                },
            )
            Box(Modifier.padding(horizontal = 3.dp).size(1.dp, 13.dp).background(pal().line))
            CtrlChip(
                text = "思考 $thinkingLabel",
                width = 104.dp,
                glyph = G.SPARKLE,
                glyphTint = if (thinkingEnabled) pal().accent else pal().faint,
                // 不支持思考的模型也让这颗可点：弹层里会说清"为什么没有档位"。
                // 以前是 enabled=false 静默变灰、点了没反应，用户只会以为界面坏了。
                onClick = { onPop(Pop.THINK) },
                modifier = Modifier.onGloballyPositioned {
                    onChipPlaced(Pop.THINK, it.positionInWindow().x.toInt())
                },
            )
            Spacer(Modifier.weight(1f))
            CtrlChip(
                text = when (permission) {
                    PermissionMode.READONLY -> "只读"
                    PermissionMode.CONFIRM -> "需确认"
                    PermissionMode.ALLOW -> "允许"
                    else -> permission.label
                },
                width = 108.dp,
                glyph = G.SHIELD,
                glyphTint = if (permission == PermissionMode.ALLOW) pal().warn else pal().muted,
                bg = if (permission == PermissionMode.ALLOW) pal().warnSoft else Color.Transparent,
                fg = if (permission == PermissionMode.ALLOW) pal().warn else pal().muted,
                onClick = { onPop(Pop.PERMIT) },
                modifier = Modifier.onGloballyPositioned {
                    onChipPlaced(Pop.PERMIT, it.positionInWindow().x.toInt())
                },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
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

            // 空输入时降级成描边禁用态：以前不管有没有内容都是实心蓝，
            // "能不能发"看不出来，蓝色也被稀释成一个普通装饰。
            val canSend = hasModel && (streaming || input.isNotBlank())
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (canSend) pal().accent else Color.Transparent)
                    .border(1.dp, if (canSend) Color.Transparent else pal().line, CircleShape)
                    .clickable(enabled = canSend) { if (streaming) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                // 禁用态不能沿用 onAccent：深色主题下 onAccent 是深墨蓝，落在近黑底上看不见
                val sendFg = if (canSend) pal().onAccent else pal().faint
                if (streaming) Glyph(G.STOP, 14.dp, sendFg, 2.2f)
                else Glyph(G.ARROW_UP, 16.dp, sendFg, 2.4f)
            }
            }

            // 状态行收进卡片右下角：少一层横条，点一下还能直接换工作区。
            // 以前它单独占一行居中灰字，既占高，又和上面的控制条抢注意力。
            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── 上下文占用：微型进度条 + 百分比 ──
                // 放工作区左边、同一行：这一行本来就是"这段会话的运行时状态"，
                // 两个信息性质一致，没必要为它再起一行。
                if (ctxReady) {
                    val pct = ctxPercent
                    val tone = when {
                        pct >= 90 -> pal().bad
                        pct >= 70 -> pal().warn
                        else -> pal().hint
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onOpenCtx)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(34.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(pal().line),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((pct / 100f).coerceIn(0f, 1f))
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(tone),
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text("$pct%", fontSize = 10.sp, color = tone)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                val (label, tint) = when {
                    !hasModel -> "先添加模型" to pal().hint
                    // 没选工作区但有终端：文件工具不发，run_shell 照常可用。
                    // 写"工具不可用"会让人以为 agent 什么都不会做，实际它能跑命令。
                    workspaceName.isBlank() && shellReady ->
                        "选择工作区 · 终端可用" to pal().warn
                    workspaceName.isBlank() -> "选择工作区 · 才能读写文件" to pal().warn
                    permission == PermissionMode.READONLY -> "$workspaceName · 只读" to pal().hint
                    else -> "$workspaceName · 可读写" to pal().hint
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenWorkspace)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph(G.FOLDER, 10.dp, tint, 1.7f)
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontSize = 10.sp, color = tint, maxLines = 1)
                }
            }
        }
    }
}

private fun thinkingLabel(model: ModelProfile?, vm: AppViewModel): String {
    if (model == null || model.thinkingLevels.isEmpty()) return "—"
    val id = vm.currentThinkingLevel(model)
    return model.thinkingLevels.firstOrNull { it.id == id }?.label ?: "—"
}

// ───────────────── 弹出层 ─────────────────

/**
 * 顶栏 ⋯ 的菜单：这段会话能做的动作都在这里。
 *
 * 全是**已有**动作，只是把散落在抽屉长按、顶栏胶囊里的入口收到一处：
 * 顶栏因此只剩「☰ + 标题 + ⋯」，也不会再出现第二个"＋"和输入框的加号打架。
 */
@Composable
private fun SessionMenu(
    hasWorkspace: Boolean,
    onNewChat: () -> Unit,
    onOpenSessions: () -> Unit,
    onDetachWorkspace: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        PopoverHead("这段会话")
        MenuRow(G.PLUS, "新对话", onNewChat)
        MenuRow(G.MENU, "切换会话", onOpenSessions)
        MenuRow(G.ARROW_DOWN, "导出会话（Markdown）", onExport)
        if (hasWorkspace) {
            MenuRow(G.FOLDER, "解除工作区绑定", onDetachWorkspace)
            Hairline(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        MenuRow(G.TRASH, "删除这段会话", onDelete, danger = true)
    }
}

@Composable
private fun MenuRow(glyph: G, text: String, onClick: () -> Unit, danger: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, 15.dp, if (danger) pal().bad else pal().muted, 1.9f)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.5.sp, color = if (danger) pal().bad else pal().ink)
    }
}

@Composable
private fun ModelPopover(vm: AppViewModel, onGoModels: () -> Unit, onClose: () -> Unit) {
    val all = vm.chatModelsWithProvider()
    val current = vm.state.value.selectedModelId
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column {
        PopoverHead("选择模型")
        if (all.isEmpty()) {
            PopoverItem(
                title = "还没有添加模型",
                subtitle = "点这里去添加，添加完就能在这里切换",
                selected = false,
                onClick = onGoModels,
                icon = { Glyph(G.PLUS, 15.dp, pal().accent, 2f) },
            )
        } else {
            // 可滚动：以前只列前 6 个，多出来的给一行"还有 N 个"的死路，
            // 用户点完还得再跳一次页面才能选到
            Column(
                Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                all.forEach { (p, m) ->
                    val sel = m.id == current
                    val def = remember(p.catalogId) { Catalog.find(ctx, p.catalogId) }
                    val levels = m.thinkingLevels
                    PopoverItem(
                        title = m.displayName.ifBlank { m.modelId },
                        // 后果写在行里：切到不支持思考的模型会把档位关掉，
                        // 以前这句话只出现在弹层底部 10sp 的小字里，选完才看见
                        subtitle = buildString {
                            // 显示名和 modelId 一样时别再重复一遍（多数自定义模型就是这样）
                            if (m.displayName.isNotBlank() && m.displayName != m.modelId) {
                                append(m.modelId).append(" · ")
                            }
                            if (levels.isEmpty()) append("不支持思考")
                            else {
                                append("思考 ${levels.size} 档")
                                if (!sel) append(" · 切换重置")
                            }
                        },
                        warnSubtitle = levels.isEmpty() && !sel,
                        selected = sel,
                        onClick = { vm.select(m.id); onClose() },
                        icon = {
                            VendorMark(
                                slug = def?.icon?.ifBlank { null },
                                fallbackText = def?.fallbackText?.takeIf { it.isNotBlank() }
                                    ?: p.displayName.take(1).uppercase(),
                                fallbackColor = parseHex(def?.fallbackColor ?: "#5F6368"),
                                dim = 18.dp,
                                glyphDim = 11.dp,
                                round = false,
                                showBorder = false,
                            )
                        },
                    )
                }
            }
            Hairline(Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            MenuRow(G.SETTINGS, "管理模型", onGoModels)
        }
    }
}

@Composable
private fun ThinkPopover(vm: AppViewModel, onClose: () -> Unit) {
    val model = vm.selectedModel()
    val levels = model?.thinkingLevels.orEmpty()
    val currentId = model?.let { vm.currentThinkingLevel(it) }
    Column {
        PopoverHead(if (levels.isEmpty()) "思考程度" else "思考程度 · ${levels.size} 档")
        if (levels.isEmpty()) {
            // 不支持思考的模型也把弹层打开：说清"为什么没有档位"，
            // 比让胶囊静默变灰、点了没反应要好
            PopoverItem(
                title = "当前模型不支持思考",
                subtitle = "换一个支持思考的模型后，这里会出现档位",
                selected = false,
                onClick = onClose,
                icon = { Glyph(G.SPARKLE, 15.dp, pal().faint, 1.8f) },
            )
        } else {
            levels.forEachIndexed { idx, lvl ->
                val sel = lvl.id == currentId
                PopoverItem(
                    title = lvl.label,
                    subtitle = thinkHint(levels.size, idx),
                    selected = sel,
                    onClick = {
                        model?.let { vm.setThinking(it.id, lvl.id) }
                        onClose()
                    },
                    icon = { ThinkBars(levels.size, idx, sel) },
                )
            }
            PopoverFoot("档位越高越慢越贵；不支持的档位不会出现")
        }
    }
}

/** 档位效果的"人话"：把 high / 1024 这类实现参数换成用户能判断的取舍 */
private fun thinkHint(total: Int, idx: Int): String = when {
    total <= 1 -> "由模型决定"
    idx == 0 -> "最快，不花思考 token"
    idx == total - 1 -> "更慢更贵，复杂推理更稳"
    else -> "默认，速度与深度平衡"
}

/** 1–3 格强度条：比四段彩点好读，也不会在七档模型上变成调色盘 */
@Composable
private fun ThinkBars(total: Int, idx: Int, active: Boolean) {
    val bars = total.coerceIn(1, 3)
    val filled = if (total <= 1) bars else {
        (idx.toFloat() / (total - 1) * bars + 0.6f).toInt().coerceIn(1, bars)
    }
    val tint = when {
        !active -> pal().faint
        idx == 0 -> pal().faint
        else -> pal().accent
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(bars) { i ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((6 + i * 3).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < filled) tint else pal().lineSoft)
            )
        }
    }
}

@Composable
private fun PermitPopover(vm: AppViewModel, onClose: () -> Unit) {
    val cur = vm.permission()
    Column {
        PopoverHead("访问权限 · 仅当前会话")
        PopoverItem(
            title = "只读",
            subtitle = "只能读与搜，改文件类工具不会出现",
            selected = cur == PermissionMode.READONLY,
            onClick = { vm.setPermission(PermissionMode.READONLY); onClose() },
            icon = { Glyph(G.SHIELD, 15.dp, pal().muted, 1.6f) },
        )
        PopoverItem(
            title = "需确认",
            subtitle = "每次写入都给你看 diff",
            selected = cur == PermissionMode.CONFIRM,
            onClick = { vm.setPermission(PermissionMode.CONFIRM); onClose() },
            icon = { Glyph(G.SHIELD, 15.dp, pal().accent, 1.6f) },
        )
        PopoverItem(
            title = "全部允许",
            subtitle = "写入不再询问，只能靠撤销回退",
            warnSubtitle = true,
            selected = cur == PermissionMode.ALLOW,
            onClick = { vm.setPermission(PermissionMode.ALLOW); onClose() },
            icon = { Glyph(G.WARN, 15.dp, pal().warn, 1.6f) },
        )
        PopoverFoot("越界路径与备份失败仍会硬拦")
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
    /** 正在跑任务的会话 id 集合：卡片右侧要挂转圈 */
    runningIds: Set<String> = emptySet(),
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
    /** 筛选片是否展开（默认收起，免得把会话列表顶下去） */
    var filterOpen by remember { mutableStateOf(false) }
    /** all / plain / workspace / week —— 与筛选片一一对应 */
    var filter by remember { mutableStateOf("all") }
    /**
     * 分组折叠状态。以前只有两组、存在 VM 里；现在多了「今天 / 更早」，
     * 就地保存即可 —— 抽屉是常驻图层，关掉再拉开状态还在。
     */
    var collapsedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val lineColor = pal().line
    val density = androidx.compose.ui.platform.LocalDensity.current

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

            // 搜索 + 筛选：右侧那颗从「当前会话的 ⋯」换成筛选开关。
            // 导出/清空是会话动作，已经收进顶栏 ⋯ 菜单 —— 同一个位置不该同时表示
            // "搜索的选项"和"会话的动作"。
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(pal().surfaceAlt)
                    .padding(start = 13.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(G.SEARCH, 15.dp, pal().hint, 2.1f)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("搜索标题与正文", fontSize = 13.5.sp, color = pal().hint)
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
                    ) { Glyph(G.CLOSE, 10.dp, pal().muted, 2.2f) }
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { filterOpen = !filterOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    // 有筛选条件时保持强调色：收起来也知道"现在不是看全部"
                    Glyph(
                        G.SLIDERS, 15.dp,
                        if (filterOpen || filter != "all") pal().accent else pal().hint, 2f,
                    )
                }
            }

            // 筛选片：默认收起，否则三行小胶囊把会话列表顶下去
            if (filterOpen) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "all" to "全部",
                        "plain" to "普通",
                        "workspace" to "工作区",
                        "week" to "最近 7 天",
                    ).forEach { (key, label) ->
                        val on = filter == key
                        Text(
                            label,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (on) pal().accentInk else pal().muted,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) pal().accentSoft else pal().surfaceAlt)
                                .clickable { filter = key }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // 搜索词同时匹配标题与正文，避免"记得聊过但忘了标题"时搜不到
            val q = query.trim()
            val weekCut = System.currentTimeMillis() - 7L * 86_400_000
            // 分组顺序 = 今天 → 更早 → 工作区：时间维度回到第一层，
            // 找"昨天那件事"不用先翻完整个普通会话组
            val groups = remember(sessions, q, filter) {
                val hits = if (q.isBlank()) sessions
                else sessions.filter { s ->
                    s.title.contains(q, true) || s.messages.any { it.text.contains(q, true) }
                }
                val base = when (filter) {
                    "plain" -> hits.filter { !it.inWorkspace }
                    "workspace" -> hits.filter { it.inWorkspace }
                    "week" -> hits.filter { it.updatedAt >= weekCut }
                    else -> hits
                }
                val ws = base.filter { it.inWorkspace }.sortedByDescending { it.updatedAt }
                val plain = base.filter { !it.inWorkspace }.sortedByDescending { it.updatedAt }
                val (today, older) = plain.partition { isSameDayMs(it.updatedAt, System.currentTimeMillis()) }
                buildList {
                    if (today.isNotEmpty()) add(Triple("today", "今天", today))
                    if (older.isNotEmpty()) add(Triple("older", "更早", older))
                    if (ws.isNotEmpty()) add(Triple("ws", "工作区", ws))
                }
            }

            Spacer(Modifier.height(6.dp))

            if (groups.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        if (q.isBlank() && filter == "all") "还没有历史会话"
                        else if (q.isNotBlank()) "没有匹配「$q」的会话"
                        else "这个筛选下没有会话",
                        style = ty().caption,
                    )
                }
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    groups.forEach { (groupKey, label, rows) ->
                        // 搜索时把列表当成"平铺的命中结果"：一律展开，也不提供折叠
                        val collapsible = q.isBlank()
                        val collapsed = collapsible && groupKey in collapsedKeys
                        item(key = "h-$groupKey") {
                            SessionGroupHeader(
                                label = label,
                                hint = if (groupKey == "ws") rows.singleWorkspaceName() else null,
                                count = rows.size,
                                collapsed = collapsed,
                                collapsible = collapsible,
                                onToggle = {
                                    collapsedKeys = if (groupKey in collapsedKeys) {
                                        collapsedKeys - groupKey
                                    } else {
                                        collapsedKeys + groupKey
                                    }
                                },
                            )
                        }
                        if (!collapsed) {
                            items(rows, key = { it.id }) { s ->
                                SessionRow(
                                    title = s.title.ifBlank { "新会话" },
                                    time = sessionTime(s.updatedAt),
                                    // 第二行带上"用什么模型聊的"，同批会话才分得清
                                    model = s.messages.lastOrNull { it.role == "assistant" }
                                        ?.modelLabel.orEmpty(),
                                    folder = if (s.inWorkspace) s.workspaceName else "",
                                    active = s.id == currentSessionId,
                                    // 这段会话正在后台跑任务：卡片右边挂一个转圈
                                    running = s.id in runningIds,
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
    /** 这段会话用哪个模型聊的（取自最后一条回复），空则不显示 */
    model: String,
    /** 工作区会话的目录名，普通会话为空 */
    folder: String,
    active: Boolean,
    /** 这段会话正在跑任务。切走之后任务仍在继续，这是它唯一的可见凭据 */
    running: Boolean = false,
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
            .padding(start = if (active) 9.dp else 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 当前会话左侧 2.5dp 强调竖条：和设置页分组、弹层选中项用同一套语言，
        // 不用再靠"底色深浅"去猜哪条是当前
        if (active) {
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(pal().accent)
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                color = pal().ink,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // 第二行：目录 · 模型 · 时间。以前只有时间，同一批会话在列表里长得一模一样
            val meta = buildList {
                if (folder.isNotBlank()) add(folder)
                if (model.isNotBlank()) add(model)
                if (time.isNotBlank()) add(time)
            }
            if (meta.isNotEmpty()) {
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (folder.isNotBlank()) {
                        Glyph(G.FOLDER, 9.dp, pal().faint, 1.6f)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        meta.joinToString(" · "),
                        fontSize = 10.sp,
                        color = pal().faint,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (running) {
            Spacer(Modifier.width(8.dp))
            RunningSpinner()
        }
    }
}

/**
 * 会话在跑任务时的转圈。
 *
 * 自绘一段 280° 圆弧并让它匀速旋转，而不是用 CircularProgressIndicator：
 * 后者自带 Material 的尺寸与配色，塞进 13.5sp 的行里会显得过大过粗。
 */
@Composable
private fun RunningSpinner(size: androidx.compose.ui.unit.Dp = 13.dp) {
    val angle by androidx.compose.animation.core.rememberInfiniteTransition(label = "sessionRun")
        .animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = tween(880, easing = androidx.compose.animation.core.LinearEasing),
            ),
            label = "sessionRunAngle",
        )
    val tint = pal().accent
    androidx.compose.foundation.Canvas(
        Modifier.size(size).rotate(angle),
    ) {
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 280f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = this.size.minDimension * 0.15f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

/** 同一天（按设备本地时区），用于把会话分成「今天 / 更早」 */
private fun isSameDayMs(a: Long, b: Long): Boolean {
    if (a <= 0L) return false
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
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

// ───────────────── 上下文占用明细 ─────────────────

/**
 * 输入框右下角那个百分比点开的明细。
 *
 * 用独立 Popup 而不是 PopoverKit 的锚点弹层：它从输入框内部弹出，
 * 锚点要跟着键盘高度走，而这套锚点机制是给"三颗胶囊"设计的（贴底部对齐）。
 * 这里居中弹一张卡更稳，也不会和胶囊弹层抢 z 序。
 */
@Composable
private fun CtxDetailDialog(vm: AppViewModel, onClose: () -> Unit) {
    val used = vm.ctxUsed
    val budget = vm.ctxBudget
    val pct = vm.ctxPercent()
    val prompt = vm.ctxPrompt
    val text = vm.ctxText
    val tool = vm.ctxTool
    val tone = when {
        pct >= 90 -> pal().bad
        pct >= 70 -> pal().warn
        else -> pal().accent
    }
    val visible = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pal().scrim)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        ) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(130)) + scaleIn(tween(170), initialScale = 0.94f),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Column(
                    Modifier
                        .width(300.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(pal().card)
                        .border(1.dp, pal().line, RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 14.dp),
                ) {
                    Text("上下文占用", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal().ink)
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            ctxK(used) + " / " + ctxK(budget),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = pal().ink,
                        )
                        Spacer(Modifier.weight(1f))
                        Text("$pct%", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = tone)
                    }

                    Spacer(Modifier.height(10.dp))
                    // 分段条：三段按各自占预算的比例，剩下的空白就是"还能用多少"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(pal().line),
                    ) {
                        if (prompt > 0) Box(Modifier.weight(prompt.toFloat()).fillMaxHeight().background(pal().accent))
                        if (text > 0) Box(Modifier.weight(text.toFloat()).fillMaxHeight().background(pal().media))
                        if (tool > 0) Box(Modifier.weight(tool.toFloat()).fillMaxHeight().background(pal().ok))
                        val rest = (budget.coerceAtLeast(1) - used).coerceAtLeast(0)
                        if (rest > 0) Box(Modifier.weight(rest.toFloat()).fillMaxHeight())
                    }

                    Spacer(Modifier.height(12.dp))
                    CtxLegend("系统提示", prompt, pal().accent)
                    CtxLegend("对话内容", text, pal().media)
                    CtxLegend("工具结果", tool, pal().ok)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("剩余可用", style = ty().micro, modifier = Modifier.weight(1f))
                        Text(ctxK((safe0(budget) - used).coerceAtLeast(0)), style = ty().micro)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "分母是模型窗口扣掉输出与系统提示预留后的额度，不是原始窗口。" +
                            "占用到 70% 时会自动压缩早期内容，数字会回落。估算值，与实际计费有偏差。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = pal().faint,
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        DialogButton("知道了", primary = true, onClick = onClose, primaryColor = pal().accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun CtxLegend(label: String, value: Int, tint: androidx.compose.ui.graphics.Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(tint))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.5.sp, color = pal().ink, modifier = Modifier.weight(1f))
        Text(ctxK(value), fontSize = 12.5.sp, color = pal().muted)
    }
}

private fun safe0(n: Int): Int = if (n <= 0) 0 else n

/** token 数写成 1.2k / 186k —— 这一屏只做量级判断，精确到个位没有意义 */
private fun ctxK(n: Int): String =
    if (n < 1000) "$n" else String.format(java.util.Locale.US, "%.1fk", n / 1000.0)
