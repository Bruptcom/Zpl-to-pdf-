package com.exemplo.declaracao.util

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.File

class ZplToPdfConverter {
    // 10x15 cm
    private val PAGE = PageSize(10f * 28.35f, 15f * 28.35f)

    fun convert(zplFile: File, pdfFile: File) {
        val zplContent = zplFile.readText()
        
        // Decodificar ZPL mantendo quebras de linha
        val decoded = zplContent
            .replace("_C3_87", "Ç").replace("_C3_89", "É").replace("_C3_83", "Ã")
            .replace("_C3_95", "Õ").replace("_C3_81", "Á").replace("_C3_8D", "Í")
            .replace("_C3_93", "Ó").replace("_C3_9A", "Ú").replace("_C2_B0", "º")
            .replace("_C3_A9", "é").replace("_C3_A3", "ã").replace("_C3_A7", "ç")
            .replace("_C3_AD", "í").replace("_C3_B3", "ó").replace("_C3_BA", "ú")
            .replace("_C3_94", "Ô").replace("_C3_B4", "ô").replace("_C3_8A", "Ê")
            .replace("_C3_AA", "ê").replace("_C3_8F", "Ï").replace("_C3_AF", "ï")
            .replace("_C3_8B", "Ë").replace("_C3_AB", "ë")
            .replace("_C2_4", "ç")
            .replace(Regex("_C[0-9A-F]_[0-9A-F]"), "")
            // Remover apenas comandos ZPL, MANTER quebras de linha
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), "")
            .replace(Regex("\\^FS|\\^FD"), "")
            // NÃO substituir \n por espaço!
            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
            .trim()

        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(8f, 8f, 8f, 8f)

        // Dividir em linhas e renderizar cada uma
        val lines = decoded.split("\n", "\r\n", "\r")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var y = PAGE.height - 12f
        val maxWidth = PAGE.width - 16f
        val lineHeight = 9f

        lines.forEach { line ->
            if (y < 15f) return@forEach // Para de renderizar se acabar o espaço

            // Detectar tipo de linha para ajustar fonte
            val fontSize = when {
                line.contains("Mercado Livre", ignoreCase = true) -> 11f
                line.contains("DESTINATARIO") || line.contains("Remetente:") -> 9f
                line.matches(Regex("AP[0-9]+BR")) -> 11f
                line.contains("NF:") || line.contains("SHP:") || line.contains("Contrato:") || line.contains("PESO:") -> 7f
                else -> 7f
            }

            val isBold = line.contains("Mercado Livre", ignoreCase = true) ||
                        line.contains("DESTINATARIO") ||
                        line.contains("Remetente:") ||
                        line.matches(Regex("AP[0-9]+BR"))

            val alignment = when {
                line.matches(Regex("AP[0-9]+BR")) -> TextAlignment.CENTER
                line.contains("PESO:") && line.contains("Contrato:") -> TextAlignment.RIGHT
                else -> TextAlignment.LEFT
            }

            try {
                val paragraph = Paragraph(line)
                    .setFontSize(fontSize)
                    .setTextAlignment(alignment)
                    .setMarginBottom(0f)
                    .setMarginTop(0f)
                    .setMultipliedLeading(0.95f)
                
                if (isBold) paragraph.setBold()
                
                doc.add(paragraph)
                y -= lineHeight
            } catch (e: Exception) {
                // Ignorar erro
            }
        }

        doc.close()
    }
}
