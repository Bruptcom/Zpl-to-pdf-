package com.exemplo.declaracao.ui

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.exemplo.declaracao.databinding.FragmentDeclaracaoBinding
import com.exemplo.declaracao.model.Declaracao
import com.exemplo.declaracao.util.CepService
import com.exemplo.declaracao.util.PdfDeclaracaoGenerator
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DeclaracaoFragment : Fragment() {
    private var _b: FragmentDeclaracaoBinding? = null
    private val b get() = _b!!
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val cep = CepService()
    private lateinit var prefs: SharedPreferences
    private var lastGeneratedPdf: File? = null

    companion object {
        private const val PREFS_NAME = "declaracao_prefs"
        private const val KEY_REM_NOME = "rem_nome"
        private const val KEY_REM_CPF = "rem_cpf"
        private const val KEY_REM_TEL = "rem_tel"
        private const val KEY_REM_EMAIL = "rem_email"
        private const val KEY_REM_END = "rem_end"
        private const val KEY_REM_NUM = "rem_num"
        private const val KEY_REM_COMP = "rem_comp"
        private const val KEY_REM_CIDADE = "rem_cidade"
    }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDeclaracaoBinding.inflate(i, c, false)
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return b.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        loadSavedData()
        
        watchCep(b.etRemCep) { endereco ->
            b.etRemEnd.setText("${endereco.logradouro}, ${endereco.bairro}".trim(',', ' '))
            b.etRemCidade.setText("${endereco.localidade}/${endereco.uf}")
        }
        watchCep(b.etDesCep) { endereco ->
            b.etDesEnd.setText("${endereco.logradouro}, ${endereco.bairro}".trim(',', ' '))
            b.etDesCidade.setText("${endereco.localidade}/${endereco.uf}")
        }

        b.btnGerar.setOnClickListener { 
            saveData()
            gerarPdf() 
        }
    }

    private fun loadSavedData() {
        b.etRemNome.setText(prefs.getString(KEY_REM_NOME, ""))
        b.etRemCpf.setText(prefs.getString(KEY_REM_CPF, ""))
        b.etRemTel.setText(prefs.getString(KEY_REM_TEL, ""))
        b.etRemEmail.setText(prefs.getString(KEY_REM_EMAIL, ""))
        b.etRemEnd.setText(prefs.getString(KEY_REM_END, ""))
        b.etRemNumero.setText(prefs.getString(KEY_REM_NUM, ""))
        b.etRemComplemento.setText(prefs.getString(KEY_REM_COMP, ""))
        b.etRemCidade.setText(prefs.getString(KEY_REM_CIDADE, ""))
    }

    private fun saveData() {
        prefs.edit().apply {
            putString(KEY_REM_NOME, b.etRemNome.text.toString())
            putString(KEY_REM_CPF, b.etRemCpf.text.toString())
            putString(KEY_REM_TEL, b.etRemTel.text.toString())
            putString(KEY_REM_EMAIL, b.etRemEmail.text.toString())
            putString(KEY_REM_END, b.etRemEnd.text.toString())
            putString(KEY_REM_NUM, b.etRemNumero.text.toString())
            putString(KEY_REM_COMP, b.etRemComplemento.text.toString())
            putString(KEY_REM_CIDADE, b.etRemCidade.text.toString())
            apply()
        }
        Toast.makeText(requireContext(), "Dados do remetente salvos!", Toast.LENGTH_SHORT).show()
    }

    private fun watchCep(field: android.widget.EditText, onOk: (CepService.Endereco) -> Unit) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                val v = e?.toString()?.replace(Regex("\\D"), "") ?: return
                if (v.length == 8) {
                    scope.launch {
                        val endereco = withContext(Dispatchers.IO) { cep.consultar(v) }
                        endereco?.let(onOk)
                    }
                }
            }
        })
    }

    private fun gerarPdf() {
        val d = Declaracao(
            remNome = b.etRemNome.text.toString(),
            remCpf = b.etRemCpf.text.toString(),
            remTel = b.etRemTel.text.toString(),
            remEmail = b.etRemEmail.text.toString(),
            remEnd = b.etRemEnd.text.toString(),
            remNumero = b.etRemNumero.text.toString(),
            remComplemento = b.etRemComplemento.text.toString(),
            remCidade = b.etRemCidade.text.toString(),
            desNome = b.etDesNome.text.toString(),
            desCpf = b.etDesCpf.text.toString(),
            desEnd = b.etDesEnd.text.toString(),
            desNumero = b.etDesNumero.text.toString(),
            desComplemento = b.etDesComplemento.text.toString(),
            desCidade = b.etDesCidade.text.toString(),
            descricao = b.etDescricao.text.toString(),
            qtd = b.etQtd.text.toString(),
            valor = b.etValor.text.toString(),
            peso = b.etPeso.text.toString()
        )

        if (d.remNome.isBlank() || d.desNome.isBlank()) {
            Toast.makeText(requireContext(), "Preencha pelo menos os nomes", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            b.tvStatus.text = "🔄 Gerando PDF..."
            b.progress.visibility = View.VISIBLE

            try {
                val result = withContext(Dispatchers.IO) {
                    val ctx = requireContext()
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "DACE_${timestamp}.pdf"
                    
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val decDir = File(downloadDir, "Declaracoes").apply { mkdirs() }
                    val file = File(decDir, fileName)
                    
                    PdfDeclaracaoGenerator().gerar(d, file)
                    file
                }

                lastGeneratedPdf = result
                b.progress.visibility = View.GONE
                b.tvStatus.text = "✅ PDF gerado!\n📁 Download/Declaracoes/"
                
                showActionButtons(result)
                
                Toast.makeText(requireContext(), "PDF salvo em: Download/Declaracoes/", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                b.progress.visibility = View.GONE
                b.tvStatus.text = "❌ Erro: ${e.message}"
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showActionButtons(pdf: File) {
        val container = b.actionButtonsContainer
        
        if (container.childCount > 0) {
            container.removeAllViews()
        }

        val actionsLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 12, 0, 0)
        }

        val shareBtn = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "📤 Compartilhar"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 4
            }
            setOnClickListener { sharePdf(pdf) }
        }

        val openBtn = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "👁️ Abrir"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openPdf(pdf) }
        }

        actionsLayout.addView(shareBtn)
        actionsLayout.addView(openBtn)
        container.addView(actionsLayout)
    }

    private fun openPdf(pdf: File) {
        val ctx = requireContext()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", pdf)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(android.content.Intent.createChooser(intent, "Abrir PDF"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Nenhum app para abrir PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePdf(pdf: File) {
        val ctx = requireContext()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", pdf)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, pdf.name)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(android.content.Intent.createChooser(intent, "Compartilhar PDF"))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Erro ao compartilhar", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
