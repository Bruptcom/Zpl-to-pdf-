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
        
        // Extrair dados
        val nf = extractAfter(text, "NF:").take(10)
        val shp = extractAfter(text, "SHP:").take(15)
        val contrato = extractAfter(text, "Contrato:").take(12)
        val peso = extractAfter(text, "PESO:").take(10)
        val rastreio = findPattern(text, "AP[0-9]+BR")
        
        val destNome = extractLineAfter(text, "DESTINATARIO")
        val destEndereco = extractLineAfter(text, destNome).take(60)
        val destCidade = extractAfter(text, "Vitória").let { "Vitória $it" }.take(40)
        
        val remNome = extractLineAfter(text, "Remetente:").take(60)
        val remEndereco = extractAfter(text, "Estrada da Capoeira").take(70)
        val remCidade = extractAfter(text, "Rio de Janeiro").take(30)

        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(5f, 5f, 5f, 5f)

        var y = PAGE.height - 8f
        val w = PAGE.width - 10f
        
        // Mercado Livre
        addText(doc, "Mercado Livre", 10f, y, w, TextAlignment.LEFT, true)
        y -= 12f
        
        // NF e Contrato
        addText(doc, "NF: $nf", 7f, y, w/3, TextAlignment.LEFT)
        addText(doc, "Contrato: $contrato", 7f, y, w/3, TextAlignment.CENTER)
        addText(doc, "PESO: $peso", 7f, y, w, TextAlignment.RIGHT)
        y -= 10f
        
        // SHP e PAC
        addText(doc, "SHP: $shp", 7f, y, w/2, TextAlignment.LEFT)
        addText(doc, "PAC", 8f, y, w, TextAlignment.RIGHT)
        y -= 12f
        
        // Rastreio
        addText(doc, rastreio, 11f, y, w, TextAlignment.CENTER, true)
        y -= 18f
        
        // Recebedor
        addText(doc, "Recebedor:__________________", 7f, y, w, TextAlignment.LEFT)
        y -= 8f
        addText(doc, "Assinatura:____________  Documento:____________", 6f, y, w, TextAlignment.LEFT)
        y -= 14f
        
        // DESTINATARIO
        addText(doc, "DESTINATARIO", 8f, y, w, TextAlignment.LEFT, true)
        y -= 10f
        
        addText(doc, destNome, 7f, y, w, TextAlignment.LEFT)
        y -= 9f
        
        addText(doc, destEndereco, 6f, y, w, TextAlignment.LEFT)
        y -= 9f
        
        addText(doc, destCidade, 7f, y, w, TextAlignment.LEFT)
        y -= 12f
        
        // Remetente
        addText(doc, "Remetente:", 8f, y, w, TextAlignment.LEFT, true)
        y -= 10f
        
        addText(doc, remNome, 7f, y, w, TextAlignment.LEFT)
        y -= 9f
        
        addText(doc, remEndereco, 6f, y, w, TextAlignment.LEFT)
        y -= 9f
        
        addText(doc, remCidade, 7f, y, w, TextAlignment.LEFT)
        y -= 12f
        
        // Número
        addText(doc, "*$shp*", 8f, y, w, TextAlignment.CENTER)
        
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
            .replace(Regex("_C[0-9A-F]_[0-9A-F]"), "")
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), "\n")
            .replace(Regex("\\^FS|\\^FD"), "")
            .replace(Regex("\\|"), "\n")
            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractAfter(text: String, marker: String): String {
        val idx = text.indexOf(marker, ignoreCase = true)
        if (idx == -1) return ""
        val start = idx + marker.length
        val end = text.indexOf("\n", start).takeIf { it != -1 } ?: start + 50
        return text.substring(start, end).trim()
    }

    private fun extractLineAfter(text: String, marker: String): String {
        val idx = text.indexOf(marker, ignoreCase = true)
        if (idx == -1) return ""
        val start = idx + marker.length
        val end = text.indexOf("\n", start).takeIf { it != -1 } ?: start + 80
        return text.substring(start, end).trim()
    }

    private fun findPattern(text: String, pattern: String): String {
        return Regex(pattern, RegexOption.IGNORE_CASE).find(text)
            ?.value?.trim() ?: ""
    }

    private fun addText(doc: Document, text: String, size: Float, y: Float, width: Float, align: TextAlignment, bold: Boolean = false) {
        if (y < 15f || text.isBlank()) return
        try {
            val p = Paragraph(text)
                .setFontSize(size)
                .setTextAlignment(align)
                .setFixedPosition(5f, y, width)
                .setMarginBottom(0f)
                .setMarginTop(0f)
            if (bold) p.setBold()
            doc.add(p)
        } catch (e: Exception) {}
    }
}
