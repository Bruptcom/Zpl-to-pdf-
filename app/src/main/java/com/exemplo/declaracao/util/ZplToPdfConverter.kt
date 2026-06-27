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
        
        val writer = PdfWriter(pdfFile)
        val pdf = PdfDocument(writer)
        pdf.defaultPageSize = PAGE
        val doc = Document(pdf)
        doc.setMargins(5f, 5f, 5f, 5f)

        val labelData = parseZPLLabel(zplContent)
        renderFormattedLabel(doc, labelData)
        
        doc.close()
    }

    data class LabelData(
        var nf: String = "",
        var shp: String = "",
        var contrato: String = "",
        var peso: String = "",
        var codigoRastreio: String = "",
        var destinatarioNome: String = "",
        var destinatarioEndereco: String = "",
        var destinatarioCep: String = "",
        var remetenteNome: String = "",
        var remetenteEndereco: String = "",
        var remetenteCep: String = "",
        var daceText: String = "",
        var otherText: MutableList<String> = mutableListOf()
    )

    private fun parseZPLLabel(zpl: String): LabelData {
        val data = LabelData()
        
        // Decodificar texto ZPL (remover encoding estranho)
        val cleanText = decodeZPLText(zpl)
        
        // Extrair campos usando regex
        data.nf = extractPattern(cleanText, "NF[:\\s]*([0-9]+)")
        data.shp = extractPattern(cleanText, "SHP[:\\s]*([0-9]+)")
        data.contrato = extractPattern(cleanText, "Contrato[:\\s]*([0-9]+)")
        data.peso = extractPattern(cleanText, "PESO[:\\s]*([0-9]+g?)")
        data.codigoRastreio = extractPattern(cleanText, "(AP[0-9]+BR)")
        
        // Extrair destinatário
        val destMatch = Regex("DESTINATARIO\\s*\\n?\\s*([^\n]+)\\s*\\n?\\s*([^\n]+)\\s*\\n?\\s*([0-9]{5}-?[0-9]{3})", RegexOption.IGNORE_CASE).find(cleanText)
        if (destMatch != null) {
            data.destinatarioNome = destMatch.groupValues[1].trim()
            data.destinatarioEndereco = destMatch.groupValues[2].trim()
            data.destinatarioCep = destMatch.groupValues[3].trim()
        }
        
        // Extrair remetente
        val remMatch = Regex("Remetente:\\s*\\n?\\s*([^\n]+)\\s*\\n?\\s*([^\n]+)\\s*\\n?\\s*([0-9]{5}-?[0-9]{3})", RegexOption.IGNORE_CASE).find(cleanText)
        if (remMatch != null) {
            data.remetenteNome = remMatch.groupValues[1].trim()
            data.remetenteEndereco = remMatch.groupValues[2].trim()
            data.remetenteCep = remMatch.groupValues[3].trim()
        }
        
        // Extrair DACE
        val daceMatch = Regex("DACE RESUMIDA.*?(?=Chave de Acesso|$)", RegexOption.DOT_MATCHES_ALL).find(cleanText)
        if (daceMatch != null) {
            data.daceText = daceMatch.value.trim()
        }
        
        return data
    }

    private fun decodeZPLText(zpl: String): String {
        return zpl
            .replace("_C3_A9", "é")
            .replace("_C3_A3", "ã")
            .replace("_C3_A7", "ç")
            .replace("_C3_AD", "í")
            .replace("_C3_B3", "ó")
            .replace("_C3_BA", "ú")
            .replace("_C2_B0", "º")
            .replace("_C3_87", "Ç")
            .replace("_C3_89", "É")
            .replace("_C3_83", "Ã")
            .replace("_C3_95", "Õ")
            .replace("_C3_81", "Á")
            .replace("_C3_8D", "Í")
            .replace("_C3_93", "Ó")
            .replace("_C3_9A", "Ú")
            .replace(Regex("_C[0-9]_[0-9]"), "")
            .replace(Regex("\\^[A-Z]{1,3}[0-9,]*"), " ")
            .replace(Regex("\\^FS|\\^FD|\\^XF"), " ")
            .replace(Regex("[\\x00-\\x1F]"), "\n")
            .replace(Regex("\\|"), "\n")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractPattern(text: String, pattern: String): String {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun renderFormattedLabel(doc: Document, data: LabelData) {
        var currentY = PAGE.height - 8f
        val maxWidth = PAGE.width - 10f
        val lineHeight = 9f
        
        // Cabeçalho
        if (data.nf.isNotEmpty()) {
            addText(doc, "Mercado Livre", 9f, currentY, maxWidth, TextAlignment.LEFT, true)
            currentY -= lineHeight
        }
        
        addText(doc, "NF: ${data.nf}  SHP: ${data.shp}", 7f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= lineHeight
        
        addText(doc, "Contrato: ${data.contrato}  PESO: ${data.peso}", 7f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= lineHeight
        
        if (data.codigoRastreio.isNotEmpty()) {
            addText(doc, data.codigoRastreio, 9f, currentY, maxWidth, TextAlignment.CENTER, true)
            currentY -= lineHeight
        }
        
        currentY -= 5f
        
        // Recebedor
        addText(doc, "Recebedor: ___________________________", 7f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= lineHeight
        
        addText(doc, "Assinatura: ________________  Documento: ________________", 6f, currentY, maxWidth, TextAlignment.LEFT)
        currentY -= lineHeight * 1.5f
        
        // Destinatário
        addText(doc, "DESTINATÁRIO", 8f, currentY, maxWidth, TextAlignment.LEFT, true)
        currentY -= lineHeight
        
        if (data.destinatarioNome.isNotEmpty()) {
            addText(doc, data.destinatarioNome, 7f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= lineHeight
        }
        
        if (data.destinatarioEndereco.isNotEmpty()) {
            addText(doc, data.destinatarioEndereco, 6f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= lineHeight
        }
        
        if (data.destinatarioCep.isNotEmpty()) {
            addText(doc, data.destinatarioCep, 7f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= lineHeight
        }
        
        currentY -= 5f
        
        // Remetente
        addText(doc, "Remetente:", 8f, currentY, maxWidth, TextAlignment.LEFT, true)
        currentY -= lineHeight
        
        if (data.remetenteNome.isNotEmpty()) {
            addText(doc, data.remetenteNome, 7f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= lineHeight
        }
        
        if (data.remetenteEndereco.isNotEmpty()) {
            addText(doc, data.remetenteEndereco, 6f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= lineHeight
        }
        
        if (data.remetenteCep.isNotEmpty()) {
            addText(doc, data.remetenteCep, 7f, currentY, maxWidth, TextAlignment.LEFT)
            currentY -= lineHeight
        }
        
        currentY -= 5f
        
        // DACE
        if (data.daceText.isNotEmpty()) {
            addText(doc, "DACE RESUMIDA", 7f, currentY, maxWidth, TextAlignment.LEFT, true)
            currentY -= lineHeight
            
            val daceLines = data.daceText.chunked(60)
            daceLines.take(8).forEach { line ->
                addText(doc, line, 5f, currentY, maxWidth, TextAlignment.LEFT)
                currentY -= 7f
            }
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
        if (y < 10f || text.isBlank()) return
        
        try {
            val paragraph = Paragraph(text)
                .setFontSize(fontSize)
                .setTextAlignment(alignment)
                .setFixedPosition(5f, y, width)
                .setMarginBottom(0f)
                .setMarginTop(0f)
            
            if (bold) {
                paragraph.setBold()
            }
            
            doc.add(paragraph)
        } catch (e: Exception) {
            // Ignorar erro
        }
    }
}
