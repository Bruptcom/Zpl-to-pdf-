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
        val decoded = decodeZPL(zplContent)
        
        // Extrair dados de forma mais robusta
        val nf = extractValue(decoded, "NF:", "SHP:")
        val shp = extractValue(decoded, "SHP:", "PESO:")
        val peso = extractValue(decoded, "PESO:", "Contrato:")
        val contrato = extractValue(decoded, "Contrato:", "\n")
        val rastreio = extractPattern(decoded, "(AP[0-9]+BR)")
        
        val destNome = extractSection(decoded, "DESTINATARIO", "Edificio|Rua|Apt|CEP")
        val destEnd = extractSection(decoded, "Rua|Jardim|Edificio", "Remetente:").take(80)
        val destCep = extractPattern(decoded, "([0-9]{5}-[0-9]{3})")
        
        val remNome = extractSection(decoded, "Remetente:", "Estrada|Referencia").take(60)
        val remEnd = extractSection(decoded, "Estrada|Capoeira", "DACE|23026").take(80)
        val remCep = extractPattern(decoded, "(23026-220|23026220)")

        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(8f, 8f, 8f, 8f)

        var y = PAGE.height - 10f
        val w = PAGE.width - 16f
        
        // Cabeçalho
        addText(doc, "Mercado Livre", 10f, y, w, TextAlignment.LEFT, true)
        y -= 12f
        
        addText(doc, "NF: $nf", 7f, y, w/2, TextAlignment.LEFT)
        addText(doc, "Contrato: $contrato", 7f, y, w, TextAlignment.RIGHT)
        y -= 9f
        
        addText(doc, "SHP: $shp", 7f, y, w/2, TextAlignment.LEFT)
        addText(doc, "PESO: $peso", 7f, y, w, TextAlignment.RIGHT)
        y -= 9f
        
        addText(doc, "PAC", 8f, y, w, TextAlignment.CENTER)
        y -= 11f
        
        if (rastreio.isNotEmpty()) {
            addText(doc, rastreio, 10f, y, w, TextAlignment.CENTER, true)
            y -= 15f
        }
        
        addText(doc, "Recebedor:__________________", 7f, y, w, TextAlignment.LEFT)
        y -= 8f
        addText(doc, "Assinatura:____________  Documento:____________", 6f, y, w, TextAlignment.LEFT)
        y -= 12f
        
        addText(doc, "DESTINATÁRIO", 8f, y, w, TextAlignment.LEFT, true)
        y -= 10f
        
        if (destNome.isNotEmpty()) {
            addText(doc, destNome, 7f, y, w, TextAlignment.LEFT)
            y -= 8f
        }
        
        if (destEnd.isNotEmpty()) {
            addText(doc, destEnd, 6f, y, w, TextAlignment.LEFT)
            y -= 8f
        }
        
        if (destCep.isNotEmpty()) {
            addText(doc, destCep, 7f, y, w, TextAlignment.LEFT)
            y -= 10f
        }
        
        addText(doc, "Remetente:", 8f, y, w, TextAlignment.LEFT, true)
        y -= 10f
        
        if (remNome.isNotEmpty()) {
            addText(doc, remNome, 7f, y, w, TextAlignment.LEFT)
            y -= 8f
        }
        
        if (remEnd.isNotEmpty()) {
            addText(doc, remEnd, 6f, y, w, TextAlignment.LEFT)
            y -= 8f
        }
        
        if (remCep.isNotEmpty()) {
            addText(doc, remCep, 7f, y, w, TextAlignment.LEFT)
            y -= 10f
        }
        
        if (shp.isNotEmpty()) {
            addText(doc, "*$shp*", 8f, y, w, TextAlignment.CENTER)
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
            .replace("_C3_94", "Ô").replace("_C3_B4", "ô").replace("_C3_8A", "Ê")
            .replace("_C3_AA", "ê").replace("_C3_8F", "Ï").replace("_C3_AF", "ï")
            .replace("_C2_4", "ç").replace("_C3_8F", "I")
            .replace(Regex("_C[0-9A-F]_[0-9A-F]"), "")
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), "\n")
            .replace(Regex("\\^FS|\\^FD"), "")
            .replace(Regex("\\|"), "\n")
            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractValue(text: String, start: String, end: String): String {
        val startIndex = text.indexOf(start, ignoreCase = true)
        if (startIndex == -1) return ""
        
        val endIndex = text.indexOf(end, startIndex + start.length, ignoreCase = true)
            .takeIf { it != -1 && it > startIndex } ?: (startIndex + 20).coerceAtMost(text.length)
        
        return text.substring(startIndex + start.length, endIndex)
            .replace(Regex("[^0-9a-zA-Z-]"), "").trim().take(15)
    }

    private fun extractSection(text: String, startMarker: String, endMarker: String): String {
        val startIndex = text.indexOf(startMarker, ignoreCase = true)
        if (startIndex == -1) return ""
        
        val endIndex = Regex(endMarker, RegexOption.IGNORE_CASE).find(text, startIndex + startMarker.length)
            ?.range?.first ?: (startIndex + 100).coerceAtMost(text.length)
        
        return text.substring(startIndex + startMarker.length, endIndex)
            .replace(Regex("^[,\\s]+"), "").trim()
    }

    private fun extractPattern(text: String, pattern: String): String {
        return Regex(pattern, RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.getOrNull(1)?.trim()?.take(20) ?: ""
    }

    private fun addText(doc: Document, text: String, size: Float, y: Float, width: Float, align: TextAlignment, bold: Boolean = false) {
        if (y < 15f || text.isBlank()) return
        try {
            val p = Paragraph(text).setFontSize(size).setTextAlignment(align)
                .setFixedPosition(8f, y, width).setMarginBottom(0f).setMarginTop(0f)
            if (bold) p.setBold()
            doc.add(p)
        } catch (e: Exception) {}
    }
}
