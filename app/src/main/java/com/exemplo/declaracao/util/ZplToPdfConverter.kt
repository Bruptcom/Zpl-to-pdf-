package com.exemplo.declaracao.util

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.properties.TextAlignment
import java.io.File

class ZplToPdfConverter {
    // 10x15 cm - tamanho padrão de etiqueta
    private val PAGE = PageSize(10f * 28.35f, 15f * 28.35f)

    fun convert(zplFile: File, pdfFile: File) {
        val zplContent = zplFile.readText()
        
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(5f, 5f, 5f, 5f)

        // Verificar se é ZPL com gráfico GFA (etiqueta de postagem)
        if (zplContent.contains("^GFA") || zplContent.contains("GFA,")) {
            renderShippingLabel(doc, zplContent)
        } else {
            renderSimpleText(doc, zplContent)
        }
        
        doc.close()
    }

    private fun renderShippingLabel(doc: Document, zpl: String) {
        // Extrair texto legível do ZPL
        val lines = extractTextFromZPL(zpl)
        
        var currentY = PAGE.height - 10f
        val lineHeight = 9f
        val maxWidth = PAGE.width - 10f
        
        lines.forEach { line ->
            if (currentY < 10f) return@forEach // Não criar nova página
            
            val paragraph = Paragraph(line.trim())
                .setFontSize(7f)
                .setTextAlignment(TextAlignment.LEFT)
                .setFixedPosition(5f, currentY, maxWidth)
                .setMarginBottom(0f)
                .setMarginTop(0f)
            
            doc.add(paragraph)
            currentY -= lineHeight
        }
    }

    private fun extractTextFromZPL(zpl: String): List<String> {
        val lines = mutableListOf<String>()
        
        // Remover comandos ZPL e extrair texto legível
        val cleanText = zpl
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), " ") // Remover comandos ^XX
            .replace(Regex("\\^[A-Z]{1,3}[0-9]+,[0-9]+"), " ") // Remover comandos ^XX,N,N
            .replace(Regex("_C[0-9]_[0-9]"), " ") // Remover encoding _C3_87
            .replace(Regex("\\^FS|\\^FD|\\^XF|\\^A.*,"), " ")
            .replace(Regex("[\\x00-\\x1F]"), " ") // Remover caracteres de controle
            .replace(Regex("\\s+"), " ") // Múltiplos espaços
            .trim()
        
        // Dividir em linhas de ~80 caracteres
        cleanText.chunked(70).forEach { chunk ->
            lines += chunk.trim()
        }
        
        // Se não extraiu nada, usar texto original limpo
        if (lines.isEmpty()) {
            lines += cleanText.take(1000)
        }
        
        return lines
    }

    private fun renderSimpleText(doc: Document, text: String) {
        val lines = text.split("\n", "\r\n", "\r")
            .filter { it.isNotBlank() }
            .map { it.trim() }
        
        var currentY = PAGE.height - 10f
        val lineHeight = 10f
        val maxWidth = PAGE.width - 10f
        
        lines.forEach { line ->
            if (currentY < 10f) return@forEach
            
            val paragraph = Paragraph(line)
                .setFontSize(8f)
                .setTextAlignment(TextAlignment.LEFT)
                .setFixedPosition(5f, currentY, maxWidth)
                .setMarginBottom(0f)
                .setMarginTop(0f)
            
            doc.add(paragraph)
            currentY -= lineHeight
        }
    }
}
