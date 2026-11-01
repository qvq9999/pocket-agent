package com.pocketagent.app.agent

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * docx / xlsx 的读写。
 *
 * 不引第三方库：docx/xlsx 本质就是 **ZIP + OOXML**，JDK 自带的 zip 与 DOM 就够，
 * 少一层依赖，也少一处"手机上能不能跑"的不确定性。
 *
 * 四条必须记住的性质（每一条都是踩过坑才写下的）：
 *
 * 1. **docx 里的文字不是连着存的**。Word 会把 `{{姓名}}` 拆成 `{{姓` + `名}}`
 *    （拼写检查、格式微调、输入法都会导致拆分），所以替换必须在**段落级拼接后**做，
 *    再按字符区间映射回各个节点。
 * 2. **表格的行列号不等于视觉行列**。中文表单大量使用合并单元格，
 *    一个 `w:tc` 可能跨 3 列（`w:gridSpan`）或纵向延续（`w:vMerge`）——
 *    按下标数会把内容填到隔壁格，用户看到的却是"填错了"。
 *    所以读写都先建"栅格"：视觉坐标 → 真正持有内容的锚点单元格。
 * 3. **Excel 的日期是数字**。`2026-11-01` 存的是 46303 + 单元格的数字格式。
 *    只写文本会让它变成左对齐字符串，排序、公式、筛选全部失效。
 * 4. **写入前必须自检**。OOXML 是"改一个字节就可能打不开"的格式，
 *    写完立刻回读一遍（能解析 + 段落数不减），失败就整个放弃，绝不让用户拿到坏文件。
 */
object OfficeFiles {

    /** 单个 Office 文件允许的最大字节数。手机内存与快照成本之间取的折中 */
    const val MAX_BYTES = 30L * 1024 * 1024

    private const val NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val NS_S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    private const val NS_P = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private const val NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val NS_XML = "http://www.w3.org/XML/1998/namespace"

    /** 页眉、页脚、脚注这些"附加部件"。正文之外的文字基本都在这里 */
    private val EXTRA_PART = Regex("""word/(header|footer|footnotes|endnotes)\d*\.xml""")

    // ─────────────────────────── ZIP ───────────────────────────

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.isDirectory) continue
                out[e.name] = zin.readBytes()
            }
        }
        if (out.isEmpty()) throw ToolError("BAD_OFFICE", "这不是一个有效的 docx/xlsx（ZIP 内容为空）")
        return out
    }

    /** 用替换后的条目重新打包，其余条目原样复制 */
    private fun rezip(original: ByteArray, replacements: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream(original.size + 8192)
        ZipOutputStream(bos).use { zout ->
            ZipInputStream(ByteArrayInputStream(original)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name
                    val data = replacements[name] ?: zin.readBytes()
                    // 替换过的条目也要把原流读完，否则 nextEntry 位置不对
                    if (replacements.containsKey(name)) zin.readBytes()
                    val ne = ZipEntry(name)
                    ne.method = ZipEntry.DEFLATED
                    zout.putNextEntry(ne)
                    zout.write(data)
                    zout.closeEntry()
                }
            }
        }
        return bos.toByteArray()
    }

    // ─────────────────────────── XML ───────────────────────────

    private fun parse(bytes: ByteArray): Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        // 文档来自用户目录，可能是别人发来的：禁掉 DTD 与外部实体（XXE）
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun serialize(doc: Document): ByteArray {
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        // 不缩进：缩进会插入空白文本节点，Word 显示上可能多出空隙
        t.setOutputProperty(OutputKeys.INDENT, "no")
        val bos = ByteArrayOutputStream()
        t.transform(DOMSource(doc), StreamResult(bos))
        return bos.toByteArray()
    }

    private fun children(el: Element, local: String): List<Element> {
        val out = ArrayList<Element>()
        val kids = el.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n is Element && n.localName == local) out.add(n)
        }
        return out
    }

    /** 段落 / 单元格里的全部文本节点，按文档顺序 */
    private fun textNodes(el: Element): List<Element> {
        val out = ArrayList<Element>()
        fun walk(n: Node) {
            if (n is Element && n.localName == "t") { out.add(n); return }
            val kids = n.childNodes
            for (i in 0 until kids.length) walk(kids.item(i))
        }
        walk(el)
        return out
    }

    private fun Element.textOf(): String = textNodes(this).joinToString("") { it.textContent ?: "" }

    private fun setText(el: Element, text: String) {
        el.textContent = text
        // 带前后的空格必须声明 preserve，否则 Word 会把它吃掉
        runCatching { el.setAttributeNS(NS_XML, "xml:space", "preserve") }
    }

    /** w:xxx 属性。有的文档用了别的前缀声明，getAttributeNS 取不到时退回字符串形式 */
    private fun Element.w(name: String): String {
        val v = runCatching { getAttributeNS(NS_W, name) }.getOrDefault("")
        return v.ifEmpty { getAttribute("w:$name") }
    }

    // ─────────────────────────── DOCX 读 ───────────────────────────

    data class DocxView(val text: String, val paragraphs: Int, val tables: Int)

    /**
     * 输出结构化的可见文本：
     *   [p3] 正常段落          [p7 H2] 二级标题        [p9 ·] 列表项
     *   [t1.r2.c1] 张三        ← 表格单元格（视觉行列，从 1 开始）
     *   [t1.r2.c1(跨3列)] 基本情况
     *   ↑                      ← 这一格被上面的纵向合并覆盖
     * 模型据此既能看清要改哪一段，也能精确指向某个单元格。
     */
    fun docxRead(bytes: ByteArray, maxChars: Int = 24000): DocxView {
        val zip = unzip(bytes)
        val docXml = zip["word/document.xml"]
            ?: throw ToolError("BAD_OFFICE", "这个 docx 里没有 word/document.xml，可能不是 Word 文档")
        val doc = parse(docXml)
        val body = doc.getElementsByTagNameNS(NS_W, "body").item(0) as? Element
            ?: throw ToolError("BAD_OFFICE", "docx 结构异常：找不到 body")

        val sb = StringBuilder()
        var pCount = 0
        var tCount = 0
        var truncated = false

        fun append(line: String) {
            if (truncated) return
            if (sb.length + line.length + 2 > maxChars) {
                truncated = true
                return
            }
            sb.append(line).append('\n')
        }

        fun walkBlock(el: Element) {
            if (truncated) return
            when (el.localName) {
                "p" -> {
                    pCount++
                    val text = el.textOf()
                    // 空段落也标出来：用户说"第 5 行那个空"时，模型需要看到它确实存在
                    append("[p$pCount${paraSemantics(el)}] $text")
                }

                "tbl" -> {
                    tCount++
                    append("[表格 t$tCount]")
                    val grid = buildGrid(el)
                    grid.rowsLine(tCount).forEach { append("  $it") }
                }

                // 目录、内容控件之类：继续往里走，别把里面的文字漏掉
                else -> {
                    val kids = el.childNodes
                    for (i in 0 until kids.length) {
                        val n = kids.item(i)
                        if (n is Element) walkBlock(n)
                    }
                }
            }
        }

        val kids = body.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n is Element) walkBlock(n)
        }

        if (truncated) sb.append("…（内容过长已截断，共 $pCount 段 / $tCount 个表格）\n")

        // 结构摘要：模型只看文本时**不知道自己看不见什么**，于是会断言"文档里没有图"，
        // 或者改完文字把图注挤错位也毫无察觉。这里不展开内容，只报个数与边界，
        // 一行字的成本换掉一整类误判。
        val unseen = docxUnseenParts(zip)
        if (unseen.isNotBlank()) sb.append('\n').append(unseen).append('\n')

        // 页眉 / 页脚：以前完全不读，于是"把页眉的公司名改掉"这类需求，
        // 模型只能回答"文档里没有" —— 它根本看不见。
        val extra = readExtraParts(zip)
        if (extra.isNotEmpty()) {
            sb.append("\n（以下在页眉/页脚/脚注里，同样可以用 replacements 替换）\n")
            extra.forEach { (label, text) -> sb.append("[$label] $text\n") }
        }

        return DocxView(sb.toString().trimEnd(), pCount, tCount)
    }

    /** 段落语义标记：标题层级 / 列表项。模型靠它判断"改标题"还是"改正文" */
    private fun paraSemantics(p: Element): String {
        val pr = children(p, "pPr").firstOrNull() ?: return ""
        val style = children(pr, "pStyle").firstOrNull()?.w("val").orEmpty()
        val outline = children(pr, "outlineLvl").firstOrNull()?.w("val")?.toIntOrNull()
        val level = when {
            outline != null -> outline + 1
            style.startsWith("Heading", true) -> style.takeLastWhile { it.isDigit() }.toIntOrNull() ?: 1
            style.startsWith("标题") -> style.takeLastWhile { it.isDigit() }.toIntOrNull() ?: 1
            style == "Title" -> 1
            else -> 0
        }
        val isList = children(pr, "numPr").isNotEmpty()
        return buildString {
            if (level in 1..6) append(" H$level")
            if (isList) append(" ·")
        }
    }

    /**
     * 这份文档里"本工具看不见但确实存在"的东西。
     *
     * 只数不解析：图片的媒体文件在 word/media/ 下，批注在 comments.xml，
     * 修订是 document.xml 里的 w:ins/w:del。报出来是为了让模型知道边界，
     * 不是为了让它去改 —— 这些本工具都不支持。
     */
    private fun docxUnseenParts(zip: Map<String, ByteArray>): String {
        val media = zip.keys.count { it.startsWith("word/media/") }
        val hasComments = zip.keys.any { it == "word/comments.xml" }
        val body = zip["word/document.xml"]
        val revisions = if (body == null) 0 else runCatching {
            val t = String(body, Charsets.UTF_8)
            Regex("<w:(ins|del)[ >]").findAll(t).count()
        }.getOrDefault(0)
        val parts = ArrayList<String>()
        if (media > 0) parts.add("$media 张图片/媒体")
        if (hasComments) parts.add("批注")
        if (revisions > 0) parts.add("$revisions 处修订未处理")
        if (parts.isEmpty()) return ""
        return "（另含 ${parts.joinToString("、")}，本工具不读取也不修改；" +
            "需要动它们时请如实告诉用户）"
    }

    private fun readExtraParts(zip: Map<String, ByteArray>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for ((name, raw) in zip) {
            if (!EXTRA_PART.matches(name)) continue
            val doc = runCatching { parse(raw) }.getOrNull() ?: continue
            val ps = doc.getElementsByTagNameNS(NS_W, "p")
            val text = buildString {
                for (i in 0 until ps.length) {
                    val t = (ps.item(i) as? Element)?.textOf().orEmpty().trim()
                    if (t.isNotEmpty()) { if (isNotEmpty()) append(" | "); append(t) }
                }
            }
            if (text.isNotBlank()) out.add(labelOfPart(name) to text.take(600))
        }
        return out
    }

    private fun labelOfPart(name: String): String {
        val base = name.substringAfterLast('/')
        val n = base.filter { it.isDigit() }
        return when {
            base.startsWith("header") -> "页眉" + if (n.isNotEmpty()) " $n" else ""
            base.startsWith("footer") -> "页脚" + if (n.isNotEmpty()) " $n" else ""
            base.startsWith("footnotes") -> "脚注"
            else -> "尾注"
        }
    }

    // ─────────────────────── 表格栅格（合并单元格） ───────────────────────

    /**
     * 一张表的"视觉栅格"。
     *
     * [at] 把每个视觉坐标映射到**真正持有内容的那个 tc**（合并区域的锚点），
     * [cover] 反过来记录每个锚点覆盖了哪些坐标，用来算"跨几行跨几列"。
     * 读和写都走这张栅格，才不会把 r2.c3 的内容写到隔壁格去。
     */
    private class Grid(
        val rows: Int,
        val cols: Int,
        val at: Map<Pair<Int, Int>, Element>,
        val cover: Map<Element, List<Pair<Int, Int>>>,
    ) {
        /** 每行渲染成一行文本（含合并标记），由 docxRead 直接输出 */
        fun rowsLine(table: Int): List<String> {
            val out = ArrayList<String>(rows)
            for (row in 1..rows) {
                val parts = ArrayList<String>()
                var col = 1
                while (col <= cols) {
                    val anchor = at[row to col]
                    if (anchor == null) { col++; continue }
                    val cover = cover[anchor] ?: listOf(row to col)
                    val minR = cover.minOf { it.first }
                    val maxR = cover.maxOf { it.first }
                    val minC = cover.minOf { it.second }
                    val maxC = cover.maxOf { it.second }
                    if (row == minR && col == minC) {
                        val rSpan = maxR - minR + 1
                        val cSpan = maxC - minC + 1
                        val mark = buildString {
                            if (cSpan > 1) append("跨${cSpan}列")
                            if (rSpan > 1) { if (isNotEmpty()) append('/'); append("跨${rSpan}行") }
                        }
                        // 标签本身保持纯净（[t1.r2.c2]），合并信息挂在文本后面，
                        // 这样模型按标签取坐标时不需要先剥掉括号里的注解
                        val suffix = if (mark.isNotEmpty()) "〔$mark〕" else ""
                        parts += "[t$table.r$row.c$col] ${cellText(anchor)}$suffix"
                    } else {
                        // 被合并覆盖的位置：给个记号，否则看起来像"空单元格"
                        parts += "↑"
                    }
                    col = maxC + 1
                }
                out += parts.joinToString(" │ ")
            }
            return out
        }
    }

    private fun tcProps(tc: Element): Element? = children(tc, "tcPr").firstOrNull()

    /**
     * 单元格的可见文字，段与段之间用 `⏎` 标出来。
     *
     * 以前直接把所有段落拼成一串：一个两行的格子在读出来时变成"第二行第一行"糊在一起，
     * 模型照抄这串文字去 docx_fill 里找，而替换是按**段落**匹配的，
     * 于是永远 NO_MATCH，怎么重试都过不去。标出断点后它才知道该一行一行改。
     */
    private fun cellText(tc: Element): String =
        children(tc, "p").joinToString("⏎") { it.textOf() }

    private fun gridSpanOf(tc: Element): Int {
        val pr = tcProps(tc) ?: return 1
        val g = children(pr, "gridSpan").firstOrNull() ?: return 1
        return (g.w("val").toIntOrNull() ?: 1).coerceIn(1, 64)
    }

    /** "restart"（合并起点）/ "continue"（延续上一格）/ null（普通格） */
    private fun vMergeOf(tc: Element): String? {
        val pr = tcProps(tc) ?: return null
        val v = children(pr, "vMerge").firstOrNull() ?: return null
        return if (v.w("val").equals("restart", true)) "restart" else "continue"
    }

    private fun buildGrid(table: Element): Grid {
        val trs = children(table, "tr")
        val at = HashMap<Pair<Int, Int>, Element>()
        val cover = LinkedHashMap<Element, MutableList<Pair<Int, Int>>>()
        // 视觉列 → 该列上正在纵向合并的锚点
        val vAnchor = HashMap<Int, Element>()
        var maxCol = 0

        trs.forEachIndexed { ri, tr ->
            val row = ri + 1
            var col = 1
            for (tc in children(tr, "tc")) {
                val span = gridSpanOf(tc)
                val v = vMergeOf(tc)
                val anchor: Element = if (v == "continue") {
                    // 正常文档一定有起点；异常文档找不到起点就让这一格自成锚点，至少不丢内容
                    vAnchor[col] ?: tc
                } else {
                    if (v == "restart") for (c in col until col + span) vAnchor[c] = tc
                    else for (c in col until col + span) vAnchor.remove(c)
                    tc
                }
                for (c in col until col + span) {
                    at[row to c] = anchor
                    cover.getOrPut(anchor) { ArrayList() }.add(row to c)
                }
                col += span
            }
            maxCol = maxOf(maxCol, col - 1)
        }
        return Grid(trs.size, maxCol, at, cover)
    }

    // ─────────────────────────── DOCX 写 ───────────────────────────

    /** 一次表格单元格编辑。行列从 1 开始，是**视觉**行列，与 docx_read 输出的标签一致 */
    data class CellEdit(val table: Int, val row: Int, val col: Int, val value: String)

    /**
     * 批量替换 + 按单元格填值。**全部成功或有失败就整体不生效** ——
     * 半成功最难排查：用户以为填好了，实际只填了一半。
     *
     * 替换同时作用于正文、页眉、页脚、脚注；单元格编辑只在正文表格里。
     */
    fun docxFill(
        bytes: ByteArray,
        replacements: List<Pair<String, String>>,
        cells: List<CellEdit>,
    ): ByteArray {
        val zip = unzip(bytes)
        val docXml = zip["word/document.xml"]
            ?: throw ToolError("BAD_OFFICE", "这个 docx 里没有 word/document.xml")
        val doc = parse(docXml)

        // 要改写的所有部件：正文 + 页眉页脚。解析不了的部件跳过，不让整份失败
        val parts = ArrayList<Pair<String, Document>>()
        parts += "word/document.xml" to doc
        for ((name, raw) in zip) {
            if (!EXTRA_PART.matches(name)) continue
            runCatching { parse(raw) }.getOrNull()?.let { parts += name to it }
        }

        val misses = ArrayList<String>()
        val pBefore = doc.getElementsByTagNameNS(NS_W, "p").length
        val tBefore = doc.getElementsByTagNameNS(NS_W, "tbl").length
        // setCellText 会把"只留第一段"时多出来的段落删掉，这是**有意**的收缩。
        // 自检必须把这份账算进去，否则任何"填一个原本有两段的单元格"都会被判成
        // 结构损坏 —— 模型无论怎么重试都过不去，最后只会把步数烧光。
        var collapsedP = 0
        // 只重打包**真正改过**的部件：没动的页眉/脚注保持原始字节，
        // 少一次 XML 重排就少一分"改完排版变了"的风险
        val changed = HashSet<String>()

        // ① 文本替换：在段落级拼接后匹配，能跨 run
        //
        // 段落文本按部件缓存一份：以前每个 find 都要把整份文档的 DOM 重新遍历拼接，
        // 50 处替换 × 5000 段 = 25 万次全段遍历，填一张大表能卡十几秒。
        // 缓存后每个 find 只剩字符串比较，改过的段落就地作废（置 null 懒重算）。
        class Idx(val paras: ArrayList<Element>, val texts: Array<String?>)
        val index = parts.map { (_, d) ->
            val pl = d.getElementsByTagNameNS(NS_W, "p")
            val list = ArrayList<Element>(pl.length)
            for (i in 0 until pl.length) (pl.item(i) as? Element)?.let { list.add(it) }
            Idx(list, arrayOfNulls(list.size))
        }
        for ((find, replace) in replacements) {
            if (find.isEmpty()) {
                misses.add("find 不能为空")
                continue
            }
            val looseNeedle = find.filter { !it.isWhitespace() }
            var hit = 0
            index.forEachIndexed { pi, idx ->
                for (i in idx.paras.indices) {
                    var t = idx.texts[i]
                    if (t == null) {
                        t = idx.paras[i].textOf()
                        idx.texts[i] = t
                    }
                    // 廉价筛查：命中不了就完全不必进 replaceInParagraph 走 DOM
                    val candidate = t.contains(find) ||
                        (looseNeedle.isNotEmpty() &&
                            t.length > looseNeedle.length &&
                            t.filter { c -> !c.isWhitespace() }.contains(looseNeedle))
                    if (!candidate) continue
                    val n = replaceInParagraph(idx.paras[i], find, replace)
                    if (n > 0) {
                        idx.texts[i] = null
                        hit += n
                        changed.add(parts[pi].first)
                    }
                }
            }
            if (hit == 0) misses.add("找不到「${find.take(30)}」")
        }

        // ② 表格单元格：视觉坐标 → 锚点单元格（合并区域落到锚点上，不落进隐藏格）
        if (cells.isNotEmpty()) {
            val tables = doc.getElementsByTagNameNS(NS_W, "tbl")
            for (e in cells) {
                val tbl = tables.item(e.table - 1) as? Element
                if (tbl == null) { misses.add("第 ${e.table} 个表格不存在"); continue }
                val grid = buildGrid(tbl)
                val cell = grid.at[e.row to e.col]
                if (cell == null) {
                    misses.add(
                        "表格 t${e.table} 没有第 ${e.row} 行第 ${e.col} 列" +
                            "（该表共 ${grid.rows} 行 × ${grid.cols} 列）",
                    )
                    continue
                }
                collapsedP += setCellText(doc, cell, e.value)
                changed.add("word/document.xml")
            }
        }

        if (misses.isNotEmpty()) {
            val hint = placeholderHints(doc)
            throw ToolError(
                "NO_MATCH",
                "以下操作没有生效，整个文件未做修改：" + misses.joinToString("；") +
                    (if (hint.isNotEmpty()) "\n$hint" else "") +
                        // 明确写"怎么改"，否则模型最容易做的动作就是原样再提交一次
                        "\n请按失败原因**改掉那一处**再提交：坐标超范围的改成上面给的行列范围内，" +
                        "文本找不到的换成上面列出的实际占位符。重试时务必只改失败的条目，" +
                        "已经能命中的条目保持原样一起提交即可。",
            )
        }

        val out = HashMap<String, ByteArray>()
        for ((name, d) in parts) {
            if (name in changed) out[name] = serialize(d)
        }

        // ③ 自检：段落/表格数量不能变少（除本函数自己压掉的段），且整包必须还能解析
        val pAfter = doc.getElementsByTagNameNS(NS_W, "p").length
        val tAfter = doc.getElementsByTagNameNS(NS_W, "tbl").length
        if (pAfter + collapsedP < pBefore || tAfter < tBefore) {
            throw ToolError(
                "VALIDATE_FAILED",
                "改写后结构不完整（段落 $pAfter/$pBefore、表格 $tAfter/$tBefore），已放弃修改",
            )
        }

        val result = rezip(bytes, out)
        runCatching { docxRead(result, 200_000) }.getOrElse {
            throw ToolError("VALIDATE_FAILED", "改写后的文件无法重新解析（${it.message}），已放弃修改")
        }
        return result
    }

    /**
     * 找不到占位符时，把文档里"看起来像占位符"的片段列出来。
     *
     * 模型最常见的失败不是改错，而是**猜不中锚点**：`{{ 姓名 }}` 还是 `{{姓名}}`、
     * 或者模板用的是 `____`。列出来它一次就能改对，省掉好几轮往返。
     */
    private fun placeholderHints(doc: Document): String {
        val re = Regex("""\{\{[^{}]{1,40}}}|【[^】]{1,20}】|［[^］]{1,20}］|_{3,}|□{2,}""")
        val seen = LinkedHashSet<String>()
        val ps = doc.getElementsByTagNameNS(NS_W, "p")
        for (i in 0 until ps.length) {
            if (seen.size >= 20) break
            val t = (ps.item(i) as? Element)?.textOf() ?: continue
            re.findAll(t).forEach { m -> if (seen.size < 20) seen.add(m.value) }
        }
        if (seen.isEmpty()) return ""
        return "文档里实际存在的疑似占位符（可直接用作 find）：" + seen.joinToString("、")
    }

    /**
     * 审批卡片用：把"改哪里、原来是什么、改成什么"算出来。
     *
     * 不这么做的后果是用户只看到一行 `张三 → 李四`，根本判断不了改的是哪张表哪一格，
     * 只能点同意然后祈祷 —— 审批就退化成了仪式。
     */
    data class EditPreview(val label: String, val before: String, val after: String)

    fun previewEdits(
        bytes: ByteArray,
        replacements: List<Pair<String, String>>,
        cells: List<CellEdit>,
        limit: Int = 8,
    ): List<EditPreview> {
        val zip = unzip(bytes)
        val out = ArrayList<EditPreview>()

        val parts = LinkedHashMap<String, Document>()
        zip["word/document.xml"]?.let { raw ->
            runCatching { parse(raw) }.getOrNull()?.let { parts["word/document.xml"] = it }
        }
        for ((name, raw) in zip) {
            if (!EXTRA_PART.matches(name)) continue
            runCatching { parse(raw) }.getOrNull()?.let { parts[name] = it }
        }

        // 文本替换：定位到第一个命中的段落（正文给 [pN]，页眉页脚给 [页眉 1 pN]）
        for ((find, replace) in replacements) {
            if (out.size >= limit) break
            var placed = false
            for ((name, d) in parts) {
                if (placed) break
                val ps = d.getElementsByTagNameNS(NS_W, "p")
                for (i in 0 until ps.length) {
                    val text = (ps.item(i) as? Element)?.textOf() ?: continue
                    if (!text.contains(find)) continue
                    val where = if (name == "word/document.xml") "[p${i + 1}]"
                    else "[" + labelOfPart(name) + " p${i + 1}]"
                    out += EditPreview(
                        where,
                        text.trim().take(80),
                        text.replace(find, replace).trim().take(80),
                    )
                    placed = true
                    break
                }
            }
            if (!placed) out += EditPreview("（未找到）", find.take(40), replace.take(40))
        }

        // 单元格：从栅格取旧值，标签同时给出表/行/列
        val doc = parts["word/document.xml"]
        if (doc != null && cells.isNotEmpty()) {
            val tables = doc.getElementsByTagNameNS(NS_W, "tbl")
            for (c in cells) {
                val tbl = tables.item(c.table - 1) as? Element ?: continue
                val grid = buildGrid(tbl)
                val cell = grid.at[c.row to c.col] ?: continue
                val cover = grid.cover[cell] ?: emptyList()
                val minR = cover.minOfOrNull { it.first } ?: c.row
                val minC = cover.minOfOrNull { it.second } ?: c.col
                val mark = if (cover.size > 1) "（合并单元格）" else ""
                out += EditPreview(
                    "[t${c.table}.r$minR.c$minC]$mark",
                    cell.textOf().trim().take(40).ifEmpty { "（空）" },
                    c.value.take(40).ifEmpty { "（清空）" },
                )
            }
        }
        return out
    }

    /**
     * 在一个段落里做替换，返回替换次数。
     *
     * 关键：先把整段所有 `<w:t>` 拼成一条字符串再找匹配，
     * 找到后按字符区间把新文本放回**第一个受影响的节点**，其余节点留空。
     * 这样 `{{姓` + `名}}` 这种被 Word 拆开的占位符也能一次替换掉。
     */
    private fun replaceInParagraph(p: Element, find: String, replace: String): Int {
        // 先按原样精确匹配；一次都没命中再退回"忽略空白差异"。
        // 退这一步是因为最常见的失败不是找错锚点，而是**空白对不上**：
        // Word 里看到的「{{ 姓名 }}」在 XML 里可能是全角空格、nbsp，或者被拆成
        // 三段 run（每段自带一个空格）。模型照 docx_read 的输出抄 find 时，
        // 肉眼分不出这些差异，于是同一处反复报 NO_MATCH。
        val exact = replaceExact(p, find, replace)
        if (exact > 0) return exact
        return replaceLoose(p, find, replace)
    }

    /** 逐节点拼接后做精确子串替换，可跨 run */
    private fun replaceExact(p: Element, find: String, replace: String): Int {
        var total = 0
        while (true) {
            val nodes = textNodes(p)
            if (nodes.isEmpty()) break
            val full = nodes.joinToString("") { it.textContent ?: "" }
            val start = full.indexOf(find)
            if (start < 0) break
            splice(p, start, start + find.length, replace)
            total++
            // 防止 replace 里包含 find 时无限循环
            if (replace.contains(find)) break
        }
        return total
    }

    /**
     * 忽略空白的宽松替换。
     *
     * 做法：把段落文本压成"只保留非空白字符"的序列，并记下每个字符在原文里的下标；
     * 在压缩后的序列上找 find（同样压掉空白），命中后回查原文的起止位置再替换。
     * 这样换进去的文本仍然是连续的，不会把原文的空格节奏打乱到别的段落里。
     */
    private fun replaceLoose(p: Element, find: String, replace: String): Int {
        val needle = find.filter { !it.isWhitespace() }
        if (needle.isEmpty()) return 0
        val needleOfReplace = replace.filter { !it.isWhitespace() }
        var total = 0
        while (total < 200) {
            val nodes = textNodes(p)
            if (nodes.isEmpty()) break
            val full = nodes.joinToString("") { it.textContent ?: "" }
            val norm = StringBuilder(full.length)
            val map = ArrayList<Int>(full.length)
            full.forEachIndexed { i, ch ->
                if (!ch.isWhitespace()) {
                    norm.append(ch)
                    map.add(i)
                }
            }
            val hit = norm.indexOf(needle)
            if (hit < 0) break
            val from = map[hit]
            val to = map[hit + needle.length - 1] + 1
            splice(p, from, to, replace)
            total++
            if (needleOfReplace.contains(needle)) break
        }
        return total
    }

    /** 把 [start, end) 这一段文本换成 replace（可跨多个 w:t 节点） */
    /**
     * 把 [start, end) 这一段文本换成 replace（可跨多个 w:t 节点）。
     *
     * 新文本按**各节点被覆盖的字符数**成比例分配，而不是整段落进第一个节点：
     * 「请把**这份**报表发出」在 XML 里就是三个 run，全塞进第一个 run 会让中间的
     * 加粗与颜色在替换后凭空消失 —— 用户看到的就是"改完一个字，半行都不粗了"。
     * 按比例分完，每个 run 的 rPr 原样保留。
     */
    private fun splice(p: Element, start: Int, end: Int, replace: String) {
        class Touch(val node: Element, val from: Int, val to: Int)
        val touches = ArrayList<Touch>()
        var offset = 0
        for (t in textNodes(p)) {
            val s = t.textContent ?: ""
            val nodeStart = offset
            val nodeEnd = offset + s.length
            offset = nodeEnd
            if (nodeEnd <= start || nodeStart >= end) continue
            touches.add(
                Touch(
                    t,
                    (start - nodeStart).coerceIn(0, s.length),
                    (end - nodeStart).coerceIn(0, s.length),
                )
            )
        }
        if (touches.isEmpty()) return
        val covered = touches.sumOf { it.to - it.from }
        var given = 0
        touches.forEachIndexed { i, touch ->
            val s = touch.node.textContent ?: ""
            val prefix = s.substring(0, touch.from.coerceAtMost(s.length))
            val suffix = s.substring(touch.to.coerceAtMost(s.length))
            // 最后一个节点拿走全部余量：整数除法会丢字符，宁可让它偏一点也不能少字
            val share = if (i == touches.lastIndex) replace.length - given
            else if (covered <= 0) 0
            else (replace.length.toLong() * (touch.to - touch.from) / covered).toInt()
            given += share
            setText(touch.node, prefix + replace.substring(given - share, given) + suffix)
        }
    }

    /**
     * 把单元格内容换成 value，尽量保留原有段落与运行属性。
     *
     * 值里带换行时按**一行一段**铺开：以前只留第一个段落，于是"地址：XX市\n电话：139…"
     * 这种两行内容会被压成一行，而模板里那个格子本来就是设计成两行的。
     * 复用已有段落（而不是新建）是关键 —— 新段落没有原段的 pPr，居中/缩进会丢。
     *
     * 返回**被删掉的段落数**：调用方（docxFill 的结构自检）必须知道这份收缩是
     * 有意的，否则会把正常填写误判成"段落变少 = 文档损坏"。
     */
    private fun setCellText(doc: Document, cell: Element, value: String): Int {
        val ps = children(cell, "p").toMutableList()
        val first = ps.firstOrNull()
        if (first == null) {
            val p = doc.createElementNS(NS_W, "w:p")
            val r = doc.createElementNS(NS_W, "w:r")
            val t = doc.createElementNS(NS_W, "w:t")
            setText(t, value)
            r.appendChild(t)
            p.appendChild(r)
            cell.appendChild(p)
            return 0
        }
        val lines = value.split('\n').ifEmpty { listOf("") }
        // 段落不够就克隆第一个补齐：克隆带上原段的 pPr，格式因此能延续
        while (ps.size < lines.size) {
            val clone = first.cloneNode(true) as Element
            cell.appendChild(clone)
            ps.add(clone)
        }
        var dropped = 0
        ps.drop(lines.size).forEach {
            cell.removeChild(it)
            dropped++
        }
        lines.forEachIndexed { i, line ->
            val ts = textNodes(ps[i])
            if (ts.isEmpty()) {
                val r = ps[i].ownerDocument.createElementNS(NS_W, "w:r")
                val t = ps[i].ownerDocument.createElementNS(NS_W, "w:t")
                setText(t, line)
                r.appendChild(t)
                ps[i].appendChild(r)
            } else {
                setText(ts[0], line)
                ts.drop(1).forEach { setText(it, "") }
            }
        }
        return dropped
    }

    // ─────────────────────────── PPTX 读 / 改 ───────────────────────────

    data class PptxView(val text: String, val slides: Int, val shapes: Int)

    /**
     * 一处幻灯片文本编辑。
     *
     * [slide] / [shape] 都是 1-based，与 [pptxRead] 输出的 `[s页.框号]` 标签一致。
     * [find] 留空 = 把整个文本框换成 [replace]；给了 find 就只做精确替换（可跨 run）。
     */
    data class PptEdit(val slide: Int, val shape: Int, val find: String, val replace: String)

    /**
     * 一页里的可编辑形状，按文档顺序。
     *
     * 只取 spTree 的**直接子节点**：组合形状（grpSp）里的成员不单独编号，
     * 否则读侧给的坐标会和写侧找到的形状错位 —— 那种 bug 一旦出，用户看到的就是
     * "改第 2 个框，结果动到了第 3 个"。
     */
    private fun pptShapes(slide: Element): List<Element> {
        val tree = children(slide, "cSld").firstOrNull()?.let { children(it, "spTree").firstOrNull() }
            ?: return emptyList()
        return children(tree, "sp") + children(tree, "graphicFrame")
    }

    /** 一个形状里的可见文字：文本框按段落拼，表格按"行 | 单元格"拼 */
    private fun pptShapeText(shape: Element): String {
        // 注意判的是形状**自己**是什么：graphicFrame 就是表格载体，
        // 找它的子节点里有没有 graphicFrame 永远为 false，表格会被读成空
        if (shape.localName == "graphicFrame") {
            val nodes = shape.getElementsByTagNameNS(NS_A, "tbl")
            val tbl = nodes.item(0) as? Element ?: return ""
            return children(tbl, "tr").joinToString("\n") { tr ->
                children(tr, "tc").joinToString(" | ") { it.textOf() }
            }
        }
        val body = children(shape, "txBody").firstOrNull() ?: return ""
        return children(body, "p").joinToString("\n") { it.textOf() }
    }

    fun pptxRead(bytes: ByteArray, maxChars: Int = 18000): PptxView {
        val zip = unzip(bytes)
        val names = zip.keys
            .filter { Regex("^ppt/slides/slide\\d+\\.xml$").matches(it) }
            .sortedBy { it.substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: 0 }
        if (names.isEmpty()) throw ToolError("BAD_OFFICE", "这个 pptx 里没有幻灯片，可能不是演示文稿")
        val sb = StringBuilder()
        var truncated = false
        var shapeTotal = 0
        names.forEachIndexed { idx, name ->
            if (truncated) return@forEachIndexed
            val slide = runCatching { parse(zip[name]!!) }.getOrNull()
                ?.let { it.getElementsByTagNameNS(NS_P, "sld").item(0) as? Element } ?: return@forEachIndexed
            val shapes = pptShapes(slide)
            shapeTotal += shapes.size
            if (sb.length + 24 > maxChars) { truncated = true; return@forEachIndexed }
            sb.append("[第 ${idx + 1} 页] 共 ${shapes.size} 个可编辑形状\n")
            shapes.forEachIndexed { si, sh ->
                val t = pptShapeText(sh)
                val isTable = sh.localName == "graphicFrame"
                val line = "  [s${idx + 1}.${si + 1}${if (isTable) " 表格" else ""}] " +
                    t.replace("\n", "\n        ")
                if (sb.length + line.length + 2 > maxChars) { truncated = true; return@forEachIndexed }
                sb.append(line).append('\n')
            }
        }
        if (truncated) sb.append("…（内容过长已截断，共 ${names.size} 页）\n")
        return PptxView(sb.toString().trimEnd(), names.size, shapeTotal)
    }

    /**
     * 批量改幻灯片文字。**全部成功或有失败就整体不生效**，与 [docxFill] 同一条规矩。
     *
     * 只重打包真正改过的那几页，其余保持原始字节：少一次 XML 重排就少一分"改完版式变了"。
     */
    fun pptxEdit(bytes: ByteArray, edits: List<PptEdit>): ByteArray {
        if (edits.isEmpty()) throw ToolError("BAD_ARGS", "edits 不能为空")
        val zip = unzip(bytes)
        val names = zip.keys
            .filter { Regex("^ppt/slides/slide\\d+\\.xml$").matches(it) }
            .sortedBy { it.substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: 0 }
        if (names.isEmpty()) throw ToolError("BAD_OFFICE", "这个 pptx 里没有幻灯片")

        val misses = ArrayList<String>()
        val changed = HashMap<String, ByteArray>()
        // 同一页的多个编辑要在同一份 DOM 上依次做完，否则后一个编辑会覆盖前一个
        edits.groupBy { it.slide }.forEach { (slideNo, list) ->
            val part = names.getOrNull(slideNo - 1)
            if (part == null) {
                list.forEach { misses.add("没有第 $slideNo 页（这份共 ${names.size} 页）") }
                return@forEach
            }
            if (part in changed) {
                // 上面已经解过；这里重新解一次以保证拿到的是"改到一半"的版本
                zip[part] = changed[part]!!
            }
            val doc = runCatching { parse(zip[part]!!) }.getOrElse {
                list.forEach { misses.add("第 $slideNo 页解析失败") }
                return@forEach
            }
            val slide = doc.getElementsByTagNameNS(NS_P, "sld").item(0) as? Element
            if (slide == null) { list.forEach { misses.add("第 $slideNo 页结构异常") }; return@forEach }
            val shapes = pptShapes(slide)
            var touched = false
            for (e in list) {
                val sh = shapes.getOrNull(e.shape - 1)
                if (sh == null) {
                    misses.add("第 $slideNo 页没有第 ${e.shape} 个形状（这一页共 ${shapes.size} 个）")
                    continue
                }
                val hit = if (e.find.isEmpty()) {
                    pptSetShapeText(doc, sh, e.replace)
                    true
                } else {
                    pptReplaceInShape(sh, e.find, e.replace) > 0
                }
                if (!hit) misses.add("第 $slideNo 页第 ${e.shape} 个形状里找不到「${e.find.take(30)}」")
                else touched = true
            }
            if (touched) changed[part] = serialize(doc)
        }
        if (misses.isNotEmpty()) {
            throw ToolError(
                "NO_MATCH",
                "以下操作没有生效，整个文件未做修改：" + misses.distinct().joinToString("；") +
                    "\n先用 pptx_read 看清每页有哪些形状、里面是什么文字，再照 [s页.框号] 改；" +
                    "find 必须是读到的原文（同一次调用里只改失败那一条，其余保持原样一起提交）。",
            )
        }
        return rezip(bytes, changed)
    }

    /** 把整个形状换成 value：写进第一个 a:t，其余清空 —— 保留这个框原有的字体与位置 */
    private fun pptSetShapeText(doc: Document, shape: Element, value: String) {
        val ts = textNodes(shape)
        if (ts.isEmpty()) {
            val body = children(shape, "txBody").firstOrNull()
                ?: doc.createElementNS(NS_P, "p:txBody").also { shape.appendChild(it) }
            val p = doc.createElementNS(NS_A, "a:p")
            val r = doc.createElementNS(NS_A, "a:r")
            val t = doc.createElementNS(NS_A, "a:t")
            setText(t, value)
            r.appendChild(t); p.appendChild(r); body.appendChild(p)
            return
        }
        // 多行按段落分：一个 a:t 里的 \n 不会被 PowerPoint 渲染成换行
        val lines = value.split('\n')
        val ps = shape.getElementsByTagNameNS(NS_A, "p")
        val firstP = if (ps.length > 0) ps.item(0) as? Element else null
        ts.forEach { setText(it, "") }
        setText(ts[0], lines.firstOrNull().orEmpty())
        if (lines.size > 1 && firstP != null) {
            val parent = firstP.parentNode
            var anchor: Node = firstP
            lines.drop(1).forEach { line ->
                val clone = firstP.cloneNode(true) as Element
                val ct = textNodes(clone)
                ct.forEach { setText(it, "") }
                if (ct.isEmpty()) {
                    val r = clone.ownerDocument.createElementNS(NS_A, "a:r")
                    val t = clone.ownerDocument.createElementNS(NS_A, "a:t")
                    setText(t, line); r.appendChild(t); clone.appendChild(r)
                } else setText(ct[0], line)
                parent.insertBefore(clone, anchor.nextSibling)
                anchor = clone
            }
        }
    }

    private fun pptReplaceInShape(shape: Element, find: String, replace: String): Int {
        var total = 0
        // 段落级拼接后匹配：与 docx 同一套做法，能跨 run（PowerPoint 会把一行拆成多个 a:r）
        val ps = ArrayList<Element>()
        val bl = shape.getElementsByTagNameNS(NS_A, "p")
        for (i in 0 until bl.length) ps.add(bl.item(i) as Element)
        for (p in ps) {
            while (true) {
                val nodes = textNodes(p)
                if (nodes.isEmpty()) break
                val full = nodes.joinToString("") { it.textContent ?: "" }
                val start = full.indexOf(find)
                if (start < 0) break
                var offset = 0
                var inserted = false
                for (t in nodes) {
                    val s = t.textContent ?: ""
                    val nodeStart = offset
                    val nodeEnd = offset + s.length
                    offset = nodeEnd
                    if (nodeEnd <= start || nodeStart >= start + find.length) continue
                    val localStart = (start - nodeStart).coerceIn(0, s.length)
                    val localEnd = (start + find.length - nodeStart).coerceIn(0, s.length)
                    val newText = if (!inserted) { inserted = true; s.substring(0, localStart) + replace + s.substring(localEnd) }
                    else s.substring(0, localStart) + s.substring(localEnd)
                    setText(t, newText)
                }
                total++
                if (replace.contains(find)) break
            }
        }
        return total
    }

    // ─────────────────────────── XLSX ───────────────────────────

    data class SheetRef(val name: String, val path: String)

    /** 一个单元格样式（cellXfs 里的一项）对应的数字格式 */
    private class NumStyle(val fmtId: Int, val code: String?) {
        val kind: FmtKind = classify(fmtId, code)
    }

    private enum class FmtKind { DATE, DATETIME, TIME, PERCENT, OTHER }

    private fun classify(id: Int, code: String?): FmtKind {
        val c = (code ?: "").lowercase().replace("\"", "").replace("\\", "").trim()
        if (id == 9 || id == 10 || c.contains('%')) return FmtKind.PERCENT
        val hasDate = c.contains('y') || c.contains('d')
        val hasTime = c.contains('h') || c.contains('s')
        val builtinDate = id in 14..17 || id == 22 || id in 27..36 || id in 45..47 || id in 50..58
        return when {
            hasDate && hasTime -> FmtKind.DATETIME
            hasDate -> FmtKind.DATE
            hasTime -> FmtKind.TIME
            builtinDate -> FmtKind.DATE
            else -> FmtKind.OTHER
        }
    }

    private fun sheets(zip: Map<String, ByteArray>): List<SheetRef> {
        val wbXml = zip["xl/workbook.xml"]
            ?: throw ToolError("BAD_OFFICE", "这个 xlsx 里没有 xl/workbook.xml")
        val wb = parse(wbXml)
        val rels = runCatching {
            zip["xl/_rels/workbook.xml.rels"]?.let { parse(it) }
        }.getOrNull()

        // r:id → 目标路径（相对 xl/）
        val relMap = HashMap<String, String>()
        rels?.let {
            val list = it.getElementsByTagNameNS(NS_PKG_REL, "Relationship")
            for (i in 0 until list.length) {
                val e = list.item(i) as? Element ?: continue
                val id = e.getAttribute("Id")
                val target = e.getAttribute("Target")
                if (id.isNotEmpty() && target.isNotEmpty()) {
                    relMap[id] = if (target.startsWith("/")) target.trimStart('/')
                    else "xl/" + target.removePrefix("./")
                }
            }
        }

        val out = ArrayList<SheetRef>()
        val list = wb.getElementsByTagNameNS(NS_S, "sheet")
        for (i in 0 until list.length) {
            val e = list.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            val rid = e.getAttributeNS(NS_R, "id").ifBlank { e.getAttribute("r:id") }
            val path = relMap[rid] ?: "xl/worksheets/sheet${i + 1}.xml"
            if (zip.containsKey(path)) out.add(SheetRef(name, path))
        }
        if (out.isEmpty()) throw ToolError("BAD_OFFICE", "xlsx 里没有可读的工作表")
        return out
    }

    private fun sharedStrings(zip: Map<String, ByteArray>): List<String> {
        val raw = zip["xl/sharedStrings.xml"] ?: return emptyList()
        val doc = parse(raw)
        val out = ArrayList<String>()
        val si = doc.getElementsByTagNameNS(NS_S, "si")
        for (i in 0 until si.length) {
            val e = si.item(i) as? Element ?: continue
            // 富文本会拆成多个 r/t，普通字符串只有一个 t
            val ts = e.getElementsByTagNameNS(NS_S, "t")
            val sb = StringBuilder()
            for (j in 0 until ts.length) sb.append(ts.item(j).textContent ?: "")
            out.add(sb.toString())
        }
        return out
    }

    /** 某份工作簿是不是 1904 日期系统（Mac 老文件会用） */
    private fun isDate1904(zip: Map<String, ByteArray>): Boolean {
        val raw = zip["xl/workbook.xml"] ?: return false
        val doc = runCatching { parse(raw) }.getOrNull() ?: return false
        val pr = doc.getElementsByTagNameNS(NS_S, "workbookPr").item(0) as? Element ?: return false
        val v = pr.getAttribute("date1904")
        return v == "1" || v.equals("true", true)
    }

    private fun readStyles(zip: Map<String, ByteArray>): List<NumStyle> {
        val raw = zip["xl/styles.xml"] ?: return emptyList()
        val doc = runCatching { parse(raw) }.getOrNull() ?: return emptyList()

        val custom = HashMap<Int, String>()
        val nfs = doc.getElementsByTagNameNS(NS_S, "numFmts").item(0) as? Element
        nfs?.let {
            for (e in children(it, "numFmt")) {
                val id = e.getAttribute("numFmtId").toIntOrNull() ?: continue
                custom[id] = e.getAttribute("formatCode")
            }
        }
        val out = ArrayList<NumStyle>()
        // 只取 cellXfs 的直接子节点：getElementsByTagNameNS 会把 cellStyleXfs 里的 xf 也算进来，
        // 索引一旦错位，日期就会显示成乱七八糟的数字
        val cellXfs = doc.getElementsByTagNameNS(NS_S, "cellXfs").item(0) as? Element
        cellXfs?.let {
            for (xf in children(it, "xf")) {
                val id = xf.getAttribute("numFmtId").toIntOrNull() ?: 0
                out.add(NumStyle(id, custom[id]))
            }
        }
        return out
    }

    /** 列字母 → 序号（A=1, Z=26, AA=27） */
    fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch !in 'A'..'Z' && ch !in 'a'..'z') break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n
    }

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun splitRef(ref: String): Pair<Int, Int>? {
        val col = colIndex(ref)
        val row = ref.dropWhile { !it.isDigit() }.toIntOrNull() ?: return null
        if (col <= 0 || row <= 0) return null
        return row to col
    }

    data class SheetView(val text: String, val sheets: List<String>, val rows: Int, val cols: Int)

    /**
     * 读一张表。输出是 TSV（第一列是行号），比 JSON 省 token，
     * 模型看表格也更容易对上"第 3 行第 2 列"。
     *
     * [startRow] 让模型能看第 81 行以后的内容，不必把整表拉回来。
     */
    fun xlsxRead(
        bytes: ByteArray,
        sheetName: String?,
        maxRows: Int = 80,
        startRow: Int = 1,
    ): SheetView {
        val zip = unzip(bytes)
        val list = sheets(zip)
        val target = if (sheetName.isNullOrBlank()) list.first()
        else list.firstOrNull { it.name == sheetName }
            ?: throw ToolError("NOT_FOUND", "没有名为「$sheetName」的工作表。现有：" + list.joinToString("、") { it.name })

        val sst = sharedStrings(zip)
        val styles = readStyles(zip)
        val d1904 = isDate1904(zip)
        val doc = parse(zip[target.path] ?: throw ToolError("BAD_OFFICE", "工作表文件缺失"))
        val cells = readCells(doc, sst, styles, d1904)

        val maxRow = cells.keys.maxOfOrNull { it.first } ?: 0
        val maxCol = cells.keys.maxOfOrNull { it.second } ?: 0
        val from = startRow.coerceAtLeast(1)
        val to = minOf(maxRow, from + maxRows - 1)

        val sb = StringBuilder()
        sb.append("工作表「${target.name}」：$maxRow 行 × $maxCol 列")
        if (from > 1) sb.append("（本次第 $from-$to 行）")
        sb.append('\n')
        if (maxRow == 0) sb.append("（这张表是空的）\n")
        for (r in from..to) {
            val line = ArrayList<String>()
            var lastNonEmpty = 0
            for (c in 1..maxCol) {
                val v = cells[r to c] ?: ""
                line.add(v.replace('\n', ' ').replace('\t', ' '))
                if (v.isNotEmpty()) lastNonEmpty = c
            }
            if (lastNonEmpty == 0) continue
            sb.append("$r\t").append(line.take(lastNonEmpty).joinToString("\t")).append('\n')
        }
        if (to in 1 until maxRow) {
            sb.append("…（还有 ${maxRow - to} 行，用 start_row=${to + 1} 继续读）\n")
        }

        // 公式要单独标出来：模型不知道那是公式，就可能直接写值把它抹掉
        val formulas = ArrayList<String>()
        val cs = doc.getElementsByTagNameNS(NS_S, "c")
        for (i in 0 until cs.length) {
            val c = cs.item(i) as? Element ?: continue
            val f = children(c, "f").firstOrNull()?.textContent?.trim().orEmpty()
            if (f.isEmpty()) continue
            formulas.add("${c.getAttribute("r")} = $f")
            if (formulas.size >= 20) break
        }
        if (formulas.isNotEmpty()) {
            sb.append("（公式格，直接写值会删掉公式：" + formulas.joinToString("，") + "）\n")
        }

        return SheetView(sb.toString().trimEnd(), list.map { it.name }, maxRow, maxCol)
    }

    private fun readCells(
        doc: Document,
        sst: List<String>,
        styles: List<NumStyle>,
        date1904: Boolean,
    ): Map<Pair<Int, Int>, String> {
        val out = HashMap<Pair<Int, Int>, String>()
        val list = doc.getElementsByTagNameNS(NS_S, "c")
        for (i in 0 until list.length) {
            val c = list.item(i) as? Element ?: continue
            val ref = c.getAttribute("r")
            val pos = splitRef(ref) ?: continue
            val type = c.getAttribute("t")
            val v = children(c, "v").firstOrNull()?.textContent.orEmpty()
            val inline = children(c, "is").firstOrNull()
            val kind = styles.getOrNull(c.getAttribute("s").toIntOrNull() ?: 0)?.kind ?: FmtKind.OTHER
            val value = when {
                inline != null -> inline.getElementsByTagNameNS(NS_S, "t").let { ts ->
                    buildString { for (j in 0 until ts.length) append(ts.item(j).textContent ?: "") }
                }
                type == "s" -> v.toIntOrNull()?.let { sst.getOrNull(it) } ?: ""
                type == "inlineStr" -> v
                type == "b" -> if (v == "1") "TRUE" else "FALSE"
                kind == FmtKind.DATE || kind == FmtKind.DATETIME || kind == FmtKind.TIME ->
                    v.toDoubleOrNull()?.let { serialToText(it, kind, date1904) } ?: v
                kind == FmtKind.PERCENT -> v.toDoubleOrNull()?.let { trimNum(it * 100) + "%" } ?: v
                else -> if (type.isEmpty() || type == "n") trimNumStr(v) else v
            }
            if (value.isNotEmpty()) out[pos] = value
        }
        return out
    }

    private fun trimNum(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString()
        else String.format(Locale.US, "%.4f", d).trimEnd('0').trimEnd('.')

    private fun trimNumStr(s: String): String {
        if (s.isEmpty() || s.contains('e', true) || s.contains('E')) return s
        val d = s.toDoubleOrNull() ?: return s
        return if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString() else s
    }

    private fun baseDate(date1904: Boolean): LocalDate =
        if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)

    private fun serialToText(serial: Double, kind: FmtKind, date1904: Boolean): String {
        val dt = baseDate(date1904).atStartOfDay().plusSeconds(Math.round(serial * 86400.0))
        return when (kind) {
            FmtKind.DATE -> dt.toLocalDate().toString()
            FmtKind.TIME -> String.format(Locale.US, "%02d:%02d", dt.hour, dt.minute)
            else -> String.format(Locale.US, "%s %02d:%02d", dt.toLocalDate(), dt.hour, dt.minute)
        }
    }

    private val DATE_RE = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?$""")
    private val PCT_RE = Regex("""^(\d+(?:\.\d+)?)\s*%$""")

    private fun parseDateValue(text: String): Pair<LocalDateTime, Boolean>? {
        val m = DATE_RE.matchEntire(text.trim()) ?: return null
        return runCatching {
            val d = LocalDate.of(
                m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
            )
            val hasTime = m.groupValues[4].isNotEmpty()
            val h = m.groupValues[4].toIntOrNull() ?: 0
            val mi = m.groupValues[5].toIntOrNull() ?: 0
            val s = m.groupValues[6].toIntOrNull() ?: 0
            d.atTime(h, mi, s) to hasTime
        }.getOrNull()
    }

    private fun serialOf(dt: LocalDateTime, date1904: Boolean): Double {
        val days = ChronoUnit.DAYS.between(baseDate(date1904), dt.toLocalDate()).toDouble()
        val secs = dt.toLocalTime().toSecondOfDay().toDouble()
        return Math.round((days + secs / 86400.0) * 1e6) / 1e6
    }

    private fun fmtSerial(d: Double): String =
        if (d == Math.floor(d)) d.toLong().toString()
        else String.format(Locale.US, "%.6f", d).trimEnd('0').trimEnd('.')

    /** 批量写单元格。日期/百分比会写成"数值 + 数字格式"，而不是文本 */
    fun xlsxSet(
        bytes: ByteArray,
        sheetName: String?,
        cells: List<Pair<String, String>>,
        overwriteFormula: Boolean = false,
    ): ByteArray {
        val zip = unzip(bytes)
        val list = sheets(zip)
        val target = if (sheetName.isNullOrBlank()) list.first()
        else list.firstOrNull { it.name == sheetName }
            ?: throw ToolError("NOT_FOUND", "没有名为「$sheetName」的工作表。现有：" + list.joinToString("、") { it.name })

        val path = target.path
        val doc = parse(zip[path] ?: throw ToolError("BAD_OFFICE", "工作表文件缺失"))
        val sheetData = doc.getElementsByTagNameNS(NS_S, "sheetData").item(0) as? Element
            ?: throw ToolError("BAD_OFFICE", "工作表结构异常：找不到 sheetData")

        val d1904 = isDate1904(zip)
        val styles = readStyles(zip)
        val replacements = HashMap<String, ByteArray>()
        val fmtCache = HashMap<FmtKind, Int>()
        var stylesChanged = false

        /** 取样式索引：已有日期格式就沿用，否则新建（失败返回 -1，调用方退回文本） */
        fun styleFor(kind: FmtKind, keepExisting: Int): Int {
            if (keepExisting >= 0) return keepExisting
            fmtCache[kind]?.let { return it }
            val idx = ensureFormat(zip, kind)
            fmtCache[kind] = idx
            if (idx >= 0) stylesChanged = true
            return idx
        }

        // 先统一检查公式格：一旦发现有公式还没被允许覆盖，就整批拒绝，
        // 避免"写了前三个、第四个才发现是公式"的半成功状态
        if (!overwriteFormula) {
            val blocked = ArrayList<String>()
            for ((ref, _) in cells) {
                val (row, col) = splitRef(ref) ?: continue
                val c = findCell(sheetData, row, colName(col)) ?: continue
                if (children(c, "f").isNotEmpty()) {
                    blocked.add("$ref（" + (children(c, "f").firstOrNull()?.textContent ?: "").take(40) + "）")
                }
            }
            if (blocked.isNotEmpty()) {
                throw ToolError(
                    "IS_FORMULA",
                    "这些格子里是公式，直接写值会把公式删掉：" + blocked.joinToString("、") +
                        "。确实要覆盖就先读一遍确认，再带 overwrite_formula=true 重试。",
                )
            }
        }

        for ((ref, value) in cells) {
            val pos = splitRef(ref)
                ?: throw ToolError("BAD_ARGS", "单元格坐标不合法：$ref（应形如 B3）")
            val (row, col) = pos
            val rowEl = ensureRow(doc, sheetData, row)
            val c = ensureCell(doc, rowEl, row, col)
            val existingStyle = c.getAttribute("s").toIntOrNull() ?: -1
            val text = value.trim()

            val date = parseDateValue(text)
            val pct = PCT_RE.matchEntire(text)?.groupValues?.get(1)?.toDoubleOrNull()

            when {
                date != null -> {
                    val kind = if (date.second) FmtKind.DATETIME else FmtKind.DATE
                    // 原本就是日期格式的列，沿用它自己的格式（可能是 yyyy年m月d日）
                    val keep = existingStyle.takeIf {
                        it >= 0 && styles.getOrNull(it)?.kind in setOf(FmtKind.DATE, FmtKind.DATETIME)
                    }
                    val idx = styleFor(kind, keep ?: -1)
                    if (idx >= 0) {
                        writeCellRaw(doc, c, fmtSerial(serialOf(date.first, d1904)), idx, numeric = true)
                    } else {
                        // 样式表不可用：宁可写成文本，也不写一个数字让人看不懂
                        writeCellRaw(doc, c, text, existingStyle, numeric = false)
                    }
                }

                pct != null -> {
                    val idx = styleFor(FmtKind.PERCENT, -1)
                    if (idx >= 0) writeCellRaw(doc, c, fmtSerial(pct / 100.0), idx, numeric = true)
                    else writeCellRaw(doc, c, text, existingStyle, numeric = false)
                }

                else -> {
                    val num = text.toDoubleOrNull()
                    if (text.isNotBlank() && num != null) {
                        writeCellRaw(doc, c, text, existingStyle, numeric = true)
                    } else {
                        writeCellRaw(doc, c, text, existingStyle, numeric = false)
                    }
                }
            }
            sortRow(rowEl)
        }

        if (stylesChanged) zip["xl/styles.xml"]?.let { replacements["xl/styles.xml"] = it }
        replacements[path] = serialize(doc)
        val result = rezip(bytes, replacements)
        runCatching { xlsxRead(result, target.name, 3) }.getOrElse {
            throw ToolError("VALIDATE_FAILED", "改写后的表格无法重新解析（${it.message}），已放弃修改")
        }
        return result
    }

    private fun findCell(sheetData: Element, row: Int, colName: String): Element? {
        val rowEl = children(sheetData, "row")
            .firstOrNull { it.getAttribute("r").toIntOrNull() == row } ?: return null
        return children(rowEl, "c").firstOrNull { it.getAttribute("r") == "$colName$row" }
    }

    /**
     * 找一个"日期/百分比"样式索引，没有就新建。
     *
     * 为什么要动 styles.xml：Excel 里"看起来是日期"靠的是单元格的数字格式，
     * 只写数字不写格式，用户看到的是一串 46303。
     * 返回 -1 表示这份文件没有可用的样式表 —— 调用方退回写文本，绝不写坏。
     */
    private fun ensureFormat(zip: MutableMap<String, ByteArray>, kind: FmtKind): Int {
        val raw = zip["xl/styles.xml"] ?: return -1
        val doc = runCatching { parse(raw) }.getOrNull() ?: return -1
        val root = doc.documentElement ?: return -1
        val cellXfs = doc.getElementsByTagNameNS(NS_S, "cellXfs").item(0) as? Element ?: return -1

        val code = when (kind) {
            FmtKind.DATE -> "yyyy-mm-dd"
            FmtKind.DATETIME -> "yyyy-mm-dd hh:mm"
            FmtKind.PERCENT -> "0.00%"
            else -> return -1
        }
        // 百分比用内置 10（0.00%），日期必须自定义：内置 14 是 m/d/yy，中文场景不好看
        var fmtId = if (kind == FmtKind.PERCENT) 10 else -1

        val numFmts = doc.getElementsByTagNameNS(NS_S, "numFmts").item(0) as? Element
        if (fmtId < 0) {
            val existing = numFmts?.let { nfs ->
                children(nfs, "numFmt").firstOrNull {
                    it.getAttribute("formatCode").equals(code, true)
                }?.getAttribute("numFmtId")?.toIntOrNull()
            }
            fmtId = existing ?: run {
                val nfs = numFmts ?: doc.createElementNS(NS_S, "numFmts").also {
                    root.insertBefore(it, root.firstChild)
                }
                val maxId = children(nfs, "numFmt")
                    .mapNotNull { it.getAttribute("numFmtId").toIntOrNull() }
                    .maxOrNull() ?: 163
                val newId = maxOf(maxId + 1, 164)
                val el = doc.createElementNS(NS_S, "numFmt")
                el.setAttribute("numFmtId", newId.toString())
                el.setAttribute("formatCode", code)
                nfs.appendChild(el)
                nfs.setAttribute("count", children(nfs, "numFmt").size.toString())
                newId
            }
        }

        val xfs = children(cellXfs, "xf")
        val reused = xfs.indexOfFirst { (it.getAttribute("numFmtId").toIntOrNull() ?: 0) == fmtId }
        val index = if (reused >= 0) {
            reused
        } else {
            val template = xfs.firstOrNull() ?: return -1
            val clone = template.cloneNode(true) as? Element ?: return -1
            clone.setAttribute("numFmtId", fmtId.toString())
            clone.setAttribute("applyNumberFormat", "1")
            cellXfs.appendChild(clone)
            cellXfs.setAttribute("count", children(cellXfs, "xf").size.toString())
            children(cellXfs, "xf").size - 1
        }
        zip["xl/styles.xml"] = serialize(doc)
        return index
    }

    private fun ensureRow(doc: Document, sheetData: Element, row: Int): Element {
        val rows = children(sheetData, "row")
        rows.firstOrNull { it.getAttribute("r").toIntOrNull() == row }?.let { return it }
        val el = doc.createElementNS(NS_S, "row")
        el.setAttribute("r", row.toString())
        // 按行号插入到正确位置：Excel 对行顺序敏感
        val before = rows.firstOrNull { (it.getAttribute("r").toIntOrNull() ?: 0) > row }
        if (before != null) sheetData.insertBefore(el, before) else sheetData.appendChild(el)
        return el
    }

    private fun ensureCell(doc: Document, rowEl: Element, row: Int, col: Int): Element {
        val name = colName(col) + row
        children(rowEl, "c").firstOrNull { it.getAttribute("r") == name }?.let { return it }
        val el = doc.createElementNS(NS_S, "c")
        el.setAttribute("r", name)
        rowEl.appendChild(el)
        return el
    }

    /**
     * 写一个单元格。**只改内容和 t 属性，不动 s（样式）** ——
     * 动了样式，用户的边框、填充、货币格式会一起消失。
     */
    private fun writeCellRaw(doc: Document, c: Element, value: String, styleIndex: Int, numeric: Boolean) {
        // 清掉旧内容，避免 t/v/f 混在一起导致 Excel 报"需要修复"
        while (c.firstChild != null) c.removeChild(c.firstChild)
        if (styleIndex >= 0) c.setAttribute("s", styleIndex.toString()) else c.removeAttribute("s")

        if (numeric) {
            c.removeAttribute("t")
            val v = doc.createElementNS(NS_S, "v")
            v.textContent = value
            c.appendChild(v)
        } else {
            c.setAttribute("t", "inlineStr")
            val isEl = doc.createElementNS(NS_S, "is")
            val t = doc.createElementNS(NS_S, "t")
            t.textContent = value
            isEl.appendChild(t)
            c.appendChild(isEl)
        }
    }

    private fun sortRow(rowEl: Element) {
        val cells = children(rowEl, "c")
        val sorted = cells.sortedBy { colIndex(it.getAttribute("r")) }
        if (sorted == cells) return
        cells.forEach { rowEl.removeChild(it) }
        sorted.forEach { rowEl.appendChild(it) }
    }

    fun kindOf(name: String): Kind = when (name.substringAfterLast('.', "").lowercase()) {
        "docx", "docm", "dotx" -> Kind.DOCX
        "xlsx", "xlsm", "xltx" -> Kind.XLSX
        "doc", "dot", "xls", "xlt" -> Kind.LEGACY
        else -> Kind.OTHER
    }

    /** 文件类型（不是数字格式） */
    enum class Kind { DOCX, XLSX, LEGACY, OTHER }
}
