package com.pocketagent.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.pocketagent.app.MainActivity
import com.pocketagent.app.R

/**
 * agent 执行期的常驻通知（前台服务）。
 *
 * 解决的是这个场景：用户让它"把这个目录整理一下"，然后切去看别的 App ——
 * 多步任务还在跑，但 Activity 一离开屏幕，`viewModelScope` 随时可能被回收，
 * 于是任务停在第三步，用户回来只看到半截结果，还以为是自己网络断了。
 *
 * 三条刻意的小心：
 * 1. `START_NOT_STICKY`。进程被杀后**不要**自己复活 —— 复活出来的是一次没有上下文的
 *    假执行，比干脆停掉更难排查。
 * 2. 通知不显示也不影响保活。Android 13 起没有 `POST_NOTIFICATIONS` 时通知会被隐藏，
 *    但服务仍被视为前台服务、仍不被回收，所以不做权限请求也不会退化。
 * 3. `stopForeground(REMOVE)` 必须在 onDestroy 也调一次：直接 stopService 时
 *    不走 onStartCommand 的 STOP 分支，漏了就会留一条撤不掉的通知。
 */
class AgentRunService : Service() {

    /** 部分唤醒锁：屏幕灭掉后 CPU 仍能跑完当前这一步，而不是被 Doze 掐在半路 */
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "正在处理…" }
        // 关键：进不了前台就必须立刻退。
        // 只吞异常不停服务的话，系统会在 5 秒后以
        // ForegroundServiceDidNotStartInTimeException 把进程干掉 —— 那比没有这个服务更糟。
        val ok = runCatching { startForeground(NOTIF_ID, buildNotification(text)) }.isSuccess
        if (!ok) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        return START_NOT_STICKY
    }

    /**
     * 拿一条带超时的部分唤醒锁。
     *
     * 后台 + 灭屏时 CPU 会进 Doze：前台服务只保证"进程不被回收"，不保证"还能继续算/继续收流"。
     * 带 10 分钟超时（每次 start 会重新续上），任何异常退出最多 10 分钟后自动释放，
     * 不会留下一条永远不灭的锁把电耗光。
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketAgent:agent-run").apply {
            setReferenceCounted(false)
            runCatching { acquire(10 * 60 * 1000L) }
        }
    }

    override fun onDestroy() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                // 点通知回的是"正在聊的那个界面"，不该另起一个实例
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("Pocket Agent")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "任务执行", NotificationManager.IMPORTANCE_LOW).apply {
                description = "agent 在后台连续执行多步任务时显示"
                // 关掉声音与震动：这是进度提示，不是提醒
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "agent_run"
        private const val NOTIF_ID = 1001

        const val ACTION_STOP = "com.pocketagent.app.action.STOP_RUN"
        const val EXTRA_TEXT = "text"

        /**
         * 开始或更新。在用户可见的界面里调用，不会触发 Android 12 的后台启动限制。
         *
         * 返回是否真的发出去了 —— 调用方要能知道"保活没起来"。
         * 以前这里一律 runCatching 吞掉，服务起不来时用户毫不知情，
         * 只会在切后台回来发现任务停在半路。
         */
        fun start(ctx: Context, text: String): Boolean {
            val i = Intent(ctx, AgentRunService::class.java).putExtra(EXTRA_TEXT, text)
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            }.isSuccess
        }

        /** 结束。走 stopService → onDestroy → stopForeground(REMOVE)，通知一并撤掉 */
        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, AgentRunService::class.java)) }
        }
    }
}
