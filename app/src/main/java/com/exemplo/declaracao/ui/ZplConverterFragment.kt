package com.exemplo.declaracao.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipInputStream

class ZplConverterFragment : Fragment() {
    private var _b: FragmentZplBinding? = null
    private val b get() = _b!!
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val generatedPdfs = mutableListOf<File>()

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) res.data?.data?.let { handleUri(it) }
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
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val zplDir = File(downloadDir, "ZPL_PDFs").apply { mkdirs() }
                
                val tmpDir = File(ctx.cacheDir, "zpl_tmp").apply { mkdirs(); deleteRecursively(); mkdirs() }

                val name = ctx.contentResolver.query(uri, null, null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow("_display_name")) else "arquivo"
                }

                val files = mutableListOf<File>()

                if (name.endsWith(".zip", true)) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zis ->
                            var e = zis.nextEntry
                            while (e != null) {
                                if (!e.isDirectory && e.name.endsWith(".zpl", true)) {
                                    val out = File(tmpDir, e.name)
                                    out.outputStream().use { zis.copyTo(it) }
                                    files += out
                                }
                                e = zis.nextEntry
                            }
                        }
                    }
                } else {
                    val f = File(tmpDir, name)
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { input.copyTo(it) }
                    }
                    files += f
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val converter = ZplToPdfConverter()
                
                files.forEachIndexed { idx, zplFile ->
                    val pdfName = if (files.size > 1) {
                        "ZPL_${timestamp}_${idx + 1}.pdf"
                    } else {
                        "ZPL_${timestamp}.pdf"
                    }
                    val pdf = File(zplDir, pdfName)
                    converter.convert(zplFile, pdf)
                    generatedPdfs += pdf
                }

                withContext(Dispatchers.Main) {
                    b.progress.visibility = View.GONE
                    b.tvStatus.text = "✅ ${generatedPdfs.size} PDF(s) gerado(s)!\n📁 Download/ZPL_PDFs/"
                    createPdfButtons()
                    Toast.makeText(ctx, "PDFs salvos em: Download/ZPL_PDFs", Toast.LENGTH_LONG).show()
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
            // Card do PDF
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

            // Botões de ação
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
        }
    }

    private fun openPdf(pdf: File) {
        val ctx = requireContext()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", pdf)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Abrir PDF"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Nenhum app para abrir PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePdf(pdf: File) {
        val ctx = requireContext()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", pdf)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, pdf.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Compartilhar PDF"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Erro ao compartilhar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
