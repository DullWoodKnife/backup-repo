package com.example.databackup.data.local

import org.apache.poi.hwpf.HWPFDocument
import org.apache.poi.hwpf.usermodel.Range
import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger

/**
 * .doc 格式（Word 97-2003 / OLE2）SCI 论文格式化器
 *
 * 使用 Apache POI HWPF (Horrible Word Processor Format) 读取 .doc 文件，
 * 提取文本段落和基本样式信息，然后转换为 .docx 格式并应用 SCI 排版。
 *
 * ============================================================
 * 原因：为什么不直接用 HWPF 修改 .doc？
 * - HWPF 是 POI 中最不成熟的模块，不支持修改字体、行距、页边距等核心排版属性
 * - HWPF 不支持写入修改后的文档（只能读取）
 * - 因此采用"读取 .doc → 提取内容 → 生成 .docx → 应用 SCI 格式"的策略
 *
 * ============================================================
 * 风险说明：
 * 1. 格式丢失：
 *    .doc → .docx 转换过程中，表格、图片、页眉页脚、嵌入对象等复杂元素会丢失。
 *    仅保留纯文本段落和基本的粗体/斜体/字号信息。
 *
 * 2. 编码问题：
 *    .doc 文件可能使用非 Unicode 编码（如 GBK/GB2312），HWPF 读取时可能乱码。
 *    当前依赖 HWPF 的默认编码检测，对中文文档通常有效但非 100% 可靠。
 *
 * 3. 内存占用：
 *    HWPF 将整个文档加载到内存，超大 .doc 文件（>20MB）可能导致 OOM。
 *    当前限制为 50MB，超过直接跳过。
 *
 * 4. 样式信息不完整：
 *    .doc 中的字体/字号信息提取依赖 Range.getCharacterRun()，
 *    但部分文档的样式信息存储在全局样式表中，HWPF 解析不一定完整。
 *    转换后的 .docx 会统一使用 SCI 标准格式覆盖。
 */
class DocConverter {

    companion object {
        const val CONVERTED_SUFFIX = "_converted.docx"
    }

    /**
     * 将 .doc 文件转换为 .docx 并应用 SCI 格式
     * @param docFile 源 .doc 文件
     * @param config SCI 格式配置
     * @return Result 包含转换后的 .docx 文件引用和结果描述
     */
    fun convertAndFormat(docFile: File, config: SCIDocumentFormatter.FormatConfig): Result<File> {
        return try {
            if (!docFile.exists() || !docFile.canRead()) {
                return Result.failure(Exception("文件不存在或无法读取: ${docFile.name}"))
            }
            if (docFile.length() > 50 * 1024 * 1024) {
                return Result.failure(Exception("文件超过 50MB: ${docFile.name}"))
            }

            // 输出文件名：原文件名_converted.docx
            val outputName = docFile.nameWithoutExtension + CONVERTED_SUFFIX
            val outputFile = File(docFile.parent, outputName)

            FileInputStream(docFile).use { fis ->
                val hwpfDoc = HWPFDocument(fis)
                val range = hwpfDoc.range

                // 创建新的 .docx 文档
                val docxDoc = XWPFDocument()

                // 复制页面设置（A4 / Letter + 标准页边距）
                applyPageSettings(docxDoc, config)

                // 遍历 .doc 段落，提取文本和基本样式
                val paragraphCount = range.numParagraphs()
                for (i in 0 until paragraphCount) {
                    val hwpfParagraph = range.getParagraph(i)
                    val text = hwpfParagraph.text()

                    if (text.isBlank()) continue

                    // 创建 .docx 段落
                    val xwpfParagraph = docxDoc.createParagraph()

                    // 提取 .doc 中的字体/字号信息
                    val numRuns = hwpfParagraph.numCharacterRuns()
                    if (numRuns > 0) {
                        for (j in 0 until numRuns) {
                            val run = hwpfParagraph.getCharacterRun(j)
                            val xwpfRun = xwpfParagraph.createRun()
                            xwpfRun.setText(run.text())

                            // 尝试保留原文档的字体信息
                            val fontName = run.fontName
                            if (fontName != null && fontName.isNotEmpty()) {
                                xwpfRun.fontFamily = fontName
                            } else {
                                xwpfRun.fontFamily = config.fontFamily
                            }

                            // 字号：HWPF 返回半磅值，需要除以 2
                            val fontSizeHalfPt = run.fontSize
                            if (fontSizeHalfPt > 0) {
                                xwpfRun.fontSize = fontSizeHalfPt / 2
                            } else {
                                xwpfRun.fontSize = SCIDocumentFormatter.SIZE_BODY
                            }

                            xwpfRun.isBold = run.isBold
                            xwpfRun.isItalic = run.isItalic
                            xwpfRun.color = "000000"
                        }
                    } else {
                        // 没有子 run，直接写入纯文本
                        val run = xwpfParagraph.createRun()
                        run.setText(text)
                        run.fontFamily = config.fontFamily
                        run.fontSize = SCIDocumentFormatter.SIZE_BODY
                        run.color = "000000"
                    }

                    // 设置行距
                    setLineSpacing(xwpfParagraph, config)
                }

                // 保存为 .docx
                FileOutputStream(outputFile).use { fos ->
                    docxDoc.write(fos)
                }
                docxDoc.close()
                hwpfDoc.close()
            }

            // 对生成的 .docx 再应用一次完整的 SCI 格式化（覆盖样式）
            val formatter = SCIDocumentFormatter()
            formatter.setConfig(config)
            val formatResult = formatter.formatDocument(outputFile)

            if (formatResult.isFailure) {
                // 格式化失败但转换成功，仍返回转换后的文件
                outputFile.delete()
                return Result.failure(formatResult.exceptionOrNull()
                    ?: Exception("转换后格式化失败"))
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun applyPageSettings(document: XWPFDocument, config: SCIDocumentFormatter.FormatConfig) {
        val body = document.document.body
        val sectPr = body.sectPr ?: body.addNewSectPr()
        val pageSize = sectPr.pgSz ?: sectPr.addNewPgSz()
        if (config.paperSize.uppercase() == "LETTER") {
            pageSize.w = BigInteger.valueOf((216 * 1440 / 25.4).toLong())
            pageSize.h = BigInteger.valueOf((279 * 1440 / 25.4).toLong())
        } else {
            pageSize.w = BigInteger.valueOf((210 * 1440 / 25.4).toLong())
            pageSize.h = BigInteger.valueOf((297 * 1440 / 25.4).toLong())
        }
        val pageMar = sectPr.pgMar ?: sectPr.addNewPgMar()
        val marginAll = BigInteger.valueOf(1440)
        pageMar.top = marginAll
        pageMar.bottom = marginAll
        pageMar.left = marginAll
        pageMar.right = marginAll
    }

    private fun setLineSpacing(paragraph: XWPFParagraph, config: SCIDocumentFormatter.FormatConfig) {
        val ctp = paragraph.ctp
        val pPr = ctp.pPr ?: ctp.addNewPPr()
        var spacing = pPr.spacing
        if (spacing == null) spacing = pPr.addNewSpacing()
        val lineValue = when (config.lineSpacing) {
            "single" -> BigInteger.valueOf(240)
            "1.5" -> BigInteger.valueOf(360)
            else -> BigInteger.valueOf(480)
        }
        spacing.line = lineValue
        spacing.lineRule = STLineSpacingRule.AUTO
    }
}
