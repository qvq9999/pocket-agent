package com.pocketagent.app.agent

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 写前快照 + 撤销日志。
 *
 * 为什么必须有：文件工具一旦放开写，最坏的结局不是"没改成"，是**把用户的资料改没了**。
 * 模型可以在一步里重写整个文件，而它看不见自己会毁掉什么。所以规矩是：
 * **先落一份原件到应用私有目录，成功之后才允许动工作区**；备份失败就不执行。
 *
 * 存放：`filesDir/undo/<txnId>/<n>.dat` + `index.json` 索引。
 *
 * 两处刻意的设计：
 * 1. **备份走流式复制**（[WorkspaceFs.copyTo]），不把原文件读进内存。
 *    早期版本用 `readBytes` 备份，于是 256KB 以上的文件一律"删不掉、移不动"——
 *    一张手机照片就能触发。现在内存占用恒定 64KB，与文件大小无关。
 * 2. **双重淘汰**：条数超 [MAX_TXN] 或总量超 [MAX_BYTES] 都从最旧的开始删。
 *    手机空间比"能不能翻旧账"重要，但删掉的要让 UI 能解释成"已不可撤销"。
 */
class UndoStore(private val ctx: Context) {

    /**
     * 一个待保护的路径：
     *  - src = null + existed = true + isDir = true → 原本是个空目录，撤销时重建目录；
     *  - src = null + existed = false → 原本不存在，撤销时把它删掉；
     *  - src != null → 原本有内容，撤销时把这份内容流式写回去。
     */
    class Snap(
        val rel: String,
        val existed: Boolean,
        val src: WorkspaceFs.Entry? = null,
        val isDir: Boolean = false,
    )

    @Serializable
    private data class Index(
        val version: Int = 1,
        val txns: List<Txn> = emptyList(),
    )

    @Serializable
    private data class Txn(
        val id: String,
        val time: Long,
        val title: String,
        /** 本次事务占用的快照字节数，用于总量淘汰 */
        val bytes: Long = 0L,
        val items: List<Item> = emptyList(),
    )

    @Serializable
    private data class Item(
        val rel: String,
        val existed: Boolean,
        /** 相对 txn 目录的文件名；没有内容可备份时为空串 */
        val file: String = "",
        val bytes: Long = 0L,
        val dir: Boolean = false,
    )

    private val root = File(ctx.filesDir, "undo")
    private val indexFile = File(root, "index.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val lock = Any()

    fun record(fs: WorkspaceFs, title: String, snaps: List<Snap>): String = synchronized(lock) {
        if (!root.exists() && !root.mkdirs()) {
            throw ToolError("SNAPSHOT", "无法创建快照目录，已取消本次修改")
        }
        val id = "u-" + UUID.randomUUID().toString().take(8)
        val dir = File(root, id)

        val items = ArrayList<Item>(snaps.size)
        var total = 0L
        try {
            if (!dir.mkdirs()) throw ToolError("SNAPSHOT", "无法创建快照目录，已取消本次修改")
            snaps.forEachIndexed { i, s ->
                val src = s.src
                if (src == null) {
                    items.add(Item(s.rel, s.existed, "", 0L, s.isDir))
                } else {
                    val f = File(dir, "$i.dat")
                    val written = runCatching {
                        f.outputStream().use { out -> fs.copyTo(src, out) }
                    }.getOrElse { e ->
                        throw if (e is ToolError) e else ToolError("SNAPSHOT", "快照失败：${e.message.orEmpty()}")
                    }
                    // 有些 provider 返回 SIZE = -1，那时无法比对，只能信实际写入量
                    if (src.size >= 0 && written != src.size) {
                        throw ToolError("SNAPSHOT", "快照不完整（$written/${src.size} 字节），已取消本次修改")
                    }
                    total += written
                    items.add(Item(s.rel, s.existed, "$i.dat", written, s.isDir))
                }
            }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw if (e is ToolError) e else ToolError("SNAPSHOT", "快照失败：${e.message.orEmpty()}")
        }

        val idx = readIndex()
        val next = idx.copy(
            txns = (idx.txns + Txn(id, System.currentTimeMillis(), title, total, items)).takeLast(MAX_TXN)
        )
        writeIndex(next)
        prune(next)
        id
    }

    /** 撤销一个事务。返回恢复的文件数；抛 ToolError 表示没恢复成功（此时不谎称成功） */
    fun undo(fs: WorkspaceFs, txnId: String): Int = synchronized(lock) {
        val idx = readIndex()
        val txn = idx.txns.firstOrNull { it.id == txnId }
            ?: throw ToolError("UNDO_GONE", "这条撤销记录已不存在（可能已被清理）")
        val dir = File(root, txn.id)
        var restored = 0

        // 逆序恢复：同一个事务里多次写同一路径时，最后落的是最初那份
        txn.items.asReversed().forEach { item ->
            val entry = fs.resolve(item.rel)
            if (item.existed) {
                if (item.dir) {
                    if (entry == null) {
                        fs.createDir(PathGuard.parentOf(item.rel), PathGuard.nameOf(item.rel))
                    }
                } else {
                    val f = File(dir, item.file)
                    if (!f.exists()) throw ToolError("UNDO_GONE", "快照文件缺失：${item.rel}")
                    val target = entry
                        ?: fs.createFile(PathGuard.parentOf(item.rel), PathGuard.nameOf(item.rel))
                    // 流式写回：快照可能有几十 MB
                    runCatching {
                        f.inputStream().use { ins ->
                            fs.openOutput(target, append = false).use { out -> ins.copyTo(out, 64 * 1024) }
                        }
                    }.getOrElse { e ->
                        throw if (e is ToolError) e else ToolError("IO", "恢复失败：${e.message.orEmpty()}")
                    }
                }
            } else {
                // 原本不存在 → 撤销 = 把新建的东西删掉
                if (entry != null) fs.delete(entry)
            }
            restored++
        }

        writeIndex(idx.copy(txns = idx.txns.filterNot { it.id == txnId }))
        runCatching { dir.deleteRecursively() }
        restored
    }

    fun has(txnId: String): Boolean =
        txnId.isNotBlank() && readIndex().txns.any { it.id == txnId }

    private fun readIndex(): Index = runCatching {
        if (indexFile.exists()) json.decodeFromString<Index>(indexFile.readText()) else Index()
    }.getOrDefault(Index())

    private fun writeIndex(next: Index) {
        runCatching {
            root.mkdirs()
            val tmp = File(root, "index.json.tmp")
            tmp.writeText(json.encodeToString(next))
            if (!tmp.renameTo(indexFile)) {
                indexFile.writeText(json.encodeToString(next))
                tmp.delete()
            }
        }
    }

    /** 条数与总量双重淘汰，多余目录连同数据一起删 */
    private fun prune(current: Index) {
        var txns = current.txns
        var totalBytes = txns.sumOf { it.bytes }
        var trimmed = false
        while (totalBytes > MAX_BYTES && txns.size > 1) {
            totalBytes -= txns.first().bytes
            txns = txns.drop(1)
            trimmed = true
        }
        val keep = txns.map { it.id }.toSet()
        runCatching {
            root.listFiles()?.forEach { f ->
                if (f.isDirectory && f.name !in keep) f.deleteRecursively()
            }
        }
        if (trimmed) writeIndex(current.copy(txns = txns))
    }

    companion object {
        /** 最多保留多少个撤销事务 */
        const val MAX_TXN = 30

        /** 快照总量上限。超了从最旧的删，UI 上表现为"已不可撤销" */
        const val MAX_BYTES = 200L * 1024 * 1024
    }
}
