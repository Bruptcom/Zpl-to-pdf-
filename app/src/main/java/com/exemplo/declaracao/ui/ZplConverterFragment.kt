package com.exemplo.declaracao.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.exemplo.declaracao.databinding.FragmentZplBinding
import com.exemplo.declaracao.util.ZplToPdfConverter
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

class ZplConverterFragment : Fragment() {
    private var _b: FragmentZplBinding? = null
    private val b get() = _b!!
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val generatedPdfs = mutableListOf<File>()

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.data?.let { handleUri(it) }
        }
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentZplBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        b.btnPick.setOnClickListener { 
            if (checkStoragePermission()) {
                selectFile()
            } else {
                requestStoragePermission()
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${requireContext().packageName}")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE),
                100
            )
        }
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream", "text/plain", "*/*"))
        }
        try {
            picker.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erro ao abrir seletor: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleUri(uri: Uri) {
        b.progress.visibility = View.VISIBLE
        b.tvStatus.text = "🔄 Processando..."
        generatedPdfs.clear()
        clearPdfButtons()

        scope.launch {
            try {
                val ctx = requireContext()
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val zplDir = File(downloadDir, "ZPL_PDFs").apply { 
                    if (!exists()) mkdirs()
                }
                
                val tmpDir = File(ctx.cacheDir, "zpl_tmp").apply { 
                    if (exists()) deleteRecursively()
                    mkdirs() 
                }

                // Obter nome do arquivo
                val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex("_display_name")
                        if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    } else null
                } ?: "arquivo"

                val files = mutableListOf<File>()

                // Verificar se é ZIP
                val isZip = name.endsWith(".zip", ignoreCase = true)

                if (isZip) {
                    // Processar ZIP
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        try {
                            ZipInputStream(input).use { zis ->
                                var entry = zis.nextEntry
                                var count = 0
                                while (entry != null) {
                                    if (!entry.isDirectory && entry.name.endsWith(".zpl", ignoreCase = true)) {
                                        val outFile = File(tmpDir, "${entry.name}_${System.currentTimeMillis()}.zpl")
                                        FileOutputStream(outFile).use { output ->
                                            zis.copyTo(output)
                                        }
                                        files += outFile
                                        count++
                                    }
                                    entry = zis.nextEntry
                                }
                                if (count == 0) {
                                    throw Exception("Nenhum arquivo .zpl encontrado no ZIP")
                                }
                            }
                        } catch (e: Exception) {
                            throw Exception("Erro ao extrair ZIP: ${e.message}")
                        }
                    }
                } else {
                    // Processar ZPL individual
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
                    try {
                        if (zplFile.length() == 0L) {
                            throw Exception("Arquivo ZPL vazio: ${zplFile.name}")
                        }
                        
                        val pdfName = if (files.size > 1) {
                            "ZPL_${timestamp}_${idx + 1}.pdf"
                        } else {
                            "ZPL_${timestamp}.pdf"
                        }
                        
                        val pdfFile = File(zplDir, pdfName)
                        converter.convert(zplFile, pdfFile)
                        
                        if (pdfFile.exists() && pdfFile.length() > 0) {
                            generatedPdfs += pdfFile
                        } else {
                            throw Exception("PDF não foi criado corretamente")
                        }
                    } catch (e: Exception) {
                        throw Exception("Erro ao converter ${zplFile.name}: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    b.progress.visibility = View.GONE
                    if (generatedPdfs.isNotEmpty()) {
                        b.tvStatus.text = "✅ ${generatedPdfs.size} PDF(s) gerado(s)!\n📁 Download/ZPL_PDFs/"
                        createPdfButtons()
                        Toast.makeText(ctx, "PDFs salvos em: Download/ZPL_PDFs", Toast.LENGTH_LONG).show()
                    } else {
                        b.tvStatus.text = "❌ Nenhum PDF gerado"
                        Toast.makeText(ctx, "Nenhum PDF foi gerado", Toast.LENGTH_LONG).show()
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
        try {
            b.pdfButtonsContainer.removeAllViews()
        } catch (e: Exception) {
            // Ignorar erro
        }
    }

    private fun createPdfButtons() {
        val container = try {
            b.pdfButtonsContainer
        } catch (e: Exception) {
            return
        }
        
        container.removeAllViews()
        
        generatedPdfs.forEach { pdf ->
            if (!pdf.exists()) return@forEach
            
            try {
                val cardView = com.google.android.material.card.MaterialCardView(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setCardBackgroundColor(android.graphics.Color.WHITE)
                    radius = 12f
                    cardElevation = 2f
                    setContentPadding(16, 16, 16, 16)
                    setOnClickListener { openPdf(pdf) }
                }

                val layout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val icon = android.widget.TextView(requireContext()).apply {
                    text = "📄"
                    textSize = 24f
                }

                val info = android.widget.TextView(requireContext()).apply {
                    text = pdf.name
                    textSize = 14f
                    setTextColor(android.graphics.Color.parseColor("#334155"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 12
                        marginEnd = 8
                    }
                }

                layout.addView(icon)
                layout.addView(info)
                cardView.addView(layout)
                container.addView(cardView)

                val actionsLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPadding(0, 8, 0, 0)
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

                actionsLayout.addView(shareBtn)
                actionsLayout.addView(openBtn)
                container.addView(actionsLayout)

                val separator = View(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(android.graphics.Color.parseColor("#E2E8F0"))
                }
                container.addView(separator)
            } catch (e: Exception) {
                // Ignorar erro ao criar botão
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
            Toast.makeText(requireContext(), "Erro ao abrir PDF: ${e.message}", Toast.LENGTH_SHORT).show()
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
