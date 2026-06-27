package com.exemplo.declaracao.ui
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.exemplo.declaracao.databinding.FragmentZplBinding
import com.exemplo.declaracao.util.ZplToPdfConverter
import kotlinx.coroutines.*
import java.io.File
import java.util.zip.ZipInputStream
class ZplConverterFragment : Fragment() {
    private var _b: FragmentZplBinding? = null
    private val b get() = _b!!
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) res.data?.data?.let { handleUri(it) }
    }
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentZplBinding.inflate(i, c, false); return b.root }
    override fun onViewCreated(v: View, s: Bundle?) {
        b.btnPick.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
            picker.launch(intent)
        }
    }
    private fun handleUri(uri: Uri) {
        b.progress.visibility = View.VISIBLE; b.tvStatus.text = "Processando..."
        scope.launch {
            try {
                val ctx = requireContext()
                val outDir = File(ctx.getExternalFilesDir(null), "ZPL_PDF").apply { mkdirs() }
                val tmpDir = File(ctx.cacheDir, "zpl_tmp").apply { mkdirs(); deleteRecursively(); mkdirs() }
                val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow("_display_name")) else null } ?: "arquivo"
                val files = mutableListOf<File>()
                if (name.endsWith(".zip", true)) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        ZipInputStream(input).use { zis ->
                            var e = zis.nextEntry
                            while (e != null) { if (!e.isDirectory && e.name.endsWith(".zpl", true)) { val out = File(tmpDir, e.name); out.outputStream().use { zis.copyTo(it) }; files += out }; e = zis.nextEntry }
                        }
                    }
                } else { val f = File(tmpDir, name); ctx.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }; files += f }
                val converter = ZplToPdfConverter()
                val generated = mutableListOf<String>()
                files.forEach { zplFile -> val pdf = File(outDir, "${zplFile.nameWithoutExtension}.pdf"); converter.convert(zplFile, pdf); generated += pdf.absolutePath }
                withContext(Dispatchers.Main) { b.progress.visibility = View.GONE; b.tvStatus.text = "✅ ${generated.size} PDF(s) gerado(s):\n" + generated.joinToString("\n") { it.substringAfterLast("/") }; Toast.makeText(ctx, "Concluído", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) { withContext(Dispatchers.Main) { b.progress.visibility = View.GONE; b.tvStatus.text = "❌ Erro: ${e.message}" } }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
