package com.pocketagent.app.agent

import android.content.Context
import java.io.File

/**
 * 冷记忆：应用私有目录里的 Markdown 知识库，**按需读取**。
 *
 * 为什么不是"每次请求都把记忆塞进 system prompt"：
 * 那种做法（热记忆）只适合三五条短偏好。一旦用户攒了十几篇项目笔记，
 * 每次请求都重发一遍，token 全花在"它可能用不上的旧笔记"上。
 *
 * 冷记忆把方向反过来：文档留在磁盘上，模型需要时自己 `memory_search` 检索、
 * `memory_read` 打开。**不需要向量库** —— 手机上的记忆量级（几十篇 md）用关键词检索足够，
 * 引入 embedding 只会带来模型下载、索引重建、结果不可解释三个新问题。
 *
 * 刻意放在 `filesDir/memory`（app 私有），不占工作区：
 * 记忆是"给 agent 自己用的"，不该出现在用户的目录里被误删或被当成文件管理对象。
 */
class MemoryStore(private val ctx: Context) {

    private val root = File(ctx.filesDir, "memory")

    /** 一篇记忆文档的元信息 */
    data class Doc(
        val name: String,
        val size: Long,
        val mtime: Long,
    ) {
        /** 展示给模型时去掉 .md 后缀，读的时候再补回来 */
        val title: String get() = name.removeSuffix(".md")
    }

    data class Hit(val doc: String, val line: Int, val text: String)

    private fun ensureRoot(): File {
        if (!root.exists() && !root.mkdirs()) {
            throw ToolError("IO", "无法创建记忆目录")
        }
        return root
    }

    /** 名字压成安全文件名：不允许路径分隔符与上跳，避免写到 memory/ 之外 */
    fun safeName(raw: String): String {
        var n = raw.trim()
        if (n.isEmpty()) throw ToolError("BAD_ARGS", "记忆名称不能为空")
        n = n.removeSuffix(".md")
        n = n.replace('\\', '/').substringAfterLast('/')
        n = n.replace(Regex("[\\\\:*?\"<>|\\u0000-\\u001F]"), "_").trim()
        if (n.isEmpty() || n == "." || n == "..") throw ToolError("BAD_ARGS", "记忆名称不合法")
        if (n.length > 80) n = n.take(80)
        return "$n.md"
    }

    fun list(): List<Doc> {
        val dir = ensureRoot()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".md", ignoreCase = true) }
            ?.map { Doc(it.name, it.length(), it.lastModified()) }
            ?.sortedByDescending { it.mtime }
            ?: emptyList()
    }

    fun read(name: String): String {
        val f = File(ensureRoot(), safeName(name))
        if (!f.exists()) throw ToolError("NOT_FOUND", "没有这篇记忆：$name（可以先用 memory_list 看有哪些）")
        if (f.length() > MAX_DOC.toLong()) {
            throw ToolError("TOO_LARGE", "这篇记忆超过 ${MAX_DOC / 1024}KB，请先在记忆里精简它")
        }
        return runCatching { f.readText() }.getOrElse { throw ToolError("IO", "读取记忆失败：${it.message.orEmpty()}") }
    }

    fun save(name: String, content: String): Doc {
        if (content.length > MAX_DOC) {
            throw ToolError("TOO_LARGE", "单篇记忆不能超过 ${MAX_DOC / 1024}KB，请拆成多篇")
        }
        val dir = ensureRoot()
        val files = dir.listFiles()?.filter { it.isFile }?.size ?: 0
        val safe = safeName(name)
        val f = File(dir, safe)
        // 同名 = 更新（覆盖），不同名但内容撞车 = 重复。
        // 后一种才是真问题：模型换个标题把同一段偏好写第二遍，
        // 检索时两条都在、互相矛盾，用户只能一条条比对哪条是新的。
        if (!f.exists()) {
            val dup = findDuplicate(safe, content)
            if (dup != null) {
                throw ToolError(
                    "DUPLICATE",
                    "记忆库里已有内容相同的「${dup.title}」，没有重复写入。" +
                        "要更新它就保存同名（name=\"${dup.title}\"），要删就先让用户在记忆页删除。",
                )
            }
            if (files >= MAX_DOCS) {
                throw ToolError("TOO_MANY", "记忆已有 $MAX_DOCS 篇，先删掉一些不用的再写新的")
            }
        }
        val body = if (content.startsWith("#")) content else "# ${name.removeSuffix(".md")}\n\n$content"
        runCatching { f.writeText(body) }
            .getOrElse { throw ToolError("IO", "写入记忆失败：${it.message.orEmpty()}") }
        return Doc(f.name, f.length(), f.lastModified())
    }

    /** 和已有笔记"实质相同"的那一篇。只比前 4k 字，足够识别重复又不至于把整库读一遍 */
    private fun findDuplicate(exceptName: String, content: String): Doc? {
        val mine = fingerprint(content)
        if (mine.isEmpty()) return null
        for (d in list()) {
            if (d.name == exceptName) continue
            val f = File(root, d.name)
            if (f.length() > MAX_SCAN.toLong()) continue
            val other = runCatching { f.readText() }.getOrNull() ?: continue
            if (fingerprint(other) == mine) return d
        }
        return null
    }

    /** 去 #标题 行、去空白、小写 —— 只留"说了什么"，不管格式怎么写 */
    private fun fingerprint(content: String): String {
        val body = content.lineSequence()
            .dropWhile { it.isBlank() }
            .filterNot { it.startsWith("# ") }
            .joinToString("\n")
            .replace(Regex("\\s+"), "")
            .lowercase()
        return if (body.length < 12) "" else body.take(4096)
    }

    fun delete(name: String): Boolean {
        val f = File(ensureRoot(), safeName(name))
        return f.exists() && f.delete()
    }

    /**
     * 清空整个记忆库。
     *
     * 逐个文件删再删目录，最后确认目录真的没了：私有目录里删文件没有回收站，
     * "以为删了其实还在"是这类功能最不能出的错。
     *
     * @return 仍然存在（删除失败）的文件数
     */
    fun clearAll(): Int {
        val dir = root
        if (!dir.exists()) return 0
        var left = 0
        dir.listFiles()?.forEach { f ->
            if (!f.delete()) left++
        }
        if (left == 0) runCatching { dir.delete() }
        return left
    }

    /**
     * 关键词检索。
     *
     * 规则刻意简单：**按 `|` 分隔多个词，命中任意一个即算匹配**，大小写不敏感。
     * 不用正则 —— 模型给的正则经常把整个检索搞崩，而记忆检索要的是"找得到"不是"精确"。
     * 命中行带上行号，模型可以据此判断要不要 memory_read 全文。
     */
    fun search(query: String, limit: Int = 20): Pair<List<Hit>, Int> {
        val words = query.split('|', '｜')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(query.trim()) }
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList<Hit>() to 0

        val hits = ArrayList<Hit>()
        var scanned = 0
        for (doc in list()) {
            if (hits.size >= limit) break
            scanned++
            val f = File(root, doc.name)
            if (f.length() > MAX_SCAN.toLong()) continue
            val lines = runCatching { f.readLines() }.getOrNull() ?: continue
            for (idx in lines.indices) {
                if (hits.size >= limit) break
                val line = lines[idx]
                val l = line.lowercase()
                if (words.any { l.contains(it.lowercase()) }) {
                    hits.add(Hit(doc.title, idx + 1, line.trim().take(220)))
                }
            }
        }
        return hits to scanned
    }

    companion object {
        /** 单篇记忆上限 */
        const val MAX_DOC = 256 * 1024

        /** 记忆总篇数上限 */
        const val MAX_DOCS = 60

        /** 单篇超过这个大小就不参与关键词扫描（避免一次搜索把整库读进内存） */
        const val MAX_SCAN = 512 * 1024
    }
}
