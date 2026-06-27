package com.exemplo.declaracao.util

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.File

class ZplToPdfConverter {
    // 10x15 cm em pontos (1 cm = 28.35 pt)
    private val PAGE = PageSize(10f * 28.35f, 15f * 28.35f)
    private val PAGE_WIDTH = PAGE.width
    private val PAGE_HEIGHT = PAGE.height

    fun convert(zplFile: File, pdfFile: File) {
        val text = zplFile.readText()
        
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(5f, 5f, 5f, 5f)

        // Parse ZPL commands
        val elements = parseZPL(text)
        
        // Render elements
        var currentY = PAGE_HEIGHT - 10f
        val lineHeight = 12f
        
        elements.forEach { element ->
            if (currentY < 10f) {
                // Nova página se acabar o espaço
                doc.add(Paragraph("\n").setFixedPosition(currentY))
                currentY = PAGE_HEIGHT - 10f
            }
            
            val paragraph = Paragraph(element.text)
                .setFontSize(element.fontSize)
                .setTextAlignment(element.alignment)
                .setFixedPosition(5f, currentY, PAGE_WIDTH - 10f)
            
            doc.add(paragraph)
            currentY -= lineHeight + 2f
        }
        
        doc.close()
    }

    data class ZplElement(
        val text: String,
        val fontSize: Float = 10f,
        val alignment: TextAlignment = TextAlignment.LEFT
    )

    private fun parseZPL(zpl: String): List<ZplElement> {
        val elements = mutableListOf<ZplElement>()
        val lines = zpl.split("\n", "\r\n", "\r")
        
        var currentFontSize = 10f
        var currentAlignment = TextAlignment.LEFT
        
        lines.forEach { line ->
            val trimmedLine = line.trim()
            
            if (trimmedLine.isEmpty()) return@forEach
            
            // Processar comandos ZPL
            when {
                // Campo de texto ^FD...^FS
                trimmedLine.contains("^FD") && trimmedLine.contains("^FS") -> {
                    val text = extractFDContent(trimmedLine)
                    if (text.isNotEmpty()) {
                        elements += ZplElement(text, currentFontSize, currentAlignment)
                    }
                }
                // Fonte ^A0, ^A1, etc
                trimmedLine.startsWith("^A") -> {
                    currentFontSize = extractFontSize(trimmedLine)
                }
                // Posição ^FOx,y
                trimmedLine.startsWith("^FO") -> {
                    // Podemos usar para ajustar alinhamento
                }
                // Texto simples (fallback)
                trimmedLine.isNotEmpty() && !trimmedLine.startsWith("^") -> {
                    elements += ZplElement(trimmedLine, currentFontSize, currentAlignment)
                }
            }
        }
        
        // Se não encontrou elementos formatados, tenta extrair texto simples
        if (elements.isEmpty()) {
            lines.filter { it.trim().isNotEmpty() && !it.trim().startsWith("^") }
                .forEach { line ->
                    elements += ZplElement(line.trim(), 8f, TextAlignment.LEFT)
                }
        }
        
        return elements
    }

    private fun extractFDContent(line: String): String {
        val startIndex = line.indexOf("^FD")
        val endIndex = line.indexOf("^FS")
        
        if (startIndex == -1 || endIndex == -1 || endIndex <= startIndex) {
            return ""
        }
        
        return line.substring(startIndex + 3, endIndex).trim()
    }

    private fun extractFontSize(line: String): Float {
        // ^A0,N,20,20 - o terceiro parâmetro é a altura da fonte
        val parts = line.substringAfter("^A").split(",", ";", " ")
        return parts.getOrNull(2)?.toFloatOrNull()?.coerceIn(5f, 50f) ?: 10f
    }
}
