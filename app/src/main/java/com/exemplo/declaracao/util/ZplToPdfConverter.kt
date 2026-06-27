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

        // Extrair informações estruturadas do ZPL
        val labelData = parseZPLLabel(zplContent)
        
        // Renderizar etiqueta formatada
        renderFormattedLabel(doc, labelData)
        
        doc.close()
    }

    data class LabelData(
        val nf: String = "",
        val shp: String = "",
        val contrato: String = "",
        val peso: String = "",
        val codigoRastreio: String = "",
        val destinatarioNome: String = "",
        val destinatarioEndereco: String = "",
        val destinatarioCep: String = "",
        val remetenteNome: String = "",
        val remetenteEndereco: String = "",
        val remetenteCep: String = "",
        val otherText: MutableList<String> = mutableListOf()
    )

    private fun parseZPLLabel(zpl: String): LabelData {
        val data = LabelData()
        val cleanText = zpl
            .replace(Regex("_C[0-9]_[0-9]"), "") // Remover encoding
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), " ")
            .replace(Regex("\\^FS|\\^FD|\\^XF"), " ")
            .replace(Regex("[\\x00-\\x1F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Extrair campos específicos
        data.nf = extractField(cleanText, "NF[:\\s]+([0-9]+)")
        data.shp = extractField(cleanText, "SHP[:\\s]+([0-9]+)")
        data.contrato = extractField(cleanText, "Contrato[:\\s]+([0-9]+)")
        data.peso = extractField(cleanText, "PESO[:\\s]+([0-9]+g?)")
        data.codigoRastreio = extractField(cleanText, "(AP[0-9]+BR)")
        
        // Extrair destinatário
        val destIndex = cleanText.indexOf("DESTINATARIO", ignoreCase = true)
        if (destIndex != -1) {
            val afterDest = cleanText.substring(destIndex + 12)
            data.destinatarioNome = extractLine(afterDest)
            data.destinatarioEndereco = extractLine(afterDest.substringAfter(data.destinatarioNome))
            data.destinatarioCep = extractField(afterDest, "([0-9]{5}-?[0-9]{3})")
        }
        
        // Extrair remetente
        val remIndex = cleanText.indexOf("Remetente:", ignoreCase = true)
        if (remIndex != -1) {
            val afterRem = cleanText.substring(remIndex + 10)
            data.remetenteNome = extractLine(afterRem)
            data.remetenteEndereco = extractLine(afterRem.substringAfter(data.remetenteNome))
            data.remetenteCep = extractField(afterRem, "([0-9]{5}-?[0-9]{3})")
        }

        return data
    }

    private fun extractField(text: String, pattern: String): String {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun extractLine(text: String): String {
        return text.takeWhile { it !in listOf(',', '\n', '\r') }.trim().take(80)
    }

    private fun renderFormattedLabel(doc: Document, data: LabelData) {
        var currentY = PAGE.height - 8f
        val maxWidth = PAGE.width - 10f
        
        // Título/Logo (Mercado Livre ou genérico)
        if (data.nf.isNotEmpty()) {
            addText(doc, "Mercado Livre", 10f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= 12f
        }
        
        // Informações principais
        addText(doc, "NF: ${data.nf}", 8f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= 10f
        
        addText(doc, "SHP: ${data.shp}", 8f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= 10f
        
        addText(doc, "Contrato: ${data.contrato}", 8f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= 10f
        
        addText(doc, "PESO: ${data.peso}", 8f, currentY, maxWidth, TextAlignment.RIGHT)
        currentY -= 10f
        
        if (data.codigoRastreio.isNotEmpty()) {
            addText(doc, data.codigoRastreio, 9f, currentY, maxWidth, TextAlignment.CENTER)
            currentY -= 12f
        }
        
        // Espaço para código de barras
        currentY -= 15f
        
        // Recebedor
        addText(doc, "Recebedor: _________________________", 8f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= 10f
        
        addText(doc, "Assinatura: ______________________  Documento: ____________________", 7f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= 12f
        
        // Destinatário
        addText(doc, "DESTINATÁRIO", 9f, currentY, maxWidth, TextAlignment.LEFT, true)
        currentY -= 12f
        
        if (data.destinatarioNome.isNotEmpty()) {
            addText(doc, data.destinatarioNome, 8f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= 10f
        }
        
        if (data.destinatarioEndereco.isNotEmpty()) {
            addText(doc, data.destinatarioEndereco, 7f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= 9f
        }
        
        if (data.destinatarioCep.isNotEmpty()) {
            addText(doc, data.destinatarioCep, 8f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= 12f
        }
        
        // Espaço para código de barras do destinatário
        currentY -= 15f
        
        // Remetente
        addText(doc, "Remetente:", 9f, currentY, maxWidth, TextAlignment.LEFT, true)
        currentY -= 12f
        
        if (data.remetenteNome.isNotEmpty()) {
            addText(doc, data.remetenteNome, 8f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= 10f
        }
        
        if (data.remetenteEndereco.isNotEmpty()) {
            addText(doc, data.remetenteEndereco, 7f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= 9f
        }
        
        if (data.remetenteCep.isNotEmpty()) {
            addText(doc, data.remetenteCep, 8f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= 12f
        }
        
        // Espaço para código de barras do remetente
        currentY -= 15f
        
        // Número do pedido
        if (data.shp.isNotEmpty()) {
            addText(doc, "*${data.shp}*", 9f, currentY, maxWidth, TextAlignment.CENTER)
        }
    }

    private fun addText(
        doc: Document,
        text: String,
        fontSize: Float,
        y: Float,
        width: Float,
        alignment: TextAlignment,
        bold: Boolean = false
    ) {
        if (y < 10f) return // Não renderizar se sair da página
        
        val paragraph = Paragraph(text)
            .setFontSize(fontSize)
            .setTextAlignment(alignment)
            .setFixedPosition(5f, y, width)
            .setMarginBottom(0f)
            .setMarginTop(0f)
        
        if (bold) {
            paragraph.setBold()
        }
        
        try {
            doc.add(paragraph)
        } catch (e: Exception) {
            // Ignorar erro de posicionamento
        }
    }
}
