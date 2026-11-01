package com.pocketagent.app.agent

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.text.Normalizer

/**
 * 路径守卫。
 *
 * 这一层不是"顺手做的校验"，而是整个文件工具的红线：
 * 模型可以把用户说的任何话当指令，一句话就能构造 `../../databases/xxx` 去读别处。
 * 所以这里只做一件事 —— 把用户给的字面路径压成一个**不含绝对路径、不含上跳、不含系统目录**
 * 的相对路径，压不出来就拒绝。真正的"是否越界"由 [WorkspaceFs] 解析出的 URI 二次校验。
 */
object PathGuard {

    private const val MAX_LEN = 512
    private const val MAX_SEG = 200

    fun normalize(raw: String): String {
        var p = raw.trim().replace('\\', '/')
        if (p.any { it == '\u0000' || it.isISOControl() }) {
            throw ToolError("BAD_PATH", "路径包含控制字符")
        }
        if (p.length > MAX_LEN) throw ToolError("BAD_PATH", "路径过长（上限 $MAX_LEN 字符）")
        if (p.startsWith("~")) throw ToolError("ESCAPE", "不接受以 ~ 开头的路径")
        if (p.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(p)) {
            throw ToolError("ESCAPE", "只能使用工作区内的相对路径，不能写绝对路径")
        }

        val segs = p.split('/')
            .filter { it.isNotBlank() && it != "." }
            .map { Normalizer.normalize(it, Normalizer.Form.NFC).trim() }

        for (s in segs) {
            if (s == "..") throw ToolError("ESCAPE", "路径不能包含 ..（禁止向上跳出工作区）")
            if (s.length > MAX_SEG) throw ToolError("BAD_PATH", "文件名过长")
        }

        val joined = segs.joinToString("/")
        val lower = joined.lowercase()
        // /Android/data 与 /Android/obb 受系统保护，SAF 也拿不到，直接挡掉免得给出误导性的报错
        if (lower == "android" ||
            lower.startsWith("android/data") ||
            lower.startsWith("android/obb")
        ) {
            throw ToolError("PROTECTED", "Android/data 与 Android/obb 属于系统保护目录，不能访问")
        }
        return joined
    }

    /** 路径的父目录，根目录返回空串 */
    fun parentOf(rel: String): String =
        if (rel.contains('/')) rel.substringBeforeLast('/') else ""

    fun nameOf(rel: String): String = rel.substringAfterLast('/')
}

/**
 * 工作区文件访问（Storage Access Framework）。
 *
 * 刻意不用 `DocumentFile.listFiles()`：它是"每个子项一次 IPC"，
 * 一个几百项的目录会把主线程拖到 ANR。这里全部走 `DocumentsContract.query`，
 * 一次拿全一层的列，然后按名字精确匹配来逐级下钻。
 */
class WorkspaceFs(private val ctx: Context, val treeUri: Uri) {

    data class Entry(
        val name: String,
        val docId: String,
        val uri: Uri,
        val isDir: Boolean,
        val size: Long,
        val mtime: Long,
    )

    private val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    private fun childUriOf(dirDocId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)

    private fun docUriOf(docId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)

    /**
     * 列出某目录的直接子项，**失败会抛异常**（授权失效是 SecurityException）。
     *
     * 调用方必须自己决定怎么处理：[rawChildren] 是"读不到就当空"的旧语义，
     * 但那条语义害过人 —— 授权被回收时界面会说"文件已经不在了"。
     */
    private fun queryChildren(dirDocId: String): List<Entry> {
        val id = dirDocId.ifBlank { rootDocId }
        val out = ArrayList<Entry>()
        ctx.contentResolver.query(childUriOf(id), projection, null, null, null)?.use { c ->
            val iId = c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val iName = c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val iMime = c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val iSize = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val iTime = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (c.moveToNext()) {
                val docId = if (iId >= 0) c.getString(iId) else continue
                val name = if (iName >= 0) c.getString(iName).orEmpty() else ""
                val mime = if (iMime >= 0) c.getString(iMime).orEmpty() else ""
                out.add(
                    Entry(
                        name = Normalizer.normalize(name, Normalizer.Form.NFC),
                        docId = docId,
                        uri = docUriOf(docId),
                        isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        size = if (iSize >= 0 && !c.isNull(iSize)) c.getLong(iSize) else -1L,
                        mtime = if (iTime >= 0 && !c.isNull(iTime)) c.getLong(iTime) else 0L,
                    )
                )
            }
        }
        return out
    }

    /** 列出某目录的直接子项；读失败就返回空（内部算法用，界面不要用它判断"有没有"） */
    private fun rawChildren(dirDocId: String): List<Entry> =
        runCatching { queryChildren(dirDocId) }.getOrDefault(emptyList())

    fun rootEntry(): Entry {
        val name = runCatching {
            ctx.contentResolver.query(docUriOf(rootDocId), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()
        return Entry(name.ifBlank { "工作区" }, rootDocId, docUriOf(rootDocId), true, -1L, 0L)
    }

    fun children(rel: String): List<Entry> {
        val dir = if (rel.isBlank()) rootEntry() else (resolve(rel) ?: throw ToolError("NOT_FOUND", "目录不存在：$rel"))
        if (!dir.isDir) throw ToolError("NOT_DIR", "这不是目录：$rel")
        return rawChildren(dir.docId)
    }

    /**
     * 逐级下钻解析相对路径。任何一级找不到就返回 null（由调用方决定是 NOT_FOUND 还是"可以新建"）。
     * 最终 URI 必须落在 tree 之内 —— 这是对 PathGuard 之外的第二道保险。
     */
    fun resolve(rel: String): Entry? {
        val norm = PathGuard.normalize(rel)
        if (norm.isBlank()) return rootEntry()
        var cur = rootEntry()
        for (seg in norm.split('/')) {
            if (!cur.isDir) return null
            val match = rawChildren(cur.docId).firstOrNull { it.name == seg } ?: return null
            cur = match
        }
        if (!cur.uri.toString().startsWith(treeUri.toString())) {
            throw ToolError("ESCAPE", "解析结果越出了工作区")
        }
        return cur
    }

    /**
     * 打开产出文件时用的解析结果。
     *
     * 存在的意义：**把"读不到"和"没有"分开**。以前这两种都返回 null，
     * 于是授权失效会被说成"文件已经不在了"（用户明明能在文件管理器里看到它）。
     */
    sealed class Lookup {
        data class Found(val entry: Entry) : Lookup()
        object NotFound : Lookup()
        data class Denied(val message: String) : Lookup()
        data class Failed(val message: String) : Lookup()
    }

    /**
     * 安全的逐级解析：任何一级读不到都如实区分成 Denied / Failed，
     * 而不是像 [resolve] 那样一律返回 null。
     */
    fun lookup(rel: String): Lookup {
        val norm = try {
            PathGuard.normalize(rel)
        } catch (e: Throwable) {
            return Lookup.Failed(e.message ?: "路径不合法")
        }
        if (norm.isBlank()) return Lookup.Found(rootEntry())
        var cur = rootEntry()
        for (seg in norm.split('/')) {
            if (!cur.isDir) return Lookup.NotFound
            val kids = try {
                queryChildren(cur.docId)
            } catch (e: SecurityException) {
                return Lookup.Denied("这个目录的读取授权已经失效")
            } catch (e: Throwable) {
                return Lookup.Failed(e.message ?: "读取目录失败")
            }
            val match = kids.firstOrNull { it.name == seg }
                ?: kids.firstOrNull { it.name.equals(seg, ignoreCase = true) }
                ?: kids.firstOrNull { looseName(it.name) == looseName(seg) }
                ?: return Lookup.NotFound
            cur = match
        }
        if (!cur.uri.toString().startsWith(treeUri.toString())) {
            return Lookup.Failed("解析结果越出了工作区")
        }
        return Lookup.Found(cur)
    }

    /**
     * 宽松查找：名字忽略大小写、空白、下划线、连字符，并在一层子目录里兜一圈。
     *
     * 为什么需要：SAF 在重名时会把新文件命名成 "报告 (1).pptx"；文件也可能被用户在
     * 文件管理器里改名。模型当初记下的路径不会跟着变，快捷方式于是悬空。
     */
    fun findLoosely(rel: String): Entry? {
        val norm = runCatching { PathGuard.normalize(rel) }.getOrNull() ?: return null
        val want = looseName(PathGuard.nameOf(norm))
        if (want.isEmpty()) return null
        val dirs = ArrayList<Entry>()
        val parent = PathGuard.parentOf(norm)
        val p = if (parent.isBlank()) rootEntry() else runCatching { resolve(parent) }.getOrNull()
        if (p != null && p.isDir) dirs.add(p)
        val root = rootEntry()
        if (dirs.none { it.docId == root.docId }) dirs.add(root)
        // 一层子目录：技能产出经常被放进子文件夹
        rawChildren(root.docId).filter { it.isDir }.take(40).forEach { dirs.add(it) }
        // 换扩展名也算同一份文件：Word/WPS 的"另存为"经常把 .docx 存成 .doc，
        // 截图里那种「卡片写着 .docx，目录里只有 .doc」就是这么来的。
        val stem = looseName(stemOf(PathGuard.nameOf(norm)))
        for (d in dirs) {
            val kids = rawChildren(d.docId)
            kids.firstOrNull { !it.isDir && looseName(it.name) == want }?.let { return it }
            if (stem.isNotEmpty()) {
                kids.firstOrNull { !it.isDir && looseName(stemOf(it.name)) == stem }?.let { return it }
            }
        }
        return null
    }

    /** 去掉最后一个扩展名后的主干名；没有扩展名就原样返回 */
    private fun stemOf(name: String): String = name.substringBeforeLast('.', name)

    /** 宽松比较用的名字：NFC + 小写 + 去掉空白/下划线/连字符 */
    private fun looseName(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFC)
            .lowercase()
            .replace(Regex("[\\s\\u00A0_]+"), "")
            .replace(Regex("[-—–]+"), "")

    // ─────────── 大文件：流式访问 ───────────
    //
    // 这一组方法存在的唯一理由：**不把整个文件读进内存**。
    // 之前所有路径都走 readBytes，于是 256KB 变成了一道硬墙 —— 一张手机照片就删不掉。
    // 现在按用途分流：看内容走窗口、找内容走 grep、备份/移动走流式复制。

    /** 原始输入流。调用方负责 close；这里不限制文件大小 */
    fun openInput(entry: Entry): java.io.InputStream =
        ctx.contentResolver.openInputStream(entry.uri)
            ?: throw ToolError("IO", "无法打开文件流（授权可能已失效，请重新选择工作区）")

    /** append=true 时以追加模式打开（SAF 的 "wa"） */
    fun openOutput(entry: Entry, append: Boolean = false): java.io.OutputStream =
        ctx.contentResolver.openOutputStream(entry.uri, if (append) "wa" else "wt")
            ?: throw ToolError("IO", "无法写入（授权可能已失效，请重新选择工作区）")

    /** 只读文件头若干字节：用来判断是不是二进制，不必把 10MB 读进来 */
    fun readHead(entry: Entry, n: Int = 4096): ByteArray {
        val buf = ByteArray(n)
        var total = 0
        runCatching {
            openInput(entry).use { ins ->
                while (total < n) {
                    val r = ins.read(buf, total, n - total)
                    if (r <= 0) break
                    total += r
                }
            }
        }.getOrElse { e ->
            throw ToolError("IO", "读取失败：${e.message.orEmpty()}")
        }
        return if (total == n) buf else buf.copyOf(total)
    }

    /** 读文件末尾 n 字节（判断"是否以换行结尾"这类问题，不必整读） */
    fun readTail(entry: Entry, n: Int = 1): ByteArray {
        if (entry.size in 0..n.toLong()) return readHead(entry, n)
        if (entry.size < 0) return ByteArray(0)
        val buf = ByteArray(n)
        var read = 0
        return runCatching {
            var skip = entry.size - n
            openInput(entry).use { ins ->
                while (skip > 0) {
                    val s = ins.skip(skip)
                    if (s <= 0) break
                    skip -= s
                }
                while (read < n) {
                    val r = ins.read(buf, read, n - read)
                    if (r <= 0) break
                    read += r
                }
            }
            buf.copyOf(read)
        }.getOrDefault(ByteArray(0))
    }

    /**
     * 流式复制到 out，返回字节数。
     * 备份（UndoStore）、跨目录移动都走这里 —— 内存占用恒定 64KB，与文件大小无关。
     */
    fun copyTo(entry: Entry, out: java.io.OutputStream, maxBytes: Long = MAX_COPY): Long {
        var total = 0L
        val buf = ByteArray(64 * 1024)
        runCatching {
            openInput(entry).use { ins ->
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > maxBytes) {
                        throw ToolError("TOO_LARGE", "单个文件超过 ${maxBytes / 1048576}MB，超出处理上限")
                    }
                    out.write(buf, 0, n)
                }
            }
        }.getOrElse { e ->
            if (e is ToolError) throw e
            throw ToolError("IO", "复制失败：${e.message.orEmpty()}")
        }
        return total
    }

    /** 流式复制到工作区里的另一个文件（移动用）。返回字节数 */
    fun copyInto(src: Entry, dst: Entry): Long {
        var n = 0L
        openOutput(dst, append = false).use { out -> n = copyTo(src, out) }
        return n
    }

    /**
     * 追加一段文本。写的是增量，不重写整个文件，
     * 所以给大日志补一行不需要把 10MB 读进来再写回去。
     */
    fun appendText(entry: Entry, text: String): Int {
        val bytes = text.toByteArray(Charsets.UTF_8)
        runCatching {
            openOutput(entry, append = true).use { it.write(bytes) }
        }.getOrElse { e ->
            if (e is ToolError) throw e
            throw ToolError("IO", "追加失败（部分厂商不支持追加模式）：${e.message.orEmpty()}")
        }
        return bytes.size
    }

    /** [readWindow] 的结果。totalLines = -1 表示文件太大、没有把行数数完 */
    data class Window(
        val text: String,
        val totalLines: Int,
        val fromLine: Int,
        val toLine: Int,
        /** 撞到单次返回字符上限（不是文件结束） */
        val cutByChars: Boolean,
        /** 后面还有内容没返回 */
        val hasMore: Boolean,
    )

    /**
     * 按行取一段窗口，流式扫描，不整文件进内存。
     *
     * 超过 [COUNT_LIMIT] 的文件不再统计总行数（数它要把整个文件读一遍），
     * 返回 totalLines = -1，UI 侧显示为"行数未统计"。
     */
    fun readWindow(
        entry: Entry,
        startLine: Int = 1,
        maxLines: Int = 400,
        maxChars: Int = MAX_WINDOW_CHARS,
    ): Window {
        val from = startLine.coerceAtLeast(1)
        val sb = StringBuilder()
        var lineNo = 0
        var taken = 0
        var lastTaken = from - 1
        var cutByChars = false
        var hasMore = false
        var total = 0
        val countAll = entry.size in 0..COUNT_LIMIT

        runCatching {
            openInput(entry).bufferedReader(Charsets.UTF_8).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    lineNo++
                    if (countAll) total = lineNo
                    if (lineNo < from) continue

                    if (taken >= maxLines) {
                        hasMore = true
                        if (!countAll) break else continue
                    }
                    // 单行过长（压缩过的 JSON / 生产日志）只留开头，否则一行就能把上下文吃光
                    val shown = if (line.length > MAX_LINE_CHARS) {
                        line.take(MAX_LINE_CHARS) + "…（本行共 ${line.length} 字符）"
                    } else line
                    if (sb.length + shown.length + 1 > maxChars) {
                        cutByChars = true
                        hasMore = true
                        if (!countAll) break else continue
                    }
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(shown)
                    taken++
                    lastTaken = lineNo
                }
            }
        }.getOrElse { e ->
            if (e is ToolError) throw e
            throw ToolError("IO", "读取失败：${e.message.orEmpty()}")
        }

        return Window(
            text = sb.toString(),
            totalLines = if (countAll) total else -1,
            fromLine = from,
            toLine = lastTaken,
            cutByChars = cutByChars,
            hasMore = hasMore,
        )
    }

    /** 一次 grep 命中的一行 */
    data class Hit(val line: Int, val text: String)

    /**
     * 按行正则匹配，流式扫描。
     * 这是"在 10MB 日志里找到报错那几行"的正确做法 —— 让模型读全文是不可能的。
     * 返回 (命中列表, 是否因为上限提前停止)。
     */
    fun grep(
        entry: Entry,
        re: Regex,
        maxHits: Int = 50,
        maxScanLines: Int = 200_000,
    ): Pair<List<Hit>, Boolean> {
        val hits = ArrayList<Hit>()
        var scanned = 0
        var stopped = false
        runCatching {
            openInput(entry).bufferedReader(Charsets.UTF_8).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    scanned++
                    if (scanned > maxScanLines) { stopped = true; break }
                    if (re.containsMatchIn(line)) {
                        hits.add(Hit(scanned, if (line.length > 300) line.take(300) + "…" else line))
                        if (hits.size >= maxHits) { stopped = true; break }
                    }
                }
            }
        }.getOrElse { e ->
            if (e is ToolError) throw e
            throw ToolError("IO", "读取失败：${e.message.orEmpty()}")
        }
        return hits to stopped
    }

    fun readBytes(entry: Entry, limit: Long = MAX_READ): ByteArray {
        val bytes = runCatching {
            ctx.contentResolver.openInputStream(entry.uri)?.use { ins ->
                val buf = ByteArray(64 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var total = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > limit) throw ToolError("TOO_LARGE", "文件超过 ${limit / 1024}KB，无法整体读取")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        }.getOrElse { e ->
            if (e is ToolError) throw e
            throw ToolError("IO", "读取失败：${e.message.orEmpty()}")
        }
        return bytes ?: throw ToolError("IO", "无法打开文件流（授权可能已失效，请重新选择工作区）")
    }

    fun writeBytes(entry: Entry, bytes: ByteArray) {
        runCatching {
            ctx.contentResolver.openOutputStream(entry.uri, "wt")?.use { it.write(bytes) }
                ?: throw ToolError("IO", "无法写入（授权可能已失效，请重新选择工作区）")
        }.getOrElse { e ->
            if (e is ToolError) throw e
            throw ToolError("IO", "写入失败：${e.message.orEmpty()}")
        }
    }

    /** 在指定目录下新建文件；同名已存在时由调用方先处理 */
    fun createFile(parentRel: String, name: String, mime: String = guessMime(name)): Entry {
        val parent = if (parentRel.isBlank()) rootEntry()
        else (resolve(parentRel) ?: throw ToolError("NOT_FOUND", "父目录不存在：$parentRel"))
        if (!parent.isDir) throw ToolError("NOT_DIR", "父路径不是目录：$parentRel")
        val docId = runCatching {
            DocumentsContract.createDocument(
                ctx.contentResolver,
                docUriOf(parent.docId),
                mime,
                name,
            )
        }.getOrNull() ?: throw ToolError("IO", "新建文件失败（可能没有写权限）")
        return Entry(name, DocumentsContract.getDocumentId(docId), docId, false, 0L, 0L)
    }

    /** 在指定目录下新建子目录（撤销空目录删除时用） */
    fun createDir(parentRel: String, name: String): Entry {
        val parent = if (parentRel.isBlank()) rootEntry()
        else (resolve(parentRel) ?: throw ToolError("NOT_FOUND", "父目录不存在：$parentRel"))
        val docId = runCatching {
            DocumentsContract.createDocument(
                ctx.contentResolver,
                docUriOf(parent.docId),
                DocumentsContract.Document.MIME_TYPE_DIR,
                name,
            )
        }.getOrNull() ?: throw ToolError("IO", "新建目录失败（可能没有写权限）")
        return Entry(name, DocumentsContract.getDocumentId(docId), docId, true, 0L, 0L)
    }

    fun delete(entry: Entry) {
        val ok = runCatching {
            DocumentsContract.deleteDocument(ctx.contentResolver, entry.uri)
        }.getOrDefault(false)
        if (!ok) throw ToolError("IO", "删除失败（目录非空或没有权限）")
    }

    fun rename(entry: Entry, newName: String): Entry {
        val uri = runCatching {
            DocumentsContract.renameDocument(ctx.contentResolver, entry.uri, newName)
        }.getOrNull() ?: throw ToolError("IO", "重命名失败（目标名可能已存在）")
        return Entry(newName, DocumentsContract.getDocumentId(uri), uri, entry.isDir, entry.size, entry.mtime)
    }

    companion object {
        /** 小文件整体读上限。只用于 diff、短文本预览这类"确实需要全文"的场合 */
        const val MAX_READ = 2L * 1024 * 1024
        /** 整文件重写上限。增量写入走 append，不受这个限制 */
        const val MAX_WRITE = 512L * 1024
        /** 单个文件的流式处理上限（备份 / 移动 / 复制） */
        const val MAX_COPY = 64L * 1024 * 1024
        /**
         * 一次 read_file 最多返回多少字符。
         *
         * 必须**小于**回灌上限（[ToolPolicy.MAX_RESULT_CHARS]）：结果一旦超过回灌上限，
         * `truncateForModel` 会保留头尾、省略中间，模型按工具的承诺读到的是一份缺了中间的文件。
         * 留 2k 余量给行号、省略提示这些包装字符。
         */
        const val MAX_WINDOW_CHARS = ToolPolicy.MAX_RESULT_CHARS - 2_000
        /** 单行展示上限，防止压缩过的 JSON 一行吃掉整个窗口 */
        const val MAX_LINE_CHARS = 2000
        /** 超过这个大小就不再统计总行数（数它要整读一遍，不划算） */
        const val COUNT_LIMIT = 4L * 1024 * 1024

        fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "md", "markdown", "txt", "log", "csv", "json", "xml", "yaml", "yml", "html", "css", "js", "ts", "kt", "java", "py", "sh", "ini", "toml" -> "text/plain"
            else -> "application/octet-stream"
        }

        fun looksBinary(bytes: ByteArray): Boolean {
            val n = minOf(bytes.size, 4096)
            for (i in 0 until n) if (bytes[i].toInt() == 0) return true
            return false
        }
    }
}
