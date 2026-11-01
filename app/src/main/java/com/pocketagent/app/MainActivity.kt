package com.pocketagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketagent.app.ui.AgentTheme
import com.pocketagent.app.ui.ThemeMode
import com.pocketagent.app.ui.pal

class MainActivity : ComponentActivity() {

    /**
     * Android 13+ 的通知权限。
     *
     * 没有它，前台服务的通知会被系统隐藏 —— 服务本身仍算前台、任务仍不被回收，
     * 但用户看不到"它还在跑"，会以为卡死了。所以这个权限只为"看得见"，不是为前提。
     * 用系统默认行为：拒绝后不再纠缠（用户想开可以自己去系统设置）。
     */
    private val askNotification =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    /** 和 setContent 里 viewModel() 是同一个实例；onStop 时用它把这一轮的消息落盘 */
    private var hostVm: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边：系统栏透明，内容各自避让。
        // 注意：这里绝不能再包一层 systemBarsPadding —— 那会把抽屉一起缩进安全区，
        // 抽屉就永远到不了状态栏和底部手势条（之前那个 bug 的根因）。
        enableEdgeToEdge()
        // 三键导航下系统会给导航栏加半透明 scrim，纯黑主题里会显出一条灰带
        window.isNavigationBarContrastEnforced = false

        maybeAskNotificationPermission()

        setContent {
            val vm: AppViewModel = viewModel()
            hostVm = vm
            val persisted by vm.state.collectAsStateWithLifecycle()

            val mode = when (persisted.theme) {
                "dark" -> ThemeMode.DARK
                "light" -> ThemeMode.LIGHT
                else -> ThemeMode.SYSTEM
            }

            AgentTheme(mode = mode) {
                Surface(Modifier.fillMaxSize(), color = pal().pageBg) {
                    AppRoot(vm)
                }
            }
        }
    }

    /**
     * 退到后台/切走时落一次盘。
     *
     * agentTurn 现在每步都会存，但用户按 Home 的瞬间可能正好有一条刚出的草稿没存；
     * 进程被系统回收时这一条就变成"AI 说到一半没了"。这里兜底，成本是一次 JSON 写。
     */
    override fun onStop() {
        super.onStop()
        runCatching { hostVm?.onHostStop() }
    }

    private fun maybeAskNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return
        // 首启问一次，拒绝后不再纠缠（系统在多次拒绝后会自动静默）。
        // 用 post 让首帧先画出来，避免权限弹窗盖在空白页上。
        window.decorView.post { runCatching { askNotification.launch(android.Manifest.permission.POST_NOTIFICATIONS) } }
    }
}
