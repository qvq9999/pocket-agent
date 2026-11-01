package com.pocketagent.app.agent

import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 从零生成 .docx / .pptx。
 *
 * ── 为什么要原生写 OOXML，而不是装个 python 库 ──
 * 上一版这条路是"内置一个 39MB 的 PPT 技能 + 在手机上装 Python 运行时"：
 * APK 里 86% 的体积是那个包，首次任务还要先下载几百兆依赖、连跑几步脚本。
 * 而"做一份文档"真正需要的只是**一个结构正确的 zip + 几段 XML**，
 * 手机自己完全写得出来，零下载、零脚本、一次工具调用就完事。
 *
 * ── 换来的是什么 ──
 * 输出是"干净可读的文档"，不是设计稿：没有图片、图表、SmartArt、动画、自定义母版。
 * 这是刻意的取舍 —— 那些东西的复杂度都在"资源包与渲染"上，而移动端拿不到渲染。
 *
 * ── 正确性优先于精简 ──
 * 每个包都写全 Office 打开时**必须**存在的部件（缺一个就是"文件已损坏"）：
 * docx 的 body 末尾必须有段落收尾（表格后面直接跟 sectPr 会打不开）；
 * pptx 的 theme 里 fmtScheme 四组的条目数是固定的（3/3/3/3），少一条 PowerPoint 直接拒开。
 * 这两条都不是"最好有"，是"没有就坏"，所以模板里写死并加了注释。
 */
object OfficeCreate {

    private const val NS_REL_PKG = "http://schemas.openxmlformats.org/package/2006/relationships"
    private const val NS_DOC_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    // 只用于把中文正文里的 XML 元字符安全落地；所有模板文本都过它
    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> if (c.code < 32 && c != '\n' && c != '\t') Unit else append(c)
        }
    }

    private fun stamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))

    private const val XML_DECL = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    /** zip 打包：Office 不在意条目顺序，但 [Content_Types].xml 放第一个是各家惯例 */
    private fun zip(parts: LinkedHashMap<String, String>): ByteArray {
        val out = ByteArrayOutputStream(32 * 1024)
        ZipOutputStream(out).use { z ->
            z.setLevel(6)
            for ((name, body) in parts) {
                z.putNextEntry(ZipEntry(name))
                z.write(body.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun rels(vararg pairs: Triple<String, String, String>): String =
        XML_DECL + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            pairs.joinToString("") { (id, type, target) ->
                "<Relationship Id=\"$id\" Type=\"$type\" Target=\"$target\"/>"
            } + "</Relationships>"

    private fun coreProps(title: String, author: String): String =
        XML_DECL + """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""" +
            "<dc:creator>${esc(author)}</dc:creator>" +
            "<cp:lastModifiedBy>${esc(author)}</cp:lastModifiedBy>" +
            "<dc:title>${esc(title)}</dc:title>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">${stamp()}</dcterms:created>" +
            "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">${stamp()}</dcterms:modified>" +
            "</cp:coreProperties>"

    // ═══════════════════════════ DOCX ═══════════════════════════

    /**
     * 一个段落块。
     *
     * [kind]：`h1` `h2` `h3` `title` `p` `li` `quote` `code` `table` `pagebreak`
     * [level] 只对 `li` 有意义（0/1/2 三级缩进）。
     * [rows] 只对 `table` 有意义，第一行按表头处理。
     */
    data class DocxBlock(
        val kind: String,
        val text: String = "",
        val level: Int = 0,
        val rows: List<List<String>> = emptyList(),
    )

    private const val CT_TYPES = "http://schemas.openxmlformats.org/package/2006/content-types"

    fun docx(blocks: List<DocxBlock>, title: String, author: String = "PocketAgent"): ByteArray {
        if (blocks.isEmpty()) throw ToolError("BAD_ARGS", "blocks 不能为空：至少给一个段落")
        val body = StringBuilder()
        var tableAtEnd = false
        for (b in blocks) {
            when (b.kind) {
                "pagebreak" -> body.append(
                    "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"
                )

                "table" -> {
                    body.append(docxTable(b.rows))
                    tableAtEnd = true
                }

                else -> body.append(docxPara(b))
            }
        }
        // 表格不能是 body 的最后一个元素：Word 会在打开时报"文件已损坏"。
        // 补一个空段落收尾，同时它也是"表格后面要能继续打字"的正常形态。
        if (tableAtEnd) body.append("<w:p/>")

        val document = XML_DECL +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="$NS_DOC_REL">""" +
            "<w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" " +
            "w:header=\"851\" w:footer=\"992\" w:gutter=\"0\"/></w:sectPr>" +
            "</w:body></w:document>"

        val parts = LinkedHashMap<String, String>()
        parts["[Content_Types].xml"] = XML_DECL +
            """<Types xmlns="$CT_TYPES">""" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>" +
            "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
            "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>" +
            "</Types>"
        parts["_rels/.rels"] = rels(
            Triple("rId1", "$NS_DOC_REL/officeDocument", "word/document.xml"),
            Triple("rId2", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties", "docProps/core.xml"),
            Triple("rId3", "$NS_DOC_REL/extended-properties", "docProps/app.xml"),
        )
        parts["word/document.xml"] = document
        parts["word/_rels/document.xml.rels"] = rels(
            Triple("rId1", "$NS_DOC_REL/styles", "styles.xml"),
            Triple("rId2", "$NS_DOC_REL/numbering", "numbering.xml"),
        )
        parts["word/styles.xml"] = DOCX_STYLES
        parts["word/numbering.xml"] = DOCX_NUMBERING
        parts["docProps/core.xml"] = coreProps(title, author)
        parts["docProps/app.xml"] = XML_DECL +
            "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
            "<Application>PocketAgent</Application></Properties>"
        return zip(parts)
    }

    /** run：`xml:space="preserve"` 不能省，否则正文里的空格与缩进会被 XML 规范化吃掉 */
    private fun run(text: String, mono: Boolean = false, italic: Boolean = false): String {
        val rPr = buildString {
            if (mono || italic) {
                append("<w:rPr>")
                if (mono) append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:eastAsia=\"等线\"/>")
                if (italic) append("<w:i/>")
                append("</w:rPr>")
            }
        }
        return "<w:r>$rPr<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r>"
    }

    private fun docxPara(b: DocxBlock): String {
        val style = when (b.kind) {
            "title" -> "Title"
            "h1" -> "Heading1"
            "h2" -> "Heading2"
            "h3" -> "Heading3"
            "h4" -> "Heading4"
            "quote" -> "Quote"
            "code" -> "Code"
            else -> null
        }
        val pPr = buildString {
            if (style != null) append("<w:pStyle w:val=\"$style\"/>")
            // outlineLvl 与 pStyle 一起写：只给样式的文件在"生成目录/大纲视图"时读不出层级，
            // 而我们的 docxRead 也是按 outlineLvl 优先判定的
            when (b.kind) {
                "title", "h1" -> append("<w:outlineLvl w:val=\"0\"/>")
                "h2" -> append("<w:outlineLvl w:val=\"1\"/>")
                "h3" -> append("<w:outlineLvl w:val=\"2\"/>")
                "h4" -> append("<w:outlineLvl w:val=\"3\"/>")
                else -> Unit
            }
            if (b.kind == "li") {
                append("<w:numPr><w:ilvl w:val=\"${b.level.coerceIn(0, 2)}\"/><w:numId w:val=\"1\"/></w:numPr>")
            }
            append("</w:pPr>")
        }.let { if (it == "</w:pPr>") "" else "<w:pPr>$it" }
        // 一个 w:t 里的 \n 会被 XML 当空白吃掉，所以代码块必须逐行落成 run + w:br
        val lines = b.text.split('\n')
        val runs = when {
            b.kind == "code" -> lines.mapIndexed { i, line ->
                run(line, mono = true) + if (i == lines.lastIndex) "" else "<w:r><w:br/></w:r>"
            }.joinToString("")

            b.text.isEmpty() -> ""
            else -> run(b.text, italic = b.kind == "quote")
        }
        return "<w:p>$pPr$runs</w:p>"
    }

    private fun docxTable(rows: List<List<String>>): String {
        val clean = rows.filter { it.any { c -> c.isNotBlank() } }
        if (clean.isEmpty()) return ""
        val cols = clean.maxOf { it.size }
        val colW = 9026 / cols
        val grid = (1..cols).joinToString("") { "<w:gridCol w:w=\"$colW\"/>" }
        val trs = clean.mapIndexed { ri, row ->
            val tcs = (0 until cols).joinToString("") { ci ->
                val v = row.getOrElse(ci) { "" }
                val hdr = if (ri == 0) "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/>" else ""
                val runs = v.split('\n').joinToString("<w:r><w:br/></w:r>") { line -> run(line) }
                "<w:tc><w:tcPr><w:tcW w:w=\"$colW\" w:type=\"dxa\"/>$hdr</w:tcPr>" +
                    "<w:p>${if (runs.isEmpty()) "<w:r><w:t xml:space=\"preserve\"/></w:r>" else runs}</w:p></w:tc>"
            }
            "<w:tr>$tcs</w:tr>"
        }.joinToString("")
        return "<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/>" +
            "<w:tblW w:w=\"0\" w:type=\"auto\"/>" +
            "<w:tblBorders>" +
            listOf("top", "left", "bottom", "right", "insideH", "insideV").joinToString("") {
                "<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFBFBF\"/>"
            } +
            "</w:tblBorders></w:tblPr><w:tblGrid>$grid</w:tblGrid>$trs</w:tbl>"
    }

    /**
     * styles.xml。字号单位是半磅（22 = 11pt），颜色不带 #。
     *
     * `w:latentStyles` 与 `docDefaults` 都要在：缺 docDefaults 时 Word 会退回它的
     * 内置默认（Calibri 11），中文正文看起来就像没设字体。
     */
    private val DOCX_STYLES = XML_DECL +
        """<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
        "<w:docDefaults><w:rPrDefault><w:rPr>" +
        "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"等线\" w:cs=\"Calibri\"/>" +
        "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"zh-CN\" w:eastAsia=\"zh-CN\"/>" +
        "</w:rPr></w:rPrDefault>" +
        "<w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"288\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault>" +
        "</w:docDefaults>" +
        style("Normal", "Normal", null, "") +
        style("Heading1", "Heading 1", "Normal",
            "<w:pPr><w:keepNext/><w:spacing w:before=\"320\" w:after=\"120\"/></w:pPr>" +
                "<w:rPr><w:b/><w:color w:val=\"1F3864\"/><w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/></w:rPr>") +
        style("Heading2", "Heading 2", "Normal",
            "<w:pPr><w:keepNext/><w:spacing w:before=\"260\" w:after=\"100\"/></w:pPr>" +
                "<w:rPr><w:b/><w:color w:val=\"2E5496\"/><w:sz w:val=\"28\"/><w:szCs w:val=\"28\"/></w:rPr>") +
        style("Heading3", "Heading 3", "Normal",
            "<w:pPr><w:keepNext/><w:spacing w:before=\"220\" w:after=\"80\"/></w:pPr>" +
                "<w:rPr><w:b/><w:color w:val=\"404040\"/><w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/></w:rPr>") +
        style("Heading4", "Heading 4", "Normal",
            "<w:pPr><w:keepNext/></w:pPr><w:rPr><w:b/><w:i/><w:sz w:val=\"22\"/></w:rPr>") +
        style("Title", "Title", "Normal",
            "<w:pPr><w:spacing w:after=\"240\"/></w:pPr>" +
                "<w:rPr><w:b/><w:color w:val=\"1F3864\"/><w:sz w:val=\"48\"/><w:szCs w:val=\"48\"/></w:rPr>") +
        style("Quote", "Quote", "Normal",
            "<w:pPr><w:ind w:left=\"425\"/><w:spacing w:before=\"120\" w:after=\"120\"/></w:pPr>" +
                "<w:rPr><w:i/><w:color w:val=\"595959\"/></w:rPr>") +
        style("Code", "Code Block", "Normal",
            "<w:pPr><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F5F5F5\"/>" +
                "<w:spacing w:before=\"120\" w:after=\"120\"/></w:pPr>" +
                "<w:rPr><w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\" w:eastAsia=\"等线\"/>" +
                "<w:sz w:val=\"20\"/><w:szCs w:val=\"20\"/></w:rPr>") +
        style("ListParagraph", "List Paragraph", "Normal",
            "<w:pPr><w:ind w:left=\"720\" w:hanging=\"360\"/></w:pPr>") +
        // TableGrid 必须存在：docxTable 引用了它，缺样式 Word 会报文档被损坏
        """<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/>""" +
            "<w:tblPr><w:tblBorders>" +
            listOf("top", "left", "bottom", "right", "insideH", "insideV").joinToString("") {
                "<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"BFBFBF\"/>"
            } + "</w:tblBorders></w:tblPr></w:style>" +
        "</w:styles>"

    private fun style(id: String, name: String, basedOn: String?, props: String): String =
        "<w:style w:type=\"paragraph\" w:styleId=\"$id\"><w:name w:val=\"$name\"/>" +
            (if (basedOn == null) "" else "<w:basedOn w:val=\"$basedOn\"/>") + props + "</w:style>"

    /** 三级项目符号列表。lvlText 用字面字符而不是 Symbol 私有区码位：后者在非英文 Word 上容易变豆腐块 */
    private val DOCX_NUMBERING = XML_DECL +
        """<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
        "<w:abstractNum w:abstractNumId=\"0\">" +
        "<w:multiLevelType w:val=\"hybridMultilevel\"/>" +
        listOf("•", "◦", "▪").mapIndexed { i, ch ->
            "<w:lvl w:ilvl=\"$i\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/>" +
                "<w:lvlText w:val=\"$ch\"/><w:lvlJc w:val=\"left\"/>" +
                "<w:pPr><w:ind w:left=\"${720 * (i + 1)}\" w:hanging=\"360\"/></w:pPr>" +
                "<w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:hint=\"default\"/></w:rPr></w:lvl>"
        }.joinToString("") +
        "</w:abstractNum><w:num w:numId=\"1\"><w:abstractNumId w:val=\"0\"/></w:num></w:numbering>"

    // ═══════════════════════════ PPTX ═══════════════════════════

    /**
     * 一页幻灯片。
     *
     * [layout]：`title`（封面）/ `bullets`（标题+正文）/ `two`（双栏）/ `blank`（只有标题）/ `table`
     * [bullets] 是 (缩进级别, 文本) 对，级别 0..2。
     * [rows] 只对 `table` 有意义。
     */
    data class PptSlide(
        val layout: String,
        val title: String = "",
        val bullets: List<Pair<Int, String>> = emptyList(),
        val subtitle: String = "",
        val rows: List<List<String>> = emptyList(),
    )

    // 16:9 的 EMU 尺寸：9144000 x 6858000（12192000x6858000 是 16:9 全尺寸，这里用宽屏标准）
    private const val SLIDE_W = 12192000
    private const val SLIDE_H = 6858000

    fun pptx(slides: List<PptSlide>, title: String, author: String = "PocketAgent"): ByteArray {
        if (slides.isEmpty()) throw ToolError("BAD_ARGS", "slides 不能为空：至少给一页")
        val parts = LinkedHashMap<String, String>()

        val ct = StringBuilder(XML_DECL).append("""<Types xmlns="$CT_TYPES">""")
            .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            .append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>")
            .append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>")
            .append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>")
            .append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>")
        slides.indices.forEach { i ->
            ct.append("<Override PartName=\"/ppt/slides/slide${i + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>")
        }
        ct.append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>")
            .append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>")
            .append("</Types>")
        parts["[Content_Types].xml"] = ct.toString()

        parts["_rels/.rels"] = rels(
            Triple("rId1", "$NS_DOC_REL/officeDocument", "ppt/presentation.xml"),
            Triple("rId2", "http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties", "docProps/core.xml"),
            Triple("rId3", "$NS_DOC_REL/extended-properties", "docProps/app.xml"),
        )

        // presentation.xml：sldMasterId 必须从 2147483648 起、sldId 必须落在 256..2147483647。
        // 这两段号段是 ECMA-376 规定的，撞了 PowerPoint 会直接报文件损坏
        val sldIds = slides.indices.joinToString("") { i ->
            "<p:sldId id=\"${256 + i}\" r:id=\"rId${i + 2}\"/>"
        }
        parts["ppt/presentation.xml"] = XML_DECL +
            """<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="$NS_DOC_REL" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""" +
            "<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst>" +
            "<p:sldIdLst>$sldIds</p:sldIdLst>" +
            "<p:sldSz cx=\"$SLIDE_W\" cy=\"$SLIDE_H\"/>" +
            "<p:notesSz cx=\"$SLIDE_H\" cy=\"$SLIDE_W\"/>" +
            "</p:presentation>"
        parts["ppt/_rels/presentation.xml.rels"] = rels(
            *buildList {
                add(Triple("rId1", "$NS_DOC_REL/slideMaster", "slideMasters/slideMaster1.xml"))
                slides.indices.forEach { i ->
                    add(Triple("rId${i + 2}", "$NS_DOC_REL/slide", "slides/slide${i + 1}.xml"))
                }
            }.toTypedArray()
        )
        parts["ppt/slideMasters/slideMaster1.xml"] = PPT_MASTER
        parts["ppt/slideMasters/_rels/slideMaster1.xml.rels"] = rels(
            Triple("rId1", "$NS_DOC_REL/slideLayout", "../slideLayouts/slideLayout1.xml"),
            Triple("rId2", "$NS_DOC_REL/theme", "../theme/theme1.xml"),
        )
        parts["ppt/slideLayouts/slideLayout1.xml"] = PPT_LAYOUT
        parts["ppt/slideLayouts/_rels/slideLayout1.xml.rels"] = rels(
            Triple("rId1", "$NS_DOC_REL/slideMaster", "../slideMasters/slideMaster1.xml"),
        )
        parts["ppt/theme/theme1.xml"] = PPT_THEME

        slides.forEachIndexed { i, s ->
            parts["ppt/slides/slide${i + 1}.xml"] = pptSlide(s, i)
            parts["ppt/slides/_rels/slide${i + 1}.xml.rels"] = rels(
                Triple("rId1", "$NS_DOC_REL/slideLayout", "../slideLayouts/slideLayout1.xml"),
            )
        }
        parts["docProps/core.xml"] = coreProps(title, author)
        parts["docProps/app.xml"] = XML_DECL +
            "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">" +
            "<Application>PocketAgent</Application><Slides>${slides.size}</Slides></Properties>"
        return zip(parts)
    }

    /** 一个文本框。spPr 里的几何必须是 rect，缺 xfrm 时 PowerPoint 会把它摆在 0,0 叠成一坨 */
    private fun shape(id: Int, name: String, x: Int, y: Int, cx: Int, cy: Int, paras: String): String =
        "<p:sp><p:nvSpPr><p:cNvPr id=\"$id\" name=\"${esc(name)}\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>" +
            "<p:spPr><a:xfrm><a:off x=\"$x\" y=\"$y\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>" +
            "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/></p:spPr>" +
            "<p:txBody><a:bodyPr wrap=\"square\" lIns=\"91440\" tIns=\"45720\" rIns=\"45720\" bIns=\"45720\" anchor=\"t\">" +
            "<a:normAutofit/></a:bodyPr><a:lstStyle/>$paras</p:txBody></p:sp>"

    /**
     * 一段文字。
     *
     * `b="0"` 与 `spcBef` 必须显式写：母版带了 txStyles 的默认加粗与间距，
     * 不覆盖的话正文每一行都会看起来像标题。
     */
    private fun para(
        text: String,
        lvl: Int,
        size: Int,
        bold: Boolean,
        color: String,
        align: String? = null,
    ): String =
        "<a:p><a:pPr lvl=\"${lvl.coerceIn(0, 4)}\"${if (bold) " b=\"1\"" else " b=\"0\""}" +
            (if (align != null) " algn=\"$align\"" else "") +
            "><a:lnSpc><a:spcPct val=\"110000\"/></a:lnSpc>" +
            "<a:spcBef><a:spcPts val=\"600\"/></a:spcBef></a:pPr>" +
            "<a:r><a:rPr lang=\"zh-CN\" altLang=\"en-US\" sz=\"$size\" dirty=\"0\">" +
            "<a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill>" +
            "<a:latin typeface=\"Calibri\"/><a:ea typeface=\"等线\"/></a:rPr>" +
            "<a:t>${esc(text)}</a:t></a:r></a:p>"

    private fun pptSlide(s: PptSlide, index: Int): String {
        val body = StringBuilder()
        var nextId = 2
        val titleSize = if (s.layout == "title") 4400 else 2800
        if (s.title.isNotBlank()) {
            body.append(
                shape(
                    nextId++, "标题 ${index + 1}",
                    685800, if (s.layout == "title") 2286000 else 457200,
                    SLIDE_W - 1371600, if (s.layout == "title") 1143000 else 838200,
                    para(s.title, 0, titleSize, true, if (s.layout == "title") "1F3864" else "1F3864",
                        align = if (s.layout == "title") "ctr" else null),
                )
            )
        }
        if (s.subtitle.isNotBlank()) {
            body.append(
                shape(
                    nextId++, "副标题",
                    914400, 3505200, SLIDE_W - 1828800, 914400,
                    para(s.subtitle, 0, 2000, false, "595959", align = "ctr"),
                )
            )
        }
        when (s.layout) {
            "table" -> if (s.rows.isNotEmpty()) {
                body.append(pptTable(nextId, s.rows))
            }

            "two" -> {
                val half = s.bullets.size / 2
                listOf(s.bullets.take(half), s.bullets.drop(half)).forEachIndexed { col, items ->
                    if (items.isEmpty()) return@forEachIndexed
                    val paras = items.joinToString("") { (lvl, t) -> para(t, lvl, 1600, false, "262626") }
                    body.append(
                        shape(
                            nextId++, "内容${col + 1}",
                            685800 + col * ((SLIDE_W - 1371600) / 2 + 68580),
                            1524000, (SLIDE_W - 1371600) / 2 - 68580, 4572000,
                            paras,
                        )
                    )
                }
            }

            else -> if (s.bullets.isNotEmpty() && s.layout != "blank") {
                val paras = s.bullets.joinToString("") { (lvl, t) ->
                    // 级别越大字越小，视觉上就有层级；缩进由 lvl 交给主题处理
                    val sz = when (lvl) { 0 -> 2000; 1 -> 1700; else -> 1500 }
                    para(t, lvl, sz, lvl == 0, if (lvl == 0) "262626" else "404040")
                }
                body.append(
                    shape(
                        nextId++, "内容",
                        685800, if (s.title.isBlank()) 685800 else 1524000,
                        SLIDE_W - 1371600,
                        if (s.title.isBlank()) SLIDE_H - 1371600 else 4572000,
                        paras,
                    )
                )
            }
        }
        // 一页什么形状都没有时也要留一个空的 spTree：PowerPoint 不接受没有形状树的页
        return XML_DECL +
            """<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="$NS_DOC_REL" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""" +
            "<p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
            "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/>" +
            "<a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
            body +
            "</p:spTree></p:cSld><p:clrMapOvr><a:overrideClrMapping bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" " +
            "accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" " +
            "accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/></p:clrMapOvr></p:sld>"
    }

    private fun pptTable(id: Int, rows: List<List<String>>): String {
        val clean = rows.filter { it.any { c -> c.isNotBlank() } }.take(12)
        val cols = clean.maxOf { it.size }.coerceAtLeast(1)
        val w = SLIDE_W - 1371600
        val colW = w / cols
        val rowH = 370840
        val trs = clean.mapIndexed { ri, row ->
            val tcs = (0 until cols).joinToString("") { ci ->
                val v = row.getOrElse(ci) { "" }
                val fill = if (ri == 0) "<a:solidFill><a:srgbClr val=\"1F3864\"/></a:solidFill>"
                else if (ri % 2 == 0) "<a:solidFill><a:srgbClr val=\"F2F5FA\"/></a:solidFill>"
                else "<a:noFill/>"
                "<a:tc><a:txBody><a:bodyPr/><a:lstStyle/>" +
                    "<a:p><a:pPr b=\"0\"${if (ri == 0) " algn=\"ctr\"" else ""}/>" +
                    "<a:r><a:rPr lang=\"zh-CN\" sz=\"1400\"${if (ri == 0) " b=\"1\"" else ""} dirty=\"0\">" +
                    "<a:solidFill><a:srgbClr val=\"${if (ri == 0) "FFFFFF" else "262626"}\"/></a:solidFill>" +
                    "<a:latin typeface=\"Calibri\"/><a:ea typeface=\"等线\"/></a:rPr>" +
                    "<a:t>${esc(v)}</a:t></a:r></a:p></a:txBody>" +
                    "<a:tcPr marL=\"91440\" marR=\"91440\" marT=\"45720\" marB=\"45720\">$fill" +
                    "<a:lnT w=\"6350\" cap=\"flat\"><a:solidFill><a:srgbClr val=\"BFBFBF\"/></a:solidFill></a:lnT>" +
                    "<a:lnB w=\"6350\" cap=\"flat\"><a:solidFill><a:srgbClr val=\"BFBFBF\"/></a:solidFill></a:lnB>" +
                    "</a:tcPr></a:tc>"
            }
            "<a:tr h=\"$rowH\">$tcs</a:tr>"
        }.joinToString("")
        val grid = (1..cols).joinToString("") { "<a:gridCol w=\"$colW\"/>" }
        return "<p:graphicFrame><p:nvGraphicFramePr>" +
            "<p:cNvPr id=\"$id\" name=\"表格\"/><p:cNvGraphicFramePr><a:graphicFrameLocks noGrp=\"1\"/></p:cNvGraphicFramePr>" +
            "<p:nvPr/></p:nvGraphicFramePr>" +
            "<p:xfrm><a:off x=\"685800\" y=\"1524000\"/><a:ext cx=\"$w\" cy=\"${rowH * clean.size}\"/></p:xfrm>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/table\">" +
            "<a:tbl><a:tblPr firstRow=\"1\" bandRow=\"1\"><a:tableStyleId>" +
            "{5C22544A-7EE6-4342-B048-85BDC9FD1C3A}</a:tableStyleId></a:tblPr>" +
            "<a:tblGrid>$grid</a:tblGrid>$trs</a:tbl></a:graphicData></a:graphic></p:graphicFrame>"
    }

    /**
     * 母版。`<p:txStyles>` 必须齐（title/body/other 三块），
     * 否则形状里的 `lvl` 与默认字号没有依据，PowerPoint 会按它的内置默认渲染成一片小字。
     */
    private const val PPT_MASTER = XML_DECL +
        """<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""" +
        "<p:cSld><p:bg><p:bgPr><a:solidFill><a:srgbClr val=\"FFFFFF\"/></a:solidFill><a:effectLst/></p:bgPr></p:bg>" +
        "<p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
        "</p:spTree></p:cSld>" +
        "<p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" " +
        "accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/>" +
        "<p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst>" +
        "<p:txStyles>" +
        "<p:titleStyle><a:lvl1pPr algn=\"l\"><a:defRPr sz=\"4400\" b=\"1\"><a:solidFill><a:srgbClr val=\"1F3864\"/></a:solidFill></a:defRPr></a:lvl1pPr></p:titleStyle>" +
        "<p:bodyStyle><a:lvl1pPr><a:defRPr sz=\"2000\"><a:solidFill><a:srgbClr val=\"262626\"/></a:solidFill></a:defRPr></a:lvl1pPr>" +
        "<a:lvl2pPr><a:defRPr sz=\"1700\"><a:solidFill><a:srgbClr val=\"404040\"/></a:solidFill></a:defRPr></a:lvl2pPr>" +
        "<a:lvl3pPr><a:defRPr sz=\"1500\"><a:solidFill><a:srgbClr val=\"595959\"/></a:solidFill></a:defRPr></a:lvl3pPr></p:bodyStyle>" +
        "<p:otherStyle><a:lvl1pPr><a:defRPr sz=\"1800\"/></a:lvl1pPr></p:otherStyle>" +
        "</p:txStyles></p:sldMaster>"

    /** 版式：`type="blank"` + 空 cSld/spTree，是"不继承任何东西"的最省事写法 */
    private const val PPT_LAYOUT = XML_DECL +
        """<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank" preserve="1">""" +
        "<p:cSld name=\"空白版式\"><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>" +
        "<p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>" +
        "</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>"

    /**
     * 主题。
     *
     * fmtScheme 的四组条目数是**硬性规定**：fillStyleLst 3 条、lnStyleLst 3 条、
     * effectStyleLst 3 条、bgFillStyleLst 3 条。少一条 PowerPoint 直接判定文件损坏，
     * 而 WPS 往往还能打开 —— 所以只用 WPS 自测会漏掉这个错，必须两边都验。
     */
    private const val PPT_THEME = XML_DECL +
        """<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="PocketAgent">""" +
        "<a:themeElements>" +
        "<a:clrScheme name=\"PocketAgent\">" +
        "<a:dk1><a:srgbClr val=\"262626\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1>" +
        "<a:dk2><a:srgbClr val=\"1F3864\"/></a:dk2><a:lt2><a:srgbClr val=\"F2F5FA\"/></a:lt2>" +
        "<a:accent1><a:srgbClr val=\"2E5496\"/></a:accent1><a:accent2><a:srgbClr val=\"5B9BD5\"/></a:accent2>" +
        "<a:accent3><a:srgbClr val=\"70AD47\"/></a:accent3><a:accent4><a:srgbClr val=\"FFC000\"/></a:accent4>" +
        "<a:accent5><a:srgbClr val=\"ED7D31\"/></a:accent5><a:accent6><a:srgbClr val=\"A5A5A5\"/></a:accent6>" +
        "<a:hlink><a:srgbClr val=\"0563C1\"/></a:hlink><a:folHlink><a:srgbClr val=\"954F72\"/></a:folHlink>" +
        "</a:clrScheme>" +
        "<a:fontScheme name=\"PocketAgent\">" +
        "<a:majorFont><a:latin typeface=\"Calibri Light\"/><a:ea typeface=\"等线\"/><a:cs typeface=\"\"/>" +
        "<a:font script=\"Hans\" typeface=\"等线\"/></a:majorFont>" +
        "<a:minorFont><a:latin typeface=\"Calibri\"/><a:ea typeface=\"等线\"/><a:cs typeface=\"\"/>" +
        "<a:font script=\"Hans\" typeface=\"等线\"/></a:minorFont>" +
        "</a:fontScheme>" +
        "<a:fmtScheme name=\"PocketAgent\">" +
        "<a:fillStyleLst>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
        "<a:gradFill rotWithShape=\"1\"><a:gsLst>" +
        "<a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"110000\"/><a:satMod val=\"105000\"/><a:tint val=\"67000\"/></a:schemeClr></a:gs>" +
        "<a:gs pos=\"50000\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"105000\"/><a:satMod val=\"103000\"/><a:tint val=\"73000\"/></a:schemeClr></a:gs>" +
        "<a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"105000\"/><a:satMod val=\"109000\"/><a:tint val=\"81000\"/></a:schemeClr></a:gs>" +
        "</a:gsLst><a:lin ang=\"5400000\" scaled=\"0\"/></a:gradFill>" +
        "<a:gradFill rotWithShape=\"1\"><a:gsLst>" +
        "<a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:satMod val=\"103000\"/><a:lumMod val=\"102000\"/><a:tint val=\"94000\"/></a:schemeClr></a:gs>" +
        "<a:gs pos=\"50000\"><a:schemeClr val=\"phClr\"><a:satMod val=\"110000\"/><a:lumMod val=\"100000\"/><a:shade val=\"100000\"/></a:schemeClr></a:gs>" +
        "<a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:lumMod val=\"99000\"/><a:satMod val=\"120000\"/><a:shade val=\"78000\"/></a:schemeClr></a:gs>" +
        "</a:gsLst><a:lin ang=\"5400000\" scaled=\"0\"/></a:gradFill>" +
        "</a:fillStyleLst>" +
        "<a:lnStyleLst>" +
        "<a:ln w=\"6350\" cap=\"flat\" cmp=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>" +
        "<a:ln w=\"12700\" cap=\"flat\" cmp=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>" +
        "<a:ln w=\"19050\" cap=\"flat\" cmp=\"sng\" algn=\"ctr\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill><a:prstDash val=\"solid\"/><a:miter lim=\"800000\"/></a:ln>" +
        "</a:lnStyleLst>" +
        "<a:effectStyleLst>" +
        "<a:effectStyle><a:effectLst/></a:effectStyle>" +
        "<a:effectStyle><a:effectLst/></a:effectStyle>" +
        "<a:effectStyle><a:effectLst><a:outerShdw blurRad=\"57150\" dist=\"19050\" dir=\"5400000\" algn=\"ctr\" rotWithShape=\"0\">" +
        "<a:srgbClr val=\"000000\"><a:alpha val=\"63000\"/></a:srgbClr></a:outerShdw></a:effectLst></a:effectStyle>" +
        "</a:effectStyleLst>" +
        "<a:bgFillStyleLst>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill>" +
        "<a:solidFill><a:schemeClr val=\"phClr\"><a:tint val=\"95000\"/><a:satMod val=\"170000\"/></a:schemeClr></a:solidFill>" +
        "<a:gradFill rotWithShape=\"1\"><a:gsLst>" +
        "<a:gs pos=\"0\"><a:schemeClr val=\"phClr\"><a:tint val=\"93000\"/><a:satMod val=\"150000\"/><a:shade val=\"98000\"/><a:lumMod val=\"102000\"/></a:schemeClr></a:gs>" +
        "<a:gs pos=\"50000\"><a:schemeClr val=\"phClr\"><a:tint val=\"98000\"/><a:satMod val=\"130000\"/><a:shade val=\"90000\"/><a:lumMod val=\"103000\"/></a:schemeClr></a:gs>" +
        "<a:gs pos=\"100000\"><a:schemeClr val=\"phClr\"><a:shade val=\"63000\"/><a:satMod val=\"120000\"/></a:schemeClr></a:gs>" +
        "</a:gsLst><a:lin ang=\"5400000\" scaled=\"1\"/></a:gradFill>" +
        "</a:bgFillStyleLst>" +
        "</a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>"
}
