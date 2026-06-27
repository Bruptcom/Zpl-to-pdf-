package com.exemplo.declaracao.util
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
class CepService {
    data class Endereco(val cep: String, val logradouro: String, val bairro: String, val localidade: String, val uf: String)
    private val http = OkHttpClient()
    private val gson = Gson()
    fun consultar(cep: String): Endereco? = try {
        val req = Request.Builder().url("https://viacep.com.br/ws/$cep/json/").build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) gson.fromJson(r.body!!.string(), Endereco::class.java) else null }
    } catch (_: Exception) { null }
}
