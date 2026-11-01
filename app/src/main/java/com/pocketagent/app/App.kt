package com.pocketagent.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.pocketagent.app.agent.ProotRuntime
import com.pocketagent.app.data.SkillStore
import java.io.File

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Thread { runUpgradeCleanup(this) }.apply { name = "upgrade-cleanup"; isDaemon = true }.start()
    }

    companion object {
        lateinit var instance: App
            private set

        private const val TAG = "UpgradeCleanup"
        private const val PREFS = "pa_upgrade"
        private const val KEY = "cleanupVersion"

        /**
         * 清理版本号。**加一条清理就必须 +1**，否则已经升级过的用户不会重跑。
         *
         * 1：整体移除记忆功能 → 删 filesDir/memory
         * 2：移除 ppt-master 技能与脚本运行时 → 删技能旧解压副本、skill_run 的文件桥、
         *    以及整个 Linux 环境（Python 是专为那个技能装的，留着白占一两百兆）
         */
        private const val CLEANUP_VERSION = 2

        /**
         * 覆盖安装**不会**动 app 私有目录：上一个版本装过的东西会一直躺在那儿 ——
         * 没人读、界面上也没有入口能删。所以每次"删功能"都要配一条这里的一次性清理，
         * 否则用户手机上的空间和隐私都收不回来。
         */
        private fun runUpgradeCleanup(ctx: Context) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val done = prefs.getInt(KEY, 0)
            if (done >= CLEANUP_VERSION) return
            runCatching {
                // —— v1：记忆目录 ——
                if (done < 1) {
                    File(ctx.filesDir, "memory").deleteRecursively()
                }

                // —— v2：技能包与脚本运行时 ——
                if (done < 2) {
                    // ① filesDir/skills 里只保留 catalog 里还存在的技能。
                    //    不解压也没关系：下一次 skill_read 会自己 ensure() 重建。
                    val keep = runCatching {
                        SkillStore.catalog(ctx).map { it.id }.toSet()
                    }.getOrDefault(emptySet())
                    val skillsRoot = SkillStore.root(ctx)
                    if (skillsRoot.isDirectory) {
                        skillsRoot.listFiles()?.forEach { dir ->
                            if (dir.isDirectory && dir.name !in keep) {
                                Log.i(TAG, "drop stale skill dir: " + dir.name)
                                dir.deleteRecursively()
                            }
                        }
                        // 打一半的临时目录与旧版本标记：留着只会让下一次解压误判
                        File(ctx.filesDir, "skills.tmp").deleteRecursively()
                        File(skillsRoot, ".pack").delete()
                    }

                    // ② skill_run 的文件桥：/workspace/in 与 out 里全是脚本的中间产物
                    listOf("in", "out", "home").forEach {
                        File(File(ctx.filesDir, ProotRuntime.WORKSPACE_DIR), it).deleteRecursively()
                    }

                    // ③ 整个 Linux 环境。Python 是专为 ppt-master 装的，
                    //    用户明确选择"重置"，所以连 Ubuntu base 一起删 —— 需要终端能力时
                    //    在「设置 → 扩展」重新装一次，比留一套装了一半的包更可控。
                    File(ctx.filesDir, ProotRuntime.ROOTFS_DIR).deleteRecursively()
                    File(ctx.filesDir, ProotRuntime.ROOTFS_DIR + "-staging").deleteRecursively()
                    File(ctx.cacheDir, "rootfs.tar.gz").delete()
                    File(ctx.cacheDir, "rootfs.tar.gz.part").delete()
                }

                prefs.edit().putInt(KEY, CLEANUP_VERSION).apply()
                Log.i(TAG, "cleanup to v$CLEANUP_VERSION done")
            }.onFailure { Log.w(TAG, "cleanup failed", it) }
        }
    }
}
