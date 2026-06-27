package com.exemplo.declaracao.util

import com.exemplo.declaracao.model.Declaracao
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PdfDeclaracaoGenerator {
    fun gerar(d: Declaracao, out: File) {
        val pdf = PdfDocument(PdfWriter(out))
        pdf.defaultPageSize = PageSize.A4
        val doc = Document(pdf)
        doc.setMargins(20f, 20f, 20f, 20f)
        
        // Título
        doc.add(Paragraph("DACE - DECLARAÇÃO AUXILIAR DE CONTEÚDO ELETRÔNICA")
            .setBold().setFontSize(14f).setTextAlignment(TextAlignment.CENTER))
        doc.add(Paragraph("Folha 1/1").setTextAlignment(TextAlignment.RIGHT).setFontSize(9f))
        doc.add(Paragraph("DATA: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())).setFontSize(10f))
        doc.add(Paragraph(" ").setFontSize(8f))
        
        // REMETENTE
        doc.add(Paragraph("REMETENTE").setBold().setFontSize(11f))
        val t1 = Table(UnitValue.createPercentArray(floatArrayOf(25f, 75f))).useAllAvailableWidth()
        t1.addCell(Cell().add(Paragraph("Nome:").setBold()))
        t1.addCell(Cell().add(Paragraph(d.remNome)))
        t1.addCell(Cell().add(Paragraph("CPF/CNPJ:").setBold()))
        t1.addCell(Cell().add(Paragraph(d.remCpf)))
        t1.addCell(Cell().add(Paragraph("Telefone:").setBold()))
        t1.addCell(Cell().add(Paragraph(d.remTel)))
        t1.addCell(Cell().add(Paragraph("E-mail:").setBold()))
        t1.addCell(Cell().add(Paragraph(d.remEmail)))
        t1.addCell(Cell().add(Paragraph("Endereço:").setBold()))
        val remEndereco = "${d.remEnd}, ${d.remNumero} ${d.remComplemento}".trim().replace("  ", " ")
        t1.addCell(Cell().add(Paragraph(remEndereco)))
        t1.addCell(Cell().add(Paragraph("Cidade/UF:").setBold()))
        t1.addCell(Cell().add(Paragraph(d.remCidade)))
        doc.add(t1)
        
        doc.add(Paragraph(" ").setFontSize(8f))
        
        // DESTINATÁRIO
        doc.add(Paragraph("DESTINATÁRIO").setBold().setFontSize(11f))
        val t2 = Table(UnitValue.createPercentArray(floatArrayOf(25f, 75f))).useAllAvailableWidth()
        t2.addCell(Cell().add(Paragraph("Nome:").setBold()))
        t2.addCell(Cell().add(Paragraph(d.desNome)))
        t2.addCell(Cell().add(Paragraph("CPF/CNPJ:").setBold()))
        t2.addCell(Cell().add(Paragraph(d.desCpf)))
        t2.addCell(Cell().add(Paragraph("Endereço:").setBold()))
        val desEndereco = "${d.desEnd}, ${d.desNumero} ${d.desComplemento}".trim().replace("  ", " ")
        t2.addCell(Cell().add(Paragraph(desEndereco)))
        t2.addCell(Cell().add(Paragraph("Cidade/UF:").setBold()))
        t2.addCell(Cell().add(Paragraph(d.desCidade)))
        doc.add(t2)
        
        doc.add(Paragraph(" ").setFontSize(8f))
        
        // CONTEÚDO
        doc.add(Paragraph("IDENTIFICAÇÃO DOS BENS").setBold().setFontSize(11f))
        val t3 = Table(UnitValue.createPercentArray(floatArrayOf(5f, 50f, 10f, 15f, 15f))).useAllAvailableWidth()
        t3.addHeaderCell(Cell().add(Paragraph("ITEM").setBold()))
        t3.addHeaderCell(Cell().add(Paragraph("DESCRIÇÃO").setBold()))
        t3.addHeaderCell(Cell().add(Paragraph("QTD").setBold()))
        t3.addHeaderCell(Cell().add(Paragraph("VALOR").setBold()))
        t3.addHeaderCell(Cell().add(Paragraph("PESO").setBold()))
        
        t3.addCell(Cell().add(Paragraph("1")))
        t3.addCell(Cell().add(Paragraph(d.descricao)))
        t3.addCell(Cell().add(Paragraph(d.qtd)))
        t3.addCell(Cell().add(Paragraph("R$ ${d.valor}")))
        t3.addCell(Cell().add(Paragraph("${d.peso} kg")))
        
        doc.add(t3)
        
        doc.add(Paragraph(" ").setFontSize(8f))
        doc.add(Paragraph("Valor Total: R$ ${d.valor}").setBold())
        doc.add(Paragraph("Peso Total: ${d.peso} kg").setBold())
        
        doc.close()
    }
}
