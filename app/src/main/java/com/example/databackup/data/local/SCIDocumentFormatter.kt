package com.example.databackup.data.local

import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger

class SCIDocumentFormatter {

    companion object {
        const val FONT_BODY = "Times New Roman"
        const val FONT_SANS = "Arial"
        const val SIZE_TITLE = 16
        const val SIZE_SECTION = 14
        const val SIZE_SUBSECTION = 12
        const val SIZE_SUBSUBSECTION = 12
        const val SIZE_BODY = 12
        const val SIZE_ABSTRACT = 12
        val DOUBLE_SPACING = BigInteger.valueOf(480)
        val MARGIN_ALL = BigInteger.valueOf(1440)
        val PAGE_WIDTH = BigInteger.valueOf((210 * 1440 / 25.4).toLong())
        val PAGE_HEIGHT = BigInteger.valueOf((297 * 1440 / 25.4).toLong())
    }

    data class FormatConfig(
        val paperSize: String = "A4",
        val enableTitlePage: Boolean = true,
        val enableAbstract: Boolean = true,
        val enableKeywords: Boolean = true,
        val lineSpacing: String = "double",
        val fontFamily: String = FONT_BODY,
        val align: String = "justified"
    )

    private var config: FormatConfig = FormatConfig()

    fun setConfig(config: FormatConfig) {
        this.config = config
    }

    fun formatDocument(file: File): Result<String> {
        return try {
            if (!file.exists() || !file.canRead()) {
                return Result.failure(Exception("文件不存在或无法读取: ${file.name}"))
            }
            if (file.length() > 50 * 1024 * 1024) {
                return Result.failure(Exception("文件超过 50MB，跳过格式化: ${file.name}"))
            }
            val backupFile = File(file.parent, file.name + ".bak")
            file.copyTo(backupFile, overwrite = true)
            val resultMsg = when {
                file.name.lowercase().endsWith(".docx") -> formatDocx(file)
                file.name.lowercase().endsWith(".doc") -> formatDoc(file)
                else -> "不支持的文件格式: ${file.name}"
            }
            Result.success(resultMsg)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formatDocx(file: File): String {
        FileInputStream(file).use { fis ->
            val document = XWPFDocument(fis)
            applyPageSettings(document)
            val paragraphs = document.paragraphs
            if (paragraphs.isEmpty()) {
                return "[${file.name}] 文档为空"
            }
            if (config.enableTitlePage) {
                formatTitlePage(document, paragraphs)
            }
            formatBody(document, paragraphs)
            FileOutputStream(file).use { fos ->
                document.write(fos)
            }
            document.close()
        }
        return buildString {
            append("[${file.name}] 已按 SCI 论文通用格式排版")
            append("\n  \u00b7 字体: ${config.fontFamily} ${SIZE_BODY}pt")
            append("\n  \u00b7 行距: ${if (config.lineSpacing == "double") "双倍" else config.lineSpacing}")
            append("\n  \u00b7 页边距: 2.54cm (1 inch)")
            append("\n  \u00b7 对齐: ${if (config.align == "justified") "两端对齐" else "左对齐"}")
            append("\n  \u00b7 原文件已备份为 .bak")
        }
    }

    private fun formatDoc(file: File): String {
        return "[${file.name}] .doc 旧格式暂不支持自动排版，建议先用 Word/WPS 另存为 .docx"
    }

    private fun applyPageSettings(document: XWPFDocument) {
        val body = document.document.body
        val sectPr = body.sectPr ?: body.addNewSectPr()
        val pageSize = sectPr.pgSz ?: sectPr.addNewPgSz()
        if (config.paperSize.uppercase() == "LETTER") {
            pageSize.w = BigInteger.valueOf((216 * 1440 / 25.4).toLong())
            pageSize.h = BigInteger.valueOf((279 * 1440 / 25.4).toLong())
        } else {
            pageSize.w = PAGE_WIDTH
            pageSize.h = PAGE_HEIGHT
        }
        val pageMar = sectPr.pgMar ?: sectPr.addNewPgMar()
        pageMar.top = MARGIN_ALL
        pageMar.bottom = MARGIN_ALL
        pageMar.left = MARGIN_ALL
        pageMar.right = MARGIN_ALL
    }

    private fun formatTitlePage(document: XWPFDocument, paragraphs: List<XWPFParagraph>) {
        var titleFound = false
        for (i in 0 until minOf(8, paragraphs.size)) {
            val paragraph = paragraphs[i]
            val text = paragraph.text.trim()
            if (text.isEmpty()) continue
            when {
                !titleFound && text.length <= 200 && !isKnownSection(text) -> {
                    paragraph.alignment = ParagraphAlignment.CENTER
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_TITLE
                        run.isBold = true
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph)
                    titleFound = true
                }
                isAbstractParagraph(text) -> {
                    paragraph.alignment = ParagraphAlignment.LEFT
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_ABSTRACT
                        run.isBold = false
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph)
                }
                isKeywordsParagraph(text) -> {
                    paragraph.alignment = ParagraphAlignment.LEFT
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_BODY
                        run.isBold = false
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph)
                }
                !titleFound -> {
                    paragraph.alignment = ParagraphAlignment.CENTER
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_BODY
                        run.isBold = false
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph)
                }
            }
            if (titleFound && i >= 5) break
        }
    }

    private fun formatBody(document: XWPFDocument, paragraphs: List<XWPFParagraph>) {
        val startIndex = if (config.enableTitlePage) 5 else 0
        for (i in startIndex until paragraphs.size) {
            val paragraph = paragraphs[i]
            val text = paragraph.text.trim()
            if (text.isEmpty()) continue
            when {
                isLevel1Heading(text) -> {
                    paragraph.alignment = ParagraphAlignment.LEFT
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_SECTION
                        run.isBold = true
                        run.isItalic = false
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph, extraBefore = 240, extraAfter = 120)
                }
                isLevel2Heading(text) -> {
                    paragraph.alignment = ParagraphAlignment.LEFT
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_SUBSECTION
                        run.isBold = true
                        run.isItalic = true
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph, extraBefore = 120, extraAfter = 60)
                }
                isLevel3Heading(text) -> {
                    paragraph.alignment = ParagraphAlignment.LEFT
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_SUBSUBSECTION
                        run.isBold = false
                        run.isItalic = true
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph, extraBefore = 60, extraAfter = 60)
                }
                isReferenceItem(text) -> {
                    paragraph.alignment = if (config.align == "justified") ParagraphAlignment.BOTH else ParagraphAlignment.LEFT
                    paragraph.firstLineIndent = 0
                    paragraph.indentFromLeft = 0
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_BODY
                        run.isBold = false
                        run.isItalic = false
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph)
                }
                else -> {
                    paragraph.alignment = if (config.align == "justified") ParagraphAlignment.BOTH else ParagraphAlignment.LEFT
                    paragraph.firstLineIndent = 0
                    for (run in paragraph.runs) {
                        run.fontFamily = config.fontFamily
                        run.fontSize = SIZE_BODY
                        run.isBold = false
                        run.isItalic = false
                        run.color = "000000"
                    }
                    setLineSpacing(paragraph)
                }
            }
        }
    }

    private fun setLineSpacing(paragraph: XWPFParagraph, extraBefore: Int = 0, extraAfter: Int = 0) {
        val ctp = paragraph.ctp
        val pPr = ctp.pPr ?: ctp.addNewPPr()
        var spacing = pPr.spacing
        if (spacing == null) spacing = pPr.addNewSpacing()
        val lineValue = when (config.lineSpacing) {
            "single" -> BigInteger.valueOf(240)
            "1.5" -> BigInteger.valueOf(360)
            else -> DOUBLE_SPACING
        }
        spacing.line = lineValue
        spacing.lineRule = STLineSpacingRule.AUTO
        if (extraBefore > 0) spacing.before = BigInteger.valueOf(extraBefore.toLong())
        if (extraAfter > 0) spacing.after = BigInteger.valueOf(extraAfter.toLong())
    }

    private val level1Keywords = listOf(
        "abstract", "introduction", "background",
        "materials and methods", "methods", "methodology",
        "results", "discussion", "conclusion", "conclusions",
        "references", "acknowledgments", "acknowledgements",
        "supplementary", "appendix", "ethics",
        "声明", "摘要", "引言", "方法", "结果", "讨论", "结论", "致谢", "参考文献"
    )

    private fun isKnownSection(text: String): Boolean {
        return level1Keywords.any { text.lowercase().startsWith(it) } ||
               text.lowercase().startsWith("keywords") ||
               text.lowercase().startsWith("key words")
    }

    private fun isAbstractParagraph(text: String): Boolean {
        return text.lowercase().startsWith("abstract") || text.lowercase().startsWith("摘要")
    }

    private fun isKeywordsParagraph(text: String): Boolean {
        return text.lowercase().startsWith("keywords") ||
               text.lowercase().startsWith("key words") ||
               text.lowercase().startsWith("关键词")
    }

    private fun isLevel1Heading(text: String): Boolean {
        val numberPrefix = Regex("^\\d+[.．]\\s*(.+)")
        val match = numberPrefix.find(text)
        val content = match?.groupValues?.get(1)?.lowercase()?.trim() ?: text.lowercase().trim()
        return level1Keywords.any { content.startsWith(it) }
    }

    private fun isLevel2Heading(text: String): Boolean {
        return text.matches(Regex("^\\d+[.．]\\d+[.．]?\\d*\\s+.+"))
    }

    private fun isLevel3Heading(text: String): Boolean {
        return text.matches(Regex("^\\d+[.．]\\d+[.．]\\d+\\s+.+")) ||
               text.matches(Regex("^\\([a-zA-Z0-9]+\\)\\s+.+"))
    }

    private fun isReferenceItem(text: String): Boolean {
        return text.matches(Regex("^\\[\\d+\\].*")) ||
               text.matches(Regex("^\\d+[.．]\\s*(.+)?"))
    }
}
