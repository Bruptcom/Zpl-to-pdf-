package com.exemplo.declaracao.util
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import java.io.File
class ZplToPdfConverter {
    private val PAGE = PageSize(10f * 28.35f, 15f * 28.35f)
    fun convert(zplFile: File, pdfFile: File) {
        val text = zplFile.readText()
        val pdf = PdfDocument(PdfWriter(pdfFile))
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(0f, 0f, 0f, 0f)
        val lines = text.split(Regex("\\^|~")).map { it.trim() }.filter { it.isNotEmpty() }
        var curX = 0; var curY = 0; var curFont = 10f
        for (line in lines) {
            when {
                line.startsWith("FO") -> {
                    val parts = line.removePrefix("FO").split(",").map { it.toIntOrNull() ?: 0 }
                    curX = parts.getOrElse(0){0}; curY = parts.getOrElse(1){0}
                }
                line.startsWith("A0") || line.startsWith("A1") -> {
                    val parts = line.substring(2).split(",").map { it.trim() }
                    curFont = parts.getOrNull(1)?.toFloatOrNull() ?: 10f
                }
                line.startsWith("FD") -> {
                    val txt = line.removePrefix("FD").substringBefore("^").trim()
                    if (txt.isNotEmpty()) {
                        val xPt = (curX / 800f) * PAGE.width
                        val yPt = PAGE.height - (curY / 1200f) * PAGE.height - 10f
                        val p = Paragraph(txt).setFontSize(curFont).setFixedPosition(xPt, yPt, PAGE.width - xPt)
                        doc.add(p)
                    }
                }
            }
        }
        doc.close()
    }
}
