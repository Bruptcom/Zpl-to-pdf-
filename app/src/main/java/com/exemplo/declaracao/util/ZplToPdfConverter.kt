package com.exemplo.declaracao.util

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.File

class ZplToPdfConverter {
    private val PAGE = PageSize(10f * 28.35f, 15f * 28.35f)

    fun convert(zplFile: File, pdfFile: File) {
        val zplContent = zplFile.readText()
        val decodedText = decodeZPL(zplContent)
        
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(8f, 8f, 8f, 8f)

        // Extrair dados do texto decodificado
        val nf = extract(decodedText, "NF:\\s*([0-9]+)")
        val shp = extract(decodedText, "SHP:\\s*([0-9]+)")
        val contrato = extract(decodedText, "Contrato:\\s*([0-9]+)")
        val peso = extract(decodedText, "PESO:\\s*([0-9]+g?)")
        val rastreio = extract(decodedText, "(AP[0-9]+BR)")
        
        val destinatario = extractSection(decodedText, "DESTINATARIO", "Remetente:")
        val remetente = extractSection(decodedText, "Remetente:", "DACE")
        
        // Renderizar etiqueta
        var y = PAGE.height - 10f
        val w = PAGE.width - 16f
        
        // Cabeçalho
        addText(doc, "Mercado Livre", 10f, y, w, TextAlignment.LEFT, true)
        y -= 12f
        
        addText(doc, "NF: $nf", 8f, y, w/2, TextAlignment.LEFT)
        addText(doc, "Contrato: $contrato", 8f, y, w, TextAlignment.RIGHT)
        y -= 10f
        
        addText(doc, "SHP: $shp", 8f, y, w/2, TextAlignment.LEFT)
        addText(doc, "PESO: $peso", 8f, y, w, TextAlignment.RIGHT)
        y -= 12f
        
        if (rastreio.isNotEmpty()) {
            addText(doc, rastreio, 11f, y, w, TextAlignment.CENTER, true)
            y -= 15f
        }
        
        y -= 10f
        
        // Recebedor
        addText(doc, "Recebedor: _______________________", 8f, y, w, TextAlignment.LEFT)
        y -= 10f
        addText(doc, "Assinatura: ____________ Documento: ____________", 7f, y, w, TextAlignment.LEFT)
        y -= 14f
        
        // Destinatário
        addText(doc, "DESTINATÁRIO", 9f, y, w, TextAlignment.LEFT, true)
        y -= 11f
        
        destinatario.split("\n").forEach { line ->
            if (line.isNotBlank() && y > 40f) {
                addText(doc, line.trim(), 7f, y, w, TextAlignment.LEFT)
                y -= 9f
            }
        }
        
        y -= 8f
        
        // Remetente
        addText(doc, "Remetente:", 9f, y, w, TextAlignment.LEFT, true)
        y -= 11f
        
        remetente.split("\n").forEach { line ->
            if (line.isNotBlank() && y > 20f) {
                addText(doc, line.trim(), 7f, y, w, TextAlignment.LEFT)
                y -= 9f
            }
        }
        
        doc.close()
    }

    private fun decodeZPL(zpl: String): String {
        return zpl
            .replace("_C3_87", "Ç").replace("_C3_89", "É").replace("_C3_83", "Ã")
            .replace("_C3_95", "Õ").replace("_C3_81", "Á").replace("_C3_8D", "Í")
            .replace("_C3_93", "Ó").replace("_C3_9A", "Ú").replace("_C2_B0", "º")
            .replace("_C3_A9", "é").replace("_C3_A3", "ã").replace("_C3_A7", "ç")
            .replace("_C3_AD", "í").replace("_C3_B3", "ó").replace("_C3_BA", "ú")
            .replace("_C2_4", "ç")
            .replace(Regex("_C[0-9A-F]_[0-9A-F]"), "")
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), " ")
            .replace(Regex("\\^FS|\\^FD"), " ")
            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
            .replace(Regex("\\|"), "\n")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extract(text: String, pattern: String): String {
        return Regex(pattern, RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun extractSection(text: String, start: String, end: String): String {
        val startIndex = text.indexOf(start, ignoreCase = true)
        if (startIndex == -1) return ""
        
        val endIndex = text.indexOf(end, startIndex, ignoreCase = true).takeIf { it != -1 } ?: text.length
        return text.substring(startIndex + start.length, endIndex)
            .replace(Regex("\\s+"), " ").trim()
    }

    private fun addText(
        doc: Document,
        text: String,
        size: Float,
        y: Float,
        width: Float,
        align: TextAlignment,
        bold: Boolean = false
    ) {
        if (y < 15f || text.isBlank()) return
        
        val p = Paragraph(text).setFontSize(size).setTextAlignment(align)
            .setFixedPosition(8f, y, width).setMarginBottom(0f).setMarginTop(0f)
        if (bold) p.setBold()
        
        try { doc.add(p) } catch (e: Exception) {}
    }
}
