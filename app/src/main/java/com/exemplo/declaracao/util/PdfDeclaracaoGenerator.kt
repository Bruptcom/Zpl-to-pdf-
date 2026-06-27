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
        doc.add(Paragraph("DACE - DECLARAÇÃO AUXILIAR DE CONTEÚDO ELETRÔNICA").setBold().setFontSize(14f).setTextAlignment(TextAlignment.CENTER))
        doc.add(Paragraph("Folha 1/1").setTextAlignment(TextAlignment.RIGHT).setFontSize(9f))
        doc.add(Paragraph("DATA: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())).setFontSize(10f))
        doc.add(Paragraph(" ").setFontSize(6f))
        doc.add(Paragraph("REMETENTE").setBold())
        val t1 = Table(UnitValue.createPercentArray(floatArrayOf(20f, 80f))).useAllAvailableWidth()
        t1.addCell(Cell().add(Paragraph("Nome:")).setBold()); t1.addCell(Cell().add(Paragraph(d.remNome)))
        t1.addCell(Cell().add(Paragraph("CPF/CNPJ:")).setBold()); t1.addCell(Cell().add(Paragraph(d.remCpf)))
        t1.addCell(Cell().add(Paragraph("Endereço:")).setBold()); t1.addCell(Cell().add(Paragraph(d.remEnd)))
        t1.addCell(Cell().add(Paragraph("Cidade/UF:")).setBold()); t1.addCell(Cell().add(Paragraph(d.remCidade)))
        doc.add(t1)
        doc.add(Paragraph(" ").setFontSize(6f))
        doc.add(Paragraph("DESTINATÁRIO").setBold())
        val t2 = Table(UnitValue.createPercentArray(floatArrayOf(20f, 80f))).useAllAvailableWidth()
        t2.addCell(Cell().add(Paragraph("Nome:")).setBold()); t2.addCell(Cell().add(Paragraph(d.desNome)))
        t2.addCell(Cell().add(Paragraph("CPF/CNPJ:")).setBold()); t2.addCell(Cell().add(Paragraph(d.desCpf)))
        t2.addCell(Cell().add(Paragraph("Endereço:")).setBold()); t2.addCell(Cell().add(Paragraph(d.desEnd)))
        t2.addCell(Cell().add(Paragraph("Cidade/UF:")).setBold()); t2.addCell(Cell().add(Paragraph(d.desCidade)))
        doc.add(t2)
        doc.add(Paragraph(" ").setFontSize(6f))
        doc.add(Paragraph("IDENTIFICAÇÃO DOS BENS").setBold())
        val t3 = Table(UnitValue.createPercentArray(floatArrayOf(10f, 50f, 10f, 15f, 15f))).useAllAvailableWidth()
        t3.addHeaderCell(Cell().add(Paragraph("ITEM").setBold()))
        t3.addHeaderCell(Cell().add(Paragraph("DESCRIÇÃO").setBold()))
        t3.addHeaderCell(Cell().add(Paragraph("QTD").setBold()))
        t3.addHeaderCell(Cell().add(Paragraph("VALOR").setBold()))
        t3.addHeaderCell(Cell().add(Paragraph("PESO").setBold()))
        t3.addCell(Cell().add(Paragraph("1")))
        t3.addCell(Cell().add(Paragraph(d.descricao)))
        t3.addCell(Cell().add(Paragraph(d.qtd)))
        t3.addCell(Cell().add(Paragraph(d.valor)))
        t3.addCell(Cell().add(Paragraph(d.peso)))
        doc.add(t3)
        doc.close()
    }
}
