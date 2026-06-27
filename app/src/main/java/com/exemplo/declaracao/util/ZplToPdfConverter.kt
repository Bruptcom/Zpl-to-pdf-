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
        val lines = parseZPL(zplContent)
        
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(10f, 10f, 10f, 10f)

        var yPos = PAGE.height - 15f
        val lineWidth = PAGE.width - 20f
        val lineSpacing = 8f
        
        lines.forEach { line ->
            if (yPos < 15f) return@forEach
            
            val fontSize = when {
                line.contains("Mercado Livre", ignoreCase = true) -> 11f
                line.contains("DESTINATÁRIO") || line.contains("Remetente:") -> 9f
                line.contains("AP") && line.contains("BR") -> 10f
                else -> 7f
            }
            
            val alignment = when {
                line.contains("AP") && line.contains("BR") -> TextAlignment.CENTER
                line.contains("Contrato") && line.contains("PESO") -> TextAlignment.RIGHT
                else -> TextAlignment.LEFT
            }
            
            val isBold = line.contains("Mercado Livre", ignoreCase = true) ||
                        line.contains("DESTINATÁRIO") ||
                        line.contains("Remetente:")
            
            try {
                val paragraph = Paragraph(line.trim())
                    .setFontSize(fontSize)
                    .setTextAlignment(alignment)
                    .setFixedPosition(10f, yPos, lineWidth)
                    .setMarginBottom(0f)
                    .setMarginTop(0f)
                    .setMultipliedLeading(0.9f)
                
                if (isBold) paragraph.setBold()
                
                doc.add(paragraph)
                yPos -= lineSpacing + (if (fontSize > 9f) 2f else 0f)
            } catch (e: Exception) {
                // Ignorar erro
            }
        }
        
        doc.close()
    }

    private fun parseZPL(zpl: String): List<String> {
        val result = mutableListOf<String>()
        
        // Decodificar todo o texto ZPL
        var decoded = zpl
            // Decodificar caracteres especiais
            .replace("_C3_87", "Ç").replace("_C3_89", "É").replace("_C3_83", "Ã")
            .replace("_C3_95", "Õ").replace("_C3_81", "Á").replace("_C3_8D", "Í")
            .replace("_C3_93", "Ó").replace("_C3_9A", "Ú").replace("_C2_B0", "º")
            .replace("_C3_A9", "é").replace("_C3_A3", "ã").replace("_C3_A7", "ç")
            .replace("_C3_AD", "í").replace("_C3_B3", "ó").replace("_C3_BA", "ú")
            .replace("_C3_94", "Ô").replace("_C3_B4", "ô")
            .replace("_C2_4", "ç")
            .replace(Regex("_C[0-9A-F]_[0-9A-F]"), "")
            // Remover comandos ZPL
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), " ")
            .replace(Regex("\\^FS|\\^FD|\\^XF|\\^LH"), " ")
            .replace(Regex("\\|"), "\n")
            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
            .trim()
        
        // Dividir em linhas
        val rawLines = decoded.split("\n", "\r\n", "\r")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        // Processar cada linha
        rawLines.forEach { line ->
            val cleanLine = line
                .replace(Regex("\\s+"), " ")
                .trim()
            
            if (cleanLine.isNotEmpty() && cleanLine.length > 2) {
                // Quebrar linhas muito longas
                if (cleanLine.length > 50) {
                    cleanLine.chunked(45).forEach { chunk ->
                        result += chunk.trim()
                    }
                } else {
                    result += cleanLine
                }
            }
        }
        
        return result.ifEmpty { listOf(decoded.take(200)) }
    }
}
