package com.exemplo.declaracao.util

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.File

class ZplToPdfConverter {
    private val PAGE = PageSize(10f * 28.35f, 15f * 28.35f) // 10x15 cm

    fun convert(zplFile: File, pdfFile: File) {
        val zplContent = zplFile.readText()
        val text = decodeZPL(zplContent)
        
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(10f, 10f, 10f, 10f)

        // Extrair dados do texto decodificado
        val nf = getValueBetween(text, "NF:", "SHP:").trim()
        val shp = getValueBetween(text, "SHP:", "PESO:").trim()
        val peso = getValueBetween(text, "PESO:", "Contrato:").trim()
        val contrato = getValueBetween(text, "Contrato:", "\n").trim()
        val rastreio = findPattern(text, "(AP[0-9]+BR)")
        
        val destNome = getValueAfter(text, "DESTINATARIO", "\n").trim()
        val destEndereco = getValueAfter(text, destNome, "29060").take(60)
        val destCep = findPattern(text, "(29060-300|29060300)")
        
        val remNome = getValueAfter(text, "Remetente:", "\n").trim().take(60)
        val remEndereco = getValueAfter(text, "Estrada da Capoeira", "23026").take(70)
        val remCep = findPattern(text, "(23026-220|23026220)")

        // Renderizar etiqueta
        var y = PAGE.height - 12f
        val w = PAGE.width - 20f
        
        // Mercado Livre
        addText(doc, "Mercado Livre", 11f, y, w, TextAlignment.LEFT, true)
        y -= 14f
        
        // NF e Contrato na mesma linha
        addText(doc, "NF: $nf", 7f, y, w/2, TextAlignment.LEFT)
        addText(doc, "Contrato: $contrato", 7f, y, w, TextAlignment.RIGHT)
        y -= 10f
        
        // SHP e PESO na mesma linha
        addText(doc, "SHP: $shp", 7f, y, w/2, TextAlignment.LEFT)
        addText(doc, "PESO: $peso", 7f, y, w, TextAlignment.RIGHT)
        y -= 10f
        
        // PAC
        addText(doc, "PAC", 9f, y, w, TextAlignment.CENTER)
        y -= 12f
        
        // Código de rastreio
        if (rastreio.isNotEmpty()) {
            addText(doc, rastreio, 11f, y, w, TextAlignment.CENTER, true)
            y -= 16f
        }
        
        // Recebedor
        addText(doc, "Recebedor:__________________", 7f, y, w, TextAlignment.LEFT)
        y -= 9f
        addText(doc, "Assinatura:____________  Documento:____________", 6f, y, w, TextAlignment.LEFT)
        y -= 13f
        
        // DESTINATARIO
        addText(doc, "DESTINATÁRIO", 9f, y, w, TextAlignment.LEFT, true)
        y -= 11f
        
        if (destNome.isNotEmpty()) {
            addText(doc, destNome, 7f, y, w, TextAlignment.LEFT)
            y -= 9f
        }
        
        if (destEndereco.isNotEmpty()) {
            addText(doc, destEndereco, 6f, y, w, TextAlignment.LEFT)
            y -= 9f
        }
        
        if (destCep.isNotEmpty()) {
            addText(doc, destCep, 7f, y, w, TextAlignment.LEFT)
            y -= 11f
        }
        
        // Remetente
        addText(doc, "Remetente:", 9f, y, w, TextAlignment.LEFT, true)
        y -= 11f
        
        if (remNome.isNotEmpty()) {
            addText(doc, remNome, 7f, y, w, TextAlignment.LEFT)
            y -= 9f
        }
        
        if (remEndereco.isNotEmpty()) {
            addText(doc, remEndereco, 6f, y, w, TextAlignment.LEFT)
            y -= 9f
        }
        
        if (remCep.isNotEmpty()) {
            addText(doc, remCep, 7f, y, w, TextAlignment.LEFT)
            y -= 11f
        }
        
        // Número do pedido
        if (shp.isNotEmpty()) {
            addText(doc, "*$shp*", 8f, y, w, TextAlignment.CENTER)
        }
        
        doc.close()
    }

    private fun decodeZPL(zpl: String): String {
        return zpl
            // Decodificar UTF-8 encoding do ZPL
            .replace("_C3_87", "Ç").replace("_C3_89", "É").replace("_C3_83", "Ã")
            .replace("_C3_95", "Õ").replace("_C3_81", "Á").replace("_C3_8D", "Í")
            .replace("_C3_93", "Ó").replace("_C3_9A", "Ú").replace("_C2_B0", "º")
            .replace("_C3_A9", "é").replace("_C3_A3", "ã").replace("_C3_A7", "ç")
            .replace("_C3_AD", "í").replace("_C3_B3", "ó").replace("_C3_BA", "ú")
            .replace("_C3_94", "Ô").replace("_C3_B4", "ô").replace("_C3_8A", "Ê")
            .replace("_C3_AA", "ê").replace("_C3_8F", "Ï").replace("_C3_AF", "ï")
            .replace("_C3_8B", "Ë").replace("_C3_AB", "ë")
            .replace(Regex("_C[0-9A-F]_[0-9A-F]"), "")
            // Remover comandos ZPL
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), "\n")
            .replace(Regex("\\^FS|\\^FD"), "")
            .replace(Regex("\\|"), "\n")
            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun getValueBetween(text: String, start: String, end: String): String {
        val startIndex = text.indexOf(start, ignoreCase = true)
        if (startIndex == -1) return ""
        
        val endIndex = text.indexOf(end, startIndex + start.length, ignoreCase = true)
            .takeIf { it != -1 && it > startIndex } ?: (startIndex + 30).coerceAtMost(text.length)
        
        return text.substring(startIndex + start.length, endIndex).trim()
    }

    private fun getValueAfter(text: String, start: String, end: String): String {
        val startIndex = text.indexOf(start, ignoreCase = true)
        if (startIndex == -1) return ""
        
        val endIndex = text.indexOf(end, startIndex + start.length, ignoreCase = true)
            .takeIf { it != -1 && it > startIndex } ?: (startIndex + 80).coerceAtMost(text.length)
        
        return text.substring(startIndex + start.length, endIndex).trim()
    }

    private fun findPattern(text: String, pattern: String): String {
        return Regex(pattern, RegexOption.IGNORE_CASE).find(text)
            ?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun addText(doc: Document, text: String, size: Float, y: Float, width: Float, align: TextAlignment, bold: Boolean = false) {
        if (y < 15f || text.isBlank()) return
        try {
            val p = Paragraph(text)
                .setFontSize(size)
                .setTextAlignment(align)
                .setFixedPosition(10f, y, width)
                .setMarginBottom(0f)
                .setMarginTop(0f)
            if (bold) p.setBold()
            doc.add(p)
        } catch (e: Exception) {}
    }
}
