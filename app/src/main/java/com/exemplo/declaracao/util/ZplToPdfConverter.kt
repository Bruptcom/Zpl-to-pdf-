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

    fun convert(zplFile: File, pdfFile: File) {
        val text = zplFile.readText()
        
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(10f, 10f, 10f, 10f)

        // Extrair e formatar texto do ZPL
        val lines = extractTextFromZPL(text)
        
        // Adicionar cada linha ao PDF
        lines.forEach { line ->
            if (line.isNotBlank()) {
                val paragraph = Paragraph(line.trim())
                    .setFontSize(8f)
                    .setTextAlignment(TextAlignment.LEFT)
                    .setMarginBottom(2f)
                doc.add(paragraph)
            }
        }
        
        doc.close()
    }

    private fun extractTextFromZPL(zpl: String): List<String> {
        val lines = mutableListOf<String>()
        val zplText = zpl.replace(Regex("[\\x00-\\x1F]"), "") // Remover caracteres de controle
        
        // Dividir por comandos ZPL e extrair texto
        val commands = zplText.split("^", "~")
        
        commands.forEach { cmd ->
            when {
                // Comando ^FD (Field Data) - texto principal
                cmd.startsWith("FD") && cmd.contains("^FS") -> {
                    val text = cmd.substringAfter("FD").substringBefore("^FS").trim()
                    if (text.isNotEmpty()) {
                        lines += text
                    }
                }
                // Texto simples sem comandos
                cmd.length > 3 && !cmd.startsWith("A") && !cmd.startsWith("FO") && 
                !cmd.startsWith("BY") && !cmd.startsWith("LH") -> {
                    val cleanText = cmd.replace(Regex("\\^FS|\\^FD|\\^XF|\\^A.*,"), "")
                        .replace(Regex("_C[0-9]_[0-9]"), "") // Remover encoding estranho
                        .trim()
                    if (cleanText.length > 2) {
                        lines += cleanText
                    }
                }
            }
        }
        
        // Se não encontrou nada, tentar extrair texto linha por linha
        if (lines.isEmpty()) {
            zplText.split("\n", "\r").forEach { line ->
                val cleanLine = line.trim()
                    .replace(Regex("\\^[A-Z]{1,2}[0-9,]*"), "")
                    .replace(Regex("_C[0-9]_[0-9]"), " ")
                    .replace(Regex("\\^FS|\\^FD"), "")
                    .trim()
                if (cleanLine.length > 2 && !cleanLine.startsWith("^")) {
                    lines += cleanLine
                }
            }
        }
        
        return lines.ifEmpty { listOf(zplText.take(500)) }
    }
}
