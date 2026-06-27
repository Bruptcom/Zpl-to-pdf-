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
        
        // Extrair dados
        val nf = find(decoded, "NF:\\s*([0-9]+)")
        val shp = find(decoded, "SHP:\\s*([0-9]+)")
        val contrato = find(decoded, "Contrato:\\s*([0-9]+)")
        val peso = find(decoded, "PESO:\\s*([0-9]+g?)")
        val rastreio = find(decoded, "(AP[0-9]+BR)")
        
        val destNome = find(decoded, "DESTINATARIO\\s*\\n?\\s*([^\n]+)")
        val destEnd = find(decoded, "DESTINATARIO[^\n]*\\n[^\n]*\\n?\\s*([^\n]+)")
        val destCep = find(decoded, "([0-9]{5}-?[0-9]{3}).*?(?=Remetente)", RegexOption.DOT_MATCHES_ALL)
        
        val remNome = find(decoded, "Remetente:\\s*\\n?\\s*([^\n]+)")
        val remEnd = find(decoded, "Remetente:[^\n]*\\n[^\n]*\\n?\\s*([^\n]+)")
        val remCep = find(decoded, "(Remetente[^\n]*\\n[^\n]*\\n[^\\n]*\\n?\\s*([0-9]{5}-?[0-9]{3}))")
            .let { find(decoded, "([0-9]{5}-?[0-9]{3})", startIndex = decoded.indexOf("Remetente")) }

        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(8f, 8f, 8f, 8f)

        var y = PAGE.height - 10f
        val w = PAGE.width - 16f
        
        // Mercado Livre
        addText(doc, "Mercado Livre", 10f, y, w, TextAlignment.LEFT, true)
        y -= 12f
        
        // NF e Contrato
        addText(doc, "NF: $nf", 7f, y, w/2, TextAlignment.LEFT)
        addText(doc, "Contrato: $contrato", 7f, y, w, TextAlignment.RIGHT)
        y -= 9f
        
        // SHP e PESO
        addText(doc, "SHP: $shp", 7f, y, w/2, TextAlignment.LEFT)
        addText(doc, "PESO: $peso", 7f, y, w, TextAlignment.RIGHT)
        y -= 9f
        
        // PAC
        addText(doc, "PAC", 8f, y, w, TextAlignment.CENTER)
        y -= 11f
        
        // Rastreio
        if (rastreio.isNotEmpty()) {
            addText(doc, rastreio, 10f, y, w, TextAlignment.CENTER, true)
            y -= 15f
        }
        
        // Recebedor
        addText(doc, "Recebedor:__________________", 7f, y, w, TextAlignment.LEFT)
        y -= 8f
        addText(doc, "Assinatura:____________  Documento:____________", 6f, y, w, TextAlignment.LEFT)
        y -= 12f
        
        // DESTINATARIO
        addText(doc, "DESTINATÁRIO", 8f, y, w, TextAlignment.LEFT, true)
        y -= 10f
        
        if (destNome.isNotEmpty()) {
            addText(doc, destNome, 7f, y, w, TextAlignment.LEFT)
            y -= 8f
        }
        
        if (destEnd.isNotEmpty() && destEnd != destNome) {
            addText(doc, destEnd, 6f, y, w, TextAlignment.LEFT)
            y -= 8f
        }
        
        if (destCep.isNotEmpty()) {
            addText(doc, destCep, 7f, y, w, TextAlignment.LEFT)
            y -= 10f
        }
        
        // Remetente
        addText(doc, "Remetente:", 8f, y, w, TextAlignment.LEFT, true)
        y -= 10f
        
        if (remNome.isNotEmpty()) {
            addText(doc, remNome, 7f, y, w, TextAlignment.LEFT)
            y -= 8f
        }
        
        if (remEnd.isNotEmpty() && remEnd != remNome) {
            addText(doc, remEnd, 6f, y, w, TextAlignment.LEFT)
            y -= 8f
        }
        
        if (remCep.isNotEmpty()) {
            addText(doc, remCep, 7f, y, w, TextAlignment.LEFT)
            y -= 10f
        }
        
        // Número
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
            .replace("_C3_AA", "ê").replace("_C2_4", "ç")
            .replace(Regex("_C[0-9A-F]_[0-9A-F]"), "")
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), "\n")
            .replace(Regex("\\^FS|\\^FD"), "")
            .replace(Regex("\\|"), "\n")
            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
            .trim()
    }

    private fun find(text: String, pattern: String, options: RegexOption = RegexOption.IGNORE_CASE, startIndex: Int = 0): String {
        return try {
            Regex(pattern, options).find(text.substring(startIndex))
                ?.groupValues?.getOrNull(1)?.trim()?.take(60) ?: ""
        } catch (e: Exception) { "" }
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
