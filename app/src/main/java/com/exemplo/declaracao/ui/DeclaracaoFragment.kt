package com.exemplo.declaracao.ui
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.exemplo.declaracao.databinding.FragmentDeclaracaoBinding
import com.exemplo.declaracao.model.Declaracao
import com.exemplo.declaracao.util.CepService
import com.exemplo.declaracao.util.PdfDeclaracaoGenerator
import kotlinx.coroutines.*
import java.io.File
class DeclaracaoFragment : Fragment() {
    private var _b: FragmentDeclaracaoBinding? = null
    private val b get() = _b!!
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val cep = CepService()
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View { _b = FragmentDeclaracaoBinding.inflate(i, c, false); return b.root }
    override fun onViewCreated(v: View, s: Bundle?) {
        watchCep(b.etRemCep) { b.etRemEnd.setText("${it.logradouro}, ${it.bairro}"); b.etRemCidade.setText("${it.localidade}/${it.uf}") }
        watchCep(b.etDesCep) { b.etDesEnd.setText("${it.logradouro}, ${it.bairro}"); b.etDesCidade.setText("${it.localidade}/${it.uf}") }
        b.btnGerar.setOnClickListener { gerarPdf() }
    }
    private fun watchCep(field: android.widget.EditText, onOk: (CepService.Endereco) -> Unit) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                val v = e?.toString()?.replace(Regex("\\D"), "") ?: return
                if (v.length == 8) scope.launch { withContext(Dispatchers.IO) { cep.consultar(v) }?.let(onOk) }
            }
        })
    }
    private fun gerarPdf() {
        val d = Declaracao(b.etRemNome.text.toString(), b.etRemCpf.text.toString(), b.etRemTel.text.toString(), b.etRemEmail.text.toString(), b.etRemEnd.text.toString(), b.etRemCidade.text.toString(), b.etDesNome.text.toString(), b.etDesCpf.text.toString(), b.etDesEnd.text.toString(), b.etDesCidade.text.toString(), b.etDescricao.text.toString(), b.etQtd.text.toString(), b.etValor.text.toString(), b.etPeso.text.toString())
        if (d.remNome.isBlank() || d.desNome.isBlank()) { Toast.makeText(requireContext(), "Preencha os nomes", Toast.LENGTH_SHORT).show(); return }
        scope.launch {
            b.tvStatus.text = "Gerando PDF..."
            val out = withContext(Dispatchers.IO) { val dir = File(requireContext().getExternalFilesDir(null), "Declaracoes").apply { mkdirs() }; val file = File(dir, "DACE_${System.currentTimeMillis()}.pdf"); PdfDeclaracaoGenerator().gerar(d, file); file }
            b.tvStatus.text = "✅ PDF gerado:\n${out.absolutePath}"
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
