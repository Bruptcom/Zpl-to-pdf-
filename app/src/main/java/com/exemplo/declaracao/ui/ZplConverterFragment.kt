package com.exemplo.declaracao.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.exemplo.declaracao.databinding.FragmentZplBinding
import com.exemplo.declaracao.util.ZplToPdfConverter
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

class ZplConverterFragment : Fragment() {
    private var _b: FragmentZplBinding? = null
    private val b get() = _b!!
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val generatedPdfs = mutableListOf<File>()
    private var currentPdfToSave: File? = null

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.data?.let { handleUri(it) }
        }
    }

    private val createPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { savePdfToUri(it) }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentZplBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        b.btnPick.setOnClickListener { selectFile() }
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        picker.launch(intent)
    }

    private fun handleUri(uri: Uri) {
        b.progress.visibility = View.VISIBLE
        b.tvStatus.text = "🔄 Processando..."
        generatedPdfs.clear()
        clearPdfButtons()

        scope.launch {
            try {
                val ctx = requireContext()
                val tmpDir = File(ctx.cacheDir, "zpl_tmp").apply { 
                    if (exists()) deleteRecursively()
                    mkdirs() 
                }

                val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex("_display_name")
                        if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    } else null
                } ?: "arquivo"

                val files = mutableListOf<File>()
                val isZip = name.endsWith(".zip", ignoreCase = true)

                if (isZip) {
                    // Processar ZIP - aceitar .zpl, .txt ou qualquer arquivo
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zis ->
                            var entry = zis.nextEntry
                            var count = 0
                            val foundFiles = mutableListOf<String>()
                            
                            while (entry != null) {
                                if (!entry.isDirectory) {
                                    // Aceitar arquivos .zpl, .txt, ou que pareçam ZPL
                                    val isZplFile = entry.name.endsWith(".zpl", ignoreCase = true) ||
                                                    entry.name.endsWith(".txt", ignoreCase = true) ||
                                                    entry.name.contains("zpl", ignoreCase = true)
                                    
                                    if (isZplFile) {
                                        val outFile = File(tmpDir, entry.name)
                                        FileOutputStream(outFile).use { output ->
                                            zis.copyTo(output)
                                        }
                                        files += outFile
                                        foundFiles += entry.name
                                        count++
                                    }
                                }
                                entry = zis.nextEntry
                            }
                            
                            if (count == 0) {
                                // Se não achou .zpl ou .txt, tentar todos os arquivos
                                ctx.contentResolver.openInputStream(uri)?.use { input2 ->
                                    ZipInputStream(input2).use { zis2 ->
                                        var entry2 = zis2.nextEntry
                                        while (entry2 != null) {
                                            if (!entry2.isDirectory && entry2.name.isNotBlank()) {
                                                val outFile = File(tmpDir, entry2.name)
                                                FileOutputStream(outFile).use { output ->
                                                    zis2.copyTo(output)
                                                }
                                                files += outFile
                                                foundFiles += entry2.name
                                                count++
                                            }
                                            entry2 = zis2.nextEntry
                                        }
                                    }
                                }
                            }
                            
                            if (count == 0) {
                                throw Exception("Nenhum arquivo encontrado no ZIP. Arquivos: ${foundFiles.joinToString()}")
                            }
                        }
                    }
                } else {
                    // Processar arquivo individual
                    val outFile = File(tmpDir, name)
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    files += outFile
                }

                if (files.isEmpty()) {
                    throw Exception("Nenhum arquivo para processar")
                }

                // Converter para PDF
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val converter = ZplToPdfConverter()
                
                files.forEachIndexed { idx, zplFile ->
                    if (zplFile.length() == 0L) {
                        throw Exception("Arquivo vazio: ${zplFile.name}")
                    }
                    
                    val pdfName = if (files.size > 1) {
                        "ZPL_${timestamp}_${idx + 1}.pdf"
                    } else {
                        "ZPL_${timestamp}.pdf"
                    }
                    
                    val pdfFile = File(ctx.cacheDir, pdfName)
                    converter.convert(zplFile, pdfFile)
                    
                    if (pdfFile.exists() && pdfFile.length() > 0) {
                        generatedPdfs += pdfFile
                    } else {
                        throw Exception("PDF não criado: ${zplFile.name}")
                    }
                }

                withContext(Dispatchers.Main) {
                    b.progress.visibility = View.GONE
                    if (generatedPdfs.isNotEmpty()) {
                        b.tvStatus.text = "✅ ${generatedPdfs.size} PDF(s) gerado(s)!\nToque para salvar/compartilhar"
                        createPdfButtons()
                        Toast.makeText(ctx, "PDFs gerados com sucesso!", Toast.LENGTH_LONG).show()
                    } else {
                        b.tvStatus.text = "❌ Nenhum PDF gerado"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    b.progress.visibility = View.GONE
                    b.tvStatus.text = "❌ Erro: ${e.message}"
                    Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearPdfButtons() {
        b.pdfButtonsContainer.removeAllViews()
    }

    private fun createPdfButtons() {
        val container = b.pdfButtonsContainer
        container.removeAllViews()
        
        generatedPdfs.forEach { pdf ->
            if (!pdf.exists()) return@forEach
            
            val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setCardBackgroundColor(android.graphics.Color.WHITE)
                radius = 12f
                cardElevation = 2f
                setContentPadding(16, 16, 16, 16)
            }

            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }

            val info = android.widget.TextView(requireContext()).apply {
                text = "📄 ${pdf.name}\nTamanho: ${(pdf.length() / 1024.0).toInt()} KB"
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#334155"))
            }

            val actionsLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, 12, 0, 0)
            }

            val saveBtn = com.google.android.material.button.MaterialButton(requireContext()).apply {
                text = "💾 Salvar"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 4
                }
                setOnClickListener { 
                    currentPdfToSave = pdf
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    createPdfLauncher.launch("ZPL_${timestamp}.pdf")
                }
            }

            val shareBtn = com.google.android.material.button.MaterialButton(requireContext()).apply {
                text = "📤 Compartilhar"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 4
                }
                setOnClickListener { sharePdf(pdf) }
            }

            val openBtn = com.google.android.material.button.MaterialButton(requireContext()).apply {
                text = "👁️ Abrir"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { openPdf(pdf) }
            }

            actionsLayout.addView(saveBtn)
            actionsLayout.addView(shareBtn)
            actionsLayout.addView(openBtn)
            
            layout.addView(info)
            layout.addView(actionsLayout)
            cardView.addView(layout)
            container.addView(cardView)
        }
    }

    private fun savePdfToUri(uri: Uri) {
        scope.launch {
            try {
                val pdf = currentPdfToSave ?: return@launch
                val ctx = requireContext()
                
                ctx.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    pdf.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(ctx, "PDF salvo com sucesso!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Erro ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openPdf(pdf: File) {
        try {
            if (!pdf.exists()) {
                Toast.makeText(requireContext(), "Arquivo não encontrado", Toast.LENGTH_SHORT).show()
                return
            }
            
            val ctx = requireContext()
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", pdf)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Abrir PDF"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erro ao abrir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePdf(pdf: File) {
        try {
            if (!pdf.exists()) {
                Toast.makeText(requireContext(), "Arquivo não encontrado", Toast.LENGTH_SHORT).show()
                return
            }
            
            val ctx = requireContext()
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", pdf)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, pdf.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar PDF"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erro ao compartilhar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
